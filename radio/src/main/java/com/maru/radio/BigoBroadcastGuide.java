package com.maru.radio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * BIGO launcher and step-by-step help using only public Android APIs.
 * It NEVER touches BIGO's private activities, microphone slider, or live controls.
 * No accessibility, overlay, screen capture or unverified deep link is used.
 */
public final class BigoBroadcastGuide {
    private static final String BIGO_PACKAGE = "sg.bigo.live";
    private static final String CHANNEL = "maru_radio_bigo_handoff";
    private static final int START_NOTE = 8511;
    private static final int END_NOTE = 8512;

    private BigoBroadcastGuide() {}

    /**
     * Foreground user action only. Once the owner has independently opted
     * in to narrowly-scoped BIGO Accessibility controls, launch BIGO and
     * request the guarded five-screen composer/mixer/live start sequence.
     * This does not auto-unlock, invent credentials, route audio to BIGO,
     * or confirm a remote listener's reception.
     */
    public static void startAutomatic(Activity host) {
        if (!BigoControlAssist.approved(host) || !BigoControlAssist.connected(host)) {
            new AlertDialog.Builder(host)
                    .setTitle("BIGO 자동 시작 · 연결 필요")
                    .setMessage("현재 BIGO 자동 조작에 필요한 앱 내부 동의 또는 "
                            + "Android 접근성 연결이 없습니다. "
                            + "삼성에서 '제한된 설정으로 제어됨'이 표시된 경우 "
                            + "MARU는 보안 차단을 자체 해제하지 못합니다. "
                            + "앱 권한의 '모든 권한'도 접근성 허용이 아닙니다. "
                            + "연결 진단에서 설치 출처와 실제 접속 상태를 확인해 주세요. "
                            + "형의 기존 음악·녹음 파일은 삭제하지 마세요.")
                    .setPositiveButton("🔍 연결 상태 확인", (d, w) ->
                            host.startActivity(new Intent(host,
                                    RadioAccessDoctorActivity.class)))
                    .setNeutralButton("▶ BIGO 수동 열기", (d, w) -> openBigo(host, false))
                    .setNegativeButton("취소", null).show();
            return;
        }
        Intent bigo = host.getPackageManager().getLaunchIntentForPackage(BIGO_PACKAGE);
        if (bigo == null) {
            new AlertDialog.Builder(host).setTitle("BIGO 앱 없음")
                    .setMessage("BIGO LIVE 설치 상태를 먼저 확인해 주세요.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        if (!BigoControlAssist.queue(host, true)) {
            new AlertDialog.Builder(host).setTitle("BIGO 자동 시작 불가")
                    .setMessage("자동 조작 연결에 실패했습니다. "
                            + "MARU에서 동의·접근성 상태를 다시 확인해 주세요.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        // This is started only after an explicit tap while MARU is foreground.
        // Android may restrict later background launches for alarms.
        openBigo(host, false);
    }

    public static void showStart(Activity host) {
        new AlertDialog.Builder(host)
                .setTitle("🎙 BIGO 오디오 라이브 시작")
                .setMessage("형이 보내준 BIGO 오디오 LIVE 준비 화면 기준:\n\n"
                        + "① 방송 시작 전 믹서에는 마이크 볼륨, 미리 듣기, 지능형 오디오 효과, "
                        + "음악 효과(Original/리버브/Live Concert/KTV), 이퀄라이저, "
                        + "변조 기능이 보입니다.\n"
                        + "② 중요: 여기서 Original은 '효과 없음'을 선택하는 버튼이지 "
                        + "MARU의 Suno 원곡을 불러오는 기능이 아닙니다. "
                        + "커스텀 이퀄라이저도 파일 가져오기가 아닙니다.\n"
                        + "③ 이 세 장의 사진에서는 '내 음악·BGM·파일 가져오기' 버튼이 "
                        + "확인되지 않았어요. 음악을 BIGO에 송출했다고 판단하지 않습니다.\n"
                        + "④ 방송을 시작한 후 도구/메뉴(☰)에서 별도의 '음악' 또는 'BGM' "
                        + "항목이 있는지 확인하세요. BIGO 공식 안내는 방송 중 도구에 "
                        + "음악 관련 기능이 있을 수 있다고 설명합니다.\n"
                        + "⑤ '내 음악/로컬 파일'이 실제 보이는 경우에만 MARU에서 "
                        + "Music/MARU RADIO 폴더로 복사한 Suno 원곡을 선택해 볼 수 있습니다.\n"
                        + "⑥ 청취자에게 확인해야 하는 것은 '폰 미디어 음량 0에서도 "
                        + "노래가 일정하게 들리는가'와 '아환 답변이 실제 들리는가' "
                        + "두 가지입니다.\n\n"
                        + "Android MARU는 BIGO의 마이크 채널에 TTS PCM을 "
                        + "직접 주입하지 못합니다. 현재 효과 메뉴만으로는 "
                        + "두 핵심 기능을 완성한 것이 아닙니다. "
                        + "형의 원곡과 녹음 파일은 삭제하지 않습니다.")
                .setPositiveButton("BIGO 열어 설정하기", (d, w) -> openBigo(host, false))
                .setNegativeButton("취소", null)
                .show();
    }

    public static void showEnd(Activity host) {
        new AlertDialog.Builder(host)
                .setTitle("■ BIGO 오디오 라이브 종료")
                .setMessage("① BIGO 방송 진행 화면으로 돌아가기\n"
                        + "② 오른쪽 위 X 또는 종료 메뉴 누르기\n"
                        + "③ '라이브 종료하기'를 직접 확인하기\n\n"
                        + "방송 종료는 BIGO에서 확인해야 해요. "
                        + "MARU의 음악 종료와 BIGO 생방송 종료는 서로 다릅니다.")
                .setPositiveButton("음악 끄고 BIGO 열기", (d, w) -> {
                    host.stopService(new Intent(host, RadioPlaybackService.class));
                    openBigo(host, true);
                })
                .setNeutralButton("BIGO만 열기", (d, w) -> openBigo(host, true))
                .setNegativeButton("취소", null)
                .show();
    }

    public static void openBigo(Activity host, boolean ending) {
        Intent launch = host.getPackageManager().getLaunchIntentForPackage(BIGO_PACKAGE);
        if (launch == null) {
            new AlertDialog.Builder(host).setTitle("BIGO 앱을 찾을 수 없음")
                    .setMessage("BIGO LIVE 앱의 설치 상태를 확인해 주세요. "
                            + "이 기능은 BIGO 앱을 여는 연결입니다.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        try {
            postChecklist(host, ending, launch);
            host.startActivity(launch);
        } catch (RuntimeException e) {
            new AlertDialog.Builder(host).setTitle("BIGO 열기 실패")
                    .setMessage("BIGO가 실행되지 않았어요. BIGO 앱을 직접 열어 "
                            + "오디오 라이브 화면을 확인해 주세요.")
                    .setPositiveButton("확인", null).show();
        }
    }

    private static void postChecklist(Context ctx, boolean ending, Intent bigoLaunch) {
        if (Build.VERSION.SDK_INT >= 33
                && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            return; // Not required to launch BIGO; user can follow the dialog instructions.
        }
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                "BIGO 방송 준비 및 종료 안내", NotificationManager.IMPORTANCE_DEFAULT));

        PendingIntent returnToBigo = PendingIntent.getActivity(ctx,
                ending ? END_NOTE : START_NOTE, bigoLaunch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = ending ? "BIGO 방송 종료 확인" : "BIGO 오디오 LIVE 방송 준비";
        String details = ending
                ? "BIGO 방송 화면의 X → '라이브 종료하기'를 직접 확인하세요."
                : "오디오라이브 믹서는 음향효과 전용. 방송 중 도구에서 별도 음악/BGM 찾기";
        Notification note = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle(title)
                .setContentText(details)
                .setStyle(new Notification.BigTextStyle().bigText(details
                        + "\n※ MARU는 BIGO 버튼/믹서 설정을 자동 변경하지 않습니다."))
                .setContentIntent(returnToBigo)
                .setAutoCancel(true)
                .setTimeoutAfter(15 * 60 * 1000L)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        nm.notify(ending ? END_NOTE : START_NOTE, note);
    }

    public static PendingIntent endBigoShortcut(Context ctx) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(BIGO_PACKAGE);
        if (launch == null) return null;
        return PendingIntent.getActivity(ctx, END_NOTE + 1, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
