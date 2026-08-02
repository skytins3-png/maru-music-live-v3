package com.maru.musiclive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class ScreenOcrGreetingService extends Service {
    public static final String MODE_LOCAL_TEST = "local_test";
    public static final String MODE_DETECT_ONLY = "detect_only";
    public static final String MODE_AUTO_GREETING = "auto_greeting";

    private static final String CHANNEL_ID = "maru_screen_ocr";
    private static final int NOTIFICATION_ID = 2902;
    private static final String ACTION_START =
            "com.maru.musiclive.SCREEN_OCR_START";
    private static final String ACTION_STOP =
            "com.maru.musiclive.SCREEN_OCR_STOP";
    private static final String EXTRA_RESULT_CODE = "result_code";
    private static final String EXTRA_RESULT_DATA = "result_data";
    private static final String EXTRA_MODE = "mode";
    private static final long FRAME_INTERVAL_MS = 1_200L;
    private static final long JOIN_SESSION_TTL_MS = 12L * 60L * 60L * 1000L;
    private static final long CHAT_REPLY_TTL_MS = 10L * 60L * 1000L;

    private final BigoEventParser eventParser = new BigoEventParser();
    private final BigoJoinParser joinParser = new BigoJoinParser();
    private final ChatMessageParser chatParser = new ChatMessageParser();
    private final LiveEventCooldown eventCooldown = new LiveEventCooldown();
    private final SeenCache joinSeen = new SeenCache(1_000);
    private final SeenCache chatSeen = new SeenCache(1_000);
    private final AtomicBoolean ocrBusy = new AtomicBoolean(false);
    private final Handler main = new Handler(Looper.getMainLooper());

    private HandlerThread workerThread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private TextRecognizer koreanRecognizer;
    private TextRecognizer chineseRecognizer;
    private TextRecognizer latinRecognizer;
    private long lastFrameMs;
    private String mode = MODE_DETECT_ONLY;

    public static void start(
            Context context, int resultCode, Intent resultData, String mode) {
        Intent intent = new Intent(context, ScreenOcrGreetingService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
                .putExtra(EXTRA_MODE, mode);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        if (context == null) return;
        // 중지 버튼 때문에 새 OCR 서비스를 만들지 않는다.
        try {
            context.stopService(new Intent(context, ScreenOcrGreetingService.class));
        } catch (RuntimeException ignored) {
            // 이미 종료된 상태라면 할 일이 없다.
        }
        AutoGreetingStore.setRunningMode(context, "");
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        workerThread = new HandlerThread("maru-screen-ocr");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopCapture("사용자가 입장 감지를 중지했습니다.");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action) || intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        mode = intent.getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_DETECT_ONLY;
        startForegroundCompat(notification("화면 OCR 시작 준비 중"));
        ensureRecognizers();

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        } else {
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }
        if (resultData == null || resultCode == 0) {
            stopCapture("화면 공유 승인 정보가 없습니다.");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            beginProjection(resultCode, resultData);
        } catch (RuntimeException error) {
            stopCapture("화면 OCR 시작 실패: " + safeMessage(error));
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void ensureRecognizers() {
        if (koreanRecognizer == null) {
            koreanRecognizer = TextRecognition.getClient(
                    new KoreanTextRecognizerOptions.Builder().build());
        }
        if (chineseRecognizer == null) {
            chineseRecognizer = TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build());
        }
        if (latinRecognizer == null) {
            latinRecognizer = TextRecognition.getClient(
                    TextRecognizerOptions.DEFAULT_OPTIONS);
        }
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void beginProjection(int resultCode, Intent data) {
        releaseProjection();
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(resultCode, data);
        if (projection == null) throw new IllegalStateException("MediaProjection null");
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                worker.post(() -> {
                    stopCapture("화면 공유가 종료되었습니다.");
                    stopSelf();
                });
            }
        }, worker);

        ScreenSize size = screenSize();
        imageReader = ImageReader.newInstance(
                size.width, size.height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::onImageAvailable, worker);
        virtualDisplay = projection.createVirtualDisplay(
                "MARU-BIGO-OCR",
                size.width,
                size.height,
                size.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                worker);
        if (virtualDisplay == null) {
            throw new IllegalStateException("VirtualDisplay null");
        }
        AutoGreetingStore.setRunningMode(this, mode);
        AutoGreetingStore.setStatus(this, statusLabel() + " 실행 중");
        updateNotification(statusLabel() + " 실행 중");
    }

    private ScreenSize screenSize() {
        WindowManager manager = getSystemService(WindowManager.class);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && manager != null) {
            WindowMetrics windowMetrics = manager.getMaximumWindowMetrics();
            Rect bounds = windowMetrics.getBounds();
            width = Math.max(1, bounds.width());
            height = Math.max(1, bounds.height());
        }
        return new ScreenSize(width, height, metrics.densityDpi);
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastFrameMs < FRAME_INTERVAL_MS || !ocrBusy.compareAndSet(false, true)) {
                return;
            }
            lastFrameMs = now;
            Bitmap bitmap = bitmapFromImage(image);
            image.close();
            image = null;
            if (bitmap == null) {
                ocrBusy.set(false);
                return;
            }
            processWithRecognizer(0, bitmap);
        } catch (RuntimeException error) {
            ocrBusy.set(false);
            AutoGreetingStore.setStatus(this, "화면 처리 오류: " + safeMessage(error));
        } finally {
            if (image != null) image.close();
        }
    }

    private Bitmap bitmapFromImage(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length == 0) return null;
        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        int bitmapWidth = image.getWidth() + rowPadding / pixelStride;
        Bitmap padded = Bitmap.createBitmap(
                bitmapWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap cropped = Bitmap.createBitmap(
                padded, 0, 0, image.getWidth(), image.getHeight());
        if (cropped != padded) padded.recycle();

        int maxWidth = 1440;
        if (cropped.getWidth() <= maxWidth) return cropped;
        int scaledHeight = Math.max(1,
                Math.round(cropped.getHeight() * (maxWidth / (float) cropped.getWidth())));
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, maxWidth, scaledHeight, true);
        if (scaled != cropped) cropped.recycle();
        return scaled;
    }

    private void processWithRecognizer(int index, Bitmap bitmap) {
        List<TextRecognizer> recognizers = Arrays.asList(
                koreanRecognizer, chineseRecognizer, latinRecognizer);
        if (index >= recognizers.size()) {
            finishFrame(bitmap);
            return;
        }
        TextRecognizer recognizer = recognizers.get(index);
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(text -> {
                    String recognized = text.getText();
                    AutoGreetingStore.recordOcrFrame(
                            this,
                            recognized,
                            System.currentTimeMillis());
                    if (handleRecognizedText(recognized)) {
                        finishFrame(bitmap);
                    } else {
                        processWithRecognizer(index + 1, bitmap);
                    }
                })
                .addOnFailureListener(error ->
                        processWithRecognizer(index + 1, bitmap));
    }

    private boolean handleRecognizedText(String text) {
        long now = System.currentTimeMillis();
        if (MODE_LOCAL_TEST.equals(mode)) {
            return handleStrictJoinText(text, now);
        }
        if (MODE_AUTO_GREETING.equals(mode)) {
            boolean handled = handleAutoBroadcastEvents(text, now);
            if (AdaptiveAiStore.enabled(this)
                    && AdaptiveAiStore.conversationEnabled(this)) {
                handled |= handleSafeChatMessages(text, now);
            }
            return handled;
        }
        return handleDetectOnlyEvents(text, now);
    }

    /**
     * 실방송 자동 인사는 엄격한 입장 문구만 처리한다.
     * 일반 댓글, 좋아요, 선물, 팔로우, 학습 문구는 음성 대기열에 넣지 않는다.
     */
    private boolean handleStrictJoinText(String text, long now) {
        boolean handled = false;
        for (JoinEvent join : joinParser.parseAll(text)) {
            String nickname = join.nickname == null ? "" : join.nickname.trim();
            if (nickname.isEmpty()) continue;

            // 같은 방송 세션에서는 같은 닉네임을 한 번만 인사한다.
            String key = "JOIN|" + nickname.toLowerCase(java.util.Locale.ROOT);
            if (!joinSeen.markIfNew(key, now, JOIN_SESSION_TTL_MS)) continue;

            String language = AdaptiveAiStore.enabled(this)
                    ? AdaptiveAiStore.resolveEventLanguage(
                            this, nickname, join.languageHint)
                    : join.languageHint;
            LiveEvent event = new LiveEvent(
                    EventType.JOIN,
                    nickname,
                    "",
                    join.rawText,
                    language,
                    now);

            handled = true;
            AutoGreetingStore.recordEvent(this, event);
            if (AdaptiveAiStore.enabled(this)) {
                AdaptiveAiStore.observeEvent(this, event);
            }

            String status = nickname + " · 입장 감지 · 곡 재생 중 음성 없음";
            AutoGreetingStore.setStatus(this, status);
            updateNotification(status);
            IntermissionStore.recordEvent(this, event);
            LiveOverlayController.show(this, event);

            if (MODE_LOCAL_TEST.equals(mode)) {
                main.postDelayed(() -> {
                    stopCapture("로컬 OCR 입장 감지 성공 · 글 알림 확인");
                    stopSelf();
                }, 1_800L);
                break;
            }
        }
        return handled;
    }

    /**
     * 실방송 모드: 입장/좋아요/선물/팔로우는 작은 글로 표시한다.
     * 곡 재생 중에는 어떤 이벤트도 TTS로 읽지 않고, 선물/팔로우 감사만 다음 곡 직전에 통합한다.
     */
    private boolean handleAutoBroadcastEvents(String text, long now) {
        boolean handled = handleStrictJoinText(text, now);
        Set<String> fingerprints = new HashSet<>();
        for (LiveEvent original : eventParser.parseAll(text)) {
            if (original == null) continue;
            if (original.type != EventType.LIKE
                    && original.type != EventType.GIFT
                    && original.type != EventType.FOLLOW) {
                continue;
            }
            if (!fingerprints.add(original.fingerprint())) continue;
            if (!eventCooldown.markIfNew(original, now)) continue;

            String language = AdaptiveAiStore.enabled(this)
                    ? AdaptiveAiStore.resolveEventLanguage(
                            this,
                            original.nickname,
                            original.languageHint)
                    : original.languageHint;
            LiveEvent event = original.withLanguage(language);

            handled = true;
            AutoGreetingStore.recordEvent(this, event);
            if (AdaptiveAiStore.enabled(this)) {
                AdaptiveAiStore.observeEvent(this, event);
            }
            IntermissionStore.recordEvent(this, event);

            String status = event.nickname + " · " + event.type.koreanLabel
                    + " 글 알림 · 곡 사이 감사 대기";
            AutoGreetingStore.setStatus(this, status);
            updateNotification(status);
            LiveOverlayController.show(this, event);
        }
        return handled;
    }

    /** 감지만 모드는 4개 이벤트를 글로 표시하고 음성은 출력하지 않는다. */
    private boolean handleDetectOnlyEvents(String text, long now) {
        List<LiveEvent> parsed = new ArrayList<>(eventParser.parseAll(text));
        if (AdaptiveAiStore.enabled(this)) {
            parsed.addAll(LearnedEventMatcher.match(
                    text,
                    AdaptiveAiStore.eventRules(this)));
        }

        Set<String> fingerprints = new HashSet<>();
        boolean handled = false;
        for (LiveEvent original : parsed) {
            if (original == null) continue;
            if (original.type != EventType.JOIN
                    && original.type != EventType.LIKE
                    && original.type != EventType.GIFT
                    && original.type != EventType.FOLLOW) {
                continue;
            }
            if (!fingerprints.add(original.fingerprint())) continue;
            if (!eventCooldown.markIfNew(original, now)) continue;

            String language = AdaptiveAiStore.enabled(this)
                    ? AdaptiveAiStore.resolveEventLanguage(
                            this,
                            original.nickname,
                            original.languageHint)
                    : original.languageHint;
            LiveEvent event = original.withLanguage(language);

            handled = true;
            AutoGreetingStore.recordEvent(this, event);
            if (AdaptiveAiStore.enabled(this)) {
                AdaptiveAiStore.observeEvent(this, event);
            }
            String status = event.nickname + " · " + event.type.koreanLabel + " 글 알림 · 음성 없음";
            AutoGreetingStore.setStatus(this, status);
            updateNotification(status);
            LiveOverlayController.show(this, event);
        }
        return handled;
    }


    /**
     * Safe adaptive conversational AI. It learns from chat and shows a small visual reply only.
     * Entry notifications never receive an AI reply; they remain event records for the
     * between-song welcome and next-song voice announcement.
     * It never types into BIGO and never speaks over a song, so keyboard and TTS loops are avoided.
     * Song requests are learned as phrases but always receive a fixed, polite
     * original-songs-only refusal; learning can never turn acceptance on.
     */
    private boolean handleSafeChatMessages(String text, long now) {
        boolean handled = false;
        String host = AdaptiveAiStore.hostNickname(this).trim();
        for (ChatMessage chat : chatParser.parseAll(text)) {
            // BIGO 입장 문구는 이벤트로만 기록한다. AI 댓글 답변·학습 대상에서 제외한다.
            if (!AutoReplyPolicy.shouldAutoReply(chat)) continue;
            if (!host.isEmpty() && host.equalsIgnoreCase(chat.nickname.trim())) continue;
            if (!chatSeen.markIfNew(chat.fingerprint(), now, CHAT_REPLY_TTL_MS)) continue;

            handled = true;
            AdaptiveAiStore.observeChat(this, chat);
            String language = AdaptiveAiStore.resolveChatLanguage(
                    this, chat.nickname, chat.message);
            ConversationIntent intent = ConversationEngine.classify(
                    chat.message, language);
            boolean learnedSongRequest = intent == ConversationIntent.UNKNOWN
                    && AdaptiveAiStore.isLearnedSongRequest(this, chat.message);
            String answer;
            if (intent == ConversationIntent.SONG_REQUEST || learnedSongRequest) {
                int requestCount = AdaptiveAiStore.recordSongRequest(
                        this, chat.nickname, chat.message, language);
                answer = ConversationEngine.songRequestRefusal(
                        chat.nickname, language, requestCount).text;
            } else {
                answer = AdaptiveAiStore.customReply(this, chat.message, language);
                if (answer.isEmpty()) {
                    ConversationEngine.Reply reply = ConversationEngine.reply(
                            chat.nickname, chat.message, language);
                    if (reply.shouldSpeak()) answer = reply.text;
                }
            }
            if (answer.isEmpty()) {
                AdaptiveAiStore.recordCandidate(
                        this, chat.nickname, chat.message, language);
                AutoGreetingStore.setStatus(
                        this, chat.nickname + " · AI 학습 후보 저장");
                continue;
            }

            LiveEvent dialogue = new LiveEvent(
                    EventType.CHAT,
                    chat.nickname,
                    answer,
                    chat.rawText,
                    language,
                    now);
            AutoGreetingStore.recordEvent(this, dialogue);
            LiveOverlayController.showDialogue(
                    this, chat.nickname, answer, language);
            AutoGreetingStore.setStatus(
                    this, chat.nickname + " · 습득·진화 AI 화면 답변 · 키보드 없음");
            updateNotification(chat.nickname + " · AI 화면 답변");
        }
        return handled;
    }

    private void finishFrame(Bitmap bitmap) {
        bitmap.recycle();
        ocrBusy.set(false);
    }

    private String statusLabel() {
        if (MODE_LOCAL_TEST.equals(mode)) return "로컬 OCR 테스트";
        if (MODE_AUTO_GREETING.equals(mode)) return "BIGO 이벤트 글 · 안전 대화형 AI · 곡 사이 5개 언어 안내";
        return "BIGO 이벤트 글 알림 · 음성 없음";
    }

    private void stopCapture(String status) {
        AutoGreetingStore.setStatus(this, status);
        AutoGreetingStore.setRunningMode(this, "");
        updateNotification(status);
        releaseProjection();
    }

    private void releaseProjection() {
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (projection != null) {
            MediaProjection current = projection;
            projection = null;
            try { current.stop(); } catch (RuntimeException ignored) {}
        }
        ocrBusy.set(false);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_ocr_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.screen_ocr_channel_description));
        channel.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification(text));
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle("MARU BIGO 입장 감지")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        AutoGreetingStore.setRunningMode(this, "");
        releaseProjection();
        if (koreanRecognizer != null) koreanRecognizer.close();
        if (chineseRecognizer != null) chineseRecognizer.close();
        if (latinRecognizer != null) latinRecognizer.close();
        main.removeCallbacksAndMessages(null);
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    private static final class ScreenSize {
        final int width;
        final int height;
        final int densityDpi;

        ScreenSize(int width, int height, int densityDpi) {
            this.width = width;
            this.height = height;
            this.densityDpi = densityDpi;
        }
    }
}
