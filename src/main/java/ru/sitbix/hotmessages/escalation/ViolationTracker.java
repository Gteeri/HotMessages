package ru.sitbix.hotmessages.escalation;

import org.bukkit.configuration.ConfigurationSection;
import ru.sitbix.hotmessages.HotMessagesPlugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ViolationTracker {
    private final HotMessagesPlugin plugin;
    private volatile boolean enabled;
    private volatile int maxViolations;
    private volatile long windowSeconds;
    private volatile long muteDuration;
    private volatile String muteReason;
    private final Map<UUID, List<Long>> violations = new LinkedHashMap<>();

    public ViolationTracker(HotMessagesPlugin plugin) {
        this.plugin = plugin;
        configure();
    }

    public void configure() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("escalation");
        if (section == null) {
            enabled = false;
            return;
        }
        enabled = section.getBoolean("enabled", true);
        maxViolations = Math.max(1, section.getInt("max-violations", 3));
        windowSeconds = Math.max(10, section.getInt("window-seconds", 3600));
        muteDuration = Math.max(-1, section.getInt("mute-duration-seconds", 120));
        muteReason = section.getString("mute-reason", "Многократные нарушения правил чата");
        synchronized (violations) {
            violations.clear();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public record EscalationResult(boolean shouldMute, int currentCount, int maxViolations) {
    }

    public EscalationResult addViolation(UUID uuid) {
        if (!enabled) {
            return new EscalationResult(false, 0, maxViolations);
        }

        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        synchronized (violations) {
            List<Long> times = violations.computeIfAbsent(uuid, k -> new ArrayList<>());
            Iterator<Long> it = times.iterator();
            while (it.hasNext()) {
                if (now - it.next() > windowMs) {
                    it.remove();
                }
            }
            times.add(now);
            int count = times.size();
            if (count >= maxViolations) {
                times.clear();
                return new EscalationResult(true, count, maxViolations);
            }
            return new EscalationResult(false, count, maxViolations);
        }
    }

    public int getViolationCount(UUID uuid) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        synchronized (violations) {
            List<Long> times = violations.get(uuid);
            if (times == null) return 0;
            times.removeIf(t -> now - t > windowMs);
            return times.size();
        }
    }

    public void cleanup(UUID uuid) {
        synchronized (violations) {
            violations.remove(uuid);
        }
    }

    public long muteDuration() {
        return muteDuration;
    }

    public String muteReason() {
        return muteReason;
    }

    public long windowSeconds() {
        return windowSeconds;
    }
}
