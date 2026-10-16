package com.maru.radio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;
import java.util.List;

/**
 * Android offline-capable system TTS + an optional user-recorded greeting clip.
 * TextToSpeech speaks in the installed system voice, NOT a clone of the host.
 * No streaming and no recording permissions are requested.
 */
public final class RadioDjVoice {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private MediaPlayer clip;
    private boolean ready;
    private boolean initialized;
    private boolean initializationFailed;
    private int generation;
    private String pendingText;
    private int pendingLanguage = RadioDjLanguagePolicy.ENGLISH;
    private Runnable pendingDone;
    private Runnable timeout;
    private String status = "시스템 음성 엔진 대기";
    private boolean closed;

    public RadioDjVoice(Context ctx) {
        context = ctx.getApplicationContext();
    }

    public String status() { return status; }

    public void say(String text, Runnable after) {
        say(text, RadioDjLanguagePolicy.ENGLISH, after);
    }

    /** Speak one segment in an explicitly specified installed Android voice. */
    public void say(String text, int language, Runnable after) {
        if (closed) { if (after != null) after.run(); return; }
        cancel();
        final int ticket = generation;
        if (!RadioDjSettings.isVoiceEnabled(context) || text == null || text.trim().isEmpty()) {
            if (after != null) after.run();
            return;
        }
        String safe = text.trim().substring(0, Math.min(text.trim().length(), 230));
        pendingText = safe;
        pendingLanguage = Math.max(0,Math.min(4,language));
        pendingDone = after;
        timeout = () -> complete(ticket);
        main.postDelayed(timeout, 15000L);
        if (ready) startSpeech(ticket);
        else if (initializationFailed) complete(ticket); // do not stall songs for 15s
        else if (!initialized) {
            initialized = true;
            try {
                tts = new TextToSpeech(context, init -> main.post(() -> {
                    if (closed) return;
                    ready = init == TextToSpeech.SUCCESS && tts != null;
                    initializationFailed = !ready;
                    if (ready) {
                        tts.setSpeechRate(0.93f);
                        tts.setPitch(0.96f);
                        // Media usage can be captured by a *separate app* if
                        // it has lawful consent and supports Android media
                        // capture. This does NOT inject audio into BIGO mic.
                        AudioAttributes.Builder voiceAttrs = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
                        if (android.os.Build.VERSION.SDK_INT >= 29)
                            voiceAttrs.setAllowedCapturePolicy(
                                    AudioAttributes.ALLOW_CAPTURE_BY_ALL);
                        tts.setAudioAttributes(voiceAttrs.build());
                    }
                    // A new announcement may have replaced the pending text
                    // while Android was still initializing the TTS engine.
                    if (pendingText != null) {
                        if (ready) startSpeech(generation);
                        else {
                            status = "설치된 한국어 TTS 음성이 없어 안내 멘트 건너뜀";
                            complete(generation);
                        }
                    }
                }));
            } catch (RuntimeException e) {
                initializationFailed = true;
                status = "시스템 음성 엔진 실행 불가";
                complete(ticket);
            }
        }
    }

    /** Short, deterministic bilingual intros. A new request cancels older speech. */
    public void sayLines(List<RadioDjLanguagePolicy.Line> lines, Runnable after) {
        if (lines == null || lines.isEmpty()) {
            if (after != null) after.run();
            return;
        }
        speakLine(lines, 0, after);
    }

    private void speakLine(List<RadioDjLanguagePolicy.Line> lines, int index, Runnable after) {
        if (index >= lines.size()) {
            if (after != null) after.run();
            return;
        }
        RadioDjLanguagePolicy.Line line = lines.get(index);
        say(line.text, line.language, () -> speakLine(lines, index + 1, after));
    }

    /** User-provided previously-recorded audio: only a fixed greeting, not cloning. */
    public void greeting(String fallback, Runnable after) {
        String saved = RadioDjSettings.voiceClip(context);
        if (saved == null || saved.isEmpty()) { say(fallback, after); return; }
        cancel();
        if (closed) { if (after != null) after.run(); return; }
        final int ticket = generation;
        pendingDone = after;
        timeout = () -> complete(ticket);
        main.postDelayed(timeout, 15000L);
        try {
            clip = new MediaPlayer();
            AudioAttributes.Builder voiceClipAttrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
            if (android.os.Build.VERSION.SDK_INT >= 29)
                voiceClipAttrs.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL);
            clip.setAudioAttributes(voiceClipAttrs.build());
            clip.setDataSource(context, Uri.parse(saved));
            clip.setOnPreparedListener(p -> {
                if (ticket == generation && !closed) {
                    status = "형이 녹음한 인사 음성 재생";
                    try { p.start(); }
                    catch (RuntimeException e) {
                        fallbackGreeting(ticket, fallback, after);
                    }
                }
            });
            clip.setOnCompletionListener(p -> complete(ticket));
            clip.setOnErrorListener((p, what, extra) -> {
                fallbackGreeting(ticket, fallback, after);
                return true;
            });
            clip.prepareAsync();
        } catch (Exception badClip) {
            cancel();
            say(fallback, after);
        }
    }

    private void fallbackGreeting(int ticket, String EnglishText, Runnable after) {
        if (ticket != generation || closed) return;
        // A missing / corrupted / revoked recording must not stall the show.
        cancel();
        status = "녹음 파일이 재생되지 않아 영어 TTS로 대신 인사합니다.";
        say(EnglishText, RadioDjLanguagePolicy.ENGLISH, after);
    }

    private void startSpeech(int ticket) {
        if (closed || !ready || ticket != generation || pendingText == null) return;
        Locale[] voices = { Locale.KOREAN, Locale.SIMPLIFIED_CHINESE,
                Locale.US, Locale.JAPANESE, new Locale("ru", "RU") };
        int index = pendingLanguage;
        int support = tts.setLanguage(voices[index]);
        if (support == TextToSpeech.LANG_MISSING_DATA
                || support == TextToSpeech.LANG_NOT_SUPPORTED) {
            status = "선택한 언어 음성이 설치되지 않아 멘트를 생략했어요";
            complete(ticket);
            return;
        }
        String utterance = "MARU-DJ-" + ticket;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) {
                if (utterance.equals(id)) main.post(() -> complete(ticket));
            }
            @Override public void onError(String id) {
                if (utterance.equals(id)) main.post(() -> complete(ticket));
            }
        });
        status = "Android " + RadioReplies.LANGUAGES[index] + " TTS 아나운서";
        int r = tts.speak(pendingText, TextToSpeech.QUEUE_FLUSH, null, utterance);
        if (r == TextToSpeech.ERROR) complete(ticket);
    }

    private void complete(int ticket) {
        if (ticket != generation) return;
        Runnable done = pendingDone;
        pendingDone = null;
        pendingText = null;
        main.removeCallbacks(timeout);
        timeout = null;
        releaseClip();
        generation++;
        if (done != null) main.post(done);
    }

    private void releaseClip() {
        if (clip != null) {
            try { clip.reset(); clip.release(); } catch (RuntimeException ignored) {}
            clip = null;
        }
    }

    public void cancel() {
        generation++;
        pendingText = null;
        pendingDone = null;
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = null;
        if (tts != null) try { tts.stop(); } catch (RuntimeException ignored) {}
        releaseClip();
    }

    public void close() {
        closed = true;
        cancel();
        if (tts != null) try { tts.shutdown(); } catch (RuntimeException ignored) {}
        tts = null;
        ready = false;
    }
}
