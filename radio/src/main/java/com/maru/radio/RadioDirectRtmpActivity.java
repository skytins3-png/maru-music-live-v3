package com.maru.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * BIGO official OBS/RTMP route requires an account-provided server and key.
 * Keys are only ever passed in-process to the local foreground service:
 * never saved, logged, pasted into GitHub, exported, or sent to ChatGPT.
 */
public final class RadioDirectRtmpActivity extends Activity {
    private EditText server, key;
    private TextView state;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        @Override public void run() {
            if (state != null) state.setText(RadioDirectRtmpService.status());
            handler.postDelayed(this, 2000L);
        }
    };
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(0xFFF5F1FF);
        t.setTextSize(size); t.setPadding(0, dp(9), 0, dp(9));
        return t;
    }
    private Button button(String name, Runnable fn) {
        Button b = new Button(this);
        b.setText(name); b.setAllCaps(false);
        b.setOnClickListener(v -> fn.run()); return b;
    }
    private EditText input(String hint, boolean secret) {
        EditText v = new EditText(this);
        v.setTextColor(0xFFFFFFFF); v.setHintTextColor(0xFFBDB7CC);
        v.setHint(hint);
        v.setInputType(secret
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        v.setSingleLine(true); return v;
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF161228);
        LinearLayout body = new LinearLayout(this);
        body.setPadding(dp(18), dp(32), dp(18), dp(35));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);

        body.addView(text("📡 MARU 독립 음악 송출 · RTMP", 23));
        body.addView(text("휴대폰 볼륨을 0으로 내려도 음악 PCM을 "
                + "스피커가 아닌 네트워크로 직접 보내는 별도 방식입니다. "
                + "카메라·마이크 없이 정지 표지 + Suno 음악을 송출합니다.", 15));
        body.addView(text("방송 PCM 음량은 원곡 레벨 그대로 고정합니다. "
                + "자동 증폭/정규화/음량 보정은 하지 않습니다. "
                + "휴대폰 미디어 음량을 0으로 해도 RTMP 음악 전송 값은 달라지지 않아요. "
                + "아환이 말하는 짧은 구간에는 멘트를 듣기 쉽게 음악이 잠시 낮아집니다. "
                + "실제 BIGO 청취가 되는지는 계정 RTMP 권한과 별도 기기 검증이 필요합니다.", 13));
        body.addView(text("중요: 일반 BIGO 오디오 LIVE의 자동 믹서 연결이 아닙니다. "
                + "형의 BIGO 계정에 OBS 스트리밍(서버 주소·스트림키)이 "
                + "실제로 허용되어 있을 때만 이용할 수 있어요. "
                + "키는 이 휴대폰 앱에만 입력하고 절대 채팅으로 보내지 마세요.", 13));
        body.addView(text("BIGO 공식 안내: bigo.tv/ko/bigo-streamer/obs/", 13));

        server = input("BIGO가 발급한 RTMP(S) 서버 URL", false);
        key = input("BIGO가 발급한 비공개 스트림 키", true);
        body.addView(server); body.addView(key);
        state = text("아직 서버에 연결하지 않았습니다.", 15);
        body.addView(state);

        body.addView(button("▶ 독립 RTMP 음악 송출 시작", () -> {
            String address = server.getText().toString().trim();
            String secret = key.getText().toString().trim();
            if (address.isEmpty() || secret.isEmpty()) {
                new AlertDialog.Builder(this).setTitle("BIGO 서버·스트림 키 필요")
                        .setMessage("BIGO 공식 OBS 스트리밍 설정에서 "
                                + "계정에 발급된 서버 URL과 키를 확인해 주세요. "
                                + "계정에서 지원하지 않으면 강제로 시작할 수 없어요.")
                        .setPositiveButton("확인", null).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("진짜 인터넷 생방송 시작 확인")
                    .setMessage("입력한 서버로 라이브 음악·고정 표지를 송출합니다. "
                            + "휴대폰 미디어 볼륨은 방송 소리에 영향을 주지 않도록 "
                            + "디코더에서 직접 오디오를 전송합니다. "
                            + "아환 TTS·자동 인사/감사 댓글을 RTMP 음악에 믹싱하는 "
                            + "실험 코드를 추가했지만 실제 BIGO 청취 수신은 별도 기기로 확인해야 해요. "
                            + "시작할까요?")
                    .setPositiveButton("내 RTMP 서버로 시작", (dialog, which) -> {
                        Intent launch = new Intent(this, RadioDirectRtmpService.class)
                                .setAction(RadioDirectRtmpService.ACTION_START)
                                .putExtra(RadioDirectRtmpService.EXTRA_SERVER, address)
                                .putExtra(RadioDirectRtmpService.EXTRA_KEY, secret);
                        try {
                            if (Build.VERSION.SDK_INT >= 26) startForegroundService(launch);
                            else startService(launch);
                            key.setText(""); // Don't leave the stream key in editable UI.
                            state.setText("RTMP 시작 요청 · 연결 상태를 확인하세요.");
                        } catch (RuntimeException e) {
                            state.setText("Android 송출 서비스를 시작하지 못했어요.");
                        }
                    })
                    .setNegativeButton("취소", null).show();
        }));
        body.addView(button("■ 독립 RTMP 송출 즉시 종료", () -> {
            try {
                startService(new Intent(this, RadioDirectRtmpService.class)
                        .setAction(RadioDirectRtmpService.ACTION_STOP));
            } catch (RuntimeException e) { state.setText("서비스 종료 요청 실패"); }
        }));
        body.addView(text("🔎 수신 테스트: 다른 휴대폰에서 내 BIGO 룸에 "
                + "접속해 음악이 들리는지 확인하고, 송출 휴대폰의 음량을 0으로 "
                + "내린 뒤에도 청취자 소리가 계속 나오는지 시험하세요. "
                + "소리가 들리지 않으면 송출 완료가 아닙니다.", 13));
        body.addView(text("안내: 첫 곡과 같은 샘플레이트·채널 수를 가진 "
                + "원곡만 매끄럽게 이어서 재생할 수 있습니다. "
                + "호환곡이 없으면 송출을 멈추고 화면에 이유를 표시합니다. "
                + "모든 곡을 같은 포맷으로 변환하는 기능, 형의 녹음 인사 PCM, "
                + "실기기 BIGO 청취 검증은 아직 필요합니다.", 12));
        body.addView(button("← MARU RADIO 돌아가기", this::finish));
        setContentView(scroll);
    }
    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(update);
        handler.post(update);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(update);
        super.onPause();
    }
}
