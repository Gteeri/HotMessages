package ru.sitbix.hotmessages.log;

import java.time.Instant;
import java.util.UUID;

public record LogEntry(
    Instant time,
    UUID playerUuid,
    String playerName,
    String channel,
    String message,
    String ruleId,
    String reason,
    String normalized
) {
    public String fileLine() {
        return "%s | %s | %s | %s | %s | %s | normalized=%s".formatted(
            time,
            playerName,
            channel,
            reason,
            ruleId,
            clean(message),
            clean(normalized)
        );
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
