package com.maru.radio;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Locale;

/** Local-only radio program preferences. No contact, user ID or conversation storage. */
public final class RadioProgram {
    public static final String[] NAMES = {"전체", "한국노래", "중국노래", "서양노래", "연주곡", "4장르 자동 순환"};
    private static final String PREF = "maru_radio_program";
    private RadioProgram() {}

    private static SharedPreferences pref(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static int active(Context context) {
        return clamp(pref(context).getInt("category", 0));
    }
    public static void select(Context context, int category) {
        pref(context).edit().putInt("category", clamp(category)).apply();
    }
    public static int category(Context context, Originals.Track song) {
        try {
            JSONObject map = new JSONObject(pref(context).getString("map", "{}"));
            if (map.has(song.hash)) return Math.min(4, clamp(map.optInt(song.hash, 0)));
        } catch (Exception ignored) {}
        return inferred(song.title);
    }
    public static void classify(Context context, Originals.Track song, int category) {
        try {
            JSONObject map = new JSONObject(pref(context).getString("map", "{}"));
            map.put(song.hash, Math.min(4, clamp(category)));
            pref(context).edit().putString("map", map.toString()).apply();
        } catch (Exception ignored) {}
    }
    // A fresh installation NEVER stops the music after a hidden default hour.
    // A timer exists only if the user explicitly saves one. 0 means disabled.
    public static int durationMinutes(Context context) {
        int value = pref(context).getInt("duration", 0);
        return value == 0 ? 0 : Math.max(5, Math.min(720, value));
    }
    public static void setDurationMinutes(Context context, int minutes) {
        pref(context).edit().putInt("duration",
                minutes <= 0 ? 0 : Math.max(5, Math.min(720, minutes))).apply();
    }
    public static int inferred(String title) {
        if (title == null) return 3;
        String n = title.toLowerCase(Locale.ROOT);
        if (n.contains("instrumental") || n.contains("piano") || n.contains("no vocal")
            || n.contains("연주") || n.contains("피아노") || n.contains("纯音乐")
            || n.contains("钢琴") || n.contains("古筝")) return 4;
        for (int i = 0; i < title.length(); i++) {
            char ch = title.charAt(i);
            if (ch >= 0xAC00 && ch <= 0xD7A3) return 1;
            if (ch >= 0x4E00 && ch <= 0x9FFF) return 2;
        }
        return 3;
    }
    private static int clamp(int n) { return Math.max(0, Math.min(5, n)); }
}
