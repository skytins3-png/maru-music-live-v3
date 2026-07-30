package com.maru.musiclive;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class LiveEventCooldown {
    private final Map<String, Long> seen = new HashMap<>();

    public synchronized boolean markIfNew(LiveEvent event, long nowMs) {
        if (event == null) return false;
        long cooldown = cooldownFor(event.type);
        String key = event.fingerprint();
        Long previous = seen.get(key);
        if (previous != null && nowMs - previous < cooldown) return false;
        seen.put(key, nowMs);
        cleanup(nowMs);
        return true;
    }

    public static long cooldownFor(EventType type) {
        if (type == EventType.LIKE) return 20_000L;
        if (type == EventType.GIFT) return 2_000L;
        if (type == EventType.FOLLOW) return 300_000L;
        if (type == EventType.CHAT) return 45_000L;
        return 60_000L;
    }

    private void cleanup(long nowMs) {
        Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().getValue() > 900_000L) iterator.remove();
        }
        if (seen.size() > 1_000) seen.clear();
    }
}
