package com.maru.musiclive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends ComponentActivity implements PlaybackService.Listener {
    private static final long CONTROLS_HIDE_DELAY_MS = 5_000L;
    private static final long IMAGE_CHANGE_MS = 12_000L;
    private static final long HOST_SPEECH_AUTO_RESTORE_MS = 45_000L;
    private static final int MAX_EVENT_OVERLAY_QUEUE = 12;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> songs = new ArrayList<>();
    private final List<String> images = new ArrayList<>();
    private final List<String> lyricDocs = new ArrayList<>();
    private final List<LyricsCore.Line> lyrics = new ArrayList<>();
    private final List<String> activeSongMedia = new ArrayList<>();
    private final ArrayDeque<OverlayMessage> eventOverlayQueue = new ArrayDeque<>();

    private PlaybackService playback;
    private boolean bound;
    private boolean broadcastVisible;
    private boolean localTestMode;
    private boolean pendingAutoMusicStart;
    private boolean eventOverlayShowing;
    private int overlayGeneration;
    private int imageIndex;
    private int mediaIndex;
    private int activeEventPriority;
    private long activeEventUntil;
    private String pendingSongMediaUri = "";
    private EventType pendingEventVisualType = EventType.UNKNOWN;
    private BroadcastMode selectedMode = BroadcastMode.PORTRAIT_9_16;

    private FrameLayout appRoot;
    private ScrollView setupScreen;
    private FrameLayout broadcastScreen;
    private ImageView backgroundImage;
    private ImageView foregroundImage;
    private VideoView mediaVideo;
    private TextView titleView;
    private TextView lyricView;
    private TextView timeView;
    private TextView testOverlayView;
    private TextView autoGreetingStatusView;
    private LinearLayout controls;
    private Button playButton;
    private ListView songList;

    private ActivityResultLauncher<String[]> audioPickerLauncher;
    private ActivityResultLauncher<String[]> imagePickerLauncher;
    private ActivityResultLauncher<String[]> lyricsPickerLauncher;
    private ActivityResultLauncher<String[]> greetingAudioPickerLauncher;
    private ActivityResultLauncher<String[]> songMediaPickerLauncher;
    private ActivityResultLauncher<String[]> eventVisualPickerLauncher;
    private ActivityResultLauncher<String> aiBackupCreateLauncher;
    private ActivityResultLauncher<String[]> aiBackupOpenLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<Intent> screenCaptureLauncher;
    private String pendingCaptureMode = ScreenOcrGreetingService.MODE_LOCAL_TEST;

    private final Runnable updateProgress = new Runnable() {
        @Override public void run() {
            if (playback != null && broadcastVisible
                    && timeView != null && lyricView != null) {
                int position = playback.position();
                int duration = playback.duration();
                timeView.setText(formatTime(position) + " / " + formatTime(duration));
                String current = LyricsCore.twoLines(lyrics, position);
                lyricView.setText(current);
                lyricView.setVisibility(
                        current.trim().isEmpty() ? View.GONE : View.VISIBLE);
            }
            handler.postDelayed(this, 250L);
        }
    };

    private final Runnable rotateImage = new Runnable() {
        @Override public void run() {
            if (broadcastVisible
                    && System.currentTimeMillis() >= activeEventUntil
                    && !activeSongMedia.isEmpty()
                    && (mediaVideo == null
                    || mediaVideo.getVisibility() != View.VISIBLE)) {
                advanceSongMedia();
            }
            handler.postDelayed(this, localTestMode ? 4_000L : IMAGE_CHANGE_MS);
        }
    };

    private final Runnable hideControls = () -> {
        if (localTestMode) return;
        if (controls != null) controls.setVisibility(View.GONE);
        if (titleView != null) titleView.setVisibility(View.GONE);
        if (timeView != null) timeView.setVisibility(View.GONE);
    };

    private final Runnable restoreHostSpeech = this::endHostSpeechDuck;

    private final BroadcastReceiver liveEventReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (AutoGreetingService.ACTION_BROADCAST_CLOSED.equals(action)) {
                finishBroadcast();
                toast("방송 안내와 음악을 완전히 종료했습니다.");
                return;
            }
            if (LiveOverlayController.ACTION_SHOW.equals(action)) {
                handleLiveEventOverlay(intent);
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            playback = ((PlaybackService.LocalBinder) service).service();
            playback.addListener(MainActivity.this);
            playback.setQueue(songs);
            playback.setRepeatAll(AppStorage.repeatAll(MainActivity.this));
            playback.setRandomMode(AppStorage.random(MainActivity.this));
            bound = true;
            if (pendingAutoMusicStart
                    || (localTestMode && broadcastVisible)
                    || ScreenOcrGreetingService.MODE_AUTO_GREETING.equals(
                            AutoGreetingStore.runningMode(
                                    MainActivity.this))) {
                pendingAutoMusicStart = false;
                playback.prepareForBroadcast();
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            if (playback != null) playback.removeListener(MainActivity.this);
            playback = null;
            bound = false;
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        allowAudioCapture();
        // V3.1.1: V3.1.0에서 강제로 꺼진 대화형 AI를 안전한 화면 답변 모드로 1회 복원한다.
        AdaptiveAiStore.migrateSafeConversationV311(this);
        registerLaunchers();
        IntentFilter liveFilter = new IntentFilter();
        liveFilter.addAction(LiveOverlayController.ACTION_SHOW);
        liveFilter.addAction(AutoGreetingService.ACTION_BROADCAST_CLOSED);
        ContextCompat.registerReceiver(
                this,
                liveEventReceiver,
                liveFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        loadStoredData();
        ensureDefaultMedia();
        buildSetupScreen();
        startAndBindPlayback();
        requestNotificationPermission();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (broadcastVisible) showBroadcastEndMenu();
                else finish();
            }
        });
    }

    private void registerLaunchers() {
        audioPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    for (Uri uri : uris) {
                        persist(uri);
                        if (!songs.contains(uri.toString())) songs.add(uri.toString());
                    }
                    AppStorage.saveSongs(this, songs);
                    refreshSongList();
                    if (playback != null) playback.setQueue(songs);
                });

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    for (Uri uri : uris) {
                        persist(uri);
                        if (!images.contains(uri.toString())) images.add(uri.toString());
                    }
                    AppStorage.saveImages(this, images);
                    toast(images.size() + "개 이미지 등록");
                });

        lyricsPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    for (Uri uri : uris) {
                        persist(uri);
                        if (!lyricDocs.contains(uri.toString())) lyricDocs.add(uri.toString());
                    }
                    AppStorage.saveLyrics(this, lyricDocs);
                    loadLyricsForCurrentSong();
                    toast(lyricDocs.size() + "개 가사 등록");
                });

        greetingAudioPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    persist(uri);
                    AutoGreetingStore.setGreetingAudioUri(this, uri.toString());
                    toast("이 버전은 곡 사이에만 휴대폰 TTS를 사용합니다.");
                    refreshAutoGreetingStatus();
                });

        songMediaPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (pendingSongMediaUri.isEmpty()) return;
                    List<String> values = new ArrayList<>();
                    for (Uri uri : uris) {
                        persist(uri);
                        values.add(uri.toString());
                    }
                    SongMediaStore.add(this, pendingSongMediaUri, values);
                    toast(values.size() + "개 곡 전용 이미지·영상 연결");
                    if (pendingSongMediaUri.equals(currentSongUri())) {
                        loadActiveSongMedia();
                    }
                });

        eventVisualPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null || pendingEventVisualType == EventType.UNKNOWN) return;
                    persist(uri);
                    EventVisualStore.setMediaUri(
                            this,
                            pendingEventVisualType,
                            uri.toString());
                    toast(pendingEventVisualType.koreanLabel + " 화면 등록");
                });

        aiBackupCreateLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri == null) return;
                    writeText(uri, AdaptiveAiStore.exportJson(this));
                });

        aiBackupOpenLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    String text = readText(uri);
                    boolean ok = AdaptiveAiStore.importJson(this, text);
                    toast(ok ? "AI 학습 데이터 복원 완료" : "AI 백업 파일 형식 오류");
                    refreshAutoGreetingStatus();
                });

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {});

        screenCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleScreenCaptureResult);
    }

    private void loadStoredData() {
        songs.addAll(AppStorage.loadSongs(this));
        images.addAll(AppStorage.loadImages(this));
        lyricDocs.addAll(AppStorage.loadLyrics(this));
        selectedMode = BroadcastMode.fromStored(AppStorage.broadcastMode(this));
    }

    private void ensureDefaultMedia() {
        if (songs.isEmpty()) {
            songs.add(resourceUri(R.raw.actual_music));
            AppStorage.saveSongs(this, songs);
        }

        if (images.isEmpty()) {
            images.add(
                    resourceUri(
                            R.drawable.actual_image_01));
            images.add(
                    resourceUri(
                            R.drawable.actual_image_02));
            AppStorage.saveImages(this, images);
        }

        // Remove the old built-in timed instructions. They were the
        // permanent BIGO warning text seen in previous versions.
        String oldDefaultLyrics =
                resourceUri(R.raw.actual_lyrics);
        boolean removed =
                lyricDocs.removeIf(
                        oldDefaultLyrics::equals);
        if (removed) {
            AppStorage.saveLyrics(this, lyricDocs);
        }

        loadLyricsForCurrentSong();
    }

    private String resourceUri(int resourceId) {
        return "android.resource://" + getPackageName() + "/" + resourceId;
    }

    private void buildSetupScreen() {
        appRoot = new FrameLayout(this);
        appRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.maru_bg));

        setupScreen = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(24), dp(20), dp(40));
        setupScreen.addView(column);

        TextView heading = text("MARU MUSIC LIVE GAME", 27, true);
        heading.setTextColor(ContextCompat.getColor(this, R.color.maru_accent));
        column.addView(heading);

        TextView version = text(
                "V3.1.2 · 상단 이미지 꽉 채움 · 습득·진화 AI 화면 답변 · 무키보드 종료 · 랜덤 20분 차단",
                15,
                true);
        version.setTextColor(ContextCompat.getColor(this, R.color.maru_subtext));
        column.addView(version);

        TextView coreNotice = text(
                "노래가 재생되는 동안에는 노래 소리만 나옵니다. "
                        + "입장·좋아요·선물·팔로우는 14sp 작은 글로 표시하고, 음성 안내는 곡과 곡 사이에만 나옵니다.",
                15,
                true);
        coreNotice.setBackgroundColor(0x6651246B);
        column.addView(coreNotice);

        TextView limitNotice = text(
                "곡 사이 통합 안내는 매번 한국어→영어→중국어→일본어→러시아어 다섯 언어를 모두 연속 재생합니다. "
                        + "대화형 AI는 켠 경우에만 인사·감사·신청곡 같은 안전 문구를 학습하고 작은 화면 답변만 표시합니다. “게임 좋아하세요?” 같은 미학습 질문은 자동 답변하지 않습니다. 키보드 자동 입력과 곡 중 TTS는 사용하지 않습니다.",
                14,
                false);
        limitNotice.setBackgroundColor(0x553D7A46);
        column.addView(limitNotice);

        autoGreetingStatusView = text("AI·OCR 상태 확인 중", 14, false);
        autoGreetingStatusView.setBackgroundColor(0x553D7A46);
        column.addView(autoGreetingStatusView);

        column.addView(button(
                "1. OCR 입장 로컬 테스트 · BIGO 미실행",
                v -> requestScreenCapture(ScreenOcrGreetingService.MODE_LOCAL_TEST)));
        column.addView(button(
                "2. BIGO 이벤트 글 알림만 · 음성 없음",
                v -> requestScreenCapture(ScreenOcrGreetingService.MODE_DETECT_ONLY)));
        column.addView(button(
                "3. 이벤트 글 + 습득·진화 AI 화면 답변 + 음악 + 곡 사이 5개 언어",
                v -> requestScreenCapture(ScreenOcrGreetingService.MODE_AUTO_GREETING)));
        column.addView(button(
                "4. BIGO 게임 LIVE 음악 송출 우선 시작",
                v -> startBigoMusicOnly()));
        column.addView(button(
                "5. OCR·곡 사이 안내 중지",
                v -> {
                    ScreenOcrGreetingService.stop(this);
                    AutoGreetingService.cancel(this);
                    AutoGreetingStore.setRunningMode(this, "");
                    AutoGreetingStore.setStatus(this, "중지 요청 완료");
                    refreshAutoGreetingStatus();
                }));

        column.addView(button(
                "5개 언어 연속 통합 안내 테스트",
                v -> runActualGreetingTest()));
        column.addView(button(
                "휴대폰 TTS 음성 설정",
                v -> openTtsSettings()));

        CheckBox adaptive = checkBox(
                "AI 습득·청취자 언어 기억",
                AdaptiveAiStore.enabled(this));
        adaptive.setOnCheckedChangeListener((button, checked) -> {
            AdaptiveAiStore.setEnabled(this, checked);
            refreshAutoGreetingStatus();
        });
        column.addView(adaptive);

        CheckBox conversation = checkBox(
                "습득·진화 대화형 AI · 작은 화면 답변 · 키보드 없음",
                AdaptiveAiStore.conversationEnabled(this));
        conversation.setOnCheckedChangeListener((button, checked) -> {
            AdaptiveAiStore.setConversationEnabled(this, checked);
            toast(checked
                    ? "대화형 AI 화면 답변을 켰습니다. 노래 중 음성은 나오지 않습니다."
                    : "대화형 AI 화면 답변을 껐습니다.");
            refreshAutoGreetingStatus();
        });
        column.addView(conversation);

        column.addView(button(
                "습득·진화 AI 학습·언어·백업 관리",
                v -> showAiManagementDialog()));
        column.addView(button(
                "입장 감사 화면 설정",
                v -> showEventVisualSettings()));
        column.addView(button(
                "선택 곡 이미지·MP4 연결",
                v -> showSongMediaPickerDialog()));

        column.addView(space(10));
        column.addView(button(
                "6. YouTube 공식 연결",
                v -> startActivity(new Intent(this, YouTubePlayerActivity.class))));
        column.addView(button(
                "7. 화면·음악·입장인사 로컬 테스트",
                v -> startLocalTest()));
        column.addView(button(
                "8. 실방송 화면 시작",
                v -> showLiveBroadcastWarning()));

        column.addView(space(12));
        column.addView(button("노래 추가", v ->
                audioPickerLauncher.launch(new String[]{"audio/*"})));
        column.addView(button("노래 전체 삭제", v -> confirmDeleteAllSongs()));
        column.addView(button("공통 배경 이미지 추가", v ->
                imagePickerLauncher.launch(new String[]{"image/*"})));
        column.addView(button("가사 추가 (LRC/TXT)", v ->
                lyricsPickerLauncher.launch(
                        new String[]{"text/*", "application/octet-stream"})));

        CheckBox repeat = checkBox("전체 반복", AppStorage.repeatAll(this));
        repeat.setOnCheckedChangeListener((buttonView, checked) -> {
            AppStorage.setRepeatAll(this, checked);
            if (playback != null) playback.setRepeatAll(checked);
        });
        column.addView(repeat);

        CheckBox random = checkBox("랜덤 재생 · 같은 곡 20분 절대 중복 차단", AppStorage.random(this));
        random.setOnCheckedChangeListener((buttonView, checked) -> {
            AppStorage.setRandom(this, checked);
            if (playback != null) playback.setRandomMode(checked);
        });
        column.addView(random);

        CheckBox songTitleTts = checkBox(
                "곡 사이 5개 언어 통합 안내",
                AppStorage.songTitleTts(this));
        songTitleTts.setOnCheckedChangeListener((buttonView, checked) ->
                AppStorage.setSongTitleTts(this, checked));
        column.addView(songTitleTts);

        CheckBox fillImage = checkBox(
                "상단 분할 화면 이미지 좌우 여백 없이 꽉 채우기",
                AppStorage.fillBroadcastImage(this));
        fillImage.setOnCheckedChangeListener((buttonView, checked) -> {
            AppStorage.setFillBroadcastImage(this, checked);
            toast(checked ? "이미지를 화면 폭에 맞춰 꽉 채웁니다." : "이미지 전체 보기로 바꿨습니다.");
        });
        column.addView(fillImage);

        TextView songGuide = text(
                "노래 터치: 재생 · 길게: 삭제 · 곡별 이미지/MP4는 위 버튼에서 연결",
                14,
                false);
        songGuide.setTextColor(ContextCompat.getColor(this, R.color.maru_subtext));
        column.addView(songGuide);

        songList = new ListView(this);
        songList.setDividerHeight(1);
        songList.setOnItemClickListener((parent, view, position, id) -> {
            if (playback != null) playback.play(position);
        });
        songList.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDeleteSong(position);
            return true;
        });
        column.addView(songList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(300)));
        refreshSongList();
        refreshAutoGreetingStatus();

        appRoot.addView(setupScreen);
        setContentView(appRoot);
    }

    private void requestScreenCapture(String mode) {
        pendingCaptureMode = mode;
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            toast("화면 공유 기능을 사용할 수 없습니다.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("BIGO 이벤트 OCR · 노래 중 음악만")
                .setMessage(
                        "다음 화면 공유 확인창에서 ‘전체 화면’을 선택하세요.\n\n"
                                + "노래 재생 중에는 노래 소리만 나옵니다. 입장·좋아요·선물·팔로우는 14sp 작은 글로 표시합니다.\n"
                                + "선물·팔로우 감사와 다음 곡 제목은 곡 사이마다 한국어·영어·중국어·일본어·러시아어 다섯 언어로 모두 안내합니다. 일반 댓글은 무시합니다.")
                .setPositiveButton("화면 공유 계속", (dialog, which) ->
                        screenCaptureLauncher.launch(manager.createScreenCaptureIntent()))
                .setNegativeButton("취소", null)
                .show();
    }

    private void handleScreenCaptureResult(ActivityResult result) {
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null) {
            AutoGreetingStore.setStatus(this, "화면 공유가 취소되었습니다.");
            refreshAutoGreetingStatus();
            toast("화면 공유가 취소되었습니다.");
            return;
        }
        try {
            String requestedMode = pendingCaptureMode;
            if (ScreenOcrGreetingService.MODE_AUTO_GREETING.equals(requestedMode)) {
                IntermissionStore.resetSession(this);
            }
            ScreenOcrGreetingService.start(
                    this,
                    result.getResultCode(),
                    data,
                    requestedMode);

            if (ScreenOcrGreetingService.MODE_AUTO_GREETING.equals(
                    requestedMode)) {
                startMusicForBroadcast();
            }

            handler.postDelayed(() -> {
                if (ScreenOcrGreetingService.MODE_LOCAL_TEST.equals(requestedMode)) {
                    startActivity(new Intent(this, OcrTestActivity.class));
                } else {
                    openBigoChat();
                }
            }, 650L);
        } catch (RuntimeException error) {
            AutoGreetingStore.setStatus(this, "입장 감지 시작 실패: " + error.getMessage());
            refreshAutoGreetingStatus();
            toast("입장 감지를 시작하지 못했습니다.");
        }
    }

    private void startBigoMusicOnly() {
        ScreenOcrGreetingService.stop(this);
        AutoGreetingService.cancel(this);
        IntermissionStore.resetSession(this);
        startMusicForBroadcast();
        AutoGreetingStore.setRunningMode(this, "music_only");
        AutoGreetingStore.setStatus(this, "노래 재생 중 음악만 · 곡 사이 5개 언어 안내 대기");
        refreshAutoGreetingStatus();
        handler.postDelayed(this::openBigoChat, 350L);
    }

    private void runActualGreetingTest() {
        StringBuilder preview = new StringBuilder();
        String[] languages = IntermissionStore.announcementLanguages();
        for (int i = 0; i < languages.length; i++) {
            if (i > 0) preview.append("\n\n");
            preview.append(IntermissionAnnouncementText.build(
                    "별빛 아래서",
                    languages[i],
                    java.util.Collections.singletonList("Test Listener"),
                    java.util.Collections.singletonList("New Friend")));
        }

        new AlertDialog.Builder(this)
                .setTitle("5개 언어 연속 통합 안내 테스트")
                .setMessage(preview.toString())
                .setPositiveButton("실제 음성 테스트", (dialog, which) ->
                        AutoGreetingService.announceIntermission(
                                this,
                                "별빛 아래서",
                                java.util.Collections.singletonList("Test Listener"),
                                java.util.Collections.singletonList("New Friend"),
                                -1))
                .setNegativeButton("닫기", null)
                .show();
    }

    private void openTtsSettings() {
        Intent intent =
                new Intent(
                        "com.android.settings.TTS_SETTINGS");

        if (intent.resolveActivity(
                getPackageManager()) != null) {
            startActivity(intent);
            return;
        }

        try {
            startActivity(
                    new Intent(
                            android.provider.Settings
                                    .ACTION_SETTINGS));
        } catch (RuntimeException error) {
            toast("휴대폰 TTS 설정을 열 수 없습니다.");
        }
    }

    private boolean isScreenOcrRunning() {
        return !AutoGreetingStore.runningMode(this).trim().isEmpty();
    }

    private void refreshAutoGreetingStatus() {
        if (autoGreetingStatusView == null) return;
        String running = AutoGreetingStore.runningMode(this);
        String mode;
        if (ScreenOcrGreetingService.MODE_AUTO_GREETING.equals(running)) {
            mode = "BIGO 이벤트 글 · 안전 대화형 AI 화면 답변 · 곡 사이 5개 언어 안내";
        } else if (ScreenOcrGreetingService.MODE_DETECT_ONLY.equals(running)) {
            mode = "BIGO 이벤트 글 알림 실행 중 · 음성 없음";
        } else if (ScreenOcrGreetingService.MODE_LOCAL_TEST.equals(running)) {
            mode = "로컬 OCR 입장 테스트 중";
        } else if ("music_only".equals(running)) {
            mode = "BIGO 게임 LIVE 음악만 재생 · 곡 사이 5개 언어 안내";
        } else {
            mode = "OCR 이벤트 감지 중지됨";
        }

        String lastName = AutoGreetingStore.lastNickname(this);
        EventType lastType = AutoGreetingStore.lastEventType(this);
        String last = lastName.isEmpty()
                ? "아직 이벤트 감지 기록 없음"
                : "마지막: "
                        + lastName
                        + " · "
                        + lastType.koreanLabel
                        + " · "
                        + GreetingLanguage.koreanLabel(
                                AutoGreetingStore.lastLanguage(this));

        String ocrText = AutoGreetingStore.lastOcrText(this)
                .replace('\n', ' ')
                .trim();
        if (ocrText.length() > 70) ocrText = ocrText.substring(0, 70) + "…";

        autoGreetingStatusView.setText(
                mode + "\n"
                        + "매 곡 사이 TTS: 한국어 → 영어 → 중국어 → 일본어 → 러시아어 모두 연속\n"
                        + "AI 습득: " + (AdaptiveAiStore.enabled(this) ? "켜짐" : "꺼짐")
                        + " · 대화형 화면 답변: "
                        + (AdaptiveAiStore.conversationEnabled(this) ? "켜짐" : "꺼짐")
                        + " · 댓글 음성 답변: 항상 꺼짐"
                        + " · 미확인 언어: "
                        + GreetingLanguage.koreanLabel(AdaptiveAiStore.defaultLanguage(this))
                        + "\n"
                        + last + "\n"
                        + "입장 " + AutoGreetingStore.eventCount(this, EventType.JOIN)
                        + " · 좋아요 " + AutoGreetingStore.eventCount(this, EventType.LIKE)
                        + " · 선물 " + AutoGreetingStore.eventCount(this, EventType.GIFT)
                        + " · 팔로우 " + AutoGreetingStore.eventCount(this, EventType.FOLLOW)
                        + "\n"
                        + "최근 OCR: " + (ocrText.isEmpty() ? "없음" : ocrText)
                        + " · 프레임 " + AutoGreetingStore.ocrFrameCount(this) + "회\n"
                        + "상태: " + AutoGreetingStore.status(this));
    }

    private void startLocalTest() {
        localTestMode = true;
        selectedMode =
                BroadcastMode.PORTRAIT_9_16;
        pendingAutoMusicStart = true;
        startBroadcast();
    }

    private void showLiveBroadcastWarning() {
        new AlertDialog.Builder(this)
                .setTitle("실방송 시작 전 확인")
                .setMessage(
                        "로컬 테스트에서 음악·이미지·가사·안내음성을 모두 확인했습니까?\n\n"
                                + "실방송 화면에서는 청취자에게 소리와 화면이 전달될 수 있습니다.")
                .setPositiveButton("확인 후 계속", (dialog, which) -> {
                    localTestMode = false;
                    showBroadcastModeDialog();
                })
                .setNegativeButton("로컬 테스트로 돌아가기", null)
                .show();
    }

    private void showBroadcastModeDialog() {
        localTestMode = false;
        String[] labels = {
                BroadcastMode.PORTRAIT_9_16.label,
                BroadcastMode.LANDSCAPE_16_9.label
        };
        int selected = selectedMode == BroadcastMode.PORTRAIT_9_16 ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle("방송 화면 선택")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    selectedMode = which == 0
                            ? BroadcastMode.PORTRAIT_9_16
                            : BroadcastMode.LANDSCAPE_16_9;
                    AppStorage.setBroadcastMode(this, selectedMode.name());
                    dialog.dismiss();
                    startBroadcast();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void startBroadcast() {
        broadcastVisible = true;

        if (localTestMode) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (selectedMode == BroadcastMode.LANDSCAPE_16_9) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        hideSystemBars();

        broadcastScreen = new FrameLayout(this);
        broadcastScreen.setBackgroundColor(Color.BLACK);

        backgroundImage = new ImageView(this);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundImage.setAlpha(0.58f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundImage.setRenderEffect(
                    RenderEffect.createBlurEffect(
                            28f, 28f, Shader.TileMode.CLAMP));
        }
        broadcastScreen.addView(backgroundImage, match());

        View shade = new View(this);
        shade.setBackgroundColor(0x55000000);
        broadcastScreen.addView(shade, match());

        mediaVideo = new VideoView(this);
        mediaVideo.setVisibility(View.GONE);
        broadcastScreen.addView(mediaVideo, match());

        foregroundImage = new ImageView(this);
        foregroundImage.setScaleType(AppStorage.fillBroadcastImage(this)
                ? ImageView.ScaleType.CENTER_CROP
                : ImageView.ScaleType.FIT_CENTER);
        broadcastScreen.addView(foregroundImage, match());

        titleView = text(
                localTestMode
                        ? "로컬 테스트 · BIGO 미실행"
                        : currentTitle(),
                BroadcastVisualProfile.TITLE_SP, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setBackgroundColor(
                localTestMode ? 0xCC225A30 : 0x66000000);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(BroadcastVisualProfile.TITLE_HEIGHT_DP),
                Gravity.TOP);
        titleParams.topMargin = dp(BroadcastVisualProfile.TITLE_TOP_MARGIN_DP);
        broadcastScreen.addView(titleView, titleParams);

        testOverlayView = text("", BroadcastVisualProfile.EVENT_SP, true);
        testOverlayView.setGravity(Gravity.CENTER);
        testOverlayView.setTextColor(Color.WHITE);
        testOverlayView.setSingleLine(true);
        testOverlayView.setMaxLines(1);
        testOverlayView.setEllipsize(TextUtils.TruncateAt.END);
        testOverlayView.setIncludeFontPadding(false);
        testOverlayView.setPadding(dp(12), 0, dp(12), 0);
        testOverlayView.setBackground(eventOverlayBackground());
        testOverlayView.setVisibility(View.GONE);
        int overlayWidth = BroadcastVisualProfile.overlayWidthPx(
                getResources().getDisplayMetrics().widthPixels,
                dp(BroadcastVisualProfile.EVENT_MIN_WIDTH_DP));
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                overlayWidth,
                dp(BroadcastVisualProfile.EVENT_HEIGHT_DP),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        overlayParams.topMargin = dp(BroadcastVisualProfile.EVENT_TOP_MARGIN_DP);
        broadcastScreen.addView(testOverlayView, overlayParams);

        lyricView = text("", BroadcastVisualProfile.LYRIC_SP, true);
        lyricView.setGravity(Gravity.CENTER);
        lyricView.setTextColor(Color.WHITE);
        lyricView.setMaxLines(2);
        lyricView.setPadding(dp(8), dp(5), dp(8), dp(5));
        lyricView.setBackgroundColor(0x77000000);
        FrameLayout.LayoutParams lyricParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        lyricParams.leftMargin = dp(18);
        lyricParams.rightMargin = dp(18);
        lyricParams.bottomMargin = dp(BroadcastVisualProfile.LYRIC_BOTTOM_MARGIN_DP);
        broadcastScreen.addView(lyricView, lyricParams);

        timeView = text("00:00 / 00:00", BroadcastVisualProfile.TIME_SP, false);
        timeView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams timeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(BroadcastVisualProfile.TIME_HEIGHT_DP),
                Gravity.BOTTOM);
        timeParams.bottomMargin = dp(BroadcastVisualProfile.TIME_BOTTOM_MARGIN_DP);
        broadcastScreen.addView(timeView, timeParams);

        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setBackgroundColor(0xDD101018);

        controls.addView(smallButton("이전", v -> {
            if (playback != null) playback.previous();
        }));
        playButton = smallButton("재생", v -> {
            if (playback != null) playback.toggle();
        });
        controls.addView(playButton);
        controls.addView(smallButton("다음", v -> {
            if (playback != null) playback.next();
        }));

        if (localTestMode) {
            controls.addView(smallButton("가짜 이벤트", v ->
                    showFakeEventDialog()));
            controls.addView(smallButton("안내음성", v ->
                    runGreetingVoiceTest()));
            controls.addView(smallButton("이미지", v ->
                    showNextImageNow()));
            controls.addView(smallButton("가사", v ->
                    runLyricsDisplayTest()));
            controls.addView(smallButton("테스트 끝", v ->
                    finishBroadcast()));
        } else {
            controls.addView(smallButton("답변", v ->
                    showQuickReplyDialog()));
            controls.addView(smallButton("말하기", v ->
                    beginHostSpeechDuck()));
            controls.addView(smallButton("복귀", v ->
                    endHostSpeechDuck()));
            controls.addView(smallButton("BIGO", v ->
                    openBigoChat()));
            controls.addView(smallButton("종료", v ->
                    showBroadcastEndMenu()));
        }

        FrameLayout.LayoutParams controlsParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        dp(64),
                        Gravity.BOTTOM);
        broadcastScreen.addView(controls, controlsParams);

        broadcastScreen.setOnClickListener(v ->
                showControlsTemporarily());
        setContentView(broadcastScreen);

        loadActiveSongMedia();
        showActiveSongMedia();

        loadLyricsForCurrentSong();
        showControlsTemporarily();

        handler.removeCallbacks(updateProgress);
        handler.removeCallbacks(rotateImage);
        handler.post(updateProgress);
        handler.postDelayed(
                rotateImage,
                localTestMode ? 4_000L : IMAGE_CHANGE_MS);

        if (localTestMode) {
            showTemporaryTestOverlay(
                    "로컬 테스트 시작 · 음악을 즉시 재생합니다.",
                    2_500L);
            startMusicForBroadcast();
        }
    }

    private void showBroadcastEndMenu() {
        new AlertDialog.Builder(this)
                .setTitle("방송 종료")
                .setMessage("키보드 없이 미리 저장된 5개 언어 종료 안내를 재생한 뒤 음악·OCR·AI를 모두 끌 수 있습니다.")
                .setPositiveButton("안내 후 완전 종료", (dialog, which) -> beginPresetBroadcastClosing())
                .setNeutralButton("즉시 완전 종료", (dialog, which) -> stopAllBroadcastNow())
                .setNegativeButton("취소", null)
                .show();
    }

    private void beginPresetBroadcastClosing() {
        if (playback != null) playback.pause();
        showTemporaryTestOverlay(
                "오늘 음악 방송은 여기까지입니다.\n함께해 주셔서 감사합니다.",
                12_000L);
        ScreenOcrGreetingService.stop(this);
        boolean started = AutoGreetingService.announceClosing(this);
        if (!started) stopAllBroadcastNow();
    }

    private void stopAllBroadcastNow() {
        Intent stop = new Intent(this, PlaybackService.class)
                .setAction(PlaybackService.ACTION_STOP_ALL);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(stop);
            else startService(stop);
        } catch (RuntimeException ignored) {
            if (playback != null) playback.stopPlayback();
        }
        ScreenOcrGreetingService.stop(this);
        AutoGreetingService.cancel(this);
        finishBroadcast();
        toast("음악·OCR·AI를 완전히 종료했습니다.");
    }

    private void finishBroadcast() {
        boolean wasLocalTest = localTestMode;
        broadcastVisible = false;
        localTestMode = false;

        handler.removeCallbacks(updateProgress);
        handler.removeCallbacks(rotateImage);
        handler.removeCallbacks(hideControls);
        handler.removeCallbacks(restoreHostSpeech);
        endHostSpeechDuck();
        activeEventPriority = 0;
        activeEventUntil = 0L;
        eventOverlayQueue.clear();
        eventOverlayShowing = false;
        if (mediaVideo != null) {
            try { mediaVideo.stopPlayback(); } catch (RuntimeException ignored) {}
        }

        if (wasLocalTest && playback != null) {
            playback.pause();
        }

        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        showSystemBars();
        setContentView(appRoot);
    }

    private void showControlsTemporarily() {
        if (controls == null) return;

        controls.setVisibility(View.VISIBLE);
        titleView.setVisibility(View.VISIBLE);
        timeView.setVisibility(View.VISIBLE);

        handler.removeCallbacks(hideControls);
        if (!localTestMode) {
            handler.postDelayed(
                    hideControls,
                    CONTROLS_HIDE_DELAY_MS);
        }
    }

    private void runFakeViewerTest() {
        runFakeEvent(EventType.JOIN);
    }

    private void showFakeEventDialog() {
        String[] labels = {"입장", "좋아요", "선물", "팔로우"};
        new AlertDialog.Builder(this)
                .setTitle("가짜 이벤트 로컬 테스트")
                .setItems(labels, (dialog, which) -> {
                    EventType[] types = {
                            EventType.JOIN,
                            EventType.LIKE,
                            EventType.GIFT,
                            EventType.FOLLOW
                    };
                    runFakeEvent(types[which]);
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void runFakeEvent(EventType type) {
        String detail = type == EventType.GIFT
                ? "Rose x10"
                : "";
        LiveEvent event = new LiveEvent(
                type,
                "Test Listener",
                detail,
                "LOCAL TEST",
                GreetingLanguage.ENGLISH,
                System.currentTimeMillis());
        LiveOverlayController.show(this, event);
        if (type == EventType.JOIN) {
            AutoGreetingService.announceEvent(this, event);
        }
    }

    private void showEventVisualSettings() {
        EventType[] types = {EventType.JOIN};
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].koreanLabel
                    + (EventVisualStore.mediaUri(this, types[i]).isEmpty()
                    ? " · 미등록"
                    : " · 등록됨");
        }
        new AlertDialog.Builder(this)
                .setTitle("이벤트 감사 이미지·MP4")
                .setItems(labels, (dialog, which) -> {
                    EventType type = types[which];
                    new AlertDialog.Builder(this)
                            .setTitle(type.koreanLabel + " 화면")
                            .setItems(
                                    new String[]{"이미지·MP4 선택", "등록 삭제", "가짜 테스트"},
                                    (menu, action) -> {
                                        if (action == 0) {
                                            pendingEventVisualType = type;
                                            eventVisualPickerLauncher.launch(
                                                    new String[]{"image/*", "video/*"});
                                        } else if (action == 1) {
                                            EventVisualStore.clear(this, type);
                                            toast(type.koreanLabel + " 화면 삭제");
                                        } else {
                                            runFakeEvent(type);
                                        }
                                    })
                            .setNegativeButton("닫기", null)
                            .show();
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showSongMediaPickerDialog() {
        if (songs.isEmpty()) {
            toast("먼저 노래를 추가하세요.");
            return;
        }
        String[] names = new String[songs.size()];
        for (int i = 0; i < songs.size(); i++) {
            int count = SongMediaStore.load(this, songs.get(i)).size();
            names[i] = displayName(Uri.parse(songs.get(i))) + " · 미디어 " + count + "개";
        }
        new AlertDialog.Builder(this)
                .setTitle("곡별 이미지·MP4 연결")
                .setItems(names, (dialog, which) -> {
                    pendingSongMediaUri = songs.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle(names[which])
                            .setItems(
                                    new String[]{"이미지·MP4 추가", "연결 전체 삭제"},
                                    (menu, action) -> {
                                        if (action == 0) {
                                            songMediaPickerLauncher.launch(
                                                    new String[]{"image/*", "video/*"});
                                        } else {
                                            SongMediaStore.clear(this, pendingSongMediaUri);
                                            loadActiveSongMedia();
                                            toast("곡별 미디어 연결 삭제");
                                        }
                                    })
                            .setNegativeButton("닫기", null)
                            .show();
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showAiManagementDialog() {
        String[] labels = {
                "AI 학습 현황",
                "미확인 청취자 기본 언어",
                "내 BIGO 닉네임 설정",
                "마지막 청취자 언어 수정",
                "최근 OCR 이벤트 문구 학습",
                "대화 답변 직접 학습",
                "AI 학습 데이터 백업",
                "AI 학습 데이터 복원",
                "AI 학습 데이터 초기화"
        };
        new AlertDialog.Builder(this)
                .setTitle("습득·진화형 AI 관리")
                .setItems(labels, (dialog, which) -> {
                    switch (which) {
                        case 0: showMessage("AI 학습 현황", AdaptiveAiStore.summary(this)); break;
                        case 1: chooseDefaultLanguage(); break;
                        case 2: editHostNickname(); break;
                        case 3: correctLastViewerLanguage(); break;
                        case 4: learnRecentEventPhrase(); break;
                        case 5: learnConversationReply(); break;
                        case 6: aiBackupCreateLauncher.launch("MARU-AI-BACKUP.json"); break;
                        case 7: aiBackupOpenLauncher.launch(new String[]{"application/json", "text/*"}); break;
                        case 8: confirmClearAi(); break;
                        default: break;
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void chooseDefaultLanguage() {
        String[] labels = GreetingLanguage.supportedLabels();
        String[] codes = GreetingLanguage.supportedCodes();
        int selected = 0;
        String current = AdaptiveAiStore.defaultLanguage(this);
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(current)) selected = i;
        final int checked = selected;
        new AlertDialog.Builder(this)
                .setTitle("미확인 청취자 기본 언어")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AdaptiveAiStore.setDefaultLanguage(this, codes[which]);
                    dialog.dismiss();
                    refreshAutoGreetingStatus();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void editHostNickname() {
        EditText input = new EditText(this);
        input.setText(AdaptiveAiStore.hostNickname(this));
        input.setHint("예: 꿈에서넌");
        new AlertDialog.Builder(this)
                .setTitle("내 BIGO 닉네임")
                .setMessage("내가 쓴 댓글에 AI가 다시 답하지 않도록 사용합니다.")
                .setView(input)
                .setPositiveButton("저장", (dialog, which) ->
                        AdaptiveAiStore.setHostNickname(this, input.getText().toString()))
                .setNegativeButton("취소", null)
                .show();
    }

    private void correctLastViewerLanguage() {
        String name = AutoGreetingStore.lastNickname(this);
        if (name.isEmpty()) {
            toast("마지막 청취자 기록이 없습니다.");
            return;
        }
        String[] labels = GreetingLanguage.supportedLabels();
        String[] codes = GreetingLanguage.supportedCodes();
        new AlertDialog.Builder(this)
                .setTitle(name + " 언어 수정")
                .setItems(labels, (dialog, which) -> {
                    AdaptiveAiStore.rememberLanguage(this, name, codes[which], 10);
                    toast(name + " · " + labels[which] + " 기억 완료");
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void learnRecentEventPhrase() {
        EditText input = new EditText(this);
        input.setText(AutoGreetingStore.lastOcrText(this));
        input.setHint("반복되는 BIGO 이벤트 핵심 문구");
        new AlertDialog.Builder(this)
                .setTitle("최근 OCR 문구 학습")
                .setMessage("전체 문장보다 'sent a gift'처럼 반복되는 핵심 문구를 남기세요.")
                .setView(input)
                .setPositiveButton("다음", (dialog, which) -> {
                    String phrase = input.getText().toString().trim();
                    EventType[] types = {EventType.JOIN, EventType.LIKE, EventType.GIFT, EventType.FOLLOW};
                    String[] labels = {"입장", "좋아요", "선물", "팔로우"};
                    new AlertDialog.Builder(this)
                            .setTitle("이 문구의 종류")
                            .setItems(labels, (d, index) -> {
                                String language = GreetingLanguage.detectFromText(phrase);
                                AdaptiveAiStore.addEventRule(this, phrase, types[index], language);
                                toast("새 이벤트 문구를 학습했습니다.");
                            })
                            .show();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void learnConversationReply() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText trigger = new EditText(this);
        trigger.setHint("청취자 문구: 예) where are you from");
        org.json.JSONObject latest = AdaptiveAiStore.latestCandidate(this);
        trigger.setText(latest.optString("message", ""));
        EditText answer = new EditText(this);
        answer.setHint("AI 답변 문장");
        box.addView(trigger);
        box.addView(answer);
        new AlertDialog.Builder(this)
                .setTitle("대화 답변 학습")
                .setView(box)
                .setPositiveButton("저장", (dialog, which) -> {
                    String t = trigger.getText().toString();
                    String a = answer.getText().toString();
                    AdaptiveAiStore.addCustomReply(
                            this,
                            t,
                            a,
                            GreetingLanguage.detectFromText(a));
                    toast("대화 답변을 학습했습니다.");
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void confirmClearAi() {
        new AlertDialog.Builder(this)
                .setTitle("AI 학습 초기화")
                .setMessage("기억한 언어·문구·답변을 모두 지울까요?")
                .setPositiveButton("초기화", (dialog, which) -> {
                    AdaptiveAiStore.clear(this);
                    refreshAutoGreetingStatus();
                    toast("AI 학습 데이터를 초기화했습니다.");
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();
    }

    private void runGreetingVoiceTest() {
        showTemporaryTestOverlay(
                "곡 사이 통합 안내 로컬 테스트\n"
                        + "노래 재생 중에는 실행하지 않습니다.",
                4_000L);
        AutoGreetingService.announceIntermission(
                this,
                "별빛 아래서",
                java.util.Collections.singletonList("테스트 청취자"),
                java.util.Collections.singletonList("새 친구"),
                -1);
    }

    private void runLyricsDisplayTest() {
        if (lyricView == null) return;
        lyricView.setText(
                "가사 자막 로컬 테스트\n"
                        + "두 번째 줄 표시 확인");
        lyricView.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> {
            if (lyricView != null && localTestMode) {
                String current = playback == null
                        ? ""
                        : LyricsCore.twoLines(
                                lyrics, playback.position());
                lyricView.setText(current);
                lyricView.setVisibility(
                        current.trim().isEmpty()
                                ? View.GONE
                                : View.VISIBLE);
            }
        }, 5_000L);
    }

    private void showNextImageNow() {
        if (activeSongMedia.isEmpty()) {
            toast("등록된 곡 이미지·영상이 없습니다.");
            return;
        }
        advanceSongMedia();
        showTemporaryTestOverlay(
                "곡 미디어 전환 " + (mediaIndex + 1) + " / " + activeSongMedia.size(),
                2_000L);
    }

    private void showTemporaryTestOverlay(
            String message,
            long durationMs) {
        if (testOverlayView == null) return;

        eventOverlayQueue.clear();
        eventOverlayShowing = false;
        testOverlayView.animate().cancel();
        int generation = ++overlayGeneration;
        testOverlayView.setAlpha(1f);
        testOverlayView.setText(message);
        testOverlayView.setVisibility(View.VISIBLE);

        handler.postDelayed(() -> {
            if (testOverlayView != null
                    && generation == overlayGeneration) {
                testOverlayView.setText("");
                testOverlayView.setVisibility(View.GONE);
            }
        }, Math.max(500L, durationMs));
    }

    private void showImage(String uriText) {
        showMediaUri(uriText, false);
    }

    private void loadActiveSongMedia() {
        activeSongMedia.clear();
        String song = currentSongUri();
        if (!song.isEmpty()) activeSongMedia.addAll(SongMediaStore.load(this, song));
        if (activeSongMedia.isEmpty()) activeSongMedia.addAll(images);
        mediaIndex = 0;
    }

    private void showActiveSongMedia() {
        if (activeSongMedia.isEmpty()) return;
        mediaIndex = Math.max(0, Math.min(mediaIndex, activeSongMedia.size() - 1));
        showMediaUri(activeSongMedia.get(mediaIndex), false);
    }

    private void advanceSongMedia() {
        if (activeSongMedia.isEmpty()) return;
        mediaIndex = (mediaIndex + 1) % activeSongMedia.size();
        showMediaUri(activeSongMedia.get(mediaIndex), false);
    }

    private void showMediaUri(String uriText, boolean eventMedia) {
        if (uriText == null || uriText.trim().isEmpty()) return;
        Uri uri = Uri.parse(uriText);
        if (isVideoUri(uri)) {
            if (backgroundImage != null) backgroundImage.setVisibility(View.GONE);
            if (foregroundImage != null) foregroundImage.setVisibility(View.GONE);
            if (mediaVideo == null) return;
            mediaVideo.setVisibility(View.VISIBLE);
            try {
                mediaVideo.stopPlayback();
                mediaVideo.setVideoURI(uri);
                mediaVideo.setOnPreparedListener(player -> {
                    player.setVolume(0f, 0f);
                    player.setLooping(eventMedia);
                    mediaVideo.start();
                });
                mediaVideo.setOnCompletionListener(player -> {
                    if (!eventMedia
                            && broadcastVisible
                            && System.currentTimeMillis() >= activeEventUntil) {
                        advanceSongMedia();
                    }
                });
                mediaVideo.start();
            } catch (RuntimeException error) {
                toast("동영상 열기 실패");
            }
            return;
        }

        if (mediaVideo != null) {
            try { mediaVideo.stopPlayback(); } catch (RuntimeException ignored) {}
            mediaVideo.setVisibility(View.GONE);
        }
        if (backgroundImage != null) backgroundImage.setVisibility(View.VISIBLE);
        if (foregroundImage != null) foregroundImage.setVisibility(View.VISIBLE);
        Bitmap bitmap = decodeBitmap(uri);
        if (bitmap != null) {
            backgroundImage.setImageBitmap(bitmap);
            foregroundImage.setImageBitmap(bitmap);
        }
    }

    private boolean isVideoUri(Uri uri) {
        String type = getContentResolver().getType(uri);
        if (type != null && type.startsWith("video/")) return true;
        String value = uri.toString().toLowerCase(Locale.ROOT);
        return value.endsWith(".mp4")
                || value.endsWith(".m4v")
                || value.endsWith(".webm")
                || value.endsWith(".3gp");
    }

    private String currentSongUri() {
        if (songs.isEmpty()) return "";
        int index = playback == null ? 0 : playback.currentIndex();
        if (index < 0 || index >= songs.size()) index = 0;
        return songs.get(index);
    }

    private void handleLiveEventOverlay(Intent intent) {
        if (!broadcastVisible || testOverlayView == null) return;

        EventType type = EventType.fromStored(
                intent.getStringExtra(LiveOverlayController.EXTRA_TYPE));
        String text = intent.getStringExtra(LiveOverlayController.EXTRA_TEXT);
        long duration = intent.getLongExtra(
                LiveOverlayController.EXTRA_DURATION,
                type.overlayDurationMs);
        int priority = intent.getIntExtra(
                LiveOverlayController.EXTRA_PRIORITY,
                type.priority);

        OverlayMessage message = new OverlayMessage(
                type,
                text == null ? type.koreanLabel : text,
                Math.max(1_500L, duration),
                priority);
        enqueueEventOverlay(message);
    }

    private void enqueueEventOverlay(OverlayMessage message) {
        if (message == null) return;
        if (eventOverlayQueue.size() >= MAX_EVENT_OVERLAY_QUEUE) {
            eventOverlayQueue.pollFirst();
        }
        eventOverlayQueue.offerLast(message);
        if (!eventOverlayShowing) showNextEventOverlay();
    }

    private void showNextEventOverlay() {
        if (testOverlayView == null) {
            eventOverlayShowing = false;
            return;
        }
        OverlayMessage message = eventOverlayQueue.pollFirst();
        if (message == null) {
            eventOverlayShowing = false;
            activeEventPriority = 0;
            activeEventUntil = 0L;
            testOverlayView.animate().cancel();
            testOverlayView.setText("");
            testOverlayView.setVisibility(View.GONE);
            showActiveSongMedia();
            return;
        }

        eventOverlayShowing = true;
        activeEventPriority = message.priority;
        activeEventUntil = System.currentTimeMillis() + message.durationMs;
        int generation = ++overlayGeneration;

        testOverlayView.animate().cancel();
        testOverlayView.setAlpha(0f);
        testOverlayView.setText(message.text);
        testOverlayView.setVisibility(View.VISIBLE);
        testOverlayView.animate().alpha(1f).setDuration(160L).start();

        String media = EventVisualStore.mediaUri(this, message.type);
        if (!media.isEmpty()) showMediaUri(media, true);

        long fadeStart = Math.max(1_000L, message.durationMs - 220L);
        handler.postDelayed(() -> {
            if (generation != overlayGeneration || testOverlayView == null) return;
            testOverlayView.animate()
                    .alpha(0f)
                    .setDuration(200L)
                    .withEndAction(() -> {
                        if (generation != overlayGeneration) return;
                        testOverlayView.setText("");
                        testOverlayView.setVisibility(View.GONE);
                        testOverlayView.setAlpha(1f);
                        activeEventPriority = 0;
                        activeEventUntil = 0L;
                        eventOverlayShowing = false;
                        showActiveSongMedia();
                        showNextEventOverlay();
                    })
                    .start();
        }, fadeStart);
    }

    private GradientDrawable eventOverlayBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xD9111111);
        background.setCornerRadius(dp(BroadcastVisualProfile.EVENT_CORNER_DP));
        background.setStroke(dp(1), 0x55FFFFFF);
        return background;
    }

    private Bitmap decodeBitmap(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return input == null ? null : BitmapFactory.decodeStream(input);
        } catch (Exception ex) {
            toast("이미지 열기 실패");
            return null;
        }
    }

    private void loadLyricsForCurrentSong() {
        lyrics.clear();

        if (lyricDocs.isEmpty()) {
            hideLyricView();
            return;
        }

        int songIndex = playback == null
                ? 0
                : playback.currentIndex();
        if (songIndex < 0) songIndex = 0;

        int lyricIndex;
        if (lyricDocs.size() == songs.size()
                && songIndex < lyricDocs.size()) {
            lyricIndex = songIndex;
        } else if (songs.size() == 1
                && lyricDocs.size() == 1) {
            lyricIndex = 0;
        } else {
            hideLyricView();
            return;
        }

        try (InputStream input =
                     getContentResolver()
                             .openInputStream(
                                     Uri.parse(
                                             lyricDocs.get(
                                                     lyricIndex)))) {
            if (input != null) {
                lyrics.addAll(
                        LyricsCore.parse(input));
            }
        } catch (Exception ignored) {
            lyrics.clear();
        }

        if (lyrics.isEmpty()) {
            hideLyricView();
        }
    }

    private void hideLyricView() {
        if (lyricView == null) return;
        lyricView.setText("");
        lyricView.setVisibility(View.GONE);
    }

    private void startMusicForBroadcast() {
        if (songs.isEmpty()) {
            pendingAutoMusicStart = false;
            toast("방송할 노래가 없습니다. 노래를 추가하세요.");
            return;
        }

        pendingAutoMusicStart = true;
        if (playback == null) return;

        playback.setQueue(songs);
        playback.prepareForBroadcast();
        pendingAutoMusicStart = false;
    }

    private void confirmDeleteSong(int position) {
        if (position < 0 || position >= songs.size()) return;

        String name =
                displayName(Uri.parse(songs.get(position)));
        new AlertDialog.Builder(this)
                .setTitle("노래 삭제")
                .setMessage(name + "을(를) 삭제할까요?")
                .setPositiveButton(
                        "삭제",
                        (dialog, which) ->
                                deleteSong(position))
                .setNegativeButton("취소", null)
                .show();
    }

    private void deleteSong(int position) {
        if (position < 0 || position >= songs.size()) return;

        if (playback != null) {
            playback.stopPlayback();
        }

        songs.remove(position);
        if (lyricDocs.size() > position
                && lyricDocs.size() == songs.size() + 1) {
            lyricDocs.remove(position);
            AppStorage.saveLyrics(this, lyricDocs);
        }

        AppStorage.saveSongs(this, songs);
        refreshSongList();
        if (playback != null) {
            playback.setQueue(songs);
        }
        loadLyricsForCurrentSong();
        toast("노래를 삭제했습니다.");
    }

    private void confirmDeleteAllSongs() {
        new AlertDialog.Builder(this)
                .setTitle("노래 전체 삭제")
                .setMessage(
                        "등록된 노래를 모두 삭제할까요? "
                                + "곡 사이 TTS 설정은 삭제되지 않습니다.")
                .setPositiveButton(
                        "전체 삭제",
                        (dialog, which) -> {
                            if (playback != null) {
                                playback.stopPlayback();
                            }
                            songs.clear();
                            lyricDocs.clear();
                            AppStorage.saveSongs(this, songs);
                            AppStorage.saveLyrics(this, lyricDocs);
                            if (playback != null) {
                                playback.setQueue(songs);
                            }
                            refreshSongList();
                            hideLyricView();
                            toast("노래와 가사를 모두 삭제했습니다.");
                        })
                .setNegativeButton("취소", null)
                .show();
    }

    private void startAndBindPlayback() {
        Intent intent = new Intent(this, PlaybackService.class);
        ContextCompat.startForegroundService(this, intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }





    private void beginHostSpeechDuck() {
        if (playback != null) playback.setHostVolume(0.18f);
        handler.removeCallbacks(restoreHostSpeech);
        handler.postDelayed(restoreHostSpeech, HOST_SPEECH_AUTO_RESTORE_MS);
        toast("말하기 모드: 음악 18%");
    }

    private void endHostSpeechDuck() {
        if (playback != null) playback.setHostVolume(1f);
        handler.removeCallbacks(restoreHostSpeech);
    }

    private void showQuickReplyDialog() {
        List<QuickReplies.Entry> entries = QuickReplies.all();
        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            QuickReplies.Entry entry = entries.get(i);
            labels[i] = entry.language() + " · " + entry.text();
        }
        new AlertDialog.Builder(this)
                .setTitle("빠른답변 복사")
                .setItems(labels, (dialog, which) -> copyReplyAndOpenBigo(entries.get(which).text()))
                .setNeutralButton("직접 입력", (dialog, which) -> showCustomReplyDialog())
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showCustomReplyDialog() {
        EditText input = new EditText(this);
        input.setHint("답변을 입력하세요");
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(dp(24), dp(8), dp(24), 0);
        holder.addView(input, match());
        new AlertDialog.Builder(this)
                .setTitle("직접 답변")
                .setView(holder)
                .setPositiveButton("복사 후 BIGO 열기",
                        (dialog, which) -> copyReplyAndOpenBigo(input.getText().toString()))
                .setNegativeButton("취소", null)
                .show();
    }

    private void copyReplyAndOpenBigo(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("MARU 답변", text));
        openBigoChat();
    }

    private void openBigoChat() {
        if (localTestMode) {
            toast("로컬 테스트에서는 BIGO LIVE를 열지 않습니다.");
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage("sg.bigo.live");
        if (launch == null) {
            toast("BIGO LIVE를 찾지 못했습니다.");
            return;
        }
        startActivity(launch);
    }


    private void allowAudioCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AudioManager manager = getSystemService(AudioManager.class);
            if (manager != null) {
                manager.setAllowedCapturePolicy(android.media.AudioAttributes.ALLOW_CAPTURE_BY_ALL);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void persist(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}
    }

    private void writeText(Uri uri, String value) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("output null");
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.flush();
            toast("AI 학습 백업 저장 완료");
        } catch (Exception error) {
            toast("백업 저장 실패");
        }
    }

    private String readText(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return "";
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8_192];
            int read;
            while ((read = input.read(chunk)) >= 0) {
                if (read > 0) buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            toast("백업 파일 읽기 실패");
            return "";
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return uri.getLastPathSegment() == null ? "노래" : uri.getLastPathSegment();
    }

    private String currentTitle() {
        if (playback == null || playback.currentIndex() < 0 ||
                playback.currentIndex() >= songs.size()) {
            return songs.isEmpty() ? "MARU MUSIC LIVE" : displayName(Uri.parse(songs.get(0)));
        }
        return displayName(Uri.parse(songs.get(playback.currentIndex())));
    }

    private void refreshSongList() {
        if (songList == null) return;
        List<String> names = new ArrayList<>();
        for (String song : songs) names.add(displayName(Uri.parse(song)));
        songList.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, names));
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void showSystemBars() {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .show(WindowInsetsCompat.Type.systemBars());
    }

    private TextView text(String value, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(this, R.color.maru_text));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        p.topMargin = dp(8);
        button.setLayoutParams(p);
        return button;
    }

    private Button smallButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(BroadcastVisualProfile.CONTROL_SP);
        button.setAllCaps(false);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(54), 1f);
        p.leftMargin = dp(1);
        p.rightMargin = dp(1);
        button.setLayoutParams(p);
        return button;
    }

    private CheckBox checkBox(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(ContextCompat.getColor(this, R.color.maru_text));
        box.setChecked(checked);
        return box;
    }

    private View space(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatTime(int ms) {
        int total = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public void onTrackChanged(int index) {
        runOnUiThread(() -> {
            if (titleView != null) {
                titleView.setText(
                        localTestMode
                                ? "로컬 테스트 · BIGO 미실행"
                                : currentTitle());
            }
            loadLyricsForCurrentSong();
            loadActiveSongMedia();
            if (broadcastVisible
                    && System.currentTimeMillis() >= activeEventUntil) {
                showActiveSongMedia();
            }
        });
    }

    @Override public void onStateChanged(boolean playing) {
        runOnUiThread(() -> {
            if (playButton != null) playButton.setText(playing ? "일시정지" : "재생");
        });
    }

    @Override public void onError(String message) {
        runOnUiThread(() -> toast(message));
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAutoGreetingStatus();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(liveEventReceiver); } catch (IllegalArgumentException ignored) {}
        if (bound) {
            if (playback != null) playback.removeListener(this);
            unbindService(serviceConnection);
            bound = false;
        }
        super.onDestroy();
    }
    private static final class OverlayMessage {
        final EventType type;
        final String text;
        final long durationMs;
        final int priority;

        OverlayMessage(EventType type, String text, long durationMs, int priority) {
            this.type = type == null ? EventType.UNKNOWN : type;
            this.text = text == null ? "" : text.trim();
            this.durationMs = durationMs;
            this.priority = priority;
        }
    }

}
