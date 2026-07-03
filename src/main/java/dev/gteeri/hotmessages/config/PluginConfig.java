package dev.gteeri.hotmessages.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Загружает config.yml, messages.yml и banned-words.yml, даёт типизированный доступ
 * к настройкам и форматирует сообщения (& и hex-цвета вида &#RRGGBB).
 */
public final class PluginConfig {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File bannedWordsFile;
    private FileConfiguration bannedWords;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        saveResourceIfMissing("messages.yml");
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));

        saveResourceIfMissing("banned-words.yml");
        bannedWordsFile = new File(plugin.getDataFolder(), "banned-words.yml");
        bannedWords = YamlConfiguration.loadConfiguration(bannedWordsFile);
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    // ============================ основной конфиг ============================

    public boolean enabled() {
        return config.getBoolean("enabled", true);
    }

    public void setEnabled(boolean value) {
        config.set("enabled", value);
        plugin.saveConfig();
    }

    public boolean filterEnabled() {
        return config.getBoolean("filter.enabled", true);
    }

    public String filterMode() {
        return config.getString("filter.mode", "CENSOR").toUpperCase();
    }

    public char censorSymbol() {
        String s = config.getString("filter.censor-symbol", "*");
        return s == null || s.isEmpty() ? '*' : s.charAt(0);
    }

    public String bypassPermission() {
        return config.getString("filter.bypass-permission", "hotmessages.bypass");
    }

    public boolean warningsEnabled() {
        return config.getBoolean("warnings.enabled", true);
    }

    public int warningsBeforeMute() {
        return config.getInt("warnings.warnings-before-mute", 3);
    }

    public long warningResetAfterMinutes() {
        return config.getLong("warnings.warning-reset-after-minutes", 30);
    }

    public long muteDurationMinutes() {
        return config.getLong("warnings.mute-duration-minutes", 5);
    }

    public boolean escalateMute() {
        return config.getBoolean("warnings.escalate-mute", true);
    }

    public long maxMuteDurationMinutes() {
        return config.getLong("warnings.max-mute-duration-minutes", 120);
    }

    public boolean antiSpamEnabled() {
        return config.getBoolean("anti-spam.enabled", true);
    }

    public long messageCooldownMs() {
        return config.getLong("anti-spam.message-cooldown-ms", 1200);
    }

    public int duplicateLimit() {
        return config.getInt("anti-spam.duplicate-limit", 3);
    }

    public long duplicateWindowSeconds() {
        return config.getLong("anti-spam.duplicate-window-seconds", 30);
    }

    public boolean capsEnabled() {
        return config.getBoolean("anti-spam.caps.enabled", true);
    }

    public int capsMinLength() {
        return config.getInt("anti-spam.caps.min-length", 8);
    }

    public int capsMaxPercentage() {
        return config.getInt("anti-spam.caps.max-percentage", 70);
    }

    public boolean floodCharsEnabled() {
        return config.getBoolean("anti-spam.flood-chars.enabled", true);
    }

    public int floodMaxRepeats() {
        return config.getInt("anti-spam.flood-chars.max-repeats", 3);
    }

    public long cleanupIntervalSeconds() {
        return config.getLong("performance.cleanup-interval-seconds", 300);
    }

    public long forgetAfterMinutes() {
        return config.getLong("performance.forget-after-minutes", 15);
    }

    public String mutesFileName() {
        return config.getString("storage.mutes-file", "mutes.yml");
    }

    // ============================ запрещённые слова ============================

    public List<String> bannedWords() {
        return new ArrayList<>(bannedWords.getStringList("words"));
    }

    public void addBannedWord(String word) {
        List<String> words = bannedWords();
        words.add(word);
        bannedWords.set("words", words);
        saveBannedWords();
    }

    public boolean removeBannedWord(String word) {
        List<String> words = bannedWords();
        boolean removed = words.removeIf(w -> w.equalsIgnoreCase(word));
        if (removed) {
            bannedWords.set("words", words);
            saveBannedWords();
        }
        return removed;
    }

    private void saveBannedWords() {
        try {
            bannedWords.save(bannedWordsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить banned-words.yml: " + e.getMessage());
        }
    }

    // ============================ сообщения / цвета ============================

    public String raw(String key) {
        return messages.getString(key, key);
    }

    public String colorize(String input) {
        if (input == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public String msg(String key, String... placeholders) {
        String text = raw("prefix") + raw(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace(placeholders[i], placeholders[i + 1]);
        }
        return colorize(text);
    }

    public void reload() {
        load();
    }
}
