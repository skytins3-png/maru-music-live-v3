package com.maru.radio;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-way byte-preserving copy from explicitly imported Suno originals into
 * Android's public Music/MARU RADIO folder through MediaStore.
 *
 * This is NOT an inter-app microphone bridge. BIGO must independently offer
 * a local-song selection in its own mixer; no BIGO stream is started by copy.
 */
public final class RadioBigoMediaExporter {
    private static final String FOLDER = Environment.DIRECTORY_MUSIC + "/MARU RADIO/";
    private RadioBigoMediaExporter() {}

    public static final class Report {
        public int copied;
        public int alreadyAvailable;
        public int failed;
        public boolean cancelled;
        public String error = "";
    }
    public interface Progress {
        void step(int processed, int total, String title, Report report);
    }

    public static Report copyOriginals(Context ctx, List<Originals.Track> originals,
            AtomicBoolean cancel, Progress progress) {
        Report report = new Report();
        if (originals == null) return report;
        if (Build.VERSION.SDK_INT < 29) {
            report.failed = originals.size();
            report.error = "Android 10 이상에서 지원하는 공유 Music 보관 기능입니다.";
            return report;
        }
        for (int i = 0; i < originals.size(); i++) {
            if (cancel != null && cancel.get()) {
                report.cancelled = true;
                break;
            }
            Originals.Track t = originals.get(i);
            try {
                boolean copied = copyOne(ctx, t, cancel);
                if (copied) report.copied++;
                else report.alreadyAvailable++;
            } catch (Exception failure) {
                report.failed++;
                report.error = failure instanceof java.io.InterruptedIOException
                        ? "사용자가 복사를 중단했습니다."
                        : "선택한 원본의 읽기/복사/검증 실패: " + t.title;
                if (failure instanceof java.io.InterruptedIOException) {
                    report.cancelled = true;
                    break;
                }
            }
            if (progress != null) progress.step(i + 1, originals.size(), t.title, report);
        }
        return report;
    }

    private static boolean copyOne(Context ctx, Originals.Track track,
            AtomicBoolean cancel) throws Exception {
        if (track == null || track.title == null || track.hash == null)
            throw new IOException("Invalid original reference");
        if (!RadioBigoExportNaming.extension(track.title.toLowerCase(java.util.Locale.ROOT))
                .isEmpty()) {
            // Keep actual format; no lossy transcode or app loudness processing.
        } else {
            // Some providers omit an extension; the current original export
            // mechanism also assumes MP3. An unknown format must not be faked.
            throw new IOException("Unknown audio extension");
        }
        ContentResolver resolver = ctx.getContentResolver();
        String name = RadioBigoExportNaming.fileName(track.hash, track.title);
        if (exists(resolver, name)) return false;

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Audio.Media.MIME_TYPE, RadioBigoExportNaming.mimeType(name));
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, FOLDER);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        values.put(MediaStore.Audio.Media.IS_MUSIC, 1);

        Uri dst = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (dst == null) throw new IOException("Cannot insert shared Music item");
        boolean complete = false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long copied = 0;
            try (InputStream in = track.isDownloadReference()
                    ? resolver.openInputStream(track.sourceUri())
                    : new FileInputStream(track.file());
                 OutputStream out = resolver.openOutputStream(dst, "w")) {
                if (in == null || out == null) throw new IOException("Cannot open audio");
                byte[] data = new byte[64 * 1024];
                int n;
                while ((n = in.read(data)) != -1) {
                    if (cancel != null && cancel.get())
                        throw new java.io.InterruptedIOException("Cancelled");
                    if (n == 0) continue;
                    copied += n;
                    if (copied > 300L * 1024 * 1024)
                        throw new IOException("Original audio exceeds 300MB");
                    digest.update(data, 0, n);
                    out.write(data, 0, n);
                }
                out.flush();
            }
            if (copied <= 0 || !hex(digest.digest()).equalsIgnoreCase(track.hash))
                throw new IOException("Source SHA-256 mismatches stored original");
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Audio.Media.IS_PENDING, 0);
            if (resolver.update(dst, ready, null, null) != 1)
                throw new IOException("Could not publish copied audio");
            complete = true;
            return true;
        } finally {
            // Failed/partial copies are never exposed as playable audio.
            if (!complete) resolver.delete(dst, null, null);
        }
    }

    private static boolean exists(ContentResolver resolver, String name) {
        try (Cursor cursor = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                MediaStore.Audio.Media.RELATIVE_PATH + "=? AND "
                        + MediaStore.Audio.Media.DISPLAY_NAME + "=? AND "
                        + MediaStore.Audio.Media.IS_PENDING + "=0",
                new String[]{FOLDER, name}, null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (RuntimeException denied) {
            // Duplicate query may be restricted, but copy must still use public APIs.
            // Do not claim the current file was already published.
            return false;
        }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i*2] = digits[(bytes[i] >>> 4) & 15];
            out[i*2+1] = digits[bytes[i] & 15];
        }
        return new String(out);
    }
}
