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
import java.util.List;

/**
 * One-shot clock scheduling for MARU's OWN local music player only.
 * Automatic music: opt-in exact Android alarm; no other app's broadcast controls.
 * BIGO still requires its host to tap Audio Live / Mixer / Start / End manually.
 */
public final class RadioSchedule extends BroadcastReceiver {
    public static final String ACTION_REMIND = "com.maru.radio.REMIND_BIGO_START";
    public static final String ACTION_END = "com.maru.radio.REMIND_BIGO_END";
    public static final String EXTRA_READY = "radio_schedule_ready";
    public static final String EXTRA_AUTO_READY = "radio_schedule_auto_music";
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
        return at > System.currentTimeMillis() ? at : 0L;
    }

    public static long endAt(Context context) {
        long at = pref(context).getLong("end_when", 0L);
        return at > System.currentTimeMillis() ? at : 0L;
    }

    public static boolean automaticMusic(Context context) {
        return pref(context).getBoolean("auto_music", false);
    }

    public static String formatted(Context context) {
        return format(scheduledAt(context));
    }

    public static String endFormatted(Context context) {
        return format(endAt(context));
    }

    private static String format(long time) {
        return time == 0L ? "설정 안 함"
                : DateFormat.format("M월 d일 HH:mm", new Date(time)).toString();
    }

    public static boolean notificationsAllowed(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    /** An automatic background foreground-service launch needs an exact-alarm exemption. */
    public static boolean canAutoStart(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        return manager != null && manager.canScheduleExactAlarms();
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

    private static void arm(Context context, long at, boolean end, boolean autoMusic) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) throw new IllegalStateException("알람을 사용할 수 없습니다.");
        if (autoMusic && !canAutoStart(context))
            throw new SecurityException("음악 자동 시작에는 정확한 알람 허용이 필요합니다.");
        // User opt-in automatic local music and user-authorized exact end clocks.
        // Reminder-only alarms do not request exact-alarm permission.
        if ((autoMusic || end) && canAutoStart(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at,
                    alarm(context, end));
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at,
                    alarm(context, end));
        }
    }

    private static void schedule(Context context, long at, boolean end, boolean autoMusic) {
        checkTime(at);
        if (!notificationsAllowed(context))
            throw new SecurityException("알림 권한이 필요합니다.");
        arm(context, at, end, autoMusic);
        SharedPreferences.Editor edit = pref(context).edit()
                .putLong(end ? "end_when" : "when", at);
        if (!end) edit.putBoolean("auto_music", autoMusic);
        edit.apply();
    }

    public static void set(Context context, long at) { schedule(context, at, false, false); }
    public static void setAutomatic(Context context, long at) {
        schedule(context, at, false, true);
    }
    public static void setEnd(Context context, long at) { schedule(context, at, true, false); }

    private static void cancelOne(Context context, boolean end) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager != null) manager.cancel(alarm(context, end));
        SharedPreferences.Editor edit = pref(context).edit()
                .remove(end ? "end_when" : "when");
        if (!end) edit.remove("auto_music");
        edit.apply();
    }

    public static void cancel(Context context) { cancelOne(context, false); }
    public static void cancelEnd(Context context) { cancelOne(context, true); }

    /** Re-arm ONLY future user-selected alarms after a reboot. Never start audio on boot. */
    private static void restore(Context context) {
        SharedPreferences sp = pref(context);
        long start = sp.getLong("when", 0L);
        long end = sp.getLong("end_when", 0L);
        long now = System.currentTimeMillis();
        try {
            if (start > now) arm(context, start, false, sp.getBoolean("auto_music", false));
            if (end > now) arm(context, end, true, false);
        } catch (RuntimeException ignored) {
            // No silently downgraded auto-start: user must restore exact-alarm access.
        }
    }

    /** Accept only the existing user-authorized queue and currently chosen genre. */
    private static boolean startLocalMusic(Context context) {
        List<Originals.Track> tracks = Originals.list(context);
        int category = RadioProgram.active(context);
        Originals.Track first = null;
        for (Originals.Track track : tracks) {
            if (category == 0 || category == 5
                    || RadioProgram.category(context, track) == category) {
                first = track;
                break;
            }
        }
        if (first == null) return false;
        Intent play = new Intent(context, RadioPlaybackService.class)
                .setAction(RadioPlaybackService.ACTION_PLAY)
                .putExtra(RadioPlaybackService.EXTRA_HASH, first.hash);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(play);
            else context.startService(play);
            // Android can still deny sound (audio focus/OS policy). Notification says attempted.
            return true;
        } catch (RuntimeException denied) {
            return false;
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            restore(context);
            return;
        }
        boolean isEnd = ACTION_END.equals(intent.getAction());
        if (!isEnd && !ACTION_REMIND.equals(intent.getAction())) return;

        SharedPreferences sp = pref(context);
        String key = isEnd ? "end_when" : "when";
        long expected = sp.getLong(key, 0L);
        if (expected == 0L) return;
        long now = System.currentTimeMillis();
        if (now + 1000L < expected) return; // Reject obsolete early delivery.
        boolean automatic = !isEnd && sp.getBoolean("auto_music", false);
        sp.edit().remove(key).apply();

        boolean started = false;
        boolean skippedForEnd = false;
        if (isEnd) {
            try { context.stopService(new Intent(context, RadioPlaybackService.class)); }
            catch (RuntimeException ignored) {}
        } else if (automatic) {
            long end = sp.getLong("end_when", 0L);
            skippedForEnd = end > 0L && now >= end;
            if (!skippedForEnd) started = startLocalMusic(context);
        }

        if (!notificationsAllowed(context)) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                "MARU 음악 예약·BIGO 수동 방송 안내", NotificationManager.IMPORTANCE_HIGH));

        Intent activity = new Intent(context, RadioAutomationActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(isEnd ? EXTRA_END_READY
                        : automatic ? EXTRA_AUTO_READY : EXTRA_READY, true)
                .putExtra("music_launch_attempted", started);
        PendingIntent tap = PendingIntent.getActivity(context,
                isEnd ? END_REQUEST : START_REQUEST, activity,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = isEnd ? "MARU 음악 종료 · BIGO 방송은 수동 종료"
                : automatic ? started ? "MARU 음악 자동 재생 요청 · BIGO 시작은 수동"
                        : "MARU 자동 재생 실패 · 확인 필요"
                : "MARU 방송 시작 준비 알림";
        String description = isEnd
                ? "음악 종료 요청 완료. BIGO에서는 '라이브 종료하기'를 눌러야 해요."
                : automatic ? skippedForEnd
                        ? "종료 시각이 지나 음악을 시작하지 않았어요."
                        : started ? "음악 재생을 요청했어요. BIGO 오디오 LIVE는 직접 시작하세요."
                        : "선택한 장르의 원곡 또는 Android 자동 재생 권한을 확인하세요."
                : "탭해서 음악 재생 및 BIGO LIVE 시작을 직접 확인하세요.";

        Notification.Builder builder = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle(title)
                .setContentText(description)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER);
        if (isEnd) {
            PendingIntent bigoOpen = BigoBroadcastGuide.endBigoShortcut(context);
            if (bigoOpen != null) builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "BIGO 열어 종료 확인", bigoOpen).build());
        }
        nm.notify(isEnd ? END_NOTICE : START_NOTICE, builder.build());
    }
}
