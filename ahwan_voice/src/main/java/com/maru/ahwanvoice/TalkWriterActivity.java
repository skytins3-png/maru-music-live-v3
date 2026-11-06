package com.maru.ahwanvoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Producer-controlled talk writer. It proposes original fictional scripts;
 * it cannot produce speech or clone singing voices. All talk must be reviewed
 * and recorded by a permitted speaker before being included in the audio queue.
 */
public final class TalkWriterActivity extends Activity {
    public static final String EXTRA_SINGER_INDEX="com.maru.ahwanvoice.SINGER_INDEX";
    private static final String PREF_NAME="ahwan_adaptive_writer_private_v1";
    private static final int MAX_SCRIPT_LENGTH=4500;
    private SharedPreferences pref;
    private AdaptiveTalkEngine.Memory memory;
    private Spinner topic;
    private EditText script;
    private TextView state;
    private int singerIndex, currentTopic=-1;
    private AdaptiveTalkEngine.Suggestion proposal;

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        pref=getSharedPreferences(PREF_NAME,MODE_PRIVATE);
        singerIndex=Math.max(0,Math.min(LiveShowActivity.SINGERS.length-1,
                getIntent().getIntExtra(EXTRA_SINGER_INDEX,0)));
        memory=AdaptiveTalkEngine.Memory.deserialize(pref.getString("ratings",""));
        buildUi();
        suggest();
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout parent,String value,int size){
        TextView view=new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0,dp(7),0,dp(7));
        parent.addView(view);
        return view;
    }
    private Button button(LinearLayout parent,String title,View.OnClickListener callback){
        Button view=new Button(this);
        view.setText(title);
        view.setAllCaps(false);
        view.setOnClickListener(callback);
        parent.addView(view);
        return view;
    }
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout area=new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(16),dp(18),dp(16),dp(30));
        scroll.addView(area);
        setContentView(scroll);
        label(area,"아환 AI 토크 작가 · 학습형 방송 대본",23);
        label(area,"오늘 초대 가수: "+LiveShowActivity.SINGERS[singerIndex]
                +" (가상 AI 가수) · 실제 사람의 근황이 아니라 창작 설정을 바탕으로 대화합니다.",14);
        label(area,"일상·생활·개그·사연·여행·음악 이야기 중 선택",16);
        String[] items=new String[AdaptiveTalkEngine.topicCount()+1];
        items[0]="자동 추천 · 최근 주제 반복 피하기";
        System.arraycopy(AdaptiveTalkEngine.TOPICS,0,items,1,AdaptiveTalkEngine.topicCount());
        topic=new Spinner(this);
        topic.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,items));
        area.addView(topic);
        button(area,"새로운 방송 이야기 제안",v->suggest());
        script=new EditText(this);
        script.setMinLines(8);
        script.setMaxLines(18);
        script.setTextSize(15);
        script.setHint("대화 대본 미리보기 · 진행자가 직접 수정할 수 있어요.");
        script.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        area.addView(script);
        button(area,"👍 이 대화 마음에 들어 · 다음 편에 반영",v->rate(1));
        button(area,"👎 반복되거나 재미없어 · 다음 추천에 반영",v->rate(-1));
        button(area,"대본 승인·가수별 보관",v->approve());
        button(area,"승인한 대본 다시 불러오기",v->restore());
        button(area,"대본 복사",v->copyScript());
        button(area,"형의 실제 목소리 녹음실 열기",v->
                startActivity(new Intent(this,VoiceStudioActivity.class)));
        button(area,"학습 기록 초기화 (확인 후)",v->clearMemory());
        state=label(area,"오프라인 개인 취향 기억 · 다음 주제를 중복 없이 추천합니다.",14);
        label(area,"이 기능의 '학습'은 사용자가 선택한 주제와 좋아요/싫어요를 기억해 "
                +"다음 대본을 고르는 방식입니다. 인터넷 자동 수집·기초 AI 모델 재훈련은 하지 않습니다. "
                +"청취자 개인정보와 댓글을 학습에 저장하지 않습니다. "
                +"진짜 목소리는 녹음·권리 승인한 파일만 방송실에 등록해야 하며 "
                +"휴대폰 TTS와 자동 음성 복제는 사용하지 않습니다.",12);
    }

    private void suggest(){
        int selected=topic.getSelectedItemPosition()-1;
        proposal=AdaptiveTalkEngine.suggest(memory,selected,singerIndex,
                LiveShowActivity.SINGERS[singerIndex]);
        currentTopic=proposal.topic;
        script.setText(proposal.script);
        if(state!=null)state.setText("대본 제안: "+proposal.title
                +" · 지금까지 반영된 평가 "+memory.episodes+"건");
    }

    private void rate(int direction) {
        if(currentTopic<0 || proposal==null){
            state.setText("먼저 새로운 방송 이야기를 제안하세요.");
            return;
        }
        AdaptiveTalkEngine.record(memory,currentTopic,direction);
        pref.edit().putString("ratings",memory.serialize()).apply();
        state.setText((direction>0?"좋아요":"아쉬움")+" 평가 저장 · 다음 추천부터 반영합니다.");
        // Deliberately do not store or auto-broadcast raw suggested text on rating.
        suggest();
    }

    private String key(){return "approved_script_"+singerIndex;}
    private void approve() {
        String value=script.getText().toString().trim();
        if(value.isEmpty() || value.length()>MAX_SCRIPT_LENGTH){
            state.setText("방송 대본은 1~4500자로 입력해 주세요.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("방송 대본 최종 승인")
            .setMessage("실제 방송 전 사연 공개 동의·사실관계·저작권을 다시 확인하세요. "
                    +"대본을 승인해도 자동 음성 생성이나 BIGO 송출은 시작되지 않습니다.")
            .setPositiveButton("가수별 대본 저장",(d,w)->{
                pref.edit().putString(key(),value).apply();
                state.setText("대본 승인·개인 기기 보관 완료 · 실제 녹음을 등록해 방송하세요.");
            })
            .setNegativeButton("취소",null).show();
    }
    private void restore() {
        String value=pref.getString(key(),"");
        if(value.isEmpty()) {state.setText("이 가수의 승인된 대본이 없습니다.");return;}
        script.setText(value);
        state.setText("승인한 대본을 불러왔습니다. 방송 전에 내용을 확인하세요.");
    }
    private void copyScript() {
        String value=script.getText().toString();
        if(value.trim().isEmpty()){state.setText("복사할 대본이 없습니다.");return;}
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm==null){state.setText("이 기기에서 복사 기능을 사용할 수 없습니다.");return;}
        cm.setPrimaryClip(ClipData.newPlainText("아환 방송 대본",value));
        state.setText("대본 복사 완료 · 녹음실에서 형의 실제 목소리로 녹음하세요.");
    }
    private void clearMemory(){
        new AlertDialog.Builder(this)
            .setTitle("취향 학습 기록 초기화")
            .setMessage("좋아요·싫어요와 최근 대화 주제 기록만 지웁니다. "
                    +"승인된 방송 대본과 원본 음원은 삭제하지 않습니다.")
            .setPositiveButton("학습 기록 초기화",(d,w)->{
                memory=new AdaptiveTalkEngine.Memory();
                pref.edit().remove("ratings").apply();
                suggest();
                state.setText("주제 학습 기록 초기화 완료 · 승인한 대본은 보존했습니다.");
            })
            .setNegativeButton("취소",null).show();
    }
    public String scriptForTest(){return script==null?"":script.getText().toString();}
    public String stateForTest(){return state==null?"":state.getText().toString();}
}
