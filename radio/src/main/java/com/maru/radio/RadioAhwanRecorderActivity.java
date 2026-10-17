package com.maru.radio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

/**
 * Explicit MIC recording for SHORT Ahwan greeting/thanks/outro clips.
 * User taps record and stop, then separately enables broadcast playback.
 * Does NOT continuously monitor BIGO, capture another app's speaker output,
 * stream microphone audio or clone a voice. Recordings are app-private.
 */
public final class RadioAhwanRecorderActivity extends Activity {
    private static final int ASK_MIC = 930;
    private static final String[] SLOTS = {
            RadioAhwanClipSlot.OPENING, RadioAhwanClipSlot.GIFT,
            RadioAhwanClipSlot.FOLLOW, RadioAhwanClipSlot.CLOSING
    };
    private static final int WHITE = 0xFFF6F1FF;
    private MediaRecorder recorder;
    private MediaPlayer preview;
    private String pendingSlot;
    private String recordingSlot;
    private File temporary;
    private long startedAt;
    private LinearLayout root;
    private TextView status;

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setTextColor(WHITE);
        t.setTextSize(sp);
        t.setPadding(dp(4), dp(7), dp(4), dp(7));
        return t;
    }
    private void button(LinearLayout area, String title, Runnable work) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setOnClickListener(v -> work.run());
        area.addView(b, new LinearLayout.LayoutParams(-1, -2));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF120F22);
        if (Build.VERSION.SDK_INT >= 35) scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            v.setPadding(0, safe.top, 0, safe.bottom);
            return insets;
        });
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(40));
        scroll.addView(root);
        setContentView(scroll);
        redraw();
    }
    private void redraw() {
        root.removeAllViews();
        root.addView(text("🎙 아환 · 마이크 음성 녹음실", 24));
        root.addView(text("이 휴대폰 마이크로 방송 시작 인사·선물 감사·팔로우 감사·"
                + "방송 종료 인사를 각각 직접 녹음할 수 있습니다. "
                + "녹음한 문장만 재생하며 새 문장을 형 목소리로 만들어 내는 기능은 아닙니다. "
                + "BIGO 실제 청취자에게 전달되는지는 별도로 확인해야 합니다.", 14));
        status = text(recordingSlot == null ? "녹음 대기 · 모든 파일은 휴대폰 앱 내부 보관"
                : "🔴 녹음 중: " + RadioAhwanClipSlot.title(recordingSlot)
                + " · 같은 버튼을 눌러 종료", 14);
        root.addView(status);
        for (String slot : SLOTS) {
            root.addView(text("━━━━━━━━ " + RadioAhwanClipSlot.title(slot), 18));
            String uri = RadioDjSettings.recordedClipUri(this, slot);
            boolean saved = uri != null && !uri.isEmpty();
            root.addView(text(saved
                    ? "녹음 등록됨 · 방송 사용 " + (RadioDjSettings.clipEnabled(this, slot) ? "켜짐" : "꺼짐")
                    : "등록된 녹음 없음", 13));
            button(root, slot.equals(recordingSlot)
                    ? "■ 녹음 끝내고 저장" : "🎤 마이크로 직접 녹음", () -> toggleRecording(slot));
            button(root, "▶ 실제 목소리 미리듣기", () -> playPreview(slot));
            button(root, RadioDjSettings.clipEnabled(this,slot)
                    ? "☑ 방송에서 사용 중 · 누르면 해제" : "□ 방송에서 사용하기",
                    () -> toggleEnabled(slot));
        }
        button(root, "← 아환 설정 화면으로", this::finish);
    }
    private void toggleEnabled(String slot) {
        if (recordingSlot != null) { status.setText("녹음을 먼저 끝내 주세요."); return; }
        String value = RadioDjSettings.recordedClipUri(this, slot);
        if (!RadioDjSettings.clipEnabled(this, slot) && (value == null || value.isEmpty())) {
            status.setText("먼저 해당 인사 문장을 녹음해 주세요.");
            return;
        }
        RadioDjSettings.setClipEnabled(this, slot,
                !RadioDjSettings.clipEnabled(this, slot));
        redraw();
    }
    private void toggleRecording(String slot) {
        if (recordingSlot != null) {
            if (recordingSlot.equals(slot)) finishRecording(true);
            else status.setText("녹음 중인 인사를 먼저 종료해 주세요.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingSlot = slot;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, ASK_MIC);
            return;
        }
        beginRecording(slot);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != ASK_MIC) return;
        String slot = pendingSlot;
        pendingSlot = null;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED
                && RadioAhwanClipSlot.valid(slot)) beginRecording(slot);
        else status.setText("마이크 권한을 허용해야 직접 녹음할 수 있습니다.");
    }
    private void beginRecording(String slot) {
        stopPreview();
        try {
            File dir = new File(getFilesDir(), "ahwan_voice");
            if (!dir.isDirectory() && !dir.mkdirs())
                throw new IOException("녹음 폴더 생성 불가");
            temporary = File.createTempFile("capture_", ".m4a", dir);
            recorder = Build.VERSION.SDK_INT >= 31
                    ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(temporary.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            startedAt = SystemClock.elapsedRealtime();
            recordingSlot = slot;
            redraw();
        } catch (Exception e) {
            abandonRecording();
            status.setText("마이크 녹음을 시작할 수 없어요. 다른 앱이 마이크 사용 중인지 확인하세요.");
        }
    }
    private void finishRecording(boolean save) {
        String slot = recordingSlot;
        File temp = temporary;
        if (recorder == null || slot == null) return;
        long duration = SystemClock.elapsedRealtime() - startedAt;
        if (duration < 1000L) save = false;
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            save = false; // too short / microphone error, old stored clip is untouched
        }
        recorder.release();
        recorder = null;
        recordingSlot = null;
        temporary = null;
        if (save && temp != null && temp.isFile() && temp.length() > 1500L) {
            File folder = new File(getFilesDir(), "ahwan_voice");
            File destination = new File(folder, RadioAhwanClipSlot.filename(slot));
            // Avoid damaging previously saved voice on a failed new recording.
            File staged = new File(folder, "ready_" + RadioAhwanClipSlot.filename(slot));
            if (staged.exists()) staged.delete();
            if (temp.renameTo(staged)) {
                if (destination.exists() && !destination.delete()) {
                    staged.delete();
                    save = false;
                } else if (!staged.renameTo(destination)) {
                    save = false;
                } else {
                    RadioDjSettings.setRecordedClipUri(this, slot, Uri.fromFile(destination));
                    // Playback remains OFF until the owner explicitly enables it.
                    save = true;
                }
            } else save = false;
        } else save = false;
        if (temp != null && temp.exists()) temp.delete();
        redraw();
        status.setText(save ? "녹음 저장 완료 · 아래 '방송에서 사용하기'를 눌러 주세요."
                : "녹음이 너무 짧거나 실패했어요. 이전 녹음은 그대로 보관됩니다.");
    }
    private void abandonRecording() {
        if (recorder != null) {
            try { recorder.stop(); } catch (RuntimeException ignored) {}
            recorder.release();
            recorder = null;
        }
        recordingSlot = null;
        if (temporary != null && temporary.exists()) temporary.delete();
        temporary = null;
    }
    private void playPreview(String slot) {
        if (recordingSlot != null) { status.setText("녹음을 먼저 종료해 주세요."); return; }
        stopPreview();
        String audio = RadioDjSettings.recordedClipUri(this, slot);
        if (audio == null || audio.isEmpty()) {
            status.setText("먼저 녹음부터 해 주세요.");
            return;
        }
        try {
            preview = new MediaPlayer();
            preview.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            preview.setDataSource(this, Uri.parse(audio));
            preview.setOnCompletionListener(p -> stopPreview());
            preview.prepare();
            preview.start();
            status.setText("▶ " + RadioAhwanClipSlot.title(slot)
                    + " · 휴대폰에서만 미리듣기 (BIGO 송출 검증 아님)");
        } catch (Exception e) {
            stopPreview();
            status.setText("녹음 파일 재생 실패 · 권한이나 저장 파일을 확인해 주세요.");
        }
    }
    private void stopPreview() {
        if (preview != null) {
            try { preview.stop(); } catch (RuntimeException ignored) {}
            preview.release();
            preview = null;
        }
    }
    @Override protected void onPause() {
        super.onPause();
        stopPreview();
        // Never leave mic recording active when the user moves to BIGO.
        if (recordingSlot != null) {
            abandonRecording();
            redraw();
            status.setText("화면을 나가 녹음이 중단됐어요. 기존 녹음은 그대로입니다.");
        }
    }
    @Override protected void onDestroy() {
        abandonRecording();
        stopPreview();
        super.onDestroy();
    }
}
