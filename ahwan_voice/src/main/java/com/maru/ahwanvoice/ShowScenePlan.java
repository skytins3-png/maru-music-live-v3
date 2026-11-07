package com.maru.ahwanvoice;

/**
 * Pure, offline visual cue rules. Does not synthesize faces, voices or video,
 * start BIGO, or imply a candidate has actually recorded a song.
 */
public final class ShowScenePlan {
    public enum Mode { STAGE, TALK, BRIDGE }
    public static final String[] CAST = {
            "아환", "윤솔", "강태준", "청호", "서아", "린아", "민혁", "하린"
    };
    private ShowScenePlan() {}

    public static Mode modeForCue(int cue) {
        if (cue == 2 || cue == 4) return Mode.STAGE;
        if (cue == 1 || cue == 3) return Mode.TALK;
        return Mode.BRIDGE;
    }
    public static String[] peopleForCue(int cue, String singerName, int singerIndex) {
        if (cue == 2 || cue == 4) {
            return new String[] { safeSingerName(singerName) };
        }
        if (cue == 1) return new String[] {"아환", "윤솔"};
        if (cue == 3) return new String[] {
                "아환", singerIndex % 2 == 0 ? "하린" : "민혁"
        };
        return new String[] {"아환"};
    }
    private static String safeSingerName(String value) {
        if (value == null || value.trim().isEmpty()) return "가상 AI 가수 후보";
        String clean = value.replaceAll("[\\p{Cntrl}<>]", "").trim();
        return clean.length() > 30 ? clean.substring(0,30) : clean;
    }
}
