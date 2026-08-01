package com.maru.musiclive;

/**
 * Central broadcast voice policy.
 *
 * The app uses the device TTS engine and does not force a male or female voice.
 * Spoken audio is limited to track intermissions, manual five-language tests,
 * and the preset closing announcement. Viewer comments and live events remain
 * visual-only while music is playing.
 */
public final class BroadcastVoicePolicy {
    public static final float SPEECH_RATE = 0.94f;
    public static final float PITCH = 1.00f;
    public static final float VOLUME = 1.00f;

    public static final boolean FORCE_GENDER = false;
    public static final boolean SPEAK_COMMENTS = false;
    public static final boolean SPEAK_EVENTS_DURING_SONG = false;

    public static final String WINDOW_INTERMISSION = "intermission";
    public static final String WINDOW_MANUAL_TEST = "manual_test";
    public static final String WINDOW_CLOSING = "closing";
    public static final String WINDOW_COMMENT = "comment";
    public static final String WINDOW_EVENT_DURING_SONG = "event_during_song";

    private static final String[] ORDERED_LANGUAGES = {
            GreetingLanguage.KOREAN,
            GreetingLanguage.ENGLISH,
            GreetingLanguage.CHINESE,
            GreetingLanguage.JAPANESE,
            GreetingLanguage.RUSSIAN
    };

    private BroadcastVoicePolicy() {}

    public static String[] orderedLanguages() {
        return ORDERED_LANGUAGES.clone();
    }

    public static boolean isSpeechAllowed(String window) {
        if (window == null) return false;
        switch (window) {
            case WINDOW_INTERMISSION:
            case WINDOW_MANUAL_TEST:
            case WINDOW_CLOSING:
                return true;
            case WINDOW_COMMENT:
                return SPEAK_COMMENTS;
            case WINDOW_EVENT_DURING_SONG:
                return SPEAK_EVENTS_DURING_SONG;
            default:
                return false;
        }
    }

    public static boolean isExpectedFiveLanguageOrder(String[] languages) {
        if (languages == null || languages.length != ORDERED_LANGUAGES.length) return false;
        for (int i = 0; i < ORDERED_LANGUAGES.length; i++) {
            if (!ORDERED_LANGUAGES[i].equals(languages[i])) return false;
        }
        return true;
    }
}
