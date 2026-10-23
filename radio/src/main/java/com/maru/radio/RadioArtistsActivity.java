package com.maru.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
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
    private TextToSpeech announcer;
    private boolean announcerReady;
    private Button announceButton;
    private static final String AHWAN = "안녕하세요. 마루 라디오의 AI 아나운서, 아환입니다. 찾아오신 여러분, 반갑습니다. 오늘도 편안한 음악으로 함께할게요. 지금 들으실 곡은 AI 가수 서하린의 중국어 노래, 바람이 그리움을 너에게 전해입니다. 멀리 있어도 변하지 않는 마음을 담았어요. 잠시 쉬어가며 함께 들어주세요.";
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
            announcer = new TextToSpeech(this, status -> {
                announcerReady = status == TextToSpeech.SUCCESS
                        && announcer != null
                        && announcer.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE;
                if (announceButton != null) {
                    announceButton.setEnabled(announcerReady);
                    announceButton.setText(announcerReady ? "▶ 아환 안내 멘트 · 기기 음성" : "한국어 기기 음성을 사용할 수 없습니다");
                }
            });
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
        text("Suno Voice 저장: 강태준·서하린. 아환 안내 음성 생성. 음색 일관성·가창력 검증은 아직 완료되지 않았습니다. 아래 30명은 제작 후보이며, 전원 제작 완료가 아닙니다.", 14);
        text("아환 · 라디오 안내", 22);
        text("기기 음성은 휴대폰의 한국어 TTS를 사용하며 Suno 아환 음성과 다릅니다. 아래 Suno 버튼은 외부 앱/브라우저를 엽니다. 음원은 APK에 포함되지 않으며 BIGO 자동 송출 기능이 아닙니다.", 14);
        announceButton = new Button(this);
        announceButton.setText(announcerReady ? "▶ 아환 안내 멘트 · 기기 음성" : "한국어 기기 음성 준비 중");
        announceButton.setEnabled(announcerReady);
        announceButton.setOnClickListener(v -> {
            if (announcerReady) {
                announcer.setSpeechRate(0.92f);
                announcer.speak(AHWAN, TextToSpeech.QUEUE_FLUSH, null, "ahwan-welcome");
            }
        });
        content.addView(announceButton);
        button("■ 안내 멘트 중지", () -> { if (announcer != null) announcer.stop(); });
        button("아환 생성 음성 · Suno에서 열기 (34초)", () -> openSuno("https://suno.com/song/d3810f43-58b2-43e9-b698-0f5eada1ef17"));
        button("서하린 중국어 곡 · Suno에서 열기 (약 4분 30초)", () -> openSuno("https://suno.com/song/e49a8797-0383-49a7-8349-33d7ad989e8a"));
        button("강태준 · 너의 불빛 아래 · Suno에서 열기", () -> openSuno("https://suno.com/song/0147f033-eb2a-42c1-9cfe-5b29e58f4580"));
        button("강태준 · 천천히 함께 가자 · Suno에서 열기", () -> openSuno("https://suno.com/song/9197eb9f-d45e-4f0a-bbc5-a423bdf09b6a"));
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
    private void openSuno(String url) {
        if (announcer != null) announcer.stop();
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException ex) {
            new AlertDialog.Builder(this).setMessage("링크를 열 브라우저가 없습니다. 브라우저를 설치한 뒤 다시 시도해 주세요.").setPositiveButton("확인", null).show();
        }
    }
    @Override protected void onStop() {
        if (announcer != null) announcer.stop();
        super.onStop();
    }
    @Override protected void onDestroy() {
        if (announcer != null) { announcer.stop(); announcer.shutdown(); }
        super.onDestroy();
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
