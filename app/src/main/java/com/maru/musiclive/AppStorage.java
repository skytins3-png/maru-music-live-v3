package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AppStorage {
    private static final String PREFS = "maru_music_live";
    private static final String KEY_SONGS = "songs";
    private static final String KEY_IMAGES = "images";
    private static final String KEY_LYRICS = "lyrics";
    private static final String KEY_REPEAT = "repeat";
    private static final String KEY_RANDOM = "random";
    private static final String KEY_MODE = "broadcast_mode";
    private static final String KEY_SONG_TITLE_TTS = "song_title_tts";
    private static final String KEY_RANDOM_HISTORY = "random_history_v1";
    private static final String KEY_RANDOM_CYCLE = "random_cycle_v1";
    private static final String KEY_FILL_BROADCAST_IMAGE = "fill_broadcast_image";

    private AppStorage() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String encode(List<String> items) {
        return String.join("\n", items);
    }

    private static List<String> decode(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return out;
        for (String line : value.split("\n")) {
            if (!line.trim().isEmpty()) out.add(line);
        }
        return out;
    }

    public static List<String> loadSongs(Context c) { return decode(prefs(c).getString(KEY_SONGS, "")); }
    public static List<String> loadImages(Context c) { return decode(prefs(c).getString(KEY_IMAGES, "")); }
    public static List<String> loadLyrics(Context c) { return decode(prefs(c).getString(KEY_LYRICS, "")); }

    public static void saveSongs(Context c, List<String> v) { prefs(c).edit().putString(KEY_SONGS, encode(v)).apply(); }
    public static void saveImages(Context c, List<String> v) { prefs(c).edit().putString(KEY_IMAGES, encode(v)).apply(); }
    public static void saveLyrics(Context c, List<String> v) { prefs(c).edit().putString(KEY_LYRICS, encode(v)).apply(); }

    public static boolean repeatAll(Context c) { return prefs(c).getBoolean(KEY_REPEAT, true); }
    public static boolean random(Context c) { return prefs(c).getBoolean(KEY_RANDOM, false); }
    public static void setRepeatAll(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_REPEAT, v).apply(); }
    public static void setRandom(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_RANDOM, v).apply(); }

    /** Split-screen BIGO use: crop the song image slightly so the top pane has no wide side bars. */
    public static boolean fillBroadcastImage(Context c) {
        return prefs(c).getBoolean(KEY_FILL_BROADCAST_IMAGE, true);
    }

    public static void setFillBroadcastImage(Context c, boolean value) {
        prefs(c).edit().putBoolean(KEY_FILL_BROADCAST_IMAGE, value).apply();
    }

    public static boolean songTitleTts(Context c) {
        return prefs(c).getBoolean(KEY_SONG_TITLE_TTS, true);
    }

    public static void setSongTitleTts(Context c, boolean value) {
        prefs(c).edit()
                .putBoolean(KEY_SONG_TITLE_TTS, value)
                .apply();
    }

    public static Map<String, Long> loadRandomHistory(Context c) {
        Map<String, Long> out = new LinkedHashMap<>();
        String raw = prefs(c).getString(KEY_RANDOM_HISTORY, "");
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String line : raw.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab >= line.length() - 1) continue;
            try {
                long time = Long.parseLong(line.substring(0, tab));
                String key = decodeKey(line.substring(tab + 1));
                if (!key.isEmpty()) out.put(key, time);
            } catch (RuntimeException ignored) {}
        }
        return out;
    }

    public static Set<String> loadRandomCycle(Context c) {
        Set<String> out = new HashSet<>();
        String raw = prefs(c).getString(KEY_RANDOM_CYCLE, "");
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String line : raw.split("\n")) {
            try {
                String key = decodeKey(line);
                if (!key.isEmpty()) out.add(key);
            } catch (RuntimeException ignored) {}
        }
        return out;
    }

    public static void saveRandomPlaybackState(
            Context c,
            Map<String, Long> history,
            Set<String> cycle) {
        StringBuilder historyText = new StringBuilder();
        if (history != null) {
            for (Map.Entry<String, Long> entry : history.entrySet()) {
                String key = entry.getKey();
                Long time = entry.getValue();
                if (key == null || key.trim().isEmpty() || time == null) continue;
                if (historyText.length() > 0) historyText.append('\n');
                historyText.append(Math.max(0L, time))
                        .append('\t')
                        .append(encodeKey(key));
            }
        }
        StringBuilder cycleText = new StringBuilder();
        if (cycle != null) {
            for (String key : cycle) {
                if (key == null || key.trim().isEmpty()) continue;
                if (cycleText.length() > 0) cycleText.append('\n');
                cycleText.append(encodeKey(key));
            }
        }
        prefs(c).edit()
                .putString(KEY_RANDOM_HISTORY, historyText.toString())
                .putString(KEY_RANDOM_CYCLE, cycleText.toString())
                .apply();
    }

    private static String encodeKey(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeKey(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        return new String(
                Base64.getUrlDecoder().decode(value.trim()),
                StandardCharsets.UTF_8);
    }

    public static String broadcastMode(Context c) {
        return prefs(c).getString(KEY_MODE, BroadcastMode.PORTRAIT_9_16.name());
    }
    public static void setBroadcastMode(Context c, String v) {
        prefs(c).edit().putString(KEY_MODE, v).apply();
    }
}
