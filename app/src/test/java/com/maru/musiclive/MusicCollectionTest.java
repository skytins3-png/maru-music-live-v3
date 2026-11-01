package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

public class MusicCollectionTest {
    @Test public void differentFacetsCanIncludeOneSongAtOnce() {
        List<String> ids=Arrays.asList("korean-trot","chinese-ballad","western-pop");
        List<String> regions=Arrays.asList(MusicCollection.KOREAN,
                MusicCollection.CHINESE,MusicCollection.WESTERN);
        List<String> styles=Arrays.asList(MusicCollection.TROT,
                MusicCollection.BALLAD,MusicCollection.OTHER);
        List<String> sources=Arrays.asList(MusicCollection.SUNO,
                MusicCollection.PLATFORM,MusicCollection.DEVICE);
        assertEquals(Arrays.asList("korean-trot"),
                MusicCollection.filtered(ids,regions,styles,sources,MusicCollection.TROT));
        assertEquals(Arrays.asList("korean-trot"),
                MusicCollection.filtered(ids,regions,styles,sources,MusicCollection.KOREAN));
        assertEquals(Arrays.asList("chinese-ballad"),
                MusicCollection.filtered(ids,regions,styles,sources,MusicCollection.BALLAD));
        assertEquals(Arrays.asList("chinese-ballad"),
                MusicCollection.filtered(ids,regions,styles,sources,MusicCollection.CHINESE));
        assertEquals(Arrays.asList("korean-trot"),
                MusicCollection.filtered(ids,regions,styles,sources,MusicCollection.SUNO));
        assertEquals(ids,MusicCollection.filtered(ids,regions,styles,sources,MusicCollection.ALL));
    }
    @Test public void deliberateClassificationBeatsFilenameGuesswork() {
        assertEquals(MusicCollection.TROT,MusicCollection.detectStyle("가을 남자 트로트.mp3"));
        assertEquals(MusicCollection.BALLAD,MusicCollection.detectStyle("Autumn ballad.wav"));
        assertEquals(MusicCollection.CHINESE,MusicCollection.detectRegion("云南的清晨.mp3"));
        assertEquals(MusicCollection.KOREAN,MusicCollection.detectRegion("내사랑.mp3"));
        assertEquals(MusicCollection.INSTRUMENTAL,MusicCollection.detectRegion("고쟁 연주.mp3"));
        assertEquals(MusicCollection.OTHER,MusicCollection.detectStyle("내사랑.mp3"));
        assertFalse(MusicCollection.isSource("listener-unreviewed"));
    }
    @Test public void cataloguePathsRejectTraversalAndExternalNetworkUrls() {
        assertTrue(PublicCatalogImporter.trustedRelativeAudio("tracks/2026-가을-사랑.mp3"));
        assertFalse(PublicCatalogImporter.trustedRelativeAudio("https://example.com/track.mp3"));
        assertFalse(PublicCatalogImporter.trustedRelativeAudio("tracks/../bad.mp3"));
        assertFalse(PublicCatalogImporter.trustedRelativeAudio("tracks/folder/bad.mp3"));
        assertFalse(PublicCatalogImporter.trustedRelativeAudio("tracks/virus.exe"));
        assertFalse(PublicCatalogImporter.trustedRelativeAudio("tracks/bad.mp3?token=123"));
    }
}
