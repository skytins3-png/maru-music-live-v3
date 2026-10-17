package com.maru.radio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Evidence-oriented, no-key BIGO Audio LIVE test screen.
 * This activity cannot capture BIGO receiver output and does not fabricate it.
 * The owner marks second-device observations; defaults are always unchecked.
 */
public final class RadioBigoNoKeyTestActivity extends Activity {
    private final int white = 0xFFF6F1FF;
    private final int purple = 0xFFBAA0FF;
    private CheckBox selected, normal, muted, comment, voice;
    private TextView report, chatStatus;

    private int dp(int n) {
        return (int)(getResources().getDisplayMetrics().density * n + .5f);
    }
    private TextView label(String words, int size, int color) {
        TextView view = new TextView(this);
        view.setText(words);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(dp(3), dp(10), dp(3), dp(10));
        return view;
    }
    private void addButton(LinearLayout root, String text, Runnable run) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(15f);
        b.setText(text);
        b.setOnClickListener(v -> run.run());
        root.addView(b, new LinearLayout.LayoutParams(-1, dp(58)));
    }
    private CheckBox addCheck(LinearLayout root, String title) {
        CheckBox c = new CheckBox(this);
        c.setTextColor(white);
        c.setTextSize(15);
        c.setText(title);
        c.setChecked(false); // no unsupported success remembered or auto-assumed
        c.setPadding(dp(3), dp(10), dp(3), dp(10));
        c.setOnCheckedChangeListener((b, isChecked) -> update());
        root.addView(c);
        return c;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF120F22);
        if (Build.VERSION.SDK_INT >= 35)
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(17), dp(15), dp(17), dp(36));
        scroll.addView(root);
        root.addView(label("🧪 BIGO 오디오 LIVE · 스트림 키 없는 실방송 검증", 22, purple));
        root.addView(label("MARU 음악 재생 성공은 BIGO 방송 송출 성공과 다릅니다. "
                + "이 페이지는 가상 테스트 결과를 방송 성공이라고 표시하지 않습니다. "
                + "반드시 다른 휴대폰에서 내 BIGO 방송을 실제로 들어보고 항목을 체크하세요.", 14, white));
        root.addView(label("1. BIGO 자체 믹서에서 원곡을 재생할 수 있는지", 18, purple));
        selected = addCheck(root, "BIGO 내부 음악/BGM 선택에서 내 Suno 원곡을 직접 선택함");
        normal = addCheck(root, "다른 청취자 휴대폰에서 음악이 정상 음량으로 들림");
        muted = addCheck(root, "방송 휴대폰의 미디어 음량 0에서도 청취자에게 동일한 음악이 들림");

        root.addView(label("2. 새 BIGO 댓글 → 아환 음성답변", 18, purple));
        comment = addCheck(root, "방송 중 새 댓글이 아환 상태 화면에서 인식된 것을 확인함");
        voice = addCheck(root, "아환이 답한 소리가 다른 청취자 휴대폰에도 실제 들림");
        chatStatus = label("", 13, white);
        root.addView(chatStatus);
        report = label("", 15, 0xFFB0E4C5);
        root.addView(report);
        addButton(root, "🔎 청취자 기준으로 결과 다시 판정", this::update);
        addButton(root, "🎙 아환 댓글 감지 상태·동의 확인", () ->
                startActivity(new Intent(this, RadioDjActivity.class)));
        addButton(root, "🎵 BIGO 자체 음악 목록용 원곡 Music 폴더 준비", () ->
                startActivity(new Intent(this, RadioBigoMusicPrepActivity.class)));
        addButton(root, "📱 BIGO 오디오 LIVE 열기 (키 불필요)", () ->
                BigoBroadcastGuide.openBigo(this, false));
        addButton(root, "🎵 전체 원곡 목록 열기", () ->
                startActivity(new Intent(this, RadioAllSongsActivity.class)));
        addButton(root, "← MARU RADIO 돌아가기", this::finish);
        setContentView(scroll);
        update();
    }
    @Override protected void onResume() {
        super.onResume();
        if (report != null) update();
    }
    private boolean isOn(CheckBox c) { return c != null && c.isChecked(); }
    private void update() {
        if (report == null) return;
        BigoNoKeyEvidence.Snapshot x = new BigoNoKeyEvidence.Snapshot(
                isOn(selected), isOn(normal), isOn(muted), isOn(comment), isOn(voice));
        String outcome = BigoNoKeyEvidence.complete(x)
                ? "청취자 기준 2개 항목 모두 사용자가 확인함"
                : "미완료 · 실제 청취자 검증 또는 BIGO 입력 경로 확인 필요";
        report.setText("음악: " + BigoNoKeyEvidence.musicStatus(x)
                + "\n댓글: " + BigoNoKeyEvidence.commentStatus(x)
                + "\n판정: " + outcome
                + "\n※ 체크는 사용자 관찰 기록이며 자동 증명이나 서버 검사가 아닙니다.");
        if (chatStatus != null)
            chatStatus.setText("아환 자동댓글 동의 "
                    + (BigoControlAssist.commentReplies(this) ? "ON" : "OFF")
                    + " / 접근성 서비스 "
                    + (BigoControlAssist.connected(this) ? "연결" : "미연결")
                    + "\n" + BigoControlAssist.chatState(this)
                    + "\nBIGO는 MARU TTS를 마이크 입력으로 자동 받아주는 것이 아닙니다.");
    }
}
