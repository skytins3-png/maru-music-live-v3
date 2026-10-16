package com.maru.radio;

/** JVM regression for bounded compressor modes. Does not claim LUFS tests. */
public final class RadioLevelPolicySelfTest {
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError("FAIL: " + name);
        System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        check(RadioLevelPolicy.clamp(-100) == RadioLevelPolicy.EVEN,
                "corrupt preference picks moderate default, not unbounded gain");
        check(RadioLevelPolicy.clamp(500) == RadioLevelPolicy.EVEN,
                "oversized mode picks moderate default");
        check(RadioLevelPolicy.clamp(RadioLevelPolicy.OFF) == RadioLevelPolicy.OFF,
                "owner can disable DSP to hear Suno original");
        check(RadioLevelPolicy.NAMES.length == 3, "three explicit modes");
        check(RadioLevelPolicy.inputGainDb(RadioLevelPolicy.PRESENT) <= 4f,
                "boost capped to avoid excessive input gain");
        check(RadioLevelPolicy.outputGainDb(RadioLevelPolicy.PRESENT) <= 5f,
                "compressor makeup gain capped");
        check(RadioLevelPolicy.limiterCeilingDb() <= -1f,
                "master peaks protected below digital full scale");
        for (int mode = 1; mode <= 2; mode++) {
            float low = RadioLevelPolicy.approximateOutputDb(-29f, mode);
            float high = RadioLevelPolicy.approximateOutputDb(-8f, mode);
            check(high - low < 21f,
                    "song-level difference reduced by dynamic range in mode " + mode);
            check(high <= RadioLevelPolicy.limiterCeilingDb(),
                    "strong samples never predicted above peak ceiling " + mode);
            check(low > -29f, "quiet passages are raised in mode " + mode);
        }
        for (float db = -80f; db <= 0; db += 1f) {
            check(RadioLevelPolicy.approximateOutputDb(db, RadioLevelPolicy.OFF) == db,
                    "unprocessed original preserved at " + db + " dB");
        }
        System.out.println("PASS: bounded audio leveling policy only. "
                + "Device effects, LUFS and BIGO viewer route still unverified.");
    }
}
