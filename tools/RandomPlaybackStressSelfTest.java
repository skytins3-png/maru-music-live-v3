import com.maru.musiclive.RandomPlaybackGuard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class RandomPlaybackStressSelfTest {
    public static void main(String[] args) {
        enoughSongsStress();
        shortQueueNeverRepeatsInsideTwentyMinutes();
        duplicateEntriesAreOneTrack();
        fullCycleBeforeRepeat();
        System.out.println("RANDOM-PLAYBACK-STRESS: 1000/1000 + STRICT CASES PASS");
    }

    private static void enoughSongsStress() {
        List<String> queue = new ArrayList<>();
        for (int i = 0; i < 12; i++) queue.add("song:" + i);

        RandomPlaybackGuard guard = new RandomPlaybackGuard();
        Map<String, Long> observed = new HashMap<>();
        Random random = new Random(310L);
        long now = 10_000L;
        int current = 0;
        guard.markStarted(queue.get(current), now);
        observed.put(queue.get(current), now);

        int checks = 0;
        while (checks < 1000) {
            int next = guard.chooseNext(queue, current, random, now);
            if (next < 0 || next >= queue.size()) {
                throw new AssertionError("invalid index: " + next + " at " + checks);
            }
            if (next == current) {
                throw new AssertionError("immediate repeat at check " + checks);
            }
            String key = queue.get(next);
            Long old = observed.get(key);
            if (old != null && now - old < RandomPlaybackGuard.DEFAULT_COOLDOWN_MS) {
                throw new AssertionError(
                        "track repeated inside 20 minutes at check " + checks
                                + ": " + key + " elapsed=" + (now - old));
            }
            guard.markStarted(key, now);
            observed.put(key, now);
            current = next;
            now += 2L * 60L * 1000L;
            checks++;
        }
        if (checks != 1000) throw new AssertionError("expected 1000 checks");
    }

    private static void shortQueueNeverRepeatsInsideTwentyMinutes() {
        List<String> queue = new ArrayList<>();
        queue.add("short:0");
        queue.add("short:1");
        queue.add("short:2");
        RandomPlaybackGuard guard = new RandomPlaybackGuard();
        guard.markStarted(queue.get(0), 0L);
        guard.markStarted(queue.get(1), 120_000L);
        guard.markStarted(queue.get(2), 240_000L);

        int blocked = guard.chooseNext(queue, 2, new Random(1L), 360_000L);
        if (blocked != -1 || !guard.lastChoiceBlockedByCooldown()) {
            throw new AssertionError("short queue must wait instead of repeating: " + blocked);
        }
        if (guard.lastRequiredWaitMs() <= 0L) {
            throw new AssertionError("strict wait time missing");
        }

        long readyAt = 0L + RandomPlaybackGuard.DEFAULT_COOLDOWN_MS;
        int ready = guard.chooseNext(queue, 2, new Random(1L), readyAt);
        if (ready != 0) {
            throw new AssertionError("oldest eligible song should resume after cooldown: " + ready);
        }
    }

    private static void duplicateEntriesAreOneTrack() {
        for (int check = 0; check < 1000; check++) {
            List<String> keys = new ArrayList<>();
            keys.add("title:same song");
            keys.add("title:same song");
            keys.add("title:same song");
            keys.add("title:other song");
            RandomPlaybackGuard guard = new RandomPlaybackGuard();
            long now = 1_000L + check;
            guard.markStarted(keys.get(0), now);
            int next = guard.chooseNext(keys, 0, new Random(2L + check), now + 1_000L);
            if (next != 3) {
                throw new AssertionError(
                        "duplicate aliases must not replay the same song at check "
                                + check + ": " + next);
            }
            guard.markStarted(keys.get(next), now + 1_000L);
            int blocked = guard.chooseNext(keys, next, new Random(3L + check), now + 2_000L);
            if (blocked != -1 || !guard.lastChoiceBlockedByCooldown()) {
                throw new AssertionError(
                        "three duplicate entries must wait at check " + check);
            }
        }
        System.out.println("DUPLICATE-ALIAS-STRESS: 1000/1000");
    }

    private static void fullCycleBeforeRepeat() {
        List<String> queue = new ArrayList<>();
        for (int i = 0; i < 8; i++) queue.add("cycle:" + i);
        boolean[] seen = new boolean[8];
        RandomPlaybackGuard guard = new RandomPlaybackGuard();
        long now = 0L;
        int current = 0;
        seen[0] = true;
        guard.markStarted(queue.get(0), now);
        for (int i = 1; i < queue.size(); i++) {
            now += RandomPlaybackGuard.DEFAULT_COOLDOWN_MS;
            int next = guard.chooseNext(queue, current, new Random(i), now);
            if (next < 0 || seen[next]) {
                throw new AssertionError("repeat before full cycle at step " + i + ": " + next);
            }
            seen[next] = true;
            guard.markStarted(queue.get(next), now);
            current = next;
        }
        for (boolean value : seen) {
            if (!value) throw new AssertionError("cycle did not include every track");
        }
    }
}
