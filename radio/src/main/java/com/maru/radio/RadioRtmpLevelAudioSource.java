package com.maru.radio;

import android.content.Context;
import android.net.Uri;

import com.pedro.encoder.Frame;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.sources.audio.AudioFileSource;
import com.pedro.encoder.input.sources.audio.AudioSource;
import kotlin.Unit;

/**
 * On-the-fly, MONITOR-INDEPENDENT PCM loudness control for RTMP ONLY.
 *
 * Source PCM never enters the phone speaker or media volume stream.
 * Each 16-bit PCM sample gets slowly smoothed gain and a -0.75 dBFS peak
 * limiter. No input WAV/MP3 gets rewritten. This is NOT LUFS-based matching.
 *
 * Sample rate/stereo requirements are dictated by the decoder. A separate
 * receiver-side BIGO volume test is still mandatory.
 */
public final class RadioRtmpLevelAudioSource extends AudioSource {
    private final AudioFileSource file;
    private final RadioRtmpPcmVoiceMixer voiceMixer = new RadioRtmpPcmVoiceMixer();
    private boolean outputStereo;
    private float envelope = 0f;
    private float smoothedGain = 1f;

    public RadioRtmpLevelAudioSource(Context context, Uri source,
            kotlin.jvm.functions.Function1<Boolean, Unit> onFinish) {
        file = new AudioFileSource(context, source, false, onFinish);
    }

    @Override protected boolean create(int rate, boolean isStereo,
            boolean echoCanceler, boolean suppressor) {
        outputStereo = isStereo;
        return file.init(rate, isStereo, echoCanceler, suppressor);
    }

    @Override public void start(GetMicrophoneData receive) {
        file.start(frame -> {
            limitPcm(frame);
            // This mixes synthesised Ahwan PCM before network AAC encoding;
            // STREAM_MUSIC / phone mute doesn't touch these bytes.
            voiceMixer.mix(frame.getBuffer(), frame.getOffset(), frame.getSize(), outputStereo);
            receive.inputPCMData(frame);
        });
    }

    private void limitPcm(Frame frame) {
        if (frame == null || frame.getBuffer() == null) return;
        byte[] bytes = frame.getBuffer();
        int start = Math.max(0, frame.getOffset());
        int end = Math.min(bytes.length, start + Math.max(0, frame.getSize()));
        // RootEncoder AudioFileSource delivers signed 16-bit little-endian PCM.
        for (int i = start; i + 1 < end; i += 2) {
            int raw = (short) ((bytes[i] & 0xFF) | (bytes[i + 1] << 8));
            float sample = raw / 32768f;
            float magnitude = Math.abs(sample);
            envelope = Math.max(magnitude, envelope * .99995f);
            // Moderate upward gain for quiet songs, attenuate excessive peaks.
            float wanted = Math.min(1.70f, .87f / Math.max(.04f, envelope));
            smoothedGain = .998f * smoothedGain + .002f * wanted;
            float output = Math.max(-.915f, Math.min(.915f, sample * smoothedGain));
            int fixed = Math.round(output * 32767f);
            bytes[i] = (byte) fixed;
            bytes[i + 1] = (byte) (fixed >>> 8);
        }
    }

    public boolean queueAnnouncement(short[] monoPcm) {
        return voiceMixer.enqueue(monoPcm);
    }

    public int queuedAnnouncements() { return voiceMixer.queued(); }

    public void clearAnnouncements() { voiceMixer.clear(); }

    public void replaceFile(Context ctx, Uri source) throws java.io.IOException {
        envelope = 0f;
        smoothedGain = 1f;
        file.replaceFile(ctx, source);
    }

    @Override public void stop() { file.stop(); }
    @Override public boolean isRunning() { return file.isRunning(); }
    @Override public void release() { voiceMixer.clear(); file.release(); }
}
