package ru.sitbix.hotmessages;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sitbix.hotmessages.chat.ChatService;
import ru.sitbix.hotmessages.command.HotMessagesCommand;
import ru.sitbix.hotmessages.config.ConfigService;
import ru.sitbix.hotmessages.config.FilterConfig;
import ru.sitbix.hotmessages.database.DatabaseService;
import ru.sitbix.hotmessages.domain.DomainService;
import ru.sitbix.hotmessages.escalation.ViolationTracker;
import ru.sitbix.hotmessages.filter.ChatFilter;
import ru.sitbix.hotmessages.filter.SpamTracker;
import ru.sitbix.hotmessages.gui.GuiManager;
import ru.sitbix.hotmessages.listener.ChatListener;
import ru.sitbix.hotmessages.listener.CleanupListener;
import ru.sitbix.hotmessages.log.LogService;
import ru.sitbix.hotmessages.message.MessageService;
import ru.sitbix.hotmessages.mute.MuteService;
import ru.sitbix.hotmessages.pm.PmCommand;
import ru.sitbix.hotmessages.pm.PmService;
import ru.sitbix.hotmessages.sound.SoundService;

import java.io.File;

public final class HotMessagesPlugin extends JavaPlugin {
    private FilterConfig filterConfig;
    private MessageService messages;
    private ChatFilter chatFilter;
    private SpamTracker spamTracker;
    private LogService logService;
    private GuiManager guiManager;
    private DomainService domainService;
    private MuteService muteService;
    private ConfigService configService;
    private PmService pmService;
    private SoundService soundService;
    private ViolationTracker violationTracker;
    private DatabaseService databaseService;
    private ChatService chatService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialize database
        this.databaseService = new DatabaseService(this);
        databaseService.init();

        // Migrate mutes from old YAML if exists
        File oldMutesFile = new File(getDataFolder(), "mutes.yml");
        databaseService.migrateFromYaml(oldMutesFile);

        this.logService = new LogService(this);
        this.spamTracker = new SpamTracker();
        this.domainService = new DomainService(this);
        this.muteService = new MuteService(this);
        this.muteService.load();
        this.configService = new ConfigService(this);
        this.pmService = new PmService(this);
        this.soundService = new SoundService(this);
        this.violationTracker = new ViolationTracker(this);
        reloadHotMessages();
        this.chatService = new ChatService(this);
        this.soundService.load();

        // Load logs from database into memory cache
        logService.loadFromDatabase();

        // Purge old logs on startup
        logService.purgeOldLogs();

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new CleanupListener(this), this);
        this.guiManager = new GuiManager(this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        HotMessagesCommand hotMessagesCommand = new HotMessagesCommand(this);
        registerCommand("hotmessages", hotMessagesCommand, "hm", "hmchat", "chatfilter");

        registerPmCommands();
    }

    @Override
    public void onDisable() {
        if (logService != null) {
            logService.close();
        }
        if (domainService != null) {
            domainService.close();
        }
        if (databaseService != null) {
            databaseService.close();
        }
    }

    public void reloadHotMessages() {
        reloadConfig();
        this.filterConfig = FilterConfig.load(getConfig());
        this.messages = new MessageService(filterConfig.messages());
        this.chatFilter = new ChatFilter(filterConfig, domainService);
        this.spamTracker.configure(filterConfig);

        if (logService != null) {
            logService.configure(filterConfig);
        }
    }

    private void registerCommand(String name, HotMessagesCommand executor, String... aliases) {
        getServer().getCommandMap().register(name, "hotmessages", new org.bukkit.command.Command(name) {
            {
                setAliases(java.util.List.of(aliases));
                setDescription("HotMessages command");
                setPermission(filterConfig().settings().adminPermission());
            }

            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return executor.onCommand(sender, this, commandLabel, args);
            }

            @Override
            public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                return executor.onTabComplete(sender, this, alias, args);
            }
        });
    }

    private void registerPmCommands() {
        String[] pmAliases = {"m", "msg", "tell", "w", "whisper", "pm"};
        for (String alias : pmAliases) {
            registerPmCommand(alias);
        }
        registerPmCommand("r");
        registerPmCommand("reply");
    }

    private void registerPmCommand(String alias) {
        PmCommand pmCommand = new PmCommand(this, alias);
        getServer().getCommandMap().register(alias, "hotmessages", new org.bukkit.command.Command(alias) {
            {
                setDescription("Private message command");
            }

            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return pmCommand.onCommand(sender, this, commandLabel, args);
            }

            @Override
            public java.util.List<String> tabComplete(CommandSender sender, String commandLabel, String[] args) {
                return pmCommand.onTabComplete(sender, this, commandLabel, args);
            }
        });
    }

    public FilterConfig filterConfig() {
        return filterConfig;
    }

    public MessageService messages() {
        return messages;
    }

    public ChatFilter chatFilter() {
        return chatFilter;
    }

    public SpamTracker spamTracker() {
        return spamTracker;
    }

    public LogService logService() {
        return logService;
    }

    public GuiManager guiManager() {
        return guiManager;
    }

    public DomainService domainService() {
        return domainService;
    }

    public MuteService muteService() {
        return muteService;
    }

    public ConfigService configService() {
        return configService;
    }

    public PmService pmService() {
        return pmService;
    }

    public SoundService soundService() {
        return soundService;
    }

    public ViolationTracker violationTracker() {
        return violationTracker;
    }

    public DatabaseService databaseService() {
        return databaseService;
    }

    public ChatService chatService() {
        return chatService;
    }
}
