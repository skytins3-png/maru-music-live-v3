package com.maru.musiclive;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads audio indexed by Android MediaStore without moving or deleting originals.
 * Exact duplicate bytes are collapsed to the first item.
 */
public final class DeviceMusicScanner {
    private static final long MIN_DURATION_MS = 1_000L;

    public static final class Result {
        public final List<SongItem> songs;
        public final int duplicates;

        Result(List<SongItem> songs, int duplicates) {
            this.songs = songs;
            this.duplicates = duplicates;
        }
    }

    private DeviceMusicScanner() {}

    public static Result scan(Context context) {
        List<SongItem> found = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        int duplicates = 0;
        ContentResolver resolver = context.getContentResolver();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                + MediaStore.Audio.Media.DURATION + " >= ?";
        String[] args = {String.valueOf(MIN_DURATION_MS)};

        try (Cursor cursor = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                MediaStore.Audio.Media.DATE_MODIFIED + " DESC")) {
            if (cursor == null) return new Result(found, 0);
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                long size = cursor.getLong(sizeColumn);
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                String digest = sha256(resolver, uri, size);
                if (digest.isEmpty()) continue;
                if (!hashes.add(digest)) {
                    duplicates++;
                    continue;
                }
                String title = cursor.getString(titleColumn);
                if (title == null || title.trim().isEmpty()) {
                    title = cursor.getString(nameColumn);
                }
                found.add(new SongItem(uri.toString(), title));
            }
        } catch (SecurityException ignored) {
            return new Result(new ArrayList<>(), 0);
        }
        return new Result(found, duplicates);
    }

    private static String sha256(ContentResolver resolver, Uri uri, long size) {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return "";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder value = new StringBuilder(80);
            value.append(size).append(':');
            for (byte b : digest.digest()) {
                value.append(String.format(Locale.ROOT, "%02x", b));
            }
            return value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
