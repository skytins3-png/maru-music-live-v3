package com.maru.radio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

/**
 * Clock-time broadcast preparation reminders and optional multilingual replies.
 * Does not access BIGO chat or claim the ability to schedule unattended live streaming.
 */
public final class RadioAutomationActivity extends Activity {
    private static final int ASK_NOTIFICATION = 501;
    private static final int ASK_END_NOTIFICATION = 502;
    private static final int ASK_PAIR_NOTIFICATION = 503;
    private static final int BG = 0xFF110E21;
    private static final int PANEL = 0xFF281F43;
    private static final int WHITE = 0xFFF7F1FF;
    private static final int PURPLE = 0xFFB89BFF;

    private TextView scheduleStatus;
    private TextView endStatus;
    private TextView bigoStatus;
    private TextView note;
    private long pendingStartAt;
    private long pendingEndAt;
    private boolean pendingAutoMusic;
    private boolean waitingExactAccess;
    private boolean waitingPairExactAccess;

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        render();
        handleStartIntent(getIntent());
    }

    @Override protected void onResume() {
        super.onResume();
        if (waitingExactAccess || waitingPairExactAccess) {
            boolean pair = waitingPairExactAccess;
            waitingExactAccess = false;
            waitingPairExactAccess = false;
            if (RadioSchedule.canAutoStart(this) && pendingStartAt > System.currentTimeMillis()) {
                if (pair) savePair(); else saveSchedule();
            } else {
                message("정확한 알람이 허용되지 않았어요. 자동 음악 예약은 저장되지 않았습니다.");
            }
        }
        updateStatus();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleStartIntent(intent);
    }

    private int dp(int x) {
        return (int) (getResources().getDisplayMetrics().density * x + .5f);
    }

    private TextView label(String value, int fontSize, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(fontSize);
        t.setPadding(dp(2), dp(7), dp(2), dp(7));
        return t;
    }

    private Button button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(PANEL);
        background.setCornerRadius(dp(14));
        result.setBackground(background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(result, lp);
        return result;
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(0, safe.top, 0, safe.bottom);
                return insets;
            });
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(19), dp(17), dp(19), dp(36));
        scroll.addView(content);
        content.addView(label("📻 MARU 원곡 자동 시작·종료", 27, PURPLE));
        content.addView(label("MARU 음악만 예약 자동 재생·종료 · BIGO 라이브 조작은 수동", 14, WHITE));

        LinearLayout schedule = card(content);
        schedule.addView(label("① 원하는 음악 시작 시각", 21, WHITE));
        schedule.addView(label("‘음악 자동 시작’은 선택한 Suno 원곡을 예약 시각에 "
                + "자동 재생하도록 Android에 요청해요. BIGO 오디오 라이브 방송은 "
                + "자동 시작되지 않으니 직접 눌러야 해요.",
                14, 0xFFDDD2E8));
        scheduleStatus = label("음악 시작 예약 없음", 16, PURPLE);
        schedule.addView(scheduleStatus);
        schedule.addView(button("⏯ 시작·종료 한번에 자동 예약", this::choosePair));
        schedule.addView(button("🎵 음악 자동 시작 시간 지정", () -> chooseClock(true)));
        schedule.addView(button("🔔 알림만 예약 (직접 재생)", () -> chooseClock(false)));
        schedule.addView(button("시작 예약 취소", this::cancelSchedule));

        LinearLayout time = card(content);
        time.addView(label("② 원하는 음악 종료 시각", 21, WHITE));
        time.addView(label("1시간 자동 종료는 없습니다. 형이 지정한 시각에만 "
                + "MARU 음악을 종료하고 BIGO 종료 알림을 보냅니다. "
                + "BIGO 라이브는 방송 화면에서 직접 종료해야 합니다.",
                14, 0xFFDDD2E8));
        endStatus = label("자동 종료 시각: 설정 안 함", 16, PURPLE);
        time.addView(endStatus);
        time.addView(button("⏰ 종료 시각 지정", this::chooseEndClock));
        time.addView(button("종료 예약 취소", this::cancelEndSchedule));
        time.addView(button("⏹ 시작·종료 예약 모두 취소", this::cancelPair));
        time.addView(button("재생 후 음악 종료 타이머 설정", () -> {
            startActivity(new Intent(this, RadioActivity.class));
            finish();
        }));

        LinearLayout bigo = card(content);
        bigo.addView(label("🔒 BIGO 오디오 LIVE 자동 버튼 (실험·선택)", 19, WHITE));
        bigo.addView(label("선택사항. BIGO 화면만 읽어 음성 LIVE → 믹서 → "
                + "마이크1 → Original → 방송하기 / 방송 종료 순서로 자동 누르기를 "
                + "시도합니다. 오직 사용자가 지정한 시작·종료 시간에만 최대 90초 동작해요. "
                + "잠금 화면·앱 변경·예상 밖 화면에서는 실패하며 실제 송출 보장이 없습니다. "
                + "댓글·비밀번호 등은 수집·보관·전송하지 않습니다. "
                + "Android 접근성 설정을 따로 허용해야 합니다. "
                + "Play Protect 경고가 나면 설치를 중단하세요.",
                13, 0xFFDDD2E8));
        bigoStatus = label("BIGO 자동 조작 동의 안 함", 14, PURPLE);
        bigo.addView(bigoStatus);
        bigo.addView(button("⚠️ BIGO 자동화 개인정보 안내·명시적 동의", this::consentBigo));
        bigo.addView(button("⛔ 동의 철회·BIGO 자동 조작 끄기", this::disableBigo));

        LinearLayout replies = card(content);
        replies.addView(label("③ 댓글·선물 다국어 답변", 21, WHITE));
        replies.addView(label("한국어·중국어·영어·일본어·러시아어. "
                + "작성된 문구를 직접 확인하고 복사해 BIGO에 붙여넣으세요. "
                + "댓글 자동 수집이나 자동 전송은 아직 지원되지 않아요.",
                14, 0xFFDDD2E8));
        replies.addView(button("💬 댓글 답변 문구 선택", this::replyLanguages));

        LinearLayout notes = card(content);
        notes.addView(label("방송 자동 운영 관련 안내", 18, PURPLE));
        notes.addView(label("🎵 자동 시작은 먼저 파일을 등록하고 정확한 알람 특별 접근을 "
                + "허용한 경우에만 동작하도록 예약해요. 휴대폰이 재부팅되면 "
                + "예약을 재등록하지만 기기 전원 꺼짐·오디오 포커스·절전 정책으로 "
                + "시작이 실패할 수 있습니다. 종료는 MARU 음악만 끝내며, "
                + "BIGO 실제 생방송의 시작·믹서 조작·종료는 직접 해야 합니다.",
                13, 0xFFDDD2E8));

        note = label("원곡은 재가공하지 않아요.", 13, 0xFFA8E3BE);
        content.addView(note);
        content.addView(button("← MARU RADIO 돌아가기", this::finish));
        setContentView(scroll);
        updateStatus();
    }

    private void updateStatus() {
        if (scheduleStatus != null) scheduleStatus.setText(
                (RadioSchedule.scheduledAt(this) == 0L ? "음악 시작 예약: "
                        : RadioSchedule.automaticMusic(this) ? "🎵 음악 자동 시작: "
                        : "🔔 시작 알림만: ")
                        + RadioSchedule.formatted(this));
        if (endStatus != null) endStatus.setText(
                "MARU 음악 종료 시각: " + RadioSchedule.endFormatted(this)
                        + " (BIGO는 동의·접근성·화면 인식 시에만 자동 시도)");
        if (bigoStatus != null) bigoStatus.setText(
                "동의: " + (BigoControlAssist.approved(this) ? "허용" : "안 함")
                + " / Android 접근성: " + (BigoControlAssist.connected(this)
                        ? "켜짐" : "꺼짐")
                + "\n최근 상태: " + BigoControlAssist.status(this));
    }

    private void consentBigo() {
        new AlertDialog.Builder(this)
            .setTitle("BIGO 접근성 사용·개인정보 안내")
            .setMessage("MARU는 예약 시각에 사용자의 BIGO 오디오 LIVE 버튼을 "
                    + "자동 조작하기 위해 Android 접근성 화면 읽기 및 화면 누르기를 "
                    + "사용합니다. BIGO 화면의 글자와 버튼 정보를 잠시 확인하지만 "
                    + "채팅·비밀번호·연락처를 저장하거나 외부로 보내지 않습니다. "
                    + "BIGO 외의 앱 화면은 받지 않습니다.\\n\\n"
                    + "오디오 LIVE 준비 화면·믹서·마이크 볼륨 1·Original·방송하기 "
                    + "및 BIGO 종료 버튼만 지정된 순서로 찾습니다. "
                    + "화면을 정확히 확인할 수 없다면 조작을 멈춥니다. "
                    + "잠금 상태나 기기 보안 제한을 우회하지 않습니다.\\n\\n"
                    + "동의를 철회하면 자동화 요청을 즉시 취소할 수 있습니다. "
                    + "이 기능은 BIGO에서 승인/검증한 기능이 아니며 실제 방송 "
                    + "시작·종료, 음악 송출은 반드시 휴대폰에서 검증해야 합니다.")
            .setPositiveButton("동의하고 접근성 설정 열기", (d, w) -> {
                BigoControlAssist.approve(this, true);
                updateStatus();
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch (RuntimeException e) {
                    message("Android 접근성 설정을 열 수 없어요.");
                }
            })
            .setNegativeButton("동의하지 않음", null)
            .show();
    }

    private void disableBigo() {
        BigoControlAssist.approve(this, false);
        updateStatus();
        message("BIGO 자동 제어 동의 철회됨. Android 접근성 설정에서도 서비스를 꺼 주세요.");
    }

    private void message(String value) { if (note != null) note.setText(value); }

    private void choosePair() {
        // Pick the NEXT occurrence of each clock time, then advance the end to
        // the following day when necessary. No hard-coded one-hour period.
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (v, startHour, startMin) -> {
            pendingStartAt = RadioClock.nextOccurrence(now, startHour, startMin);
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(pendingStartAt);
            new TimePickerDialog(this, (v2, endHour, endMin) -> {
                pendingEndAt = RadioClock.endAfter(now, pendingStartAt, endHour, endMin);
                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(pendingEndAt);
                String when = "시작 " + DateFormat.format("M월 d일 HH:mm", start)
                        + "\n종료 " + DateFormat.format("M월 d일 HH:mm", end);
                new AlertDialog.Builder(this)
                        .setTitle("MARU 음악 자동 시작·종료 예약")
                        .setMessage(when + "\n\n선택한 장르의 MARU 원곡만 예약 시간에 "
                                + "재생·종료합니다. BIGO 방송하기, 믹서, 실제 방송 종료는 "
                                + "직접 조작해야 해요. 정확한 알람 허용이 필요합니다.")
                        .setPositiveButton("두 시간 예약 저장", (d, w) -> savePair())
                        .setNegativeButton("취소", null).show();
            }, start.get(Calendar.HOUR_OF_DAY),
                    start.get(Calendar.MINUTE), true).show();
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private boolean hasProgramTracks() {
        int selected = RadioProgram.active(this);
        for (Originals.Track track : Originals.list(this)) {
            if (selected == 0 || selected == 5
                    || RadioProgram.category(this, track) == selected) return true;
        }
        return false;
    }

    private void savePair() {
        if (pendingStartAt <= System.currentTimeMillis() || pendingEndAt <= pendingStartAt) {
            message("예약할 시작·종료 시각이 올바르지 않아요. 두 시간을 다시 지정해 주세요.");
            return;
        }
        if (!hasProgramTracks()) {
            message("선택한 장르에 원곡이 없어요. 먼저 Download에서 음악을 가져와 주세요.");
            return;
        }
        if (!RadioSchedule.notificationsAllowed(this)) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        ASK_PAIR_NOTIFICATION);
            } else message("알림 권한을 먼저 허용해 주세요.");
            return;
        }
        if (!RadioSchedule.canAutoStart(this)) {
            new AlertDialog.Builder(this).setTitle("음악 자동 예약 권한")
                    .setMessage("Android의 '알람 및 리마인더' 정확한 알람을 허용한 뒤 "
                            + "MARU 시작·종료를 함께 예약할 수 있어요.")
                    .setPositiveButton("설정에서 허용", (d, w) -> {
                        try {
                            waitingPairExactAccess = true;
                            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(android.net.Uri.parse("package:" + getPackageName())));
                        } catch (RuntimeException ex) {
                            waitingPairExactAccess = false;
                            message("시스템 알람 설정을 열 수 없어요.");
                        }
                    })
                    .setNegativeButton("취소", null).show();
            return;
        }
        try {
            RadioSchedule.setPair(this, pendingStartAt, pendingEndAt);
            // Apply the new end clock to any music that is already playing.
            // When playback starts later, it also reads this saved end automatically.
            try {
                startService(new Intent(this, RadioPlaybackService.class)
                        .setAction(RadioPlaybackService.ACTION_SET_WALL_END)
                        .putExtra(RadioPlaybackService.EXTRA_END_AT, pendingEndAt));
            } catch (RuntimeException ignored) {}
            updateStatus();
            message("✅ MARU 음악 시작·종료를 함께 예약했어. BIGO 방송은 수동 시작·종료야.");
        } catch (RuntimeException e) {
            message("두 시간 예약 실패: 알람 접근·저장 설정을 확인해 주세요.");
        }
    }

    private void cancelPair() {
        RadioSchedule.cancelPair(this);
        pendingStartAt = 0L;
        pendingEndAt = 0L;
        try {
            // Cancel only clock-based stop, not the independently chosen duration timer.
            startService(new Intent(this, RadioPlaybackService.class)
                    .setAction(RadioPlaybackService.ACTION_CANCEL_WALL_END));
        } catch (RuntimeException ignored) {}
        updateStatus();
        message("시작·종료 예약 모두 취소 완료 · 재생 중인 음악은 유지됩니다.");
    }

    private void chooseClock(boolean autoMusic) {
        pendingAutoMusic = autoMusic;
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, min) -> {
            pendingStartAt = RadioClock.nextOccurrence(now, hour, min);
            Calendar chosen = Calendar.getInstance();
            chosen.setTimeInMillis(pendingStartAt);
            new AlertDialog.Builder(this).setTitle(
                            autoMusic ? "MARU 원곡 자동 재생 예약" : "음악 시작 알림 예약")
                    .setMessage("예약 시각: " + DateFormat.format("M월 d일 HH:mm", chosen)
                            + (autoMusic
                                ? "\n\n해당 시각에 MARU 원곡 재생을 요청합니다. "
                                  + "정확한 알람 허용이 필요하며, BIGO 오디오 LIVE는 "
                                  + "직접 방송하기를 눌러야 합니다."
                                : "\n\n예약 시각에 알림을 표시하고, "
                                  + "알림을 눌러야 음악을 직접 시작할 수 있어요."))
                    .setPositiveButton("예약 저장", (dialog, which) -> saveSchedule())
                    .setNegativeButton("취소", null).show();
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private void saveSchedule() {
        if (pendingStartAt <= System.currentTimeMillis()) {
            message("지난 시간입니다. 시작 시각을 다시 지정해 주세요.");
            return;
        }
        if (!RadioSchedule.notificationsAllowed(this)) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        ASK_NOTIFICATION);
            } else message("알림을 허용해 주세요.");
            return;
        }
        if (pendingAutoMusic && !RadioSchedule.canAutoStart(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("정확한 알람 접근 허용 필요")
                    .setMessage("휴대폰이 잠겨 있어도 MARU 음악 자동 시작을 요청하려면 "
                            + "시스템의 '알람 및 리마인더' 특별 접근을 허용해야 해요. "
                            + "허용하지 않으면 자동 시작 예약을 만들지 않습니다.")
                    .setPositiveButton("시스템 설정 열기", (d, w) -> {
                        try {
                            waitingExactAccess = true;
                            Intent settings = new Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(settings);
                        } catch (RuntimeException ex) {
                            waitingExactAccess = false;
                            message("정확한 알람 설정을 열 수 없어요.");
                        }
                    })
                    .setNegativeButton("취소", null).show();
            return;
        }
        if (pendingAutoMusic) {
            int category = RadioProgram.active(this);
            boolean found = false;
            for (Originals.Track track : Originals.list(this)) {
                if (category == 0 || category == 5
                        || RadioProgram.category(this, track) == category) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                message("선택한 장르에 곡이 없어 자동 시작을 예약하지 않았어. 먼저 원곡을 가져와 줘.");
                return;
            }
        }
        try {
            if (pendingAutoMusic) RadioSchedule.setAutomatic(this, pendingStartAt);
            else RadioSchedule.set(this, pendingStartAt);
            updateStatus();
            message(pendingAutoMusic
                    ? "MARU 음악 자동 재생 예약 완료. BIGO 방송 시작은 직접 눌러야 해요."
                    : "시작 알림 예약 완료. 알림을 누른 뒤 직접 음악을 켜 주세요.");
        } catch (RuntimeException ex) {
            message("예약 실패. 정확한 알람/알림/배터리 설정을 확인해 주세요.");
        }
    }

    private void chooseEndClock() {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, minute) -> {
            long start = RadioSchedule.scheduledAt(this);
            pendingEndAt = start > 0
                    ? RadioClock.endAfter(now, start, hour, minute)
                    : RadioClock.nextOccurrence(now, hour, minute);
            Calendar chosen = Calendar.getInstance();
            chosen.setTimeInMillis(pendingEndAt);
            new AlertDialog.Builder(this)
                    .setTitle("MARU 음악 종료 시각")
                    .setMessage("선택: " + DateFormat.format("M월 d일 HH:mm", chosen)
                            + "\n\n이 시각에 MARU 음악을 자동 종료합니다. "
                            + "BIGO 오디오 라이브 방송은 종료되지 않습니다. "
                            + "종료 알림을 확인하고 BIGO에서 직접 '라이브 종료하기'를 눌러 주세요.")
                    .setPositiveButton("이 시간에 음악 종료", (d, w) -> saveEndSchedule())
                    .setNegativeButton("취소", null).show();
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private void saveEndSchedule() {
        if (pendingEndAt <= System.currentTimeMillis()) {
            message("종료 시각이 이미 지났습니다. 다시 설정해 주세요.");
            return;
        }
        if (!RadioSchedule.notificationsAllowed(this)) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        ASK_END_NOTIFICATION);
            } else message("알림 권한을 확인해 주세요.");
            return;
        }
        try {
            RadioSchedule.setEnd(this, pendingEndAt);
            updateStatus();
            message("음악 종료 예약: " + RadioSchedule.endFormatted(this)
                    + " · BIGO 라이브는 직접 종료해야 해요.");
            // If playback is already active, bind its foreground timer to this exact
            // chosen wall-clock end as well. The OS alarm provides a backup.
            try {
                Intent end = new Intent(this, RadioPlaybackService.class)
                        .setAction(RadioPlaybackService.ACTION_SET_WALL_END)
                        .putExtra(RadioPlaybackService.EXTRA_END_AT, pendingEndAt);
                startService(end);
            } catch (RuntimeException ignored) {}
        } catch (RuntimeException ex) {
            message("종료 시각 예약에 실패했어요. 알림 설정을 확인해 주세요.");
        }
    }

    private void cancelEndSchedule() {
        RadioSchedule.cancelEnd(this);
        pendingEndAt = 0;
        try {
            startService(new Intent(this, RadioPlaybackService.class)
                    .setAction(RadioPlaybackService.ACTION_CANCEL_WALL_END));
        } catch (RuntimeException ignored) {}
        updateStatus();
        message("음악 종료 시각 예약을 취소했어. 자동 종료 없음.");
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions,
            int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != ASK_NOTIFICATION && request != ASK_END_NOTIFICATION
                && request != ASK_PAIR_NOTIFICATION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (request == ASK_PAIR_NOTIFICATION) savePair();
            else if (request == ASK_END_NOTIFICATION) saveEndSchedule();
            else saveSchedule();
        } else message("알림 권한을 허용하지 않아 예약하지 않았어요.");
    }

    private void cancelSchedule() {
        RadioSchedule.cancel(this);
        pendingStartAt = 0L;
        updateStatus();
        message("방송 시작 알림 예약을 취소했어요.");
    }

    private void handleStartIntent(Intent intent) {
        if (intent == null) return;
        if (intent.getBooleanExtra(RadioSchedule.EXTRA_AUTO_READY, false)) {
            intent.removeExtra(RadioSchedule.EXTRA_AUTO_READY);
            boolean attempted = intent.getBooleanExtra("music_launch_attempted", false);
            new AlertDialog.Builder(this)
                    .setTitle("예약한 MARU 음악 시작 시각")
                    .setMessage(attempted
                        ? "MARU에서 음악 자동 재생을 요청했어요. 실제 소리가 나는지 확인하세요. "
                          + "BIGO 라이브 방송은 자동 시작되지 않았어요."
                        : "MARU 자동 재생에 실패했거나 예약 종료 시각이 지났어요. "
                          + "저장곡과 휴대폰 상태를 확인하세요.")
                    .setPositiveButton("BIGO 열어 직접 방송하기",
                            (d, w) -> BigoBroadcastGuide.showStart(this))
                    .setNegativeButton("닫기", null).show();
            return;
        }
        if (intent.getBooleanExtra(RadioSchedule.EXTRA_END_READY, false)) {
            intent.removeExtra(RadioSchedule.EXTRA_END_READY);
            new AlertDialog.Builder(this).setTitle("MARU 음악 종료 시각")
                    .setMessage("MARU 음악의 재생 종료 시각입니다. "
                            + "BIGO 라이브 자체는 자동으로 끝낼 수 없으니 "
                            + "BIGO 오디오 LIVE 화면에서 종료 버튼을 눌러 주세요.")
                    .setPositiveButton("BIGO 열어 종료하기", (d, w) -> openBigoEndApp())
                    .setNegativeButton("닫기", null).show();
            return;
        }
        if (!intent.getBooleanExtra(RadioSchedule.EXTRA_READY, false)) return;
        intent.removeExtra(RadioSchedule.EXTRA_READY);
        new AlertDialog.Builder(this).setTitle("📻 방송 시작 준비 시간")
                .setMessage("선택한 분류의 Suno 원곡을 재생하고 BIGO LIVE를 열까요?\n\n"
                        + "BIGO는 시작 화면만 열립니다. 라이브 방송을 시작하려면 "
                        + "BIGO의 '오디오 라이브 → 방송하기' 버튼을 직접 눌러 주세요.")
                .setPositiveButton("음악 재생 + BIGO 열기", (dialog, which) -> beginBroadcast())
                .setNegativeButton("나중에", null).show();
    }

    private void beginBroadcast() {
        int cat = RadioProgram.active(this);
        List<Originals.Track> saved = Originals.list(this);
        Originals.Track first = null;
        for (Originals.Track t : saved) {
            if (cat == 0 || cat == 5 || RadioProgram.category(this, t) == cat) {
                first = t;
                break;
            }
        }
        if (first != null) {
            Intent play = new Intent(this, RadioPlaybackService.class)
                    .setAction(RadioPlaybackService.ACTION_PLAY)
                    .putExtra(RadioPlaybackService.EXTRA_HASH, first.hash);
            try {
                // Only explicitly configured end times are honored.
                // No automatic 60-minute stop on a freshly scheduled program.
                startForegroundService(play);
                long endAt = RadioSchedule.endAt(this);
                if (endAt > System.currentTimeMillis()) {
                    Intent timer = new Intent(this, RadioPlaybackService.class)
                            .setAction(RadioPlaybackService.ACTION_SET_WALL_END)
                            .putExtra(RadioPlaybackService.EXTRA_END_AT, endAt);
                    startService(timer);
                }
            } catch (RuntimeException ignored) {
                message("음악 서비스를 시작하지 못했어요. MARU 플레이어를 확인해 주세요.");
            }
        } else {
            message("선택한 장르에 원곡이 없어 음악을 재생하지 않았어요.");
        }
        openBigoApp();
    }

    private void openBigoApp() {
        BigoBroadcastGuide.openBigo(this, false);
    }

    private void openBigoEndApp() {
        BigoBroadcastGuide.openBigo(this, true);
    }

    private void replyLanguages() {
        new AlertDialog.Builder(this).setTitle("댓글 답변 언어")
                .setItems(RadioReplies.LANGUAGES, (d, lang) -> new AlertDialog.Builder(this)
                        .setTitle("방송 중 상황")
                        .setItems(RadioReplies.EVENTS, (d2, event) -> showReply(lang, event))
                        .setNegativeButton("닫기", null).show())
                .setNegativeButton("닫기", null).show();
    }

    private void showReply(int language, int event) {
        String text = RadioReplies.message(language, event);
        new AlertDialog.Builder(this).setTitle(RadioReplies.EVENTS[event])
                .setMessage(text + "\n\n※ 자동 전송이 아닌 복사 기능입니다.")
                .setPositiveButton("문구 복사", (d, w) -> {
                    ClipboardManager clips =
                            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clips != null) {
                        clips.setPrimaryClip(ClipData.newPlainText("MARU 답변", text));
                        message("답변 문구 복사 완료. BIGO 댓글창에 붙여넣어 주세요.");
                    }
                })
                .setNeutralButton("다른 문구", (d, w) -> replyLanguages())
                .setNegativeButton("닫기", null).show();
    }
}
