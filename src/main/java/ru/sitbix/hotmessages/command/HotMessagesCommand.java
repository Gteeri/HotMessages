package ru.sitbix.hotmessages.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.sitbix.hotmessages.HotMessagesPlugin;
import ru.sitbix.hotmessages.filter.FilterResult;
import ru.sitbix.hotmessages.log.LogEntry;
import ru.sitbix.hotmessages.mute.MuteService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HotMessagesCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("gui", "reload", "toggle", "logs", "test", "help", "ghost", "mute", "unmute");
    private static final List<String> MUTE_SUBS = List.of("mute", "unmute");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final HotMessagesPlugin plugin;

    public HotMessagesCommand(HotMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(plugin.filterConfig().settings().adminPermission())) {
            sender.sendMessage(plugin.messages().component("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player player) {
                plugin.guiManager().openMain(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "reload" -> reload(sender);
            case "toggle" -> toggle(sender);
            case "logs" -> logs(sender);
            case "test" -> test(sender, args);
            case "mute" -> mute(sender, args);
            case "unmute" -> unmute(sender, args);
            case "ghost" -> ghost(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(plugin.filterConfig().settings().adminPermission())) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> all = new ArrayList<>(SUBCOMMANDS);
            all.addAll(MUTE_SUBS);
            return all.stream().filter(value -> value.startsWith(prefix)).toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("mute") || sub.equals("unmute")) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
            }
        }

        return List.of();
    }

    private void reload(CommandSender sender) {
        plugin.reloadHotMessages();
        sender.sendMessage(plugin.messages().component("reloaded"));
    }

    private void toggle(CommandSender sender) {
        boolean enabled = plugin.getConfig().getBoolean("settings.enabled", true);
        plugin.getConfig().set("settings.enabled", !enabled);
        plugin.saveConfig();
        plugin.reloadHotMessages();
        sender.sendMessage(plugin.messages().component(!enabled ? "enabled" : "disabled"));
    }

    private void logs(CommandSender sender) {
        List<LogEntry> logs = plugin.logService().recent();
        if (logs.isEmpty()) {
            sender.sendMessage(plugin.messages().component("logs-empty"));
            return;
        }

        int count = Math.min(10, logs.size());
        for (int index = 0; index < count; index++) {
            LogEntry log = logs.get(index);
            sender.sendMessage(plugin.messages().rawComponent(
                "&8[" + TIME_FORMAT.format(log.time()) + "] &e" + log.playerName()
                    + " &7(" + log.channel() + ", &c" + log.reason() + "&7): &f" + trim(log.message(), 90)
            ));
        }
    }

    private void test(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().rawComponent("&e/" + argsLabel(sender) + " test <сообщение>"));
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        FilterResult result = plugin.chatFilter().check(message);
        if (result.blocked()) {
            sender.sendMessage(plugin.messages().component("test-blocked", Map.of("reason", result.reason())));
        } else {
            sender.sendMessage(plugin.messages().component("test-clean"));
        }
    }

    private void mute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().rawComponent("&e/mute <игрок> [время: 10m, 1h, 1d] [причина]"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.messages().component("mute-player-not-found", Map.of("player", args[1])));
            return;
        }

        long duration = 600;
        String reason = "Нарушение правил чата";
        String muter = sender.getName();

        if (args.length >= 3) {
            duration = parseDuration(args[2]);
        }
        if (args.length >= 4) {
            reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        }

        plugin.muteService().mute(target.getUniqueId(), duration, reason, muter);

        String timeStr = duration <= 0 ? "навсегда" : formatDuration(duration);
        Map<String, String> placeholders = Map.of(
            "player", target.getName(),
            "time", timeStr,
            "reason", reason
        );
        sender.sendMessage(plugin.messages().component("mute-success", placeholders));
        target.sendMessage(plugin.messages().component("mute-player", placeholders));
        plugin.soundService().play(target, "muted");
    }

    private void unmute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().rawComponent("&e/unmute <игрок>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.messages().component("mute-player-not-found", Map.of("player", args[1])));
            return;
        }

        if (plugin.muteService().unmute(target.getUniqueId())) {
            sender.sendMessage(plugin.messages().component("unmute-success", Map.of("player", target.getName())));
            target.sendMessage(plugin.messages().component("unmute-player"));
            plugin.soundService().play(target, "unmuted");
        } else {
            sender.sendMessage(plugin.messages().component("mute-not-muted", Map.of("player", target.getName())));
        }
    }

    private void ghost(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().component("pm-console"));
            return;
        }
        plugin.pmService().toggleGhost(player);
    }

    private long parseDuration(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.equals("0") || lower.equals("permanent") || lower.equals("perm") || lower.equals("навсегда")) {
            return -1;
        }
        try {
            long value = Long.parseLong(lower.replaceAll("[^0-9]", ""));
            if (lower.endsWith("s")) return value;
            if (lower.endsWith("m")) return value * 60;
            if (lower.endsWith("h")) return value * 3600;
            if (lower.endsWith("d")) return value * 86400;
            return value * 60;
        } catch (NumberFormatException e) {
            return 600;
        }
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) return "навсегда";
        if (seconds < 60) return seconds + "с";
        if (seconds < 3600) return (seconds / 60) + "м";
        if (seconds < 86400) return (seconds / 3600) + "ч";
        return (seconds / 86400) + "д";
    }

    private void sendHelp(CommandSender sender) {
        List<String> lines = new ArrayList<>();
        lines.add("&6&lHotMessages &8» &fКоманды:");
        lines.add("&e/hm gui &7- открыть GUI");
        lines.add("&e/hm reload &7- перезагрузить конфиг");
        lines.add("&e/hm toggle &7- включить/выключить фильтр");
        lines.add("&e/hm logs &7- последние блокировки");
        lines.add("&e/hm test <текст> &7- проверить сообщение");
        lines.add("&e/hm ghost &7- режим слежки за ЛС");
        lines.add("&e/mute <игрок> [время] [причина] &7- замутить");
        lines.add("&e/unmute <игрок> &7- размутить");
        lines.add("&7--- &fЛС &7---");
        lines.add("&e/msg <игрок> <сообщение> &7- личное сообщение");
        lines.add("&e/r <сообщение> &7- ответ на ЛС");
        for (String line : lines) {
            sender.sendMessage(plugin.messages().rawComponent(line));
        }
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private String argsLabel(CommandSender sender) {
        return sender instanceof Player ? "hm" : "hotmessages";
    }
}
