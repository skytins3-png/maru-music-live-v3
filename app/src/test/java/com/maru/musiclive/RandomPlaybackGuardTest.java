package com.maru.musiclive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public final class RandomPlaybackGuardTest {
    @Test public void avoidsSameTrackForTwentyMinutesWhenEnoughSongsExist() {
        RandomPlaybackGuard guard = new RandomPlaybackGuard();
        List<String> queue = tracks(12);
        long now = 1_000_000L;
        int current = 0;
        guard.markStarted(queue.get(current), now);

        for (int i = 0; i < 200; i++) {
            int next = guard.chooseNext(queue, current, new Random(1000L + i), now);
            assertTrue(next >= 0);
            assertFalse(guard.wasPlayedWithin(queue.get(next), now));
            guard.markStarted(queue.get(next), now);
            current = next;
            now += 2L * 60L * 1000L;
        }
    }

    @Test public void shortQueueWaitsInsteadOfBreakingCooldown() {
        RandomPlaybackGuard guard = new RandomPlaybackGuard();
        List<String> queue = tracks(3);
        guard.markStarted(queue.get(0), 0L);
        guard.markStarted(queue.get(1), 120_000L);
        guard.markStarted(queue.get(2), 240_000L);

        assertEquals(-1, guard.chooseNext(queue, 2, new Random(1L), 360_000L));
        assertTrue(guard.lastChoiceBlockedByCooldown());
        assertTrue(guard.lastRequiredWaitMs() > 0L);
    }

    @Test public void duplicateKeysCannotCauseTripleReplay() {
        RandomPlaybackGuard guard = new RandomPlaybackGuard();
        List<String> queue = new ArrayList<>();
        queue.add("title:same");
        queue.add("title:same");
        queue.add("title:other");
        guard.markStarted(queue.get(0), 0L);
        assertEquals(2, guard.chooseNext(queue, 0, new Random(2L), 1_000L));
        guard.markStarted(queue.get(2), 1_000L);
        assertEquals(-1, guard.chooseNext(queue, 2, new Random(3L), 2_000L));
    }

    @Test public void doesNotRepeatBeforeAFullRandomCycle() {
        RandomPlaybackGuard guard = new RandomPlaybackGuard();
        List<String> queue = tracks(8);
        boolean[] seen = new boolean[8];
        long now = 0L;
        int current = 0;
        seen[current] = true;
        guard.markStarted(queue.get(current), now);

        for (int i = 1; i < queue.size(); i++) {
            now += RandomPlaybackGuard.DEFAULT_COOLDOWN_MS;
            int next = guard.chooseNext(queue, current, new Random(i), now);
            assertTrue(next >= 0);
            assertFalse(seen[next]);
            seen[next] = true;
            guard.markStarted(queue.get(next), now);
            current = next;
        }
        for (boolean value : seen) assertTrue(value);
    }

    private static List<String> tracks(int count) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add("song:" + i);
        return out;
    }
}
