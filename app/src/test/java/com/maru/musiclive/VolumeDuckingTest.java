package com.maru.musiclive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Covers the BIGO handoff bug where an Activity was destroyed mid-duck. */
public final class VolumeDuckingTest {
    private static final float EPSILON = 0.001f;

    @Test public void lostActivityCannotLeaveHostVolumeStuckAtEighteenPercent() {
        VolumeDucking volume = new VolumeDucking();
        volume.setHostTimed(0.18f, 1_000L, 15_000L);
        assertEquals(0.18f, volume.effective(), EPSILON);
        assertFalse(volume.restoreExpired(15_999L));
        assertEquals(0.18f, volume.effective(), EPSILON);
        assertTrue(volume.restoreExpired(16_000L));
        assertEquals(1f, volume.effective(), EPSILON);
        assertEquals(0L, volume.nextExpiryMs());
    }

    @Test public void overlappingHostAndGuidanceDucksRestoreIndependently() {
        VolumeDucking volume = new VolumeDucking();
        volume.setHostTimed(0.18f, 100L, 500L);
        volume.setGuidanceTimed(0.35f, 120L, 1_000L);
        assertEquals(0.18f, volume.effective(), EPSILON);
        assertEquals(600L, volume.nextExpiryMs());
        assertTrue(volume.restoreExpired(600L));
        assertEquals(0.35f, volume.effective(), EPSILON);
        assertEquals(1_120L, volume.nextExpiryMs());
        assertTrue(volume.restoreExpired(1_120L));
        assertEquals(1f, volume.effective(), EPSILON);
    }

    @Test public void newHostDuckSupersedesPreviousTimeout() {
        VolumeDucking volume = new VolumeDucking();
        volume.setHostTimed(0.18f, 100L, 500L);
        volume.setHostTimed(0.40f, 400L, 500L);
        assertFalse(volume.restoreExpired(600L));
        assertEquals(0.40f, volume.effective(), EPSILON);
        assertTrue(volume.restoreExpired(900L));
        assertEquals(1f, volume.effective(), EPSILON);
    }

    @Test public void explicitRestoreImmediatelyRemovesAllAttenuation() {
        VolumeDucking volume = new VolumeDucking();
        volume.setHostTimed(0f, 20L, 5_000L);
        volume.setGuidanceTimed(0.2f, 20L, 5_000L);
        assertEquals(0f, volume.effective(), EPSILON);
        volume.restoreAll();
        assertEquals(1f, volume.effective(), EPSILON);
        assertEquals(0L, volume.nextExpiryMs());
    }

    @Test public void fullVolumeDoesNotCreateAnExpiryAndInvalidVolumeIsSafe() {
        VolumeDucking volume = new VolumeDucking();
        volume.setHostTimed(1f, 1_000L, 5_000L);
        volume.setGuidanceTimed(Float.NaN, 1_000L, 5_000L);
        assertEquals(0L, volume.nextExpiryMs());
        assertEquals(1f, volume.effective(), EPSILON);
    }

    @Test public void expiryAvoidsOverflowAtLargeMonotonicTime() {
        VolumeDucking volume = new VolumeDucking();
        volume.setHostTimed(0.18f, Long.MAX_VALUE - 4L, 10_000L);
        assertEquals(Long.MAX_VALUE, volume.nextExpiryMs());
        assertFalse(volume.restoreExpired(Long.MAX_VALUE - 1L));
        assertTrue(volume.restoreExpired(Long.MAX_VALUE));
    }
}
