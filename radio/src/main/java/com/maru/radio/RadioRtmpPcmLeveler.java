package com.maru.radio;

/**
 * Streaming 16-bit little-endian PCM loudness rider for the independent RTMP
 * encoder input. Uses no Android speaker, device volume, or microphone APIs.
 * A short-time RMS target gently corrects song-to-song level differences;
 * a stereo-linked peak guard avoids clipping on loud transients.
 *
 * This is RMS matching, NOT perceptual LUFS normalization. It changes only
 * in-flight network PCM, never original downloaded Suno media.
 */
public final class RadioRtmpPcmLeveler {
    private static final float TARGET_RMS = 0.12f;
    private static final float PEAK_CEILING = 0.90f;
    private static final float MIN_GAIN = 0.30f;
    private static final float MAX_GAIN = 2.50f;

    private final float energyStep;
    private final float attenuationStep;
    private final float boostStep;
    private final float peakRecoveryStep;
    private double energy = TARGET_RMS * TARGET_RMS;
    private float musicGain = 1.0f;
    private float peakGain = 1.0f;

    public RadioRtmpPcmLeveler(int sampleRate) {
        if (sampleRate < 8000 || sampleRate > 192000)
            throw new IllegalArgumentException("Unsupported PCM sample rate");
        energyStep = response(sampleRate, 0.22f);
        attenuationStep = response(sampleRate, 0.14f);
        boostStep = response(sampleRate, 0.85f);
        peakRecoveryStep = response(sampleRate, 0.20f);
    }

    private static float response(int rate, float seconds) {
        return (float) (1.0 - Math.exp(-1.0 / (rate * seconds)));
    }

    public void process(byte[] pcm, int offset, int length, boolean stereo) {
        if (pcm == null || offset < 0 || length <= 0 || offset > pcm.length) return;
        final int channels = stereo ? 2 : 1;
        final int frameSize = channels * 2;
        final int end = offset + Math.min(length, pcm.length - offset);
        for (int pos = offset; pos + frameSize <= end; pos += frameSize) {
            float squareSum = 0f;
            float peak = 0f;
            for (int c = 0; c < channels; c++) {
                int at = pos + c * 2;
                float sample = (short) ((pcm[at] & 255) | (pcm[at + 1] << 8)) / 32768f;
                squareSum += sample * sample;
                peak = Math.max(peak, Math.abs(sample));
            }
            energy += energyStep * ((squareSum / channels) - energy);
            float rms = (float) Math.sqrt(Math.max(0.0, energy));
            // Avoid boosting room noise and digital silence between songs.
            float desired = rms < 0.009f ? 1f : TARGET_RMS / rms;
            desired = Math.max(MIN_GAIN, Math.min(MAX_GAIN, desired));
            float step = desired < musicGain ? attenuationStep : boostStep;
            musicGain += (desired - musicGain) * step;

            // One linked limiter across both channels preserves the stereo image.
            float guard = peak * musicGain;
            float required = guard <= PEAK_CEILING ? 1f : PEAK_CEILING / guard;
            if (required < peakGain) peakGain = required; // immediate attack
            else peakGain = Math.min(required,
                    peakGain + (1f - peakGain) * peakRecoveryStep);
            float multiplier = musicGain * peakGain;

            for (int c = 0; c < channels; c++) {
                int at = pos + c * 2;
                int raw = (short) ((pcm[at] & 255) | (pcm[at + 1] << 8));
                float out = Math.max(-PEAK_CEILING,
                        Math.min(PEAK_CEILING, (raw / 32768f) * multiplier));
                int fixed = Math.round(out * 32767f);
                pcm[at] = (byte) fixed;
                pcm[at + 1] = (byte) (fixed >>> 8);
            }
        }
    }
}
