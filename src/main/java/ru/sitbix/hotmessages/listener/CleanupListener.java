package ru.sitbix.hotmessages.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.sitbix.hotmessages.HotMessagesPlugin;

import java.util.UUID;

public final class CleanupListener implements Listener {
    private final HotMessagesPlugin plugin;

    public CleanupListener(HotMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.spamTracker().cleanup(uuid);
        plugin.violationTracker().cleanup(uuid);
        plugin.pmService().cleanup(uuid);
        plugin.guiManager().cancelInput(uuid);
    }
}
