package ru.sitbix.hotmessages.sound;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.sitbix.hotmessages.HotMessagesPlugin;

import java.util.HashMap;
import java.util.Map;

public final class SoundService {
    private final HotMessagesPlugin plugin;
    private final Map<String, SoundConfig> sounds = new HashMap<>();

    public SoundService(HotMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        sounds.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sounds");
        if (section == null) {
            plugin.getLogger().warning("[SoundService] No sounds section found in config!");
            return;
        }

        for (String key : section.getKeys(false)) {
            String soundName = section.getString(key + ".sound", "");
            float volume = (float) section.getDouble(key + ".volume", 1.0);
            float pitch = (float) section.getDouble(key + ".pitch", 1.0);
            boolean enabled = section.getBoolean(key + ".enabled", true);

            Sound sound = null;
            if (!soundName.isEmpty()) {
                try {
                    sound = Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[SoundService] Invalid sound: " + soundName);
                }
            }
            sounds.put(key, new SoundConfig(sound, soundName, volume, pitch, enabled));
        }
        plugin.getLogger().info("[SoundService] Loaded " + sounds.size() + " sounds.");
    }

    public void play(Player player, String key) {
        SoundConfig config = sounds.get(key);
        if (config == null) return;
        if (!config.enabled()) return;
        if (config.sound() == null) {
            plugin.getLogger().warning("[SoundService] Sound is null for key=" + key + " name=" + config.soundName());
            return;
        }
        player.playSound(player.getLocation(), config.sound(), config.volume(), config.pitch());
    }

    public void playAt(org.bukkit.Location location, String key) {
        SoundConfig config = sounds.get(key);
        if (config == null || !config.enabled() || config.sound() == null) return;
        location.getWorld().playSound(location, config.sound(), config.volume(), config.pitch());
    }

    public record SoundConfig(Sound sound, String soundName, float volume, float pitch, boolean enabled) {
    }
}
