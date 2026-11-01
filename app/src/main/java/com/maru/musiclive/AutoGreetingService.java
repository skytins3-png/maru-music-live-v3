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
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;

import androidx.core.app.NotificationCompat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final String ACTION_RADIO_INTERMISSION =
            "com.maru.musiclive.TTS_RADIO_INTERMISSION";
    private static final String ACTION_SONG_TITLE =
            "com.maru.musiclive.TTS_SONG_TITLE";
    private static final String ACTION_CLOSING =
            "com.maru.musiclive.TTS_CLOSING";
    public static final String ACTION_BROADCAST_CLOSED =
            "com.maru.musiclive.BROADCAST_CLOSED";
    public static final String ACTION_STORY_VISUAL =
            "com.maru.musiclive.STORY_VISUAL";
    public static final String ACTION_HOST_VISUAL =
            "com.maru.musiclive.HOST_VISUAL";
    public static final String EXTRA_HOST_MODE = "host_visual_mode";
    public static final String EXTRA_STORY_VISUAL_ID = "approved_story_id";
    private static final String ACTION_CANCEL =
            "com.maru.musiclive.TTS_CANCEL";

    private static final String EXTRA_VALUE = "value";
    private static final String EXTRA_LANGUAGE = "language";
    private static final String EXTRA_GIFT_NAMES = "gift_names";
    private static final String EXTRA_FOLLOW_NAMES = "follow_names";
    private static final String EXTRA_RESUME_INDEX = "resume_index";
    private static final String EXTRA_ARTIST_ID = "artist_id";
    private static final String EXTRA_EPISODE_INDEX = "episode_index";
    private static final String EXTRA_LISTENER_REQUESTED = "listener_requested";
    private static final long SPEAK_TIMEOUT_MS = 35_000L;
    private static final long TTS_INIT_TIMEOUT_MS = 15_000L;
    private static final String EXTRA_ANNOUNCEMENT_TOKEN =
            PlaybackService.EXTRA_ANNOUNCEMENT_TOKEN;
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
    private int activeDialogueIndex = -1;
    private RadioConversationScript.Line[] activeDialogue;
    private String activeStoryId = "";
    private final List<Voice> availableKoreanVoices = new ArrayList<>();
    private final List<Voice> availableEnglishVoices = new ArrayList<>();
    private boolean englishRadioDialogue;

    private final Runnable ttsInitTimeout = () -> {
        if (ttsReady || ttsFailed) return;
        ttsFailed = true;
        AutoGreetingStore.setStatus(this,
                "음성 엔진 초기화 지연 · 음악을 계속 재생합니다.");
        resumeQueuedWithoutSpeech();
    };
    private final Runnable speakTimeout = () -> {
        if (!speaking) return;
        if (isRadioRequest()) {
            AutoGreetingStore.setStatus(this, "대화 시간 초과 · 다음 멘트로 넘어갑니다.");
            try { if (tts != null) tts.stop(); } catch (RuntimeException ignored) {}
            advanceDialogue(false);
        } else if (isMultiLanguageRequest()) {
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
            int resumeIndex, String token) {
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
                .putExtra(EXTRA_RESUME_INDEX, resumeIndex)
                .putExtra(EXTRA_ANNOUNCEMENT_TOKEN, token);
        return safeStart(context, intent);
    }

    /** Queue an original two-presenter exchange, then resume the selected track. */
    public static boolean announceRadioIntermission(
            Context context, String nextTitle,
            List<String> giftNames, List<String> followNames,
            int resumeIndex, int artistId, int episodeIndex, String token,
            boolean listenerRequested) {
        if (context == null || resumeIndex < 0) return false;
        return safeStart(context, new Intent(context, AutoGreetingService.class)
                .setAction(ACTION_RADIO_INTERMISSION)
                .putExtra(EXTRA_VALUE, RadioConversationScript.cleanTitle(nextTitle))
                .putExtra(EXTRA_LANGUAGE, GreetingLanguage.KOREAN)
                .putStringArrayListExtra(EXTRA_GIFT_NAMES,
                        new ArrayList<>(giftNames == null
                                ? java.util.Collections.<String>emptyList() : giftNames))
                .putStringArrayListExtra(EXTRA_FOLLOW_NAMES,
                        new ArrayList<>(followNames == null
                                ? java.util.Collections.<String>emptyList() : followNames))
                .putExtra(EXTRA_RESUME_INDEX, resumeIndex)
                .putExtra(EXTRA_ARTIST_ID, artistId)
                .putExtra(EXTRA_EPISODE_INDEX, episodeIndex)
                .putExtra(EXTRA_LISTENER_REQUESTED, listenerRequested)
                .putExtra(EXTRA_ANNOUNCEMENT_TOKEN, token));
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
        main.postDelayed(ttsInitTimeout, TTS_INIT_TIMEOUT_MS);
    }

    private void initializeTts() {
        tts = new TextToSpeech(this, status -> {
            main.removeCallbacks(ttsInitTimeout);
            if (ttsFailed) return; // Timeout already continued music, never restart talk.
            if (status != TextToSpeech.SUCCESS) {
                ttsReady = false;
                ttsFailed = true;
                AutoGreetingStore.setStatus(this, "TTS 초기화 실패 · 다음 노래를 바로 시작합니다.");
                main.post(this::resumeQueuedWithoutSpeech);
                return;
            }

            ttsReady = true;
            ttsFailed = false;
            loadSupportedKoreanVoices();
            loadSupportedEnglishVoices();
            tts.setSpeechRate(BroadcastVoicePolicy.SPEECH_RATE);
            tts.setPitch(BroadcastVoicePolicy.PITCH);

            AudioAttributes.Builder attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
                                isRadioRequest()
                                        ? "Ahwan Radio · guest conversation"
                                        : "곡 사이 TTS 안내 재생 중");
                    });
                }

                @Override public void onDone(String utteranceId) {
                    main.post(() -> {
                        if (!utteranceId.equals(activeUtteranceId)) return;
                        if (isRadioRequest()) advanceDialogue(true);
                        else if (isMultiLanguageRequest()) advanceLanguage(true);
                        else finishCurrent(true);
                    });
                }

                @Override public void onError(String utteranceId) {
                    main.post(() -> {
                        if (utteranceId.equals(activeUtteranceId)) {
                            handleSpeechError("곡 사이 TTS 오류");
                        }
                    });
                }

                @Override public void onError(String utteranceId, int errorCode) {
                    main.post(() -> {
                        if (utteranceId.equals(activeUtteranceId)) {
                            handleSpeechError("곡 사이 TTS 오류 " + errorCode);
                        }
                    });
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
                && !ACTION_RADIO_INTERMISSION.equals(action)
                && !ACTION_SONG_TITLE.equals(action)
                && !ACTION_CLOSING.equals(action)) {
            return START_NOT_STICKY;
        }

        AnnouncementRequest request = AnnouncementRequest.fromIntent(intent);
        if (ttsFailed) {
            if (request.kind == AnnouncementRequest.KIND_CLOSING) finishClosing();
            else if (request.resumeIndex >= 0)
                resumeTrack(request.resumeIndex, request.resumeToken);
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
        if (!ttsReady || speaking || activeRequest != null) return;
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
        activeDialogueIndex = request.kind == AnnouncementRequest.KIND_RADIO ? 0 : -1;
        englishRadioDialogue = request.kind == AnnouncementRequest.KIND_RADIO
                && AppStorage.englishRadioEnabled(this);
        activeStoryId = "";
        if (request.kind == AnnouncementRequest.KIND_RADIO) {
            if (englishRadioDialogue) {
                // English is ALWAYS short regardless of older saved long-talk settings.
                RadioAudienceStore.Story story = RadioAudienceStore.peekStory(this);
                if (story != null) activeStoryId = story.id;
                activeDialogue = EnglishRadioTalkShow.shortBeforeSong(
                        request.value, request.episodeIndex,
                        java.time.LocalDate.now(), request.artistId,
                        story == null ? "" : story.name,
                        story == null ? "" : story.text);
            } else if (AppStorage.longGuestTalkEnabled(this)) {
                RadioAudienceStore.Story story = RadioAudienceStore.peekStory(this);
                if (story != null) activeStoryId = story.id;
                activeDialogue = GuestTalkShow.beforeSong(
                        request.value, request.episodeIndex, java.time.LocalDate.now(),
                        request.artistId, story == null ? "" : story.name,
                        story == null ? "" : story.text,
                        RadioAudienceStore.approvedResearchNote(this, request.episodeIndex),
                        request.listenerRequested);
            } else {
                activeDialogue = MultiArtistRadioProgram.beforeSong(
                        request.value, request.episodeIndex, java.time.LocalDate.now(),
                        AppStorage.dailyRadioEnabled(this), request.giftNames,
                        request.followNames, request.artistId);
            }
        } else {
            activeDialogue = null;
        }

        if (isRadioRequest()) {
            announceHostVisual(BroadcastMediaSelector.TALK);
            // Notify only our own app: never disclose story text or touch BIGO.
            sendBroadcast(new Intent().setAction(ACTION_STORY_VISUAL).setPackage(getPackageName())
                    .putExtra(EXTRA_STORY_VISUAL_ID, activeStoryId));
            speakCurrentDialogue();
        } else if (isMultiLanguageRequest()) {
            // Greetings and farewell announcements use Ahwan's greeting clip.
            announceHostVisual(BroadcastMediaSelector.GREETING);
            speakCurrentLanguage();
        } else {
            speakSingleLanguage(request, request.language);
        }
    }

    private void announceHostVisual(String mode) {
        sendBroadcast(new Intent().setAction(ACTION_HOST_VISUAL)
                .setPackage(getPackageName())
                .putExtra(EXTRA_HOST_MODE, mode == null ? "" : mode));
    }

    /** Each line completes before the next speaker starts; no song overlap. */
    private void speakCurrentDialogue() {
        if (!isRadioRequest() || activeDialogue == null) {
            finishCurrent(false);
            return;
        }
        if (activeDialogueIndex < 0 || activeDialogueIndex >= activeDialogue.length) {
            finishCurrent(true);
            return;
        }
        String dialogLanguage = englishRadioDialogue
                ? GreetingLanguage.ENGLISH : GreetingLanguage.KOREAN;
        if (!languageAvailable(dialogLanguage)) {
            AutoGreetingStore.setStatus(this,
                    "Selected TTS voice unavailable · playing the next track");
            finishCurrent(false);
            return;
        }
        int result = tts.setLanguage(GreetingLanguage.locale(dialogLanguage));
        if (result == TextToSpeech.LANG_MISSING_DATA
                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            finishCurrent(false);
            return;
        }

        RadioConversationScript.Line line = activeDialogue[activeDialogueIndex];
        applyFictionalVoice(line);
        String speakerName = line.artist != null ? line.artist.name
                : line.speaker == RadioConversationScript.Speaker.AHWAN ? "아환" : "윤솔";
        String status = speakerName + " · "
                + (activeDialogueIndex + 1) + "/" + activeDialogue.length + " 대화 중";
        AutoGreetingStore.setStatus(this, status);
        startForeground(NOTIFICATION_ID, notification(status));
        speaking = true;
        activeUtteranceId = UUID.randomUUID().toString();
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, BroadcastVoicePolicy.VOLUME);
        if (tts.speak(line.text, TextToSpeech.QUEUE_FLUSH, params,
                activeUtteranceId) == TextToSpeech.ERROR) {
            handleSpeechError("대화 음성 재생 오류");
            return;
        }
        main.removeCallbacks(speakTimeout);
        main.postDelayed(speakTimeout, SPEAK_TIMEOUT_MS);
    }

    /** Use local Korean voices where installed, without cloning anyone. */
    private void loadSupportedKoreanVoices() {
        availableKoreanVoices.clear();
        try {
            Set<Voice> installed = tts.getVoices();
            if (installed == null) return;
            for (Voice voice : installed) {
                if (voice == null || voice.getLocale() == null
                        || !"ko".equals(voice.getLocale().getLanguage())
                        || voice.isNetworkConnectionRequired()) continue;
                availableKoreanVoices.add(voice);
            }
            availableKoreanVoices.sort(Comparator.comparing(Voice::getName));
        } catch (RuntimeException ignored) {
            // Some device TTS providers do not enumerate installed voices.
        }
    }

    private void loadSupportedEnglishVoices() {
        availableEnglishVoices.clear();
        try {
            Set<Voice> installed = tts.getVoices();
            if (installed == null) return;
            for (Voice voice : installed) {
                if (voice == null || voice.getLocale() == null
                        || !"en".equals(voice.getLocale().getLanguage())
                        || voice.isNetworkConnectionRequired()) continue;
                availableEnglishVoices.add(voice);
            }
            availableEnglishVoices.sort(Comparator.comparing(Voice::getName));
        } catch (RuntimeException ignored) {}
    }

    private void applyFictionalVoice(RadioConversationScript.Line line) {
        VirtualArtistCatalog.Artist artist = line.artist;
        int voiceSlot = artist == null ? 0 : artist.voiceSlot;
        List<Voice> voices = englishRadioDialogue
                ? availableEnglishVoices : availableKoreanVoices;
        if (!voices.isEmpty()) {
            try {
                Voice chosen = voices.get(
                        Math.floorMod(voiceSlot, voices.size()));
                tts.setVoice(chosen);
            } catch (RuntimeException ignored) {
                // Fall back to phone's existing Korean voice.
            }
        }
        tts.setPitch(artist != null ? artist.pitch :
                line.speaker == RadioConversationScript.Speaker.AHWAN ? 0.92f : 1.09f);
        tts.setSpeechRate(artist != null ? artist.pace : BroadcastVoicePolicy.SPEECH_RATE);
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
        tts.setPitch(BroadcastVoicePolicy.PITCH);
        tts.setSpeechRate(BroadcastVoicePolicy.SPEECH_RATE);
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

    private boolean isRadioRequest() {
        return activeRequest != null
                && activeRequest.kind == AnnouncementRequest.KIND_RADIO;
    }

    private void advanceDialogue(boolean success) {
        main.removeCallbacks(speakTimeout);
        speaking = false;
        activeUtteranceId = "";
        activeDialogueIndex++;
        if (activeDialogue == null || activeDialogueIndex >= activeDialogue.length) {
            finishCurrent(success);
        } else {
            main.postDelayed(this::speakCurrentDialogue, 180L);
        }
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
                isRadioRequest()
                        ? status + " · 다음 진행 멘트로 넘어갑니다."
                        : isMultiLanguageRequest()
                            ? status + " · 다음 언어로 넘어갑니다."
                            : status + " · 다음 노래를 시작합니다.");
        if (isRadioRequest()) advanceDialogue(false);
        else if (isMultiLanguageRequest()) advanceLanguage(false);
        else finishCurrent(false);
    }

    private void finishCurrent(boolean success) {
        main.removeCallbacks(speakTimeout);
        int resumeIndex = activeResumeIndex;
        int finishedKind = activeRequest == null ? -1 : activeRequest.kind;
        String resumeToken = activeRequest == null ? "" : activeRequest.resumeToken;
        if (success && finishedKind == AnnouncementRequest.KIND_RADIO
                && !activeStoryId.isEmpty())
            RadioAudienceStore.markStoryRead(this, activeStoryId);
        if (finishedKind == AnnouncementRequest.KIND_RADIO) {
            sendBroadcast(new Intent().setAction(ACTION_STORY_VISUAL).setPackage(getPackageName())
                    .putExtra(EXTRA_STORY_VISUAL_ID, ""));
        }
        // No stale host movie when dialogue/TTS ends or fails.
        announceHostVisual("");
        activeStoryId = "";
        speaking = false;
        activeUtteranceId = "";
        activeRequestKey = "";
        activeResumeIndex = -1;
        activeRequest = null;
        activeLanguageIndex = -1;
        activeDialogueIndex = -1;
        activeDialogue = null;
        englishRadioDialogue = false;
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
        if (resumeIndex >= 0) resumeTrack(resumeIndex, resumeToken);
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
        String resumeToken = "";
        boolean closing = false;
        synchronized (queue) {
            while (!queue.isEmpty()) {
                AnnouncementRequest request = queue.poll();
                if (request == null) continue;
                if (request.kind == AnnouncementRequest.KIND_CLOSING) closing = true;
                if (resumeIndex < 0 && request.resumeIndex >= 0) {
                    resumeIndex = request.resumeIndex;
                    resumeToken = request.resumeToken;
                }
            }
            queuedKeys.clear();
        }
        if (closing) {
            finishClosing();
            return;
        }
        if (resumeIndex >= 0) resumeTrack(resumeIndex, resumeToken);
        stopSelf();
    }

    private void resumeTrack(int index, String token) {
        Intent intent = new Intent(this, PlaybackService.class)
                .setAction(PlaybackService.ACTION_PLAY_AFTER_ANNOUNCEMENT)
                .putExtra(PlaybackService.EXTRA_TRACK_INDEX, index)
                .putExtra(EXTRA_ANNOUNCEMENT_TOKEN, token);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
        } catch (RuntimeException ignored) {
            AutoGreetingStore.setStatus(this, "다음 노래 시작 요청 실패");
        }
    }

    private void cancelAll() {
        announceHostVisual("");
        sendBroadcast(new Intent().setAction(ACTION_STORY_VISUAL)
                .setPackage(getPackageName())
                .putExtra(EXTRA_STORY_VISUAL_ID, ""));
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
        activeDialogueIndex = -1;
        activeDialogue = null;
        activeStoryId = "";
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
        static final int KIND_RADIO = 4;

        final int kind;
        final String value;
        final String language;
        final List<String> giftNames;
        final List<String> followNames;
        final int resumeIndex;
        String resumeToken = "";
        int artistId;
        int episodeIndex;
        boolean listenerRequested;

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
                    + "|" + (kind == KIND_INTERMISSION || kind == KIND_CLOSING ? "all5"
                            : kind == KIND_RADIO ? "radio" : language)
                    + "|" + giftNames.toString().toLowerCase(Locale.ROOT)
                    + "|" + followNames.toString().toLowerCase(Locale.ROOT)
                    + "|" + resumeIndex + "|" + artistId + "|" + episodeIndex
                    + "|" + resumeToken + "|" + listenerRequested;
        }

        static AnnouncementRequest fromIntent(Intent intent) {
            String action = intent.getAction();
            String value = intent.getStringExtra(EXTRA_VALUE);
            String language = intent.getStringExtra(EXTRA_LANGUAGE);
            int resumeIndex = intent.getIntExtra(EXTRA_RESUME_INDEX, -1);
            if (ACTION_INTERMISSION.equals(action)) {
                ArrayList<String> gifts = intent.getStringArrayListExtra(EXTRA_GIFT_NAMES);
                ArrayList<String> follows = intent.getStringArrayListExtra(EXTRA_FOLLOW_NAMES);
                AnnouncementRequest request = new AnnouncementRequest(
                        KIND_INTERMISSION,
                        value, language, gifts, follows, resumeIndex);
                request.resumeToken = intent.getStringExtra(EXTRA_ANNOUNCEMENT_TOKEN);
                return request;
            }
            if (ACTION_RADIO_INTERMISSION.equals(action)) {
                AnnouncementRequest radio = new AnnouncementRequest(
                        KIND_RADIO, value, GreetingLanguage.KOREAN,
                        intent.getStringArrayListExtra(EXTRA_GIFT_NAMES),
                        intent.getStringArrayListExtra(EXTRA_FOLLOW_NAMES),
                        resumeIndex);
                radio.artistId = intent.getIntExtra(EXTRA_ARTIST_ID, 0);
                radio.episodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, 0);
                radio.listenerRequested = intent.getBooleanExtra(EXTRA_LISTENER_REQUESTED, false);
                radio.resumeToken = intent.getStringExtra(EXTRA_ANNOUNCEMENT_TOKEN);
                return radio;
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
