package ru.sitbix.hotmessages.pm;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sitbix.hotmessages.HotMessagesPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PmService {
    private final HotMessagesPlugin plugin;
    private final Map<UUID, UUID> replyTargets = new ConcurrentHashMap<>();
    private final Set<UUID> ghostMode = ConcurrentHashMap.newKeySet();

    public PmService(HotMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendPm(Player sender, Player target, String message) {
        replyTargets.put(target.getUniqueId(), sender.getUniqueId());
        replyTargets.put(sender.getUniqueId(), target.getUniqueId());

        sender.sendMessage(plugin.messages().component("pm-sent", Map.of(
            "target", target.getName(),
            "message", message
        )));
        plugin.soundService().play(sender, "pm-send");

        target.sendMessage(plugin.messages().component("pm-received", Map.of(
            "sender", sender.getName(),
            "message", message
        )));
        plugin.soundService().play(target, "pm-receive");

        notifyGhosts(sender, target, message);
    }

    public boolean sendReply(Player sender, String message) {
        UUID targetId = replyTargets.get(sender.getUniqueId());
        if (targetId == null) {
            sender.sendMessage(plugin.messages().component("pm-no-reply"));
            return false;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.messages().component("pm-target-offline"));
            return false;
        }

        sendPm(sender, target, message);
        return true;
    }

    public UUID getReplyTarget(Player player) {
        return replyTargets.get(player.getUniqueId());
    }

    public void toggleGhost(Player player) {
        if (ghostMode.remove(player.getUniqueId())) {
            player.sendMessage(plugin.messages().component("pm-ghost-disabled"));
        } else {
            ghostMode.add(player.getUniqueId());
            player.sendMessage(plugin.messages().component("pm-ghost-enabled"));
        }
    }

    public boolean isGhost(Player player) {
        return ghostMode.contains(player.getUniqueId());
    }

    public Set<UUID> ghosts() {
        return ghostMode;
    }

    public void cleanup(UUID uuid) {
        replyTargets.remove(uuid);
        ghostMode.remove(uuid);
    }

    private void notifyGhosts(Player sender, Player target, String message) {
        for (UUID ghostId : ghostMode) {
            if (ghostId.equals(sender.getUniqueId()) || ghostId.equals(target.getUniqueId())) {
                continue;
            }
            Player ghost = Bukkit.getPlayer(ghostId);
            if (ghost != null && ghost.isOnline()) {
                ghost.sendMessage(plugin.messages().component("pm-ghost-see", Map.of(
                    "sender", sender.getName(),
                    "target", target.getName(),
                    "message", message
                )));
            }
        }
    }
}
