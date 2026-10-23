package com.maru.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * New standalone radio app. NO old MARU screen, AI, OCR, WebView or app-volume tricks.
 * This app prepares actual original audio files. BIGO controls live broadcast mixing.
 */
public final class RadioActivity extends Activity {
    private static final int PICK_AUDIO = 101;
    private static final int EXPORT_AUDIO = 102;
    private static final int BACKGROUND = 0xFF110E21;
    private static final int PANEL = 0xFF211B39;
    private static final int VIOLET = 0xFFB89BFF;
    private static final int WHITE = 0xFFF7F1FF;

    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private Originals.Track exporting;
    private RadioPlaybackService radioService;
    private boolean playerBound;
    private boolean playerBindRequested;
    private TextView playerTitle;
    private TextView playerTime;
    private TextView programSummary;
    private Button programCategoryButton;
    private Button presenterToggleButton;
    private Button playerToggleButton;
    private SeekBar playerSeek;
    private boolean playerSeeking;
    private final Handler playerHandler = new Handler(Looper.getMainLooper());
    private final Runnable playerRefresh = new Runnable() {
        @Override public void run() {
            updatePlayerStatus();
            if (playerBound && !isFinishing()) playerHandler.postDelayed(this, 750L);
        }
    };
    private final ServiceConnection playerConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            radioService = ((RadioPlaybackService.LocalBinder) service).getService();
            playerBound = true;
            updatePlayerStatus();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            playerBound = false;
            radioService = null;
            updatePlayerStatus();
        }
    };

    private ListView songList;
    private BaseAdapter songAdapter;
    private TextView songCount;
    private TextView status;
    private EditText search;
    private final List<Originals.Track> allTracks = new ArrayList<>();
    private final List<Originals.Track> shownTracks = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private boolean sameTitleOnly = false;
    private boolean importInProgress = false;
    private int sortMode = 0; // 0 = newest imported first, 1 = title, 2 = oldest first


    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        build();
        acceptShare(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptShare(intent);
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        // One UI / Android 15+ force edge-to-edge for targetSdk 35+.
        // Keep first heading below the status bar, and buttons above gesture navigation.
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(0, safe.top, 0, safe.bottom);
                return insets;
            });
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(26));
        scroll.addView(root);

        // Preserve the currently installed MARU RADIO and its private files.
        // This separate package has independent app storage; never imply that
        // old app-local songs/preferences have already been copied.
        if ("com.maru.radio.transition.v17".equals(getPackageName())) {
            root.addView(label("🛡 MARU RADIO 1.9.25 시험판", 26, VIOLET, true));
            root.addView(label("기존 MARU RADIO는 삭제되거나 바뀌지 않았습니다. 이 앱은 별도로 설치되어 저장곡과 설정이 자동 이전되지 않습니다.",
                    14, WHITE, false));
            root.addView(label("① 원본 Suno 음악은 Download 폴더에서 다시 선택하세요. ② 진행자 대화 기능을 켜고 시험해 보세요. ③ 원래 사용하던 앱은 그대로 두세요.",
                    13, 0xFFFFD8AE, false));
            root.addView(compactButton("⬇ Download에서 자작곡 다시 연결", this::pickDownloads));
            root.addView(label("⚠ BIGO 청취자 실제 소리 전달은 별도 기기로 확인해야 합니다. 서명 문제 해결 없이 기존 앱을 덮어쓰지 않습니다.",
                    12, 0xFFDDD4F5, false));
            gap(root, 12);
        }
        root.addView(label("MARU RADIO · 예약·STUDIO 통합", 25, VIOLET, true));
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            root.addView(label("설치 버전 " + info.versionName + " · " + getPackageName(), 12, WHITE, false));
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        root.addView(compactButton("🎤 가수 30명 후보 · 실제 음원 연결", () ->
                startActivity(new Intent(this, RadioArtistsActivity.class))));
        root.addView(label("Suno 원곡 · BIGO 오디오 LIVE", 16, WHITE, false));
        root.addView(label("Suno MP3/WAV 원곡 · 연속재생 · 백그라운드 음악", 13, 0xFFB1A6C5, false));
        gap(root, 8);

        LinearLayout playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setPadding(dp(15), dp(12), dp(15), dp(11));
        GradientDrawable playerBg = new GradientDrawable();
        playerBg.setColor(PANEL);
        playerBg.setCornerRadius(dp(16));
        playerCard.setBackground(playerBg);
        LinearLayout.LayoutParams playerCardParams =
                new LinearLayout.LayoutParams(-1, -2);
        playerCardParams.bottomMargin = dp(14);
        root.addView(playerCard, playerCardParams);
        playerCard.addView(label("🎵 원곡 플레이어", 20, VIOLET, true));
        playerTitle = label("보관곡을 선택하면 여기에서 재생돼요.", 15, WHITE, false);
        playerTitle.setMaxLines(2);
        playerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        playerCard.addView(playerTitle);
        LinearLayout playbackButtons = new LinearLayout(this);
        playbackButtons.setOrientation(LinearLayout.HORIZONTAL);
        playbackButtons.setGravity(Gravity.CENTER);
        playbackButtons.addView(compactButton("⏮ 이전", () ->
                playerAction(RadioPlaybackService.ACTION_PREVIOUS)),
                new LinearLayout.LayoutParams(0, dp(55), 1));
        playerToggleButton = compactButton("▶ 재생", this::togglePlayer);
        playerToggleButton.setTextSize(17);
        playbackButtons.addView(playerToggleButton,
                new LinearLayout.LayoutParams(0, dp(55), 1.35f));
        playbackButtons.addView(compactButton("다음 ⏭", () ->
                playerAction(RadioPlaybackService.ACTION_NEXT)),
                new LinearLayout.LayoutParams(0, dp(55), 1));
        playerCard.addView(playbackButtons);
        playerSeek = new SeekBar(this);
        playerSeek.setMax(1000);
        playerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) { playerSeeking = true; }
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                playerSeeking = false;
                if (radioService != null) radioService.seekTo(seekBar.getProgress());
                updatePlayerStatus();
            }
        });
        playerCard.addView(playerSeek);
        playerTime = label("00:00 / 00:00", 12, 0xFFCCC2DF, false);
        playerCard.addView(playerTime);
        playerCard.addView(compactButton("■ 재생 종료", () ->
                playerAction(RadioPlaybackService.ACTION_STOP)));
        gap(root, 8);

        LinearLayout programCard = new LinearLayout(this);
        programCard.setOrientation(LinearLayout.VERTICAL);
        programCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable programBg = new GradientDrawable();
        programBg.setColor(0xFF2D2442);
        programBg.setCornerRadius(dp(14));
        programCard.setBackground(programBg);
        LinearLayout.LayoutParams programParams = new LinearLayout.LayoutParams(-1, -2);
        programParams.bottomMargin = dp(14);
        root.addView(programCard, programParams);
        programCard.addView(label("📻 방송 편성 · 시간 설정", 19, WHITE, true));
        programCategoryButton = compactButton("노래 분류 선택", this::selectProgramCategory);
        programCategoryButton.setTextSize(15);
        programCard.addView(programCategoryButton);
        presenterToggleButton = compactButton("", this::togglePresenters);
        programCard.addView(presenterToggleButton);
        updatePresenterToggle();
        programCard.addView(label("선택한 가상 진행자들이 기기 TTS로 인사하고 원본을 소개한 뒤 노래를 재생합니다. BIGO 마이크 송출은 별도 확인이 필요합니다.",
                12, 0xFFCCC0DA, false));
        programSummary = label("방송 분류와 종료시간을 선택하세요.", 13, 0xFFE4D8F3, false);
        programCard.addView(programSummary);
        LinearLayout timerButtons = new LinearLayout(this);
        timerButtons.setOrientation(LinearLayout.HORIZONTAL);
        timerButtons.addView(compactButton("⏱ 선택한 분 뒤 음악 종료", this::configureProgramTimer),
                new LinearLayout.LayoutParams(0, dp(53), 1.2f));
        timerButtons.addView(compactButton("타이머 취소", this::cancelProgramTimer),
                new LinearLayout.LayoutParams(0, dp(53), 0.85f));
        programCard.addView(timerButtons);
        programCard.addView(compactButton("📅 시작·종료 시각 함께 예약", () ->
                startActivity(new Intent(this, RadioAutomationActivity.class))));
        programCard.addView(label("예약 시 MARU 음악 자동 시작·종료 · BIGO 방송 제어와 댓글 수집은 미연결",
                12, 0xFFCCC0DA, false));
        gap(root, 8);

        LinearLayout bigoStartEnd = new LinearLayout(this);
        bigoStartEnd.setOrientation(LinearLayout.VERTICAL);
        bigoStartEnd.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bigoBackground = new GradientDrawable();
        bigoBackground.setColor(0xFF2E204C);
        bigoBackground.setCornerRadius(dp(14));
        bigoStartEnd.setBackground(bigoBackground);
        LinearLayout.LayoutParams bigoBoxParams = new LinearLayout.LayoutParams(-1, -2);
        bigoBoxParams.bottomMargin = dp(14);
        root.addView(bigoStartEnd, bigoBoxParams);
        bigoStartEnd.addView(label("🎙 BIGO 오디오 LIVE 시작 · 종료", 18, WHITE, true));
        LinearLayout startEndButtons = new LinearLayout(this);
        startEndButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button startBigo = button("▶ BIGO 시작 화면", this::requestBigoStart);
        startBigo.setTextSize(15);
        Button endBigo = button("■ BIGO 종료 화면", this::requestBigoEnd);
        endBigo.setTextSize(15);
        startEndButtons.addView(startBigo, new LinearLayout.LayoutParams(0, dp(58), 1));
        startEndButtons.addView(endBigo, new LinearLayout.LayoutParams(0, dp(58), 1));
        bigoStartEnd.addView(startEndButtons);
        bigoStartEnd.addView(button("🎧 BIGO 청취자 수신 테스트", () ->
                startActivity(new Intent(this, BigoAudioCheckActivity.class))));
        bigoStartEnd.addView(label("접근성 권한 없이 BIGO 실행만 지원합니다. "
                + "라이브 시작·종료는 BIGO 화면에서 직접 누르세요.", 12, 0xFFDDD4F5, false));

        card(root, "1  DOWNLOAD에서 원곡 가져오기",
                "이미 저장한 Suno MP3/WAV를 여러 곡 선택하세요. 같은 내용은 한 곡만 남깁니다.",
                "Download 바로가기", this::pickDownloads,
                "다른 폴더에서 가져오기", this::pickFiles);

        card(root, "2  원곡 목록 · 선택 삭제",
                "다운로드 원곡은 복사하지 않고 읽기 권한만 보관합니다. 검색·선택 삭제는 MARU 목록에만 적용됩니다.",
                "곡 목록 새로고침", this::refreshSongs,
                "같은 제목 후보 확인", this::toggleSameTitleFilter);

        songCount = label("보관 원곡 0곡", 16, VIOLET, true);
        root.addView(songCount);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(0xFFA79BB8);
        search.setTextSize(16);
        search.setHint("곡 제목 검색");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateVisibleTracks();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(search);

        LinearLayout bulkTools = new LinearLayout(this);
        bulkTools.setOrientation(LinearLayout.HORIZONTAL);
        bulkTools.addView(compactButton("보이는 곡 선택", this::selectShown), new LinearLayout.LayoutParams(0, -2, 1f));
        bulkTools.addView(compactButton("선택 해제", this::clearSelection), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(bulkTools);
        LinearLayout cleanupTools = new LinearLayout(this);
        cleanupTools.setOrientation(LinearLayout.HORIZONTAL);
        cleanupTools.addView(compactButton("선택 삭제", this::deleteSelected), new LinearLayout.LayoutParams(0, -2, 1f));
        cleanupTools.addView(compactButton("정렬", this::sortDialog), new LinearLayout.LayoutParams(0, -2, 0.6f));
        cleanupTools.addView(compactButton("중복 검사", this::pruneCatalogDuplicates), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(cleanupTools);

        songList = new ListView(this);
        songList.setDividerHeight(dp(2));
        songList.setBackgroundColor(0xFF19152D);
        songList.setNestedScrollingEnabled(true);
        songAdapter = new BaseAdapter() {
            @Override public int getCount() { return shownTracks.size(); }
            @Override public Object getItem(int pos) { return shownTracks.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View old, ViewGroup parent) {
                Originals.Track t = shownTracks.get(pos);
                LinearLayout row = new LinearLayout(RadioActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(6), dp(3), dp(3), dp(3));
                CheckBox choose = new CheckBox(RadioActivity.this);
                choose.setTextColor(WHITE);
                choose.setTextSize(14);
                choose.setMaxLines(2);
                choose.setEllipsize(android.text.TextUtils.TruncateAt.END);
                choose.setText(t.title + "  · " + RadioProgram.NAMES[RadioProgram.category(RadioActivity.this, t)]);
                choose.setChecked(selected.contains(t.hash));
                choose.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) selected.add(t.hash);
                    else selected.remove(t.hash);
                    updateCount();
                });
                row.addView(choose, new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(compactButton("▶", () -> playTrack(t)),
                        new LinearLayout.LayoutParams(dp(48), dp(50)));
                row.addView(compactButton("⋮", () -> showTrackActions(t)),
                        new LinearLayout.LayoutParams(dp(48), dp(50)));
                return row;
            }
        };
        songList.setAdapter(songAdapter);
        root.addView(songList, new LinearLayout.LayoutParams(-1, dp(430)));
        gap(root, 10);

        card(root, "3  BIGO 오디오 LIVE",
                "BIGO 오디오 LIVE의 음악/믹서에 로컬 파일 선택 기능이 있는 경우 저장한 원곡을 불러오세요.",
                "BIGO LIVE 열기", this::openBigo,
                "송출 음량 확인 방법", this::broadcastGuide);

        root.addView(compactButton("Suno 열기", this::openSuno));
        status = label("준비 완료 · 다운로드 원본 파일은 변경하지 않아요.", 13,
                0xFF9BDEBC, false);
        root.addView(status);
        setContentView(scroll);
        refreshSongs();
        refreshProgramUI();
    }

    private void card(LinearLayout root, String title, String body,
                      String button1, Runnable action1, String button2, Runnable action2) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(17), dp(15), dp(15));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        bg.setCornerRadius(dp(16));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        root.addView(box, lp);
        box.addView(label(title, 19, WHITE, true));
        TextView detail = label(body, 14, 0xFFC6C0D5, false);
        detail.setPadding(0, dp(8), 0, dp(8));
        box.addView(detail);
        box.addView(button(button1, action1));
        if (button2 != null) box.addView(button(button2, action2));
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(3), 0, dp(4));
        return t;
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(15);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private Button compactButton(String text, Runnable action) {
        Button button = button(text, action);
        button.setTextSize(12);
        button.setMinimumHeight(dp(45));
        button.setMinHeight(dp(45));
        button.setPadding(dp(2), 0, dp(2), 0);
        return button;
    }

    private void gap(LinearLayout box, int dp) {
        View v = new View(this);
        box.addView(v, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private void pickFiles() {
        chooseAudio(false);
    }

    private void pickDownloads() {
        chooseAudio(true);
    }

    /** Ask the system picker to start in Download; Android providers may override it. */
    private void chooseAudio(boolean preferDownload) {
        if (importInProgress) {
            say("먼저 진행 중인 음악 가져오기가 끝나야 해요.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if (preferDownload) {
            try {
                Uri folder = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents", "primary:Download");
                i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder);
            } catch (RuntimeException ignored) {
                // Users can navigate manually if their document provider has no Download shortcut.
            }
        }
        try {
            startActivityForResult(i, PICK_AUDIO);
        } catch (RuntimeException ex) {
            say("파일 선택기를 열지 못했습니다. '다른 폴더'를 이용해 주세요.");
        }
    }

    private void openSuno() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://suno.com/")));
        } catch (Exception ex) {
            say("Suno 앱 또는 브라우저를 열 수 없어요.");
        }
    }

    private void openBigo() {
        // Foreground playback continues while switching to BIGO.
        Intent open = getPackageManager().getLaunchIntentForPackage("sg.bigo.live");
        if (open == null) {
            new AlertDialog.Builder(this).setMessage(
                    "BIGO LIVE가 설치되어 있는지 확인하세요. 방송 송출은 BIGO 앱에서 시작합니다.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        try {
            startActivity(open);
        } catch (Exception e) {
            say("BIGO를 열 수 없어요.");
        }
    }

    private void requestBigoStart() {
        new AlertDialog.Builder(this).setTitle("BIGO 오디오 LIVE 시작")
                .setMessage("BIGO 앱을 연 뒤 '오디오 라이브'를 선택하고 "
                        + "'방송하기' 버튼을 직접 눌러 주세요.\n\n"
                        + "보안을 위해 MARU는 접근성 권한을 요청하지 않으며 "
                        + "다른 앱의 버튼을 자동 조작하지 않습니다.")
                .setPositiveButton("BIGO 열기", (d, w) -> openBigo())
                .setNegativeButton("취소", null).show();
    }

    private void requestBigoEnd() {
        new AlertDialog.Builder(this).setTitle("BIGO 라이브 종료")
                .setMessage("BIGO를 열고 현재 방송 화면 오른쪽 위 X를 누른 뒤 "
                        + "'라이브 종료하기'를 직접 선택해 주세요.\n\n"
                        + "음악 타이머 종료는 BIGO 방송을 자동 종료하지 않습니다.")
                .setPositiveButton("BIGO 열기", (d, w) -> openBigo())
                .setNegativeButton("취소", null).show();
    }

    private void broadcastGuide() {
        new AlertDialog.Builder(this)
            .setTitle("원곡 · 방송 음량 확인")
            .setMessage("① 아래 음악 목록에서 'BIGO용 저장'을 눌러 원본 MP3/WAV를 저장합니다.\n\n"
                + "② BIGO 오디오 LIVE를 실행해 도구 → 음악/믹서에서 로컬 음원을 선택할 수 있는지 확인합니다.\n\n"
                + "③ BIGO 내부 음악 입력이 지원될 때 그곳에서 파일을 선택하세요.\n\n"
                + "④ 다른 휴대폰으로 시청하며 방송 볼륨이 유지되는지 검사합니다.\n\n"
                + "주의: Suno 앱에서 바로 재생한 소리를 MARU가 BIGO 마이크로 보내거나 "
                + "휴대폰 볼륨을 0으로 해도 송출을 고정하는 기능은 구현되어 있지 않습니다.")
            .setPositiveButton("확인", null).show();
    }

    private void togglePresenters() {
        boolean next = !RadioProgram.showEnabled(this);
        RadioProgram.setShowEnabled(this, next);
        updatePresenterToggle();
        say(next
                ? "가상 진행자 대화 켜짐. 다음 노래부터 TTS로 소개합니다. 기기 음성 엔진이 없다면 원곡만 이어집니다."
                : "가상 진행자 대화 꺼짐. 다음 노래부터 원곡만 재생합니다.");
    }
    private void updatePresenterToggle() {
        if (presenterToggleButton != null) presenterToggleButton.setText(
                RadioProgram.showEnabled(this)
                        ? "🎙 진행자 대화 켜짐 (누르면 끄기)"
                        : "🎙 진행자 대화 꺼짐 (누르면 켜기)");
    }

    private void selectProgramCategory() {
        int current = RadioProgram.active(this);
        new AlertDialog.Builder(this)
                .setTitle("방송할 노래 분류")
                .setSingleChoiceItems(RadioProgram.NAMES, current, (dialog, choice) -> {
                    if (radioService != null && radioService.isPlaying()) {
                        say("재생 중에는 방송 분류가 다음 곡부터 반영됩니다.");
                    }
                    RadioProgram.select(this, choice);
                    updateVisibleTracks();
                    refreshProgramUI();
                    dialog.dismiss();
                })
                .setNegativeButton("닫기", null).show();
    }

    private void classifyTrack(Originals.Track track) {
        new AlertDialog.Builder(this)
                .setTitle("이 노래의 분류 · " + track.title)
                .setSingleChoiceItems(new String[]{"한국노래", "중국노래", "서양노래", "연주곡"},
                        Math.max(0, RadioProgram.category(this, track) - 1), (dialog, choice) -> {
                            RadioProgram.classify(this, track, choice + 1);
                            updateVisibleTracks();
                            dialog.dismiss();
                            say("곡 분류 저장: " + RadioProgram.NAMES[choice + 1]);
                        })
                .setNegativeButton("취소", null).show();
    }

    private void refreshProgramUI() {
        if (programCategoryButton != null) {
            programCategoryButton.setText("🎼 " + RadioProgram.NAMES[RadioProgram.active(this)] + " ▾");
        }
        if (programSummary != null) {
            int minutes = RadioProgram.durationMinutes(this);
            long remaining = radioService == null ? 0 : radioService.timerRemainingMs();
            programSummary.setText("시작: " + RadioSchedule.formatted(this)
                    + "\n종료: " + RadioSchedule.endFormatted(this)
                    + "\n" + (minutes == 0 ? "분 타이머 사용 안 함" : "선택한 분 타이머 " + minutes + "분") + " · "
                    + (remaining <= 0 ? "타이머 중지" :
                    "자동 재생 종료까지 " + (remaining / 60000) + "분 "
                            + ((remaining / 1000) % 60) + "초")
                    + " · BIGO 라이브 종료는 직접 조작");
        }
    }

    private void configureProgramTimer() {
        int currentMinutes = RadioProgram.durationMinutes(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(10), dp(24), dp(4));
        content.addView(label("재생 중인 원곡 음악을 종료할 시간을 정하세요. "
                + "BIGO 라이브 자체는 종료되지 않으므로 BIGO에서 직접 끝내야 해요.",
                14, Color.DKGRAY, false));
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(5);
        picker.setMaxValue(720);
        picker.setValue(Math.max(5, currentMinutes));
        picker.setWrapSelectorWheel(false);
        content.addView(picker);
        new AlertDialog.Builder(this)
                .setTitle("음악 자동 종료 시간")
                .setView(content)
                .setPositiveButton("타이머 시작", (dialog, which) -> {
                    int chosen = picker.getValue();
                    RadioProgram.setDurationMinutes(this, chosen);
                    if (radioService == null || !radioService.isPlaying()) {
                        say("시간은 저장했어. 먼저 ▶ 재생을 누르고 시간 설정을 다시 눌러줘.");
                        refreshProgramUI();
                        return;
                    }
                    Intent timer = new Intent(this, RadioPlaybackService.class)
                            .setAction(RadioPlaybackService.ACTION_SET_TIMER)
                            .putExtra(RadioPlaybackService.EXTRA_MINUTES, chosen);
                    startService(timer);
                    say(chosen + "분 뒤 MARU 음악만 자동 종료돼. BIGO는 직접 종료해 줘.");
                    refreshProgramUI();
                })
                .setNegativeButton("취소", null).show();
    }

    private void cancelProgramTimer() {
        RadioProgram.setDurationMinutes(this, 0);
        startService(new Intent(this, RadioPlaybackService.class)
                .setAction(RadioPlaybackService.ACTION_CANCEL_TIMER));
        say("음악 종료 타이머 취소 · 음악 재생은 유지합니다.");
        refreshProgramUI();
    }

    private void refreshSongs() {
        if (songList == null) return;
        allTracks.clear();
        allTracks.addAll(Originals.list(this));
        updateVisibleTracks();
    }

    private void updateCount() {
        if (songCount == null) return;
        songCount.setText("보관 " + allTracks.size() + "곡 · 표시 "
                + shownTracks.size() + "곡 · 선택 " + selected.size() + "곡");
    }

    private void updateVisibleTracks() {
        shownTracks.clear();
        List<Originals.Track> sorted = new ArrayList<>(allTracks);
        if (sortMode == 0) Collections.reverse(sorted);
        else if (sortMode == 1) Collections.sort(sorted,
                (a, b) -> a.title.compareToIgnoreCase(b.title));
        Map<String, Integer> nameCounts = new HashMap<>();
        for (Originals.Track t : allTracks) {
            String normalized = t.title.trim().toLowerCase(Locale.ROOT);
            nameCounts.put(normalized, nameCounts.containsKey(normalized)
                    ? nameCounts.get(normalized) + 1 : 1);
        }
        String query = search == null ? "" :
                search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int activeCategory = RadioProgram.active(this);
        for (Originals.Track t : sorted) {
            if (activeCategory != 0 && activeCategory != 5
                    && RadioProgram.category(this, t) != activeCategory) continue;
            if (!t.title.toLowerCase(Locale.ROOT).contains(query)) continue;
            String normalized = t.title.trim().toLowerCase(Locale.ROOT);
            if (sameTitleOnly && nameCounts.get(normalized) < 2) continue;
            shownTracks.add(t);
        }
        if (songAdapter != null) songAdapter.notifyDataSetChanged();
        updateCount();
    }

    private void sortDialog() {
        new AlertDialog.Builder(this)
                .setTitle("노래 정렬")
                .setSingleChoiceItems(new String[]{"최근 추가순", "제목순", "오래된 추가순"},
                        sortMode, (d, index) -> {
                            sortMode = index;
                            d.dismiss();
                            updateVisibleTracks();
                        })
                .setNegativeButton("닫기", null).show();
    }

    private void toggleSameTitleFilter() {
        sameTitleOnly = !sameTitleOnly;
        updateVisibleTracks();
        say(sameTitleOnly
                ? "같은 제목의 후보만 표시 중 · 다른 편곡이나 버전일 수 있으니 직접 확인하세요."
                : "모든 원곡 표시 중 · 내용이 완전히 같은 파일은 자동 제외됩니다.");
    }

    private void selectShown() {
        for (Originals.Track t : shownTracks) selected.add(t.hash);
        if (songAdapter != null) songAdapter.notifyDataSetChanged();
        updateCount();
    }

    private void clearSelection() {
        selected.clear();
        if (songAdapter != null) songAdapter.notifyDataSetChanged();
        updateCount();
    }

    private void deleteSelected() {
        Set<String> targets = new HashSet<>(selected);
        if (targets.isEmpty()) {
            say("삭제할 노래를 먼저 체크해 주세요.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("선택한 " + targets.size() + "곡 삭제")
            .setMessage("MARU RADIO 목록에서 " + targets.size() + "곡을 제거할까요?\n\n"
                    + "Download와 Suno 원본 파일은 그대로 보존합니다.")
            .setPositiveButton("선택 삭제", (dialog, which) -> {
                if (radioService != null && targets.contains(radioService.currentHash())) {
                    playerAction(RadioPlaybackService.ACTION_STOP);
                }
                say("선택한 " + targets.size() + "곡 목록 정리 중…");
                files.execute(() -> {
                    int removed = Originals.removeMany(getApplicationContext(), targets);
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        selected.clear();
                        refreshSongs();
                        say("MARU 목록에서 " + removed + "곡 제거 · Download 원본 유지");
                    });
                });
            })
            .setNegativeButton("취소", null)
            .show();
    }

    private void pruneCatalogDuplicates() {
        files.execute(() -> {
            int removed = Originals.pruneDuplicateCatalogEntries(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                refreshSongs();
                say(removed == 0
                        ? "동일 파일 중복 0곡 · 가져오기 때 SHA-256으로 자동 제외하고 있어요."
                        : "중복 목록 " + removed + "건 정리 완료 · 원곡 다운로드 파일은 유지");
            });
        });
    }

    private void showTrackActions(Originals.Track track) {
        new AlertDialog.Builder(this).setTitle(track.title)
                .setItems(new String[]{"원곡 재생", "BIGO용 저장", "곡 분류 변경", "이 곡 삭제"}, (dialog, which) -> {
                    if (which == 0) playTrack(track);
                    else if (which == 1) export(track);
                    else if (which == 2) classifyTrack(track);
                    else confirmDelete(track);
                })
                .setNegativeButton("닫기", null).show();
    }

    private void playTrack(Originals.Track track) {
        if (track == null) { say("원곡을 먼저 가져와 주세요."); return; }
        Intent i = new Intent(this, RadioPlaybackService.class)
                .setAction(RadioPlaybackService.ACTION_PLAY)
                .putExtra(RadioPlaybackService.EXTRA_HASH, track.hash);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            say("선택 원곡 재생: " + track.title);
            if (playerTitle != null) playerTitle.setText(track.title);
        } catch (RuntimeException error) {
            say("음악 재생을 시작하지 못했어요. 앱을 다시 열고 재생해 주세요.");
        }
    }

    private void playerAction(String action) {
        if (RadioPlaybackService.ACTION_NEXT.equals(action)
                || RadioPlaybackService.ACTION_PREVIOUS.equals(action)) {
            if (radioService == null || radioService.currentHash().isEmpty()) {
                if (!shownTracks.isEmpty()) playTrack(shownTracks.get(0));
                else say("먼저 원곡을 가져와 주세요.");
                return;
            }
        }
        Intent intent = new Intent(this, RadioPlaybackService.class).setAction(action);
        try {
            startService(intent);
        } catch (RuntimeException error) {
            say("재생 조작을 수행하지 못했어요.");
        }
        updatePlayerStatus();
    }

    private void togglePlayer() {
        if (radioService != null && !radioService.currentHash().isEmpty()) {
            playerAction(RadioPlaybackService.ACTION_TOGGLE);
        } else if (!shownTracks.isEmpty()) {
            playTrack(shownTracks.get(0));
        } else {
            say("원곡을 먼저 가져와 주세요.");
        }
    }

    private static String minutes(int ms) {
        int sec = Math.max(0, ms / 1000);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", sec / 60, sec % 60);
    }

    private void updatePlayerStatus() {
        refreshProgramUI();
        if (playerTitle == null || playerToggleButton == null) return;
        if (radioService == null) {
            playerToggleButton.setText("▶ 재생");
            if (playerTitle.getText().length() == 0)
                playerTitle.setText("목록에서 음악을 선택하세요.");
            return;
        }
        String title = radioService.currentTitle();
        boolean playing = radioService.isPlaying();
        playerTitle.setText((radioService.isLoading() ? "불러오는 중 · "
                : playing ? "재생 중 · " : "일시정지 · ") + title);
        playerToggleButton.setText(playing ? "⏸ 일시정지" : "▶ 재생");
        int duration = radioService.durationMs();
        if (!playerSeeking && playerSeek != null) {
            playerSeek.setMax(Math.max(duration, 1000));
            playerSeek.setProgress(radioService.positionMs());
        }
        if (playerTime != null) playerTime.setText(
                minutes(radioService.positionMs()) + " / " + minutes(duration));
    }

    private void export(Originals.Track t) {
        exporting = t;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String name = Originals.exportFilename(t);
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        String mime = lower.endsWith(".wav") ? "audio/wav" :
                lower.endsWith(".flac") ? "audio/flac" :
                lower.endsWith(".m4a") ? "audio/mp4" :
                lower.endsWith(".ogg") ? "audio/ogg" :
                "audio/mpeg";
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, EXPORT_AUDIO);
    }

    private void confirmDelete(Originals.Track t) {
        new AlertDialog.Builder(this).setTitle("MARU 보관곡 삭제")
            .setMessage(t.title + "\n\nMARU 목록에서 제외합니다. Download와 Suno 원곡은 삭제되지 않습니다.")
            .setPositiveButton("삭제", (d, w) -> {
                if (radioService != null && t.hash.equals(radioService.currentHash())) {
                    playerAction(RadioPlaybackService.ACTION_STOP);
                }
                Originals.remove(this, t);
                refreshSongs();
                say("MARU 보관 목록에서 삭제했어요.");
            }).setNegativeButton("취소", null).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_AUDIO) {
            List<Uri> chosen = new ArrayList<>();
            if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                for (int n = 0; n < clip.getItemCount(); n++)
                    if (clip.getItemAt(n).getUri() != null)
                        chosen.add(clip.getItemAt(n).getUri());
            } else if (data.getData() != null) chosen.add(data.getData());
            importFiles(chosen, true);
        } else if (requestCode == EXPORT_AUDIO && exporting != null && data.getData() != null) {
            Originals.Track track = exporting;
            exporting = null;
            Uri destination = data.getData();
            say("선택한 폴더에 원곡 저장 중…");
            files.execute(() -> {
                String message = "";
                try (InputStream in = track.isDownloadReference()
                            ? getContentResolver().openInputStream(track.sourceUri())
                            : new FileInputStream(track.file());
                     OutputStream out = getContentResolver().openOutputStream(destination)) {
                    if (in == null) throw new Exception("원곡 읽기 권한이 만료됐어요.");
                    if (out == null) throw new Exception("저장할 수 없는 위치예요.");
                    byte[] buf = new byte[32 * 1024];
                    int count;
                    while ((count = in.read(buf)) != -1) out.write(buf, 0, count);
                    out.flush();
                } catch (Exception e) {
                    message = "파일을 저장하지 못했어요: " + e.getMessage();
                }
                final String error = message;
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (error.isEmpty()) {
                        new AlertDialog.Builder(this).setTitle("원곡 저장 완료")
                            .setMessage("원본 파일을 그대로 저장했습니다.\nBIGO에 로컬 음악 불러오기가 보이면 이 곡을 선택하세요.")
                            .setPositiveButton("BIGO 열기", (d, w) -> openBigo())
                            .setNegativeButton("닫기", null).show();
                    } else say(error);
                });
            });
        }
    }

    private void acceptShare(Intent intent) {
        if (intent == null) return;
        List<Uri> shared = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri single = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (single != null) shared.add(single);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (many != null) shared.addAll(many);
        }
        if (!shared.isEmpty()) importFiles(shared, false);
    }

    private void importFiles(List<Uri> items, boolean fromDocumentPicker) {
        if (items.isEmpty()) {
            say("오디오 파일을 선택해 주세요. Suno 공유 링크만으로는 가져올 수 없어요.");
            return;
        }
        if (importInProgress) {
            say("이미 원곡을 가져오는 중이에요. 완료될 때까지 기다려 주세요.");
            return;
        }
        importInProgress = true;
        say("총 " + items.size() + "곡 확인 시작 · 다운로드 원본은 그대로 유지");
        files.execute(() -> {
            Originals.BatchResult result = Originals.importBatch(
                    getApplicationContext(), items, fromDocumentPicker, (done, total) -> {
                        if (done == total || done % 5 == 0) {
                            runOnUiThread(() -> {
                                if (!isFinishing()) say("원곡 확인 " + done + " / " + total + "곡");
                            });
                        }
                    });
            runOnUiThread(() -> {
                importInProgress = false;
                if (isFinishing()) return;
                refreshSongs();
                say("선택 " + items.size() + "곡 · 추가 " + result.added
                        + "곡 · 동일 파일 제외 " + result.duplicates
                        + "곡 · 오류 " + result.failed + "곡"
                        + (result.failed == 0 ? "" : " · " + result.lastError));
            });
        });
    }

    private void say(String message) {
        if (status != null) status.setText(message);
    }

    @Override protected void onStart() {
        super.onStart();
        try {
            playerBindRequested = bindService(
                    new Intent(this, RadioPlaybackService.class),
                    playerConnection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException e) {
            playerBindRequested = false;
            say("음악 재생 서비스에 연결할 수 없어요.");
        }
        playerHandler.removeCallbacks(playerRefresh);
        playerHandler.post(playerRefresh);
    }

    @Override protected void onStop() {
        playerHandler.removeCallbacks(playerRefresh);
        if (playerBindRequested) {
            try { unbindService(playerConnection); }
            catch (IllegalArgumentException ignored) {}
            playerBindRequested = false;
        }
        radioService = null;
        playerBound = false;
        super.onStop();
        // Do NOT stop foreground music; the user may open BIGO or another app.
    }

    @Override protected void onDestroy() {
        playerHandler.removeCallbacks(playerRefresh);
        files.shutdown();
        super.onDestroy();
    }
}
