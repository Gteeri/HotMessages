package dev.gteeri.hotmessages;

import dev.gteeri.hotmessages.antispam.SpamGuard;
import dev.gteeri.hotmessages.command.HotMessagesCommand;
import dev.gteeri.hotmessages.config.PluginConfig;
import dev.gteeri.hotmessages.filter.WordFilter;
import dev.gteeri.hotmessages.listener.ChatListener;
import dev.gteeri.hotmessages.punishment.MuteManager;
import dev.gteeri.hotmessages.punishment.WarnManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

/**
 * Главный класс плагина: фильтр запрещённых слов, антиспам, предупреждения/муты.
 */
public final class HotMessages extends JavaPlugin {

    private PluginConfig config;
    private WordFilter wordFilter;
    private SpamGuard spamGuard;
    private MuteManager muteManager;
    private WarnManager warnManager;

    @Override
    public void onEnable() {
        config = new PluginConfig(this);
        config.load();

        wordFilter = new WordFilter();
        wordFilter.rebuild(config.bannedWords());

        spamGuard = new SpamGuard(config);
        muteManager = new MuteManager(this, config.mutesFileName());
        muteManager.load();
        warnManager = new WarnManager(config, muteManager);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        HotMessagesCommand executor = new HotMessagesCommand(this);
        PluginCommand command = getCommand("hotmessages");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        long intervalSeconds = Math.max(30, config.cleanupIntervalSeconds());
        getServer().getAsyncScheduler().runAtFixedRate(this, task ->
                        spamGuard.cleanup(uuid -> getServer().getPlayer(uuid) != null),
                intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        getLogger().info("HotMessages включён (" + config.bannedWords().size() + " запрещённых слов).");
    }

    @Override
    public void onDisable() {
        if (muteManager != null) {
            muteManager.save();
        }
        getLogger().info("HotMessages выключен.");
    }

    public PluginConfig pluginConfig() {
        return config;
    }

    public WordFilter wordFilter() {
        return wordFilter;
    }

    public SpamGuard spamGuard() {
        return spamGuard;
    }

    public MuteManager muteManager() {
        return muteManager;
    }

    public WarnManager warnManager() {
        return warnManager;
    }

    public void reloadAll() {
        config.reload();
        wordFilter.rebuild(config.bannedWords());
    }
}
