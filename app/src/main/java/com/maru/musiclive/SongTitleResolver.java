package com.maru.musiclive;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.IOException;

public final class SongTitleResolver {
    private SongTitleResolver() {}

    public static String resolve(
            Context context,
            String uriText) {
        if (context == null
                || uriText == null
                || uriText.trim().isEmpty()) {
            return "음악";
        }

        Uri uri = Uri.parse(uriText);

        String metadataTitle =
                metadataTitle(context, uri);
        if (!metadataTitle.isEmpty()) {
            return SongTitleFormatter.clean(metadataTitle);
        }

        String displayName =
                displayName(context, uri);
        if (!displayName.isEmpty()) {
            return SongTitleFormatter.clean(displayName);
        }

        String last = uri.getLastPathSegment();
        if ("actual_music".equals(last)) {
            return "테스트 음악";
        }
        return SongTitleFormatter.clean(last);
    }

    private static String metadataTitle(
            Context context,
            Uri uri) {
        MediaMetadataRetriever retriever =
                new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String title = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE);
            return title == null ? "" : title.trim();
        } catch (RuntimeException ignored) {
            return "";
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
                // Android API 29+ declares IOException on release().
                // Cleanup failure must not prevent resolving the title.
            }
        }
    }

    private static String displayName(
            Context context,
            Uri uri) {
        try (Cursor cursor =
                     context.getContentResolver().query(
                             uri,
                             new String[]{
                                     OpenableColumns.DISPLAY_NAME
                             },
                             null,
                             null,
                             null)) {
            if (cursor != null
                    && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    return value == null ? "" : value.trim();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }
}
