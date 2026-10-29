package com.maru.musiclive;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class PlaybackRecoveryPolicyTest {
    @Test public void skipsBrokenFilesWithoutRetryLoop() {
        Set<Integer> bad = new HashSet<>();
        bad.add(0);
        assertEquals(1, PlaybackRecoveryPolicy.nextPlayable(3, 0, bad));
        bad.add(1);
        assertEquals(2, PlaybackRecoveryPolicy.nextPlayable(3, 1, bad));
        bad.add(2);
        assertEquals(-1, PlaybackRecoveryPolicy.nextPlayable(3, 2, bad));
    }
    @Test public void handlesSingleSongAndWraparound() {
        assertEquals(-1, PlaybackRecoveryPolicy.nextPlayable(1, 0,
                new HashSet<>(Arrays.asList(0))));
        assertEquals(0, PlaybackRecoveryPolicy.nextPlayable(3, 2,
                new HashSet<>(Arrays.asList(1, 2))));
        assertEquals(-1, PlaybackRecoveryPolicy.nextPlayable(0, 0,
                new HashSet<>()));
    }
}
