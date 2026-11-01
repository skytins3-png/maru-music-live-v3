package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;

public class SingleMediaPreviewPolicyTest {
    @Test public void onlyLocalPickedMediaAccepted() {
        assertTrue(SingleMediaPreviewPolicy.accept("image/jpeg", "content://com.android.providers.media/doc/01"));
        assertTrue(SingleMediaPreviewPolicy.accept("video/mp4", "content://com.android.providers.media/doc/02"));
        assertTrue(SingleMediaPreviewPolicy.accept("image/png", "android.resource://com.maru.musiclive/drawable/art"));
        assertFalse(SingleMediaPreviewPolicy.accept("image/jpeg", "https://evil.test/picture.jpg"));
        assertFalse(SingleMediaPreviewPolicy.accept("video/mp4", "file:///sdcard/private/video.mp4"));
        assertFalse(SingleMediaPreviewPolicy.accept("audio/mpeg", "content://sample/song.mp3"));
        assertFalse(SingleMediaPreviewPolicy.accept("text/html", "content://sample/movie.mp4"));
    }

    @Test public void unknownMimeUsesOnlyKnownExtensions() {
        assertTrue(SingleMediaPreviewPolicy.isVideo(null, "content://provider/clip.mp4"));
        assertTrue(SingleMediaPreviewPolicy.isImage("application/octet-stream", "content://provider/cover.png"));
        assertFalse(SingleMediaPreviewPolicy.accept(null, "content://provider/malware.apk"));
        assertFalse(SingleMediaPreviewPolicy.accept(null, "content://provider/unrecognized"));
        assertFalse(SingleMediaPreviewPolicy.isVideo("image/jpeg", "content://provider/photo.jpg"));
    }
}
