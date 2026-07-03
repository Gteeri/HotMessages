package ru.sitbix.hotmessages.filter;

import ru.sitbix.hotmessages.config.FilterConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class SpamTracker {
    private final Map<UUID, ArrayDeque<SeenMessage>> messages = new HashMap<>();
    private volatile FilterConfig config;

    public void configure(FilterConfig config) {
        this.config = config;
        synchronized (messages) {
            messages.clear();
        }
    }

    public FilterResult check(UUID playerUuid, String normalizedMessage) {
        FilterConfig activeConfig = config;
        if (activeConfig == null || !activeConfig.spam().enabled()) {
            return FilterResult.clean(normalizedMessage);
        }

        Instant now = Instant.now();
        String compact = normalizedMessage.replaceAll("[\\s._,;:/\\\\\\-+()\\[\\]{}<>\"'`~*#=]+", "");
        FilterConfig.Spam spam = activeConfig.spam();

        synchronized (messages) {
            ArrayDeque<SeenMessage> playerMessages = messages.computeIfAbsent(playerUuid, ignored -> new ArrayDeque<>());
            prune(playerMessages, now, spam.duplicateWindowSeconds());

            long burstCount = playerMessages.stream()
                .filter(seen -> Duration.between(seen.time(), now).getSeconds() <= spam.windowSeconds())
                .count();
            if (burstCount >= spam.maxMessagesPerWindow()) {
                playerMessages.addLast(new SeenMessage(now, compact));
                return FilterResult.blocked("spam:burst", "mass spam", normalizedMessage);
            }

            if (compact.length() >= spam.minMessageLengthForDuplicate()) {
                long duplicates = playerMessages.stream()
                    .filter(seen -> seen.compactMessage().equals(compact))
                    .count();
                if (duplicates >= spam.maxDuplicates()) {
                    playerMessages.addLast(new SeenMessage(now, compact));
                    return FilterResult.blocked("spam:duplicate", "duplicate spam", normalizedMessage);
                }
            }

            playerMessages.addLast(new SeenMessage(now, compact));
        }

        return FilterResult.clean(normalizedMessage);
    }

    public void cleanup(UUID uuid) {
        synchronized (messages) {
            messages.remove(uuid);
        }
    }

    private void prune(ArrayDeque<SeenMessage> playerMessages, Instant now, int maxAgeSeconds) {
        Iterator<SeenMessage> iterator = playerMessages.iterator();
        while (iterator.hasNext()) {
            SeenMessage seen = iterator.next();
            if (Duration.between(seen.time(), now).getSeconds() > maxAgeSeconds) {
                iterator.remove();
            }
        }
    }

    private record SeenMessage(Instant time, String compactMessage) {
    }
}
