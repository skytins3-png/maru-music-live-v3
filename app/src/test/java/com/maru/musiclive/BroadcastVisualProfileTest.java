package com.maru.musiclive;

import static org.junit.Assert.*;
import org.junit.Test;

public class BroadcastVisualProfileTest {
    @Test public void compactSizesAreStable() {
        assertTrue(BroadcastVisualProfile.isCompactMobileProfile());
        assertEquals(14f, BroadcastVisualProfile.EVENT_SP, 0f);
        assertEquals(17f, BroadcastVisualProfile.LYRIC_SP, 0f);
    }

    @Test public void overlayNeverExceedsScreen() {
        assertEquals(320, BroadcastVisualProfile.overlayWidthPx(320, 400));
        assertEquals(328, BroadcastVisualProfile.overlayWidthPx(400, 220));
    }
}
