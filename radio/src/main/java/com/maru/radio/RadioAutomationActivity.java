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
    private static final int BG = 0xFF110E21;
    private static final int PANEL = 0xFF281F43;
    private static final int WHITE = 0xFFF7F1FF;
    private static final int PURPLE = 0xFFB89BFF;

    private TextView scheduleStatus;
    private TextView note;
    private long pendingStartAt;

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        render();
        handleStartIntent(getIntent());
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
        content.addView(label("📻 MARU 방송 자동 운영", 27, PURPLE));
        content.addView(label("방송 준비 알림 · 음악 종료시간 · 댓글 답변 문구", 14, WHITE));

        LinearLayout schedule = card(content);
        schedule.addView(label("① 방송 시작 시각 예약", 21, WHITE));
        schedule.addView(label("휴대폰에 시작 알림을 띄워요. 알림을 누른 뒤 방송 시작을 확인해야 해요.",
                14, 0xFFDDD2E8));
        scheduleStatus = label("시작 알림 예약 없음", 16, PURPLE);
        schedule.addView(scheduleStatus);
        schedule.addView(button("📅 시작 시간 지정", this::chooseClock));
        schedule.addView(button("시작 알림 취소", this::cancelSchedule));

        LinearLayout time = card(content);
        time.addView(label("② 방송 종료 시간", 21, WHITE));
        time.addView(label("MARU RADIO 화면의 '⏱ 시간 설정 · 시작'에서 5~720분 뒤 "
                + "음악을 종료할 수 있어요. BIGO 실제 종료는 화면에서 확인해야 해요.",
                14, 0xFFDDD2E8));
        time.addView(button("음악 종료 시간 설정 화면으로", () -> {
            startActivity(new Intent(this, RadioActivity.class));
            finish();
        }));

        LinearLayout replies = card(content);
        replies.addView(label("③ 댓글·선물 다국어 답변", 21, WHITE));
        replies.addView(label("한국어·중국어·영어·일본어·러시아어. "
                + "작성된 문구를 직접 확인하고 복사해 BIGO에 붙여넣으세요. "
                + "댓글 자동 수집이나 자동 전송은 아직 지원되지 않아요.",
                14, 0xFFDDD2E8));
        replies.addView(button("💬 댓글 답변 문구 선택", this::replyLanguages));

        LinearLayout notes = card(content);
        notes.addView(label("방송 자동 운영 관련 안내", 18, PURPLE));
        notes.addView(label("BIGO 방송 시작 버튼은 사용자가 확인한 경우에만 "
                + "기존 접근성 보조 기능을 통해 누르도록 시도합니다. "
                + "휴대폰이 잠겨 있을 때 무인으로 라이브를 시작하지 않습니다. "
                + "휴대폰 절전 설정에 따라 시작 알림이 늦을 수 있어요.",
                13, 0xFFDDD2E8));

        note = label("원곡은 재가공하지 않아요.", 13, 0xFFA8E3BE);
        content.addView(note);
        content.addView(button("← MARU RADIO 돌아가기", this::finish));
        setContentView(scroll);
        updateStatus();
    }

    private void updateStatus() {
        if (scheduleStatus != null) scheduleStatus.setText(
                "방송 준비 알림: " + RadioSchedule.formatted(this));
    }

    private void message(String value) { if (note != null) note.setText(value); }

    private void chooseClock() {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, min) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.HOUR_OF_DAY, hour);
            chosen.set(Calendar.MINUTE, min);
            chosen.set(Calendar.SECOND, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            if (!chosen.after(now)) chosen.add(Calendar.DAY_OF_MONTH, 1);
            pendingStartAt = chosen.getTimeInMillis();
            new AlertDialog.Builder(this).setTitle("방송 시작 알림 예약")
                    .setMessage("예약 시각: " + DateFormat.format("M월 d일 HH:mm", chosen)
                            + "\n\n예약된 시각에 알림을 표시합니다. "
                            + "알림을 누른 뒤 '방송 시작'을 직접 확인해 주세요. "
                            + "기기가 잠겨 있을 때 자동으로 BIGO 방송을 시작하지 않습니다.")
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
        try {
            RadioSchedule.set(this, pendingStartAt);
            updateStatus();
            message("알림 예약 완료. 휴대폰 절전 상태에서는 늦게 도착할 수 있어요.");
        } catch (RuntimeException ex) {
            message("알람 예약에 실패했습니다. 알림/배터리 설정을 확인해 주세요.");
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions,
            int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != ASK_NOTIFICATION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            saveSchedule();
        else message("알림 권한을 거부하여 예약하지 않았어요.");
    }

    private void cancelSchedule() {
        RadioSchedule.cancel(this);
        pendingStartAt = 0L;
        updateStatus();
        message("방송 시작 알림 예약을 취소했어요.");
    }

    private void handleStartIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(RadioSchedule.EXTRA_READY, false)) return;
        intent.removeExtra(RadioSchedule.EXTRA_READY);
        new AlertDialog.Builder(this).setTitle("📻 방송 시작 준비 시간")
                .setMessage("선택한 분류의 Suno 원곡을 재생하고 BIGO LIVE를 열까요?\n\n"
                        + "BIGO 방송 시작 버튼은 접근성 지원이 켜져 있을 때만 누르도록 "
                        + "시도하며, 라이브 시작 여부를 BIGO 화면에서 확인해야 해요.")
                .setPositiveButton("음악 재생 + BIGO 열기", (dialog, which) -> beginBroadcast())
                .setNegativeButton("나중에", null).show();
    }

    private void beginBroadcast() {
        int cat = RadioProgram.active(this);
        List<Originals.Track> saved = Originals.list(this);
        Originals.Track first = null;
        for (Originals.Track t : saved) {
            if (cat == 0 || RadioProgram.category(this, t) == cat) {
                first = t;
                break;
            }
        }
        if (first != null) {
            Intent play = new Intent(this, RadioPlaybackService.class)
                    .setAction(RadioPlaybackService.ACTION_PLAY)
                    .putExtra(RadioPlaybackService.EXTRA_HASH, first.hash);
            try {
                // Both service intents are issued while the user-confirmation Activity
                // is foreground; do not start a new service after BIGO backgrounds us.
                startForegroundService(play);
                Intent timer = new Intent(this, RadioPlaybackService.class)
                        .setAction(RadioPlaybackService.ACTION_SET_TIMER)
                        .putExtra(RadioPlaybackService.EXTRA_MINUTES,
                                RadioProgram.durationMinutes(this))
                        .putExtra(RadioPlaybackService.EXTRA_END_BIGO,
                                RadioProgram.autoEndBigo(this));
                startService(timer);
            } catch (RuntimeException ignored) {
                message("음악 서비스를 시작하지 못했어요. MARU 플레이어를 확인해 주세요.");
            }
        } else {
            message("선택한 장르에 원곡이 없어 음악을 재생하지 않았어요.");
        }
        if (BigoControlAssist.isConnected(this)) BigoControlAssist.queue(this, true);
        Intent bigo = getPackageManager().getLaunchIntentForPackage("sg.bigo.live");
        if (bigo == null) {
            message("BIGO 앱을 찾지 못했어요. 앱이 설치되었는지 확인해 주세요.");
            return;
        }
        try { startActivity(bigo); }
        catch (RuntimeException e) { message("BIGO를 실행하지 못했어요."); }
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
