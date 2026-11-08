package com.maru.media;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MARU MEDIA host-only, standalone on one Android phone.
 * No public audience database until separately configured/verified HTTPS server.
 */
public final class MainActivity extends Activity {
 private static final int PICK_TREE=120, PICK_AUDIO=121, PICK_MANIFEST=122,
                          EXPORT=123, PICK_SCORE=124, PICK_STORY_FILE=125;
 private final int bg=0xff081524, card=0xff122b3d, white=0xfff6fbff, teal=0xff60e7d5;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private VaultDb db;
 private LinearLayout root,main;
 private TextView status,summary;
 private MediaPlayer media;
 private VaultDb.Track exportTarget;
 private long scoreTarget=-1;
 private String storyAttachment="";
 private int visible=35;
 private String currentFilter="전체",searchTerm="";
 private int currentTab=0;
 private String endpoint="", adminToken=""; // Never persist the secret token.
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private void notice(String text){ui.post(()->{if(status!=null)status.setText(text);});}
 private TextView label(String text,int size){TextView x=new TextView(this);x.setText(text);x.setTextSize(size);x.setTextColor(white);
   x.setPadding(dp(3),dp(6),dp(3),dp(6));return x;}
 private LinearLayout vertical(){LinearLayout p=new LinearLayout(this);p.setOrientation(1);return p;}
 private Button btn(LinearLayout p,String title,Runnable f){
   Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextColor(white);b.setTextSize(15);
   b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff17536c));
   LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(49));params.setMargins(0,dp(4),0,dp(4));p.addView(b,params);
   b.setOnClickListener(v->f.run());return b;
 }
 private void heading(LinearLayout p,String t){TextView h=label(t,20);h.setTextColor(teal);p.addView(h);}
 private void paragraph(LinearLayout p,String t){TextView x=label(t,13);x.setTextColor(0xffaabbca);p.addView(x);}
 private EditText edit(LinearLayout p,String hint,String value,int lines){
   EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextSize(15);e.setTextColor(white);
   e.setHintTextColor(0xff8399ae);e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(teal));
   e.setMinLines(lines);e.setMaxLines(lines<=1?1:5);e.setSingleLine(lines==1);p.addView(e);
   return e;
 }
 private void initPage(int tab){
   currentTab=tab;main.removeAllViews();updateSummary();
 }
 @Override public void onCreate(Bundle state){
   super.onCreate(state);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
   db=new VaultDb(this);
   ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
   root=vertical();root.setPadding(dp(16),dp(14),dp(16),dp(32));scroll.addView(root);setContentView(scroll);
   TextView title=label("MARU RADIO",29);title.setTypeface(null,Typeface.BOLD);title.setTextColor(teal);root.addView(title);
   root.addView(label("노래 · 사연 관리 센터 | 갤럭시 한 대",15));
   summary=label("",13);root.addView(summary);
   LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
   root.addView(nav);
   String[] labels={"노래","사연","업로드","설정"};
   for(int i=0;i<labels.length;i++){
     final int index=i;
     Button b=new Button(this);b.setAllCaps(false);b.setText(labels[i]);b.setTextSize(13);
     LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);nav.addView(b,p);
     b.setOnClickListener(v->switchTab(index));
   }
   status=label("비공개 보관함 · 서버 공개는 승인 후에만",13);status.setTextColor(teal);root.addView(status);
   main=vertical();root.addView(main);
   switchTab(0);
 }
 private void updateSummary(){
   if(summary!=null)summary.setText("보관곡 "+db.count()+"곡  |  서버 업로드 확인 "+db.uploaded()+"곡  |  원곡은 삭제하지 않음");
 }
 private void switchTab(int tab){
   initPage(tab);
   if(tab==0)renderSongs();
   else if(tab==1)renderStories();
   else if(tab==2)renderUpload();
   else renderSettings();
 }
 private void startFolder(){
   Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
   i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
   startActivityForResult(i,PICK_TREE);
 }
 private void startFiles(){
   Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
   i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
   i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/mpeg","audio/mp4","audio/x-m4a","application/zip","application/octet-stream"});
   i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
   startActivityForResult(i,PICK_AUDIO);
 }
 private void startManifest(){
   Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
   i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   startActivityForResult(i,PICK_MANIFEST);
 }
 private void persistRead(Uri u){
   try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
   catch(SecurityException ignored){}catch(IllegalArgumentException ignored){}
 }
 private void renderSongs(){
   initPage(0);
   heading(main,"노래 보관함");
   paragraph(main,"폴더 또는 ZIP 선택 → SHA-256 중복 검사 → 제목별 임시 분류. 곡을 선택하면 댓글·악보 등 하위 기능이 열립니다.");
   btn(main,"🎵 폴더에서 노래 한꺼번에 가져오기",this::startFolder);
   btn(main,"⬆ MP3·M4A·ZIP 파일 가져오기",this::startFiles);
   btn(main,"📋 107곡 검증목록 JSON 적용",this::startManifest);
   Spinner categories=new Spinner(this);
   ArrayList<String> filters=new ArrayList<>(Arrays.asList(CatalogLogic.LANGUAGES));
   for(String g:CatalogLogic.GENRES)if(!"미확인".equals(g))filters.add(g);
   ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,filters);
   categories.setAdapter(adapter);categories.setSelection(Math.max(0,filters.indexOf(currentFilter)));
   main.addView(categories);
   categories.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
     boolean initial=true;
     @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){
       String val=filters.get(position);
       if(initial){initial=false;return;}
       currentFilter=val;visible=35;renderSongs();
     }
     @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
   });
   EditText search=edit(main,"곡명 검색 (입력 후 검색 버튼)",searchTerm,1);
   btn(main,"검색",()->{searchTerm=search.getText().toString().trim();visible=35;renderSongs();});
   List<VaultDb.Track> all=db.tracks(currentFilter),display=new ArrayList<>();
   for(VaultDb.Track t:all){
     if(searchTerm.isEmpty()||(t.title+t.filename).toLowerCase(Locale.ROOT).contains(searchTerm.toLowerCase(Locale.ROOT)))
       display.add(t);
   }
   heading(main,"분류된 곡 "+display.size()+"곡 / 전체 "+db.count()+"곡");
   int limit=Math.min(visible,display.size());
   for(int i=0;i<limit;i++){
     VaultDb.Track t=display.get(i);
     LinearLayout tile=vertical();tile.setPadding(dp(10),dp(9),dp(10),dp(9));tile.setBackgroundColor(card);
     LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));main.addView(tile,lp);
     tile.addView(label(t.title,16));
     paragraph(tile,t.language+" · "+t.genre+" · "+(t.size/(1024*1024))+"MB · "+t.uploadState);
     LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);tile.addView(actions);
     Button play=new Button(this);play.setText("▶ 미리듣기");play.setAllCaps(false);actions.addView(play,new LinearLayout.LayoutParams(0,dp(43),1));
     play.setOnClickListener(v->preview(t));
     Button more=new Button(this);more.setText("상세 · 댓글");more.setAllCaps(false);actions.addView(more,new LinearLayout.LayoutParams(0,dp(43),1));
     more.setOnClickListener(v->details(t.id));
   }
   if(display.size()>limit)btn(main,"더 보기 ("+(display.size()-limit)+"곡 남음)",()->{visible+=35;renderSongs();});
 }
 private void preview(VaultDb.Track t){
   stopPlayer();
   try{
     media=new MediaPlayer();
     Uri uri=Uri.parse(t.uri);
     if("file".equalsIgnoreCase(uri.getScheme()))media.setDataSource(uri.getPath());
     else media.setDataSource(this,uri);
     media.setOnPreparedListener(mp->{mp.start();db.view(t.id);notice("재생 중: "+t.title+" (이 기기 미리보기)");});
     media.setOnCompletionListener(mp->{stopPlayer();notice("미리듣기 종료");});
     media.setOnErrorListener((mp,what,extra)->{notice("파일을 열 수 없습니다. 원본 위치를 확인하세요.");stopPlayer();return true;});
     media.prepareAsync();
   }catch(Exception e){notice("재생 실패: "+e.getMessage());stopPlayer();}
 }
 private void stopPlayer(){if(media!=null){MediaPlayer x=media;media=null;try{x.reset();}catch(Exception ignored){}x.release();}}
 private void details(long id){
   VaultDb.Track t=db.get(id);if(t==null)return;
   ScrollView scroll=new ScrollView(this);LinearLayout box=vertical();box.setPadding(dp(12),dp(8),dp(12),dp(10));scroll.addView(box);
   box.addView(label(t.title,18));
   paragraph(box,t.language+" / "+t.genre+" · 이 기기 조회 "+t.views+"회 · 내 좋아요 "+t.liked);
   paragraph(box,"공개 서버 상태: "+("UPLOADED".equals(t.uploadState)?"업로드 전송 완료":"미업로드"));
   btn(box,"▶ 미리보기",()->preview(t));
   btn(box,"⬇ 파일 저장 / 다운로드",()->download(t));
   btn(box,t.liked==1?"♥ 좋아요 취소":"♡ 좋아요",()->{db.flag(id,"liked",t.liked==1?0:1);details(id);});
   btn(box,"💬 댓글 보기·작성",()->comments(id));
   btn(box,"🎼 악보 첨부 또는 보기",()->score(id));
   btn(box,"🎶 장르 변경",()->chooseGenre(id));
   btn(box,"✎ 제목 수정",()->rename(id));
   btn(box,t.rightsOk==1?"✓ 업로드 권한 확인됨 (변경)":"업로드 권한 확인 (원곡 소유)",()->toggleRights(id));
   new AlertDialog.Builder(this).setTitle("노래 상세 기능").setView(scroll).setPositiveButton("닫기",(a,b)->{if(currentTab==0)renderSongs();}).show();
 }
 private void chooseGenre(long id){
   new AlertDialog.Builder(this).setTitle("장르를 실제 듣고 확인한 뒤 선택").setItems(CatalogLogic.GENRES,(d,which)->{
     db.update(id,"genre",CatalogLogic.GENRES[which]);notice("장르 변경: "+CatalogLogic.GENRES[which]);
   }).show();
 }
 private void rename(long id){
   EditText e=new EditText(this);VaultDb.Track t=db.get(id);if(t==null)return;e.setText(t.title);
   new AlertDialog.Builder(this).setTitle("제목 수정").setView(e).setPositiveButton("저장",(d,w)->{
     db.update(id,"title",e.getText().toString());notice("제목을 수정했습니다.");}).setNegativeButton("취소",null).show();
 }
 private void toggleRights(long id){
   VaultDb.Track t=db.get(id);if(t==null)return;
   if(t.rightsOk==1){db.flag(id,"rights_ok",0);notice("업로드 권한 해제");return;}
   new AlertDialog.Builder(this).setTitle("저작권·배포권 확인").setMessage(
       "이 음원의 권리를 직접 보유하고, 공개 업로드 및 다운로드 허용 여부를 검토했나요? 승인해도 실제 서버 업로드는 별도 실행됩니다.")
      .setPositiveButton("확인·승인",(d,w)->{db.flag(id,"rights_ok",1);notice("업로드 권한 승인됨");})
      .setNegativeButton("취소",null).show();
 }
 private void comments(long id){
   LinearLayout panel=vertical();panel.setPadding(dp(15),dp(8),dp(15),dp(8));
   paragraph(panel,"댓글은 이 휴대폰의 로컬 기록입니다. 서버 연결 전 다른 청취자에게 공개되지 않습니다.");
   for(String comment:db.comments(id))panel.addView(label("• "+comment,14));
   EditText name=edit(panel,"닉네임","호스트",1);
   EditText body=edit(panel,"댓글 내용","",3);
   new AlertDialog.Builder(this).setTitle("댓글").setView(panel)
     .setPositiveButton("등록",(d,w)->{
       if(!body.getText().toString().trim().isEmpty()){
         db.addComment(id,name.getText().toString(),body.getText().toString());
         notice("댓글이 이 기기에 저장되었습니다.");
       }
     }).setNegativeButton("닫기",null).show();
 }
 private void score(long id){
   VaultDb.Track t=db.get(id);if(t==null)return;
   String[] opts={"악보(PDF/사진) 첨부","기존 악보 열기"};
   new AlertDialog.Builder(this).setTitle("악보").setItems(opts,(d,i)->{
     if(i==0){
       scoreTarget=id;Intent select=new Intent(Intent.ACTION_OPEN_DOCUMENT);
       select.addCategory(Intent.CATEGORY_OPENABLE);select.setType("*/*");
       select.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/png","image/jpeg","image/webp"});
       select.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
       startActivityForResult(select,PICK_SCORE);
     }else if(t.scoreUri==null||t.scoreUri.isEmpty()){notice("등록된 악보가 없습니다.");}
     else{
       Intent open=new Intent(Intent.ACTION_VIEW);
       Uri u=Uri.parse(t.scoreUri);
       open.setDataAndType(u,"*/*");open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
       try{startActivity(open);}catch(Exception e){notice("악보를 열 수 없습니다: "+e.getMessage());}
     }
   }).show();
 }
 private void download(VaultDb.Track t){
   exportTarget=t;
   Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
   i.setType(t.filename.toLowerCase(Locale.ROOT).endsWith(".m4a")?"audio/mp4":"audio/mpeg");
   i.putExtra(Intent.EXTRA_TITLE,t.filename);
   try{startActivityForResult(i,EXPORT);}catch(Exception e){notice("저장할 위치를 열 수 없습니다.");}
 }
 private void copyTo(VaultDb.Track t,Uri target){
   new Thread(()->{
     try(InputStream in=getContentResolver().openInputStream(Uri.parse(t.uri));
         OutputStream out=getContentResolver().openOutputStream(target)){
       if(in==null||out==null)throw new IOException("파일 열기 실패");
       byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
       out.flush();notice("원본 음원 다운로드/복사 완료: "+t.filename);
     }catch(Exception e){notice("저장 실패: "+e.getMessage());}
   },"maru-media-export").start();
 }
 private void renderStories(){
   initPage(1);
   heading(main,"사연 받기 · 관리");
   paragraph(main,"현재는 호스트 휴대폰 안에서 사연을 접수·승인합니다. 외부 청취자 웹 접수는 서버 연결 후 활성화됩니다.");
   EditText nick=edit(main,"사연 보낸 사람 닉네임","",1);
   EditText body=edit(main,"사연 내용","",4);
   btn(main,"📎 사진·영상·음성·악보 첨부 (선택)",()->{
     Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
     i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
     startActivityForResult(i,PICK_STORY_FILE);
   });
   if(!storyAttachment.isEmpty())paragraph(main,"첨부 선택됨: "+storyAttachment);
   btn(main,"사연 접수",()->{
     if(body.getText().toString().trim().isEmpty()){notice("사연 내용을 입력하세요.");return;}
     db.addStory(nick.getText().toString(),body.getText().toString(),storyAttachment);
     storyAttachment="";notice("사연이 검토대기로 저장되었습니다.");renderStories();
   });
   heading(main,"사연함 "+db.stories().size()+"건");
   for(VaultDb.Story s:db.stories()){
     LinearLayout tile=vertical();tile.setPadding(dp(10),dp(8),dp(10),dp(8));tile.setBackgroundColor(card);
     LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));main.addView(tile,lp);
     tile.addView(label(s.author+" · "+s.status,15));
     tile.addView(label(s.body.length()>100?s.body.substring(0,100)+"…":s.body,13));
     btn(tile,"접수 상세 · 승인/예약 준비",()->storyDetail(s));
   }
 }
 private void storyDetail(VaultDb.Story s){
   ScrollView scroll=new ScrollView(this);
   LinearLayout box=vertical();box.setPadding(dp(16),dp(12),dp(16),dp(12));scroll.addView(box);
   box.addView(label(s.author+"님의 사연",17));box.addView(label(s.body,14));
   paragraph(box,"상태: "+s.status);
   if(s.attachment!=null&&!s.attachment.isEmpty())btn(box,"📎 첨부파일 열기",()->{
     Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(Uri.parse(s.attachment),"*/*");
     i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
     try{startActivity(i);}catch(Exception e){notice("첨부파일 열기 실패");}
   });
   btn(box,"승인",()->{db.storyStatus(s.id,"승인");notice("사연 승인 완료");renderStories();});
   btn(box,"거절",()->{db.storyStatus(s.id,"거절");notice("사연 거절 처리");renderStories();});
   btn(box,"방송완료 표시",()->{db.storyStatus(s.id,"방송완료");notice("방송완료 상태 저장");renderStories();});
   btn(box,"대본 작가국으로 공유",()->{
     Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
     send.putExtra(Intent.EXTRA_TEXT,"MARU 사연\n"+s.author+"\n"+s.body);
     startActivity(Intent.createChooser(send,"대본 작가국으로 공유"));
   });
   new AlertDialog.Builder(this).setTitle("사연 상세").setView(scroll).setPositiveButton("닫기",(d,w)->renderStories()).show();
 }
 private void renderUpload(){
   initPage(2);
   heading(main,"노래 업로드");
   paragraph(main,"중복 검사한 음원만 업로드할 수 있습니다. 서버 URL이 없으면 외부 전송되지 않습니다. 현재 공개 청취자 서버는 미연결입니다.");
   EditText url=edit(main,"HTTPS 업로드 서버 주소 (예: https://example.org)",endpoint,1);
   EditText token=edit(main,"관리자 업로드 인증키 (앱에 저장하지 않음)","",1);
   token.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
   btn(main,"서버 주소 저장",()->{
     String x=url.getText().toString().trim();
     if(!x.startsWith("https://")){notice("HTTPS 서버 주소가 필요합니다.");return;}
     endpoint=x;getPreferences(MODE_PRIVATE).edit().putString("endpoint",endpoint).apply();
     notice("서버 주소만 저장했습니다. 인증키는 보관하지 않습니다.");
   });
   CheckBox rights=new CheckBox(this);rights.setText("업로드할 음원은 내 창작물이며 공개·배포 권한을 직접 확인함");
   rights.setTextColor(white);main.addView(rights);
   btn(main,"선택한 권리 확인을 전체 음원에 적용",()->{
     if(!rights.isChecked()){notice("권리 확인에 먼저 체크하세요.");return;}
     new AlertDialog.Builder(this).setTitle("전체 105곡 저작권 확인")
       .setMessage("전체 보관곡을 공개 전송 가능으로 표시합니다. 각 음원의 Suno 제작 시점과 이용 조건을 확인한 경우에만 선택하세요.")
       .setPositiveButton("전체 승인",(d,w)->{db.approvedAll(true);notice("모든 곡 업로드 권한 승인 표시");})
       .setNegativeButton("취소",null).show();
   });
   btn(main,"⬆ 권리 확인된 미업로드곡 서버에 전송",()->{
     endpoint=url.getText().toString().trim();adminToken=token.getText().toString();
     if(!endpoint.startsWith("https://")){notice("공개 서버가 연결되지 않았습니다. 비공개 보관만 가능합니다.");return;}
     if(adminToken.trim().length()<10){notice("서버 관리자 인증키가 필요합니다.");return;}
     new AlertDialog.Builder(this).setTitle("서버로 업로드")
       .setMessage("권리 확인 표시된 원곡을 HTTPS 서버로 하나씩 전송합니다. 기기의 화면을 켜 두세요. 실제 공개 여부는 서버에서 확인해야 합니다.")
       .setPositiveButton("전송 시작",(d,w)->uploadAll())
       .setNegativeButton("취소",null).show();
   });
   paragraph(main,"전송 기록: 서버 승인 응답을 받은 원곡 "+db.uploaded()+"곡. 서버 기능이 없는 상태에서는 '업로드 완료'로 표시하지 않습니다.");
 }
 private void uploadAll(){
   getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
   List<VaultDb.Track> all=db.tracks("전체");
   new Thread(()->{
     int done=0,failed=0,skip=0;String error="";
     try{
       for(VaultDb.Track t:all){
         if(t.rightsOk!=1||"UPLOADED".equals(t.uploadState)){skip++;continue;}
         notice("서버 전송 중: "+(done+failed+1)+" / "+all.size()+" · "+t.title);
         try{
           if(UploadClient.upload(this,t,endpoint,adminToken)){
             db.update(t.id,"upload_state","UPLOADED");done++;
           }
         }catch(Exception e){failed++;error=e.getMessage();break;}
       }
     }finally{
       final int d=done,f=failed,s=skip;final String e=error;
       ui.post(()->{
         getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
         updateSummary();
         notice("전송 성공 "+d+"곡 · 실패 "+f+"곡 · 제외 "+s+"곡 "+e);
       });
     }
   },"maru-media-upload").start();
 }
 private void renderSettings(){
   initPage(3);
   heading(main,"관리 · 설정");
   paragraph(main,"107곡 원본 → SHA-256 완전중복 제거 → 105곡 분류. 이 앱은 원곡을 삭제하거나 재인코딩하지 않습니다.");
   btn(main,"기기에서 원곡 ZIP 고르기",this::startFiles);
   btn(main,"검증목록 JSON 불러오기",this::startManifest);
   btn(main,"노래 보관함 열기",()->switchTab(0));
   btn(main,"미리듣기 정지",this::stopPlayer);
   paragraph(main,"노래/사연 데이터는 이 앱 안에 보관되며 기존 MARU RADIO·작가국 앱과 별도입니다.");
   paragraph(main,"댓글·좋아요·조회수는 서버 연동 전까지 이 기기 내부 기록입니다. 악보는 별도로 첨부한 파일만 열 수 있습니다.");
   paragraph(main,"107곡 분류는 파일명/문자 기준 잠정 분류이며 실제 트로트·발라드·연주 장르는 수동 청음 후 확정합니다.");
 }
 private String readText(Uri uri,int max)throws IOException{
   try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream b=new ByteArrayOutputStream()){
     if(in==null)throw new IOException("파일 열기 실패");
     byte[] chunk=new byte[8192];int n;
     while((n=in.read(chunk))!=-1){b.write(chunk,0,n);if(b.size()>max)throw new IOException("파일 크기 초과");}
     return b.toString(StandardCharsets.UTF_8.name());
   }
 }
 private void startImport(LibraryImporter.Stats unused,Uri tree,List<Uri> uris){
   notice("원곡 검사 중입니다. 파일 수에 따라 시간이 걸릴 수 있습니다.");
   getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
   new Thread(()->{
     LibraryImporter importer=new LibraryImporter(this,db,(checked,added,dupe,name)->{
       if(checked%5==0)notice("검사 "+checked+"곡 · 등록 "+added+" · 중복 "+dupe+" · "+name);
     });
     String error="";
     try{
       if(tree!=null)importer.fromTree(tree);
       else importer.fromAudioUris(uris);
       String manifest=getPreferences(MODE_PRIVATE).getString("manifest","");
       if(manifest!=null&&!manifest.isEmpty())db.importManifest(manifest);
     }catch(Exception e){error=e.getMessage();}
     LibraryImporter.Stats s=importer.stats();
     final String failure=error;
     ui.post(()->{
       getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
       updateSummary();renderSongs();
       notice("검사 "+s.scanned+"곡 · 새 등록 "+s.inserted+"곡 · 내용 중복 "+s.duplicate+"곡 · 오류 "+s.invalid+" "+failure);
     });
   },"maru-batch-import").start();
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
   super.onActivityResult(request,result,data);
   if(result!=RESULT_OK||data==null||data.getData()==null && data.getClipData()==null)return;
   if(request==PICK_TREE){
     Uri tree=data.getData();if(tree==null)return;persistRead(tree);startImport(null,tree,null);
   }else if(request==PICK_AUDIO){
     ArrayList<Uri> files=new ArrayList<>();
     if(data.getClipData()!=null){
       for(int i=0;i<data.getClipData().getItemCount();i++){
         Uri u=data.getClipData().getItemAt(i).getUri();if(u!=null){persistRead(u);files.add(u);}
       }
     }else if(data.getData()!=null){persistRead(data.getData());files.add(data.getData());}
     if(!files.isEmpty())startImport(null,null,files);
   }else if(request==PICK_MANIFEST){
     Uri u=data.getData();if(u==null)return;new Thread(()->{
       try{
         String json=readText(u,300000);
         getPreferences(MODE_PRIVATE).edit().putString("manifest",json).apply();
         int applied=db.importManifest(json);
         notice("107곡 검증목록 연결 · 기존 보관곡 "+applied+"곡 분류 갱신");
         ui.post(this::renderSongs);
       }catch(Exception e){notice("검증목록 오류: "+e.getMessage());}
     },"maru-manifest").start();
   }else if(request==EXPORT){
     if(exportTarget!=null&&data.getData()!=null)copyTo(exportTarget,data.getData());
   }else if(request==PICK_SCORE){
     if(data.getData()!=null&&scoreTarget>0){
       persistRead(data.getData());db.update(scoreTarget,"score_uri",data.getData().toString());
       notice("악보 등록 완료");scoreTarget=-1;
     }
   }else if(request==PICK_STORY_FILE){
     if(data.getData()!=null){persistRead(data.getData());storyAttachment=data.getData().toString();notice("사연 첨부파일 선택");renderStories();}
   }
 }
 @Override protected void onPause(){super.onPause();stopPlayer();}
 @Override protected void onDestroy(){stopPlayer();if(db!=null)db.close();super.onDestroy();}
}