package ru.sitbix.hotmessages.log;

import ru.sitbix.hotmessages.HotMessagesPlugin;
import ru.sitbix.hotmessages.config.FilterConfig;
import ru.sitbix.hotmessages.database.DatabaseService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class LogService {
    private final HotMessagesPlugin plugin;
    private final ExecutorService writer;
    private final Deque<LogEntry> recent = new ArrayDeque<>();
    private volatile FilterConfig config;

    public LogService(HotMessagesPlugin plugin) {
        this.plugin = plugin;
        this.writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "HotMessages Log Writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void configure(FilterConfig config) {
        this.config = config;
    }

    /**
     * Load recent logs from the database into memory cache on startup.
     */
    public void loadFromDatabase() {
        DatabaseService db = plugin.databaseService();
        if (db == null) return;

        FilterConfig activeConfig = config;
        int max = activeConfig == null ? 54 : activeConfig.settings().rememberRecentLogs();
        List<LogEntry> dbLogs = db.loadRecentLogs(max);

        synchronized (recent) {
            recent.clear();
            // dbLogs are already in DESC order, so addLast preserves newest-first
            for (LogEntry entry : dbLogs) {
                recent.addLast(entry);
            }
        }
    }

    public void log(LogEntry entry) {
        FilterConfig activeConfig = config;
        int max = activeConfig == null ? 54 : activeConfig.settings().rememberRecentLogs();

        synchronized (recent) {
            recent.addFirst(entry);
            while (recent.size() > max) {
                recent.removeLast();
            }
        }

        if (activeConfig == null) {
            return;
        }

        if (activeConfig.settings().logToConsole()) {
            plugin.getLogger().warning(entry.fileLine());
        }

        // Save to database
        DatabaseService db = plugin.databaseService();
        if (db != null) {
            db.saveLog(entry);
        }

        // Optionally also write to file
        if (activeConfig.settings().logToFile()) {
            writer.execute(() -> writeToFile(entry));
        }
    }

    public List<LogEntry> recent() {
        synchronized (recent) {
            return Collections.unmodifiableList(new ArrayList<>(recent));
        }
    }

    /**
     * Purge old logs from the database based on config setting.
     */
    public void purgeOldLogs() {
        int days = plugin.getConfig().getInt("database.purge-logs-after-days", 30);
        DatabaseService db = plugin.databaseService();
        if (db != null && days > 0) {
            db.purgeOldLogs(days);
        }
    }

    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private void writeToFile(LogEntry entry) {
        try {
            Path logsDirectory = plugin.getDataFolder().toPath().resolve("logs");
            Files.createDirectories(logsDirectory);
            Files.writeString(
                logsDirectory.resolve("blocked-messages.log"),
                entry.fileLine() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not write HotMessages log: " + exception.getMessage());
        }
    }
}
