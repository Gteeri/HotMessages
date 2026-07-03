package ru.sitbix.hotmessages.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ru.sitbix.hotmessages.HotMessagesPlugin;
import ru.sitbix.hotmessages.log.LogEntry;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class DatabaseService {
    private final HotMessagesPlugin plugin;
    private HikariDataSource dataSource;
    private final ExecutorService executor;
    private boolean mysql;

    public DatabaseService(HotMessagesPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HotMessages-DB");
            t.setDaemon(true);
            return t;
        });
    }

    public void init() {
        String type = plugin.getConfig().getString("database.type", "sqlite").toLowerCase(java.util.Locale.ROOT);
        this.mysql = type.equals("mysql");
        HikariConfig config = new HikariConfig();
        config.setPoolName("HotMessages-DB");

        if (mysql) {
            String host = plugin.getConfig().getString("database.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("database.mysql.port", 3306);
            String database = plugin.getConfig().getString("database.mysql.database", "hotmessages");
            String username = plugin.getConfig().getString("database.mysql.username", "root");
            String password = plugin.getConfig().getString("database.mysql.password", "");
            int poolSize = Math.max(1, plugin.getConfig().getInt("database.mysql.pool-size", 5));

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(poolSize);
        } else {
            String fileName = plugin.getConfig().getString("database.sqlite.file", "hotmessages.db");
            File dbFile = new File(plugin.getDataFolder(), fileName);
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(1);
            config.setConnectionTestQuery("SELECT 1");
        }

        this.dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection()) {
            if (!mysql) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA synchronous=NORMAL");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to configure database", e);
        }

        createTables();
        plugin.getLogger().info("[Database] Initialized " + (mysql ? "MySQL" : "SQLite") + " database.");
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            if (mysql) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hm_mutes (
                        uuid       VARCHAR(36) PRIMARY KEY,
                        expires_at BIGINT      NOT NULL,
                        reason     TEXT        NOT NULL,
                        muter      VARCHAR(64) NOT NULL,
                        created_at BIGINT      NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hm_logs (
                        id          INT PRIMARY KEY AUTO_INCREMENT,
                        time        BIGINT      NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(64) NOT NULL,
                        channel     VARCHAR(16) NOT NULL,
                        message     TEXT        NOT NULL,
                        rule_id     VARCHAR(128),
                        reason      VARCHAR(128),
                        normalized  TEXT
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            } else {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hm_mutes (
                        uuid       VARCHAR(36) PRIMARY KEY,
                        expires_at BIGINT      NOT NULL,
                        reason     TEXT        NOT NULL,
                        muter      VARCHAR(64) NOT NULL,
                        created_at BIGINT      NOT NULL
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hm_logs (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        time        BIGINT      NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(64) NOT NULL,
                        channel     VARCHAR(16) NOT NULL,
                        message     TEXT        NOT NULL,
                        rule_id     VARCHAR(128),
                        reason      VARCHAR(128),
                        normalized  TEXT
                    )
                """);
            }

            // Create indexes (safe for both SQLite and MySQL)
            try {
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_time ON hm_logs(time)");
            } catch (SQLException ignored) {}
            try {
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_player ON hm_logs(player_uuid)");
            } catch (SQLException ignored) {}
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
        }
    }

    // ─── Mute operations ─────────────────────────────

    public void saveMute(UUID uuid, long expiresAt, String reason, String muter) {
        executor.execute(() -> {
            String sql = mysql
                ? "REPLACE INTO hm_mutes (uuid, expires_at, reason, muter, created_at) VALUES (?, ?, ?, ?, ?)"
                : "INSERT OR REPLACE INTO hm_mutes (uuid, expires_at, reason, muter, created_at) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, expiresAt);
                ps.setString(3, reason);
                ps.setString(4, muter);
                ps.setLong(5, Instant.now().getEpochSecond());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save mute for " + uuid, e);
            }
        });
    }

    public void removeMute(UUID uuid) {
        executor.execute(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM hm_mutes WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove mute for " + uuid, e);
            }
        });
    }

    public Map<UUID, MuteData> loadAllMutes() {
        Map<UUID, MuteData> result = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, expires_at, reason, muter FROM hm_mutes")) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    result.put(uuid, new MuteData(
                        rs.getLong("expires_at"),
                        rs.getString("reason"),
                        rs.getString("muter")
                    ));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load mutes", e);
        }
        return result;
    }

    public record MuteData(long expiresAt, String reason, String muter) {}

    // ─── Log operations ─────────────────────────────

    public void saveLog(LogEntry entry) {
        executor.execute(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO hm_logs (time, player_uuid, player_name, channel, message, rule_id, reason, normalized) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, entry.time().getEpochSecond());
                ps.setString(2, entry.playerUuid().toString());
                ps.setString(3, entry.playerName());
                ps.setString(4, entry.channel());
                ps.setString(5, entry.message());
                ps.setString(6, entry.ruleId());
                ps.setString(7, entry.reason());
                ps.setString(8, entry.normalized());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save log entry", e);
            }
        });
    }

    public List<LogEntry> loadRecentLogs(int limit) {
        List<LogEntry> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT time, player_uuid, player_name, channel, message, rule_id, reason, normalized FROM hm_logs ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        result.add(new LogEntry(
                            Instant.ofEpochSecond(rs.getLong("time")),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            rs.getString("channel"),
                            rs.getString("message"),
                            rs.getString("rule_id"),
                            rs.getString("reason"),
                            rs.getString("normalized")
                        ));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load recent logs", e);
        }
        return result;
    }

    public void purgeOldLogs(int days) {
        if (days <= 0) return;
        executor.execute(() -> {
            long cutoff = Instant.now().getEpochSecond() - ((long) days * 86400);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM hm_logs WHERE time < ?")) {
                ps.setLong(1, cutoff);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    plugin.getLogger().info("[Database] Purged " + deleted + " old log entries.");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to purge old logs", e);
            }
        });
    }

    // ─── Migration from mutes.yml ────────────────────────

    public void migrateFromYaml(File mutesFile) {
        if (!mutesFile.exists()) return;

        org.bukkit.configuration.file.YamlConfiguration yaml =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(mutesFile);

        if (!yaml.contains("mutes")) return;

        org.bukkit.configuration.ConfigurationSection section = yaml.getConfigurationSection("mutes");
        if (section == null) return;

        int count = 0;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long expiresAt = yaml.getLong("mutes." + key + ".expires-at", -1);
                String reason = yaml.getString("mutes." + key + ".reason", "");
                String muter = yaml.getString("mutes." + key + ".muter", "");

                // Save synchronously during migration
                String sql = mysql
                    ? "REPLACE INTO hm_mutes (uuid, expires_at, reason, muter, created_at) VALUES (?, ?, ?, ?, ?)"
                    : "INSERT OR REPLACE INTO hm_mutes (uuid, expires_at, reason, muter, created_at) VALUES (?, ?, ?, ?, ?)";
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setLong(2, expiresAt);
                    ps.setString(3, reason);
                    ps.setString(4, muter);
                    ps.setLong(5, Instant.now().getEpochSecond());
                    ps.executeUpdate();
                    count++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate mute for " + key + ": " + e.getMessage());
            }
        }

        if (count > 0) {
            File backup = new File(mutesFile.getParentFile(), "mutes.yml.bak");
            if (mutesFile.renameTo(backup)) {
                plugin.getLogger().info("[Database] Migrated " + count + " mutes from mutes.yml → database. Backup: mutes.yml.bak");
            }
        }
    }

    // ─── Lifecycle ─────────────────────────────

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
