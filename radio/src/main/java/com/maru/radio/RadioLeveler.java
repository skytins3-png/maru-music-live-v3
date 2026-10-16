package com.maru.radio;

import android.media.MediaPlayer;
import android.media.audiofx.DynamicsProcessing;
import android.os.Build;

/**
 * Audio processing is attached exclusively to the current MARU MediaPlayer
 * audio session. Every new song receives the same compressor & peak limiter,
 * making loud/quiet tracks closer in apparent loudness (NOT measured LUFS).
 * Does NOT rewrite MP3/WAV, control the phone hardware volume, or inject sound
 * into BIGO's microphone/RTMP session.
 */
public final class RadioLeveler {
    private DynamicsProcessing dsp;
    private String status = "균일 음량 처리 대기";

    public String status() { return status; }

    /** @return true when level effect is active, or deliberately turned off. */
    public boolean attach(MediaPlayer player, int requestedMode) {
        release();
        int mode = RadioLevelPolicy.clamp(requestedMode);
        if (mode == RadioLevelPolicy.OFF) {
            status = "원곡 그대로 (음량 보정 끔)";
            return true;
        }
        if (player == null || Build.VERSION.SDK_INT < 28) {
            status = "이 Android 환경은 음량 보정 미지원 · 원곡 그대로";
            return false;
        }
        try {
            int session = player.getAudioSessionId();
            if (session <= 0) throw new IllegalStateException("Invalid audio session");
            // One full-range compression band in stereo (same settings L/R),
            // followed by linked peak limiters with -1.5 dBFS ceiling.
            DynamicsProcessing.Config config = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                    2, false, 0, true, 1, false, 0, true)
                    .build();
            DynamicsProcessing effect = new DynamicsProcessing(0, session, config);
            try {
                DynamicsProcessing.MbcBand mbc = new DynamicsProcessing.MbcBand(
                        true, 22050f,
                        24f, 280f,
                        RadioLevelPolicy.compressorRatio(mode),
                        RadioLevelPolicy.thresholdDb(mode),
                        6f, -80f, 1f, 0f,
                        RadioLevelPolicy.outputGainDb(mode));
                effect.setInputGainAllChannelsTo(RadioLevelPolicy.inputGainDb(mode));
                effect.setMbcBandAllChannelsTo(0, mbc);
                effect.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                        true, true, 0, 2f, 100f, 15f,
                        RadioLevelPolicy.limiterCeilingDb(), 0f));
                effect.setEnabled(true);
                if (!effect.getEnabled()) throw new IllegalStateException("DSP inactive");
                dsp = effect;
                status = mode == RadioLevelPolicy.PRESENT
                        ? "음량 균일 + 또렷하게: 압축·리미터 동작 (기기 내)"
                        : "음량 균일: 압축·리미터 동작 (기기 내)";
                return true;
            } finally {
                if (dsp != effect) effect.release();
            }
        } catch (RuntimeException | LinkageError unsupported) {
            status = "기기 오디오 효과 미지원 · 원곡 그대로 재생";
            return false;
        }
    }

    /** Release previous session BEFORE MediaPlayer.reset/release/next track. */
    public void release() {
        if (dsp != null) {
            try { dsp.setEnabled(false); } catch (RuntimeException ignored) {}
            try { dsp.release(); } catch (RuntimeException ignored) {}
            dsp = null;
        }
    }
}
