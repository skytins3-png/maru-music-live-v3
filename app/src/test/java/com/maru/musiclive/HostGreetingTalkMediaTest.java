package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;

public class HostGreetingTalkMediaTest {
    private static final String STORY="content://maru/approved-story-video.mp4";
    private static final String GREET="content://maru/ahwan-greeting.mp4";
    private static final String TALK="content://maru/ahwan-dj-talk.mp4";
    private static final String SONG="content://maru/song-002-cover.png";
    private static final String DEFAULT="android.resource://com.maru.musiclive/123";

    @Test public void eachBroadcastSegmentSelectsOnlyItsOwnVideo() {
        assertEquals(GREET,BroadcastMediaSelector.chooseForScene("","","greeting",GREET,SONG,DEFAULT));
        assertEquals(TALK,BroadcastMediaSelector.chooseForScene("","","talk",TALK,SONG,DEFAULT));
        assertEquals(STORY,BroadcastMediaSelector.chooseForScene("story-001",STORY,"talk",TALK,SONG,DEFAULT));
        assertEquals(SONG,BroadcastMediaSelector.chooseForScene("","","",GREET,SONG,DEFAULT));
    }
    @Test public void unrelatedMediaNeverLeaksIntoUnassignedHostOrStorySegments() {
        assertEquals(DEFAULT,BroadcastMediaSelector.chooseForScene("","","greeting","",SONG,DEFAULT));
        assertEquals(DEFAULT,BroadcastMediaSelector.chooseForScene("","","talk","",SONG,DEFAULT));
        assertEquals(DEFAULT,BroadcastMediaSelector.chooseForScene("story-001","","talk",TALK,SONG,DEFAULT));
        assertEquals(DEFAULT,BroadcastMediaSelector.chooseForScene("","","","", "",DEFAULT));
        assertEquals(DEFAULT,BroadcastMediaSelector.chooseForScene("","","talk","https://evil/video.mp4",SONG,DEFAULT));
    }
    @Test public void noNetworkVideoIsConsideredApproved() {
        assertFalse(SingleMediaPreviewPolicy.isVideo("video/mp4","https://unapproved/media.mp4"));
        assertTrue(SingleMediaPreviewPolicy.isVideo("video/mp4",GREET));
    }
}
