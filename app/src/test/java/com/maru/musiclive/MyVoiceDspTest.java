package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;

public class MyVoiceDspTest {
    @Test public void silentRecordingRemainsSilent() {
        short[] silence = new short[16000];
        short[] cleaned = MyVoiceDsp.enhance(silence, 16000);
        assertEquals(silence.length, cleaned.length);
        for (short x : cleaned) assertEquals(0, x);
    }

    @Test public void quietRecordedSpeechGetsImprovedWithoutClipping() {
        int n = 16000;
        short[] input = new short[n];
        for (int i = 0; i < n; i++) {
            input[i] = (short) Math.round(1400 * Math.sin(i * 2 * Math.PI * 220.0 / n * 16));
        }
        short[] cleaned = MyVoiceDsp.enhance(input, 16000);
        assertEquals(n, cleaned.length);
        long origEnergy = 0;
        long cleanEnergy = 0;
        for (int i = 0; i < n; i++) {
            origEnergy += (long) input[i] * input[i];
            cleanEnergy += (long) cleaned[i] * cleaned[i];
            assertTrue("limiter protects output", Math.abs((int) cleaned[i]) < 32767);
        }
        assertTrue("audio is not muted", cleanEnergy > origEnergy);
        assertTrue("audio doesn't explode", cleanEnergy < 24 * origEnergy);
    }

    @Test public void rejectsEmptyAndUnreasonableSampleRates() {
        try {
            MyVoiceDsp.enhance(new short[0], 16000);
            fail("must reject empty");
        } catch (IllegalArgumentException expected) {}
        try {
            MyVoiceDsp.enhance(new short[]{2,3}, 1000);
            fail("must reject unsupported");
        } catch (IllegalArgumentException expected) {}
    }
}
