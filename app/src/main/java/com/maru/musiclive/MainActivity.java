package com.maru.musiclive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
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
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
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
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends ComponentActivity implements PlaybackService.Listener {
    private static final long CONTROLS_HIDE_DELAY_MS = 5_000L;
    private static final long IMAGE_CHANGE_MS = 12_000L;
    private static final long HOST_SPEECH_AUTO_RESTORE_MS = 45_000L;
    private static final int MAX_EVENT_OVERLAY_QUEUE = 12;
    private static final long ONE_CLICK_GUIDE_MS = 8_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService musicScanExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService appUpdateExecutor = Executors.newSingleThreadExecutor();
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
    private boolean homePlayerMode;
    private boolean miniPlayerMode;
    private boolean webStudioMode;
    private WebView webStudioView;
    private ValueCallback<Uri[]> webStudioFileCallback;
    private ActivityResultLauncher<Intent> webStudioFileLauncher;
    private TextView miniSongTitle;
    private Button miniPlayButton;
    private SingleMediaPreviewView miniMediaPreview;
    private LinearLayout miniMediaActions;
    private MediaPlayer ownVoicePreview;
    private TextView ownVoiceStatus;
    private boolean pendingAutoMusicStart;
    private boolean oneClickStarting;
    private boolean eventOverlayShowing;
    private boolean deviceMusicScanRunning;
    private boolean initialDeviceMusicScanStarted;
    private int overlayGeneration;
    private int imageIndex;
    private int mediaIndex;
    private int activeEventPriority;
    private long activeEventUntil;
    private String pendingSongMediaUri = "";
    private String pendingStoryVisualId = "";
    private String activeStoryVisualId = "";
    private String miniShownUri = "";
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
    private ActivityResultLauncher<String[]> ownVoicePickerLauncher;
    private ActivityResultLauncher<String> ownVoiceExportLauncher;
    private ActivityResultLauncher<String[]> imagePickerLauncher;
    private ActivityResultLauncher<String[]> miniMediaPickerLauncher;
    private ActivityResultLauncher<String[]> defaultVideoPickerLauncher;
    private ActivityResultLauncher<String[]> storyVisualPickerLauncher;
    private ActivityResultLauncher<String[]> lyricsPickerLauncher;
    private ActivityResultLauncher<String[]> greetingAudioPickerLauncher;
    private ActivityResultLauncher<String[]> songMediaPickerLauncher;
    private ActivityResultLauncher<String[]> eventVisualPickerLauncher;
    private ActivityResultLauncher<String> aiBackupCreateLauncher;
    private ActivityResultLauncher<String[]> aiBackupOpenLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<String> musicPermissionLauncher;
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
    private final Runnable hideMiniMediaActions = () -> {
        if (miniMediaActions != null) miniMediaActions.setVisibility(View.GONE);
    };

    private final BroadcastReceiver liveEventReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (AutoGreetingService.ACTION_STORY_VISUAL.equals(action)) {
                activeStoryVisualId = intent.getStringExtra(
                        AutoGreetingService.EXTRA_STORY_VISUAL_ID);
                if (activeStoryVisualId == null) activeStoryVisualId = "";
                if (miniPlayerMode) showSavedMiniMedia();
                if (broadcastVisible) {
                    String storyVisual = StoryMediaStore.load(MainActivity.this,
                            activeStoryVisualId);
                    if (!activeStoryVisualId.isEmpty()
                            && isValidLocalVisual(storyVisual)) {
                        showMediaUri(storyVisual, true);
                    } else if (!activeStoryVisualId.isEmpty()) {
                        showMediaUri(resourceUri(R.raw.default_broadcast), true);
                    } else {
                        showActiveSongMedia();
                    }
                }
                return;
            }
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
            syncPlayButtonState();
            String runningMode = AutoGreetingStore.runningMode(MainActivity.this);
            if (isBigoAudioCoexistenceMode(runningMode)) {
                playback.setBigoCoexistenceMode(true);
            }
            if (pendingAutoMusicStart
                    || (localTestMode && broadcastVisible)
                    || ScreenOcrGreetingService.MODE_AUTO_GREETING.equals(runningMode)
                    || "bigo_native_toolbar".equals(runningMode)) {
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
        // Allow Samsung system pop-up/freeform mode; the full image player still
        // locks its own broadcast layout when explicitly opened.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        allowAudioCapture();
        // V3.1.1: V3.1.0에서 강제로 꺼진 대화형 AI를 안전한 화면 답변 모드로 1회 복원한다.
        AdaptiveAiStore.migrateSafeConversationV311(this);
        registerLaunchers();
        IntentFilter liveFilter = new IntentFilter();
        liveFilter.addAction(LiveOverlayController.ACTION_SHOW);
        liveFilter.addAction(AutoGreetingService.ACTION_BROADCAST_CLOSED);
        liveFilter.addAction(AutoGreetingService.ACTION_STORY_VISUAL);
        ContextCompat.registerReceiver(
                this,
                liveEventReceiver,
                liveFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        loadStoredData();
        ensureDefaultMedia();
        buildSetupScreen();
        startAndBindPlayback();
        showHomePlayer();
        requestNotificationPermission();
        // Avoid expensive SHA-256 scanning of every media file on startup.
        // Existing saved songs remain; user may scan explicitly when needed.
        importSharedOriginals(getIntent());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (webStudioMode) {
                    closeEmbeddedWebStudio();
                } else if (miniPlayerMode) {
                    openSetupFromMini();
                } else if (homePlayerMode) {
                    showHomePlayerMenu();
                } else if (broadcastVisible) {
                    showBroadcastEndMenu();
                } else {
                    showHomePlayer();
                }
            }
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        importSharedOriginals(intent);
    }

    /** Accept audio files the user explicitly shares from Suno or a file manager. */
    private void importSharedOriginals(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action)
                && !Intent.ACTION_SEND_MULTIPLE.equals(action)) return;

        List<Uri> incoming = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri audio = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (audio != null) incoming.add(audio);
        } else {
            ArrayList<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (many != null) incoming.addAll(many);
        }
        if (incoming.isEmpty() && intent.getClipData() != null) {
            ClipData clip = intent.getClipData();
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) incoming.add(uri);
            }
        }
        if (incoming.isEmpty()) {
            toast("Suno 공유 링크는 오디오 파일이 아닙니다. MP3/WAV를 내려받아 공유해 주세요.");
            return;
        }
        final List<Uri> accepted = incoming;
        musicScanExecutor.execute(() -> {
            List<String> imported = new ArrayList<>();
            String firstError = "";
            for (Uri uri : accepted) {
                try {
                    String item = SharedAudioImport.copyOriginal(
                            getApplicationContext(), uri);
                    if (!imported.contains(item)) imported.add(item);
                } catch (Exception error) {
                    if (firstError.isEmpty()) firstError = error.getMessage();
                }
            }
            final String errorMessage = firstError;
            handler.post(() -> {
                if (isDestroyed()) return;
                int added = 0;
                for (String item : imported) {
                    if (!songs.contains(item)) {
                        songs.add(item);
                        added++;
                    }
                }
                if (added > 0) {
                    AppStorage.saveSongs(this, songs);
                    refreshSongList();
                    if (playback != null) playback.setQueue(songs);
                }
                if (imported.isEmpty()) {
                    toast(errorMessage == null || errorMessage.isEmpty()
                            ? "공유 음악을 가져오지 못했습니다."
                            : "가져오기 실패: " + errorMessage);
                } else {
                    toast(added > 0
                            ? "Suno 원본 " + added + "곡 저장 · 무변환 재생 준비 완료"
                            : "이미 저장된 원본 음악입니다.");
                }
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAutoGreetingStatus();
        syncPlayButtonState();
        handler.postDelayed(this::syncPlayButtonState, 250L);
        checkAppUpdate(false);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) syncPlayButtonState();
    }

    @Override public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        handler.postDelayed(this::syncPlayButtonState, 120L);
    }

    private void registerLaunchers() {
        webStudioFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    ValueCallback<Uri[]> callback = webStudioFileCallback;
                    webStudioFileCallback = null;
                    if (callback != null) {
                        Uri[] selected = WebChromeClient.FileChooserParams.parseResult(
                                result.getResultCode(), result.getData());
                        callback.onReceiveValue(selected);
                    }
                });
        ownVoicePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri == null) return;
                    if (ownVoiceStatus != null) ownVoiceStatus.setText("내 녹음 음성 보정 중...");
                    musicScanExecutor.execute(() -> {
                        try {
                            final File output = MyVoiceFileProcessor.enhance(this, uri);
                            runOnUiThread(() -> {
                                if (ownVoiceStatus != null)
                                    ownVoiceStatus.setText("보정 성공 · " + output.length() / 1024
                                            + "KB · 기기 내부에 비공개 저장");
                                toast("내 음성 보정 완료 · 미리듣기 또는 WAV 저장 가능");
                            });
                        } catch (IOException e) {
                            runOnUiThread(() -> {
                                if (ownVoiceStatus != null)
                                    ownVoiceStatus.setText("보정 실패 · " + e.getMessage());
                                toast("내 녹음 파일을 처리하지 못했습니다.");
                            });
                        }
                    });
                });
        ownVoiceExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("audio/wav"), uri -> {
                    if (uri == null) return;
                    File wav = MyVoiceFileProcessor.output(this);
                    if (!wav.isFile()) { toast("먼저 내 음성을 보정하세요."); return; }
                    musicScanExecutor.execute(() -> {
                        try (InputStream from = new java.io.FileInputStream(wav);
                             OutputStream to = getContentResolver().openOutputStream(uri, "w")) {
                            if (to == null) throw new IOException("저장 대상이 열리지 않습니다.");
                            byte[] buffer = new byte[16_384];
                            int n;
                            while ((n = from.read(buffer)) != -1) to.write(buffer, 0, n);
                            runOnUiThread(() -> toast("형의 보정 음성 WAV 저장 완료"));
                        } catch (IOException e) {
                            runOnUiThread(() -> toast("WAV 내보내기에 실패했습니다."));
                        }
                    });
                });
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

        miniMediaPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    String type = "";
                    try { type = getContentResolver().getType(uri); }
                    catch (RuntimeException ignored) {}
                    if (!SingleMediaPreviewPolicy.accept(type, uri.toString())) {
                        toast("사진 또는 영상 파일만 선택할 수 있습니다.");
                        return;
                    }
                    persist(uri);
                    AppStorage.setPopupMediaUri(this, uri.toString());
                    if (miniPlayerMode && miniMediaPreview != null) {
                        miniShownUri = uri.toString();
                        miniMediaPreview.show(uri, type);
                        showMiniMediaActions();
                    }
                });

        defaultVideoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    String mime = "";
                    try { mime = getContentResolver().getType(uri); }
                    catch (RuntimeException ignored) {}
                    if (!SingleMediaPreviewPolicy.isVideo(mime, uri.toString())) {
                        toast("기본 배경은 영상만 선택해 주세요.");
                        return;
                    }
                    persist(uri);
                    AppStorage.setDefaultBroadcastVideo(this, uri.toString());
                    if (miniPlayerMode) showSavedMiniMedia();
                    if (broadcastVisible && activeStoryVisualId.isEmpty()
                            && SongMediaStore.load(this, currentSongUri()).isEmpty())
                        showActiveSongMedia();
                    toast("기본 영상 등록 완료");
                });
        storyVisualPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null || pendingStoryVisualId.isEmpty()) return;
                    String mime = "";
                    try { mime = getContentResolver().getType(uri); }
                    catch (RuntimeException ignored) {}
                    if (!SingleMediaPreviewPolicy.accept(mime, uri.toString())) {
                        toast("사연 화면에는 사진 또는 영상만 등록할 수 있습니다.");
                        return;
                    }
                    persist(uri);
                    StoryMediaStore.save(this, pendingStoryVisualId, uri.toString());
                    if (miniPlayerMode) showSavedMiniMedia();
                    if (broadcastVisible && pendingStoryVisualId.equals(activeStoryVisualId))
                        showMediaUri(uri.toString(), true);
                    toast("선택한 사연 전용 화면 저장");
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
                    // Popup is intentionally one visual per song.
                    if (!values.isEmpty()) {
                        SongMediaStore.save(this, pendingSongMediaUri,
                                java.util.Collections.singletonList(values.get(0)));
                        toast("곡 전용 사진·영상 1개 연결");
                    }
                    if (pendingSongMediaUri.equals(currentSongUri())) {
                        loadActiveSongMedia();
                        if (miniPlayerMode) showSavedMiniMedia();
                        if (broadcastVisible && activeStoryVisualId.isEmpty())
                            showActiveSongMedia();
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

        musicPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        scanDeviceMusic(true);
                    } else {
                        toast("음악 권한을 허용해야 휴대폰 전체 노래를 자동으로 찾을 수 있습니다.");
                    }
                });

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

        TextView heading = text("MARU MUSIC LIVE", 28, true);
        heading.setTextColor(ContextCompat.getColor(this, R.color.maru_accent));
        column.addView(heading);

        TextView version = text(
                "V3.2.24 · Daily English Weather & News Radio",
                15,
                true);
        version.setTextColor(ContextCompat.getColor(this, R.color.maru_subtext));
        column.addView(version);

        // Single app hub: select MARU local songs or Suno as the music source.
        // Suno mode runs opt-in TTS from a foreground notification; MARU does not
        // capture or manipulate third-party audio or BIGO's broadcast controls.
        TextView hubTitle = text("통합 방송 · 음악과 아환 토크", 19, true);
        hubTitle.setTextColor(ContextCompat.getColor(this, R.color.maru_accent));
        column.addView(hubTitle);
        column.addView(button("🖥 왼쪽 노래목록 · 곡별 사진/영상 스튜디오",
                v -> openEmbeddedWebStudio()));
        column.addView(button("① 내 MP3 + 짧은 영어 토크", v -> {
            // Prevent two independent MARU TTS engines from speaking at once.
            stopSunoCompanion();
            // The existing native PlaybackService introduces imported songs.
            AppStorage.setRadioConversationEnabled(this, true);
            AppStorage.setEnglishRadioEnabled(this, true);
            AppStorage.setLongGuestTalkEnabled(this, false);
            AppStorage.setSongTitleTts(this, true);
            if (songs.isEmpty()) {
                toast("먼저 '노래 직접 추가'에서 원곡 MP3를 선택하세요.");
            } else {
                startLocalRadioPreview();
            }
        }));
        column.addView(button("② Suno 음악 + 아환 자동 토크 (3분 후 시작)", v ->
                startSunoCompanion(true)));
        column.addView(button("③ Suno 음악 + 아환 수동 토크", v ->
                startSunoCompanion(false)));
        column.addView(button("녹음한 내 목소리 보정하기 (MP3/M4A/WAV)", v ->
                new AlertDialog.Builder(this)
                    .setTitle("내 목소리 음질 보정")
                    .setMessage("내가 직접 녹음했거나 사용 권한이 있는 음성만 선택하세요. "
                        + "90초 이하의 짧은 녹음 파일을 기기 안에서 보정하고 원본은 그대로 둡니다. "
                        + "다른 방송인 목소리는 사용하지 마세요.")
                    .setPositiveButton("내 녹음 파일 선택", (d,w) ->
                        ownVoicePickerLauncher.launch(new String[]{"audio/*"}))
                    .setNegativeButton("취소", null).show()));
        ownVoiceStatus = text(MyVoiceFileProcessor.output(this).isFile()
                ? "내 음성 보정본 준비됨 · 비공개" : "내 음성 보정본 없음", 12, false);
        column.addView(ownVoiceStatus);
        column.addView(button("형의 보정 목소리 미리듣기", v -> previewMyVoice()));
        column.addView(button("형의 보정 WAV 휴대폰에 저장", v ->
                ownVoiceExportLauncher.launch("MARU-형-보정목소리.wav")));
        column.addView(button("Suno 음악 중 형의 보정 멘트 재생", v -> playMyVoiceOverSuno()));
        column.addView(button("아환 짧은 토크 지금 말하기", v ->
                speakSunoCompanionNow()));
        column.addView(button("Suno 아환 토크 완전 종료", v -> stopSunoCompanion()));
        column.addView(text(
                "Suno 노래를 다운로드하지 않아도 아환 멘트를 재생할 수 있습니다. "
                + "알림창의 '짧은 토크'로 직접 멘트할 수 있습니다. 자동 모드는 "
                + "Suno 곡 변경을 감지하지 않고 약 6분마다 토크합니다. "
                + "휴대폰에 따라 Suno 음악이 잠시 작아지거나 멈출 수 있습니다. "
                + "BIGO 청취자 실제 송출은 보장되지 않습니다.",
                12, false));

        TextView guide = text(
                "하나의 MARU 화면에서 원곡 라디오와 Suno + 아환 토크를 선택하세요. "
                + "Suno 곡 재생은 Suno 앱에서 직접 시작합니다. "
                + "BIGO 청취자 송출은 별도 검증이 필요합니다.",
                12, false);
        column.addView(button("BIGO 이용·개인정보 안내", v ->
                new AlertDialog.Builder(this)
                        .setTitle("BIGO 방송 조작은 사용자가 직접")
                        .setMessage("오디오 LIVE를 누르면 MARU 원곡을 재생한 뒤 BIGO 앱을 엽니다. "
                                + "개인정보 보호를 위해 다른 앱 화면 자동 조작과 접근성 권한 요청 기능은 제거했습니다. "
                                + "BIGO 화면에서 방송 종류를 선택하고 실제 방송 시작 버튼은 직접 누릅니다. "
                                + "음성은 휴대폰 TTS를 사용하며 성별은 고정하지 않고 곡 사이 또는 Suno 동반 토크에 사용합니다. "
                                + "BIGO 자체 입장·팔로우·선물 인사를 사용합니다. 댓글 자동 입력 대신 직접 입력하거나 복사한 답변을 붙여넣으세요. "
                                + "MARU나 Suno의 음악·TTS가 BIGO 청취자에게 송출된다고 보장하지 않습니다.")
                        .setPositiveButton("확인", null).show()));
        guide.setBackgroundColor(0x6651246B);
        column.addView(guide);
        column.addView(text(
                "This English edition installs separately from older MARU MUSIC LIVE and MARU RADIO STATION apps. "
                        + "Do not uninstall your existing apps. "
                        + "Their songs and private settings remain in those apps. "
                        + "Android does not automatically transfer private data between separate apps. "
                        + "Re-select your own MP3/WAV files from Downloads in this app.",
                13, true));
        column.addView(button("Check for app updates", v -> checkAppUpdate(true)));
        column.addView(text(
                "하루 한 번 새 버전을 자동 확인합니다. 업데이트가 나오면 직접 다운로드를 선택하세요. "
                        + "설치에는 Android의 사용자 승인이 필요합니다. 기존 노래와 설정을 보호하려면 앱을 삭제하지 마세요.",
                13, false));


        Button openPlayer = button(
                "전체화면 이미지 플레이어 열기",
                v -> showHomePlayer());
        openPlayer.setTextSize(20f);
        column.addView(openPlayer);
        column.addView(button("사진·영상 꽉찬 팝업 · BIGO와 함께 보기", v -> showMiniController()));

        Button regularLive = button(
                "BIGO 오디오 LIVE 시작",
                v -> startCommunityLive("오디오 LIVE"));
        regularLive.setTextSize(21f);
        regularLive.setTextColor(Color.WHITE);
        GradientDrawable regularBg = new GradientDrawable();
        regularBg.setCornerRadius(dp(16));
        regularBg.setColor(0xFFCC176A);
        regularLive.setBackground(regularBg);
        LinearLayout.LayoutParams regularParams =
                (LinearLayout.LayoutParams) regularLive.getLayoutParams();
        regularParams.height = dp(70);
        regularParams.topMargin = dp(18);
        regularLive.setLayoutParams(regularParams);
        column.addView(regularLive);


        Button dailyRadioWeb = button(
                "▶ Daily Weather & News Radio · Start English Show",
                v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://skytins3-png.github.io/maru-ai-song-studio/music-share/radio.html")));
                    } catch (RuntimeException error) {
                        toast("Open the MARU RADIO webpage in a browser.");
                    }
                });
        dailyRadioWeb.setTextSize(19f);
        column.addView(dailyRadioWeb);
        column.addView(text(
                "English weather and verified news updates come from the public MARU web radio. "
                        + "Select your weather city and tap Start radio once in your browser. "
                        + "This is a browser-based online show; the phone may stop speech on screen lock. "
                        + "It is not direct BIGO audio transmission.",
                13, false));
        column.addView(button(
                "English Radio Show · Ahwan & Guest (local preview)",
                v -> startLocalRadioPreview()));
        column.addView(button("Suno playlist · open & import MP3 files",
                v -> showSunoPlaylistGuide()));
        column.addView(button("MARU 방송 운영 센터 · 곡 등록 / 사연 예약 / 미디어 검토",
                v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://skytins3-png.github.io/maru-ai-song-studio/music-share/broadcast-center.html")));
                    } catch (RuntimeException error) {
                        toast("MARU 방송 운영 센터를 열 수 없습니다.");
                    }
                }));
        column.addView(button("MUSIC SHARE · Upload / Download / Stories",
                v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://skytins3-png.github.io/maru-ai-song-studio/music-share/")));
                    } catch (RuntimeException error) {
                        toast("Unable to open the music platform.");
                    }
                }));
        column.addView(button("Listener stories · approve before reading",
                v -> showAudienceStoryInput()));
        column.addView(button("Song requests · play from imported tracks",
                v -> showSongRequestInput()));
        column.addView(button("Producer notes · approved topics",
                v -> showApprovedLearningInput()));
        column.addView(text(
                "초대석 대화는 휴대폰 안에서 만든 가상 AI 진행 대본입니다. "
                + "사연·신청곡은 운영자가 직접 접수하고 승인한 것만 사용합니다. "
                + "외부 사이트는 운영자가 직접 열고 사실과 저작권을 검토한 "
                + "요약 메모만 저장합니다. BIGO 채팅 자동 수집이나 AI 웹 자율학습은 제공하지 않습니다.",
                13, false));
        column.addView(button(
                "BIGO 앱 직접 열기",
                v -> openBigoChat()));

        autoGreetingStatusView = text("방송 상태 확인 중", 14, false);
        autoGreetingStatusView.setBackgroundColor(0x553D7A46);
        column.addView(autoGreetingStatusView);

        column.addView(space(12));
        column.addView(button(
                "휴대폰 전체 노래 자동 불러오기",
                v -> scanDeviceMusic(true)));
        column.addView(button("노래 직접 추가 (Suno MP3/WAV 포함)", v ->
                audioPickerLauncher.launch(new String[]{"audio/*"})));
        column.addView(button("Suno → BIGO 원곡 방송 안내", v ->
                showSunoBigoGuide()));
        column.addView(button("공통 배경 이미지 추가", v ->
                imagePickerLauncher.launch(new String[]{"image/*"})));
        column.addView(button("가사 추가 (LRC/TXT)", v ->
                lyricsPickerLauncher.launch(
                        new String[]{"text/*", "application/octet-stream"})));
        column.addView(button("선택 곡 이미지·MP4 연결", v ->
                showSongMediaPickerDialog()));
        column.addView(button("사연별 사진·영상 하나씩 연결", v ->
                showStoryMediaPickerDialog()));
        column.addView(button("자료 없을 때 재생할 기본 영상 선택", v ->
                defaultVideoPickerLauncher.launch(new String[]{"video/*"})));
        column.addView(button("노래 전체 삭제", v -> confirmDeleteAllSongs()));

        CheckBox repeat = checkBox("전체 반복", AppStorage.repeatAll(this));
        repeat.setOnCheckedChangeListener((buttonView, checked) -> {
            AppStorage.setRepeatAll(this, checked);
            if (playback != null) playback.setRepeatAll(checked);
        });
        column.addView(repeat);

        CheckBox random = checkBox(
                "랜덤 재생 · 같은 곡 20분 중복 방지",
                AppStorage.random(this));
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

        CheckBox radioConversation = checkBox(
                "Guest radio show between imported songs (English by default)",
                AppStorage.radioConversationEnabled(this));
        radioConversation.setOnCheckedChangeListener((buttonView, checked) -> {
            AppStorage.setRadioConversationEnabled(this, checked);
            refreshAutoGreetingStatus();
        });
        column.addView(radioConversation);
        CheckBox dailyRadio = checkBox(
                "Fresh conversation topics by day (no reinstall needed)",
                AppStorage.dailyRadioEnabled(this));
        dailyRadio.setOnCheckedChangeListener((buttonView, checked) -> {
            AppStorage.setDailyRadioEnabled(this, checked);
            refreshAutoGreetingStatus();
        });
        column.addView(dailyRadio);
        CheckBox englishShow = checkBox(
                "English conversation · four quick replies, then music",
                AppStorage.englishRadioEnabled(this));
        englishShow.setOnCheckedChangeListener((v, checked) ->
                AppStorage.setEnglishRadioEnabled(this, checked));
        column.addView(englishShow);
        CheckBox longGuestTalk = checkBox(
                "Long Korean guest interview (English always 4 short replies)",
                AppStorage.longGuestTalkEnabled(this));
        longGuestTalk.setOnCheckedChangeListener((v, checked) ->
                AppStorage.setLongGuestTalkEnabled(this, checked));
        column.addView(longGuestTalk);
        column.addView(text(
                "32명의 가상 AI 가수가 순환 출연합니다. 날짜별 오리지널 대화가 자동 변경됩니다. "
                        + "대본은 매일 APK 재설치 없이 바뀌며 원곡은 백그라운드 재생합니다. "
                        + "실제 가수별 노래 음원과 개별 보컬 모델은 별도로 제작·검증해야 하며 BIGO 자동 송출은 아닙니다.",
                13, false));

        column.addView(button("AI 가수 32명 · 고유 음색·창법 제작표", v ->
                showSunoArtistRoster()));
        column.addView(button("휴대폰 TTS 음성 설정", v -> openTtsSettings()));
        column.addView(button("댓글 인식 · 화면 답변", v ->
                requestScreenCapture(ScreenOcrGreetingService.MODE_AUTO_GREETING)));
        column.addView(button("입장 감사 화면", v -> showEventVisualSettings()));
        column.addView(button("내 방송 닉네임", v -> editHostNickname()));
        column.addView(button("완전 종료", v -> stopAllBroadcastNow()));

        TextView songGuide = text(
                "노래 터치: 재생 · 길게: AI 가수 배정/삭제",
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
            showSongManageActions(position);
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


    /**
     * Same-app access to the web Studio. No JavaScript interface or native-file
     * bridge: the web song list remains browser-origin storage, separate from
     * MARU's native library. No BIGO broadcast automation is introduced.
     */
    private static final String TRUSTED_STUDIO_URL = TrustedWebStudio.URL;

    private boolean isTrustedStudioUrl(Uri url) {
        return url != null && TrustedWebStudio.mayNavigateInside(url.toString());
    }

    private void openEmbeddedWebStudio() {
        if (webStudioMode) return;
        // Avoid overlapping native music/Suno voice with web audio.
        if (playback != null) playback.pause();
        stopSunoCompanion();
        if (broadcastVisible) leavePlayerScreen(false);
        homePlayerMode = false;
        miniPlayerMode = false;
        webStudioMode = true;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        showSystemBars();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(ContextCompat.getColor(this, R.color.maru_bg));
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(smallButton("← MARU", v -> closeEmbeddedWebStudio()));
        header.addView(smallButton("브라우저로 열기", v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(TRUSTED_STUDIO_URL))); }
            catch (RuntimeException e) { toast("브라우저를 열 수 없습니다."); }
        }));
        layout.addView(header);
        TextView help = text("왼쪽 고정 목록 · 원곡은 이 화면에서 별도로 추가", 11, false);
        layout.addView(help);

        WebView web = new WebView(this);
        webStudioView = web;
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); // only trusted HTTPS MARU site
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // selected system file picker URI
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri target = request.getUrl();
                if (isTrustedStudioUrl(target)) return false;
                if (target != null && ("https".equalsIgnoreCase(target.getScheme())
                        || "http".equalsIgnoreCase(target.getScheme()))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, target)); }
                    catch (RuntimeException e) { toast("외부 링크를 열 수 없습니다."); }
                } else {
                    toast("허용되지 않는 스튜디오 연결 주소입니다.");
                }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!isTrustedStudioUrl(Uri.parse(url))) return;
                // Open the broadcast panel directly rather than requiring the
                // user to scroll past the large music-composer landing page.
                view.evaluateJavascript(
                        "(function(){"
                        + "if(typeof coreOpenPanel==='function')"
                        + "coreOpenPanel('audioAnalyzer','broadcast');"
                        + "var p=document.getElementById('broadcastPlayerCard');"
                        + "if(p)p.classList.remove('collapsed');"
                        + "setTimeout(function(){var e=document.getElementById("
                        + "'maruBroadcastWorkspace');if(e)e.scrollIntoView({block:'start'});},350);"
                        + "})();", null);
            }
            @Override public void onReceivedError(
                    WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) help.setText(
                        "스튜디오 연결 실패 · 인터넷 연결을 확인하세요.");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (webStudioFileCallback != null) {
                    webStudioFileCallback.onReceiveValue(null);
                    webStudioFileCallback = null;
                }
                webStudioFileCallback = callback;
                try {
                    Intent picker = params.createIntent();
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    webStudioFileLauncher.launch(picker);
                    return true;
                } catch (RuntimeException e) {
                    webStudioFileCallback = null;
                    callback.onReceiveValue(null);
                    toast("노래·사진·영상 파일 선택을 열 수 없습니다.");
                    return true;
                }
            }
        });
        layout.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(layout);
        web.loadUrl(TRUSTED_STUDIO_URL);
    }

    private void closeEmbeddedWebStudio() {
        webStudioMode = false;
        if (webStudioFileCallback != null) {
            webStudioFileCallback.onReceiveValue(null);
            webStudioFileCallback = null;
        }
        if (webStudioView != null) {
            WebView web = webStudioView;
            webStudioView = null;
            web.stopLoading();
            web.loadUrl("about:blank");
            web.destroy();
        }
        homePlayerMode = false;
        miniPlayerMode = false;
        showSystemBars();
        setContentView(appRoot);
    }

    /** Official Suno playlist links are for browsing; media import uses Android's
     * multi-select document picker. No scraping of private Suno account audio. */
    private void showSunoPlaylistGuide() {
        EditText input = new EditText(this);
        input.setHint("https://suno.com/playlist/...");
        input.setSingleLine(true);
        input.setText(AppStorage.sunoPlaylistUrl(this));
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(dp(20), dp(5), dp(20), dp(5));
        holder.addView(input, match());
        new AlertDialog.Builder(this)
                .setTitle("Suno playlist · English radio")
                .setMessage("Create a playlist in Suno and paste its share link here. "
                        + "The app can open the playlist, but cannot silently download "
                        + "private Suno songs. Download your own licensed MP3 tracks in "
                        + "Suno first, then choose multiple MP3/WAV files in one action.")
                .setView(holder)
                .setPositiveButton("Open Suno playlist", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.matches("https://(www\\.)?suno\\.com/playlist/[A-Za-z0-9-]+/?")) {
                        toast("Paste a Suno playlist URL from suno.com/playlist/...");
                        return;
                    }
                    AppStorage.setSunoPlaylistUrl(this, url);
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (RuntimeException ignored) {
                        toast("Unable to open Suno in the browser.");
                    }
                })
                .setNeutralButton("Import MP3/WAV files", (dialog, which) ->
                        audioPickerLauncher.launch(new String[]{"audio/*"}))
                .setNegativeButton("Cancel", null).show();
    }

    /** Stories are submitted manually, not taken from the BIGO private chat. */
    private void showAudienceStoryInput() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(8));
        EditText nickname = new EditText(this);
        nickname.setHint("Listener name (optional)");
        content.addView(nickname);
        EditText body = new EditText(this);
        body.setHint("Approved listener story, preferably in English (max 350 characters)");
        body.setMinLines(4);
        content.addView(body);
        CheckBox consent = checkBox(
                "I confirmed the writer consents to broadcasting this story", false);
        content.addView(consent);
        new AlertDialog.Builder(this)
                .setTitle("Listener stories · waiting "
                        + RadioAudienceStore.pendingStoryCount(this) + "건")
                .setView(content)
                .setPositiveButton("Save for reading", (dialog, which) -> {
                    if (!consent.isChecked()) {
                        toast("Confirm the story owner’s permission first.");
                        return;
                    }
                    if (RadioAudienceStore.submitApprovedStory(
                            this, nickname.getText().toString(), body.getText().toString())) {
                        toast("Story saved · read at the next guest talk.");
                    } else {
                        toast("Please enter the story.");
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showSongRequestInput() {
        if (songs.isEmpty()) {
            toast("먼저 방송할 MP3/WAV 원곡을 추가하세요.");
            return;
        }
        String[] titles = new String[songs.size()];
        for (int i = 0; i < songs.size(); i++) {
            titles[i] = displayName(Uri.parse(songs.get(i)));
        }
        new AlertDialog.Builder(this)
                .setTitle("신청곡 접수 · 대기 "
                        + RadioAudienceStore.pendingRequestCount(this) + "곡")
                .setItems(titles, (dialog, index) -> {
                    if (RadioAudienceStore.requestSong(this, songs.get(index)))
                        toast("신청곡 접수: " + titles[index]
                                + " · 현재 곡이 끝나면 우선 편성");
                })
                .setNegativeButton("닫기", null).show();
    }

    /** Web pages are opened by an explicit user action, never crawled. */
    private void showApprovedLearningInput() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(8));
        EditText url = new EditText(this);
        url.setHint("참고 사이트 https://주소");
        url.setSingleLine(true);
        content.addView(url);
        EditText note = new EditText(this);
        note.setHint("직접 검토한 짧은 요약·음악 진행 소재 (350자)");
        note.setMinLines(3);
        content.addView(note);
        new AlertDialog.Builder(this)
                .setTitle("검토한 학습자료 · 저장 "
                        + RadioAudienceStore.approvedNoteCount(this) + "건")
                .setMessage("방송의 새 소재로 쓸 자료를 운영자가 직접 검토하세요. "
                        + "외부 사이트에 자동 로그인하거나 자동 수집하지 않습니다.")
                .setView(content)
                .setPositiveButton("검토 메모 저장", (dialog, which) -> {
                    if (RadioAudienceStore.addApprovedResearch(
                            this, url.getText().toString(), note.getText().toString()))
                        toast("검토 메모 저장 완료 · 초대석 대화에 순환 반영");
                    else toast("https:// 주소와 검토 메모를 모두 입력하세요.");
                })
                .setNeutralButton("사이트 열기", (dialog, which) -> {
                    String link = url.getText().toString().trim();
                    if (!link.startsWith("https://")) {
                        toast("https:// 주소를 입력해 주세요.");
                        return;
                    }
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
                    } catch (RuntimeException error) {
                        toast("브라우저에서 사이트를 열 수 없습니다.");
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showSunoBigoGuide() {
        new AlertDialog.Builder(this)
                .setTitle("Suno 원곡으로 BIGO 오디오 LIVE")
                .setMessage(
                        "1. Suno에서 내 원곡 MP3/WAV를 휴대폰에 저장하세요.\n"
                        + "2. 파일 관리자에서 음악을 공유할 때 MARU MUSIC을 고르면 "
                        + "원본 파일을 변환하지 않고 보관합니다. 또는 '노래 직접 추가'를 누르세요.\n"
                        + "3. BIGO 오디오 LIVE의 도구 → 믹서/배경음악에서 "
                        + "휴대폰 로컬 음원 선택이 제공된다면 Suno 파일을 그쪽에서 선택해 "
                        + "송출 음량을 별도로 관리할 수 있는지 확인하세요.\n\n"
                        + "중요: Suno 앱에서 직접 재생 중인 소리를 MARU가 가로채 "
                        + "BIGO 마이크 입력으로 전달하는 기능은 아닙니다. "
                        + "휴대폰 볼륨키와 실제 시청자 음량 분리는 BIGO 지원 여부에 달려 있습니다.")
                .setPositiveButton("원곡 파일 선택", (dialog, which) ->
                        audioPickerLauncher.launch(new String[]{"audio/*"}))
                .setNegativeButton("닫기", null)
                .show();
    }

    /** Preview the DJ conversation and original music entirely on this device. */
    /** Production profiles are plans, not completed artist voice models. */
    private void showSunoArtistRoster() {
        SunoArtistProductionCatalog.Profile[] profiles =
                SunoArtistProductionCatalog.all();
        String[] labels = new String[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            SunoArtistProductionCatalog.Profile item = profiles[i];
            labels[i] = item.artistId + ". " + item.singerName()
                    + " · " + item.genre + " · " + item.language
                    + " · 제작 대기";
        }
        new AlertDialog.Builder(this)
                .setTitle("32명 보컬 제작표 · 실제 가창 생성 전")
                .setItems(labels, (dialog, which) -> showSunoArtistProfile(profiles[which]))
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showSunoArtistProfile(SunoArtistProductionCatalog.Profile singer) {
        String info = "가수: " + singer.singerName()
                + "\n장르: " + singer.genre
                + "\n언어: " + singer.language
                + "\n신곡 작업명: " + singer.originalTitle
                + "\n곡 주제: " + singer.lyricalTheme
                + "\n음역: " + singer.range
                + "\n보컬 톤: " + singer.timbre
                + "\n개별 창법: " + singer.technique
                + "\n제작 상태: 음색·곡 검증 전"
                + "\n\nSuno Style Prompt:\n" + singer.stylePrompt();
        new AlertDialog.Builder(this)
                .setTitle(singer.singerName() + " · 보컬 제작 명세")
                .setMessage(info)
                .setPositiveButton("Suno 스타일 복사", (dialog, which) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(
                                singer.singerName() + " Suno 스타일",
                                singer.stylePrompt()));
                        toast("가수별 Suno 스타일을 복사했습니다.");
                    }
                })
                .setNeutralButton("Suno 제작 화면", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://suno.com/create")));
                    } catch (RuntimeException error) {
                        toast("Suno 페이지를 열 수 없습니다.");
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void startLocalRadioPreview() {
        if (songs.isEmpty()) {
            toast("미리듣기용 음악을 먼저 추가하세요.");
            return;
        }
        if (!AppStorage.songTitleTts(this) || !AppStorage.radioConversationEnabled(this)) {
            toast("설정에서 곡 사이 TTS와 AI 가수 교대 방송을 켜 주세요.");
            return;
        }
        AutoGreetingStore.setRunningMode(this, "local_radio_preview");
        AutoGreetingStore.setStatus(this, "로컬 32명 가수 인터뷰 미리듣기 · BIGO 송출 없음");
        if (playback != null) playback.setBigoCoexistenceMode(false);
        startMusicForBroadcast();
        refreshAutoGreetingStatus();
        toast("라디오 미리듣기 실행 · 재생 중이었다면 다음 곡부터 대화합니다.");
    }


    /** User starts a companion from MARU, then listens to the official Suno app. */
    private void startSunoCompanion(boolean automatic) {
        try {
            // Never run two music sources when the user explicitly chose Suno.
            if (playback != null) playback.pause();
            AutoGreetingService.cancel(this);
            SunoTalkCompanionService.command(this, automatic
                    ? SunoTalkCompanionService.ACTION_START_AUTO
                    : SunoTalkCompanionService.ACTION_MANUAL);
            openOfficialSuno();
            toast(automatic
                    ? "아환 자동 토크 켜짐 · 알림에서 즉시 멘트 가능"
                    : "아환 수동 토크 켜짐 · 알림에서 멘트를 누르세요");
        } catch (RuntimeException error) {
            toast("Suno 토크를 시작하지 못했습니다. 알림 권한과 음성 설정을 확인하세요.");
        }
    }

    private void openOfficialSuno() {
        try {
            Intent installed = getPackageManager().getLaunchIntentForPackage("com.suno.android");
            if (installed != null) {
                startActivity(installed);
                return;
            }
        } catch (RuntimeException ignored) {}
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://suno.com/")));
        } catch (RuntimeException ignored) {
            toast("Suno 앱이나 웹사이트를 열 수 없습니다.");
        }
    }

    private void speakSunoCompanionNow() {
        try {
            SunoTalkCompanionService.command(this, SunoTalkCompanionService.ACTION_TALK_NOW);
        } catch (RuntimeException error) {
            toast("아환 토크 음성을 시작할 수 없습니다.");
        }
    }


    private void previewMyVoice() {
        File wav = MyVoiceFileProcessor.output(this);
        if (!wav.isFile()) { toast("먼저 내 음성 녹음을 선택해서 보정하세요."); return; }
        try {
            if (ownVoicePreview != null) {
                ownVoicePreview.stop();
                ownVoicePreview.release();
            }
            ownVoicePreview = new MediaPlayer();
            ownVoicePreview.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            ownVoicePreview.setDataSource(wav.getAbsolutePath());
            ownVoicePreview.setOnCompletionListener(mp -> {
                mp.release();
                if (ownVoicePreview == mp) ownVoicePreview = null;
            });
            ownVoicePreview.prepare();
            ownVoicePreview.start();
        } catch (IOException | RuntimeException e) {
            if (ownVoicePreview != null) {
                ownVoicePreview.release();
                ownVoicePreview = null;
            }
            toast("보정 WAV 미리듣기에 실패했습니다.");
        }
    }

    private void playMyVoiceOverSuno() {
        if (!MyVoiceFileProcessor.output(this).isFile()) {
            toast("보정 음성 파일을 먼저 준비하세요.");
            return;
        }
        try {
            SunoTalkCompanionService.command(this,
                    SunoTalkCompanionService.ACTION_PLAY_MY_VOICE);
        } catch (RuntimeException e) {
            toast("보정 목소리 재생에 실패했습니다.");
        }
    }

    private void stopSunoCompanion() {
        try {
            SunoTalkCompanionService.command(this, SunoTalkCompanionService.ACTION_STOP);
            toast("Suno 아환 토크 종료");
        } catch (RuntimeException error) {
            toast("토크 중지 요청을 처리하지 못했습니다.");
        }
    }

    private void startCommunityLive(String liveMode) {
        // Selecting MARU's own radio playback ends any Suno companion TTS.
        stopSunoCompanion();
        Intent bigoLaunch = getPackageManager()
                .getLaunchIntentForPackage(OneClickBroadcastPlan.BIGO_PACKAGE);
        if (songs.isEmpty()) {
            toast("방송할 노래가 없습니다. 노래를 먼저 추가하세요.");
            return;
        }
        if (bigoLaunch == null) {
            toast("BIGO LIVE를 찾지 못했습니다.");
            return;
        }
        if (!canReadBroadcastSong()) return;

        try {
            ScreenOcrGreetingService.stop(this);
            AutoGreetingService.cancel(this);
            IntermissionStore.resetSession(this);
            AutoGreetingStore.setRunningMode(this, "community_live");
            AutoGreetingStore.setStatus(
                    this,
                    liveMode + " 준비 · 음악 재생 후 BIGO 앱을 엽니다. 방송 종류·시작은 직접 선택해 주세요.");

            if (playback != null) playback.setBigoCoexistenceMode(true);
            startMusicForBroadcast();
            refreshAutoGreetingStatus();
            showTemporaryTestOverlay(
                    "BIGO 앱을 엽니다 · " + liveMode + " 선택 후 방송 시작을 직접 눌러 주세요",
                    ONE_CLICK_GUIDE_MS);
            handler.postDelayed(this::openBigoChat, 650L);
        } catch (RuntimeException error) {
            AutoGreetingStore.setRunningMode(this, "");
            AutoGreetingStore.setStatus(
                    this,
                    "BIGO 방송 준비 실패 · " + safeErrorMessage(error));
            toast("방송 준비 오류를 막았습니다. 노래를 다시 추가한 뒤 시도하세요.");
        }
    }

    private void showHomePlayer() {
        miniPlayerMode = false;
        miniSongTitle = null;
        miniPlayButton = null;
        homePlayerMode = true;
        localTestMode = false;
        selectedMode = BroadcastMode.PORTRAIT_9_16;
        AppStorage.setBroadcastMode(this, selectedMode.name());
        startBroadcast();
    }


    private void openSetupFromPlayer() {
        leavePlayerScreen(false);
    }

    private void showHomePlayerMenu() {
        String[] items = {
                "BIGO 오디오 LIVE 시작",
                "BIGO와 함께 보기 · 사진/영상 한 화면",
                "Suno 음악 + 아환 자동 토크",
                "아환 짧은 멘트 바로 말하기",
                "형의 보정 음성 멘트 재생",
                "Suno 아환 토크 종료",
                "노래·이미지·설정 열기",
                "새 버전 확인 · 업데이트",
                "완전 종료"
        };
        new AlertDialog.Builder(this)
                .setTitle("MARU MUSIC LIVE")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) startCommunityLive("오디오 LIVE");
                    else if (which == 1) showMiniController();
                    else if (which == 2) startSunoCompanion(true);
                    else if (which == 3) speakSunoCompanionNow();
                    else if (which == 4) playMyVoiceOverSuno();
                    else if (which == 5) stopSunoCompanion();
                    else if (which == 6) openSetupFromPlayer();
                    else if (which == 7) checkAppUpdate(true);
                    else stopAllBroadcastNow();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * One image or silent, looping video fills Samsung's user-selected pop-up.
     * This is only MARU's own Activity, not an injected BIGO window, and is
     * not evidence that a BIGO listener sees the picture or hears the music.
     */
    private void showMiniController() {
        if (broadcastVisible) leavePlayerScreen(false); // preserve music service
        releaseMiniMedia();
        miniPlayerMode = true;
        homePlayerMode = false;
        localTestMode = false;
        miniSongTitle = null;
        miniPlayButton = null;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        showSystemBars();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout popup = new FrameLayout(this);
        popup.setBackgroundColor(Color.BLACK);
        miniMediaPreview = new SingleMediaPreviewView(this);
        popup.addView(miniMediaPreview, match());
        // Tap the image to temporarily reveal a tiny row of controls.
        View tapLayer = new View(this);
        tapLayer.setOnClickListener(v -> showMiniMediaActions());
        popup.addView(tapLayer, match());

        miniMediaActions = new LinearLayout(this);
        miniMediaActions.setGravity(Gravity.CENTER);
        miniMediaActions.setOrientation(LinearLayout.HORIZONTAL);
        miniMediaActions.setBackgroundColor(0xCC101018);
        miniMediaActions.addView(smallButton("사진·영상 선택", v -> {
            if (miniMediaPickerLauncher != null)
                miniMediaPickerLauncher.launch(new String[]{"image/*", "video/*"});
        }));
        miniMediaActions.addView(smallButton("음악 재생/정지", v -> {
            if (playback != null) playback.toggle();
        }));
        miniMediaActions.addView(smallButton("설정", v -> openSetupFromMini()));
        FrameLayout.LayoutParams bar = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        popup.addView(miniMediaActions, bar);
        setContentView(popup);
        showSavedMiniMedia();
        showMiniMediaActions();
    }

    private boolean isValidLocalVisual(String text) {
        if (!SingleMediaPreviewPolicy.isLocalUri(text)) return false;
        Uri uri = Uri.parse(text);
        String type = "";
        try { type = getContentResolver().getType(uri); }
        catch (RuntimeException ignored) {}
        return SingleMediaPreviewPolicy.accept(type, text);
    }

    /** Each approved story and each song gets its own image/video. Default is MP4. */
    private void showSavedMiniMedia() {
        if (miniMediaPreview == null) return;
        String story = activeStoryVisualId.isEmpty()
                ? "" : StoryMediaStore.load(this, activeStoryVisualId);
        String song = "";
        java.util.List<String> assigned = SongMediaStore.load(this, currentSongUri());
        if (!assigned.isEmpty()) song = assigned.get(0);
        if (!isValidLocalVisual(story)) story = "";
        if (!isValidLocalVisual(song)) song = "";
        String fallback = AppStorage.defaultBroadcastVideo(this);
        if (!SingleMediaPreviewPolicy.isVideo(safeMime(fallback), fallback))
            fallback = resourceUri(R.raw.default_broadcast);
        String value = BroadcastMediaSelector.choose(
                activeStoryVisualId, story, song, fallback);
        if (value.equals(miniShownUri)) return; // never restart the same video on every refresh
        miniShownUri = value;
        if (value.isEmpty()) {
            miniMediaPreview.showDefaultVideo();
            return;
        }
        miniMediaPreview.show(Uri.parse(value), safeMime(value));
    }

    private String safeMime(String uriText) {
        if (!SingleMediaPreviewPolicy.isLocalUri(uriText)) return "";
        try { return getContentResolver().getType(Uri.parse(uriText)); }
        catch (RuntimeException ignored) { return ""; }
    }

    private void showMiniMediaActions() {
        if (miniMediaActions == null) return;
        miniMediaActions.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideMiniMediaActions);
        handler.postDelayed(hideMiniMediaActions, 4_000L);
    }

    private void releaseMiniMedia() {
        miniShownUri = "";
        handler.removeCallbacks(hideMiniMediaActions);
        if (miniMediaPreview != null) {
            miniMediaPreview.stopForLeavingScreen();
            miniMediaPreview = null;
        }
        miniMediaActions = null;
    }

    private void openSetupFromMini() {
        releaseMiniMedia();
        miniPlayerMode = false;
        miniSongTitle = null;
        miniPlayButton = null;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        showSystemBars();
        setContentView(appRoot);
    }

    private void leavePlayerScreen(boolean pauseMusic) {
        broadcastVisible = false;
        homePlayerMode = false;

        handler.removeCallbacks(updateProgress);
        handler.removeCallbacks(rotateImage);
        handler.removeCallbacks(hideControls);
        activeEventPriority = 0;
        activeEventUntil = 0L;
        eventOverlayQueue.clear();
        eventOverlayShowing = false;

        if (mediaVideo != null) {
            try {
                mediaVideo.stopPlayback();
            } catch (RuntimeException ignored) {}
        }

        if (pauseMusic && playback != null) {
            playback.pause();
        }

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        showSystemBars();
        refreshAutoGreetingStatus();
        setContentView(appRoot);
    }



    // V3.2.3 PLAYER VISIBILITY POLICY:
    // - 기존 V3.1.6 PlaybackService를 변경하지 않는다.
    // - 앱을 열면 이미지와 이전/재생/다음/LIVE/설정 버튼을 즉시 표시한다.
    // - 원클릭은 음악 재생 후 BIGO 일반 LIVE 방송 준비 화면까지 자동 이동한다.


    private void requestScreenCapture(String mode) {
        pendingCaptureMode = mode;
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            toast("화면 공유 기능을 사용할 수 없습니다.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("BIGO 댓글 OCR · 자동응답 입력")
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
        boolean oneClickRequest = oneClickStarting;
        if (result.getResultCode() != Activity.RESULT_OK || data == null) {
            oneClickStarting = false;
            AutoGreetingStore.setRunningMode(this, "");
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

            if (oneClickRequest && !broadcastVisible) {
                startBroadcast();
            }

            if (oneClickRequest) {
                AutoGreetingStore.setStatus(
                        this,
                        "원클릭 방송 준비 완료 · 일반 댓글 OCR·자동입력·음악 실행 중");
                refreshAutoGreetingStatus();
                showTemporaryTestOverlay(
                        "BIGO에서 게임 LIVE → MARU MUSIC LIVE 선택 → 화면 공유 허용 → 방송 시작",
                        ONE_CLICK_GUIDE_MS);
            }

            handler.postDelayed(() -> {
                if (ScreenOcrGreetingService.MODE_LOCAL_TEST.equals(requestedMode)) {
                    startActivity(new Intent(this, OcrTestActivity.class));
                } else {
                    openBigoChat();
                }
                if (oneClickRequest) {
                    oneClickStarting = false;
                }
            }, oneClickRequest
                    ? OneClickBroadcastPlan.BIGO_OPEN_DELAY_MS
                    : 650L);
        } catch (RuntimeException error) {
            oneClickStarting = false;
            AutoGreetingStore.setStatus(this, "입장 감지 시작 실패: " + error.getMessage());
            refreshAutoGreetingStatus();
            toast("입장 감지를 시작하지 못했습니다.");
        }
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
        } else if ("bigo_native_toolbar".equals(running)) {
            mode = "V3.1.6 음악 재생 구조 · BIGO 네이티브 댓글·상점 도구막대";
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
                        + "곡 사이 음성: "
                        + (!AppStorage.songTitleTts(this) ? "꺼짐"
                            : AppStorage.radioConversationEnabled(this)
                                ? (AppStorage.dailyRadioEnabled(this)
                                   ? "오늘의 대화 주제 자동 갱신 (" 
                                           + java.time.LocalDate.now().toString() + ")"
                                   : "아환과 32명 가상 AI 가수 대화")
                                : "한국어 → 영어 → 중국어 → 일본어 → 러시아어 안내")
                        + "\n"
                        + "AI 습득: " + (AdaptiveAiStore.enabled(this) ? "켜짐" : "꺼짐")
                        + " · 대화형 화면 답변: "
                        + (AdaptiveAiStore.conversationEnabled(this) ? "켜짐" : "꺼짐")
                        + " · 댓글 음성 답변: 꺼짐 · 댓글 글 자동전송: 꺼짐"
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
        // V3.2.11: 분할 화면에서도 원본 이미지의 위·아래·좌·우를 자르지 않는다.
        // 큰 세로 이미지도 사용 가능한 영역 안으로 자동 축소해서 전체가 보이게 한다.
        foregroundImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        foregroundImage.setAdjustViewBounds(true);
        foregroundImage.setPadding(dp(4), dp(4), dp(4), dp(4));
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

        if (homePlayerMode) {
            controls.addView(smallButton("사진·영상", v -> showMiniController()));
            controls.addView(smallButton("오디오 LIVE", v ->
                    startCommunityLive("오디오 LIVE")));
            controls.addView(smallButton("설정", v ->
                    openSetupFromPlayer()));
        } else if (localTestMode) {
            controls.addView(smallButton("설정", v ->
                    openSetupFromPlayer()));
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
                        dp(homePlayerMode ? 72 : 64),
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
        // One visual is fixed for the entire song; no slideshow rotation.

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
        // All audio-related controls should stop, including optional Suno TTS.
        stopSunoCompanion();
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
        homePlayerMode = false;
        oneClickStarting = false;

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
                                    new String[]{"이미지·MP4 선택", "등록 삭제"},
                                    (menu, action) -> {
                                        if (action == 0) {
                                            pendingEventVisualType = type;
                                            eventVisualPickerLauncher.launch(
                                                    new String[]{"image/*", "video/*"});
                                        } else if (action == 1) {
                                            EventVisualStore.clear(this, type);
                                            toast(type.koreanLabel + " 화면 삭제");
                                        }
                                    })
                            .setNegativeButton("닫기", null)
                            .show();
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showStoryMediaPickerDialog() {
        java.util.List<RadioAudienceStore.Story> list =
                RadioAudienceStore.pendingStories(this);
        if (list.isEmpty()) {
            toast("먼저 방송 승인을 받은 사연을 등록하세요.");
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            RadioAudienceStore.Story story = list.get(i);
            names[i] = story.name + " · " +
                    (story.text.length() > 26 ? story.text.substring(0, 26) + "…" : story.text)
                    + (StoryMediaStore.load(this, story.id).isEmpty()
                            ? " · 미등록" : " · 사진/영상 등록");
        }
        new AlertDialog.Builder(this)
                .setTitle("승인 사연별 화면 · 사연마다 하나")
                .setItems(names, (dialog, which) -> {
                    pendingStoryVisualId = list.get(which).id;
                    new AlertDialog.Builder(this)
                            .setTitle(names[which])
                            .setItems(new String[]{"사진·영상 지정", "지정 해제"}, (d, choice) -> {
                                if (choice == 0) {
                                    storyVisualPickerLauncher.launch(
                                            new String[]{"image/*", "video/*"});
                                } else {
                                    StoryMediaStore.clear(this, pendingStoryVisualId);
                                    if (miniPlayerMode) showSavedMiniMedia();
                                    if (broadcastVisible
                                            && pendingStoryVisualId.equals(activeStoryVisualId))
                                        showMediaUri(resourceUri(R.raw.default_broadcast), true);
                                    toast("사연 전용 화면 해제 · 기본 영상으로 복귀");
                                }
                            }).setNegativeButton("닫기", null).show();
                }).setNegativeButton("닫기", null).show();
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
        if (!song.isEmpty()) {
            for (String media : SongMediaStore.load(this, song)) {
                if (isValidLocalVisual(media)) {
                    activeSongMedia.add(media);
                    break; // one media for this song
                }
            }
        }
        // No shared random image: each song has its own visual or default video.
        if (activeSongMedia.size() > 1)
            activeSongMedia.subList(1, activeSongMedia.size()).clear();
        mediaIndex = 0;
    }

    private void showActiveSongMedia() {
        if (activeSongMedia.isEmpty()) {
            String fallback = AppStorage.defaultBroadcastVideo(this);
            if (!SingleMediaPreviewPolicy.isVideo(safeMime(fallback), fallback))
                fallback = resourceUri(R.raw.default_broadcast);
            showMediaUri(fallback, true);
            return;
        }
        mediaIndex = 0;
        showMediaUri(activeSongMedia.get(0), false);
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
                mediaVideo.setOnErrorListener((player, what, extra) -> {
                    String builtIn = resourceUri(R.raw.default_broadcast);
                    if (!builtIn.equals(uriText)) {
                        showMediaUri(builtIn, true);
                    } else {
                        try { mediaVideo.stopPlayback(); } catch (RuntimeException ignored) {}
                        mediaVideo.setVisibility(View.GONE);
                        if (foregroundImage != null) {
                            foregroundImage.setVisibility(View.VISIBLE);
                            foregroundImage.setImageResource(R.drawable.actual_image_01);
                        }
                        if (backgroundImage != null) backgroundImage.setVisibility(View.GONE);
                    }
                    return true;
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
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeResource(
                    getResources(),
                    R.drawable.actual_image_01);
        }
        if (bitmap != null) {
            backgroundImage.setImageBitmap(bitmap);
            foregroundImage.setImageBitmap(bitmap);
        }
    }

    private boolean isVideoUri(Uri uri) {
        String type = getContentResolver().getType(uri);
        if (type != null && type.startsWith("video/")) return true;
        String value = uri.toString().toLowerCase(Locale.ROOT);
        if (uri.toString().equals(resourceUri(R.raw.default_broadcast))) return true;
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

    private boolean canReadBroadcastSong() {
        if (songs.isEmpty()) return false;
        int index = playback == null ? 0 : playback.currentIndex();
        if (index < 0 || index >= songs.size()) index = 0;
        Uri songUri = Uri.parse(songs.get(index));
        try (AssetFileDescriptor descriptor =
                     getContentResolver().openAssetFileDescriptor(songUri, "r")) {
            if (descriptor != null) return true;
        } catch (SecurityException error) {
            toast("노래 파일 권한이 만료되었습니다. 설정에서 삭제 후 다시 추가하세요.");
            return false;
        } catch (Exception error) {
            toast("노래 파일을 열 수 없습니다. 설정에서 다시 추가하세요.");
            return false;
        }
        toast("노래 파일을 열 수 없습니다. 설정에서 다시 추가하세요.");
        return false;
    }

    private static boolean isBigoAudioCoexistenceMode(String mode) {
        return "community_live".equals(mode)
                || "music_only".equals(mode)
                || "bigo_native_toolbar".equals(mode)
                || ScreenOcrGreetingService.MODE_AUTO_GREETING.equals(mode);
    }

    private void startMusicForBroadcast() {
        if (songs.isEmpty()) {
            pendingAutoMusicStart = false;
            toast("방송할 노래가 없습니다. 노래를 추가하세요.");
            return;
        }

        pendingAutoMusicStart = true;
        if (playback == null) return;

        try {
            if (isBigoAudioCoexistenceMode(AutoGreetingStore.runningMode(this)))
                playback.setBigoCoexistenceMode(true);
            playback.setQueue(songs);
            playback.prepareForBroadcast();
        } catch (RuntimeException error) {
            pendingAutoMusicStart = false;
            AutoGreetingStore.setStatus(
                    this,
                    "음악 재생 시작 실패 · " + safeErrorMessage(error));
            toast("음악 재생 오류를 막았습니다. 노래를 다시 추가하세요.");
            return;
        }
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

    /** Attribution is opt-in; importing a file never fabricates a singer. */
    private void showSongManageActions(int position) {
        if (position < 0 || position >= songs.size()) return;
        new AlertDialog.Builder(this)
                .setTitle(displayName(Uri.parse(songs.get(position))))
                .setItems(new String[]{
                        "이 노래의 AI 가수 배정",
                        "가수 배정 해제",
                        "노래 삭제"
                }, (dialog, selected) -> {
                    if (selected == 0) showSongSingerPicker(position);
                    else if (selected == 1) {
                        AppStorage.setArtistForSong(this, songs.get(position), 0);
                        refreshSongList();
                        toast("이 곡의 가수 배정을 지웠습니다.");
                    } else confirmDeleteSong(position);
                })
                .setNegativeButton("취소", null).show();
    }

    private void showSongSingerPicker(int position) {
        if (position < 0 || position >= songs.size()) return;
        SunoArtistProductionCatalog.Profile[] profiles =
                SunoArtistProductionCatalog.all();
        String[] names = new String[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            names[i] = profiles[i].singerName() + " · " + profiles[i].genre
                    + " · " + profiles[i].language;
        }
        new AlertDialog.Builder(this)
                .setTitle("가수 배정 · 실제 가창 음원을 확인한 후 선택")
                .setItems(names, (dialog, which) -> {
                    AppStorage.setArtistForSong(this, songs.get(position),
                            profiles[which].artistId);
                    refreshSongList();
                    toast(profiles[which].singerName()
                            + " 가수 배정 완료 · 실제 음색 검증은 별도");
                })
                .setNegativeButton("취소", null).show();
    }

    private void deleteSong(int position) {
        if (position < 0 || position >= songs.size()) return;

        if (playback != null) {
            playback.stopPlayback();
        }

        AppStorage.setArtistForSong(this, songs.get(position), 0);
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
                            for (String song : songs) {
                                AppStorage.setArtistForSong(this, song, 0);
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
        try {
            ContextCompat.startForegroundService(this, intent);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException error) {
            AutoGreetingStore.setStatus(
                    this,
                    "음악 서비스 시작 실패 · " + safeErrorMessage(error));
            toast("음악 서비스를 시작하지 못했습니다. 앱을 다시 열어 주세요.");
        }
    }





    private void beginHostSpeechDuck() {
        boolean broadcasting = isBigoAudioCoexistenceMode(AutoGreetingStore.runningMode(this));
        if (!broadcasting && playback != null) playback.setHostVolume(0.18f);
        handler.removeCallbacks(restoreHostSpeech);
        if (!broadcasting) handler.postDelayed(restoreHostSpeech, HOST_SPEECH_AUTO_RESTORE_MS);
        toast(broadcasting ? "BIGO 방송 모드: MARU 음악 크기 유지" : "말하기 모드: 음악 18%");
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
        // Don't compete for Android audio focus with BIGO after handoff.
        // This is the same foreground PlaybackService, not another audio app.
        if (playback != null) playback.setBigoCoexistenceMode(true);
        Intent launch = getPackageManager()
                .getLaunchIntentForPackage(OneClickBroadcastPlan.BIGO_PACKAGE);
        if (launch == null) {
            toast("BIGO LIVE를 찾지 못했습니다.");
            return;
        }
        try {
            launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launch);
        } catch (ActivityNotFoundException | SecurityException error) {
            AutoGreetingStore.setStatus(
                    this,
                    "BIGO 실행 차단 · " + safeErrorMessage(error));
            toast("BIGO LIVE를 열 수 없습니다. BIGO 앱을 업데이트해 주세요.");
        } catch (RuntimeException error) {
            AutoGreetingStore.setStatus(
                    this,
                    "BIGO 실행 오류 방지 · " + safeErrorMessage(error));
            toast("BIGO 실행 오류를 막았습니다. MARU 음악은 계속 재생됩니다.");
        }
    }


    private void allowAudioCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AudioManager manager = getSystemService(AudioManager.class);
            if (manager != null) {
                try {
                    manager.setAllowedCapturePolicy(
                            android.media.AudioAttributes.ALLOW_CAPTURE_BY_ALL);
                } catch (RuntimeException ignored) {
                    // 제조사별 오디오 정책 오류가 있어도 앱은 계속 실행한다.
                }
            }
        }
    }

    private String safeErrorMessage(Throwable error) {
        if (error == null) return "알 수 없는 오류";
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message.trim();
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
        if ("file".equals(uri.getScheme()) && uri.getLastPathSegment() != null) {
            String storedName = uri.getLastPathSegment();
            int separator = storedName.indexOf("--");
            if (separator == 64) return storedName.substring(separator + 2);
        }
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

    private String musicReadPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void scanDeviceMusic(boolean userInitiated) {
        if (deviceMusicScanRunning) {
            if (userInitiated) toast("휴대폰 노래를 찾고 있습니다.");
            return;
        }
        if (!userInitiated && initialDeviceMusicScanStarted) return;
        initialDeviceMusicScanStarted = true;
        String permission = musicReadPermission();
        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            boolean alreadyAsked = getPreferences(Context.MODE_PRIVATE)
                    .getBoolean("music_permission_asked", false);
            if (userInitiated || !alreadyAsked) {
                getPreferences(Context.MODE_PRIVATE).edit()
                        .putBoolean("music_permission_asked", true)
                        .apply();
                musicPermissionLauncher.launch(permission);
            }
            return;
        }

        deviceMusicScanRunning = true;
        if (userInitiated) toast("휴대폰 전체 노래를 찾는 중입니다.");
        musicScanExecutor.execute(() -> {
            DeviceMusicScanner.Result result = DeviceMusicScanner.scan(this);
            handler.post(() -> applyDeviceMusicScan(result, userInitiated));
        });
    }

    private void applyDeviceMusicScan(
            DeviceMusicScanner.Result result,
            boolean userInitiated) {
        deviceMusicScanRunning = false;
        if (isFinishing() || isDestroyed()) return;

        List<String> merged = new ArrayList<>();
        for (String existing : songs) {
            if (existing == null || existing.startsWith("content://media/")) continue;
            if (!merged.contains(existing)) merged.add(existing);
        }
        for (SongItem item : result.songs) {
            if (!merged.contains(item.uri)) merged.add(item.uri);
        }

        boolean changed = !merged.equals(songs);
        if (changed) {
            if (playback != null && playback.isPlaying()) playback.stopPlayback();
            songs.clear();
            songs.addAll(merged);
            AppStorage.saveSongs(this, songs);
            refreshSongList();
            if (playback != null) playback.setQueue(songs);
            loadLyricsForCurrentSong();
        }
        if (userInitiated || changed) {
            toast(result.songs.size() + "곡 불러옴 · 동일 파일 "
                    + result.duplicates + "개 제외");
        }
    }

    private void refreshSongList() {
        if (songList == null) return;
        List<String> names = new ArrayList<>();
        for (String song : songs) {
            int singerId = AppStorage.artistForSong(this, song);
            String title = displayName(Uri.parse(song));
            if (singerId > 0) {
                title += " · " + VirtualArtistCatalog.at(singerId - 1).name;
            }
            names.add(title);
        }
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

    private void syncPlayButtonState() {
        boolean playing = playback != null && playback.isPlaying();
        if (miniPlayerMode && miniPlayButton != null) {
            miniPlayButton.setText(playing ? "일시정지" : "재생");
            miniPlayButton.setContentDescription(playing ? "음악 일시정지" : "음악 재생");
        }
        if (playButton != null) {
            playButton.setText(playing ? "일시정지" : "재생");
            playButton.setContentDescription(playing ? "음악 일시정지" : "음악 재생");
        }
    }

    @Override public void onTrackChanged(int index) {
        runOnUiThread(() -> {
            if (miniPlayerMode && activeStoryVisualId.isEmpty()) showSavedMiniMedia();
            if (miniPlayerMode && miniSongTitle != null) miniSongTitle.setText(currentTitle());
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
        runOnUiThread(this::syncPlayButtonState);
    }

    @Override public void onError(String message) {
        runOnUiThread(() -> toast(message));
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handler.postDelayed(this::syncPlayButtonState, 120L);
    }

    private void checkAppUpdate(boolean manual) {
        android.content.SharedPreferences preferences =
                getSharedPreferences("maru_update_check", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!manual) {
            long last = preferences.getLong("last_checked_ms", 0L);
            if (last > 0L && now >= last && now - last < 24L * 60L * 60L * 1000L) {
                return;
            }
        }
        preferences.edit().putLong("last_checked_ms", now).apply();
        if (manual) toast("업데이트 서버 확인 중…");
        appUpdateExecutor.execute(() -> {
            AppUpdateChecker.Result result = AppUpdateChecker.check(getApplicationContext());
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result.hasUpdate()) {
                    new AlertDialog.Builder(this)
                            .setTitle("MARU RADIO V" + result.latestVersion + " 업데이트")
                            .setMessage("현재 V" + result.installedVersion + " → 새 V"
                                    + result.latestVersion + "\n\n"
                                    + "새 APK 다운로드 페이지를 엽니다. "
                                    + "설치 전에 시스템 승인이 필요하며, 기존 앱을 삭제하지 마세요.")
                            .setPositiveButton("새 APK 다운로드", (dialog, which) -> {
                                try {
                                    startActivity(new Intent(Intent.ACTION_VIEW,
                                            Uri.parse(result.downloadUrl)));
                                } catch (ActivityNotFoundException error) {
                                    toast("다운로드를 열 브라우저가 없습니다.");
                                }
                            })
                            .setNegativeButton("나중에", null)
                            .show();
                } else if (result.needsSigningFix()) {
                    new AlertDialog.Builder(this)
                            .setTitle("업데이트 설치 보호")
                            .setMessage(result.message)
                            .setPositiveButton("확인", null)
                            .show();
                } else if (manual) {
                    new AlertDialog.Builder(this)
                            .setTitle("MARU RADIO 업데이트 확인")
                            .setMessage(result.message + "\n\n설치 버전: V"
                                    + result.installedVersion
                                    + (result.latestVersion.isEmpty() ? ""
                                            : "\n배포 버전: V" + result.latestVersion))
                            .setPositiveButton("확인", null)
                            .show();
                }
            });
        });
    }

    @Override protected void onDestroy() {
        releaseMiniMedia();
        if (webStudioFileCallback != null) {
            webStudioFileCallback.onReceiveValue(null);
            webStudioFileCallback = null;
        }
        if (webStudioView != null) {
            WebView web = webStudioView;
            webStudioView = null;
            web.destroy();
        }

        if (ownVoicePreview != null) {
            try { ownVoicePreview.stop(); } catch (RuntimeException ignored) {}
            ownVoicePreview.release();
            ownVoicePreview = null;
        }
        appUpdateExecutor.shutdownNow();
        musicScanExecutor.shutdownNow();
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
