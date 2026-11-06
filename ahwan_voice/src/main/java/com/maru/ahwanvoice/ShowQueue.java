package com.maru.ahwanvoice;

/**
 * Pure Java cue selection used by the Android live-show player.
 * Missing/unapproved media is never represented as a playable cue.
 */
public final class ShowQueue {
    public static final int CUE_COUNT = 6;

    private ShowQueue() {}

    public static int nextAvailable(boolean[] ready, int startAt) {
        if (ready == null || ready.length != CUE_COUNT) {
            throw new IllegalArgumentException("Six cue readiness flags required");
        }
        for (int i = Math.max(0, startAt); i < CUE_COUNT; i++) {
            if (ready[i]) return i;
        }
        return -1;
    }

    public static int registeredCount(boolean[] ready) {
        if (ready == null || ready.length != CUE_COUNT) {
            throw new IllegalArgumentException("Six cue readiness flags required");
        }
        int count = 0;
        for (boolean present : ready) if (present) count++;
        return count;
    }
}
