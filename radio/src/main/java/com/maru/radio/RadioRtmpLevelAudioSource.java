package com.maru.radio;

import android.content.Context;
import android.net.Uri;

import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.sources.audio.AudioFileSource;
import com.pedro.encoder.input.sources.audio.AudioSource;
import kotlin.Unit;

/**
 * DEVICE-MUTE-INDEPENDENT RTMP file-audio path, with no automatic music gain.
 *
 * Android speaker/media volume is NOT an input to the network PCM path.
 * Decoded original PCM goes straight to AAC encoding at its file-defined level;
 * it is NOT dynamically amplified, attenuated, equalized or normalized.
 *
 * When Ahwan speaks, the optional voice mixer ducks music ONLY for that spoken
 * segment. No announcement => voiceMixer.mix() returns without changing bytes.
 *
 * The class name is retained to avoid breaking the existing service integration.
 * This is an authorized RTMP route, NOT ordinary BIGO Audio LIVE microphone/BGM.
 * Only actual receiver testing establishes whether a BIGO viewer hears the mix.
 */
public final class RadioRtmpLevelAudioSource extends AudioSource {
    private final AudioFileSource file;
    private final RadioRtmpPcmVoiceMixer voiceMixer = new RadioRtmpPcmVoiceMixer();
    private boolean outputStereo;

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
            // No PCM gain adjustment: changing Android media volume to zero
            // does not alter the music bytes sent to network AAC.
            // Only TTS announcements intentionally cause brief music ducking.
            voiceMixer.mix(frame.getBuffer(), frame.getOffset(),
                    frame.getSize(), outputStereo);
            receive.inputPCMData(frame);
        });
    }

    public boolean queueAnnouncement(short[] monoPcm) {
        return voiceMixer.enqueue(monoPcm);
    }

    public int queuedAnnouncements() { return voiceMixer.queued(); }

    public void clearAnnouncements() { voiceMixer.clear(); }

    public void replaceFile(Context ctx, Uri source) throws java.io.IOException {
        // Next track keeps its own original level without a carried-over gain.
        file.replaceFile(ctx, source);
    }

    @Override public void stop() { file.stop(); }
    @Override public boolean isRunning() { return file.isRunning(); }
    @Override public void release() { voiceMixer.clear(); file.release(); }
}
