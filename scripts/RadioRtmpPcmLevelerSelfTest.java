package com.maru.radio;

/** Offline JVM signal-shaping tests; NOT a real RTMP / BIGO receiver test. */
public final class RadioRtmpPcmLevelerSelfTest {
    private static final int RATE = 48000;

    private static void require(boolean passed, String name) {
        if (!passed) throw new AssertionError("FAIL: " + name);
        System.out.println("PASS: " + name);
    }

    private static int signed(byte[] pcm, int at) {
        return (short) ((pcm[at] & 255) | (pcm[at + 1] << 8));
    }

    private static void put(byte[] pcm, int at, float amplitude) {
        int value = Math.round(Math.max(-1f, Math.min(1f, amplitude)) * 32767f);
        pcm[at] = (byte) value;
        pcm[at + 1] = (byte) (value >>> 8);
    }

    private static double signalRms(RadioRtmpPcmLeveler leveler, float amplitude,
            boolean stereo) {
        final int framesPerChunk = 512;
        int channels = stereo ? 2 : 1;
        double squared = 0;
        long counted = 0;
        for (int n = 0; n < 4 * RATE; n += framesPerChunk) {
            int frames = Math.min(framesPerChunk, 4 * RATE - n);
            byte[] block = new byte[frames * channels * 2];
            for (int f = 0; f < frames; f++) {
                float sample = amplitude * (float) Math.sin(2 * Math.PI * 443 * (n + f) / RATE);
                for (int c = 0; c < channels; c++) put(block, 2 * (f * channels + c), sample);
            }
            leveler.process(block, 0, block.length, stereo);
            if (n >= 3 * RATE) {
                for (int f = 0; f < frames * channels; f++) {
                    double v = signed(block, f * 2) / 32768.0;
                    squared += v * v;
                    counted++;
                }
            }
        }
        return Math.sqrt(squared / counted);
    }

    public static void main(String[] args) {
        double quiet = signalRms(new RadioRtmpPcmLeveler(RATE), 0.07f, true);
        double loud = signalRms(new RadioRtmpPcmLeveler(RATE), 0.44f, true);
        System.out.println("Measured quiet/loud network RMS: " + quiet + " / " + loud);
        require(Math.max(quiet, loud) / Math.min(quiet, loud) < 1.35,
                "quiet and loud songs converge to similar network loudness");

        RadioRtmpPcmLeveler stereo = new RadioRtmpPcmLeveler(RATE);
        byte[] pcm = new byte[20];
        pcm[0] = 101; pcm[1] = 102; pcm[18] = 103; pcm[19] = 104;
        for (int at = 2; at < 18; at += 4) {
            put(pcm, at, 0.43f);
            put(pcm, at + 2, 0.43f);
        }
        stereo.process(pcm, 2, 16, true);
        require(pcm[0] == 101 && pcm[1] == 102 && pcm[18] == 103 && pcm[19] == 104,
                "processing respects offset and length");
        for (int at = 2; at < 18; at += 4)
            require(signed(pcm, at) == signed(pcm, at + 2),
                    "linked stereo channels stay identical with equal inputs");

        RadioRtmpPcmLeveler peak = new RadioRtmpPcmLeveler(RATE);
        byte[] hot = new byte[8];
        put(hot, 0, 0.999f); put(hot, 2, -0.999f);
        put(hot, 4, 0.999f); put(hot, 6, -0.999f);
        peak.process(hot, 0, hot.length, true);
        for (int at = 0; at < hot.length; at += 2)
            require(Math.abs(signed(hot, at)) <= 29491,
                    "limiter keeps network PCM below -0.9 dBFS");

        RadioRtmpPcmLeveler silence = new RadioRtmpPcmLeveler(RATE);
        byte[] zero = new byte[2048];
        for (int i = 0; i < 400; i++) silence.process(zero, 0, zero.length, true);
        require(signed(zero, 0) == 0 && signed(zero, 2) == 0,
                "digital silence stays silent, no auto-gain noise");
        System.out.println("RTMP PCM leveler offline tests passed; no BIGO listener was tested.");
    }
}
