package com.maru.ahwanvoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Standalone, operator-initiated, local-only quick radio talk companion.
 * Device TTS is NOT a model of the owner's recorded voice. Pre-recorded
 * clips play the original voice without resynthesis. No BIGO private API,
 * microphone interception or accessibility permission is used.
 */
public final class VoiceStudioActivity extends Activity {
    private static final int OPEN_RECORDING = 101;
    private static final String MARU_PACKAGE = "com.maru.musiclive.voicebridge.v20261105";
    private static final String MARU_ACTIVITY = "com.maru.musiclive.MainActivity";
    private static final String[] LANGS = {"ko", "en", "zh"};
    private static final String[] LABELS = {"한국어", "English", "中文"};
    private final ArrayDeque<Line> pending = new ArrayDeque<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private MediaPlayer player;
    private Spinner langs;
    private EditText editor;
    private TextView status;
    private SharedPreferences prefs;
    private String currentUtterance = "";
    private int utteranceCount;
    private boolean ready;

    private static final class Line {
        final String code;
        final String text;
        Line(String code, String text) {
            this.code = code;
            this.text = text;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("ahwan_voice_private", MODE_PRIVATE);
        buildScreen();
        tts = new TextToSpeech(this, result -> main.post(() -> {
            if (result != TextToSpeech.SUCCESS || tts == null) {
                status.setText("휴대폰 TTS 초기화 실패 · 음성 엔진을 확인하세요.");
                return;
            }
            ready = true;
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) {
                    main.post(() -> {
                        if (id.equals(currentUtterance)) speakNext();
                    });
                }
                @Override public void onError(String id) {
                    main.post(() -> {
                        if (id.equals(currentUtterance)) {
                            pending.clear();
                            status.setText("음성 재생 오류 · 설치된 언어 데이터를 확인하세요.");
                        }
                    });
                }
            });
            status.setText("준비 완료 · 언어별 음성을 먼저 들어보고 선택하세요.");
        }));
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(22), dp(18), dp(32));
        col.setBackgroundColor(0xFFF5F7FA);
        scroll.addView(col);
        setContentView(scroll);

        title(col, "아환 VOICE STUDIO", 25);
        label(col, "별도 앱 · 즉시 토크 · MARU 연결", 14);
        label(col, "녹음 클립은 형의 실제 목소리로 재생됩니다. "
                + "새 문장을 바로 읽는 기능은 현재 휴대폰 TTS이며, "
                + "형 목소리 AI 복제는 아직 연결되지 않았습니다.", 13);

        langs = new Spinner(this);
        langs.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, LABELS));
        col.addView(langs);
        editor = new EditText(this);
        editor.setMinLines(3);
        editor.setMaxLines(6);
        editor.setText("안녕하세요. 아환 라이브입니다. 찾아주신 여러분, 환영합니다.");
        editor.setHint("실시간 방송 멘트 또는 청취자 댓글 답변 입력");
        editor.setTextSize(17);
        editor.setBackgroundColor(Color.WHITE);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        col.addView(editor);

        addButton(col, "1. 음성 직접 선택 · 미리듣기", this::chooseVoice);
        addButton(col, "2. 지금 바로 말하기", this::speakNow);
        addButton(col, "3. MARU RADIO에 보내 바로 말하기", this::sendToMaru);

        title(col, "원터치 실시간 토크", 19);
        addButton(col, "입장 환영 멘트", () -> setPreset(0));
        addButton(col, "신청곡 소개", () -> setPreset(1));
        addButton(col, "사연 소개", () -> setPreset(2));
        addButton(col, "댓글에 따뜻하게 답하기", () -> setPreset(3));
        addButton(col, "영어 → 한국어 → 중국어 순차 인사", this::speakThreeLanguages);

        title(col, "형의 실제 목소리", 19);
        addButton(col, "녹음 MP3/WAV 선택·교체", this::pickRecording);
        addButton(col, "녹음한 형 목소리 즉시 재생", this::playRecording);
        addButton(col, "음성 재생 멈추기", this::stopAll);
        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(0xFF1F3D63);
        status.setPadding(0, dp(12), 0, dp(12));
        status.setText("음성 엔진 준비 중");
        col.addView(status);
        label(col, "실제 BIGO 청취자에게 전송되는지는 BIGO의 오디오 입력 정책에 "
                + "따라 달라집니다. 자동 댓글 수집·무단 음성 복제는 하지 않습니다.", 12);
    }

    private void title(LinearLayout col, String text, int size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(0xFF123B60);
        v.setTextSize(size);
        v.setPadding(0, dp(12), 0, dp(8));
        col.addView(v);
    }

    private void label(LinearLayout col, String text, int size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(0xFF4F5A69);
        v.setTextSize(size);
        v.setPadding(0, dp(4), 0, dp(12));
        col.addView(v);
    }

    private void addButton(LinearLayout parent, String name, Runnable task) {
        Button button = new Button(this);
        button.setText(name);
        button.setAllCaps(false);
        parent.addView(button);
        button.setOnClickListener(view -> task.run());
    }

    private int dp(int pixels) {
        return Math.round(pixels * getResources().getDisplayMetrics().density);
    }

    private String lang() { return LANGS[langs.getSelectedItemPosition()]; }
    private Locale locale(String code) {
        if ("ko".equals(code)) return Locale.KOREA;
        if ("zh".equals(code)) return Locale.SIMPLIFIED_CHINESE;
        return Locale.US;
    }

    private List<Voice> availableVoices(String code) {
        List<Voice> out = new ArrayList<>();
        if (!ready || tts == null) return out;
        Set<Voice> voices = tts.getVoices();
        if (voices == null) return out;
        for (Voice v : voices) {
            if (v == null || v.getLocale() == null || v.isNetworkConnectionRequired()) continue;
            if (code.equals(v.getLocale().getLanguage())) out.add(v);
        }
        Collections.sort(out, (a,b) -> a.getName().compareTo(b.getName()));
        return out;
    }

    private boolean setVoice(String code) {
        if (!ready) return false;
        String chosen = prefs.getString("voice_" + code, "");
        if (chosen.isEmpty()) return false;
        int result = tts.setLanguage(locale(code));
        if (result == TextToSpeech.LANG_NOT_SUPPORTED ||
                result == TextToSpeech.LANG_MISSING_DATA) return false;
        for (Voice voice : availableVoices(code)) {
            if (chosen.equals(voice.getName()))
                return tts.setVoice(voice) == TextToSpeech.SUCCESS;
        }
        return false;
    }

    private void chooseVoice() {
        if (!ready) { status.setText("음성 엔진 준비 전입니다."); return; }
        String code = lang();
        List<Voice> choices = availableVoices(code);
        if (choices.isEmpty()) {
            status.setText("이 언어의 오프라인 음성이 없습니다. 휴대폰 TTS 음성 데이터를 설치하세요.");
            return;
        }
        String[] names = new String[choices.size()];
        for (int i=0;i<choices.size();i++) names[i] = choices.get(i).getName();
        new AlertDialog.Builder(this)
                .setTitle(LABELS[langs.getSelectedItemPosition()] + " 음성 미리듣기")
                .setMessage("목소리를 눌러 들은 뒤 저장하세요. 안드로이드는 성별 정보를 보장하지 않습니다.")
                .setItems(names, (dialog,index) -> {
                    Voice selected = choices.get(index);
                    tts.stop();
                    pending.clear();
                    tts.setLanguage(locale(code));
                    tts.setVoice(selected);
                    tts.setSpeechRate(0.94f);
                    tts.setPitch(1.0f);
                    String sample = "ko".equals(code)
                            ? "반갑습니다. 아환 음악 방송에 오신 여러분 환영합니다."
                            : "zh".equals(code)
                            ? "大家好，欢迎收听阿焕音乐电台。"
                            : "Hello everyone. Welcome to Ahwan Radio.";
                    tts.speak(sample, TextToSpeech.QUEUE_FLUSH, null,
                            "voice-audition");
                    new AlertDialog.Builder(this)
                            .setTitle("이 음성을 사용할까요?")
                            .setMessage(selected.getName())
                            .setPositiveButton("저장", (d,w) -> {
                                prefs.edit().putString("voice_" + code, selected.getName()).apply();
                                status.setText("선택한 음성 저장: " + selected.getName());
                            }).setNegativeButton("다시 선택", (d,w) -> chooseVoice()).show();
                }).setNegativeButton("닫기", null).show();
    }

    private void speakNow() {
        String text = editor.getText().toString().trim();
        if (text.isEmpty()) { status.setText("먼저 방송 멘트를 입력해 주세요."); return; }
        if (text.length() > 300) { status.setText("실시간 멘트는 300자 이하로 입력하세요."); return; }
        stopAll();
        pending.add(new Line(lang(), text));
        speakNext();
    }

    private void speakThreeLanguages() {
        stopAll();
        pending.add(new Line("en", "Hello, and welcome to Ahwan Radio. It's good to have you here."));
        pending.add(new Line("ko", "안녕하세요. 아환 라이브입니다. 오늘도 함께해 주셔서 감사합니다."));
        pending.add(new Line("zh", "大家好，欢迎来到阿焕音乐电台。感谢大家的陪伴。"));
        speakNext();
    }

    private void speakNext() {
        Line line = pending.pollFirst();
        if (line == null) {
            currentUtterance = "";
            status.setText("멘트 재생 완료");
            return;
        }
        if (!setVoice(line.code)) {
            pending.clear();
            currentUtterance = "";
            status.setText(line.code + " 음성 미선택 · '음성 직접 선택'에서 저장해 주세요.");
            return;
        }
        tts.setSpeechRate(0.94f);
        tts.setPitch(1.0f);
        currentUtterance = "ahwan-" + (++utteranceCount);
        status.setText("실시간 음성 재생 · " + line.code);
        if (tts.speak(line.text, TextToSpeech.QUEUE_FLUSH, null,
                currentUtterance) == TextToSpeech.ERROR) {
            pending.clear();
            status.setText("음성 읽기 실패 · 음성 데이터 확인 필요");
        }
    }

    private void setPreset(int kind) {
        String code = lang();
        String[][] templates = {
            {"어서 오세요. 아환 라디오입니다. 함께 음악을 즐겨요.",
             "Welcome to Ahwan Radio. We're glad you're here.",
             "欢迎来到阿焕电台，祝大家听歌愉快。"},
            {"신청해 주신 노래, 잠시 후 함께 들어볼게요.",
             "Thank you for your request. Let's hear your song.",
             "谢谢你的点歌，我们一起听这首歌。"},
            {"청취자 한 분이 보내주신 소중한 사연을 소개합니다.",
             "We have a listener story to share with you.",
             "接下来跟大家分享一位听众的故事。"},
            {"따뜻한 말씀 고맙습니다. 오늘도 좋은 하루 보내세요.",
             "Thank you for your kind message. Enjoy your day.",
             "感谢你的留言，祝你今天开心。"}
        };
        int which = "ko".equals(code) ? 0 : "en".equals(code) ? 1 : 2;
        editor.setText(templates[kind][which]);
        status.setText("멘트 준비 완료 · '지금 바로 말하기' 또는 'MARU에 보내기' 누르기");
    }

    private void sendToMaru() {
        String text = editor.getText().toString().trim();
        if (text.isEmpty() || text.length() > 300) {
            status.setText("먼저 1~300자의 토크 문장을 입력하세요.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.setClassName(MARU_PACKAGE, MARU_ACTIVITY);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.putExtra("com.maru.ahwanvoice.LANGUAGE", lang());
        intent.putExtra("com.maru.ahwanvoice.VOICE_NAME",
                prefs.getString("voice_" + lang(), ""));
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
            status.setText("MARU에서 확인 후 즉시 말하기를 선택하세요.");
        } catch (RuntimeException error) {
            status.setText("MARU 토크 연결판 APK를 먼저 설치해 주세요. "
                    + "이전 MARU 앱과는 별도 설치됩니다.");
        }
    }

    private void pickRecording() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, OPEN_RECORDING);
    }

    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (request != OPEN_RECORDING || result != RESULT_OK ||
                intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}
        prefs.edit().putString("original_clip", uri.toString()).apply();
        status.setText("형의 녹음 음성 등록 완료 · 버튼으로 바로 재생할 수 있습니다.");
    }

    private void playRecording() {
        String clip = prefs.getString("original_clip", "");
        if (clip.isEmpty()) { status.setText("먼저 형의 녹음 MP3/WAV를 선택해 주세요."); return; }
        stopAll();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(this, Uri.parse(clip));
            player.setOnPreparedListener(mp -> {
                status.setText("형의 실제 녹음 목소리 재생 중 · 합성 아님");
                mp.start();
            });
            player.setOnCompletionListener(mp -> {
                if (player == mp) {
                    mp.release();
                    player = null;
                    status.setText("형의 녹음 클립 재생 완료");
                }
            });
            player.prepareAsync();
        } catch (Exception error) {
            if (player != null) { player.release(); player = null; }
            status.setText("원본 녹음 파일을 열 수 없습니다. 다시 선택해 주세요.");
        }
    }

    private void stopAll() {
        pending.clear();
        currentUtterance = "";
        if (tts != null) tts.stop();
        if (player != null) {
            try { player.stop(); } catch (RuntimeException ignored) {}
            player.release();
            player = null;
        }
        if (status != null) status.setText("음성 재생 중지");
    }

    @Override protected void onDestroy() {
        stopAll();
        if (tts != null) { tts.shutdown(); tts = null; }
        super.onDestroy();
    }
}
