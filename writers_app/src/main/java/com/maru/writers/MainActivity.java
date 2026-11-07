package com.maru.writers;

import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
 private final int BG=0xff081522, FG=0xffedf4f9, TEAL=0xff75e5c6;
 private static final int EXPORT=147;
 private EditText topic,song,story,sourceText,sourceUrl,script;
 private Spinner genre,singer;
 private CheckBox storyConsent,researchConsent;
 private TextView status,archive;
 private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 private TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(FG);t.setPadding(0,dp(12),0,dp(5));return t;}
 private EditText input(LinearLayout root,String hint,int lines){
  EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(0xff8392a5);e.setTextColor(FG);e.setTextSize(15);
  e.setSingleLine(lines==1);e.setMinLines(lines);e.setMaxLines(Math.max(lines,8));
  e.setPadding(dp(10),dp(10),dp(10),dp(10));e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(TEAL));
  root.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
 }
 private Button button(LinearLayout root,String caption,Runnable handler){
  Button b=new Button(this);b.setAllCaps(false);b.setText(caption);b.setTextSize(16);
  root.addView(b,new LinearLayout.LayoutParams(-1,dp(53)));
  b.setOnClickListener(v->handler.run());return b;
 }
 private Spinner select(LinearLayout root,String[] values){
   Spinner spin=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);
   spin.setAdapter(a);root.addView(spin,new LinearLayout.LayoutParams(-1,dp(50)));return spin;
 }
 private void show(String m){runOnUiThread(()->status.setText(m));}
 private String val(EditText e){return e.getText().toString().trim();}
 private boolean validSource(String s){
   try{Uri u=Uri.parse(s);return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&!u.getHost().isEmpty()&&!s.contains("@");}
   catch(Exception ignored){return false;}
 }
 private void updateArchive(){
  archive.setText("검토 후 보관한 전문지식: "+KnowledgeStore.records(this).length()+"건 (기기 내부에만 저장)");
 }
 @Override public void onCreate(Bundle b){super.onCreate(b);
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(35));scroll.addView(root);setContentView(scroll);
  TextView head=label("MARU WRITERS STUDIO",27);head.setTextColor(TEAL);head.setTypeface(null,Typeface.BOLD);root.addView(head);
  root.addView(label("독립 작가국 V1.0 · 방송대본 제작",16));
  root.addView(label("작가 5명은 가수/진행자와 별개입니다.",14));
  for(String[] w:EditorialEngine.WRITERS)root.addView(label(w[0]+"  "+w[1]+"  — "+w[2],14));
  root.addView(label("1. 방송 기획",21));
  root.addView(label("주제",15));topic=input(root,"예: 가을 음악의 기억",1);
  topic.setText("가을 음악의 기억");
  root.addView(label("등록할 노래 제목",15));song=input(root,"실제로 보유한 음악 제목",1);
  root.addView(label("장르",15));genre=select(root,EditorialEngine.GENRES);
  root.addView(label("가상 초대 가수 (없음도 가능)",15));
  String[] guests=new String[EditorialEngine.SINGERS.length+1];guests[0]="없음";
  System.arraycopy(EditorialEngine.SINGERS,0,guests,1,EditorialEngine.SINGERS.length);
  singer=select(root,guests);
  root.addView(label("2. 조사 자료",21));
  root.addView(label("자료 조사: 한국어 위키백과 요약 (사용자가 확인해야 함)",14));
  button(root,"인터넷에서 주제 조사",()->research());
  root.addView(label("자료 요약 / 직접 작성 및 수정",15));sourceText=input(root,"자료 조사 버튼을 누르거나 직접 요약 입력",4);
  root.addView(label("출처 URL (https://)",15));sourceUrl=input(root,"https://...",1);
  researchConsent=new CheckBox(this);researchConsent.setText("출처와 사실을 직접 검토했고 방송 참고에 동의");researchConsent.setTextColor(FG);root.addView(researchConsent);
  button(root,"확인한 자료를 지식보관함에 저장",()->keepEvidence());
  archive=label("",13);root.addView(archive);updateArchive();
  button(root,"이 주제의 저장 자료 불러오기",()->loadEvidence());
  root.addView(label("3. 승인된 청취자 사연 (선택)",21));
  story=input(root,"실제 공개 승인을 받은 사연만 입력",4);
  storyConsent=new CheckBox(this);storyConsent.setText("사연 공개 및 방송 낭독 승인 확인");storyConsent.setTextColor(FG);root.addView(storyConsent);
  root.addView(label("4. 5명 작가 협업 대본",21));
  button(root,"5명 작가 역할로 방송대본 작성",()->generate());
  script=input(root,"여기에 대본이 표시됩니다. 직접 수정할 수 있어요.",11);
  script.setText(KnowledgeStore.loadScript(this));
  button(root,"대본 수정본 보관",()->{KnowledgeStore.saveScript(this,val(script));show("대본을 휴대폰 내부에 저장했습니다.");});
  button(root,"방송 진행 큐 열기",()->{
    String v=val(script);if(v.isEmpty()){show("먼저 대본을 작성하세요.");return;}
    Intent i=new Intent(this,CueActivity.class);i.putExtra("script",v.substring(0,Math.min(45000,v.length())));startActivity(i);
  });
  button(root,"대본 TXT 파일로 내보내기",()->exportScript());
  button(root,"대본 공유",()->{
    if(val(script).isEmpty()){show("대본을 먼저 작성하세요.");return;}
    Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,val(script));
    startActivity(Intent.createChooser(i,"방송대본 공유"));
  });
  status=label("준비 완료. 조사 → 출처 검토 → 대본 작성 → 큐 진행",14);status.setTextColor(TEAL);root.addView(status);
  root.addView(label("※ 이 버전의 작가는 자동 문장 구성·검수 역할입니다. 외부 생성형 AI 5개 모델, 무인 웹 순회, BIGO 라이브 음성 송출은 구현되지 않았습니다. 확인되지 않은 날씨/뉴스/사연은 생성하지 않습니다.",12));
 }
 private void research(){
   String t=val(topic);if(t.isEmpty()){show("먼저 주제를 입력하세요.");return;}
   show("공개 백과사전에서 자료를 검색하고 있습니다...");
   new Thread(()->{
     try{
       WikiResearch.Result r=WikiResearch.lookup(t);
       runOnUiThread(()->{sourceText.setText(r.summary);sourceUrl.setText(r.source);researchConsent.setChecked(false);
         show("자료 '"+r.title+"' 검색 완료. 원문을 확인한 뒤 승인해 주세요.");});
     }catch(Exception e){show("검색 불가: "+e.getMessage()+". 직접 자료를 입력할 수도 있어요.");}
   },"maru-wiki-research").start();
 }
 private void keepEvidence(){
  String t=val(topic),summary=val(sourceText),src=val(sourceUrl);
  if(t.isEmpty()||summary.isEmpty()||!validSource(src)||!researchConsent.isChecked()){
    show("주제·자료·HTTPS 출처를 입력하고 검토 완료를 체크해 주세요.");return;
  }
  try{KnowledgeStore.keep(this,t,summary.length()>600?summary.substring(0,600):summary,src);updateArchive();show("검토한 자료를 보관했습니다.");}
  catch(Exception e){show("자료 보관 오류: "+e.getMessage());}
 }
 private void loadEvidence(){
  JSONObject j=KnowledgeStore.latestFor(this,val(topic));
  if(j==null){show("이 주제에 저장된 자료가 없습니다.");return;}
  sourceText.setText(j.optString("summary",""));sourceUrl.setText(j.optString("source",""));
  researchConsent.setChecked(false);
  show("저장 자료를 불러왔습니다. 사용 전 출처를 다시 확인하세요.");
 }
 private void generate(){
  if(!EditorialEngine.uniqueWriters()){show("작가 이름이 가수와 중복됩니다. 생성 중단.");return;}
  String g=(String)genre.getSelectedItem(),a=(String)singer.getSelectedItem();
  boolean approved=researchConsent.isChecked()&&validSource(val(sourceUrl));
  String result=EditorialEngine.write(val(topic),val(song),g,a,val(story),storyConsent.isChecked(),
                           val(sourceText),val(sourceUrl),approved);
  script.setText(result);KnowledgeStore.saveScript(this,result);
  show("대본 작성 완료. 수정·저장 후 진행 큐로 확인하세요. 음성 송출은 연결되지 않았습니다.");
 }
 private void exportScript(){
  if(val(script).isEmpty()){show("먼저 대본을 작성하세요.");return;}
  Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
  i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"MARU_RADIO_SCRIPT.txt");
  try{startActivityForResult(i,EXPORT);}catch(Exception e){show("파일 저장 화면을 열 수 없습니다: "+e.getMessage());}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=EXPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
  try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
    if(out==null)throw new IOException("쓰기 권한을 확인할 수 없습니다.");
    out.write(val(script).getBytes(StandardCharsets.UTF_8));out.flush();
    show("방송대본 TXT 저장 완료");
  }catch(Exception e){show("파일 저장 실패: "+e.getMessage());}
 }
}