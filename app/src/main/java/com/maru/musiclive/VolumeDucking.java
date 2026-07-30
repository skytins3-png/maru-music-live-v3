package com.maru.musiclive;

public final class VolumeDucking {
    private float guidance = 1f;
    private float host = 1f;

    public synchronized void setGuidance(float value) {
        guidance = clamp(value);
    }

    public synchronized void setHost(float value) {
        host = clamp(value);
    }

    public synchronized float effective() {
        return Math.min(guidance, host);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
