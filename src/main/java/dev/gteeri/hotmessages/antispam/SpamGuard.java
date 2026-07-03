package dev.gteeri.hotmessages.antispam;

import dev.gteeri.hotmessages.config.PluginConfig;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Антиспам/антифлуд: ограничивает частоту сообщений, повторяющиеся сообщения подряд,
 * КАПС и растянутые повторяющимися буквами слова. Все пороги настраиваются в config.yml,
 * чтобы баланс между защитой и удобством игроков подбирался под конкретный сервер.
 */
public final class SpamGuard {

    public enum Verdict {ALLOW, BLOCK_COOLDOWN, BLOCK_DUPLICATE}

    private final PluginConfig config;
    private final ConcurrentHashMap<UUID, PlayerChatState> states = new ConcurrentHashMap<>();

    public SpamGuard(PluginConfig config) {
        this.config = config;
    }

    private PlayerChatState state(UUID uuid) {
        return states.computeIfAbsent(uuid, k -> new PlayerChatState());
    }

    public Verdict check(UUID uuid, String rawMessage) {
        PlayerChatState state = state(uuid);
        state.touch();
        long now = System.currentTimeMillis();

        if (now - state.lastMessageAt < config.messageCooldownMs()) {
            return Verdict.BLOCK_COOLDOWN;
        }

        String normalized = rawMessage.strip().toLowerCase();
        long windowMillis = config.duplicateWindowSeconds() * 1000L;
        if (normalized.equals(state.lastNormalizedMessage) && now - state.repeatWindowStart < windowMillis) {
            state.repeatCount++;
        } else {
            state.repeatCount = 1;
            state.repeatWindowStart = now;
        }
        state.lastNormalizedMessage = normalized;
        state.lastMessageAt = now;

        if (state.repeatCount >= config.duplicateLimit()) {
            return Verdict.BLOCK_DUPLICATE;
        }
        return Verdict.ALLOW;
    }

    /** Смягчает капс и растянутые буквы, не блокируя сообщение целиком (меньше раздражает игроков). */
    public String soften(String message) {
        String result = message;
        if (config.floodCharsEnabled()) {
            result = collapseRepeats(result, config.floodMaxRepeats());
        }
        if (config.capsEnabled() && result.length() >= config.capsMinLength()) {
            long letters = result.chars().filter(Character::isLetter).count();
            long upper = result.chars().filter(Character::isUpperCase).count();
            if (letters > 0 && (upper * 100.0 / letters) > config.capsMaxPercentage()) {
                result = result.toLowerCase();
            }
        }
        return result;
    }

    private String collapseRepeats(String text, int maxRepeats) {
        if (maxRepeats <= 0) return text;
        StringBuilder sb = new StringBuilder(text.length());
        int count = 0;
        char last = '\0';
        for (char c : text.toCharArray()) {
            if (Character.toLowerCase(c) == Character.toLowerCase(last)) {
                count++;
            } else {
                count = 1;
                last = c;
            }
            if (count <= maxRepeats) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Периодическая уборка неактивных игроков — экономит память на больших серверах. */
    public void cleanup(Predicate<UUID> isOnline) {
        long forgetAfter = config.forgetAfterMinutes() * 60_000L;
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(entry ->
                !isOnline.test(entry.getKey()) && now - entry.getValue().lastSeenAt.get() > forgetAfter);
    }

    public void forget(UUID uuid) {
        states.remove(uuid);
    }
}
