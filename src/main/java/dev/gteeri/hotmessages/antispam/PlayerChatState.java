package dev.gteeri.hotmessages.antispam;

import java.util.concurrent.atomic.AtomicLong;

/** Лёгкое состояние одного игрока для антиспам-проверок. */
public final class PlayerChatState {
    volatile long lastMessageAt;
    volatile String lastNormalizedMessage = "";
    volatile int repeatCount;
    volatile long repeatWindowStart;
    final AtomicLong lastSeenAt = new AtomicLong(System.currentTimeMillis());

    void touch() {
        lastSeenAt.set(System.currentTimeMillis());
    }
}
