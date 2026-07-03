package ru.sitbix.hotmessages.pm;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.sitbix.hotmessages.HotMessagesPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PmCommand implements CommandExecutor, TabCompleter {
    private final HotMessagesPlugin plugin;
    private final String alias;

    public PmCommand(HotMessagesPlugin plugin, String alias) {
        this.plugin = plugin;
        this.alias = alias;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().component("pm-console"));
            return true;
        }

        if (plugin.muteService().isMuted(player.getUniqueId())) {
            return true;
        }

        if (alias.equalsIgnoreCase("r") || alias.equalsIgnoreCase("reply")) {
            return handleReply(player, args);
        }

        return handlePm(player, args);
    }

    private boolean handlePm(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().component("pm-usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.messages().component("pm-target-offline"));
            return true;
        }

        if (target.equals(sender)) {
            sender.sendMessage(plugin.messages().component("pm-self"));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (plugin.filterConfig().settings().enabled()
                && plugin.filterConfig().settings().checkPrivateMessages()
                && !sender.hasPermission(plugin.filterConfig().settings().bypassPermission())) {
            ru.sitbix.hotmessages.filter.FilterResult result = plugin.chatFilter().check(message);
            if (result.blocked()) {
                sender.sendMessage(plugin.messages().component("blocked-player", Map.of("reason", result.reason())));
                plugin.soundService().play(sender, "blocked");
                return true;
            }
        }
        plugin.pmService().sendPm(sender, target, message);
        return true;
    }

    private boolean handleReply(Player sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.messages().component("pm-reply-usage"));
            return true;
        }

        String message = String.join(" ", args);
        if (plugin.filterConfig().settings().enabled()
                && plugin.filterConfig().settings().checkPrivateMessages()
                && !sender.hasPermission(plugin.filterConfig().settings().bypassPermission())) {
            ru.sitbix.hotmessages.filter.FilterResult result = plugin.chatFilter().check(message);
            if (result.blocked()) {
                sender.sendMessage(plugin.messages().component("blocked-player", Map.of("reason", result.reason())));
                plugin.soundService().play(sender, "blocked");
                return true;
            }
        }
        plugin.pmService().sendReply(sender, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (alias.equalsIgnoreCase("r") || alias.equalsIgnoreCase("reply")) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
        }

        return List.of();
    }
}
