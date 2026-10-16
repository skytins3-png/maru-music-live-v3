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
 * Gentle RMS gain adaptation and stereo-linked peak guarding apply before
 * network AAC encoding. No input WAV/MP3 gets rewritten. NOT LUFS matching.
 *
 * Sample rate/stereo requirements are dictated by the decoder. A separate
 * receiver-side BIGO volume test is still mandatory.
 */
public final class RadioRtmpLevelAudioSource extends AudioSource {
    private final AudioFileSource file;
    private final RadioRtmpPcmVoiceMixer voiceMixer = new RadioRtmpPcmVoiceMixer();
    private boolean outputStereo;
    private RadioRtmpPcmLeveler musicLeveler;

    public RadioRtmpLevelAudioSource(Context context, Uri source,
            kotlin.jvm.functions.Function1<Boolean, Unit> onFinish) {
        file = new AudioFileSource(context, source, false, onFinish);
    }

    @Override protected boolean create(int rate, boolean isStereo,
            boolean echoCanceler, boolean suppressor) {
        outputStereo = isStereo;
        musicLeveler = new RadioRtmpPcmLeveler(rate);
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
        if (frame == null || musicLeveler == null) return;
        // RMS compensation and linked peak guard touch only AAC-bound PCM.
        // The phone media-volume mute setting is never read or applied here.
        musicLeveler.process(frame.getBuffer(), frame.getOffset(),
                frame.getSize(), outputStereo);
    }

    public boolean queueAnnouncement(short[] monoPcm) {
        return voiceMixer.enqueue(monoPcm);
    }

    public int queuedAnnouncements() { return voiceMixer.queued(); }

    public void clearAnnouncements() { voiceMixer.clear(); }

    public void replaceFile(Context ctx, Uri source) throws java.io.IOException {
        // Retain the adapted level between songs; avoid a sudden gain jump.
        file.replaceFile(ctx, source);
    }

    @Override public void stop() { file.stop(); }
    @Override public boolean isRunning() { return file.isRunning(); }
    @Override public void release() { voiceMixer.clear(); file.release(); }
}
