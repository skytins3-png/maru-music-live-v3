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
 * Two independent, explicitly user-configured wall-clock alarms.
 * Start: notify and require a foreground user confirmation before BIGO opens.
 * End: stop ONLY MARU playback, notify host to end BIGO manually.
 * Neither alarm assumes one hour or claims control of BIGO server state.
 */
public final class RadioSchedule extends BroadcastReceiver {
    public static final String ACTION_REMIND = "com.maru.radio.REMIND_BIGO_START";
    public static final String ACTION_END = "com.maru.radio.REMIND_BIGO_END";
    public static final String EXTRA_READY = "radio_schedule_ready";
    public static final String EXTRA_END_READY = "radio_schedule_end";
    private static final String STORE = "maru_radio_schedule";
    private static final String CHANNEL = "maru_radio_onair_reminder";
    private static final int START_REQUEST = 731;
    private static final int END_REQUEST = 733;
    private static final int START_NOTICE = 732;
    private static final int END_NOTICE = 734;

    private static SharedPreferences pref(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public static long scheduledAt(Context context) {
        long at = pref(context).getLong("when", 0L);
        return at > System.currentTimeMillis() ? at : 0;
    }

    public static long endAt(Context context) {
        long at = pref(context).getLong("end_when", 0L);
        return at > System.currentTimeMillis() ? at : 0;
    }

    public static String formatted(Context context) {
        return format(scheduledAt(context));
    }

    public static String endFormatted(Context context) {
        return format(endAt(context));
    }

    private static String format(long time) {
        return time == 0 ? "설정 안 함"
                : DateFormat.format("M월 d일 HH:mm", new Date(time)).toString();
    }

    public static boolean notificationsAllowed(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private static PendingIntent alarm(Context context, boolean end) {
        Intent intent = new Intent(context, RadioSchedule.class)
                .setAction(end ? ACTION_END : ACTION_REMIND);
        return PendingIntent.getBroadcast(context, end ? END_REQUEST : START_REQUEST, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void checkTime(long at) {
        if (at <= System.currentTimeMillis())
            throw new IllegalArgumentException("지난 시간은 예약할 수 없습니다.");
    }

    private static void schedule(Context context, long at, boolean end) {
        checkTime(at);
        if (!notificationsAllowed(context))
            throw new SecurityException("알림 권한이 필요합니다.");
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) throw new IllegalStateException("알람을 사용할 수 없습니다.");
        // Inexact by design: no special exact-alarm or accessibility permission.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context, end));
        pref(context).edit().putLong(end ? "end_when" : "when", at).apply();
    }

    public static void set(Context context, long at) { schedule(context, at, false); }
    public static void setEnd(Context context, long at) { schedule(context, at, true); }

    private static void cancelOne(Context context, boolean end) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(alarm(context, end));
        pref(context).edit().remove(end ? "end_when" : "when").apply();
    }

    public static void cancel(Context context) { cancelOne(context, false); }
    public static void cancelEnd(Context context) { cancelOne(context, true); }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        boolean isEnd = ACTION_END.equals(intent.getAction());
        if (!isEnd && !ACTION_REMIND.equals(intent.getAction())) return;
        SharedPreferences sp = pref(context);
        String key = isEnd ? "end_when" : "when";
        long expected = sp.getLong(key, 0);
        if (expected == 0L) return;
        // Not in advance if an old alarm from an earlier reservation fires.
        if (System.currentTimeMillis() + 1000 < expected) return;
        sp.edit().remove(key).apply();
        if (isEnd) {
            // An explicit end time may end the local playback regardless of the
            // Activity being visible. It CANNOT end BIGO's actual live session.
            try { context.stopService(new Intent(context, RadioPlaybackService.class)); }
            catch (RuntimeException ignored) {}
        }
        if (!notificationsAllowed(context)) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                "MARU RADIO 시작·종료 알림", NotificationManager.IMPORTANCE_HIGH));
        Intent activity = new Intent(context, RadioAutomationActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(isEnd ? EXTRA_END_READY : EXTRA_READY, true);
        PendingIntent tap = PendingIntent.getActivity(context,
                isEnd ? END_REQUEST : START_REQUEST, activity,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle(isEnd ? "MARU 음악 종료 · BIGO 라이브 확인" : "MARU 방송 시작 준비 시간")
                .setContentText(isEnd
                        ? "MARU 음악은 종료했어요. BIGO 라이브는 직접 종료해 주세요."
                        : "탭해서 음악 재생 및 BIGO 라이브 시작을 직접 확인하세요.")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        nm.notify(isEnd ? END_NOTICE : START_NOTICE, note);
    }
}
