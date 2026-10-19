package com.maru.radio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional device TTS between original Suno tracks. No voice cloning,
 * recording, remote AI connection, BIGO audio-injection or chat scraping.
 */
final class RadioShowNarrator {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech speech;
    private boolean initialized;
    private boolean destroyed;
    private int generation;
    private int position;
    private String pendingUtterance = "";
    private Runnable completion;
    private final List<RadioShowLines.Line> lines = new ArrayList<>();
    private Runnable timeout;

    RadioShowNarrator(Context context) {
        speech = new TextToSpeech(context.getApplicationContext(), status -> handler.post(() -> {
            if (destroyed) return;
            initialized = status == TextToSpeech.SUCCESS && speech != null;
            if (initialized) {
                speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onDone(String utteranceId) {
                        handler.post(() -> onFinishedLine(utteranceId));
                    }
                    @Override public void onError(String utteranceId) {
                        handler.post(() -> onFinishedLine(utteranceId));
                    }
                });
            }
            if (completion != null) {
                if (initialized) next(generation);
                else finish(generation);
            }
        }));
    }

    void say(List<RadioShowLines.Line> scripts, Runnable done) {
        cancel();
        if (destroyed) { done.run(); return; }
        lines.addAll(scripts);
        completion = done;
        int ticket = generation;
        if (initialized) next(ticket);
        else {
            // The Android TTS engine may be absent or fail initialization.
            // Never block the user's original music indefinitely.
            timeout = () -> finish(ticket);
            handler.postDelayed(timeout, 4000L);
        }
    }

    private void next(int ticket) {
        if (destroyed || ticket != generation || completion == null) return;
        if (timeout != null) {handler.removeCallbacks(timeout);timeout = null;}
        if (position >= lines.size()) {finish(ticket);return;}
        if (!initialized || speech == null) {finish(ticket);return;}
        RadioShowLines.Line current = lines.get(position++);
        int support = speech.isLanguageAvailable(current.locale);
        if (support < TextToSpeech.LANG_AVAILABLE) {next(ticket);return;}
        speech.setLanguage(current.locale);
        speech.setPitch(current.pitch);
        speech.setSpeechRate(.98f);
        pendingUtterance = "maru-radio-" + ticket + "-" + position;
        try {
            int status = speech.speak(current.text, TextToSpeech.QUEUE_FLUSH, (Bundle) null, pendingUtterance);
            if (status != TextToSpeech.SUCCESS) {next(ticket);return;}
        } catch (RuntimeException ex) {next(ticket);return;}
        final String expected = pendingUtterance;
        timeout = () -> {
            if (expected.equals(pendingUtterance) && ticket == generation) {
                speech.stop();
                next(ticket);
            }
        };
        handler.postDelayed(timeout, 15000L);
    }
    private void onFinishedLine(String utteranceId) {
        if (utteranceId != null && utteranceId.equals(pendingUtterance)) next(generation);
    }
    private void finish(int ticket) {
        if (ticket != generation) return;
        Runnable done = completion;
        completion = null;
        pendingUtterance = "";
        lines.clear();
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = null;
        if (done != null) done.run();
    }
    void cancel() {
        generation++;
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = null;
        pendingUtterance = "";
        completion = null;
        lines.clear();
        position = 0;
        if (speech != null) speech.stop();
    }
    void destroy() {
        destroyed = true;
        cancel();
        if (speech != null) {
            speech.shutdown();
            speech = null;
        }
    }
}
