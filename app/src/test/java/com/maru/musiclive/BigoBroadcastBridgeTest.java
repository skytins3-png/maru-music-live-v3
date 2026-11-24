package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;

public class BigoBroadcastBridgeTest {
    @Test public void safeExportFileNameHasSourceFingerprint() {
        String one=BigoMusicExportPlan.safeName("가을바다.mp3","content://media/1");
        String two=BigoMusicExportPlan.safeName("가을바다.mp3","content://media/2");
        assertTrue(one.startsWith("MARU_"));
        assertTrue(one.endsWith("_가을바다.mp3"));
        assertNotEquals(one,two);
        assertEquals(one,BigoMusicExportPlan.safeName("가을바다.mp3","content://media/1"));
    }

    @Test public void rejectNonAudioAndUnsafeExtensions() {
        assertEquals("",BigoMusicExportPlan.safeName("malicious.apk","content://media/1"));
        assertEquals("",BigoMusicExportPlan.safeName("../secret","content://media/1"));
        assertEquals("",BigoMusicExportPlan.safeName("track.mp3",null));
        assertEquals("audio/mp4",BigoMusicExportPlan.mime("song.m4a"));
        assertEquals("audio/mpeg",BigoMusicExportPlan.mime("song.mp3"));
        assertEquals("",BigoMusicExportPlan.mime("song.html"));
    }

    @Test public void listenerCheckNeverSaysSuccessWithoutExternalEvidence() {
        boolean[] f={false,false,false,false};
        assertFalse(BigoListenerEvidence.complete(f));
        assertTrue(BigoListenerEvidence.diagnosis(f).contains("직접"));
        f[0]=true;
        assertTrue(BigoListenerEvidence.diagnosis(f).contains("모두 안 들립니다"));
        f[1]=true;
        assertTrue(BigoListenerEvidence.diagnosis(f).contains("내부 재생"));
        f[2]=true;
        assertTrue(BigoListenerEvidence.diagnosis(f).contains("다음 곡"));
        f[3]=true;
        assertTrue(BigoListenerEvidence.complete(f));
        assertTrue(BigoListenerEvidence.diagnosis(f).contains("서버 인증은 아닙니다"));
    }

    @Test public void malformedReceptionCannotPass() {
        assertFalse(BigoListenerEvidence.complete(null));
        assertFalse(BigoListenerEvidence.complete(new boolean[]{true,true,true}));
    }

    @Test public void officialBigoAppUiMustNotBeAutomated() {
        assertFalse(OneClickBroadcastPlan.CONTROLS_EXTERNAL_APP_UI);
        assertFalse(OneClickBroadcastPlan.USES_USER_ENABLED_ACCESSIBILITY_NAVIGATOR);
        assertTrue(OneClickBroadcastPlan.STOPS_BEFORE_FINAL_BROADCAST_START);
    }
}
