package com.maru.radio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Complete, stable function index for MARU RADIO.
 *
 * This page is navigation only: none of the existing broadcaster, original-song
 * catalog, DJ controls or settings are replaced, hidden or deleted.
 */
public final class RadioFeatureMenuActivity extends Activity {
    private static final int BG = 0xFF110E21;
    private static final int WHITE = 0xFFF7F1FF;
    private static final int PURPLE = 0xFFBAA0FF;

    private int dp(int size) {
        return (int) (size * getResources().getDisplayMetrics().density + 0.5f);
    }
    private TextView label(String words, int size, int color) {
        TextView view = new TextView(this);
        view.setText(words);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(dp(6), dp(6), dp(6), dp(7));
        return view;
    }
    private void heading(LinearLayout root, String words) {
        TextView title = label(words, 19, PURPLE);
        title.setPadding(dp(6), dp(15), dp(6), dp(4));
        root.addView(title);
    }
    private void item(LinearLayout root, String words, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(words);
        button.setTextSize(15);
        button.setMinHeight(dp(52));
        button.setOnClickListener(v -> action.run());
        root.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
    private void go(Class<? extends Activity> target) {
        startActivity(new Intent(this, target));
    }
    private void mainAction(String action) {
        // SingleTask-like handoff to the already opened main UI, with a *named*
        // explicit action; Android never auto-starts live broadcasting here.
        Intent intent = new Intent(this, RadioActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(RadioActivity.EXTRA_MENU_ACTION, action);
        startActivity(intent);
        finish();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                view.setPadding(0, safe.top, 0, safe.bottom);
                return insets;
            });
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(36));
        scroll.addView(root);
        root.addView(label("☰ MARU RADIO · 전체 기능 목록", 23, PURPLE));
        root.addView(label("원곡·아환·방송·예약·관리 기능을 빠짐없이 모았습니다. " +
                "여기에 보이는 기능은 기존 설정 화면으로 연결됩니다.", 14, WHITE));

        heading(root, "📋 1. 보관 음악 · 재생");
        item(root, "🎼 전체 보관곡 목록 · 모든 장르 · 검색·정렬", () -> go(RadioAllSongsActivity.class));
        item(root, "▶ 원곡 플레이어 · 이전·다음·일시정지", () -> mainAction("player"));
        item(root, "📥 Download에서 여러 곡 가져오기", () -> mainAction("download"));
        item(root, "📂 다른 폴더에서 음악 가져오기", () -> mainAction("folder"));
        item(root, "🗑 원곡 선택 삭제 · 같은 제목 확인 · 중복 검사", () -> mainAction("song_manage"));
        item(root, "🔊 음악 음량 균일화", () -> mainAction("level"));

        heading(root, "📻 2. 방송 편성 · 자동 예약");
        item(root, "🇰🇷🇨🇳🌍🎹 한국·중국·서양·연주·4장르 자동 편성",
                () -> mainAction("genre"));
        item(root, "⏱ 음악 종료 타이머 설정", () -> mainAction("timer"));
        item(root, "⏹ 음악 종료 타이머 취소", () -> mainAction("cancel_timer"));
        item(root, "📅 음악 시작·종료 예약 · BIGO 실험 자동화",
                () -> go(RadioAutomationActivity.class));
        item(root, "🔀 BIGO와 MARU 동시 재생 설정 (실험)",
                () -> mainAction("program"));

        heading(root, "🎙 3. AI 아나운서 아환 (기존 기능 전체)");
        item(root, "🎤 휴대폰 마이크로 인사 직접 녹음 · 방송 멘트 등록",
                () -> go(RadioAhwanRecorderActivity.class));
        item(root, "🎙 아환 전체 설정 · TTS·오프닝·곡 소개·언어",
                () -> go(RadioDjActivity.class));
        item(root, "👋 방송 시작·종료 인사 · 선물·팔로우 감사",
                () -> go(RadioDjActivity.class));
        item(root, "🎵 다음 곡 소개 · 신청곡 검색·재생·신청 순위",
                () -> go(RadioDjActivity.class));
        item(root, "🧪 수동 댓글·말로 입력·멘트 복사 (접힌 테스트 메뉴)",
                () -> go(RadioDjActivity.class));
        item(root, "🎤 녹음 인사 등록·미리듣기·해제",
                () -> go(RadioDjActivity.class));
        item(root, "🧠 아환 학습 답변 지정·삭제 (최대 20개)",
                () -> go(RadioDjActivity.class));
        item(root, "🔎 BIGO 댓글 연결·청취자 소리 확인",
                () -> go(RadioDjActivity.class));
        root.addView(label("현재 아환은 Android TTS/규칙 기반입니다. " +
                "실제 BIGO 청취자 음성·댓글 자동 처리 여부는 검증이 필요합니다.", 12, WHITE));

        heading(root, "📱 4. 스트림 키 없는 BIGO 오디오 LIVE (형의 방송 방식)");
        item(root, "🧪 음소거에도 청취자 음악 일정? · 아환 답변 실제 수신 검증",
                () -> go(RadioBigoNoKeyTestActivity.class));
        root.addView(label("BIGO 내부 음악/BGM 경로 확인부터 진행합니다. "
                + "다른 휴대폰에서 직접 확인하기 전에는 성공으로 표시하지 않습니다.", 12, WHITE));

        heading(root, "📡 5. BIGO 기존 안내 · 선택적 RTMP");
        item(root, "▶ BIGO 오디오 LIVE 시작 안내", () -> mainAction("bigo_start"));
        item(root, "■ BIGO 오디오 LIVE 종료 안내", () -> mainAction("bigo_end"));
        item(root, "📱 BIGO LIVE 앱 직접 열기", () -> mainAction("bigo_open"));
        item(root, "📡 휴대폰 볼륨과 독립적인 RTMP 음악 송출 (실험)",
                () -> go(RadioDirectRtmpActivity.class));
        item(root, "🔊 BIGO 방송 소리 확인 방법", () -> mainAction("sound_test"));

        heading(root, "🔗 5. 기타");
        item(root, "☀ Suno 열기", () -> mainAction("suno"));
        item(root, "← MARU RADIO 메인으로", this::finish);
        setContentView(scroll);
    }
}
