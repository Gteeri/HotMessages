package dev.gteeri.hotmessages.punishment;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Хранит временные муты чата — переживают перезапуск сервера (файл mutes.yml). */
public final class MuteManager {

    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, Long> mutedUntil = new ConcurrentHashMap<>();

    public MuteManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
    }

    public void load() {
        mutedUntil.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        for (String key : yaml.getKeys(false)) {
            long until = yaml.getLong(key);
            if (until > now) {
                try {
                    mutedUntil.put(UUID.fromString(key), until);
                } catch (IllegalArgumentException ignored) {
                    // корруптированная запись — пропускаем
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        mutedUntil.forEach((uuid, until) -> {
            if (until > now) {
                yaml.set(uuid.toString(), until);
            }
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось сохранить mutes.yml", e);
        }
    }

    public void mute(UUID uuid, long durationMillis) {
        mutedUntil.put(uuid, System.currentTimeMillis() + durationMillis);
        save();
    }

    public void unmute(UUID uuid) {
        mutedUntil.remove(uuid);
        save();
    }

    public boolean isMuted(UUID uuid) {
        Long until = mutedUntil.get(uuid);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            mutedUntil.remove(uuid);
            return false;
        }
        return true;
    }

    public long remainingSeconds(UUID uuid) {
        Long until = mutedUntil.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000);
    }
}
