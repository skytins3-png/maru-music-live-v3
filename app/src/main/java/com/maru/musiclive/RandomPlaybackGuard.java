package com.maru.musiclive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 랜덤 재생 중복을 엄격하게 차단한다.
 *
 * <p>규칙:
 * 1) 모든 서로 다른 곡이 한 번씩 나오기 전에는 이미 나온 곡을 다시 선택하지 않는다.
 * 2) 같은 곡은 마지막 재생 시작 후 20분 동안 절대 다시 선택하지 않는다.
 * 3) 같은 곡이 목록에 여러 번 들어 있어도 하나의 곡으로 취급한다.
 * 4) 조건을 만족하는 곡이 없으면 중복 재생하지 않고, 가장 먼저 보호가 풀리는 시점까지 대기한다.
 */
public final class RandomPlaybackGuard {
    public static final long DEFAULT_COOLDOWN_MS = 20L * 60L * 1000L;
    private static final long MAX_HISTORY_AGE_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_HISTORY_ITEMS = 1000;

    private final long cooldownMs;
    private final Map<String, Long> lastPlayedAt = new LinkedHashMap<>();
    private final Set<String> playedInCycle = new HashSet<>();
    private boolean lastChoiceBlockedByCooldown;
    private long lastRequiredWaitMs;

    public RandomPlaybackGuard() {
        this(DEFAULT_COOLDOWN_MS);
    }

    RandomPlaybackGuard(long cooldownMs) {
        this.cooldownMs = Math.max(0L, cooldownMs);
    }

    public synchronized void restore(Map<String, Long> history, Set<String> cycle) {
        lastPlayedAt.clear();
        playedInCycle.clear();
        if (history != null) {
            for (Map.Entry<String, Long> entry : history.entrySet()) {
                String key = cleanKey(entry.getKey());
                Long time = entry.getValue();
                if (!key.isEmpty() && time != null && time >= 0L) {
                    lastPlayedAt.put(key, time);
                }
            }
        }
        if (cycle != null) {
            for (String value : cycle) {
                String key = cleanKey(value);
                if (!key.isEmpty()) playedInCycle.add(key);
            }
        }
    }

    public synchronized void pruneToQueue(List<String> trackKeys, long nowMs) {
        Set<String> valid = uniqueKeys(trackKeys);
        lastPlayedAt.entrySet().removeIf(entry ->
                !valid.contains(entry.getKey())
                        || elapsed(nowMs, entry.getValue()) > MAX_HISTORY_AGE_MS);
        playedInCycle.removeIf(key ->
                !valid.contains(key) || !lastPlayedAt.containsKey(key));
        trimHistory();
    }

    public synchronized void markStarted(String trackKey, long nowMs) {
        String key = cleanKey(trackKey);
        if (key.isEmpty()) return;
        lastPlayedAt.put(key, Math.max(0L, nowMs));
        playedInCycle.add(key);
        trimHistory();
    }

    /**
     * @return 재생할 원본 목록 인덱스. 조건을 만족하는 곡이 없으면 -1.
     */
    public synchronized int chooseNext(
            List<String> trackKeys,
            int currentIndex,
            Random random,
            long nowMs) {
        lastChoiceBlockedByCooldown = false;
        lastRequiredWaitMs = 0L;
        if (trackKeys == null || trackKeys.isEmpty()) return -1;

        Random source = random == null ? new Random(0L) : random;
        pruneToQueue(trackKeys, nowMs);

        Set<String> unique = uniqueKeys(trackKeys);
        if (unique.isEmpty()) return -1;
        String currentKey = currentIndex >= 0 && currentIndex < trackKeys.size()
                ? cleanKey(trackKeys.get(currentIndex))
                : "";

        // 한 곡만 있거나 같은 곡이 여러 번 등록된 경우에도 20분을 지킨다.
        if (unique.size() == 1) {
            String onlyKey = unique.iterator().next();
            if (wasPlayedWithin(onlyKey, nowMs)) {
                blockUntilAvailable(trackKeys, currentIndex, nowMs, false);
                return -1;
            }
            return firstIndexOf(trackKeys, onlyKey);
        }

        List<Integer> candidates = collectCandidates(
                trackKeys, currentKey, true, true, nowMs);
        if (!candidates.isEmpty()) {
            return candidates.get(source.nextInt(candidates.size()));
        }

        // 한 바퀴를 정말 모두 돌았을 때만 새 바퀴를 시작한다.
        if (playedInCycle.containsAll(unique)) {
            playedInCycle.clear();
            if (!currentKey.isEmpty()) playedInCycle.add(currentKey);
            candidates = collectCandidates(
                    trackKeys, currentKey, true, true, nowMs);
            if (!candidates.isEmpty()) {
                return candidates.get(source.nextInt(candidates.size()));
            }
        }

        // 20분 조건을 느슨하게 풀지 않는다. 다음 가능 시각까지 대기한다.
        blockUntilAvailable(trackKeys, currentIndex, nowMs, true);
        return -1;
    }

    public synchronized boolean lastChoiceBlockedByCooldown() {
        return lastChoiceBlockedByCooldown;
    }

    public synchronized long lastRequiredWaitMs() {
        return lastRequiredWaitMs;
    }

    public synchronized Map<String, Long> historySnapshot() {
        return new LinkedHashMap<>(lastPlayedAt);
    }

    public synchronized Set<String> cycleSnapshot() {
        return new HashSet<>(playedInCycle);
    }

    public synchronized boolean wasPlayedWithin(String trackKey, long nowMs) {
        Long old = lastPlayedAt.get(cleanKey(trackKey));
        return old != null && elapsed(nowMs, old) < cooldownMs;
    }

    private void blockUntilAvailable(
            List<String> trackKeys,
            int currentIndex,
            long nowMs,
            boolean avoidCurrentWhenAlternativesExist) {
        lastChoiceBlockedByCooldown = true;
        lastRequiredWaitMs = nextEligibleDelayMsInternal(
                trackKeys, currentIndex, nowMs, avoidCurrentWhenAlternativesExist);
        if (lastRequiredWaitMs <= 0L) lastRequiredWaitMs = 1_000L;
    }

    private List<Integer> collectCandidates(
            List<String> trackKeys,
            String currentKey,
            boolean requireUnplayedCycle,
            boolean requireCooldownExpired,
            long nowMs) {
        List<Integer> out = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (int i = 0; i < trackKeys.size(); i++) {
            String key = cleanKey(trackKeys.get(i));
            if (key.isEmpty() || key.equals(currentKey) || !added.add(key)) continue;
            if (requireUnplayedCycle && playedInCycle.contains(key)) continue;
            Long old = lastPlayedAt.get(key);
            if (requireCooldownExpired && old != null && elapsed(nowMs, old) < cooldownMs) {
                continue;
            }
            out.add(i);
        }
        return out;
    }

    private long nextEligibleDelayMsInternal(
            List<String> trackKeys,
            int currentIndex,
            long nowMs,
            boolean avoidCurrentWhenAlternativesExist) {
        Set<String> unique = uniqueKeys(trackKeys);
        String currentKey = currentIndex >= 0 && currentIndex < trackKeys.size()
                ? cleanKey(trackKeys.get(currentIndex))
                : "";
        boolean hasAlternative = unique.size() > 1;
        boolean cycleComplete = playedInCycle.containsAll(unique);
        long best = Long.MAX_VALUE;

        for (String key : unique) {
            if (avoidCurrentWhenAlternativesExist && hasAlternative && key.equals(currentKey)) {
                continue;
            }
            if (!cycleComplete && playedInCycle.contains(key)) continue;
            Long old = lastPlayedAt.get(key);
            if (old == null) return 0L;
            long remaining = cooldownMs - elapsed(nowMs, old);
            if (remaining <= 0L) return 0L;
            best = Math.min(best, remaining);
        }

        // 사이클 조건 때문에 후보가 없으면 새 사이클 후보의 가장 이른 해제 시각을 구한다.
        if (best == Long.MAX_VALUE && cycleComplete) {
            for (String key : unique) {
                if (hasAlternative && key.equals(currentKey)) continue;
                Long old = lastPlayedAt.get(key);
                if (old == null) return 0L;
                long remaining = cooldownMs - elapsed(nowMs, old);
                if (remaining <= 0L) return 0L;
                best = Math.min(best, remaining);
            }
        }

        if (best == Long.MAX_VALUE && unique.size() == 1) {
            String only = unique.iterator().next();
            Long old = lastPlayedAt.get(only);
            if (old == null) return 0L;
            best = Math.max(0L, cooldownMs - elapsed(nowMs, old));
        }
        return best == Long.MAX_VALUE ? 1_000L : best;
    }

    private static int firstIndexOf(List<String> trackKeys, String key) {
        for (int i = 0; i < trackKeys.size(); i++) {
            if (cleanKey(trackKeys.get(i)).equals(key)) return i;
        }
        return -1;
    }

    private static Set<String> uniqueKeys(List<String> values) {
        Set<String> out = new HashSet<>();
        if (values == null) return out;
        for (String value : values) {
            String key = cleanKey(value);
            if (!key.isEmpty()) out.add(key);
        }
        return out;
    }

    private void trimHistory() {
        while (lastPlayedAt.size() > MAX_HISTORY_ITEMS) {
            String first = lastPlayedAt.keySet().iterator().next();
            lastPlayedAt.remove(first);
            playedInCycle.remove(first);
        }
    }

    private static long elapsed(long nowMs, long oldMs) {
        if (nowMs < oldMs) return 0L;
        return nowMs - oldMs;
    }

    private static String cleanKey(String value) {
        return value == null ? "" : value.trim();
    }
}
