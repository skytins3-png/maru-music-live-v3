package com.maru.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/** Local original-song links, not a voice generator or a certification of vocal identity. */
public final class RadioArtistsActivity extends Activity {
    private JSONArray artists;
    private SharedPreferences links;
    private LinearLayout content;
    private List<Originals.Track> tracks;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        links = getSharedPreferences("maru_studio_song_links", MODE_PRIVATE);
        try (InputStream in = getAssets().open("studio-artists.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
            artists = new JSONObject(bytes.toString("UTF-8")).getJSONArray("artists");
            render();
        } catch (Exception ex) {
            TextView error = new TextView(this);
            error.setText("가수 명단을 읽지 못했습니다. 앱을 다시 열어 주세요.");
            setContentView(error);
        }
    }
    private void text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(0xFFF7F1FF);
        view.setPadding(0, 16, 0, 12); content.addView(view);
    }
    private void button(String value, Runnable action) {
        Button view = new Button(this); view.setText(value);
        view.setAllCaps(false); view.setOnClickListener(v -> action.run());
        content.addView(view);
    }
    private Originals.Track track(String key) {
        String hash = links.getString(key, "");
        for (Originals.Track t : tracks) if (t.hash.equals(hash)) return t;
        return null;
    }
    private void render() {
        tracks = Originals.list(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF110E21);
        scroll.setFitsSystemWindows(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad); scroll.addView(content);
        text("MARU STUDIO · 가수 음원 연결", 24);
        int linked = 0;
        for (int i = 0; i < artists.length(); i++) {
            String id = artists.optJSONObject(i).optString("id");
            if (track(id + ":1") != null && track(id + ":2") != null) linked++;
        }
        text("후보 " + artists.length() + "명 · 두 곡 연결 " + linked + "명", 18);
        text("실제 가수 제작 완료: 0명. 아래는 기존 제작 후보입니다. 음원을 연결해도 목소리 일관성·가창력 검증이 완료되는 것은 아닙니다. 진행자 TTS와 구분됩니다.", 14);
        button("원곡 추가 · 라디오로 돌아가기", this::finish);
        for (int i = 0; i < artists.length(); i++) {
            JSONObject artist = artists.optJSONObject(i);
            String id = artist.optString("id");
            text(artist.optString("name") + " · " + artist.optString("groupLabel"), 20);
            text(artist.optString("genre") + " / " + artist.optString("range") + "\n음원 제작·검증 대기", 14);
            for (int slot = 1; slot <= 2; slot++) {
                final String key = id + ":" + slot;
                final String other = id + ":" + (slot == 1 ? 2 : 1);
                Originals.Track song = track(key);
                button("곡 " + slot + " 연결: " + (song == null ? "미등록" : song.title),
                        () -> choose(key, other));
                if (song != null) {
                    button("▶ " + song.title + " 원곡 재생", () -> play(song));
                    button("연결 해제 (원곡 유지)", () -> { links.edit().remove(key).apply(); render(); });
                }
            }
        }
        setContentView(scroll);
    }
    private void choose(String key, String other) {
        tracks = Originals.list(this);
        if (tracks.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("라디오 첫 화면에서 Suno 원곡 파일을 먼저 추가해 주세요.")
                    .setPositiveButton("확인", null).show(); return;
        }
        String[] names = new String[tracks.size()];
        for (int i = 0; i < names.length; i++) names[i] = tracks.get(i).title;
        new AlertDialog.Builder(this).setTitle("등록한 원곡 선택")
            .setItems(names, (dialog, which) -> {
                Originals.Track chosen = tracks.get(which);
                if (chosen.hash.equals(links.getString(other, ""))) {
                    new AlertDialog.Builder(this).setMessage("같은 파일을 두 곡으로 등록할 수 없습니다.")
                            .setPositiveButton("확인", null).show(); return;
                }
                links.edit().putString(key, chosen.hash).apply(); render();
            }).setNegativeButton("취소", null).show();
    }
    private void play(Originals.Track song) {
        // Explicit preview in the ordinary radio queue, using the untouched original.
        RadioProgram.select(this, 0);
        Intent play = new Intent(this, RadioPlaybackService.class)
                .setAction(RadioPlaybackService.ACTION_PLAY)
                .putExtra(RadioPlaybackService.EXTRA_HASH, song.hash);
        try {
            startForegroundService(play);
            finish();
        } catch (RuntimeException ex) {
            new AlertDialog.Builder(this).setMessage("재생을 시작하지 못했습니다. 라디오 화면에서 다시 시도해 주세요.")
                    .setPositiveButton("확인", null).show();
        }
    }
}
