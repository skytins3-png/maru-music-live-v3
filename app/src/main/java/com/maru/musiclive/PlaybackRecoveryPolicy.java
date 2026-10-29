package com.maru.musiclive;

import java.util.Set;

/** Prevent endless retries of unavailable original audio files. */
public final class PlaybackRecoveryPolicy {
    private PlaybackRecoveryPolicy() {}

    /** Return the next non-failed song, or -1 when no playable option remains. */
    public static int nextPlayable(int queueSize, int failedIndex, Set<Integer> failed) {
        if (queueSize <= 0 || failed == null) return -1;
        for (int step = 1; step <= queueSize; step++) {
            int index = Math.floorMod(failedIndex + step, queueSize);
            if (!failed.contains(index)) return index;
        }
        return -1;
    }
}
