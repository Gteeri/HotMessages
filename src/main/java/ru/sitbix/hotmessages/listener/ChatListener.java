package ru.sitbix.hotmessages.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.sitbix.hotmessages.HotMessagesPlugin;
import ru.sitbix.hotmessages.escalation.ViolationTracker;
import ru.sitbix.hotmessages.filter.FilterResult;
import ru.sitbix.hotmessages.log.LogEntry;
import ru.sitbix.hotmessages.mute.MuteService;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public final class ChatListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final HotMessagesPlugin plugin;

    public ChatListener(HotMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (handleMuted(player)) {
            event.setCancelled(true);
            return;
        }

        String rawMessage = PLAIN.serialize(event.message());

        if (!plugin.filterConfig().settings().enabled() || !plugin.filterConfig().settings().checkPublicChat()) {
            if (plugin.chatService().enabled()) {
                event.setCancelled(true);
                plugin.chatService().sendLocal(player, rawMessage, rawMessage);
            }
            return;
        }

        FilterResult result = check(player, rawMessage, "public");
        if (result.blocked() && shouldCancel()) {
            event.setCancelled(true);
            return;
        }

        if (plugin.chatService().enabled()) {
            event.setCancelled(true);
            if (plugin.chatService().isGlobal(rawMessage)) {
                plugin.chatService().sendGlobal(player, rawMessage);
            } else {
                plugin.chatService().sendLocal(player, rawMessage, rawMessage);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String commandLine = event.getMessage();

        if (handleMuted(player) && isPmCommand(commandLine)) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.filterConfig().settings().enabled() || !plugin.filterConfig().settings().checkPrivateMessages()) {
            return;
        }

        String message = privateMessage(commandLine);
        if (message == null || message.isBlank()) {
            return;
        }

        FilterResult result = check(player, message, "private");
        if (result.blocked() && shouldCancel()) {
            event.setCancelled(true);
        }
    }

    private boolean isPmCommand(String commandLine) {
        String withoutSlash = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        String label = withoutSlash.split("\\s+")[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0 && namespace + 1 < label.length()) {
            label = label.substring(namespace + 1);
        }
        return plugin.filterConfig().privateMessageCommands().containsKey(label);
    }

    private boolean handleMuted(Player player) {
        if (!plugin.muteService().isMuted(player.getUniqueId())) {
            return false;
        }
        MuteService.MuteEntry mute = plugin.muteService().getMute(player.getUniqueId());
        if (mute == null) {
            return false;
        }
        String timeInfo;
        if (mute.isPermanent()) {
            timeInfo = "навсегда";
        } else {
            long remaining = mute.expiresAt() - Instant.now().getEpochSecond();
            timeInfo = formatDuration(remaining);
        }
        player.sendMessage(plugin.messages().component("muted-player", Map.of(
            "reason", mute.reason(),
            "time", timeInfo
        )));
        plugin.soundService().play(player, "muted");
        return true;
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) return "истекает";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("д ");
        if (hours > 0) sb.append(hours).append("ч ");
        if (minutes > 0) sb.append(minutes).append("м ");
        if (sb.isEmpty()) sb.append(secs).append("с");
        return sb.toString().trim();
    }

    private String formatMuteDuration(long seconds) {
        if (seconds <= 0) return "навсегда";
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes > 0 && secs > 0) return minutes + " мин " + secs + " сек";
        if (minutes > 0) return minutes + " мин";
        return secs + " сек";
    }

    private String privateMessage(String commandLine) {
        String withoutSlash = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        String[] parts = withoutSlash.split("\\s+");
        if (parts.length == 0) {
            return null;
        }

        String label = parts[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0 && namespace + 1 < label.length()) {
            label = label.substring(namespace + 1);
        }

        Integer messageStart = plugin.filterConfig().privateMessageCommands().get(label);
        if (messageStart == null || parts.length <= messageStart) {
            return null;
        }

        return String.join(" ", Arrays.copyOfRange(parts, messageStart, parts.length));
    }

    private FilterResult check(Player player, String message, String channel) {
        if (player.hasPermission(plugin.filterConfig().settings().bypassPermission())) {
            return FilterResult.clean(message);
        }

        FilterResult result = plugin.chatFilter().check(message);
        if (!result.blocked()) {
            result = plugin.spamTracker().check(player.getUniqueId(), result.normalized());
        }
        if (!result.blocked()) {
            return result;
        }

        if (shouldCancel()) {
            player.sendMessage(plugin.messages().component("blocked-player", Map.of("reason", result.reason())));
            plugin.soundService().play(player, "blocked");

            ViolationTracker tracker = plugin.violationTracker();
            ViolationTracker.EscalationResult escalation = tracker.addViolation(player.getUniqueId());
            if (escalation.shouldMute()) {
                plugin.muteService().mute(player.getUniqueId(), tracker.muteDuration(), tracker.muteReason(), "AutoMod");
                plugin.soundService().play(player, "muted");
                player.sendMessage(plugin.messages().component("escalation-auto-muted", Map.of(
                    "time", formatMuteDuration(tracker.muteDuration()),
                    "reason", tracker.muteReason()
                )));
            } else if (escalation.currentCount() > 1) {
                player.sendMessage(plugin.messages().component("escalation-warning", Map.of(
                    "count", String.valueOf(escalation.currentCount()),
                    "max", String.valueOf(escalation.maxViolations())
                )));
            }
        }
        plugin.logService().log(new LogEntry(
            Instant.now(),
            player.getUniqueId(),
            player.getName(),
            channel,
            message,
            result.ruleId(),
            result.reason(),
            result.normalized()
        ));
        notifyStaff(player, message, result);
        return result;
    }

    private boolean shouldCancel() {
        return !plugin.filterConfig().settings().blockAction().equalsIgnoreCase("log-only");
    }

    private void notifyStaff(Player offender, String message, FilterResult result) {
        if (!plugin.filterConfig().settings().notifyStaff()) {
            return;
        }

        Map<String, String> placeholders = Map.of(
            "player", offender.getName(),
            "reason", result.reason(),
            "message", message
        );

        for (Player staff : plugin.getServer().getOnlinePlayers()) {
            if (staff.hasPermission(plugin.filterConfig().settings().notifyPermission())) {
                staff.sendMessage(plugin.messages().component("blocked-staff", placeholders));
            }
        }
    }
}
