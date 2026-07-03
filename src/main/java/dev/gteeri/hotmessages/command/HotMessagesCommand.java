package dev.gteeri.hotmessages.command;

import dev.gteeri.hotmessages.HotMessages;
import dev.gteeri.hotmessages.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** /hotmessages <reload|toggle|addword|removeword|warnings|clearwarnings|mute|unmute> */
public final class HotMessagesCommand implements TabExecutor {

    private final HotMessages plugin;

    public HotMessagesCommand(HotMessages plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, String[] args) {
        PluginConfig config = plugin.pluginConfig();

        if (!sender.hasPermission("hotmessages.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(config.msg("usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(config.msg("reload-success"));
            }
            case "toggle" -> {
                boolean next = !config.enabled();
                config.setEnabled(next);
                sender.sendMessage(config.msg(next ? "toggle-on" : "toggle-off"));
            }
            case "addword" -> {
                if (args.length < 2) {
                    sender.sendMessage(config.msg("usage-addword"));
                    return true;
                }
                String word = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                config.addBannedWord(word);
                plugin.wordFilter().rebuild(config.bannedWords());
                sender.sendMessage(config.msg("word-added", "%word%", word));
            }
            case "removeword" -> {
                if (args.length < 2) {
                    sender.sendMessage(config.msg("usage-removeword"));
                    return true;
                }
                String word = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (config.removeBannedWord(word)) {
                    plugin.wordFilter().rebuild(config.bannedWords());
                    sender.sendMessage(config.msg("word-removed", "%word%", word));
                } else {
                    sender.sendMessage(config.msg("word-not-found", "%word%", word));
                }
            }
            case "warnings" -> {
                if (args.length < 2) {
                    sender.sendMessage(config.msg("usage-warnings"));
                    return true;
                }
                UUID uuid = resolveUuid(args[1]);
                if (uuid == null) {
                    sender.sendMessage(config.msg("player-not-found", "%player%", args[1]));
                    return true;
                }
                int count = plugin.warnManager().warningCount(uuid);
                sender.sendMessage(config.msg("warnings-count", "%player%", args[1], "%amount%", String.valueOf(count)));
            }
            case "clearwarnings" -> {
                if (args.length < 2) {
                    sender.sendMessage(config.msg("usage-clearwarnings"));
                    return true;
                }
                UUID uuid = resolveUuid(args[1]);
                if (uuid == null) {
                    sender.sendMessage(config.msg("player-not-found", "%player%", args[1]));
                    return true;
                }
                plugin.warnManager().clear(uuid);
                sender.sendMessage(config.msg("warnings-cleared", "%player%", args[1]));
            }
            case "mute" -> {
                if (args.length < 2) {
                    sender.sendMessage(config.msg("usage-mute"));
                    return true;
                }
                UUID uuid = resolveUuid(args[1]);
                if (uuid == null) {
                    sender.sendMessage(config.msg("player-not-found", "%player%", args[1]));
                    return true;
                }
                long minutes = args.length >= 3
                        ? parseLongOrDefault(args[2], config.muteDurationMinutes())
                        : config.muteDurationMinutes();
                plugin.muteManager().mute(uuid, minutes * 60_000L);
                sender.sendMessage(config.msg("mute-success", "%player%", args[1], "%time%", String.valueOf(minutes)));
            }
            case "unmute" -> {
                if (args.length < 2) {
                    sender.sendMessage(config.msg("usage-unmute"));
                    return true;
                }
                UUID uuid = resolveUuid(args[1]);
                if (uuid == null) {
                    sender.sendMessage(config.msg("player-not-found", "%player%", args[1]));
                    return true;
                }
                plugin.muteManager().unmute(uuid);
                sender.sendMessage(config.msg("unmute-success", "%player%", args[1]));
            }
            default -> sender.sendMessage(config.msg("usage"));
        }
        return true;
    }

    private long parseLongOrDefault(String s, long def) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // Ищем среди онлайн-игроков сначала; если не найден — пробуем OfflinePlayer
    // (может на мгновение сходить в сеть для незакэшированного имени — допустимо для редких админ-команд).
    private UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "toggle", "addword", "removeword",
                    "warnings", "clearwarnings", "mute", "unmute"), args[0]);
        }
        if (args.length == 2 && List.of("warnings", "clearwarnings", "mute", "unmute").contains(args[0].toLowerCase())) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(prefix.toLowerCase())) result.add(option);
        }
        return result;
    }
}
