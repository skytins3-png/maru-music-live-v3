package com.maru.radio;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

/** Byte-for-byte import of user-approved audio files; never modifies the source. */
public final class Originals {
    private static final String PREF = "maru_radio_originals";
    private static final String KEY = "songs";
    private static final long MAX_BYTES = 300L * 1024 * 1024;

    public static final class Track {
        public final String title;
        public final String path;
        public final String hash;
        Track(String title, String path, String hash) {
            this.title = title;
            this.path = path;
            this.hash = hash;
        }
        public File file() { return new File(path); }
    }

    private Originals() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized List<Track> list(Context ctx) {
        List<Track> tracks = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(ctx).getString(KEY, "[]"));
            File root = new File(ctx.getFilesDir(), "originals").getCanonicalFile();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String path = item.optString("path", "");
                String hash = item.optString("hash", "");
                String title = item.optString("title", "Suno 원곡");
                File file = new File(path).getCanonicalFile();
                if (hash.length() == 64 && file.isFile()
                        && file.getParentFile().equals(root)
                        && file.getName().startsWith(hash + "--")) {
                    tracks.add(new Track(title, file.getAbsolutePath(), hash));
                }
            }
        } catch (Exception ignored) {
            // Unexpected preferences never grant access outside app-owned files.
        }
        return tracks;
    }

    private static synchronized void save(Context ctx, List<Track> tracks) {
        JSONArray array = new JSONArray();
        for (Track t : tracks) {
            JSONObject item = new JSONObject();
            try {
                item.put("title", t.title);
                item.put("path", t.path);
                item.put("hash", t.hash);
            } catch (Exception ignored) {}
            array.put(item);
        }
        prefs(ctx).edit().putString(KEY, array.toString()).apply();
    }

    public interface ProgressListener {
        void onProgress(int done, int total);
    }

    public static final class BatchResult {
        public int added;
        public int duplicates;
        public int failed;
        public String lastError = "";
    }

    /**
     * One batch / one catalog read: scales to 192+ user-selected files.
     * Save every 10 items to preserve progress on an interrupted import.
     * SHA-256 compares file CONTENT, not filename; same title may be different audio.
     */
    public static synchronized BatchResult importBatch(
            Context ctx, List<Uri> incoming, ProgressListener listener) {
        BatchResult result = new BatchResult();
        List<Track> tracks = list(ctx);
        Set<String> hashes = new HashSet<>();
        for (Track t : tracks) hashes.add(t.hash);
        for (int i = 0; i < incoming.size(); i++) {
            try {
                if (copyOriginal(ctx, incoming.get(i), tracks, hashes)) result.added++;
                else result.duplicates++;
            } catch (Exception ex) {
                result.failed++;
                result.lastError = ex.getMessage() == null ? "알 수 없는 파일 오류" : ex.getMessage();
            }
            if ((i + 1) % 10 == 0) save(ctx, tracks);
            if (listener != null) listener.onProgress(i + 1, incoming.size());
        }
        save(ctx, tracks);
        return result;
    }

    /** Stream only the user-selected file, then compare SHA-256 without transcoding. */
    private static boolean copyOriginal(Context ctx, Uri uri,
            List<Track> tracks, Set<String> hashes) throws Exception {
        if (uri == null) throw new IOException("원곡 파일이 선택되지 않았어요.");
        String title = name(ctx.getContentResolver(), uri);
        String mime = ctx.getContentResolver().getType(uri);
        if (!audioFile(title, mime))
            throw new IOException("MP3/WAV 등 오디오 파일만 가져올 수 있어요.");

        File directory = new File(ctx.getFilesDir(), "originals");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("원곡 보관 폴더를 만들 수 없어요.");

        File temp = File.createTempFile("receiving-", ".part", directory);
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(temp)) {
                if (in == null) throw new IOException("선택한 파일을 읽을 수 없어요.");
                byte[] buf = new byte[32 * 1024];
                int count;
                while ((count = in.read(buf)) != -1) {
                    if (count == 0) continue;
                    size += count;
                    if (size > MAX_BYTES)
                        throw new IOException("한 곡 최대 300MB까지 가져올 수 있어요.");
                    hash.update(buf, 0, count);
                    out.write(buf, 0, count);
                }
                out.flush();
            }
            if (size == 0) throw new IOException("비어 있는 파일이에요.");

            String digest = hex(hash.digest());
            if (hashes.contains(digest)) return false;
            File destination = new File(directory, digest + "--" + safeName(title, mime));
            if (!temp.renameTo(destination))
                throw new IOException("원곡 저장에 실패했어요.");

            tracks.add(new Track(title, destination.getAbsolutePath(), digest));
            hashes.add(digest);
            return true;
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    /** Delete ONLY MARU RADIO private copies; downloaded Suno originals are untouched. */
    public static synchronized int removeMany(Context ctx, Set<String> hashes) {
        if (hashes == null || hashes.isEmpty()) return 0;
        List<Track> current = list(ctx);
        List<Track> remaining = new ArrayList<>();
        int removed = 0;
        for (Track item : current) {
            if (hashes.contains(item.hash)) {
                if (item.file().delete()) removed++;
                else remaining.add(item); // Never lose a catalog entry if deletion failed.
            } else {
                remaining.add(item);
            }
        }
        save(ctx, remaining);
        return removed;
    }

    public static synchronized void remove(Context ctx, Track track) {
        Set<String> one = new HashSet<>();
        one.add(track.hash);
        removeMany(ctx, one);
    }

    /**
     * Remove old duplicate catalog entries (if any) but do not delete shared
     * content or source downloads. Normal batch import already prevents these.
     */
    public static synchronized int pruneDuplicateCatalogEntries(Context ctx) {
        List<Track> current = list(ctx);
        Set<String> seen = new HashSet<>();
        List<Track> unique = new ArrayList<>();
        int count = 0;
        for (Track t : current) {
            if (seen.add(t.hash)) unique.add(t);
            else count++;
        }
        if (count > 0) save(ctx, unique);
        return count;
    }

    private static String name(ContentResolver resolver, Uri uri) {
        try (Cursor c = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null)
                return c.getString(0);
        } catch (Exception ignored) {}
        return uri.getLastPathSegment() == null
                ? "Suno-original.mp3" : uri.getLastPathSegment();
    }

    private static boolean audioFile(String name, String mime) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") ||
                lower.endsWith(".flac") || lower.endsWith(".m4a") ||
                lower.endsWith(".ogg") || lower.endsWith(".aac")) return true;
        return mime != null && mime.toLowerCase(Locale.ROOT).startsWith("audio/");
    }

    private static String safeName(String original, String mime) {
        String clean = original.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (clean.isEmpty()) clean = "Suno-original";
        clean = clean.length() > 90 ? clean.substring(clean.length() - 90) : clean;
        String l = clean.toLowerCase(Locale.ROOT);
        if (!(l.endsWith(".mp3") || l.endsWith(".wav") || l.endsWith(".flac")
                || l.endsWith(".m4a") || l.endsWith(".ogg") || l.endsWith(".aac"))) {
            String ext = ".mp3";
            if (mime != null) {
                String m = mime.toLowerCase(Locale.ROOT);
                if (m.contains("wav")) ext = ".wav";
                else if (m.contains("flac")) ext = ".flac";
                else if (m.contains("mp4")) ext = ".m4a";
                else if (m.contains("ogg")) ext = ".ogg";
                else if (m.contains("aac")) ext = ".aac";
            }
            clean += ext;
        }
        return clean;
    }

    public static String exportFilename(Track track) {
        String raw = track.title;
        return safeName(raw, null);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 15, 16));
            out.append(Character.forDigit(b & 15, 16));
        }
        return out.toString();
    }
}
