package com.maru.musiclive;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public final class CooldownGate {
    private final Map<String, Long> last = new HashMap<>();

    public synchronized boolean allow(String nickname, long nowMs, long cooldownMs) {
        String key = nickname == null ? "" : nickname.toLowerCase(Locale.ROOT);
        Long old = last.get(key);
        if (old != null && nowMs - old < cooldownMs) return false;
        last.put(key, nowMs);
        if (last.size() > 500) {
            Iterator<Map.Entry<String, Long>> iterator = last.entrySet().iterator();
            while (iterator.hasNext()) {
                if (nowMs - iterator.next().getValue() > 3_600_000L) {
                    iterator.remove();
                }
            }
        }
        return true;
    }
}
