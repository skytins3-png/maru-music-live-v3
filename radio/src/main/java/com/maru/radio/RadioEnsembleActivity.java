package com.maru.radio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/** Nine-person virtual radio schedule: 1 lead + 8 rotating guest personalities. */
public final class RadioEnsembleActivity extends Activity {
    private final int white = 0xFFF7F1FF;
    private final int secondary = 0xFFCBC1E7;
    private LinearLayout root;
    private TextView preview;
    private TextView pacing;
    private int sample = 0;
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(2), dp(8), dp(2), dp(8));
        return t;
    }
    private void button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        root.addView(b);
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF130F23);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                v.setPadding(0,bars.top,0,bars.bottom);
                return insets;
            });
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(32));
        scroll.addView(root);
        setContentView(scroll);
        root.addView(label("📻 아환과 AI 진행자 8명 · 라디오 방송", 22, white));
        root.addView(label("아환 1명 + 각자의 캐릭터가 있는 진행자 8명. "
                + "곡이 시작되기 전에 짧게 서로 대화하고, 대화가 끝나면 "
                + "Suno 원곡이 처음부터 끝까지 흐릅니다. "
                + "곡이 끝나면 다음 대화와 노래로 이어집니다.", 14, secondary));
        root.addView(label("⚠ 현재 대화는 오프라인에서 만들어지는 짧은 대본이고 "
                + "9명의 독립 AI 모델이나 9종 복제 음성이 아닙니다. "
                + "Android TTS 음정·속도를 달리해 구분합니다. "
                + "BIGO Audio LIVE 청취자에게 송출되는 기능은 별도 검증이 필요합니다.",
                13, 0xFFFFD09B));
        Switch enabled = new Switch(this);
        enabled.setText("✅ 진행자 9명 라디오 자동 편성 사용");
        enabled.setTextSize(17);
        enabled.setTextColor(white);
        enabled.setChecked(RadioEnsembleSettings.enabled(this));
        enabled.setOnCheckedChangeListener((v,checked)->{
            RadioEnsembleSettings.setEnabled(this,checked);
            refreshPreview();
        });
        root.addView(enabled);
        root.addView(label("🎤 방송 진행자 구성", 18, white));
        for(int i=0;i<RadioEnsembleScript.CAST.length;i++) {
            RadioEnsembleScript.Role r=RadioEnsembleScript.CAST[i];
            root.addView(label((i==0?"★ 대표 DJ  ":"• AI 게스트  ")
                    +r.name+" — "+r.desk,14,secondary));
        }
        root.addView(label("🎶 대화가 나오는 간격", 18, white));
        pacing = label("", 14,white);
        root.addView(pacing);
        SeekBar frequency=new SeekBar(this);
        frequency.setMax(3);
        frequency.setProgress(RadioEnsembleSettings.every(this)-1);
        frequency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int progress,boolean fromUser){
                if(!fromUser)return;
                RadioEnsembleSettings.setEvery(RadioEnsembleActivity.this,progress+1);
                refreshPreview();
            }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });
        root.addView(frequency);
        root.addView(label("🗣 한 번 대화에 참여하는 순서",18,white));
        SeekBar length=new SeekBar(this);
        length.setMax(2);
        length.setProgress(RadioEnsembleSettings.lines(this)-2);
        length.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int progress,boolean fromUser) {
                if(!fromUser)return;
                RadioEnsembleSettings.setLines(RadioEnsembleActivity.this,progress+2);
                refreshPreview();
            }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });
        root.addView(length);
        root.addView(label("📝 대화 대본 미리보기 (소리 없이 내용 확인)",18,white));
        preview=label("",14,secondary);
        root.addView(preview);
        button("🔄 다음 진행자 대화 보기",()->{ sample++; refreshPreview(); });
        button("🎵 MARU 음악 재생 화면으로", ()-> {
            Intent go=new Intent(this,RadioActivity.class);
            go.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(go);
            finish();
        });
        button("← 돌아가기", this::finish);
        refreshPreview();
    }
    private void refreshPreview() {
        if(pacing==null||preview==null)return;
        int every=RadioEnsembleSettings.every(this);
        int lines=RadioEnsembleSettings.lines(this);
        pacing.setText((RadioEnsembleSettings.enabled(this)?"켜짐 · ":"꺼짐 · ")
                +every+"곡마다 대화 (대사 "+lines+"개) · 전체 곡 중간 끊김 없음");
        List<RadioEnsembleScript.Line> linesToDisplay =
                RadioEnsembleScript.scene(sample,"가을 바다",lines);
        StringBuilder sb=new StringBuilder();
        for(RadioEnsembleScript.Line line:linesToDisplay)
            sb.append("🎙 ").append(line.role().name).append(": ")
                    .append(line.text).append("\n\n");
        sb.append("🎵 이어서 Suno 원곡 전체 재생");
        preview.setText(sb.toString());
    }
}
