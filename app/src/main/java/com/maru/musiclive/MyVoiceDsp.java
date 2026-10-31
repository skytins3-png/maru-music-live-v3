package com.maru.musiclive;

/**
 * Pure audio DSP for a user-selected recording of THEIR OWN voice.
 * No voice identification, conversion to somebody else's voice, or generative cloning.
 * Output is speech-oriented mono PCM16; original source is never changed.
 */
public final class MyVoiceDsp {
    private MyVoiceDsp() {}

    public static short[] enhance(short[] input, int sampleRate) {
        if (input == null || input.length == 0)
            throw new IllegalArgumentException("Empty original voice recording");
        if (sampleRate < 8000 || sampleRate > 192000)
            throw new IllegalArgumentException("Unsupported sample rate");
        // DC/high-pass cut around 75Hz removes pops and low rumble.
        double rc = 1.0 / (2.0 * Math.PI * 75.0);
        double dt = 1.0 / sampleRate;
        double hpA = rc / (rc + dt);
        double previousInput = 0, previousOutput = 0;
        double[] filtered = new double[input.length];
        double energy = 0;
        for (int i = 0; i < input.length; i++) {
            double v = input[i] / 32768.0;
            double hp = hpA * (previousOutput + v - previousInput);
            previousInput = v;
            previousOutput = hp;
            filtered[i] = hp;
            energy += hp * hp;
        }
        double rms = Math.sqrt(energy / input.length);
        if (rms < 0.00015) return new short[input.length];
        // Keep speech natural: cap gain on quiet recordings instead of boosting noise.
        double makeup = Math.min(3.0, Math.max(0.4, 0.115 / rms));
        final double threshold = 0.18; // -15 dB
        double gainState = 1.0;
        double gateState = 1.0;
        double attack = Math.exp(-1.0 / (0.008 * sampleRate));
        double release = Math.exp(-1.0 / (0.17 * sampleRate));
        double gateAttack = Math.exp(-1.0 / (0.02 * sampleRate));
        double gateRelease = Math.exp(-1.0 / (0.24 * sampleRate));
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            double v = filtered[i] * makeup;
            double amplitude = Math.abs(v);
            // Mild downward compressor to stabilize speaking level.
            double desired = amplitude > threshold
                    ? Math.pow(threshold / Math.max(amplitude, 1e-9), 0.53)
                    : 1.0;
            gainState = desired < gainState
                    ? attack * gainState + (1.0 - attack) * desired
                    : release * gainState + (1.0 - release) * desired;
            // Soft expander reduces gaps between phrases, not background noise
            // overlapping speech. The gate responds smoothly to avoid chopping.
            double gateTarget = Math.abs(filtered[i]) < 0.003 ? 0.72 : 1.0;
            gateState = gateTarget > gateState
                    ? gateAttack * gateState + (1.0 - gateAttack) * gateTarget
                    : gateRelease * gateState + (1.0 - gateRelease) * gateTarget;
            double shaped = v * gainState * gateState;
            // Transparent soft limiting rather than hard clipping.
            double limit = shaped / (1.0 + Math.abs(shaped) * 0.12);
            int pcm = (int) Math.round(Math.max(-0.96, Math.min(0.96, limit)) * 32767);
            output[i] = (short) pcm;
        }
        return output;
    }
}
