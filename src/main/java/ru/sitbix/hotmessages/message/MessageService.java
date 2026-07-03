package ru.sitbix.hotmessages.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

public final class MessageService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Map<String, String> messages;

    public MessageService(Map<String, String> messages) {
        this.messages = messages;
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public Component component(String key, Map<String, String> placeholders) {
        return LEGACY.deserialize(text(key, placeholders));
    }

    public String text(String key) {
        return text(key, Map.of());
    }

    public String rawText(String key) {
        return messages.getOrDefault(key, key);
    }

    public String text(String key, Map<String, String> placeholders) {
        String prefix = messages.getOrDefault("prefix", "");
        String raw = messages.getOrDefault(key, key);
        String output = prefix + raw;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            output = output.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return output;
    }

    public Component rawComponent(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }
}
