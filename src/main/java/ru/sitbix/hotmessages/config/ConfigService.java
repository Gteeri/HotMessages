package ru.sitbix.hotmessages.config;

import org.bukkit.configuration.file.FileConfiguration;
import ru.sitbix.hotmessages.HotMessagesPlugin;

import java.util.ArrayList;
import java.util.List;

public final class ConfigService {
    private final HotMessagesPlugin plugin;

    public ConfigService(HotMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void addBlacklistFragment(String fragment) {
        FileConfiguration config = plugin.getConfig();
        List<String> fragments = new ArrayList<>(config.getStringList("blacklist.fragments"));
        if (!fragments.contains(fragment)) {
            fragments.add(fragment);
            config.set("blacklist.fragments", fragments);
            plugin.saveConfig();
        }
    }

    public boolean removeBlacklistFragment(String fragment) {
        FileConfiguration config = plugin.getConfig();
        List<String> fragments = new ArrayList<>(config.getStringList("blacklist.fragments"));
        boolean removed = fragments.remove(fragment);
        if (removed) {
            config.set("blacklist.fragments", fragments);
            plugin.saveConfig();
        }
        return removed;
    }

    public List<String> blacklistFragments() {
        return plugin.getConfig().getStringList("blacklist.fragments");
    }
}
