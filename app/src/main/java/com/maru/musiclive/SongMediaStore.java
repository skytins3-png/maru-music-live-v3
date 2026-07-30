package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public final class SongMediaStore {
    private static final String PREFS = "maru_song_media";

    private SongMediaStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<String> load(Context context, String songUri) {
        List<String> out = new ArrayList<>();
        String value = prefs(context).getString(key(songUri), "");
        if (value == null || value.trim().isEmpty()) return out;
        for (String line : value.split("\\n")) {
            if (!line.trim().isEmpty()) out.add(line.trim());
        }
        return out;
    }

    public static void add(Context context, String songUri, List<String> mediaUris) {
        List<String> items = load(context, songUri);
        if (mediaUris != null) {
            for (String uri : mediaUris) {
                if (uri != null && !uri.trim().isEmpty() && !items.contains(uri.trim())) {
                    items.add(uri.trim());
                }
            }
        }
        save(context, songUri, items);
    }

    public static void save(Context context, String songUri, List<String> mediaUris) {
        String value = mediaUris == null ? "" : String.join("\n", mediaUris);
        prefs(context).edit().putString(key(songUri), value).apply();
    }

    public static void clear(Context context, String songUri) {
        prefs(context).edit().remove(key(songUri)).apply();
    }

    private static String key(String songUri) {
        return "song_" + sha256(songUri == null ? "" : songUri);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
