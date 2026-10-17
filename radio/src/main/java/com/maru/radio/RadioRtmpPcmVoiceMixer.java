package com.maru.radio;

import java.util.ArrayDeque;

/**
 * Mono spoken clips (at the outgoing media sample rate) are mixed directly into
 * signed little-endian PCM destined for RTMP, NEVER to the Android speaker.
 * At most 3 short announcements are buffered. Limiter prevents PCM clipping.
 * Source music is ducked only while TTS is actually speaking.
 */
public final class RadioRtmpPcmVoiceMixer {
    private static final int MAX_CLIPS = 3;
    private final ArrayDeque<short[]> pending = new ArrayDeque<>();
    private short[] current;
    private int index;

    public synchronized boolean enqueue(short[] monoPcm) {
        if (monoPcm == null || monoPcm.length == 0 || monoPcm.length > 1500000)
            return false;
        if (pending.size() >= MAX_CLIPS) return false;
        pending.addLast(monoPcm);
        return true;
    }

    public synchronized void clear() {
        current = null;
        index = 0;
        pending.clear();
    }

    public synchronized int queued() {
        return pending.size() + (current == null ? 0 : 1);
    }

    public synchronized void mix(byte[] pcm, int offset, int size, boolean stereo) {
        if (pcm == null || size <= 0) return;
        int channels = stereo ? 2 : 1;
        int frameSize = channels * 2;
        int begin = Math.max(0, offset);
        int end = Math.min(pcm.length, begin + size);
        if (current == null && !pending.isEmpty()) {
            current = pending.removeFirst();
            index = 0;
        }
        for (int pos = begin; pos + frameSize <= end; pos += frameSize) {
            if (current == null) break;
            if (index >= current.length) {
                current = pending.pollFirst();
                index = 0;
                if (current == null) break;
            }
            float speech = current[index++] / 32768f;
            for (int c = 0; c < channels; c++) {
                int p = pos + c * 2;
                int original = (short) ((pcm[p] & 0xFF) | (pcm[p + 1] << 8));
                // Music ducks to 22% while announcer is speaking; prevents
                // song from masking the voice. Hard bound ≈ -0.8 dBFS.
                float mixed = (original / 32768f) * .22f + speech * .79f;
                int fixed = Math.round(Math.max(-.91f, Math.min(.91f, mixed)) * 32767);
                pcm[p] = (byte) fixed;
                pcm[p + 1] = (byte) (fixed >>> 8);
            }
        }
        if (current != null && index >= current.length) {
            current = pending.pollFirst();
            index = 0;
        }
    }
}
