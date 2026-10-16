package com.maru.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.AdapterView;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in offline radio host console: TTS, prerecorded host greeting, song
 * requests, typed replies and owner-taught phrases. No BIGO chat scraping.
 */
public final class RadioDjActivity extends Activity {
    private static final int REQUEST_GREETING_AUDIO = 801;
    private static final int REQUEST_SPOKEN_QUERY = 802;
    private static final int WHITE = 0xFFF6F1FF, PURPLE = 0xFFBAA0FF;
    private TextView status;
    private EditText comment;
    private EditText teachTrigger;
    private EditText teachAnswer;
    private Spinner languageSpinner;
    private String lastReply = "";

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + .5f);
    }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setPadding(0, dp(7), 0, dp(7));
        return t;
    }
    private Button button(String s, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private EditText input(String hint) {
        EditText v = new EditText(this);
        v.setSingleLine(true);
        v.setTextColor(WHITE);
        v.setHintTextColor(0xFFADA2C0);
        v.setTextSize(15);
        v.setHint(hint);
        return v;
    }
    private void gap(LinearLayout content) {
        View v = new View(this);
        content.addView(v, new LinearLayout.LayoutParams(-1, dp(12)));
    }
    private void say(String words) { say(words, RadioDjLanguagePolicy.ENGLISH); }
    private void say(String words, int language) {
        if (words == null || words.trim().isEmpty()) return;
        lastReply = words;
        status.setText("🎙 안내 멘트: " + words
                + "\n시스템 TTS 음성 재생 요청 · BIGO 송출 여부는 별도 확인 필요");
        Intent service = new Intent(this, RadioPlaybackService.class)
                .setAction(RadioPlaybackService.ACTION_DJ_SAY)
                .putExtra(RadioPlaybackService.EXTRA_DJ_TEXT, words)
                .putExtra(RadioPlaybackService.EXTRA_DJ_LANGUAGE, language);
        try { startService(service); }
        catch (RuntimeException ex) { oneLine("아나운서 음성 재생 실패"); }
    }
    private void send(String action, String words, String hash) {
        Intent service = new Intent(this, RadioPlaybackService.class).setAction(action);
        if (words != null) service.putExtra(RadioPlaybackService.EXTRA_DJ_TEXT, words);
        if (hash != null) service.putExtra(RadioPlaybackService.EXTRA_HASH, hash);
        try { startService(service); }
        catch (RuntimeException ex) { status.setText("음성/원곡 서비스를 시작할 수 없어요."); }
    }
    private void oneLine(String text) { if (status != null) status.setText(text); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF100E20);
        if (Build.VERSION.SDK_INT >= 35) scroll.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets i = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(0,i.top,0,i.bottom);
            return insets;
        });
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(16),dp(18),dp(40));
        scroll.addView(body);
        body.addView(text("🎙 아환 AI 라디오 아나운서", 25, PURPLE));
        body.addView(text("인사 · 신청곡 소개/재생 · 댓글 답변 · 선물 감사 · 진행 멘트", 14, WHITE));
        body.addView(text("현재는 휴대폰 내부 규칙 대화 + Android TTS입니다. "
                + "형의 실제 목소리로 자유롭게 합성하는 음성 복제 모델은 아직 없습니다. "
                + "직접 녹음한 형의 인사말은 재생할 수 있어요. "
                + "다른 앱 BIGO로 음성을 직접 송출하는 기능도 아직 검증되지 않았습니다.",
                13, 0xFFE4D4A9));

        CheckBox spoken = new CheckBox(this);
        spoken.setText("아나운서 음성 사용 (시스템 TTS)");
        spoken.setTextColor(WHITE);
        spoken.setChecked(RadioDjSettings.isVoiceEnabled(this));
        spoken.setOnCheckedChangeListener((b,checked)->
                RadioDjSettings.setVoiceEnabled(this,checked));
        body.addView(spoken);
        CheckBox open = new CheckBox(this);
        open.setText("곡 재생 시작 시 오프닝 멘트 (선택)");
        open.setTextColor(WHITE);
        open.setChecked(RadioDjSettings.opening(this));
        open.setOnCheckedChangeListener((b,v)->RadioDjSettings.setOpening(this,v));
        body.addView(open);
        CheckBox between = new CheckBox(this);
        between.setText("곡마다 다음 곡 소개 (원곡 시작 전 TTS)");
        between.setTextColor(WHITE);
        between.setChecked(RadioDjSettings.interSong(this));
        between.setOnCheckedChangeListener((b,v)->RadioDjSettings.setInterSong(this,v));
        body.addView(between);
        CheckBox memory = new CheckBox(this);
        memory.setText("방송 취향 학습 (곡 요청 횟수만 로컬 저장)");
        memory.setTextColor(WHITE);
        memory.setChecked(RadioDjSettings.isMemoryEnabled(this));
        memory.setOnCheckedChangeListener((b,v)->RadioDjSettings.setMemoryEnabled(this,v));
        body.addView(memory);
        body.addView(text("선물 보낸 사람 언어: 자동 판별 / 모르면 영어", 15, PURPLE));
        body.addView(text("한국곡: 한국어→영어 · 중국곡: 중국어→영어 · "
                + "서양곡과 연주곡: 영어만 · 시작과 종료: 영어만. "
                + "선물은 입력한 메시지의 언어로 답하고 알 수 없으면 영어로 말합니다. "
                + "실제 BIGO 댓글 자동 수신은 아직 연결되지 않았습니다.", 13, WHITE));
        languageSpinner = new Spinner(this);
        String[] giftChoices = {"자동 (모르면 English)", "한국어", "中文",
                "English", "日本語", "Русский"};
        ArrayAdapter<String> langs = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, giftChoices);
        languageSpinner.setAdapter(langs);
        languageSpinner.setSelection(RadioDjSettings.giftLanguageHint(this) + 1);
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                RadioDjSettings.setGiftLanguageHint(RadioDjActivity.this,pos - 1);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        body.addView(languageSpinner);
        gap(body);

        body.addView(text("📻 진행자 인사와 멘트", 19, PURPLE));
        body.addView(button("🎙 방송 시작 인사 (영어만)", () -> {
            String speech = RadioDjLanguagePolicy.opening().get(0).text;
            lastReply = speech;
            oneLine("아환 방송 시작 인사: " + speech);
            send(RadioPlaybackService.ACTION_DJ_GREETING, null, null);
        }));
        body.addView(button("🌹 선물 감사 인사 (보낸 사람 언어)", this::thankGift));
        body.addView(button("💗 팔로우 감사 인사 (기본 영어)", () ->
                say(RadioReplies.message(RadioDjLanguagePolicy.ENGLISH, 3))));
        body.addView(button("🎵 현재 방송목록의 다음 곡 소개", () -> {
            oneLine("현재 MARU 재생 대기열의 다음 곡을 소개해요.");
            send(RadioPlaybackService.ACTION_DJ_ANNOUNCE_NEXT, null, null);
        }));
        body.addView(button("■ 방송 종료 인사 (영어만)", () ->
                send(RadioPlaybackService.ACTION_DJ_CLOSING, null, null)));
        body.addView(button("🎤 형 목소리로 녹음한 인사 파일 선택", this::pickRecordedGreeting));
        body.addView(button("▶ 녹음된 인사 개인 미리듣기 (자동 방송과 별개)", () ->
                send(RadioPlaybackService.ACTION_DJ_TEST_RECORDED, null, null)));
        body.addView(button("🗑 등록한 인사 음성 해제", () -> {
            RadioDjSettings.setVoiceClip(this,null);
            oneLine("녹음 인사 클립 연결을 해제했어. 원본 파일은 삭제하지 않았어.");
        }));
        body.addView(text("녹음한 인사 파일은 별도 보관합니다. "
                + "자동 방송 시작은 반드시 영어 TTS를 사용해 "
                + "형이 선택한 영어-only 시작 규칙을 지킵니다. "
                + "목소리 복제/노래 가창은 지원하지 않습니다.", 12,0xFFDDCDDB));

        gap(body);
        body.addView(text("💬 실시간 대화 대본 · 요청곡", 19, PURPLE));
        body.addView(text("댓글 내용은 형이 직접 입력하거나 붙여넣습니다. "
                + "현재 BIGO 댓글을 자동 읽는 공식 연결은 없습니다.", 13, 0xFFDBD0E7));
        comment = input("예: 가을 남자 노래 틀어줘 / 오늘 피곤해");
        body.addView(comment);
        body.addView(button("🎤 말로 댓글/신청곡 입력 (외부 음성 인식)", this::recognizeSpokenQuery));
        body.addView(button("✨ 대화 답변하고 읽어주기", this::replyToComment));
        body.addView(button("🎼 입력한 제목으로 보관 원곡 찾아 재생", this::requestSong));
        body.addView(button("⭐ 많이 신청된 노래 확인", () ->
                oneLine("로컬 요청 선호곡: " + RadioDjSettings.favoriteTitle(this,
                        Originals.list(this)) + " / 학습 대화: "
                        + RadioDjSettings.replyCount(this) + "회")));
        body.addView(button("📋 방금 멘트 복사", () -> {
            if (lastReply.isEmpty()) return;
            android.content.ClipboardManager cb = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            if (cb != null) cb.setPrimaryClip(
                    android.content.ClipData.newPlainText("MARU DJ", lastReply));
            oneLine("멘트를 복사했어. BIGO에 직접 붙여넣을 수 있어.");
        }));
        gap(body);

        body.addView(text("🧠 형이 직접 가르치는 답변 (최대 20개)", 19, PURPLE));
        body.addView(text("반복되는 표현과 답변을 직접 지정하면 이후 입력에 적용됩니다. "
                + "시청자 댓글은 저장되지 않으며, 이 규칙만 휴대폰에 저장됩니다.", 13, WHITE));
        teachTrigger = input("예: 오늘 날씨");
        teachAnswer = input("예: 날씨만큼 즐거운 음악 들려드릴게요!");
        body.addView(teachTrigger);
        body.addView(teachAnswer);
        body.addView(button("📚 이 표현을 가르치기", () -> {
            String trigger = teachTrigger.getText().toString().trim();
            String answer = teachAnswer.getText().toString().trim();
            if (trigger.length()<2 || trigger.length()>40 || answer.length()<2
                    || answer.length()>160) {
                oneLine("학습 문구는 2~40자, 답변은 2~160자로 입력해 줘.");
                return;
            }
            RadioDjSettings.teach(this,trigger,answer);
            oneLine("가르친 대답 저장됨. 입력 문구가 포함되면 우선 답변합니다.");
        }));
        body.addView(button("🗑 학습 기록과 가르친 답변 모두 삭제", () ->
                new AlertDialog.Builder(this).setTitle("AI 진행자 학습 초기화")
                        .setMessage("요청곡 집계와 직접 가르친 문장을 모두 삭제할까요? "
                                + "음악 원곡·방송 예약·녹음 파일은 삭제되지 않습니다.")
                        .setPositiveButton("학습만 지우기",(d,w)->{
                            RadioDjSettings.clearMemory(this);
                            RadioDjSettings.clearHostRules(this);
                            oneLine("AI 진행자 학습 기록 초기화 완료");
                        }).setNegativeButton("취소",null).show()));
        status = text("아환 아나운서 준비 완료 · 저장곡 " + Originals.list(this).size()
                + "곡 · 오디오는 기기에서만 재생됩니다.", 14, 0xFFB0E4C5);
        body.addView(status);
        body.addView(button("← MARU RADIO 돌아가기", this::finish));
        setContentView(scroll);
    }

    private void thankGift() {
        String supplied = comment == null ? "" : comment.getText().toString();
        int hint = RadioDjSettings.giftLanguageHint(this);
        RadioDjLanguagePolicy.Line line = RadioDjLanguagePolicy.gift(supplied, hint).get(0);
        lastReply = line.text;
        oneLine("선물 감사 언어: " + RadioReplies.LANGUAGES[line.language]
                + " · BIGO 선물 이벤트 자동 수신은 아직 미연결");
        Intent action = new Intent(this, RadioPlaybackService.class)
                .setAction(RadioPlaybackService.ACTION_DJ_GIFT)
                .putExtra(RadioPlaybackService.EXTRA_DJ_TEXT, supplied)
                .putExtra(RadioPlaybackService.EXTRA_DJ_HINT, hint);
        try { startService(action); }
        catch (RuntimeException e) { oneLine("선물 감사 멘트 재생 실패"); }
    }

    private List<Originals.Track> songs() { return Originals.list(this); }
    private void replyToComment() {
        String input = comment.getText().toString().trim();
        if (input.isEmpty()) { oneLine("답변할 댓글을 입력해 주세요."); return; }
        String learned = RadioDjSettings.hostReply(this,input);
        List<Originals.Track> tracks = songs();
        List<String> titles = new ArrayList<>();
        for (Originals.Track t: tracks) titles.add(t.title);
        RadioDjBrain.Reply result = RadioDjBrain.respond(input, titles,
                RadioDjSettings.nextPhraseIndex(this));
        if (!learned.isEmpty()) {
            RadioDjSettings.rememberReply(this);
            say(learned, RadioDjLanguagePolicy.giftLanguage(input,
                    RadioDjSettings.giftLanguageHint(this)));
            return;
        }
        if (RadioDjLanguagePolicy.isGiftMessage(input)) {
            RadioDjSettings.rememberReply(this);
            thankGift();
            return;
        }
        RadioDjSettings.rememberReply(this);
        if (result.songIndex >= 0 && result.songIndex < tracks.size()) {
            RadioDjSettings.rememberSong(this, tracks.get(result.songIndex).hash);
            lastReply = result.speech;
            oneLine("🎵 요청곡: " + tracks.get(result.songIndex).title + " / " + result.speech);
            send(RadioPlaybackService.ACTION_DJ_REQUEST,result.speech,
                    tracks.get(result.songIndex).hash);
        } else {
            int detected = RadioDjLanguagePolicy.giftLanguage(input,
                    RadioDjLanguagePolicy.AUTO);
            if (detected == RadioDjLanguagePolicy.KOREAN) say(result.speech, detected);
            else say(RadioReplies.message(detected,1), detected);
        }
    }
    private void requestSong() {
        String query = comment.getText().toString().trim();
        if (query.isEmpty()) { oneLine("노래 제목을 입력해 주세요."); return; }
        List<Originals.Track> tracks = songs();
        List<String> titles = new ArrayList<>();
        for (Originals.Track t : tracks) titles.add(t.title);
        int pos = RadioDjBrain.findSong(query,titles);
        if (pos < 0) {
            oneLine("요청 제목과 정확하게 일치하는 곡이 없거나 여러 곡이 겹쳐요. "
                    + "목록의 제목을 더 구체적으로 입력해 주세요.");
            return;
        }
        Originals.Track song = tracks.get(pos);
        RadioDjSettings.rememberSong(this, song.hash);
        java.util.List<RadioDjLanguagePolicy.Line> lines =
                RadioDjLanguagePolicy.songIntro(song.title, RadioProgram.category(this, song));
        StringBuilder words = new StringBuilder();
        for (RadioDjLanguagePolicy.Line line : lines) {
            if (words.length() > 0) words.append(" / ");
            words.append(line.text);
        }
        String speech = words.toString();
        lastReply = speech;
        oneLine("요청한 목록 원곡 재생: " + song.title
                + " · TTS 소개 뒤 원곡을 그대로 재생해요.");
        send(RadioPlaybackService.ACTION_DJ_REQUEST,speech,song.hash);
    }

    private void recognizeSpokenQuery() {
        // User-invoked system speech recognition dialog: no always-on listening,
        // own app RECORD_AUDIO permission, or BIGO room audio monitoring.
        Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "마루 DJ에게 말해 주세요.");
        String[] langs = {"ko-KR", "zh-CN", "en-US", "ja-JP", "ru-RU"};
        // Spoken requests default to the operator's Korean input language.
        // Gift reply language selection never changes the music introduction policy.
        speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                langs[RadioDjSettings.language(this)]);
        try { startActivityForResult(speech, REQUEST_SPOKEN_QUERY); }
        catch (RuntimeException e) {
            oneLine("사용 가능한 시스템 음성 인식기가 없어요. 글자로 입력해 주세요.");
        }
    }

    private void pickRecordedGreeting() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(picker, REQUEST_GREETING_AUDIO); }
        catch (RuntimeException ex) { oneLine("휴대폰 오디오 파일 선택 창을 열 수 없어요."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request == REQUEST_SPOKEN_QUERY) {
            if (result == RESULT_OK && data != null) {
                ArrayList<String> phrases = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                if (phrases != null && !phrases.isEmpty()) {
                    comment.setText(phrases.get(0));
                    replyToComment(); // explicit user button launched recognition
                }
            }
            return;
        }
        if (request != REQUEST_GREETING_AUDIO || result != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            RadioDjSettings.setVoiceClip(this, uri);
            oneLine("형이 직접 녹음한 인사 음성 연결 완료. 인사 멘트에서 재생됩니다. "
                    + "새 문장을 형 목소리로 합성하는 기능은 아직 없습니다.");
        } catch (SecurityException ex) {
            oneLine("녹음 파일에 대한 장기 읽기 권한을 얻지 못했어요.");
        }
    }
}
