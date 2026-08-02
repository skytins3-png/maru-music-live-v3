package com.maru.musiclive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.core.app.NotificationCompat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * V3.1.6: TTS는 곡 사이 안내와 사용자가 누른 방송 종료 안내에서만 재생한다.
 * 곡 재생 중 입장/좋아요/선물/팔로우 이벤트는 음성으로 읽지 않는다.
 */
public final class AutoGreetingService extends Service {
    private static final String CHANNEL_ID = "maru_auto_greeting";
    private static final int NOTIFICATION_ID = 2941;

    private static final String ACTION_INTERMISSION =
            "com.maru.musiclive.TTS_INTERMISSION";
    private static final String ACTION_SONG_TITLE =
            "com.maru.musiclive.TTS_SONG_TITLE";
    private static final String ACTION_CLOSING =
            "com.maru.musiclive.TTS_CLOSING";
    public static final String ACTION_BROADCAST_CLOSED =
            "com.maru.musiclive.BROADCAST_CLOSED";
    private static final String ACTION_CANCEL =
            "com.maru.musiclive.TTS_CANCEL";

    private static final String EXTRA_VALUE = "value";
    private static final String EXTRA_LANGUAGE = "language";
    private static final String EXTRA_GIFT_NAMES = "gift_names";
    private static final String EXTRA_FOLLOW_NAMES = "follow_names";
    private static final String EXTRA_RESUME_INDEX = "resume_index";
    private static final long SPEAK_TIMEOUT_MS = 35_000L;
    private static final String[] INTERMISSION_LANGUAGES =
            BroadcastVoicePolicy.orderedLanguages();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Queue<AnnouncementRequest> queue = new ArrayDeque<>();
    private final Set<String> queuedKeys = new HashSet<>();

    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean ttsFailed;
    private boolean speaking;
    private String activeUtteranceId = "";
    private String activeRequestKey = "";
    private int activeResumeIndex = -1;
    private AnnouncementRequest activeRequest;
    private int activeLanguageIndex = -1;

    private final Runnable speakTimeout = () -> {
        if (!speaking) return;
        if (isMultiLanguageRequest()) {
            AutoGreetingStore.setStatus(this, "현재 언어 안내 시간이 초과되어 다음 언어로 넘어갑니다.");
            advanceLanguage(false);
        } else {
            AutoGreetingStore.setStatus(this, "곡 사이 TTS 시간이 초과되어 다음 노래를 시작합니다.");
            finishCurrent(false);
        }
    };

    /** 방송 이벤트는 다음 곡 사이 통합 안내에만 저장하고 즉시 말하지 않는다. */
    public static void announce(Context context, String nickname, String language) {
        if (context == null) return;
        IntermissionStore.recordEvent(context, new LiveEvent(
                EventType.JOIN,
                nickname,
                "",
                "",
                language,
                System.currentTimeMillis()));
        AutoGreetingStore.setStatus(context, "입장 감지됨 · 곡 재생 중 음성 없음");
    }

    /** 방송 이벤트는 곡 재생 중 TTS로 보내지 않는다. */
    public static void announceEvent(Context context, LiveEvent event) {
        if (context == null || event == null) return;
        IntermissionStore.recordEvent(context, event);
        AutoGreetingStore.setStatus(context, event.type.koreanLabel + " 감지됨 · 곡 사이 안내 대기");
    }

    /** 설정 화면의 단독 제목 TTS 테스트용. 방송 자동 재생 경로에서는 사용하지 않는다. */
    public static void announceSong(Context context, String title, String language) {
        if (context == null) return;
        safeStart(context, new Intent(context, AutoGreetingService.class)
                .setAction(ACTION_SONG_TITLE)
                .putExtra(EXTRA_VALUE, SongTitleFormatter.clean(title))
                .putExtra(EXTRA_LANGUAGE, GreetingLanguage.normalize(language))
                .putExtra(EXTRA_RESUME_INDEX, -1));
    }

    /**
     * 노래가 완전히 끝난 뒤 호출한다. 안내가 끝나거나 실패하면 resumeIndex 곡을 시작한다.
     */
    public static boolean announceIntermission(
            Context context,
            String nextTitle,
            List<String> giftNames,
            List<String> followNames,
            int resumeIndex) {
        if (context == null) return false;
        Intent intent = new Intent(context, AutoGreetingService.class)
                .setAction(ACTION_INTERMISSION)
                .putExtra(EXTRA_VALUE, SongTitleFormatter.clean(nextTitle))
                .putExtra(EXTRA_LANGUAGE, GreetingLanguage.KOREAN)
                .putStringArrayListExtra(
                        EXTRA_GIFT_NAMES,
                        new ArrayList<>(giftNames == null
                                ? java.util.Collections.<String>emptyList()
                                : giftNames))
                .putStringArrayListExtra(
                        EXTRA_FOLLOW_NAMES,
                        new ArrayList<>(followNames == null
                                ? java.util.Collections.<String>emptyList()
                                : followNames))
                .putExtra(EXTRA_RESUME_INDEX, resumeIndex);
        return safeStart(context, intent);
    }

    /** Keyboard-free preset closing announcement. Music is paused before this is called. */
    public static boolean announceClosing(Context context) {
        if (context == null) return false;
        return safeStart(context, new Intent(context, AutoGreetingService.class)
                .setAction(ACTION_CLOSING)
                .putExtra(EXTRA_VALUE, "broadcast-closing")
                .putExtra(EXTRA_LANGUAGE, GreetingLanguage.KOREAN)
                .putExtra(EXTRA_RESUME_INDEX, -1));
    }

    /** Conversational AI replies are visual-only during songs to keep music audio clean. */
    public static void speakDialogue(Context context, String text, String language) {
        if (context != null) {
            AutoGreetingStore.setStatus(context, "대화형 AI 화면 답변 · 노래 음성 유지");
        }
    }

    public static void cancel(Context context) {
        if (context == null) return;
        // 취소를 위해 새 포그라운드 서비스를 시작하면 최신 Android에서
        // 방송 시작 순간 서비스 제한 오류가 날 수 있다. 실행 중인 서비스만 중지한다.
        try {
            context.stopService(new Intent(context, AutoGreetingService.class));
        } catch (RuntimeException ignored) {
            // 이미 종료된 상태라면 할 일이 없다.
        }
    }

    private static boolean safeStart(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (RuntimeException first) {
            try {
                context.startService(intent);
                return true;
            } catch (RuntimeException ignored) {
                AutoGreetingStore.setStatus(context, "곡 사이 TTS 서비스를 시작하지 못했습니다.");
                return false;
            }
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification("곡 사이 통합 안내 준비 중"));
        initializeTts();
    }

    private void initializeTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                ttsReady = false;
                ttsFailed = true;
                AutoGreetingStore.setStatus(this, "TTS 초기화 실패 · 다음 노래를 바로 시작합니다.");
                main.post(this::resumeQueuedWithoutSpeech);
                return;
            }

            ttsReady = true;
            ttsFailed = false;
            tts.setSpeechRate(BroadcastVoicePolicy.SPEECH_RATE);
            tts.setPitch(BroadcastVoicePolicy.PITCH);

            AudioAttributes.Builder attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                attributes.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL);
            }
            tts.setAudioAttributes(attributes.build());

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    main.post(() -> {
                        AutoGreetingStore.recordGreetingPlayed(
                                AutoGreetingService.this,
                                System.currentTimeMillis());
                        AutoGreetingStore.setStatus(
                                AutoGreetingService.this,
                                "곡 사이 5개 언어 통합 안내 재생 중");
                    });
                }

                @Override public void onDone(String utteranceId) {
                    main.post(() -> {
                        if (!utteranceId.equals(activeUtteranceId)) return;
                        if (isMultiLanguageRequest()) advanceLanguage(true);
                        else finishCurrent(true);
                    });
                }

                @Override public void onError(String utteranceId) {
                    main.post(() -> handleSpeechError("곡 사이 TTS 오류"));
                }

                @Override public void onError(String utteranceId, int errorCode) {
                    main.post(() -> handleSpeechError("곡 사이 TTS 오류 " + errorCode));
                }
            });
            main.post(this::playNext);
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            cancelAll();
            return START_NOT_STICKY;
        }

        if (!ACTION_INTERMISSION.equals(action)
                && !ACTION_SONG_TITLE.equals(action)
                && !ACTION_CLOSING.equals(action)) {
            return START_NOT_STICKY;
        }

        AnnouncementRequest request = AnnouncementRequest.fromIntent(intent);
        if (ttsFailed) {
            if (request.kind == AnnouncementRequest.KIND_CLOSING) finishClosing();
            else if (request.resumeIndex >= 0) resumeTrack(request.resumeIndex);
            return START_NOT_STICKY;
        }

        String requestKey = request.key();
        synchronized (queue) {
            if (requestKey.equals(activeRequestKey) || queuedKeys.contains(requestKey)) {
                return START_NOT_STICKY;
            }
            while (queue.size() >= 4) {
                AnnouncementRequest removed = queue.poll();
                if (removed != null) queuedKeys.remove(removed.key());
            }
            queue.offer(request);
            queuedKeys.add(requestKey);
        }
        main.post(this::playNext);
        return START_NOT_STICKY;
    }

    private void playNext() {
        if (!ttsReady || speaking) return;
        AnnouncementRequest request;
        synchronized (queue) {
            request = queue.poll();
            if (request != null) queuedKeys.remove(request.key());
        }
        if (request == null) {
            startForeground(NOTIFICATION_ID, notification("곡 사이 통합 안내 대기 중"));
            stopSelf();
            return;
        }

        activeRequest = request;
        activeRequestKey = request.key();
        activeResumeIndex = request.resumeIndex;
        activeLanguageIndex = request.kind == AnnouncementRequest.KIND_INTERMISSION
                || request.kind == AnnouncementRequest.KIND_CLOSING ? 0 : -1;

        if (isMultiLanguageRequest()) {
            speakCurrentLanguage();
        } else {
            speakSingleLanguage(request, request.language);
        }
    }

    private void speakCurrentLanguage() {
        if (!isMultiLanguageRequest()) {
            finishCurrent(false);
            return;
        }
        if (activeLanguageIndex < 0 || activeLanguageIndex >= INTERMISSION_LANGUAGES.length) {
            finishCurrent(true);
            return;
        }
        String language = INTERMISSION_LANGUAGES[activeLanguageIndex];
        if (!languageAvailable(language)) {
            AutoGreetingStore.setStatus(
                    this,
                    GreetingLanguage.koreanLabel(language) + " 음성이 없어 다음 언어로 넘어갑니다.");
            advanceLanguage(false);
            return;
        }
        speakSingleLanguage(activeRequest, language);
    }

    private void speakSingleLanguage(AnnouncementRequest request, String requestedLanguage) {
        String language = GreetingLanguage.normalize(requestedLanguage);
        if (!languageAvailable(language)) {
            if (request.kind == AnnouncementRequest.KIND_INTERMISSION
                    || request.kind == AnnouncementRequest.KIND_CLOSING) {
                advanceLanguage(false);
            } else {
                finishCurrent(false);
            }
            return;
        }

        Locale locale = GreetingLanguage.locale(language);
        int languageResult = tts.setLanguage(locale);
        if (languageResult == TextToSpeech.LANG_MISSING_DATA
                || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (request.kind == AnnouncementRequest.KIND_INTERMISSION
                    || request.kind == AnnouncementRequest.KIND_CLOSING) {
                advanceLanguage(false);
            } else {
                finishCurrent(false);
            }
            return;
        }

        String message;
        if (request.kind == AnnouncementRequest.KIND_INTERMISSION) {
            message = IntermissionAnnouncementText.build(
                    request.value,
                    language,
                    request.giftNames,
                    request.followNames);
        } else if (request.kind == AnnouncementRequest.KIND_CLOSING) {
            message = BroadcastClosingText.build(language);
        } else {
            message = TtsAnnouncementText.songTitle(request.value, language);
        }

        speaking = true;
        activeUtteranceId = UUID.randomUUID().toString();

        String status;
        if (request.kind == AnnouncementRequest.KIND_INTERMISSION) {
            status = (activeLanguageIndex + 1) + "/" + INTERMISSION_LANGUAGES.length
                    + " " + GreetingLanguage.koreanLabel(language)
                    + " 통합 안내 재생 중";
        } else if (request.kind == AnnouncementRequest.KIND_CLOSING) {
            status = (activeLanguageIndex + 1) + "/" + INTERMISSION_LANGUAGES.length
                    + " " + GreetingLanguage.koreanLabel(language)
                    + " 방송 종료 안내 중";
        } else {
            status = GreetingLanguage.koreanLabel(language) + " 제목 안내 재생 중";
        }
        AutoGreetingStore.setStatus(this, status);
        startForeground(NOTIFICATION_ID, notification(status));

        Bundle params = new Bundle();
        params.putFloat(
                TextToSpeech.Engine.KEY_PARAM_VOLUME,
                BroadcastVoicePolicy.VOLUME);
        int result = tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                params,
                activeUtteranceId);
        if (result == TextToSpeech.ERROR) {
            handleSpeechError("TTS 호출 실패");
            return;
        }

        main.removeCallbacks(speakTimeout);
        main.postDelayed(speakTimeout, SPEAK_TIMEOUT_MS);
    }

    private boolean languageAvailable(String language) {
        int result = tts.isLanguageAvailable(GreetingLanguage.locale(language));
        return result != TextToSpeech.LANG_MISSING_DATA
                && result != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    private boolean isMultiLanguageRequest() {
        return activeRequest != null
                && (activeRequest.kind == AnnouncementRequest.KIND_INTERMISSION
                || activeRequest.kind == AnnouncementRequest.KIND_CLOSING);
    }

    private void advanceLanguage(boolean success) {
        main.removeCallbacks(speakTimeout);
        speaking = false;
        activeUtteranceId = "";
        activeLanguageIndex++;
        if (activeLanguageIndex >= INTERMISSION_LANGUAGES.length) {
            finishCurrent(success);
        } else {
            main.postDelayed(this::speakCurrentLanguage, 180L);
        }
    }

    private void handleSpeechError(String status) {
        AutoGreetingStore.setStatus(
                this,
                isMultiLanguageRequest()
                        ? status + " · 다음 언어로 넘어갑니다."
                        : status + " · 다음 노래를 시작합니다.");
        if (isMultiLanguageRequest()) advanceLanguage(false);
        else finishCurrent(false);
    }

    private void finishCurrent(boolean success) {
        main.removeCallbacks(speakTimeout);
        int resumeIndex = activeResumeIndex;
        int finishedKind = activeRequest == null ? -1 : activeRequest.kind;
        speaking = false;
        activeUtteranceId = "";
        activeRequestKey = "";
        activeResumeIndex = -1;
        activeRequest = null;
        activeLanguageIndex = -1;
        if (finishedKind == AnnouncementRequest.KIND_CLOSING) {
            AutoGreetingStore.setStatus(this, "방송 종료 안내 완료 · 전체 재생 종료");
            finishClosing();
            return;
        }
        AutoGreetingStore.setStatus(
                this,
                success
                        ? "곡 사이 통합 안내 완료 · 다음 노래 시작"
                        : "곡 사이 통합 안내 종료 · 다음 노래 시작");
        if (resumeIndex >= 0) resumeTrack(resumeIndex);
        main.postDelayed(this::playNext, 200L);
    }

    private void finishClosing() {
        ScreenOcrGreetingService.stop(this);
        Intent stopMusic = new Intent(this, PlaybackService.class)
                .setAction(PlaybackService.ACTION_STOP_ALL);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(stopMusic);
            else startService(stopMusic);
        } catch (RuntimeException ignored) {}
        AutoGreetingStore.setRunningMode(this, "");
        sendBroadcast(new Intent().setAction(ACTION_BROADCAST_CLOSED).setPackage(getPackageName()));
        stopSelf();
    }

    private void resumeQueuedWithoutSpeech() {
        int resumeIndex = -1;
        boolean closing = false;
        synchronized (queue) {
            while (!queue.isEmpty()) {
                AnnouncementRequest request = queue.poll();
                if (request == null) continue;
                if (request.kind == AnnouncementRequest.KIND_CLOSING) closing = true;
                if (resumeIndex < 0 && request.resumeIndex >= 0) resumeIndex = request.resumeIndex;
            }
            queuedKeys.clear();
        }
        if (closing) {
            finishClosing();
            return;
        }
        if (resumeIndex >= 0) resumeTrack(resumeIndex);
        stopSelf();
    }

    private void resumeTrack(int index) {
        Intent intent = new Intent(this, PlaybackService.class)
                .setAction(PlaybackService.ACTION_PLAY_AFTER_ANNOUNCEMENT)
                .putExtra(PlaybackService.EXTRA_TRACK_INDEX, index);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
        } catch (RuntimeException ignored) {
            AutoGreetingStore.setStatus(this, "다음 노래 시작 요청 실패");
        }
    }

    private void cancelAll() {
        main.removeCallbacks(speakTimeout);
        synchronized (queue) {
            queue.clear();
            queuedKeys.clear();
        }
        if (tts != null) {
            try { tts.stop(); } catch (RuntimeException ignored) {}
        }
        speaking = false;
        activeUtteranceId = "";
        activeRequestKey = "";
        activeResumeIndex = -1;
        activeRequest = null;
        activeLanguageIndex = -1;
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.auto_greeting_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.setDescription(getString(R.string.auto_greeting_channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification notification(String text) {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle("MARU 곡 사이 통합 안내")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (RuntimeException ignored) {}
            tts = null;
        }
        super.onDestroy();
    }

    private static final class AnnouncementRequest {
        static final int KIND_INTERMISSION = 1;
        static final int KIND_SONG_TEST = 2;
        static final int KIND_CLOSING = 3;

        final int kind;
        final String value;
        final String language;
        final List<String> giftNames;
        final List<String> followNames;
        final int resumeIndex;

        AnnouncementRequest(
                int kind,
                String value,
                String language,
                List<String> giftNames,
                List<String> followNames,
                int resumeIndex) {
            this.kind = kind;
            this.value = value == null ? "" : value.trim();
            this.language = GreetingLanguage.normalize(language);
            this.giftNames = giftNames == null ? new ArrayList<>() : new ArrayList<>(giftNames);
            this.followNames = followNames == null ? new ArrayList<>() : new ArrayList<>(followNames);
            this.resumeIndex = resumeIndex;
        }

        String key() {
            return kind
                    + "|" + value.toLowerCase(Locale.ROOT)
                    + "|" + (kind == KIND_INTERMISSION || kind == KIND_CLOSING ? "all5" : language)
                    + "|" + giftNames.toString().toLowerCase(Locale.ROOT)
                    + "|" + followNames.toString().toLowerCase(Locale.ROOT)
                    + "|" + resumeIndex;
        }

        static AnnouncementRequest fromIntent(Intent intent) {
            String action = intent.getAction();
            String value = intent.getStringExtra(EXTRA_VALUE);
            String language = intent.getStringExtra(EXTRA_LANGUAGE);
            int resumeIndex = intent.getIntExtra(EXTRA_RESUME_INDEX, -1);
            if (ACTION_INTERMISSION.equals(action)) {
                ArrayList<String> gifts = intent.getStringArrayListExtra(EXTRA_GIFT_NAMES);
                ArrayList<String> follows = intent.getStringArrayListExtra(EXTRA_FOLLOW_NAMES);
                return new AnnouncementRequest(
                        KIND_INTERMISSION,
                        value,
                        language,
                        gifts,
                        follows,
                        resumeIndex);
            }
            if (ACTION_CLOSING.equals(action)) {
                return new AnnouncementRequest(
                        KIND_CLOSING,
                        value,
                        GreetingLanguage.KOREAN,
                        new ArrayList<>(),
                        new ArrayList<>(),
                        -1);
            }
            return new AnnouncementRequest(
                    KIND_SONG_TEST,
                    value,
                    language,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    -1);
        }
    }
}
