package com.maru.radio;

import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, BIGO-only UI state checks for an explicitly approved schedule.
 * Does NOT decide whether to broadcast, observe chats, handle login, or search other apps.
 * All steps require recognizable anchors; unexpected states fail closed.
 */
public final class BigoAutomationPolicy {
    public enum Phase {
        IDLE, OPEN_COMPOSER, SELECT_AUDIO, OPEN_MIXER,
        SET_MIC_ONE, SELECT_ORIGINAL, CLOSE_MIXER, START_STREAM,
        VERIFY_STREAM, OPEN_END_DIALOG, CONFIRM_END, VERIFY_END, DONE, FAILED
    }

    private BigoAutomationPolicy() {}

    public static String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}·・:：_\\-]+", "");
    }

    public static boolean has(Set<String> texts, String target) {
        String wanted = normalize(target);
        if (wanted.isEmpty()) return false;
        for (String t : texts) if (normalize(t).contains(wanted)) return true;
        return false;
    }

    public static boolean isSetup(Set<String> labels) {
        return has(labels, "오디오 라이브") && has(labels, "방송하기");
    }

    public static boolean isMixer(Set<String> labels) {
        return has(labels, "믹서") && has(labels, "마이크 볼륨");
    }

    public static boolean isReadyToStart(Set<String> labels) {
        // The BIGO mixer sheet can overlay a visible composer. NEVER press
        // 방송하기 while a mixer or microphone slider is still on screen.
        return isSetup(labels) && !isMixer(labels);
    }

    public static boolean isMicOneShown(Set<String> labels) {
        if (!isMixer(labels)) return false;
        for (String label : labels) {
            if ("1".equals(normalize(label))) return true;
        }
        return false;
    }

    public static boolean isHostLive(Set<String> labels) {
        // Requires two separate host-side live anchors. Prevent false positives
        // from a start-composer screenshot which also includes "자리".
        return !has(labels, "방송하기")
                && (has(labels, "시청자") || has(labels, "명 시청"))
                && (has(labels, "자리") || has(labels, "종료") || has(labels, "방송 시간"));
    }

    /**
     * Host-only control sheet observed in the broadcaster's real Audio LIVE
     * screenshot. Recognizing this sheet is stronger evidence of the OWNER'S
     * controls than a numeric viewer count; it DOES NOT prove audio ingest.
     */
    public static boolean isHostToolsSheet(Set<String> labels) {
        if (labels == null || has(labels, "방송하기")) return false;
        return has(labels, "라이브 툴")
                && has(labels, "믹서")
                && has(labels, "프로그램 목록")
                && has(labels, "실시간 지원")
                && has(labels, "관리")
                && has(labels, "녹화");
    }

    /**
     * Weak layout hint only: never use this by itself to authorize chat
     * reading or to start/end BIGO. At least three distinct seat labels and
     * a mic label must appear, matching the real host-room screenshot.
     */
    public static boolean isOnAirSeatLayout(Set<String> labels) {
        if (labels == null || has(labels, "방송하기")
                || has(labels, "라이브 종료하기")) return false;
        if (!has(labels, "마이크")) return false;
        int seats = 0;
        for (int n = 1; n <= 12; n++) {
            for (String word : labels) {
                if (Integer.toString(n).equals(normalize(word))) {
                    seats++;
                    break;
                }
            }
        }
        return seats >= 3;
    }

    public static boolean isEndConfirmation(Set<String> labels) {
        return has(labels, "라이브 종료하기")
                || (has(labels, "방송을 종료") && has(labels, "종료"));
    }

    public static Phase startNext(Phase previous, Set<String> labels) {
        if (previous == Phase.OPEN_COMPOSER) {
            if (isSetup(labels)) return Phase.SELECT_AUDIO;
            if (has(labels, "홈") && has(labels, "라이브")) return Phase.OPEN_COMPOSER;
        }
        if (previous == Phase.SELECT_AUDIO && isSetup(labels))
            return Phase.OPEN_MIXER;
        if (previous == Phase.OPEN_MIXER && isMixer(labels))
            return Phase.SET_MIC_ONE;
        if (previous == Phase.SET_MIC_ONE && isMixer(labels))
            return Phase.SELECT_ORIGINAL;
        if (previous == Phase.SELECT_ORIGINAL && isMixer(labels))
            return Phase.CLOSE_MIXER;
        if (previous == Phase.CLOSE_MIXER && isSetup(labels))
            return Phase.START_STREAM;
        if (previous == Phase.START_STREAM && isHostLive(labels))
            return Phase.VERIFY_STREAM;
        if (previous == Phase.VERIFY_STREAM && isHostLive(labels))
            return Phase.DONE;
        return previous;
    }

    public static Phase stopNext(Phase previous, Set<String> labels) {
        if (previous == Phase.OPEN_END_DIALOG && isEndConfirmation(labels))
            return Phase.CONFIRM_END;
        if (previous == Phase.CONFIRM_END && !isEndConfirmation(labels)
                && isSetup(labels)) return Phase.VERIFY_END;
        if (previous == Phase.VERIFY_END && isSetup(labels)) return Phase.DONE;
        return previous;
    }
}
