package com.maru.radio;

/**
 * Safe, deliberately moderate playback-only dynamics presets for uploaded
 * original Suno music. ORIGINAL FILES ARE NEVER MODIFIED.
 *
 * The same compressor/limiter is applied to each track, reducing level swings
 * but NOT measuring per-song integrated LUFS, changing phone system volume,
 * or sending audio into BIGO's live microphone input.
 */
public final class RadioLevelPolicy {
    public static final int OFF = 0;
    public static final int EVEN = 1;
    public static final int PRESENT = 2;

    public static final String[] NAMES = {
        "원본 그대로 (보정 끔)",
        "방송 음량 균일 (권장)",
        "방송 음량 균일 + 더 또렷하게"
    };

    private RadioLevelPolicy() {}

    public static int clamp(int mode) {
        return mode < OFF || mode > PRESENT ? EVEN : mode;
    }

    public static float inputGainDb(int mode) {
        return mode == PRESENT ? 3.5f : mode == EVEN ? 2.0f : 0f;
    }

    public static float outputGainDb(int mode) {
        return mode == PRESENT ? 4.5f : mode == EVEN ? 3.5f : 0f;
    }

    public static float compressorRatio(int mode) {
        return mode == PRESENT ? 3.0f : mode == EVEN ? 2.5f : 1.0f;
    }

    public static float thresholdDb(int mode) {
        return mode == PRESENT ? -24f : -22f;
    }

    public static float limiterCeilingDb() { return -1.5f; }

    /**
     * Predict a single full-band static compression transfer function,
     * only for the JVM sanity tests. The actual Android DSP also has
     * attack/release, device implementation differences and stereo linking.
     */
    public static float approximateOutputDb(float inputDb, int mode) {
        int safe = clamp(mode);
        if (safe == OFF) return inputDb;
        float before = inputDb + inputGainDb(safe);
        float threshold = thresholdDb(safe);
        float after = before > threshold
                ? threshold + (before - threshold) / compressorRatio(safe)
                : before;
        return Math.min(limiterCeilingDb(), after + outputGainDb(safe));
    }
}
