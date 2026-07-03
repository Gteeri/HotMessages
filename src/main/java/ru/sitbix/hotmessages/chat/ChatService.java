package ru.sitbix.hotmessages.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.sitbix.hotmessages.HotMessagesPlugin;
import ru.sitbix.hotmessages.config.FilterConfig;

import java.util.Map;

public final class ChatService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final HotMessagesPlugin plugin;

    public ChatService(HotMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    private FilterConfig.ChatSettings settings() {
        return plugin.filterConfig().chatSettings();
    }

    public boolean enabled() {
        return settings().enabled();
    }

    public String globalPrefix() {
        return settings().globalPrefix();
    }

    public boolean isGlobal(String rawMessage) {
        return rawMessage.startsWith(globalPrefix());
    }

    public void sendLocal(Player sender, String rawMessage, String plainText) {
        Location senderLoc = sender.getLocation();
        double distance = settings().localDistance();
        String format = settings().localFormat();
        Component component = buildComponent(format, sender, plainMessage(rawMessage));

        for (Player recipient : plugin.getServer().getOnlinePlayers()) {
            if (recipient.getWorld().equals(sender.getWorld())
                    && recipient.getLocation().distanceSquared(senderLoc) <= distance * distance) {
                recipient.sendMessage(component);
            }
        }
    }

    public void sendGlobal(Player sender, String rawMessage) {
        String format = settings().globalFormat();
        Component component = buildComponent(format, sender, plainMessage(rawMessage));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    private Component buildComponent(String format, Player sender, String messageText) {
        String formatted = format
            .replace("{player}", sender.getName())
            .replace("{message}", messageText);
        return LEGACY.deserialize(formatted);
    }

    private String plainMessage(String rawMessage) {
        String text = rawMessage;
        if (text.startsWith(globalPrefix())) {
            text = text.substring(globalPrefix().length());
        }
        if (!settings().allowColorCodes()) {
            text = text.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
        }
        return text.trim();
    }

    public void sendToPlayer(Player player, String rawMessage) {
        String format = settings().localFormat();
        Component component = buildComponent(format, player, plainMessage(rawMessage));
        player.sendMessage(component);
    }
}
