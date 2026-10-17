package com.maru.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Expose imported originals to Android's shared Music library only after a
 * direct owner confirmation. Does not operate BIGO, enable stream keys, or
 * bypass Android volume/mic policies.
 */
public final class RadioBigoMusicPrepActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private boolean busy;
    private TextView progress;
    private Button copyButton;
    private Button cancelButton;

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String txt, int size) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextSize(size);
        t.setTextColor(0xFFF5EDFF);
        t.setPadding(dp(3), dp(12), dp(3), dp(12));
        return t;
    }
    private void addButton(LinearLayout parent, String title, Runnable work) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setText(title);
        b.setOnClickListener(v -> work.run());
        parent.addView(b);
        if (title.startsWith("📥")) copyButton = b;
        if (title.startsWith("■")) cancelButton = b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF120F22);
        if (Build.VERSION.SDK_INT >= 35)
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets in = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                v.setPadding(0, in.top, 0, in.bottom);
                return insets;
            });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(42));
        scroll.addView(root);
        root.addView(label("🎵 BIGO 자체 믹서용 원곡 준비", 24));
        root.addView(label("스트림 키 없이 가능한 음악 경로를 확인합니다. "
                + "기존 Suno MP3/WAV 등 원곡을 그대로 Music/MARU RADIO 폴더에 복사해 "
                + "다른 음악 앱이 발견할 수 있게 합니다. 원곡을 변환·압축·삭제하지 않습니다.", 15));
        root.addView(label("⚠ 이 기능은 노래 파일 공유만 합니다. BIGO가 해당 폴더를 "
                + "내 음악/BGM에 표시하는지, 방송 폰을 음소거해도 청취자가 듣는지는 "
                + "BIGO에서 실제 확인해야 합니다. 아환 댓글 TTS도 BIGO 마이크에 자동 연결되지 않습니다.", 14));
        progress = label("보관된 원곡 " + Originals.list(this).size()
                + "곡 · 복사 시작 전 (추가 저장 공간 필요)", 16);
        root.addView(progress);
        addButton(root, "📥 원곡 전체를 BIGO용 Music 폴더에 복사", this::askCopy);
        addButton(root, "■ 복사 중단 (진행 중인 파일은 폐기)", () -> {
            if (busy) {
                stop.set(true);
                progress.setText("복사 중단 요청 · 현재 파일을 정리하는 중입니다.");
            }
        });
        addButton(root, "▶ BIGO 오디오 LIVE 열기", () ->
                BigoBroadcastGuide.openBigo(this, false));
        addButton(root, "← MARU RADIO", this::finish);
        setContentView(scroll);
        refreshButtons();
    }
    private void refreshButtons() {
        if (copyButton != null) copyButton.setEnabled(!busy && Build.VERSION.SDK_INT >= 29);
        if (cancelButton != null) cancelButton.setEnabled(busy);
    }
    private void askCopy() {
        if (busy) return;
        List<Originals.Track> songs = Originals.list(this);
        if (songs.isEmpty()) {
            progress.setText("보관곡이 없습니다. 먼저 Suno 원곡을 MARU로 가져와 주세요.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("원곡 " + songs.size() + "곡 Music 폴더로 공유")
                .setMessage("다운로드 원곡과 등록 정보를 유지한 채 Music/MARU RADIO에 "
                        + "복사본을 만듭니다. 다른 음악 앱에서 고를 수 있는 경로지만, "
                        + "BIGO 믹서에 표시되는지는 BIGO 버전에 따라 다릅니다. "
                        + "152곡은 저장 공간을 많이 사용할 수 있습니다. "
                        + "같은 원곡은 파일 내용 해시 기준으로 재복사하지 않습니다. 계속할까요?")
                .setPositiveButton("원곡 복사 시작", (dialog, which) -> copy(songs))
                .setNegativeButton("취소", null)
                .show();
    }
    private void copy(List<Originals.Track> songs) {
        if (busy) return;
        stop.set(false);
        busy = true;
        refreshButtons();
        progress.setText("공유 Music 폴더로 원곡 복사 중...");
        worker.execute(() -> {
            RadioBigoMediaExporter.Report done = RadioBigoMediaExporter.copyOriginals(
                    getApplicationContext(), songs, stop, (index, total, title, report) ->
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed())
                            progress.setText("처리 " + index + "/" + total
                                    + " · 성공 " + report.copied
                                    + " · 이전에 복사됨 " + report.alreadyAvailable
                                    + " · 실패 " + report.failed);
                    }));
            runOnUiThread(() -> {
                busy = false;
                refreshButtons();
                if (isFinishing() || isDestroyed()) return;
                progress.setText("완료 · 새 파일 " + done.copied
                        + "곡 / 이미 준비됨 " + done.alreadyAvailable
                        + "곡 / 실패 " + done.failed + "곡"
                        + (done.cancelled ? " · 사용자가 복사 중단" : "")
                        + (done.error.isEmpty() ? "" : "\n" + done.error)
                        + "\n파일 위치: 내 파일 → 내장 저장공간 → Music → MARU RADIO"
                        + "\n다음: BIGO 자체 믹서에서 '내 음악/로컬 음악'이 있는지 확인."
                        + "\n※ 파일 복사 성공 ≠ BIGO 라이브 음성 송출 성공");
            });
        });
    }
    @Override protected void onDestroy() {
        stop.set(true);
        worker.shutdown();
        super.onDestroy();
    }
}
