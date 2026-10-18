package com.maru.radio;

/**
 * Owner-controlled voice profile settings. Stores references/intent, never
 * claims to synthesize or clone a voice without a fitted consented model.
 */
public final class RadioVoiceProfileState {
    public static final String TALK = "talk";
    public static final String SING = "sing";
    public static final int DEFAULT_OWNER_PERCENT = 50;

    private RadioVoiceProfileState() {}
    public static boolean valid(String slot) {
        return TALK.equals(slot) || SING.equals(slot);
    }
    public static String filename(String slot) {
        if (TALK.equals(slot)) return "owner_speech.m4a";
        if (SING.equals(slot)) return "owner_singing.m4a";
        throw new IllegalArgumentException("Unknown or unsafe voice sample");
    }
    public static String title(String slot) {
        if (TALK.equals(slot)) return "말하는 형의 목소리";
        if (SING.equals(slot)) return "노래하는 형의 목소리";
        throw new IllegalArgumentException("Unknown voice sample");
    }
    public static int clampPercent(int requested) {
        return Math.max(0, Math.min(100, requested));
    }
    public static String blendDescription(int requested) {
        int owner = clampPercent(requested);
        return "형 음색 " + owner + "% · 허가된 전문 DJ 음색 "
                + (100 - owner) + "% (제작 목표, 아직 음성 합성 적용 전)";
    }
    public static boolean readyForConsentBasedTraining(boolean agreed,
            boolean talkPresent, boolean singPresent) {
        return agreed && talkPresent && singPresent;
    }
}
