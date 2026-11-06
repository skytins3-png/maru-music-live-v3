package com.maru.radio;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.*;

/** Metadata and ordered playlists reference original hashes, never duplicate audio. */
public final class RadioCatalog {
    private RadioCatalog() {}
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("maru_catalog", Context.MODE_PRIVATE);
    }
    public static String artist(Context c, Originals.Track t) {
        String fallback = "미지정";
        String clean = t.title.replaceAll("(?i)\\.(mp3|wav|m4a|flac|ogg|aac)$", "");
        int dash = clean.lastIndexOf(" — ");
        if (dash >= 0) fallback = clean.substring(dash + 3).trim();
        return prefs(c).getString("artist:" + t.hash, fallback);
    }
    public static String genre(Context c, Originals.Track t) {
        return prefs(c).getString("genre:" + t.hash, "미지정");
    }
    public static void metadata(Context c, Originals.Track t, String artist, String genre) {
        prefs(c).edit().putString("artist:" + t.hash, artist.trim().isEmpty() ? "미지정" : artist.trim())
            .putString("genre:" + t.hash, genre.trim().isEmpty() ? "미지정" : genre.trim()).apply();
    }
    public static String active(Context c) { return prefs(c).getString("active", ""); }
    public static void select(Context c, String name) { prefs(c).edit().putString("active", name).apply(); }
    public static List<String> names(Context c) {
        List<String> result = new ArrayList<>();
        for (String key : prefs(c).getAll().keySet()) if (key.startsWith("list:")) result.add(key.substring(5));
        Collections.sort(result); return result;
    }
    public static void save(Context c, String name, List<Originals.Track> songs) {
        JSONArray array = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (Originals.Track t : songs) if (seen.add(t.hash)) array.put(t.hash);
        prefs(c).edit().putString("list:" + name, array.toString()).apply();
    }
    public static List<Originals.Track> ordered(Context c, List<Originals.Track> songs) {
        String name = active(c);
        if (name.isEmpty()) return songs;
        Map<String, Originals.Track> map = new HashMap<>();
        for (Originals.Track t : songs) map.put(t.hash, t);
        List<String> hashes = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(c).getString("list:" + name, "[]"));
            for (int i = 0; i < array.length(); i++) {
                hashes.add(array.optString(i));
            }
        } catch (Exception ignored) {}
        return CatalogOrder.resolve(map, hashes);
    }
}
