package com.maru.musiclive;

import static org.junit.Assert.*;

import org.junit.Test;

public class OneClickBroadcastPlanTest {
    @Test public void startsOnlyWhenReady() {
        assertTrue(OneClickBroadcastPlan.canStart(1, true, false));
        assertFalse(OneClickBroadcastPlan.canStart(0, true, false));
        assertFalse(OneClickBroadcastPlan.canStart(1, false, false));
        assertFalse(OneClickBroadcastPlan.canStart(1, true, true));
    }

    @Test public void usesBigoAndPortraitMode() {
        assertEquals("sg.bigo.live", OneClickBroadcastPlan.BIGO_PACKAGE);
        assertEquals(BroadcastMode.PORTRAIT_9_16, OneClickBroadcastPlan.BROADCAST_MODE);
        assertEquals(900L, OneClickBroadcastPlan.BIGO_OPEN_DELAY_MS);
        assertFalse(OneClickBroadcastPlan.REQUIRES_MARU_SCREEN_CAPTURE);
        assertTrue(OneClickBroadcastPlan.REQUIRES_BIGO_SCREEN_CAPTURE_CONSENT);
        assertTrue(OneClickBroadcastPlan.USES_BIGO_NATIVE_TOOLBAR);
        assertFalse(OneClickBroadcastPlan.CONTROLS_EXTERNAL_APP_UI);
        assertTrue(OneClickBroadcastPlan.RESTORES_MARU_PLAYBACK_CONTROLS);
        assertEquals(3, OneClickBroadcastPlan.PLAYBACK_START_RETRY_COUNT);
        assertEquals(700L, OneClickBroadcastPlan.PLAYBACK_START_RETRY_DELAY_MS);
    }
}
