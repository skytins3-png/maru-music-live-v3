package com.maru.musiclive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import java.io.File;
import java.io.IOException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Opt-in companion radio for a user who plays songs in the official Suno app.
 *
 * This service NEVER captures Suno sound, controls Suno's player, reads Suno
 * songs or promises BIGO microphone transmission. Only four original short
 * TTS lines play approximately every 6 minutes (not at song boundaries).
 * Android's transient MAY_DUCK focus can lower other media, but the exact
 * behavior of a third-party player depends on that app and the device.
 */
public final class SunoTalkCompanionService extends Service {
    public static final String ACTION_START_AUTO = "com.maru.musiclive.suno.START_AUTO";
    public static final String ACTION_TALK_NOW = "com.maru.musiclive.suno.TALK_NOW";
    public static final String ACTION_STOP = "com.maru.musiclive.suno.STOP";
    public static final String ACTION_MANUAL = "com.maru.musiclive.suno.MANUAL";
    public static final String ACTION_PLAY_MY_VOICE = "com.maru.musiclive.suno.PLAY_MY_VOICE";
    private static final String CHANNEL = "maru_suno_companion";
    private static final int NOTICE_ID = 2957;
    private static final long FIRST_TALK_MS = 3L * 60L * 1000L;
    private static final long TALK_INTERVAL_MS = 6L * 60L * 1000L;
    private static final long SPEECH_TIMEOUT_MS = 28_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private AudioManager.OnAudioFocusChangeListener legacyFocus;
    private boolean initialized;
    private boolean enabled;
    private boolean autoMode;
    private boolean speaking;
    private boolean pendingTalk;
    private MediaPlayer recordedVoicePlayer;
    private int episode;
    private int lineIndex;
    private String currentId = "";
    private RadioConversationScript.Line[] script;

    private final Runnable timer = () -> {
        if (!enabled || !autoMode) return;
        if (speaking || recordedVoicePlayer != null) { schedule(TALK_INTERVAL_MS); return; }
        talk();
    };
    private final Runnable watchdog = () -> {
        if (!speaking) return;
        if (tts != null) tts.stop();
        finishTalk();
    };

    public static void command(Context context, String action) {
        Intent intent = new Intent(context, SunoTalkCompanionService.class)
                .setAction(action);
        if (ACTION_START_AUTO.equals(action)
                || ACTION_TALK_NOW.equals(action)
                || ACTION_MANUAL.equals(action)
                || ACTION_PLAY_MY_VOICE.equals(action)) {
            androidx.core.content.ContextCompat.startForegroundService(context, intent);
        } else if (ACTION_STOP.equals(action)) {
            // Do not recreate a stopped service just to issue a stop command.
            context.stopService(intent);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        NotificationManager notifications = getSystemService(NotificationManager.class);
        if (notifications != null && Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "MARU · Suno 음성 토크", NotificationManager.IMPORTANCE_LOW));
        }
        // Android TTS service initialization can be asynchronous. No speech
        // will run until the engine reports a usable English voice.
        tts = new TextToSpeech(this, result -> main.post(() -> {
            if (!enabled || tts == null || result != TextToSpeech.SUCCESS) return;
            int lang = tts.setLanguage(Locale.US);
            if (lang == TextToSpeech.LANG_MISSING_DATA
                    || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                updateNotice("영어 음성 데이터가 없어 토크를 실행할 수 없습니다.");
                return;
            }
            tts.setSpeechRate(1.04f);
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { main.post(() -> nextLine(id)); }
                @Override public void onError(String id) { main.post(() -> nextLine(id)); }
            });
            initialized = true;
            if (pendingTalk) { pendingTalk = false; talk(); }
            else updateNotice("Suno 음악을 재생하세요 · 토크 준비 완료");
        }));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // Only explicit user actions start/resume this user-visible companion.
        if (!enabled) {
            enabled = true;
            startForeground(NOTICE_ID, notification("Suno 음악 + 아환 토크 준비"));
        }
        if (ACTION_PLAY_MY_VOICE.equals(action)) {
            playMyRecordedVoice();
        } else if (ACTION_START_AUTO.equals(action)) {
            autoMode = true;
            pendingTalk = false;
            schedule(FIRST_TALK_MS);
            updateNotice("자동 토크 · 3분 후 첫 멘트, 이후 약 6분 간격");
        } else if (ACTION_MANUAL.equals(action)) {
            autoMode = false;
            pendingTalk = false;
            main.removeCallbacks(timer);
            updateNotice("수동 토크 · 알림의 '짧은 토크'를 누르세요");
        } else if (ACTION_TALK_NOW.equals(action)) {
            if (!initialized) {
                pendingTalk = true;
                updateNotice("음성 엔진 준비 중 · 자동 재시도");
            } else {
                talk();
            }
        }
        return START_NOT_STICKY;
    }


    /** A user's separately enhanced, personally selected recording, not synthetic TTS. */
    private void playMyRecordedVoice() {
        File file = MyVoiceFileProcessor.output(this);
        if (!file.isFile() || file.length() < 44) {
            updateNotice("형의 보정 WAV 파일이 없습니다 · 먼저 내 목소리를 보정하세요.");
            return;
        }
        if (speaking) {
            if (tts != null) tts.stop();
            finishTalk();
        }
        stopRecordedVoice();
        if (!focusForSpeech()) {
            updateNotice("음악 오디오 우선권 사용 중 · 내 멘트를 실행하지 못함");
            return;
        }
        try {
            MediaPlayer player = new MediaPlayer();
            recordedVoicePlayer = player;
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                if (recordedVoicePlayer == mp && enabled) {
                    updateNotice("형이 녹음한 보정 목소리 재생 중");
                    mp.start();
                }
            });
            player.setOnCompletionListener(mp -> stopRecordedVoice());
            player.setOnErrorListener((mp, what, extra) -> {
                stopRecordedVoice();
                updateNotice("형 보정 목소리를 재생할 수 없습니다.");
                return true;
            });
            player.prepareAsync();
            main.removeCallbacks(timer);
        } catch (IOException | RuntimeException error) {
            stopRecordedVoice();
            updateNotice("보정 WAV 멘트 오류 · 다른 음악은 계속 유지");
        }
    }

    private void stopRecordedVoice() {
        if (recordedVoicePlayer == null) return;
        MediaPlayer current = recordedVoicePlayer;
        recordedVoicePlayer = null;
        try { current.stop(); } catch (IllegalStateException ignored) {}
        current.release();
        abandonFocus();
        if (enabled) {
            updateNotice(autoMode ? "내 멘트 종료 · 자동 토크 대기" : "형 멘트 재생 완료");
            schedule(TALK_INTERVAL_MS);
        }
    }

    private void schedule(long delayMs) {
        main.removeCallbacks(timer);
        if (enabled && autoMode) main.postDelayed(timer, delayMs);
    }

    private boolean focusForSpeech() {
        if (audio == null) return false;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS
                                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            main.post(() -> {
                                if (tts != null) tts.stop();
                                stopRecordedVoice();
                                finishTalk();
                            });
                        }
                    })
                    .build();
            return audio.requestAudioFocus(focusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        legacyFocus = change -> {
            if (change == AudioManager.AUDIOFOCUS_LOSS
                    || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                main.post(() -> { stopRecordedVoice(); finishTalk(); });
            }
        };
        return audio.requestAudioFocus(legacyFocus, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (audio == null) return;
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            audio.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        } else if (legacyFocus != null) {
            audio.abandonAudioFocus(legacyFocus);
            legacyFocus = null;
        }
    }

    private void talk() {
        if (!enabled || speaking || recordedVoicePlayer != null) return;
        if (!initialized || tts == null) {
            pendingTalk = true;
            return;
        }
        if (!focusForSpeech()) {
            updateNotice("다른 앱이 오디오를 사용 중 · 이번 토크 건너뜀");
            schedule(TALK_INTERVAL_MS);
            return;
        }
        // No fake title or 'next song' statement: this service cannot inspect Suno.
        RadioConversationScript.Line[] episodeLines =
                EnglishRadioTalkShow.shortBeforeSong("", episode++, LocalDate.now(), 1);
        script = new RadioConversationScript.Line[]{
                episodeLines[0], episodeLines[1], episodeLines[2], episodeLines[3]};
        lineIndex = 0;
        speaking = true;
        main.removeCallbacks(timer);
        updateNotice("아환과 게스트의 짧은 토크 · Suno 재생 상태는 별도");
        sayLine();
    }

    private void sayLine() {
        if (!enabled || !speaking || script == null || lineIndex >= script.length) {
            finishTalk();
            return;
        }
        RadioConversationScript.Line line = script[lineIndex];
        // Device-dependent pitch difference; distinct recorded voices are not available.
        tts.setPitch(line.speaker == RadioConversationScript.Speaker.AHWAN ? 0.95f : 1.08f);
        String id = "suno-talk-" + episode + "-" + lineIndex;
        currentId = id;
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
        main.removeCallbacks(watchdog);
        main.postDelayed(watchdog, SPEECH_TIMEOUT_MS);
        int result = tts.speak(line.text, TextToSpeech.QUEUE_FLUSH, params, id);
        if (result == TextToSpeech.ERROR) finishTalk();
    }

    private void nextLine(String id) {
        if (!speaking || !id.equals(currentId)) return;
        main.removeCallbacks(watchdog);
        lineIndex++;
        main.postDelayed(this::sayLine, 180L);
    }

    private void finishTalk() {
        if (!speaking) return;
        speaking = false;
        currentId = "";
        script = null;
        main.removeCallbacks(watchdog);
        abandonFocus();
        updateNotice(autoMode
                ? "자동 토크 켜짐 · 약 6분 후 다음 대화"
                : "수동 모드 · 짧은 토크 버튼으로 다시 듣기");
        schedule(TALK_INTERVAL_MS);
    }

    private PendingIntent action(String key, int requestCode) {
        Intent command = new Intent(this, SunoTalkCompanionService.class).setAction(key);
        return PendingIntent.getService(this, requestCode, command,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification notification(String state) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openApp = PendingIntent.getActivity(this, 93, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("MARU · Suno 음악 + 아환 토크")
                .setContentText(state)
                .setContentIntent(openApp)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_play, "짧은 토크",
                        action(ACTION_TALK_NOW, 94)).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_btn_speak_now, "형의 보정 목소리",
                        action(ACTION_PLAY_MY_VOICE, 96)).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "토크 종료",
                        action(ACTION_STOP, 95)).build())
                .build();
    }

    private void updateNotice(String message) {
        if (!enabled) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTICE_ID, notification(message));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        enabled = false;
        autoMode = false;
        pendingTalk = false;
        main.removeCallbacks(timer);
        main.removeCallbacks(watchdog);
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        stopRecordedVoice();
        abandonFocus();
        super.onDestroy();
    }
}
