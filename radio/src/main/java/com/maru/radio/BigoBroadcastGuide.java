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

    public static void showStart(Activity host) {
        new AlertDialog.Builder(host)
                .setTitle("🎙 BIGO 오디오 라이브 시작")
                .setMessage("형이 보내준 BIGO 오디오 LIVE 준비 화면 기준:\n\n"
                        + "① BIGO에서 방송 준비 → '오디오 라이브' 선택\n"
                        + "② 하단 '믹서' 열기 → 마이크 볼륨 1로 설정\n"
                        + "③ 음악 효과는 'Original(원본)' 선택\n"
                        + "④ 오른쪽 X로 믹서 닫고 '방송하기' 누르기\n"
                        + "⑤ 스트림 키가 없어도 BIGO 자체 로컬 음악/BGM 메뉴가 있는지 확인\n"
                        + "⑥ 다른 휴대폰에서 음악 수신, 방송 휴대폰 미디어 음량 0, 아환 댓글 음성을 각각 확인\n\n"
                        + "BIGO에서 MARU TTS/재생 소리가 자동 송출되지는 않습니다. " 
                        + "청취자 확인 없는 '음량 고정 성공' 표시는 하지 않습니다.\n\n"
                        + "MARU가 BIGO 앱은 열지만, 다른 앱의 마이크 슬라이더나 "
                        + "방송하기 버튼을 대신 누를 수는 없어요. "
                        + "화면에서 직접 조작해 주세요.")
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
                : "오디오 라이브 → 믹서 → 마이크 1 → Original → 방송하기";
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
