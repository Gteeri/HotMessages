package ru.sitbix.hotmessages.mute;

import ru.sitbix.hotmessages.HotMessagesPlugin;
import ru.sitbix.hotmessages.database.DatabaseService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MuteService {
    private final HotMessagesPlugin plugin;
    private final Map<UUID, MuteEntry> muted = new ConcurrentHashMap<>();

    public MuteService(HotMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        muted.clear();
        DatabaseService db = plugin.databaseService();
        if (db == null) return;

        Map<UUID, DatabaseService.MuteData> loaded = db.loadAllMutes();
        long now = Instant.now().getEpochSecond();
        for (Map.Entry<UUID, DatabaseService.MuteData> entry : loaded.entrySet()) {
            DatabaseService.MuteData data = entry.getValue();
            // Skip expired mutes
            if (data.expiresAt() > 0 && now > data.expiresAt()) {
                db.removeMute(entry.getKey());
                continue;
            }
            muted.put(entry.getKey(), new MuteEntry(data.expiresAt(), data.reason(), data.muter()));
        }
    }

    public void mute(UUID uuid, long durationSeconds, String reason, String muter) {
        long expiresAt = durationSeconds <= 0 ? -1 : Instant.now().getEpochSecond() + durationSeconds;
        muted.put(uuid, new MuteEntry(expiresAt, reason, muter));

        DatabaseService db = plugin.databaseService();
        if (db != null) {
            db.saveMute(uuid, expiresAt, reason, muter);
        }
    }

    public boolean unmute(UUID uuid) {
        boolean removed = muted.remove(uuid) != null;
        if (removed) {
            DatabaseService db = plugin.databaseService();
            if (db != null) {
                db.removeMute(uuid);
            }
        }
        return removed;
    }

    public boolean isMuted(UUID uuid) {
        MuteEntry entry = muted.get(uuid);
        if (entry == null) {
            return false;
        }
        if (entry.expiresAt() > 0 && Instant.now().getEpochSecond() > entry.expiresAt()) {
            muted.remove(uuid);
            DatabaseService db = plugin.databaseService();
            if (db != null) {
                db.removeMute(uuid);
            }
            return false;
        }
        return true;
    }

    public MuteEntry getMute(UUID uuid) {
        return muted.get(uuid);
    }

    public List<MuteEntry> all() {
        return Collections.unmodifiableList(new ArrayList<>(muted.values()));
    }

    public record MuteEntry(long expiresAt, String reason, String muter) {
        public boolean isPermanent() {
            return expiresAt <= 0;
        }

        public boolean isExpired() {
            return expiresAt > 0 && Instant.now().getEpochSecond() > expiresAt;
        }
    }
}
