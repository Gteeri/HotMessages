package dev.gteeri.hotmessages.punishment;

import dev.gteeri.hotmessages.config.PluginConfig;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Считает предупреждения игроков за нарушения фильтра и включает эскалацию до мута. */
public final class WarnManager {

    private record WarnEntry(int count, long lastWarnAt, int muteEscalations) {
    }

    private final PluginConfig config;
    private final MuteManager muteManager;
    private final ConcurrentHashMap<UUID, WarnEntry> warns = new ConcurrentHashMap<>();

    public WarnManager(PluginConfig config, MuteManager muteManager) {
        this.config = config;
        this.muteManager = muteManager;
    }

    /** @return true, если по итогу этого предупреждения выдан мут */
    public boolean warn(UUID uuid) {
        if (!config.warningsEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long resetMillis = config.warningResetAfterMinutes() * 60_000L;

        WarnEntry previous = warns.get(uuid);
        int count;
        int escalations = previous == null ? 0 : previous.muteEscalations();
        if (previous == null || now - previous.lastWarnAt() > resetMillis) {
            count = 1;
        } else {
            count = previous.count() + 1;
        }

        boolean muted = false;
        if (count >= config.warningsBeforeMute()) {
            long durationMinutes = config.muteDurationMinutes();
            if (config.escalateMute()) {
                durationMinutes = Math.min(
                        config.maxMuteDurationMinutes(),
                        durationMinutes * (long) Math.pow(2, escalations));
            }
            muteManager.mute(uuid, durationMinutes * 60_000L);
            escalations++;
            count = 0; // сбрасываем счётчик после мута
            muted = true;
        }

        warns.put(uuid, new WarnEntry(count, now, escalations));
        return muted;
    }

    public int warningCount(UUID uuid) {
        WarnEntry entry = warns.get(uuid);
        return entry == null ? 0 : entry.count();
    }

    public void clear(UUID uuid) {
        warns.remove(uuid);
    }
}
