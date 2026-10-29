package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Local, operator-approved radio inbox. No access to BIGO's private messages.
 * Audience names and messages are never obtained by screen scraping or sent
 * to any server. Stored in the app's private preferences.
 */
public final class RadioAudienceStore {
    private static final String PREF = "maru_radio_audience_v1";
    private static final String STORIES = "approved_stories";
    private static final String REQUESTS = "song_requests";
    private static final String NOTES = "approved_learning_notes";
    private static final int CAP = 30;

    public static final class Story {
        public final String id, name, text;
        Story(String id, String name, String text) {
            this.id = id; this.name = name; this.text = text;
        }
    }

    private RadioAudienceStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String clean(String text, int max) {
        if (text == null) return "";
        String s = text.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static JSONArray load(Context c, String key) {
        try { return new JSONArray(prefs(c).getString(key, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private static void save(Context c, String key, JSONArray items) {
        prefs(c).edit().putString(key, items.toString()).apply();
    }

    private static void append(Context c, String key, JSONObject item) {
        JSONArray old = load(c, key);
        JSONArray next = new JSONArray();
        for (int i = Math.max(0, old.length() - CAP + 1); i < old.length(); i++)
            next.put(old.optJSONObject(i));
        next.put(item);
        save(c, key, next);
    }

    /** Only the operator can add a story after confirming broadcast consent. */
    public static boolean submitApprovedStory(Context c, String nickname, String message) {
        String body = clean(message, 350);
        if (body.isEmpty()) return false;
        String name = clean(nickname, 24);
        if (name.isEmpty()) name = "익명 청취자";
        JSONObject object = new JSONObject();
        try {
            object.put("id", java.util.UUID.randomUUID().toString());
            object.put("name", name);
            object.put("text", body);
        } catch (Exception ignored) { return false; }
        append(c, STORIES, object);
        return true;
    }

    public static Story peekStory(Context c) {
        JSONObject first = load(c, STORIES).optJSONObject(0);
        if (first == null) return null;
        return new Story(first.optString("id"), clean(first.optString("name"), 24),
                clean(first.optString("text"), 350));
    }

    /** Never silently remove a different story that arrived during playback. */
    public static void markStoryRead(Context c, String expectedId) {
        if (expectedId == null || expectedId.isEmpty()) return;
        JSONArray old = load(c, STORIES), next = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject row = old.optJSONObject(i);
            if (row == null) continue;
            if (!removed && expectedId.equals(row.optString("id"))) {
                removed = true;
                continue;
            }
            next.put(row);
        }
        if (removed) save(c, STORIES, next);
    }

    public static boolean requestSong(Context c, String songUri) {
        String value = clean(songUri, 2048);
        if (value.isEmpty()) return false;
        // Do not duplicate pending requests for the same local song.
        JSONArray old = load(c, REQUESTS);
        for (int i = 0; i < old.length(); i++) {
            if (value.equals(old.optString(i))) return true;
        }
        JSONArray next = new JSONArray();
        for (int i = Math.max(0, old.length() - CAP + 1); i < old.length(); i++)
            next.put(old.optString(i));
        next.put(value);
        save(c, REQUESTS, next);
        return true;
    }

    public static String peekRequestedSong(Context c) {
        return clean(load(c, REQUESTS).optString(0), 2048);
    }

    public static void markRequestServed(Context c, String uri) {
        if (uri == null || uri.isEmpty()) return;
        JSONArray old = load(c, REQUESTS), next = new JSONArray();
        boolean consumed = false;
        for (int i = 0; i < old.length(); i++) {
            String value = old.optString(i);
            if (!consumed && uri.equals(value)) { consumed = true; continue; }
            next.put(value);
        }
        if (consumed) save(c, REQUESTS, next);
    }

    /** Manual notes only; any external page must be opened by the operator. */
    public static boolean addApprovedResearch(Context c, String sourceUrl, String note) {
        String url = clean(sourceUrl, 500);
        String excerpt = clean(note, 350);
        if (!url.startsWith("https://") || excerpt.isEmpty()) return false;
        JSONObject item = new JSONObject();
        try {
            item.put("url", url);
            item.put("note", excerpt);
        } catch (Exception ignored) { return false; }
        append(c, NOTES, item);
        return true;
    }

    /** Rotate short approved notes; never treat third-party claims as verified. */
    public static String approvedResearchNote(Context c, int episode) {
        JSONArray notes = load(c, NOTES);
        if (notes.length() == 0) return "";
        JSONObject row = notes.optJSONObject(Math.floorMod(episode, notes.length()));
        if (row == null) return "";
        return clean(row.optString("note"), 350);
    }

    public static int pendingStoryCount(Context c) { return load(c, STORIES).length(); }
    public static int pendingRequestCount(Context c) { return load(c, REQUESTS).length(); }
    public static int approvedNoteCount(Context c) { return load(c, NOTES).length(); }
}
