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
    private TextView greetingStatus;
    private TextView bigoChatStatus;
    private CheckBox bigoChatEnabled;
    private boolean syncingChatToggle;
    private CheckBox useRecordedGreeting;
    private CheckBox useOpening;
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
        boolean direct = RadioDirectRtmpService.isActive();
        Intent service = direct
                ? new Intent(this, RadioDirectRtmpService.class)
                    .setAction(RadioDirectRtmpService.ACTION_SAY)
                    .putExtra(RadioDirectRtmpService.EXTRA_SAY, words)
                    .putExtra(RadioDirectRtmpService.EXTRA_LANGUAGE, language)
                : new Intent(this, RadioPlaybackService.class)
                    .setAction(RadioPlaybackService.ACTION_DJ_SAY)
                    .putExtra(RadioPlaybackService.EXTRA_DJ_TEXT, words)
                    .putExtra(RadioPlaybackService.EXTRA_DJ_LANGUAGE, language);
        try { startService(service); }
        catch (RuntimeException ex) { oneLine("아나운서 음성 재생 실패"); }
    }
    private void send(String action, String words, String hash) {
        // Spoken DJ announcements go into direct network PCM when that owner
        // initiated RTMP route is active; otherwise keep original local flow.
        if (RadioDirectRtmpService.isActive() && !RadioPlaybackService.ACTION_DJ_REQUEST.equals(action)
                && !RadioPlaybackService.ACTION_DJ_ANNOUNCE_NEXT.equals(action)) {
            java.util.List<RadioDjLanguagePolicy.Line> lines = null;
            if (RadioPlaybackService.ACTION_DJ_GREETING.equals(action))
                lines = RadioDjLanguagePolicy.opening();
            else if (RadioPlaybackService.ACTION_DJ_CLOSING.equals(action))
                lines = RadioDjLanguagePolicy.closing();
            else if (RadioPlaybackService.ACTION_DJ_GIFT.equals(action))
                lines = RadioDjLanguagePolicy.gift(words,
                        RadioDjSettings.giftLanguageHint(this));
            if (lines != null) {
                for (RadioDjLanguagePolicy.Line line : lines)
                    startService(new Intent(this, RadioDirectRtmpService.class)
                            .setAction(RadioDirectRtmpService.ACTION_SAY)
                            .putExtra(RadioDirectRtmpService.EXTRA_SAY, line.text)
                            .putExtra(RadioDirectRtmpService.EXTRA_LANGUAGE, line.language));
                return;
            }
        }
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
        useOpening = new CheckBox(this);
        useOpening.setText("곡 재생 시작 시 오프닝 멘트 (선택)");
        useOpening.setTextColor(WHITE);
        useOpening.setChecked(RadioDjSettings.opening(this));
        useOpening.setOnCheckedChangeListener((b,v)->{
            RadioDjSettings.setOpening(this,v);
            updateGreetingStatus();
        });
        body.addView(useOpening);
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
        greetingStatus = text("", 14, 0xFFB0E4C5);
        body.addView(greetingStatus);
        useRecordedGreeting = new CheckBox(this);
        useRecordedGreeting.setText("✅ 방송 시작 때 형의 녹음 인사를 재생");
        useRecordedGreeting.setTextColor(WHITE);
        useRecordedGreeting.setChecked(RadioDjSettings.recordedOpening(this));
        useRecordedGreeting.setOnCheckedChangeListener((button, checked) -> {
            if (checked && RadioDjSettings.voiceClip(this).isEmpty()) {
                useRecordedGreeting.setChecked(false);
                oneLine("먼저 형의 영어 인사 녹음 파일을 선택해 줘.");
                return;
            }
            RadioDjSettings.setRecordedOpening(this, checked);
            if (checked && useOpening != null) useOpening.setChecked(true);
            updateGreetingStatus();
        });
        body.addView(useRecordedGreeting);
        body.addView(button("▶ 형의 녹음 인사 실제 소리 미리듣기", () -> {
            if (RadioDjSettings.voiceClip(this).isEmpty()) {
                oneLine("형의 녹음 인사가 아직 등록되지 않았어. 먼저 음성 파일을 골라 줘.");
                return;
            }
            send(RadioPlaybackService.ACTION_DJ_TEST_RECORDED, null, null);
        }));
        body.addView(button("🗑 등록한 인사 음성 해제", () -> {
            RadioDjSettings.setRecordedOpening(this, false);
            RadioDjSettings.setVoiceClip(this, null);
            useRecordedGreeting.setChecked(false);
            updateGreetingStatus();
            oneLine("녹음 인사 연결만 해제했어. 원본 음성은 삭제하지 않았어.");
        }));
        body.addView(text("자동 방송 시작은 영어 인사 규칙이에요. "
                + "형이 영어로 직접 녹음한 파일을 선택하고 위 항목을 켜면 "
                + "실제 방송 시작 시 녹음된 형 목소리가 먼저 나옵니다. "
                + "파일이 없거나 재생 오류면 영어 TTS로 이어집니다. "
                + "단, 녹음만으로 새 문장까지 형 목소리로 합성할 수는 없어요.",
                12, 0xFFDDCDDB));
        updateGreetingStatus();

        gap(body);
        body.addView(text("💬 BIGO 댓글 자동 감지 · 실험", 19, PURPLE));
        body.addView(text("BIGO 공식 댓글 API 연결이 아닌, 별도 동의 후 BIGO "
                + "호스트 LIVE 화면에서 인식되는 채팅 표시만 읽습니다. "
                + "안녕/감사/선물 같은 정해진 댓글에 한해 15초 이상 간격으로 "
                + "휴대폰 TTS로 응답을 시도합니다. "
                + "댓글·이름은 저장하지 않으며 BIGO 채팅창에 글을 쓰지 않습니다. "
                + "실제 댓글 인식과 청취자에게 음성 전달은 아직 검증이 필요해요.",
                13, 0xFFDBD0E7));
        bigoChatEnabled = new CheckBox(this);
        bigoChatEnabled.setText("🔍 BIGO 방송 댓글 자동 읽기·짧은 음성 답변 허용");
        bigoChatEnabled.setTextColor(WHITE);
        bigoChatEnabled.setChecked(BigoControlAssist.commentReplies(this));
        bigoChatEnabled.setOnCheckedChangeListener((b, checked) -> {
            if (syncingChatToggle) return;
            if (!checked) {
                BigoControlAssist.setCommentReplies(this, false);
                updateBigoChatStatus();
                return;
            }
            bigoChatEnabled.setChecked(false);
            if (!BigoControlAssist.approved(this)) {
                oneLine("먼저 방송 시간 설정에서 BIGO 접근성 동의를 켜 주세요.");
                updateBigoChatStatus();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("BIGO 댓글 화면 읽기 별도 동의")
                    .setMessage("BIGO 호스트 LIVE 화면이 열려 있는 동안에만 "
                            + "이름: 댓글 형태의 채팅 영역을 읽고, "
                            + "안녕·감사·선물처럼 정해진 문구에 한정해 "
                            + "아환 TTS를 휴대폰에서 재생합니다. "
                            + "일반 댓글·닉네임·비밀번호는 저장하거나 전송하지 않습니다. "
                            + "자동 답변은 BIGO 채팅 게시가 아닙니다. "
                            + "청취자에게 실제 소리가 전달되는지는 별도로 시험해야 합니다. "
                            + "Android 접근성 기능이 추가로 켜져 있어야 합니다.")
                    .setPositiveButton("위 범위에 동의", (dialog, which) -> {
                        if (BigoControlAssist.setCommentReplies(this, true)) {
                            syncingChatToggle = true;
                            bigoChatEnabled.setChecked(true);
                            syncingChatToggle = false;
                        }
                        updateBigoChatStatus();
                    })
                    .setNegativeButton("동의하지 않음", null)
                    .show();
        });
        body.addView(bigoChatEnabled);
        bigoChatStatus = text("", 13, 0xFFB0E4C5);
        body.addView(bigoChatStatus);
        body.addView(button("BIGO 댓글 연결 상태 확인", this::updateBigoChatStatus));
        body.addView(button("🔊 BIGO 청취자 실제 소리 경로 점검", () ->
                new AlertDialog.Builder(this)
                    .setTitle("음악·아환 멘트 → BIGO 청취자")
                    .setMessage("MARU의 음악·녹음 인사·TTS 음성을 Android "
                            + "미디어 캡처 허용 형식으로 출력하도록 준비했어요. "
                            + "하지만 이것만으로 BIGO 마이크에 자동 입력되지 않습니다. "
                            + "BIGO가 내부 BGM 또는 권한 있는 오디오 캡처 경로를 "
                            + "제공하는지 실제 기기에서 확인해야 해요.\\n\\n"
                            + "① BIGO 오디오 LIVE 실행\\n"
                            + "② 도구/믹서/BGM에서 로컬 원곡을 불러오는지 확인\\n"
                            + "③ 다른 휴대폰으로 음악과 아환 TTS가 들리는지 확인\\n"
                            + "④ 방송용 휴대폰 미디어 음량을 0으로 내려도 "
                            + "두 소리가 계속 들리는지 확인\\n\\n"
                            + "위 검증 전에는 '시청자 무음 독립 송출 완성'이라 "
                            + "말할 수 없어요.")
                    .setPositiveButton("BIGO 열어 실제 확인",
                            (dialog, which) -> BigoBroadcastGuide.openBigo(this, false))
                    .setNegativeButton("취소", null).show()));
        updateBigoChatStatus();
        gap(body);
        body.addView(text("💬 직접 대화 대본 · 요청곡", 19, PURPLE));
        body.addView(text("형이 직접 입력하거나 붙여넣은 댓글은 기존대로 처리할 수 있어요. "
                + "실험 자동 댓글 감지는 호스트 LIVE 실제 화면에서 별도 확인해야 합니다.",
                13, 0xFFDBD0E7));
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
        if (RadioDirectRtmpService.isActive()) {
            startService(new Intent(this, RadioDirectRtmpService.class)
                    .setAction(RadioDirectRtmpService.ACTION_SAY)
                    .putExtra(RadioDirectRtmpService.EXTRA_SAY, line.text)
                    .putExtra(RadioDirectRtmpService.EXTRA_LANGUAGE, line.language));
            return;
        }
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

    private void updateBigoChatStatus() {
        if (bigoChatStatus == null) return;
        bigoChatStatus.setText("동의: "
                + (BigoControlAssist.commentReplies(this) ? "댓글 감지 ON" : "OFF")
                + " | Android 서비스: "
                + (BigoControlAssist.connected(this) ? "연결됨" : "연결 안 됨")
                + "\\n" + BigoControlAssist.chatState(this));
    }

    @Override protected void onResume() {
        super.onResume();
        if (bigoChatEnabled != null) {
            syncingChatToggle = true;
            bigoChatEnabled.setChecked(BigoControlAssist.commentReplies(this));
            syncingChatToggle = false;
        }
        updateBigoChatStatus();
    }

    private void updateGreetingStatus() {
        if (greetingStatus == null) return;
        boolean fileChosen = !RadioDjSettings.voiceClip(this).isEmpty();
        boolean enabled = RadioDjSettings.recordedOpening(this);
        greetingStatus.setText("인사 녹음: " + (fileChosen ? "등록됨" : "아직 없음")
                + "  |  자동 오프닝: " + (enabled ? "형 목소리 사용" : "영어 TTS")
                + "  |  방송 시작 인사: "
                + (RadioDjSettings.opening(this) ? "켜짐" : "꺼짐"));
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
            try (android.content.res.AssetFileDescriptor probe =
                    getContentResolver().openAssetFileDescriptor(uri, "r")) {
                if (probe == null) {
                    oneLine("이 녹음 파일을 열 수 없어요. 다른 오디오 파일을 선택해 줘.");
                    return;
                }
            } catch (java.io.IOException ex) {
                oneLine("선택한 녹음 파일을 읽을 수 없어요.");
                return;
            }
            RadioDjSettings.setVoiceClip(this, uri);
            updateGreetingStatus();
            new AlertDialog.Builder(this)
                    .setTitle("아환 방송 시작에 형의 목소리 사용")
                    .setMessage("형이 영어로 직접 녹음한 인사 파일인가요? "
                            + "영어 인사 녹음이라면 자동 방송 시작 때 그 실제 목소리를 "
                            + "재생할 수 있어요. 한국어 녹음이라면 영어만 사용하도록 "
                            + "정한 방송 규칙과 달라지므로 미리듣기만 선택해 주세요. "
                            + "녹음 파일 하나로 새로운 문장까지 목소리를 복제하지는 못합니다.")
                    .setPositiveButton("영어 녹음 · 자동 시작에 사용", (dialog, which) -> {
                        RadioDjSettings.setRecordedOpening(this, true);
                        useRecordedGreeting.setChecked(true);
                        useOpening.setChecked(true);
                        updateGreetingStatus();
                        oneLine("형이 녹음한 인사 파일을 자동 방송 시작에 연결했어.");
                    })
                    .setNegativeButton("미리듣기만", (dialog, which) -> {
                        RadioDjSettings.setRecordedOpening(this, false);
                        useRecordedGreeting.setChecked(false);
                        updateGreetingStatus();
                        oneLine("녹음 인사를 보관했어. 자동 시작은 영어 TTS 그대로야.");
                    })
                    .show();
        } catch (SecurityException ex) {
            oneLine("녹음 파일에 대한 장기 읽기 권한을 얻지 못했어요.");
        }
    }
}
