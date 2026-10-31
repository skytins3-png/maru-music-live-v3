package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrustedWebStudioTest {
    @Test public void allowThePublisherAndItsOwnMusicFiles() {
        assertTrue(TrustedWebStudio.mayNavigateInside(TrustedWebStudio.URL));
        assertTrue(TrustedWebStudio.mayNavigateInside(
                "https://skytins3-png.github.io/maru-ai-song-studio/music-share/radio.html"));
        assertTrue(TrustedWebStudio.mayNavigateInside(
                "https://skytins3-png.github.io/maru-ai-song-studio/?song=3"));
    }
    @Test public void rejectOtherHostsProtocolsAndPathTraversal() {
        assertFalse(TrustedWebStudio.mayNavigateInside(null));
        assertFalse(TrustedWebStudio.mayNavigateInside("file:///sdcard/music.mp3"));
        assertFalse(TrustedWebStudio.mayNavigateInside("javascript:alert(1)"));
        assertFalse(TrustedWebStudio.mayNavigateInside(
                "http://skytins3-png.github.io/maru-ai-song-studio/"));
        assertFalse(TrustedWebStudio.mayNavigateInside(
                "https://skytins3-png.github.io.evil.example/maru-ai-song-studio/"));
        assertFalse(TrustedWebStudio.mayNavigateInside(
                "https://skytins3-png.github.io/maru-ai-song-studio-evil/"));
        assertFalse(TrustedWebStudio.mayNavigateInside(
                "https://skytins3-png.github.io/maru-ai-song-studio/../../outside"));
        assertFalse(TrustedWebStudio.mayNavigateInside(
                "https://skytins3-png.github.io/maru-ai-song-studio/%2e%2e/other"));
        assertFalse(TrustedWebStudio.mayNavigateInside(
                "https://skytins3-png.github.io/maru-ai-song-studio/%2Fprivate"));
        assertFalse(TrustedWebStudio.mayNavigateInside(
                "https://skytins3-png.github.io:1337/maru-ai-song-studio/"));
        assertFalse(TrustedWebStudio.mayNavigateInside(
                "https://unknown@skytins3-png.github.io/maru-ai-song-studio/"));
    }
}
