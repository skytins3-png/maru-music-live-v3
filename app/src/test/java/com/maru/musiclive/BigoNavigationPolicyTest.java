package com.maru.musiclive;

import static org.junit.Assert.*;

import org.junit.Test;

public class BigoNavigationPolicyTest {
    @Test public void normalizesRequestedMode() {
        assertEquals(BigoNavigationPolicy.MODE_REGULAR,
                BigoNavigationPolicy.normalizeMode("일반 LIVE"));
        assertEquals(BigoNavigationPolicy.MODE_AUDIO,
                BigoNavigationPolicy.normalizeMode("오디오 LIVE"));
        assertEquals(BigoNavigationPolicy.MODE_AUDIO,
                BigoNavigationPolicy.normalizeMode("Audio Live"));
    }

    @Test public void recognizesBroadcastEntryAndModes() {
        assertTrue(BigoNavigationPolicy.isEntryLabel("Go Live"));
        assertTrue(BigoNavigationPolicy.isEntryLabel("라이브"));
        assertTrue(BigoNavigationPolicy.isModeLabel(
                "일반 LIVE", BigoNavigationPolicy.MODE_REGULAR));
        assertTrue(BigoNavigationPolicy.isModeLabel(
                "Audio Live", BigoNavigationPolicy.MODE_AUDIO));
    }

    @Test public void neverTreatsFinalStartAsModeSelection() {
        assertTrue(BigoNavigationPolicy.isFinalStartLabel("방송 시작"));
        assertTrue(BigoNavigationPolicy.isFinalStartLabel("Start Live"));
        assertFalse(BigoNavigationPolicy.isModeLabel(
                "방송 시작", BigoNavigationPolicy.MODE_REGULAR));
        assertFalse(BigoNavigationPolicy.isModeLabel(
                "Start Live", BigoNavigationPolicy.MODE_AUDIO));
    }
}
