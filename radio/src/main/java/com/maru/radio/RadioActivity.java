package com.maru.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * New standalone radio app. NO old MARU screen, AI, OCR, WebView or app-volume tricks.
 * This app prepares actual original audio files. BIGO controls live broadcast mixing.
 */
public final class RadioActivity extends Activity {
    private static final int PICK_AUDIO = 101;
    private static final int EXPORT_AUDIO = 102;
    private static final int BACKGROUND = 0xFF110E21;
    private static final int PANEL = 0xFF211B39;
    private static final int VIOLET = 0xFFB89BFF;
    private static final int WHITE = 0xFFF7F1FF;

    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private MediaPlayer preview;
    private Originals.Track exporting;
    private LinearLayout songList;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        build();
        acceptShare(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptShare(intent);
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(35));
        scroll.addView(root);

        root.addView(label("MARU RADIO", 31, VIOLET, true));
        root.addView(label("Suno 원곡 · BIGO 오디오 LIVE", 16, WHITE, false));
        root.addView(label("새로 제작한 음악 전용 경량 앱", 13, 0xFFB1A6C5, false));
        gap(root, 17);

        card(root, "1  SUNO에서 내 원곡 준비",
                "Suno에서 만든 원곡을 MP3/WAV로 저장하세요. 파일을 선택하거나 다른 앱의 '공유'로 MARU RADIO에 보낼 수 있어요.",
                "Suno 열기", this::openSuno,
                "원곡 파일 가져오기", this::pickFiles);

        card(root, "2  방송할 원곡 선택",
                "원곡은 그대로 보관하고 미리듣기만 제공합니다. BIGO 송출 전에 선택한 음악을 휴대폰의 음악 폴더에 저장하세요.",
                "원곡 목록 새로고침", this::refreshSongs,
                null, null);

        songList = new LinearLayout(this);
        songList.setOrientation(LinearLayout.VERTICAL);
        root.addView(songList);
        gap(root, 8);

        card(root, "3  BIGO 오디오 LIVE",
                "BIGO 안에서 '도구 → 음악/믹서'의 로컬 파일 선택이 있을 때 저장한 원곡을 불러오세요. BIGO가 제공하는 기능에 따라 사용 여부가 달라집니다.",
                "BIGO LIVE 열기", this::openBigo,
                "송출 음량 확인 방법", this::broadcastGuide);

        status = label("준비 완료 · 원곡 파일은 변경하지 않아요.", 13,
                0xFF9BDEBC, false);
        root.addView(status);
        setContentView(scroll);
        refreshSongs();
    }

    private void card(LinearLayout root, String title, String body,
                      String button1, Runnable action1, String button2, Runnable action2) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(17), dp(15), dp(15));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        bg.setCornerRadius(dp(16));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        root.addView(box, lp);
        box.addView(label(title, 19, WHITE, true));
        TextView detail = label(body, 14, 0xFFC6C0D5, false);
        detail.setPadding(0, dp(8), 0, dp(8));
        box.addView(detail);
        box.addView(button(button1, action1));
        if (button2 != null) box.addView(button(button2, action2));
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(3), 0, dp(4));
        return t;
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(15);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void gap(LinearLayout box, int dp) {
        View v = new View(this);
        box.addView(v, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, PICK_AUDIO);
    }

    private void openSuno() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://suno.com/")));
        } catch (Exception ex) {
            say("Suno 앱 또는 브라우저를 열 수 없어요.");
        }
    }

    private void openBigo() {
        stopPreview();
        Intent open = getPackageManager().getLaunchIntentForPackage("sg.bigo.live");
        if (open == null) {
            new AlertDialog.Builder(this).setMessage(
                    "BIGO LIVE가 설치되어 있는지 확인하세요. 방송 송출은 BIGO 앱에서 시작합니다.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        try {
            startActivity(open);
        } catch (Exception e) {
            say("BIGO를 열 수 없어요.");
        }
    }

    private void broadcastGuide() {
        new AlertDialog.Builder(this)
            .setTitle("원곡 · 방송 음량 확인")
            .setMessage("① 아래 음악 목록에서 'BIGO용 저장'을 눌러 원본 MP3/WAV를 저장합니다.\n\n"
                + "② BIGO 오디오 LIVE를 실행해 도구 → 음악/믹서에서 로컬 음원을 선택할 수 있는지 확인합니다.\n\n"
                + "③ BIGO 내부 음악 입력이 지원될 때 그곳에서 파일을 선택하세요.\n\n"
                + "④ 다른 휴대폰으로 시청하며 방송 볼륨이 유지되는지 검사합니다.\n\n"
                + "주의: Suno 앱에서 바로 재생한 소리를 MARU가 BIGO 마이크로 보내거나 "
                + "휴대폰 볼륨을 0으로 해도 송출을 고정하는 기능은 구현되어 있지 않습니다.")
            .setPositiveButton("확인", null).show();
    }

    private void refreshSongs() {
        if (songList == null) return;
        songList.removeAllViews();
        List<Originals.Track> tracks = Originals.list(this);
        if (tracks.isEmpty()) {
            TextView empty = label("등록된 원곡 없음 · 위에서 가져오기를 눌러주세요.", 14,
                    0xFFB8AABF, false);
            songList.addView(empty);
            return;
        }
        TextView count = label("보관 원곡 " + tracks.size() + "곡", 17, VIOLET, true);
        songList.addView(count);
        for (Originals.Track t : tracks) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xFF19152D);
            background.setCornerRadius(dp(12));
            row.setBackground(background);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            songList.addView(row, lp);
            row.addView(label(t.title, 15, WHITE, true));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button listen = button("미리듣기", () -> togglePreview(t));
            Button export = button("BIGO용 저장", () -> export(t));
            Button remove = button("삭제", () -> confirmDelete(t));
            actions.addView(listen, new LinearLayout.LayoutParams(0, -2, 1));
            actions.addView(export, new LinearLayout.LayoutParams(0, -2, 1.3f));
            actions.addView(remove, new LinearLayout.LayoutParams(0, -2, 0.85f));
            row.addView(actions);
        }
    }

    private void togglePreview(Originals.Track track) {
        stopPreview();
        try {
            final MediaPlayer m = new MediaPlayer();
            m.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            m.setDataSource(track.path);
            m.setOnPreparedListener(player -> {
                if (preview == player) {
                    player.setVolume(1f, 1f);
                    player.start();
                    say("미리듣기 중: " + track.title);
                }
            });
            m.setOnCompletionListener(player -> stopPreview());
            m.setOnErrorListener((player, what, extra) -> {
                stopPreview();
                say("원곡 재생에 실패했어요.");
                return true;
            });
            preview = m;
            m.prepareAsync();
        } catch (Exception e) {
            stopPreview();
            say("원곡을 열 수 없어요.");
        }
    }

    private void stopPreview() {
        if (preview != null) {
            try { preview.reset(); preview.release(); }
            catch (Exception ignored) {}
            preview = null;
        }
    }

    private void export(Originals.Track t) {
        exporting = t;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String name = Originals.exportFilename(t);
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        String mime = lower.endsWith(".wav") ? "audio/wav" :
                lower.endsWith(".flac") ? "audio/flac" :
                lower.endsWith(".m4a") ? "audio/mp4" :
                lower.endsWith(".ogg") ? "audio/ogg" :
                "audio/mpeg";
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, EXPORT_AUDIO);
    }

    private void confirmDelete(Originals.Track t) {
        new AlertDialog.Builder(this).setTitle("MARU 보관곡 삭제")
            .setMessage(t.title + "\n\nMARU에 보관한 사본만 삭제합니다. Suno 원곡은 삭제되지 않습니다.")
            .setPositiveButton("삭제", (d, w) -> {
                stopPreview();
                Originals.remove(this, t);
                refreshSongs();
                say("MARU 보관 목록에서 삭제했어요.");
            }).setNegativeButton("취소", null).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_AUDIO) {
            List<Uri> chosen = new ArrayList<>();
            if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                for (int n = 0; n < clip.getItemCount(); n++)
                    if (clip.getItemAt(n).getUri() != null)
                        chosen.add(clip.getItemAt(n).getUri());
            } else if (data.getData() != null) chosen.add(data.getData());
            importFiles(chosen);
        } else if (requestCode == EXPORT_AUDIO && exporting != null && data.getData() != null) {
            Originals.Track track = exporting;
            exporting = null;
            Uri destination = data.getData();
            say("선택한 폴더에 원곡 저장 중…");
            files.execute(() -> {
                String message = "";
                try (InputStream in = new FileInputStream(track.file());
                     OutputStream out = getContentResolver().openOutputStream(destination)) {
                    if (out == null) throw new Exception("저장할 수 없는 위치예요.");
                    byte[] buf = new byte[32 * 1024];
                    int count;
                    while ((count = in.read(buf)) != -1) out.write(buf, 0, count);
                    out.flush();
                } catch (Exception e) {
                    message = "파일을 저장하지 못했어요: " + e.getMessage();
                }
                final String error = message;
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (error.isEmpty()) {
                        new AlertDialog.Builder(this).setTitle("원곡 저장 완료")
                            .setMessage("원본 파일을 그대로 저장했습니다.\nBIGO에 로컬 음악 불러오기가 보이면 이 곡을 선택하세요.")
                            .setPositiveButton("BIGO 열기", (d, w) -> openBigo())
                            .setNegativeButton("닫기", null).show();
                    } else say(error);
                });
            });
        }
    }

    private void acceptShare(Intent intent) {
        if (intent == null) return;
        List<Uri> shared = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri single = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (single != null) shared.add(single);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (many != null) shared.addAll(many);
        }
        if (!shared.isEmpty()) importFiles(shared);
    }

    private void importFiles(List<Uri> items) {
        if (items.isEmpty()) {
            say("오디오 파일을 선택해 주세요. Suno 공유 링크만으로는 가져올 수 없어요.");
            return;
        }
        say("원곡 " + items.size() + "개 확인 중 · 원본 파일은 변경하지 않아요.");
        files.execute(() -> {
            int added = 0, duplicate = 0;
            String failure = "";
            for (Uri uri : items) {
                try {
                    if (Originals.importUri(getApplicationContext(), uri)) added++;
                    else duplicate++;
                } catch (Exception ex) {
                    failure = ex.getMessage();
                }
            }
            final int addedCount = added, duplicates = duplicate;
            final String error = failure;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                refreshSongs();
                say("원곡 " + addedCount + "곡 추가 · 중복 " + duplicates
                        + "곡 제외" + (error.isEmpty() ? "" : " · " + error));
            });
        });
    }

    private void say(String message) {
        if (status != null) status.setText(message);
    }

    @Override protected void onStop() {
        super.onStop();
        // Preview audio is not a broadcast source. Never mix it into BIGO's microphone.
        stopPreview();
    }

    @Override protected void onDestroy() {
        stopPreview();
        files.shutdown();
        super.onDestroy();
    }
}
