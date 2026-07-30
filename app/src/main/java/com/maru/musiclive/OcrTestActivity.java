package com.maru.musiclive;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

public final class OcrTestActivity extends ComponentActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private long startedAt;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            long detectedAt = AutoGreetingStore.lastTime(OcrTestActivity.this);
            if (detectedAt >= startedAt) {
                status.setText("감지 성공\n휴대폰 TTS 자동 인사 재생 확인");
                status.setTextColor(Color.rgb(120, 255, 150));
                return;
            }
            if (System.currentTimeMillis() - startedAt >= 20_000L) {
                status.setText("감지 실패\n입장 감지 서비스를 중지했습니다.");
                status.setTextColor(Color.rgb(255, 150, 120));
                AutoGreetingStore.setStatus(
                        OcrTestActivity.this,
                        "로컬 OCR 테스트 20초 안에 감지되지 않음");
                ScreenOcrGreetingService.stop(OcrTestActivity.this);
                return;
            }
            status.setText("OCR 감지 중…\n최대 20초 기다리세요.");
            handler.postDelayed(this, 500L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startedAt = System.currentTimeMillis();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(40), dp(24), dp(40));
        root.setBackgroundColor(Color.rgb(18, 24, 34));

        TextView title = label("로컬 OCR 자동인사 테스트", 25, true);
        root.addView(title);

        TextView join = label("테스트청취자 님이 입장했습니다", 31, true);
        join.setTextColor(Color.WHITE);
        join.setBackgroundColor(Color.rgb(20, 94, 45));
        join.setPadding(dp(16), dp(28), dp(16), dp(28));
        root.addView(join, matchWrap());

        TextView note = label(
                "이 문구를 화면 OCR이 감지하면\n선택한 언어의 휴대폰 TTS 인사가 자동으로 재생됩니다.\nBIGO LIVE는 실행되지 않습니다.",
                18, false);
        note.setGravity(Gravity.CENTER);
        root.addView(note, matchWrap());

        status = label("OCR 감지 준비 중…", 20, true);
        status.setGravity(Gravity.CENTER);
        root.addView(status, matchWrap());

        Button close = new Button(this);
        close.setText("테스트 종료");
        close.setAllCaps(false);
        close.setOnClickListener(v -> {
            ScreenOcrGreetingService.stop(this);
            finish();
        });
        root.addView(close, matchWrap());
        setContentView(root);
        handler.postDelayed(poll, 800L);
    }

    private TextView label(String value, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.LTGRAY);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(14), 0, dp(14));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, dp(10));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
