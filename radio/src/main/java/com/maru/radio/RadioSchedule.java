package com.maru.radio;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.format.DateFormat;

import java.util.Date;

/**
 * A one-time, on-device start reminder. BIGO cannot be silently brought live
 * while the phone is locked; the host must tap and explicitly confirm.
 * Inexact Doze scheduling can delay the alarm.
 */
public final class RadioSchedule extends BroadcastReceiver {
    public static final String ACTION_REMIND = "com.maru.radio.REMIND_BIGO_START";
    public static final String EXTRA_READY = "radio_schedule_ready";
    private static final String STORE = "maru_radio_schedule";
    private static final String CHANNEL = "maru_radio_onair_reminder";
    private static final int REQUEST = 731;
    private static final int NOTICE = 732;

    private static SharedPreferences pref(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public static long scheduledAt(Context context) {
        long when = pref(context).getLong("when", 0L);
        return when > System.currentTimeMillis() ? when : 0L;
    }

    public static String formatted(Context context) {
        long time = scheduledAt(context);
        if (time == 0L) return "예약 없음";
        return DateFormat.format("M월 d일 HH:mm", new Date(time)).toString();
    }

    public static boolean notificationsAllowed(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private static PendingIntent alarm(Context context) {
        Intent intent = new Intent(context, RadioSchedule.class).setAction(ACTION_REMIND);
        return PendingIntent.getBroadcast(context, REQUEST, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void set(Context context, long at) {
        if (at <= System.currentTimeMillis()) throw new IllegalArgumentException("Past schedule");
        if (!notificationsAllowed(context)) throw new SecurityException("Notifications disabled");
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) throw new IllegalStateException("Alarm service unavailable");
        // setAndAllowWhileIdle is intentionally inexact and requires no exact-alarm permission.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context));
        pref(context).edit().putLong("when", at).apply();
    }

    public static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(alarm(context));
        pref(context).edit().remove("when").apply();
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_REMIND.equals(intent.getAction())) return;
        long expected = pref(context).getLong("when", 0L);
        // Old/cancelled reminder must never trigger a new live-start invitation.
        if (expected == 0L) return;
        pref(context).edit().remove("when").apply();
        if (!notificationsAllowed(context)) return;

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                "MARU RADIO 방송 시작 알림", NotificationManager.IMPORTANCE_HIGH));

        Intent activity = new Intent(context, RadioAutomationActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_READY, true);
        PendingIntent tap = PendingIntent.getActivity(context, REQUEST, activity,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle("MARU RADIO · 방송 준비 시간")
                .setContentText("탭해서 음악을 준비하고 BIGO 오디오 LIVE 시작을 확인하세요.")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        nm.notify(NOTICE, note);
    }
}
