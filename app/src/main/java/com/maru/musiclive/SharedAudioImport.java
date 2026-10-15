package com.maru.musiclive;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Copies an explicitly shared/downloaded Suno (or other) audio file byte-for-byte.
 * Does not capture, download or intercept another app's live playback.
 *
 * ACTION_SEND URIs may lose their read grant after the source app exits.
 * Storing an app-owned copy avoids broken playlist items after a reboot.
 */
public final class SharedAudioImport {
    private static final long MAX_IMPORT_BYTES = 300L * 1024L * 1024L;
    private static final String[] EXTENSIONS =
            {".mp3", ".wav", ".m4a", ".aac", ".flac", ".ogg", ".opus"};

    private SharedAudioImport() {}

    public static String copyOriginal(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("오디오 파일 주소가 없습니다.");
        ContentResolver resolver = context.getContentResolver();
        String sourceName = displayName(resolver, uri);
        String mime = resolver.getType(uri);
        if (!isSupportedAudio(sourceName, mime)) {
            throw new IOException("MP3/WAV 등 오디오 파일을 공유해 주세요.");
        }
        File directory = new File(context.getFilesDir(), "shared_original_audio");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("음악 저장 폴더를 만들 수 없습니다.");
        }

        File temporary = File.createTempFile("incoming-", ".part", directory);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0L;
            try (InputStream input = resolver.openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IOException("공유된 음악을 열 수 없습니다.");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) continue;
                    bytes += read;
                    if (bytes > MAX_IMPORT_BYTES) {
                        throw new IOException("300MB 이하 음악만 가져올 수 있습니다.");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
                output.flush();
                output.getFD().sync();
            }
            if (bytes == 0L) throw new IOException("비어 있는 음악 파일입니다.");
            String fingerprint = hex(digest.digest());
            String prefix = fingerprint + "--";
            File[] existing = directory.listFiles();
            if (existing != null) {
                for (File candidate : existing) {
                    if (candidate.isFile() && candidate.getName().startsWith(prefix)) {
                        return Uri.fromFile(candidate).toString();
                    }
                }
            }
            String filename = safeFilename(sourceName, mime);
            File destination = new File(directory, prefix + filename);
            if (!temporary.renameTo(destination)) {
                throw new IOException("음악 저장을 완료하지 못했습니다.");
            }
            return Uri.fromFile(destination).toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("음악 파일 확인에 실패했습니다.", error);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name;
            }
        } catch (RuntimeException ignored) {}
        String fallback = uri.getLastPathSegment();
        return fallback == null || fallback.isEmpty() ? "Suno_original" : fallback;
    }

    private static boolean isSupportedAudio(String name, String mime) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) if (lower.endsWith(ext)) return true;
        return mime != null && mime.toLowerCase(Locale.ROOT).startsWith("audio/");
    }

    private static String safeFilename(String name, String mime) {
        String raw = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (raw.isEmpty()) raw = "Suno_original";
        String lower = raw.toLowerCase(Locale.ROOT);
        boolean extensionFound = false;
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) {
                extensionFound = true;
                break;
            }
        }
        if (!extensionFound) {
            String ext = ".mp3";
            if (mime != null) {
                String kind = mime.toLowerCase(Locale.ROOT);
                if (kind.contains("wav")) ext = ".wav";
                else if (kind.contains("flac")) ext = ".flac";
                else if (kind.contains("mp4") || kind.contains("m4a")) ext = ".m4a";
                else if (kind.contains("ogg")) ext = ".ogg";
                else if (kind.contains("aac")) ext = ".aac";
            }
            raw += ext;
        }
        if (raw.length() > 100) {
            int dot = raw.lastIndexOf('.');
            String ext = dot >= 0 ? raw.substring(dot) : "";
            raw = raw.substring(0, Math.min(90, raw.length() - ext.length())) + ext;
        }
        return raw;
    }

    private static String hex(byte[] digest) {
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            value.append(Character.forDigit((b >>> 4) & 0xf, 16));
            value.append(Character.forDigit(b & 0xf, 16));
        }
        return value.toString();
    }
}
