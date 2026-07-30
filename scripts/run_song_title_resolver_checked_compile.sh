#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/song-title-resolver-checked"
SRC="$OUT/src"
CLASSES="$OUT/classes"

rm -rf "$OUT"
mkdir -p \
  "$SRC/android/content" \
  "$SRC/android/database" \
  "$SRC/android/media" \
  "$SRC/android/net" \
  "$SRC/android/provider" \
  "$CLASSES"

cat > "$SRC/android/net/Uri.java" <<'JAVA'
package android.net;

public final class Uri {
    private final String value;

    private Uri(String value) {
        this.value = value == null ? "" : value;
    }

    public static Uri parse(String value) {
        return new Uri(value);
    }

    public String getLastPathSegment() {
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    @Override public String toString() {
        return value;
    }
}
JAVA

cat > "$SRC/android/database/Cursor.java" <<'JAVA'
package android.database;

public interface Cursor extends AutoCloseable {
    boolean moveToFirst();
    int getColumnIndex(String columnName);
    String getString(int columnIndex);
    @Override void close();
}
JAVA

cat > "$SRC/android/content/ContentResolver.java" <<'JAVA'
package android.content;

import android.database.Cursor;
import android.net.Uri;

public abstract class ContentResolver {
    public abstract Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder);
}
JAVA

cat > "$SRC/android/content/Context.java" <<'JAVA'
package android.content;

public abstract class Context {
    public abstract ContentResolver getContentResolver();
}
JAVA

cat > "$SRC/android/provider/OpenableColumns.java" <<'JAVA'
package android.provider;

public interface OpenableColumns {
    String DISPLAY_NAME = "_display_name";
}
JAVA

cat > "$SRC/android/media/MediaMetadataRetriever.java" <<'JAVA'
package android.media;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

public class MediaMetadataRetriever {
    public static final int METADATA_KEY_TITLE = 7;

    public static String nextTitle = "";
    public static boolean throwOnRelease;

    public void setDataSource(Context context, Uri uri) {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("missing source");
        }
    }

    public String extractMetadata(int keyCode) {
        return keyCode == METADATA_KEY_TITLE ? nextTitle : null;
    }

    // Deliberately checked, matching current Android SDK behavior.
    public void release() throws IOException {
        if (throwOnRelease) {
            throw new IOException("simulated cleanup failure");
        }
    }
}
JAVA

cat > "$SRC/SongTitleResolverCheckedTest.java" <<'JAVA'
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.maru.musiclive.SongTitleResolver;

public final class SongTitleResolverCheckedTest {
    public static void main(String[] args) {
        FakeContext context = new FakeContext("01 - display_title.mp3");

        MediaMetadataRetriever.nextTitle = "  Metadata Song  ";
        MediaMetadataRetriever.throwOnRelease = true;
        String metadata = SongTitleResolver.resolve(
                context,
                "content://music/123");
        assertEquals("Metadata Song", metadata, "metadata title");

        MediaMetadataRetriever.nextTitle = "";
        MediaMetadataRetriever.throwOnRelease = false;
        String display = SongTitleResolver.resolve(
                context,
                "content://music/456");
        assertEquals("display title", display, "display-name fallback");

        String bundled = SongTitleResolver.resolve(
                context,
                "android.resource://com.maru.musiclive/raw/actual_music");
        // Query fallback wins in this fake resolver, so separately verify null handling.
        assertEquals("음악", SongTitleResolver.resolve(null, "x"), "null context");
        if (bundled.isEmpty()) {
            throw new AssertionError("bundled fallback returned empty title");
        }

        System.out.println("SONG-TITLE-RESOLVER-CHECKED-COMPILE-TEST: PASS");
    }

    private static void assertEquals(
            String expected,
            String actual,
            String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static final class FakeContext extends Context {
        private final ContentResolver resolver;

        FakeContext(String displayName) {
            resolver = new FakeResolver(displayName);
        }

        @Override public ContentResolver getContentResolver() {
            return resolver;
        }
    }

    private static final class FakeResolver extends ContentResolver {
        private final String displayName;

        FakeResolver(String displayName) {
            this.displayName = displayName;
        }

        @Override public Cursor query(
                Uri uri,
                String[] projection,
                String selection,
                String[] selectionArgs,
                String sortOrder) {
            return new FakeCursor(displayName);
        }
    }

    private static final class FakeCursor implements Cursor {
        private final String displayName;

        FakeCursor(String displayName) {
            this.displayName = displayName;
        }

        @Override public boolean moveToFirst() {
            return true;
        }

        @Override public int getColumnIndex(String columnName) {
            return "_display_name".equals(columnName) ? 0 : -1;
        }

        @Override public String getString(int columnIndex) {
            return columnIndex == 0 ? displayName : null;
        }

        @Override public void close() {}
    }
}
JAVA

javac \
  -encoding UTF-8 \
  -d "$CLASSES" \
  "$SRC/android/net/Uri.java" \
  "$SRC/android/database/Cursor.java" \
  "$SRC/android/content/ContentResolver.java" \
  "$SRC/android/content/Context.java" \
  "$SRC/android/provider/OpenableColumns.java" \
  "$SRC/android/media/MediaMetadataRetriever.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/SongTitleFormatter.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/SongTitleResolver.java" \
  "$SRC/SongTitleResolverCheckedTest.java"

java -cp "$CLASSES" SongTitleResolverCheckedTest
