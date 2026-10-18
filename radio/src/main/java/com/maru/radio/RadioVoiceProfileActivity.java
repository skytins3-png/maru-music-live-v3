package com.maru.radio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Consent-only private voice-reference recorder for FUTURE licensed voice model.
 * Captures two separate owner clips: spontaneous speaking and a cappella singing.
 *
 * NO background capture. NO voice model inference. NO network transfer.
 * Blend control saves only the user's requested target percentages and NEVER
 * claims to change existing Android TTS, vocals, or BIGO mic input.
 * Back up both clips BEFORE uninstalling the app.
 */
public final class RadioVoiceProfileActivity extends Activity {
    private static final String PREF = "maru_ahwan_voice_identity";
    private static final String KEY_CONSENT = "consent_local_samples";
    private static final String KEY_OWNER_PERCENT = "owner_percent";
    private static final int REQUEST_MIC = 970;
    private static final int REQUEST_EXPORT = 971;
    private static final int REQUEST_IMPORT_MP3 = 972;
    private static final int MAX_IMPORT_BYTES = 30 * 1024 * 1024;
    private static final int WHITE = 0xFFF7F0FF;
    private static final int MUTED = 0xFFC2B6D9;
    private static final int BG = 0xFF140F24;

    private LinearLayout root;
    private TextView status;
    private TextView blendText;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private File capture;
    private String activeSlot;
    private String pendingSlot;
    private String exportSlot;
    private String importSlot;
    private long started;
    private boolean rendering;

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + .5f);
    }
    private TextView text(String content, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(content);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setPadding(dp(3), dp(7), dp(3), dp(7));
        return v;
    }
    private void button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setOnClickListener(view -> action.run());
        root.addView(b, new LinearLayout.LayoutParams(-1, -2));
    }
    private SharedPreferences preferences() { return getSharedPreferences(PREF, MODE_PRIVATE); }
    private boolean consent() { return preferences().getBoolean(KEY_CONSENT, false); }
    private File folder() { return new File(getFilesDir(), "ahwan_voice_identity"); }
    private File recordedPath(String slot) {
        return new File(folder(), RadioVoiceProfileState.filename(slot));
    }
    private File importedMp3Path(String slot) {
        return new File(folder(), slot.equals(RadioVoiceProfileState.TALK)
                ? "owner_speech.mp3" : "owner_singing.mp3");
    }
    private File path(String slot) {
        File imported = importedMp3Path(slot);
        return imported.isFile() ? imported : recordedPath(slot);
    }
    private boolean present(String slot) {
        File file = path(slot);
        return file.isFile() && file.length() >= 1500;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 35) {
            scroller.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(17), dp(12), dp(17), dp(40));
        scroller.addView(root);
        setContentView(scroller);
        draw();
    }
    private void draw() {
        if (root == null) return;
        rendering = true;
        root.removeAllViews();
        root.addView(text("🎤 아환 · AI 목소리 제작 준비실", 23, WHITE));
        root.addView(text("형의 실제 음색과 허가된 전문 DJ 음색을 하나로 섞어 "
                + "대화·노래에 쓰는 것이 최종 목표예요. "
                + "이 화면은 말하기/노래하기 원본 샘플을 모으는 기능이며 "
                + "AI 음성 복제 모델은 아직 연결되지 않았습니다.", 14, MUTED));
        CheckBox agreement = new CheckBox(this);
        agreement.setText("형 본인의 목소리이며 샘플을 이 기기에 보관하는 데 동의");
        agreement.setTextSize(15);
        agreement.setTextColor(WHITE);
        agreement.setChecked(consent());
        agreement.setOnCheckedChangeListener((button, accepted) -> {
            if (rendering) return;
            if (accepted) {
                new AlertDialog.Builder(this)
                        .setTitle("형의 음성 샘플 녹음 동의")
                        .setMessage("내 목소리만 녹음하고 앱 내부에 보관합니다. "
                                + "음성 샘플은 인터넷에 전송하지 않으며, "
                                + "새로운 AI 모델 학습·음성 복제에 자동으로 쓰지 않습니다. "
                                + "다른 사람 목소리를 허락 없이 녹음하거나 복제하지 마세요. "
                                + "앱을 제거하면 백업하지 않은 샘플은 삭제될 수 있습니다.")
                        .setPositiveButton("내 목소리 녹음에 동의", (d, w) -> {
                            preferences().edit().putBoolean(KEY_CONSENT, true).apply();
                            draw();
                        })
                        .setNegativeButton("취소", (d, w) -> draw())
                        .show();
            } else {
                cancelRecording();
                stopPlayer();
                preferences().edit().putBoolean(KEY_CONSENT, false).apply();
                draw();
            }
        });
        root.addView(agreement);
        root.addView(text("동의를 취소하면 새 녹음·미리듣기가 중단됩니다. "
                + "이미 저장된 파일은 보존되므로 각각 삭제할 수 있습니다.", 12, MUTED));
        status = text(activeSlot == null ? "상태: 대기 중"
                : "🔴 녹음 중: " + RadioVoiceProfileState.title(activeSlot), 14, WHITE);
        root.addView(status);
        addSlot(RadioVoiceProfileState.TALK,
                "평소 목소리로 40~60초, 잡음 없이 편안하게 말해 주세요. "
                        + "개인정보나 다른 사람 실명은 읽지 마세요.");
        addSlot(RadioVoiceProfileState.SING,
                "반주 없이 30~60초, 형이 편한 음역에서 노래해 주세요. "
                        + "무리한 고음은 피하고 말소리와 비슷한 위치에서 자연스럽게 불러 주세요.");
        root.addView(text("━━━━━━━━ 예상 혼합 음색 비율", 18, WHITE));
        int value = RadioVoiceProfileState.clampPercent(
                preferences().getInt(KEY_OWNER_PERCENT,
                        RadioVoiceProfileState.DEFAULT_OWNER_PERCENT));
        blendText = text(RadioVoiceProfileState.blendDescription(value), 14, WHITE);
        root.addView(blendText);
        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(value);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int next, boolean fromUser) {
                if (fromUser) {
                    int owner = RadioVoiceProfileState.clampPercent(next);
                    preferences().edit().putInt(KEY_OWNER_PERCENT, owner).apply();
                    blendText.setText(RadioVoiceProfileState.blendDescription(owner));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        root.addView(slider);
        boolean ready = RadioVoiceProfileState.readyForConsentBasedTraining(
                consent(), present(RadioVoiceProfileState.TALK),
                present(RadioVoiceProfileState.SING));
        root.addView(text(ready
                        ? "✅ 말하기·노래하기 샘플 준비됨. 아직 AI 학습이나 실제 음색 혼합을 실행한 것은 아닙니다."
                        : "📋 준비 상태: 동의와 말하기·노래하기 샘플 두 개가 모두 필요합니다.",
                14, ready ? 0xFFA7E9BC : MUTED));
        root.addView(text("전문 DJ 음색은 사용권이 명확한 합성 음성으로만 사용합니다. "
                + "말하기 TTS와 AI 노래 모델은 별도로 연결·검증해야 합니다. "
                + "BIGO 청취자에게 실제 방송 음성이 들리는지는 이 화면만으로 해결되지 않습니다.", 12, MUTED));
        button("← MARU로 돌아가기", this::finish);
        rendering = false;
    }
    private void addSlot(String slot, String guide) {
        root.addView(text("━━━━━━━━ " + RadioVoiceProfileState.title(slot), 18, WHITE));
        root.addView(text(guide, 13, MUTED));
        root.addView(text(present(slot) ? "✅ 내 목소리 샘플 저장됨" : "○ 샘플 아직 없음",
                14, WHITE));
        button(slot.equals(activeSlot) ? "■ 녹음 끝내고 저장" : "🎙 내 목소리 녹음",
                () -> toggle(slot));
        button("📥 기존 MP3에서 내 목소리 가져오기", () -> chooseImport(slot));
        button("▶ 이 목소리만 미리듣기", () -> preview(slot));
        button("💾 내 녹음 외부 폴더에 백업", () -> exportSample(slot));
        if (present(slot)) button("🗑 이 음성 샘플만 삭제", () -> askDelete(slot));
    }
    private void toggle(String slot) {
        if (slot.equals(activeSlot)) {
            saveRecording();
            return;
        }
        if (activeSlot != null) {
            status.setText("먼저 진행 중인 녹음을 끝내 주세요.");
            return;
        }
        if (!consent()) {
            status.setText("먼저 형의 실제 목소리 녹음에 동의해 주세요.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingSlot = slot;
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, REQUEST_MIC);
            return;
        }
        startRecording(slot);
    }
    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req != REQUEST_MIC) return;
        String slot = pendingSlot;
        pendingSlot = null;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED
                && consent() && RadioVoiceProfileState.valid(slot)) startRecording(slot);
        else if (status != null) status.setText("마이크 사용 동의가 없어 녹음하지 않았어요.");
    }
    /**
     * User-picked MP3 reference: no audio permission sweep, no background
     * recording, no network, no embedding personal audio in GitHub builds.
     * The original recording and any previously imported reference remain
     * untouched if streaming into the staging file fails.
     */
    private void chooseImport(String slot) {
        if (!consent()) { status.setText("본인 목소리 보관 동의를 먼저 해 주세요."); return; }
        if (activeSlot != null) { status.setText("녹음을 먼저 끝내 주세요."); return; }
        if (!RadioVoiceProfileState.valid(slot)) return;
        importSlot = slot;
        Intent select = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("audio/mpeg");
        select.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(select, REQUEST_IMPORT_MP3); }
        catch (RuntimeException e) {
            importSlot = null;
            status.setText("MP3 파일 선택 화면을 열 수 없어요.");
        }
    }
    private void importMp3(String slot, Uri selected) {
        if (!consent() || !RadioVoiceProfileState.valid(slot) || activeSlot != null) return;
        File temp = null;
        try {
            File dir = folder();
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Storage unavailable");
            temp = File.createTempFile("mp3_import_", ".tmp", dir);
            int bytes = 0;
            byte[] buffer = new byte[16 * 1024];
            try (InputStream input = getContentResolver().openInputStream(selected);
                 FileOutputStream out = new FileOutputStream(temp)) {
                if (input == null) throw new IOException("Cannot open selected file");
                int n;
                while ((n = input.read(buffer)) != -1) {
                    bytes += n;
                    if (bytes > MAX_IMPORT_BYTES) throw new IOException("Too large");
                    out.write(buffer, 0, n);
                }
                out.flush();
            }
            if (bytes < 1500 || !looksLikeMp3(temp))
                throw new IOException("Not a supported MP3 recording");
            if (!safelyReplace(temp, importedMp3Path(slot)))
                throw new IOException("Could not preserve previous sample");
            File priorRecording = recordedPath(slot);
            if (priorRecording.exists()) priorRecording.delete();
            draw();
            status.setText("✅ MP3 음성 원본을 형의 " + RadioVoiceProfileState.title(slot)
                    + " 샘플로 가져왔어요. AI 복제나 인터넷 업로드는 하지 않았습니다.");
        } catch (IOException | SecurityException e) {
            status.setText("MP3 가져오기 실패. 30MB 이하 MP3 파일과 접근 권한을 확인하세요. "
                    + "기존 녹음은 유지됩니다.");
        } finally {
            if (temp != null && temp.exists()) temp.delete();
        }
    }
    private boolean looksLikeMp3(File source) throws IOException {
        try (FileInputStream input = new FileInputStream(source)) {
            byte[] first = new byte[3];
            if (input.read(first) != 3) return false;
            return (first[0] == 'I' && first[1] == 'D' && first[2] == '3')
                    || ((first[0] & 0xFF) == 0xFF
                            && ((first[1] & 0xE0) == 0xE0));
        }
    }

    private void startRecording(String slot) {
        stopPlayer();
        if (!consent()) return;
        try {
            File dir = folder();
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Folder unavailable");
            capture = File.createTempFile("voice_", ".m4a", dir);
            recorder = Build.VERSION.SDK_INT >= 31
                    ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(capture.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            started = SystemClock.elapsedRealtime();
            activeSlot = slot;
            draw();
        } catch (Exception e) {
            cancelRecording();
            status.setText("녹음을 시작하지 못했습니다. 다른 앱의 마이크 사용 여부를 확인하세요.");
        }
    }
    private void saveRecording() {
        String slot = activeSlot;
        File temp = capture;
        if (recorder == null || !RadioVoiceProfileState.valid(slot)) return;
        boolean okay = SystemClock.elapsedRealtime() - started >= 5000L;
        try { recorder.stop(); }
        catch (RuntimeException failure) { okay = false; }
        recorder.release();
        recorder = null;
        activeSlot = null;
        capture = null;
        if (okay && temp != null && temp.length() >= 1500L)
            okay = safelyReplace(temp, recordedPath(slot));
        else okay = false;
        if (okay) {
            File imported = importedMp3Path(slot);
            if (imported.exists()) imported.delete();
        }
        if (temp != null && temp.exists()) temp.delete();
        draw();
        status.setText(okay ? "✅ 목소리 샘플 저장됨. AI 복제는 아직 시작되지 않았습니다."
                : "녹음이 너무 짧거나 저장 실패. 이전 샘플은 보존했습니다.");
    }
    private boolean safelyReplace(File temp, File target) {
        File old = new File(target.getParentFile(), target.getName() + ".old");
        if (old.exists() && !old.delete()) return false;
        boolean existing = target.exists();
        if (existing && !target.renameTo(old)) return false;
        if (!temp.renameTo(target)) {
            if (existing) old.renameTo(target);
            return false;
        }
        if (old.exists()) old.delete();
        return true;
    }
    private void cancelRecording() {
        if (recorder != null) {
            try { recorder.stop(); } catch (RuntimeException ignored) {}
            recorder.release();
            recorder = null;
        }
        activeSlot = null;
        if (capture != null && capture.exists()) capture.delete();
        capture = null;
    }
    private void stopPlayer() {
        if (player != null) {
            try { player.stop(); } catch (RuntimeException ignored) {}
            player.release();
            player = null;
        }
    }
    private void preview(String slot) {
        if (!consent()) { status.setText("먼저 본인 목소리 동의가 필요해요."); return; }
        if (activeSlot != null) { status.setText("녹음을 먼저 끝내 주세요."); return; }
        if (!present(slot)) { status.setText("녹음된 샘플이 없습니다."); return; }
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(path(slot).getAbsolutePath());
            player.setOnCompletionListener(p -> stopPlayer());
            player.prepare();
            player.start();
            status.setText("▶ 녹음한 원래 목소리만 휴대폰에서 재생 중 · 합성 음성 아님");
        } catch (Exception e) {
            stopPlayer();
            status.setText("샘플 재생 실패. 파일을 확인해 주세요.");
        }
    }
    private void exportSample(String slot) {
        if (activeSlot != null) { status.setText("녹음을 먼저 종료해 주세요."); return; }
        if (!present(slot)) { status.setText("백업할 음성이 없습니다."); return; }
        exportSlot = slot;
        File source = path(slot);
        boolean mp3 = source.getName().endsWith(".mp3");
        Intent action = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mp3 ? "audio/mpeg" : "audio/mp4")
                .putExtra(Intent.EXTRA_TITLE, source.getName());
        try { startActivityForResult(action, REQUEST_EXPORT); }
        catch (RuntimeException e) {
            exportSlot = null;
            status.setText("백업 위치 선택 화면을 열 수 없습니다.");
        }
    }
    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQUEST_IMPORT_MP3) {
            String slot = importSlot;
            importSlot = null;
            if (result == RESULT_OK && data != null && data.getData() != null
                    && RadioVoiceProfileState.valid(slot))
                importMp3(slot, data.getData());
            return;
        }
        if (req != REQUEST_EXPORT) return;
        String slot = exportSlot;
        exportSlot = null;
        if (result != RESULT_OK || data == null || data.getData() == null
                || !RadioVoiceProfileState.valid(slot)) return;
        Uri dest = data.getData();
        try (FileInputStream in = new FileInputStream(path(slot));
             OutputStream out = getContentResolver().openOutputStream(dest)) {
            if (out == null) throw new IOException("Export destination not writable");
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
            status.setText("✅ 외부 폴더에 목소리 백업 완료. 파일을 확인해 주세요.");
        } catch (Exception e) {
            status.setText("백업에 실패했습니다. 다른 저장 폴더로 다시 해 주세요.");
        }
    }
    private void askDelete(String slot) {
        if (activeSlot != null) {
            status.setText("녹음을 끝낸 다음 삭제하세요.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("목소리 샘플 삭제")
                .setMessage(RadioVoiceProfileState.title(slot)
                        + " 샘플만 삭제할까요? 기존 노래·방송 인사 녹음에는 영향이 없습니다. "
                        + "앱 내부 파일은 복구하기 어려우니 먼저 백업하세요.")
                .setPositiveButton("이 샘플 삭제", (d, w) -> {
                    stopPlayer();
                    File mp3 = importedMp3Path(slot);
                    File m4a = recordedPath(slot);
                    boolean removed = (!mp3.exists() || mp3.delete())
                            && (!m4a.exists() || m4a.delete());
                    draw();
                    status.setText(removed ? "이 음성 샘플을 삭제했습니다."
                            : "삭제하지 못했습니다. 파일은 보존됩니다.");
                })
                .setNegativeButton("취소", null)
                .show();
    }
    @Override protected void onPause() {
        super.onPause();
        stopPlayer();
        if (activeSlot != null) {
            cancelRecording();
            draw();
            status.setText("화면을 벗어나 녹음을 취소했습니다. 기존 파일은 유지됩니다.");
        }
    }
    @Override protected void onDestroy() {
        cancelRecording();
        stopPlayer();
        super.onDestroy();
    }
}
