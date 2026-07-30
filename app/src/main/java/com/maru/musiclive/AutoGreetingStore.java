package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;

public final class AutoGreetingStore {
    private static final String PREFS = "maru_auto_greeting";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_AUDIO_URI = "audio_uri";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String KEY_LAST_RAW = "last_raw";
    private static final String KEY_LAST_LANGUAGE = "last_language";
    private static final String KEY_LAST_STATUS = "last_status";
    private static final String KEY_RUNNING_MODE = "running_mode";
    private static final String KEY_COOLDOWN_SECONDS = "cooldown_seconds";
    private static final String KEY_DETECTION_COUNT = "detection_count";
    private static final String KEY_LAST_OCR_TEXT = "last_ocr_text";
    private static final String KEY_LAST_OCR_TIME = "last_ocr_time";
    private static final String KEY_OCR_FRAME_COUNT = "ocr_frame_count";
    private static final String KEY_LAST_GREETING_TIME = "last_greeting_time";
    private static final String KEY_LAST_EVENT_TYPE = "last_event_type";
    private static final String KEY_JOIN_COUNT = "join_count";
    private static final String KEY_LIKE_COUNT = "like_count";
    private static final String KEY_GIFT_COUNT = "gift_count";
    private static final String KEY_FOLLOW_COUNT = "follow_count";
    private static final String KEY_CHAT_COUNT = "chat_count";

    private AutoGreetingStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply();
    }

    public static String greetingAudioUri(Context context) {
        return prefs(context).getString(KEY_AUDIO_URI, "");
    }

    public static void setGreetingAudioUri(Context context, String value) {
        prefs(context).edit().putString(
                KEY_AUDIO_URI, value == null ? "" : value).apply();
    }

    public static long cooldownMs(Context context) {
        int seconds = prefs(context).getInt(KEY_COOLDOWN_SECONDS, 60);
        return Math.max(10, Math.min(600, seconds)) * 1000L;
    }

    public static void recordDetection(
            Context context,
            String nickname,
            String raw,
            String language,
            long timeMs) {
        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_DETECTION_COUNT, 0) + 1;
        preferences.edit()
                .putString(
                        KEY_LAST_NAME,
                        nickname == null ? "" : nickname)
                .putString(KEY_LAST_RAW, raw == null ? "" : raw)
                .putString(
                        KEY_LAST_LANGUAGE,
                        GreetingLanguage.normalize(language))
                .putLong(KEY_LAST_TIME, timeMs)
                .putInt(KEY_DETECTION_COUNT, count)
                .apply();
    }

    public static void recordDetection(
            Context context,
            String nickname,
            String raw,
            long timeMs) {
        recordDetection(
                context,
                nickname,
                raw,
                GreetingLanguage.KOREAN,
                timeMs);
    }

    public static void recordEvent(Context context, LiveEvent event) {
        if (event == null) return;
        recordDetection(
                context,
                event.nickname,
                event.rawText,
                event.languageHint,
                event.timeMs);
        String counter = counterKey(event.type);
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_LAST_EVENT_TYPE, event.type.name());
        if (!counter.isEmpty()) {
            editor.putInt(counter, preferences.getInt(counter, 0) + 1);
        }
        editor.apply();
    }

    public static EventType lastEventType(Context context) {
        return EventType.fromStored(
                prefs(context).getString(KEY_LAST_EVENT_TYPE, ""));
    }

    public static int eventCount(Context context, EventType type) {
        String key = counterKey(type);
        return key.isEmpty() ? 0 : prefs(context).getInt(key, 0);
    }

    private static String counterKey(EventType type) {
        if (type == EventType.JOIN) return KEY_JOIN_COUNT;
        if (type == EventType.LIKE) return KEY_LIKE_COUNT;
        if (type == EventType.GIFT) return KEY_GIFT_COUNT;
        if (type == EventType.FOLLOW) return KEY_FOLLOW_COUNT;
        if (type == EventType.CHAT) return KEY_CHAT_COUNT;
        return "";
    }

    public static String lastNickname(Context context) {
        return prefs(context).getString(KEY_LAST_NAME, "");
    }

    public static String lastRaw(Context context) {
        return prefs(context).getString(KEY_LAST_RAW, "");
    }

    public static String lastLanguage(Context context) {
        return GreetingLanguage.normalize(
                prefs(context).getString(
                        KEY_LAST_LANGUAGE,
                        GreetingLanguage.KOREAN));
    }

    public static long lastTime(Context context) {
        return prefs(context).getLong(KEY_LAST_TIME, 0L);
    }

    public static int detectionCount(Context context) {
        return prefs(context).getInt(KEY_DETECTION_COUNT, 0);
    }

    public static void recordOcrFrame(
            Context context,
            String recognizedText,
            long timeMs) {
        String text = recognizedText == null
                ? ""
                : TextNormalizer.normalize(recognizedText);
        if (text.length() > 180) {
            text = text.substring(0, 180);
        }

        SharedPreferences preferences = prefs(context);
        int count = preferences.getInt(KEY_OCR_FRAME_COUNT, 0) + 1;
        preferences.edit()
                .putString(KEY_LAST_OCR_TEXT, text)
                .putLong(KEY_LAST_OCR_TIME, timeMs)
                .putInt(KEY_OCR_FRAME_COUNT, count)
                .apply();
    }

    public static String lastOcrText(Context context) {
        return prefs(context).getString(KEY_LAST_OCR_TEXT, "");
    }

    public static long lastOcrTime(Context context) {
        return prefs(context).getLong(KEY_LAST_OCR_TIME, 0L);
    }

    public static int ocrFrameCount(Context context) {
        return prefs(context).getInt(KEY_OCR_FRAME_COUNT, 0);
    }

    public static void recordGreetingPlayed(
            Context context,
            long timeMs) {
        prefs(context).edit()
                .putLong(KEY_LAST_GREETING_TIME, timeMs)
                .apply();
    }

    public static long lastGreetingTime(Context context) {
        return prefs(context).getLong(KEY_LAST_GREETING_TIME, 0L);
    }

    public static void setStatus(Context context, String status) {
        prefs(context).edit().putString(
                KEY_LAST_STATUS, status == null ? "" : status).apply();
    }

    public static String status(Context context) {
        return prefs(context).getString(KEY_LAST_STATUS, "대기 중");
    }

    public static void setRunningMode(Context context, String mode) {
        prefs(context).edit().putString(
                KEY_RUNNING_MODE, mode == null ? "" : mode).apply();
    }

    public static String runningMode(Context context) {
        return prefs(context).getString(KEY_RUNNING_MODE, "");
    }
}
