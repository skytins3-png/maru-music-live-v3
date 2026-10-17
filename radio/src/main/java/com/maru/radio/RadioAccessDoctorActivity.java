package com.maru.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * On-device diagnostics for inaccessible sideloaded AccessibilityService.
 * Intentionally never writes Settings.Secure, accessibility AppOps, or other
 * restricted Android settings: only the user/system can grant them.
 */
public final class RadioAccessDoctorActivity extends Activity {
    private static final int BG = 0xFF141022;
    private static final int TEXT = 0xFFF5EDFF;
    private static final int DIM = 0xFFDACCF3;
    private LinearLayout body;
    private TextView status;

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(2), dp(8), dp(2), dp(8));
        return t;
    }
    private void button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        body.addView(b, new LinearLayout.LayoutParams(-1,-2));
    }
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 35)
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(16), dp(18), dp(32));
        scroll.addView(body);
        setContentView(scroll);
        body.addView(label("🔍 BIGO 자동 방송 연결 진단", 24, TEXT));
        body.addView(label("형의 휴대폰에 실제 설치된 MARU 상태만 확인합니다. "
                + "접근성 강제 활성화, 보안 설정 우회, BIGO 계정 자동 로그인을 하지 않습니다.", 14, DIM));
        status = label("", 16, TEXT);
        body.addView(status);
        button("↻ 현재 권한·연결 상태 다시 확인", this::refresh);
        button("⚙ 설치된 접근성 앱 화면 열기", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (RuntimeException blocked) {
                status.setText("안드로이드 접근성 설정을 열지 못했습니다.");
            }
        });
        button("📱 MARU 앱 정보 화면 열기", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException blocked) {
                status.setText("앱 정보 화면을 열지 못했습니다.");
            }
        });
        button("✅ MARU 내부 BIGO 자동 조작 동의 확인", () ->
                startActivity(new Intent(this, RadioAutomationActivity.class)));
        button("▶ BIGO 수동 열기 (접근성 권한 필요 없음)", () ->
                BigoBroadcastGuide.openBigo(this, false));
        button("ℹ 제한된 설정·서명 문제 설명", this::explain);
        button("← MARU RADIO", this::finish);
        refresh();
    }
    @Override protected void onResume() {
        super.onResume();
        refresh();
    }
    private void refresh() {
        if (status == null) return;
        boolean consent = BigoControlAssist.approved(this);
        boolean connected = BigoControlAssist.connected(this);
        String installer = "(설치 출처 정보 없음)";
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.content.pm.InstallSourceInfo source =
                        getPackageManager().getInstallSourceInfo(getPackageName());
                String installerId = source.getInstallingPackageName();
                String initId = source.getInitiatingPackageName();
                installer = (installerId == null ? "불명" : installerId)
                        + (initId == null || initId.equals(installerId) ? "" : " / " + initId);
            } catch (Exception noSource) {
                installer = "Android에서 정보 제공 안 함";
            }
        }
        String version = "?";
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = info.versionName;
        } catch (Exception ignored) {}
        status.setText("설치 앱: " + getPackageName()
                + "\n버전: " + version
                + "\n설치 관리 앱: " + installer
                + "\nMARU 내부 동의: " + (consent ? "허용" : "미동의")
                + "\n접근성 서비스: " + (connected ? "연결됨" : "연결되지 않음")
                + "\n\n" + RadioAccessState.description(consent, connected)
                + "\n\n⚠ 설치 출처만으로 '제한된 설정' 차단 사유를 단정할 수 없습니다."
                + "\n⚠ 접근성 허용은 음악·아환 음성의 BIGO 청취자 송출 허용과 무관합니다.");
    }
    private void explain() {
        new AlertDialog.Builder(this)
                .setTitle("안드로이드 접근성 제한")
                .setMessage("형의 삼성 화면에서 MARU BIGO 접근성 항목이 "
                        + "'제한된 설정으로 제어됨'이라고 표시됐습니다. "
                        + "이 제한은 휴대폰 운영체제가 관리합니다. "
                        + "앱 코드나 마이크·알림 권한으로 강제 해제할 수 없습니다.\n\n"
                        + "또한 접근성 설정에 MARU 항목이 두 개라면 "
                        + "이름이 비슷한 다른 버전이나 앱이 설치되어 있을 수 있습니다. "
                        + "이번 버전은 'MARU V1.9.19 · BIGO 자동 조작'으로 구별합니다. "
                        + "기존 노래·아환 녹음은 백업 전 삭제하지 마세요.\n\n"
                        + "신뢰할 수 있는 앱이라도 화면 내용을 읽고 버튼을 누르는 "
                        + "접근성 권한은 신중히 승인해야 합니다. "
                        + "삼성 앱 정보 화면에 '제한된 설정 허용' 메뉴가 없다면 "
                        + "없는 버튼을 반복해서 찾거나 보안을 우회하지 마세요. "
                        + "정식 배포 경로와 고정된 서명키가 확보돼야 "
                        + "기존 데이터가 유지되는 업데이트를 안정적으로 제공할 수 있습니다.\n\n"
                        + "현재 권한이 막힌 경우 BIGO 수동 방송은 가능하지만 "
                        + "자동 버튼 조작과 공개 댓글 화면 감지는 실행되지 않습니다. "
                        + "그와 별개로 BIGO로 음악/TTS를 보내는 공식 입력 경로도 필요합니다.")
                .setPositiveButton("확인", null).show();
    }
}
