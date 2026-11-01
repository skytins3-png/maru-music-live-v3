package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;

public class BroadcastMediaSelectorTest {
    private static final String STORY = "content://local/story.png";
    private static final String SONG = "content://local/song.mp4";
    private static final String FALLBACK = "android.resource://com.maru.musiclive/raw/default";

    @Test public void approvedStoryOverridesSongArtwork() {
        assertEquals(STORY,BroadcastMediaSelector.choose("story-a",STORY,SONG,FALLBACK));
        assertEquals(FALLBACK,BroadcastMediaSelector.choose("story-a","",SONG,FALLBACK));
        assertEquals(FALLBACK,BroadcastMediaSelector.choose("story-a","https://attacker.example/evil.mp4",SONG,FALLBACK));
    }
    @Test public void eachTrackUsesItsOwnMediaThenFallsBack() {
        assertEquals(SONG,BroadcastMediaSelector.choose("","",SONG,FALLBACK));
        assertEquals(FALLBACK,BroadcastMediaSelector.choose("","","",FALLBACK));
        assertEquals("",BroadcastMediaSelector.choose("","","","https://attacker.example/remote.mp4"));
        assertEquals(FALLBACK,BroadcastMediaSelector.choose(null,null,"file:///private.mp4",FALLBACK));
    }
    @Test public void noUserMediaDoesNotRequireNetworkOrBigoPermissions() {
        assertTrue(SingleMediaPreviewPolicy.isVideo("video/mp4",FALLBACK));
        assertFalse(SingleMediaPreviewPolicy.accept("video/mp4","https://third-party/video.mp4"));
    }
}
