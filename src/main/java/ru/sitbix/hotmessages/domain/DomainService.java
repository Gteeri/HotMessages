package ru.sitbix.hotmessages.domain;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.sitbix.hotmessages.HotMessagesPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class DomainService {
    private final HotMessagesPlugin plugin;
    private final File file;
    private volatile List<DomainEntry> domains = List.of();
    private volatile List<Pattern> compiledPatterns = List.of();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HotMessages-DomainIO");
        t.setDaemon(true);
        return t;
    });

    public DomainService(HotMessagesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "domains.yml");
        load();
    }

    public void load() {
        Set<String> seen = new LinkedHashSet<>();

        List<DomainEntry> entries = new ArrayList<>();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("domains");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                boolean enabled = section.getBoolean(key, true);
                String domain = key.toLowerCase(Locale.ROOT);
                if (!domain.isBlank() && seen.add(domain)) {
                    entries.add(new DomainEntry(domain, enabled));
                }
            }
        }

        YamlConfiguration fileConfig = YamlConfiguration.loadConfiguration(file);
        List<String> raw = fileConfig.getStringList("domains");
        for (String entry : raw) {
            boolean enabled = true;
            String domain = entry;
            if (entry.startsWith("-")) {
                enabled = false;
                domain = entry.substring(1);
            }
            domain = domain.toLowerCase(Locale.ROOT).trim();
            if (!domain.isBlank() && seen.add(domain)) {
                entries.add(new DomainEntry(domain, enabled));
            }
        }

        this.domains = Collections.unmodifiableList(entries);
        rebuildPatterns();
    }

    public void save() {
        ioExecutor.execute(() -> {
            YamlConfiguration fileConfig = new YamlConfiguration();
            List<String> raw = new ArrayList<>();

            ConfigurationSection configSection = plugin.getConfig().getConfigurationSection("domains");
            Set<String> configKeys = configSection != null ? configSection.getKeys(false) : Set.of();

            for (DomainEntry entry : domains) {
                if (configKeys.contains(entry.domain())) {
                    continue;
                }
                raw.add(entry.enabled() ? entry.domain() : "-" + entry.domain());
            }
            fileConfig.set("domains", raw);
            try {
                fileConfig.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void close() {
        ioExecutor.shutdown();
    }

    public boolean add(String domain) {
        String normalized = domain.toLowerCase(Locale.ROOT).trim();
        if (normalized.isBlank()) {
            return false;
        }
        List<DomainEntry> updated = new ArrayList<>(domains);
        for (DomainEntry entry : updated) {
            if (entry.domain().equals(normalized)) {
                return false;
            }
        }
        updated.add(new DomainEntry(normalized, true));
        this.domains = Collections.unmodifiableList(updated);
        rebuildPatterns();
        save();
        return true;
    }

    public boolean remove(String domain) {
        String normalized = domain.toLowerCase(Locale.ROOT).trim();
        List<DomainEntry> updated = new ArrayList<>(domains);
        boolean removed = updated.removeIf(e -> e.domain().equals(normalized));
        if (removed) {
            this.domains = Collections.unmodifiableList(updated);
            rebuildPatterns();
            save();
        }
        return removed;
    }

    public boolean toggle(String domain) {
        String normalized = domain.toLowerCase(Locale.ROOT).trim();
        List<DomainEntry> updated = new ArrayList<>(domains);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).domain().equals(normalized)) {
                updated.set(i, updated.get(i).toggle());
                this.domains = Collections.unmodifiableList(updated);
                rebuildPatterns();
                save();
                return true;
            }
        }
        return false;
    }

    public boolean isEnabled(String domain) {
        String normalized = domain.toLowerCase(Locale.ROOT).trim();
        for (DomainEntry entry : domains) {
            if (entry.domain().equals(normalized)) {
                return entry.enabled();
            }
        }
        return false;
    }

    public boolean isDefault(String domain) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("domains");
        return section != null && section.contains(domain.toLowerCase(Locale.ROOT));
    }

    public List<DomainEntry> all() {
        return domains;
    }

    private void rebuildPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        for (DomainEntry entry : domains) {
            if (!entry.enabled()) {
                continue;
            }
            String domain = entry.domain();
            try {
                if (domain.startsWith(".")) {
                    String tld = domain.substring(1);
                    String normalPattern = "\\." + Pattern.quote(tld);
                    patterns.add(Pattern.compile("(?i)" + normalPattern));
                    StringBuilder spaced = new StringBuilder("\\.\\s*");
                    for (int i = 0; i < tld.length(); i++) {
                        if (i > 0) spaced.append("\\s*");
                        spaced.append(Pattern.quote(String.valueOf(tld.charAt(i))));
                    }
                    patterns.add(Pattern.compile("(?i)" + spaced.toString()));
                } else {
                    patterns.add(Pattern.compile("(?i)" + Pattern.quote(domain)));
                    String compact = domain.replaceAll("[\\s._,;:/\\\\\\-]+", "");
                    if (!compact.equals(domain)) {
                        patterns.add(Pattern.compile("(?i)" + Pattern.quote(compact)));
                    }
                }
            } catch (PatternSyntaxException ignored) {
            }
        }
        this.compiledPatterns = Collections.unmodifiableList(patterns);
    }

    public List<Pattern> compiledPatterns() {
        return compiledPatterns;
    }
}
