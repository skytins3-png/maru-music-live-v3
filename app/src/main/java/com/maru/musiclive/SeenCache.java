package com.maru.musiclive;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SeenCache {
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();
    private final int maxEntries;

    public SeenCache(int maxEntries) {
        this.maxEntries = Math.max(20, maxEntries);
    }

    public synchronized boolean markIfNew(String key, long nowMs, long ttlMs) {
        prune(nowMs, ttlMs);
        Long previous = seen.get(key);
        if (previous != null && nowMs - previous < ttlMs) return false;
        seen.put(key, nowMs);
        while (seen.size() > maxEntries) {
            Iterator<String> iterator = seen.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        return true;
    }

    private void prune(long nowMs, long ttlMs) {
        Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (nowMs - entry.getValue() >= ttlMs) iterator.remove();
        }
    }
}
