package com.maru.radio;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONObject;
import java.util.List;

/**
 * Local opt-in DJ preferences: never saves comments, viewer IDs or voice models.
 * Stores aggregate interaction counts and exact SHA-256 song request totals only.
 */
public final class RadioDjSettings {
    private static final String STORE = "maru_dj_local_v1";
    private RadioDjSettings() {}
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public static boolean interSong(Context ctx) {
        return prefs(ctx).getBoolean("inter_song", false);
    }
    public static void setInterSong(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("inter_song", value).apply();
    }
    public static boolean opening(Context ctx) {
        return prefs(ctx).getBoolean("auto_opening", false);
    }
    public static void setOpening(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("auto_opening", value).apply();
    }
    public static boolean isVoiceEnabled(Context ctx) {
        return prefs(ctx).getBoolean("voice_enabled", true);
    }
    public static void setVoiceEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("voice_enabled", value).apply();
    }
    public static boolean isMemoryEnabled(Context ctx) {
        return prefs(ctx).getBoolean("memory", false);
    }
    public static void setMemoryEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("memory", value).apply();
    }
    public static String voiceClip(Context ctx) {
        return prefs(ctx).getString("greeting_clip", "");
    }
    public static void setVoiceClip(Context ctx, Uri uri) {
        prefs(ctx).edit().putString("greeting_clip",
                uri == null ? "" : uri.toString()).apply();
    }
    public static int language(Context ctx) {
        return Math.max(0, Math.min(RadioReplies.LANGUAGES.length - 1,
                prefs(ctx).getInt("language", 0)));
    }
    public static void setLanguage(Context ctx, int language) {
        prefs(ctx).edit().putInt("language", Math.max(0,
                Math.min(RadioReplies.LANGUAGES.length - 1, language))).apply();
    }

    /** Host-authored phrases only, never automatically store audience comments. */
    public static void teach(Context ctx, String trigger, String response) {
        if (trigger == null || response == null) return;
        String word = trigger.trim().toLowerCase(java.util.Locale.ROOT);
        String answer = response.trim();
        if (word.length() < 2 || word.length() > 40
                || answer.length() < 2 || answer.length() > 160) return;
        try {
            SharedPreferences sp = prefs(ctx);
            JSONObject known = new JSONObject(sp.getString("host_rules", "{}"));
            if (!known.has(word) && known.length() >= 20) return;
            known.put(word, answer);
            sp.edit().putString("host_rules", known.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static String hostReply(Context ctx, String message) {
        if (message == null || message.trim().isEmpty()) return "";
        try {
            JSONObject known = new JSONObject(prefs(ctx).getString("host_rules", "{}"));
            java.util.Iterator<String> items = known.keys();
            String lower = message.toLowerCase(java.util.Locale.ROOT);
            String best = "";
            int length = 0;
            while (items.hasNext()) {
                String trigger = items.next();
                if (lower.contains(trigger) && trigger.length() > length) {
                    best = known.optString(trigger, "");
                    length = trigger.length();
                }
            }
            return best;
        } catch (Exception ex) { return ""; }
    }
    public static void clearHostRules(Context ctx) {
        prefs(ctx).edit().remove("host_rules").apply();
    }
    public static int replyCount(Context ctx) {
        return prefs(ctx).getInt("replies", 0);
    }
    public static int nextPhraseIndex(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        int value = sp.getInt("phrases", 0);
        if (isMemoryEnabled(ctx))
            sp.edit().putInt("phrases", (value + 1) % 1024).apply();
        return value;
    }
    public static void rememberReply(Context ctx) {
        if (!isMemoryEnabled(ctx)) return;
        SharedPreferences sp = prefs(ctx);
        sp.edit().putInt("replies", Math.min(1000000, replyCount(ctx) + 1)).apply();
    }
    public static void rememberSong(Context ctx, String hash) {
        if (!isMemoryEnabled(ctx) || hash == null || !hash.matches("[a-fA-F0-9]{64}")) return;
        try {
            SharedPreferences sp = prefs(ctx);
            JSONObject counts = new JSONObject(sp.getString("requests", "{}"));
            if (!counts.has(hash) && counts.length() >= 120) return;
            counts.put(hash, Math.min(100000, counts.optInt(hash, 0) + 1));
            sp.edit().putString("requests", counts.toString()).apply();
        } catch (Exception ignored) {}
    }
    public static String favoriteTitle(Context ctx, List<Originals.Track> songs) {
        if (songs == null) return "";
        try {
            JSONObject counts = new JSONObject(prefs(ctx).getString("requests", "{}"));
            int best = 0;
            String title = "";
            for (Originals.Track song : songs) {
                int n = counts.optInt(song.hash, 0);
                if (n > best) {
                    best = n;
                    title = song.title;
                }
            }
            return title;
        } catch (Exception e) { return ""; }
    }
    public static void clearMemory(Context ctx) {
        prefs(ctx).edit().remove("requests").remove("replies").remove("phrases").apply();
    }
}
