package com.maru.musiclive;

import static org.junit.Assert.*;
import org.junit.Test;

public class BroadcastVisualProfileTest {
    @Test public void compactSizesAreStable() {
        assertTrue(BroadcastVisualProfile.isCompactMobileProfile());
        assertEquals(14f, BroadcastVisualProfile.EVENT_SP, 0f);
        assertEquals(17f, BroadcastVisualProfile.LYRIC_SP, 0f);
        assertTrue(BroadcastVisualProfile.BIGO_NATIVE_TOOLBAR_SAFE_BOTTOM_DP >= 200);
        assertTrue(BroadcastVisualProfile.LYRIC_BOTTOM_MARGIN_DP
                > BroadcastVisualProfile.BIGO_NATIVE_TOOLBAR_SAFE_BOTTOM_DP);
    }

    @Test public void overlayNeverExceedsScreen() {
        assertEquals(320, BroadcastVisualProfile.overlayWidthPx(320, 400));
        assertEquals(328, BroadcastVisualProfile.overlayWidthPx(400, 220));
    }
}
