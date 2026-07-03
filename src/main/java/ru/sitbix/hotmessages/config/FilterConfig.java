package ru.sitbix.hotmessages.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record FilterConfig(
    Settings settings,
    Spam spam,
    Normalization normalization,
    ChatSettings chatSettings,
    Map<String, Integer> privateMessageCommands,
    List<String> whitelistFragments,
    List<RegexRule> whitelistRegex,
    List<String> blacklistFragments,
    List<String> blacklistCompactFragments,
    List<RegexRule> regexRules,
    Map<String, String> messages,
    GuiMaterials guiMaterials
) {
    public static FilterConfig load(FileConfiguration config) {
        return new FilterConfig(
            Settings.load(config),
            Spam.load(config),
            Normalization.load(config),
            ChatSettings.load(config),
            loadCommandStarts(config.getConfigurationSection("private-message-commands")),
            lowerList(config.getStringList("whitelist.fragments")),
            loadRules(config, "whitelist.regex"),
            lowerList(config.getStringList("blacklist.fragments")),
            lowerList(config.getStringList("blacklist.compact-fragments")),
            loadRules(config, "regex-rules"),
            loadMessages(config.getConfigurationSection("messages")),
            GuiMaterials.load(config)
        );
    }

    private static Map<String, Integer> loadCommandStarts(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }

        Map<String, Integer> commands = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            commands.put(key.toLowerCase(Locale.ROOT), Math.max(1, section.getInt(key, 2)));
        }
        return Collections.unmodifiableMap(commands);
    }

    private static Map<String, String> loadMessages(ConfigurationSection section) {
        Map<String, String> values = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                values.put(key, section.getString(key, ""));
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static List<RegexRule> loadRules(FileConfiguration config, String path) {
        List<RegexRule> rules = new ArrayList<>();
        for (Map<?, ?> rawRule : config.getMapList(path)) {
            Object rawId = rawRule.get("id");
            Object rawReason = rawRule.get("reason");
            Object rawPattern = rawRule.get("pattern");
            String id = rawId == null ? "rule-" + (rules.size() + 1) : String.valueOf(rawId);
            String reason = rawReason == null ? id : String.valueOf(rawReason);
            String pattern = rawPattern == null ? "" : String.valueOf(rawPattern);
            if (pattern.isBlank()) {
                continue;
            }

            try {
                rules.add(new RegexRule(id, reason, Pattern.compile(pattern)));
            } catch (PatternSyntaxException ignored) {
                // Invalid admin rules are ignored so the plugin can still boot.
            }
        }
        return Collections.unmodifiableList(rules);
    }

    private static List<String> lowerList(List<String> input) {
        List<String> output = new ArrayList<>(input.size());
        for (String value : input) {
            if (value != null && !value.isBlank()) {
                output.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableList(output);
    }

    public record Settings(
        boolean enabled,
        boolean checkPublicChat,
        boolean checkPrivateMessages,
        String blockAction,
        boolean notifyStaff,
        boolean logToFile,
        boolean logToConsole,
        int rememberRecentLogs,
        String bypassPermission,
        String notifyPermission,
        String adminPermission,
        String ghostPermission
    ) {
        private static Settings load(FileConfiguration config) {
            return new Settings(
                config.getBoolean("settings.enabled", true),
                config.getBoolean("settings.check-public-chat", true),
                config.getBoolean("settings.check-private-messages", true),
                config.getString("settings.block-action", "cancel"),
                config.getBoolean("settings.notify-staff", true),
                config.getBoolean("settings.log-to-file", true),
                config.getBoolean("settings.log-to-console", true),
                Math.max(10, config.getInt("settings.remember-recent-logs", 54)),
                config.getString("settings.bypass-permission", "hotmessages.bypass"),
                config.getString("settings.notify-permission", "hotmessages.notify"),
                config.getString("settings.admin-permission", "hotmessages.admin"),
                config.getString("settings.ghost-permission", "hotmessages.ghost")
            );
        }
    }

    public record Spam(
        boolean enabled,
        int maxMessagesPerWindow,
        int windowSeconds,
        int maxDuplicates,
        int duplicateWindowSeconds,
        int minMessageLengthForDuplicate
    ) {
        private static Spam load(FileConfiguration config) {
            return new Spam(
                config.getBoolean("spam.enabled", true),
                Math.max(2, config.getInt("spam.max-messages-per-window", 5)),
                Math.max(1, config.getInt("spam.window-seconds", 8)),
                Math.max(1, config.getInt("spam.max-duplicates", 2)),
                Math.max(5, config.getInt("spam.duplicate-window-seconds", 45)),
                Math.max(1, config.getInt("spam.min-message-length-for-duplicate", 6))
            );
        }
    }

    public record Normalization(
        boolean lowercase,
        boolean stripColorCodes,
        boolean stripDiacritics,
        boolean collapseSpaces,
        boolean removeZeroWidth,
        boolean removeSeparatorsForCompactCheck,
        int maxRepeatedCharacterRun,
        Map<String, String> replacements
    ) {
        private static Normalization load(FileConfiguration config) {
            Map<String, String> replacements = new LinkedHashMap<>();
            ConfigurationSection section = config.getConfigurationSection("normalization.replacements");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    replacements.put(key, section.getString(key, ""));
                }
            }

            return new Normalization(
                config.getBoolean("normalization.lowercase", true),
                config.getBoolean("normalization.strip-color-codes", true),
                config.getBoolean("normalization.strip-diacritics", true),
                config.getBoolean("normalization.collapse-spaces", true),
                config.getBoolean("normalization.remove-zero-width", true),
                config.getBoolean("normalization.remove-separators-for-compact-check", true),
                Math.max(1, config.getInt("normalization.max-repeated-character-run", 2)),
                Collections.unmodifiableMap(replacements)
            );
        }
    }

    public record RegexRule(String id, String reason, Pattern pattern) {
    }

    public record ChatSettings(
        boolean enabled,
        int localDistance,
        String globalPrefix,
        String localFormat,
        String globalFormat,
        boolean allowColorCodes
    ) {
        private static ChatSettings load(FileConfiguration config) {
            return new ChatSettings(
                config.getBoolean("chat.enabled", true),
                Math.max(1, config.getInt("chat.local-distance", 200)),
                config.getString("chat.global-prefix", "!"),
                config.getString("chat.local-format", "&7[Локальный] &f{player} &8» &7{message}"),
                config.getString("chat.global-format", "&6[Глобальный] &f{player} &8» &f{message}"),
                config.getBoolean("chat.allow-color-codes", true)
            );
        }
    }

    public record GuiMaterials(
        Material enabled,
        Material disabled,
        Material logs,
        Material reload,
        Material test,
        Material close
    ) {
        private static GuiMaterials load(FileConfiguration config) {
            return new GuiMaterials(
                material(config.getString("gui.material-enabled", "LIME_DYE")),
                material(config.getString("gui.material-disabled", "GRAY_DYE")),
                material(config.getString("gui.material-logs", "WRITABLE_BOOK")),
                material(config.getString("gui.material-reload", "COMPARATOR")),
                material(config.getString("gui.material-test", "SPYGLASS")),
                material(config.getString("gui.material-close", "BARRIER"))
            );
        }

        private static Material material(String name) {
            Material material = Material.matchMaterial(name == null ? "" : name);
            return material == null ? Material.STONE : material;
        }
    }
}
