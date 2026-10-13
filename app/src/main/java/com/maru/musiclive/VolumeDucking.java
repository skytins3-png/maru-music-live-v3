package com.maru.musiclive;

/**
 * Mix the application's own speech ducking independently of Android's
 * STREAM_MUSIC hardware volume. A timed duck always expires even when the
 * Activity that started the announcement is destroyed while BIGO is open.
 */
public final class VolumeDucking {
    private float guidance = 1f;
    private float host = 1f;
    private long guidanceExpiresAtMs;
    private long hostExpiresAtMs;

    public synchronized void setGuidance(float value) {
        guidance = clamp(value);
        guidanceExpiresAtMs = 0L;
    }

    public synchronized void setHost(float value) {
        host = clamp(value);
        hostExpiresAtMs = 0L;
    }

    public synchronized void setGuidanceTimed(float value, long nowMs, long maximumMs) {
        guidance = clamp(value);
        guidanceExpiresAtMs = guidance < 1f ? deadline(nowMs, maximumMs) : 0L;
    }

    public synchronized void setHostTimed(float value, long nowMs, long maximumMs) {
        host = clamp(value);
        hostExpiresAtMs = host < 1f ? deadline(nowMs, maximumMs) : 0L;
    }

    /** Restore only expired channels, never cancel an ongoing newer duck. */
    public synchronized boolean restoreExpired(long nowMs) {
        boolean changed = false;
        if (guidanceExpiresAtMs > 0L && nowMs >= guidanceExpiresAtMs) {
            guidance = 1f;
            guidanceExpiresAtMs = 0L;
            changed = true;
        }
        if (hostExpiresAtMs > 0L && nowMs >= hostExpiresAtMs) {
            host = 1f;
            hostExpiresAtMs = 0L;
            changed = true;
        }
        return changed;
    }

    /** 0 means no pending auto-restore. Uses monotonic elapsedRealtime. */
    public synchronized long nextExpiryMs() {
        if (guidanceExpiresAtMs == 0L) return hostExpiresAtMs;
        if (hostExpiresAtMs == 0L) return guidanceExpiresAtMs;
        return Math.min(guidanceExpiresAtMs, hostExpiresAtMs);
    }

    public synchronized void restoreAll() {
        guidance = 1f;
        host = 1f;
        guidanceExpiresAtMs = 0L;
        hostExpiresAtMs = 0L;
    }

    public synchronized float effective() {
        return Math.min(guidance, host);
    }

    private static long deadline(long nowMs, long durationMs) {
        long safeDuration = Math.max(1L, durationMs);
        return nowMs > Long.MAX_VALUE - safeDuration ? Long.MAX_VALUE
                : nowMs + safeDuration;
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) return 1f;
        return Math.max(0f, Math.min(1f, value));
    }
}
