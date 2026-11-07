package com.maru.writers;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
/** Manual cue reader only. No TTS, background mic, or BIGO broadcast claim. */
public final class CueActivity extends Activity{
 private final ArrayList<String> cues=new ArrayList<>();
 private int current=0;
 private TextView counter,speaker,body;
 private int dp(int a){return (int)(a*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(Bundle b){super.onCreate(b);
  getWindow().setStatusBarColor(Color.rgb(8,17,29));
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(20),dp(20),dp(20));root.setBackgroundColor(Color.rgb(8,17,29));setContentView(root);
  TextView title=new TextView(this);title.setText("MARU 방송대본 · 진행 큐");title.setTextColor(0xff75e5c6);title.setTextSize(25);root.addView(title);
  TextView caption=new TextView(this);caption.setText("방송용 오디오 출력이 아닌 수동 대본 진행 화면입니다.");caption.setTextColor(Color.LTGRAY);caption.setPadding(0,dp(12),0,dp(20));root.addView(caption);
  counter=new TextView(this);counter.setTextColor(Color.WHITE);counter.setTextSize(16);root.addView(counter);
  speaker=new TextView(this);speaker.setTextColor(0xff75e5c6);speaker.setTextSize(23);speaker.setPadding(0,dp(22),0,dp(12));root.addView(speaker);
  ScrollView scroll=new ScrollView(this);body=new TextView(this);body.setTextColor(Color.WHITE);body.setTextSize(22);body.setPadding(0,dp(6),0,dp(6));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
  Button prev=new Button(this);prev.setText("이전");buttons.addView(prev,new LinearLayout.LayoutParams(0,dp(55),1));
  Button next=new Button(this);next.setText("다음 ▶");buttons.addView(next,new LinearLayout.LayoutParams(0,dp(55),1));root.addView(buttons);
  prev.setOnClickListener(v->{if(current>0)current--;show();});
  next.setOnClickListener(v->{if(current<cues.size()-1)current++;show();});
  String script=getIntent().getStringExtra("script");
  if(script!=null)for(String l:script.split("\\r?\\n")){
    if(l.startsWith("[아환]")||l.startsWith("[윤솔]")||l.startsWith("[가수:")||l.startsWith("[큐]")||l.startsWith("[음악]"))cues.add(l);
  }
  show();
 }
 private void show(){
  counter.setText(cues.isEmpty()?"대본이 없습니다":(current+1)+" / "+cues.size());
  if(cues.isEmpty()){speaker.setText("");body.setText("먼저 작가국에서 대본을 작성하세요.");return;}
  String line=cues.get(current);int k=line.indexOf(']');
  speaker.setText(k>=0?line.substring(0,k+1):"대본");
  body.setText(k>=0?line.substring(k+1).trim():line);
 }
}