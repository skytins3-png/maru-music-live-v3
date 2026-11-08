package com.maru.media;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
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
                          EXPORT=123, PICK_SCORE=124, PICK_STORY_FILE=125, EXPORT_STORY=126,PICK_COVER=127;
 private final int bg=0xff081524, card=0xff122b3d, white=0xfff6fbff, teal=0xff60e7d5;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private VaultDb db;
 private LinearLayout root,main;
 private ScrollView mainScroller;
 private TextView status,summary;
 private LinearLayout navBar;
 private MediaPlayer media;
 private VaultDb.Track exportTarget;
 private long scoreTarget=-1,coverTarget=-1;
 private String storyAttachment="",storyNickDraft="",storyBodyDraft="";
 private String pendingStoryExport="";
 private int visible=35;
 private String currentFilter="전체",searchTerm="";
 private int currentTab=0;
 private String endpoint="", adminToken=""; // Never persist the secret token.
 private GradientDrawable round(int a,int b,int radius){
   GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});
   d.setCornerRadius(dp(radius));return d;
 }
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
   super.onCreate(state);
   getWindow().setStatusBarColor(0xff041021);getWindow().setNavigationBarColor(0xff041021);
   db=new VaultDb(this);
   endpoint=getPreferences(MODE_PRIVATE).getString("endpoint","");
   LinearLayout page=vertical();page.setBackgroundColor(bg);
   mainScroller=new ScrollView(this);mainScroller.setFillViewport(true);
   root=vertical();root.setPadding(dp(12),dp(6),dp(12),dp(16));
   mainScroller.addView(root);page.addView(mainScroller,new LinearLayout.LayoutParams(-1,0,1));
   setContentView(page);
   summary=label("",12);summary.setTextColor(0xff9cbbd4);root.addView(summary);
   status=label("",12);status.setTextColor(teal);root.addView(status);
   main=vertical();root.addView(main);
   navBar=new LinearLayout(this);navBar.setOrientation(0);
   navBar.setPadding(dp(4),dp(2),dp(4),dp(2));
   navBar.setBackground(round(0xff172741,0xff0a1529,20));
   page.addView(navBar,new LinearLayout.LayoutParams(-1,dp(68)));
   switchTab(0);
 }
  private void updateSummary(){
   if(summary!=null)summary.setText("보관곡 "+db.count()+"곡  |  서버 업로드 확인 "+db.uploaded()+"곡  |  원곡은 삭제하지 않음");
 }

 private void switchTab(int tab){
   currentTab=tab;main.removeAllViews();
   summary.setVisibility(tab==0?View.GONE:View.VISIBLE);
   status.setVisibility(View.GONE);
   updateSummary();
   renderNavigation(tab);
   if(tab==0)renderHome();
   else if(tab==1)renderSongs();
   else if(tab==2)renderStories();
   else if(tab==3)renderUpload();
   else renderSettings();
   mainScroller.smoothScrollTo(0,0);
 }
 private void renderNavigation(int selected){
   navBar.removeAllViews();
   if(selected==0){
     FrameLayout frame=new FrameLayout(this);
     frame.setBackground(round(0xff172741,0xff0a1529,20));
     frame.setClipToOutline(true);
     try(java.io.InputStream in=getAssets().open("reference_nav_home.webp")){
       Bitmap art=BitmapFactory.decodeStream(in);
       if(art!=null){
         ImageView image=new ImageView(this);image.setImageBitmap(art);
         image.setScaleType(ImageView.ScaleType.FIT_XY);
         frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
       }
     }catch(Exception ignored){}
     String[] labels={"홈","노래","사연","내정보"};
     String[] ids={"nav-home","nav-songs","nav-stories","nav-account"};
     int[] targets={0,1,2,4};
     int width=Math.max(dp(220),getResources().getDisplayMetrics().widthPixels-dp(8));
     int h=dp(64);
     for(int i=0;i<4;i++){
       final int dest=targets[i];
       TextView area=new TextView(this);
       area.setText(labels[i]);area.setTextColor(Color.TRANSPARENT);
       area.setContentDescription(ids[i]);area.setGravity(Gravity.CENTER);
       FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width/4,h);
       p.leftMargin=i*width/4;
       frame.addView(area,p);area.setOnClickListener(v->switchTab(dest));
     }
     navBar.addView(frame,new LinearLayout.LayoutParams(-1,-1));
     return;
   }
   final String[] tabs={"⌂\n홈","♫\n노래","✉\n사연","♙\n내정보"};
   final int[] targets={0,1,2,4};
   for(int i=0;i<tabs.length;i++){
     int idx=i;
     TextView item=new TextView(this);item.setText(tabs[i]);item.setGravity(Gravity.CENTER);
     item.setTextSize(14);item.setTextColor(selected==targets[i]?0xff22bafc:0xffc9d2e5);
     item.setTypeface(null,selected==targets[i]?Typeface.BOLD:Typeface.NORMAL);
     item.setContentDescription(new String[]{"nav-home","nav-songs","nav-stories","nav-account"}[i]);
     LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
     navBar.addView(item,p);item.setOnClickListener(v->switchTab(targets[idx]));
   }
 }
 private TextView bigLabel(String s,int sp){
   TextView t=label(s,sp);t.setTextColor(white);t.setTypeface(null,Typeface.BOLD);return t;
 }
 private void renderHome(){
   // The artwork is cropped from the ORIGINAL reference image supplied
   // for MARU RADIO; actual controls remain native and accessible.
   main.removeAllViews();
   final int width=Math.max(dp(220),getResources().getDisplayMetrics().widthPixels-dp(24));
   final int height=Math.round(width*670f/410f);
   FrameLayout imageScreen=new FrameLayout(this);
   imageScreen.setClipToOutline(true);
   imageScreen.setBackground(round(0xff06152b,0xff06152b,14));
   try(java.io.InputStream in=getAssets().open("reference_home.webp")){
     Bitmap visual=BitmapFactory.decodeStream(in);
     if(visual!=null){
       ImageView image=new ImageView(this);
       image.setImageBitmap(visual);
       image.setScaleType(ImageView.ScaleType.FIT_XY);
       imageScreen.addView(image,new FrameLayout.LayoutParams(-1,-1));
     }
   }catch(Exception e){
     // Never leave the page inoperable if an image asset fails to decode.
     imageScreen.setBackground(round(0xff12365d,0xff071526,14));
   }
   // Reference coordinates: source cropped from image at x=26..493, y=80..843.
   // Native tap targets preserve working song/story/import/download actions.
   homeTap(imageScreen,"설정",.835f,.004f,.995f,.105f,width,height,()->switchTab(4));
   homeTap(imageScreen,"노래 보관함",.000f,.339f,.496f,.583f,width,height,()->switchTab(1));
   homeTap(imageScreen,"노래 업로드",.510f,.339f,1f,.583f,width,height,()->switchTab(3));
   homeTap(imageScreen,"노래 다운로드",.000f,.595f,.496f,.848f,width,height,this::renderDownloads);
   homeTap(imageScreen,"사연 보내기",.510f,.595f,1f,.848f,width,height,()->switchTab(2));
   homeTap(imageScreen,"인기 노래 TOP 5",.000f,.864f,1f,1f,width,height,this::topFive);
   main.addView(imageScreen,new LinearLayout.LayoutParams(-1,height));
 }
 private void homeTap(FrameLayout parent,String label,float left,float top,float right,float bottom,
                      int width,int height,Runnable action){
   TextView touch=new TextView(this);
   touch.setText(label);touch.setTextColor(Color.TRANSPARENT);
   touch.setContentDescription(label);
   touch.setGravity(Gravity.CENTER);touch.setBackgroundColor(Color.TRANSPARENT);
   int l=Math.round(width*left),t=Math.round(height*top);
   FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(
      Math.max(dp(40),Math.round(width*(right-left))),
      Math.max(dp(38),Math.round(height*(bottom-top))));
   p.leftMargin=l;p.topMargin=t;
   parent.addView(touch,p);
   touch.setClickable(true);
   touch.setOnClickListener(v->action.run());
 }
 private void homeTile(LinearLayout row,String pict,String name,String subtitle,int top,int bottom,Runnable run){
   LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);
   tile.setBackground(round(top,bottom,20));tile.setElevation(dp(2));
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(126),1);
   p.setMargins(dp(2),dp(4),dp(2),dp(6));row.addView(tile,p);
   TextView icon=bigLabel(pict,35);icon.setGravity(Gravity.CENTER);tile.addView(icon);
   TextView title=bigLabel(name,19);title.setGravity(Gravity.CENTER);tile.addView(title);
   TextView detail=label(subtitle,12);detail.setGravity(Gravity.CENTER);tile.addView(detail);
   tile.setOnClickListener(v->run.run());
 }
  private void topFive(){
   List<VaultDb.Track> all=db.tracks("전체");
   Collections.sort(all,(a,b)->Integer.compare(b.views,a.views));
   LinearLayout panel=vertical();panel.setPadding(dp(12),dp(12),dp(12),dp(12));
   paragraph(panel,"이 휴대폰에서 미리듣기한 횟수 기준입니다. 공개 서버 조회수가 아닙니다.");
   if(all.isEmpty())panel.addView(label("아직 보관한 노래가 없습니다.",15));
   for(int i=0;i<Math.min(5,all.size());i++){
     VaultDb.Track t=all.get(i);
     btn(panel,(i+1)+"위  "+t.title+" · "+t.views+"회",()->details(t.id));
   }
   new AlertDialog.Builder(this).setTitle("내 기기 인기 노래").setView(panel)
     .setPositiveButton("닫기",null).show();
 }
 private void renderDownloads(){
   initPage(0);heading(main,"노래 다운로드 · 파일 저장");
   paragraph(main,"내 보관함에서 선택한 원곡을 휴대폰 파일로 복사합니다. 서버 다운로드와는 별개입니다.");
   btn(main,"전체 보관함에서 곡 찾기",()->switchTab(1));
   List<VaultDb.Track> all=db.tracks("전체");
   int shown=Math.min(60,all.size());
   for(int i=0;i<shown;i++){
     VaultDb.Track track=all.get(i);
     btn(main,"⬇ "+track.title,()->download(track));
   }
   if(all.size()>shown)paragraph(main,"나머지 곡은 노래 보관함의 검색으로 선택하세요.");
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
 private boolean safeStoryAttachment(Uri u){
   String mime="";
   try{mime=getContentResolver().getType(u);}catch(Exception ignored){}
   if(mime==null)mime="";
   String name=u.getLastPathSegment()==null?"":u.getLastPathSegment();
   long size=-1;
   try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
     if(c!=null&&c.moveToFirst()){
       if(!c.isNull(0))name=c.getString(0);
       if(!c.isNull(1))size=c.getLong(1);
     }
   }catch(Exception ignored){}
   if(size>25L*1024*1024)return false;
   String lower=name.toLowerCase(java.util.Locale.ROOT);
   boolean knownExt=lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")||lower.endsWith(".webp")
        ||lower.endsWith(".mp3")||lower.endsWith(".m4a")||lower.endsWith(".mp4")||lower.endsWith(".pdf");
   if(!knownExt)return false;
   if(mime.equals("application/vnd.android.package-archive")
       ||mime.equals("application/x-msdownload")||mime.equals("application/x-executable"))return false;
   return mime.isEmpty()||mime.equals("application/octet-stream")||mime.startsWith("image/")
       ||mime.startsWith("audio/")||mime.equals("video/mp4")||mime.equals("application/pdf");
 }

 private void renderSongs(){
   initPage(1);
   LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
   main.addView(top,new LinearLayout.LayoutParams(-1,dp(59)));
   TextView back=bigLabel("‹",33);top.addView(back,new LinearLayout.LayoutParams(dp(42),-2));
   back.setOnClickListener(v->switchTab(0));
   TextView name=bigLabel("노래 보관함",22);name.setGravity(Gravity.CENTER);
   top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
   TextView searchIcon=bigLabel("⌕",29);searchIcon.setGravity(Gravity.CENTER);
   top.addView(searchIcon,new LinearLayout.LayoutParams(dp(42),-2));
   searchIcon.setOnClickListener(v->new AlertDialog.Builder(this).setItems(
       new String[]{"노래 검색","폴더에서 가져오기","MP3·ZIP 가져오기","107곡 검증목록 적용"},
       (d,index)->{
         if(index==0)searchPopup();
         else if(index==1)startFolder();
         else if(index==2)startFiles();
         else startManifest();
       }).show());
   LinearLayout pills=new LinearLayout(this);pills.setOrientation(0);
   main.addView(pills,new LinearLayout.LayoutParams(-1,dp(58)));
   for(String value:new String[]{"전체","인기","최신","내 노래"}){
     TextView p=label(value,14);p.setGravity(Gravity.CENTER);p.setTextColor(white);
     boolean chosen=currentFilter.equals(value);
     p.setBackground(round(chosen?0xff21bcf5:0xff202f49,chosen?0xff0968ee:0xff15223c,28));
     LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(39),1);
     lp.setMargins(dp(2),dp(4),dp(2),dp(6));pills.addView(p,lp);
     p.setOnClickListener(v->{currentFilter=value;visible=35;renderSongs();});
   }
   Spinner categories=new Spinner(this);
   ArrayList<String> filters=new ArrayList<>(Arrays.asList("전체","한국노래","중국노래","서양노래","일본노래","검토필요"));
   for(String genre:CatalogLogic.GENRES)if(!"미확인".equals(genre))filters.add(genre);
   ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,filters);
   categories.setAdapter(adapter);
   categories.setSelection(Math.max(0,filters.indexOf(currentFilter)));
   // Genre selection is available without cluttering the primary photo-style tabs.
   TextView controls=label("분류: "+(filters.contains(currentFilter)?currentFilter:"전체")
        +"    ·    보관 "+db.count()+"곡    ·    "+(searchTerm.isEmpty()?"전체 보기":"검색: "+searchTerm),12);
   controls.setTextColor(0xff93b9d3);
   controls.setGravity(Gravity.CENTER_VERTICAL);main.addView(controls);
   controls.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("노래 분류")
      .setItems(filters.toArray(new String[0]),(d,idx)->{currentFilter=filters.get(idx);visible=35;renderSongs();}).show());
   List<VaultDb.Track> all=db.tracks(filters.contains(currentFilter)?currentFilter:"전체");
   if(currentFilter.equals("인기"))Collections.sort(all,(x,y)->Integer.compare(y.views,x.views));
   if(currentFilter.equals("최신"))Collections.sort(all,(x,y)->Long.compare(y.id,x.id));
   List<VaultDb.Track> display=new ArrayList<>();
   for(VaultDb.Track track:all){
     if(searchTerm.isEmpty()||(track.title+" "+track.artist+" "+track.filename)
       .toLowerCase(Locale.ROOT).contains(searchTerm.toLowerCase(Locale.ROOT)))display.add(track);
   }
   if(display.isEmpty()){
     LinearLayout empty=vertical();empty.setPadding(dp(10),dp(28),dp(10),dp(10));
     main.addView(empty);
     TextView icon=bigLabel("♫",42);icon.setTextColor(teal);icon.setGravity(Gravity.CENTER);empty.addView(icon);
     TextView msg=label("아직 등록된 노래가 없습니다.",16);msg.setGravity(Gravity.CENTER);empty.addView(msg);
     btn(empty,"MP3 · ZIP 가져오기",this::startFiles);
     btn(empty,"원곡 폴더 불러오기",this::startFolder);
     return;
   }
   int limit=Math.min(display.size(),visible);
   for(int i=0;i<limit;i++){
     VaultDb.Track track=display.get(i);
     LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);
     card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(7),dp(8),dp(6),dp(8));
     card.setBackground(round(0xff182a43,0xff0d1d34,19));
     LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,dp(93));
     outer.setMargins(0,dp(3),0,dp(3));main.addView(card,outer);
     FrameLayout cover=artwork(track,70);
     card.addView(cover,new LinearLayout.LayoutParams(dp(70),dp(70)));
     TextView start=bigLabel("▶",15);start.setGravity(Gravity.CENTER);start.setBackground(round(0xcc061a31,0xcc061a31,30));
     FrameLayout.LayoutParams startP=new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.RIGHT|Gravity.BOTTOM);
     cover.addView(start,startP);start.setOnClickListener(v->preview(track));
     LinearLayout info=vertical();info.setPadding(dp(9),0,0,0);
     card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
     TextView title=bigLabel(track.title,16);title.setSingleLine(true);
     title.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(title);
     TextView singer=label(track.artist.isEmpty()?"등록 음원":track.artist,13);
     singer.setTextColor(0xffd2d5e0);info.addView(singer);
     TextView count=label("◉ "+track.views+"    ♥ "+track.liked,12);
     count.setTextColor(0xffb8c8da);info.addView(count);
     TextView down=bigLabel("↓",28);down.setGravity(Gravity.CENTER);
     down.setTextColor(white);
     card.addView(down,new LinearLayout.LayoutParams(dp(37),dp(55)));
     down.setOnClickListener(v->download(track));
     info.setOnClickListener(v->details(track.id));
     card.setOnClickListener(v->details(track.id));
   }
   if(display.size()>limit)btn(main,"더 보기 ("+(display.size()-limit)+"곡)",()->{visible+=35;renderSongs();});
 }
 private FrameLayout artwork(VaultDb.Track t,int size){
   FrameLayout frame=new FrameLayout(this);
   frame.setBackground(round(0xff955c7b,0xff222e54,12));
   frame.setClipToOutline(true);
   if(t.coverUri!=null&&!t.coverUri.isEmpty()){
     ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);
     art.setImageURI(Uri.parse(t.coverUri));frame.addView(art,new FrameLayout.LayoutParams(-1,-1));
   }else{
     TextView icon=bigLabel("♫",size>100?68:26);icon.setGravity(Gravity.CENTER);
     icon.setTextColor(0xfffce0ba);frame.addView(icon,new FrameLayout.LayoutParams(-1,-1));
   }
   return frame;
 }
 private void searchPopup(){
   EditText input=new EditText(this);input.setSingleLine(true);input.setText(searchTerm);input.setHint("곡 이름 또는 가수");
   new AlertDialog.Builder(this).setTitle("노래 검색").setView(input)
     .setPositiveButton("검색",(d,w)->{searchTerm=input.getText().toString().trim();renderSongs();})
     .setNegativeButton("취소",null).show();
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
   final Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen);
   LinearLayout shell=vertical();shell.setBackgroundColor(0xff081524);
   LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
   shell.addView(top,new LinearLayout.LayoutParams(-1,dp(58)));
   TextView back=bigLabel("‹",32);back.setGravity(Gravity.CENTER);
   top.addView(back,new LinearLayout.LayoutParams(dp(55),-1));back.setOnClickListener(v->dialog.dismiss());
   TextView heading=bigLabel("곡 상세",21);heading.setGravity(Gravity.CENTER);
   top.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
   TextView menu=bigLabel("⋮",28);menu.setGravity(Gravity.CENTER);
   top.addView(menu,new LinearLayout.LayoutParams(dp(55),-1));
   ScrollView scroller=new ScrollView(this);scroller.setFillViewport(true);
   shell.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
   LinearLayout body=vertical();body.setPadding(dp(10),0,dp(10),dp(12));scroller.addView(body);
   FrameLayout banner=artwork(t,190);
   LinearLayout.LayoutParams artParam=new LinearLayout.LayoutParams(-1,dp(187));
   artParam.setMargins(dp(2),0,dp(2),dp(7));body.addView(banner,artParam);
   TextView play=bigLabel("▶",31);play.setGravity(Gravity.CENTER);
   play.setBackground(round(0xdd071628,0xdd071628,40));
   FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(60),dp(60),Gravity.CENTER);
   banner.addView(play,pp);play.setOnClickListener(v->preview(t));
   LinearLayout info=vertical();info.setPadding(dp(16),dp(7),0,dp(8));
   FrameLayout.LayoutParams tip=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
   banner.addView(info,tip);
   TextView name=bigLabel(t.title,24);info.addView(name);
   TextView artist=label(t.artist.isEmpty()?"등록된 내 음원":t.artist,15);info.addView(artist);
   TextView counts=label("◉  "+t.views+" 조회     ♥  "+t.liked+" 좋아요",15);
   counts.setGravity(Gravity.CENTER);body.addView(counts);
   LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
   body.addView(actions,new LinearLayout.LayoutParams(-1,dp(79)));
   iconAction(actions,"↓","다운로드",()->download(t));
   iconAction(actions,"♬","악보 보기",()->score(id));
   iconAction(actions,"⇧","공유",()->shareSong(t));
   boolean saved=getPreferences(MODE_PRIVATE).getBoolean("favourite-"+t.sha,false);
   iconAction(actions,saved?"★":"☆","보관함 추가",()->{
     boolean next=!getPreferences(MODE_PRIVATE).getBoolean("favourite-"+t.sha,false);
     getPreferences(MODE_PRIVATE).edit().putBoolean("favourite-"+t.sha,next).apply();
     notice(next?"즐겨찾기에 추가했습니다.":"즐겨찾기를 해제했습니다.");
     dialog.dismiss();details(id);
   });
   LinearLayout tabs=new LinearLayout(this);tabs.setBackground(round(0xff1b2c44,0xff0e1d32,25));
   body.addView(tabs,new LinearLayout.LayoutParams(-1,dp(53)));
   LinearLayout content=vertical();body.addView(content,new LinearLayout.LayoutParams(-1,-2));
   String[] labels={"댓글","곡정보","관련 노래"};
   for(int i=0;i<labels.length;i++){
     final int index=i;
     TextView tab=label(labels[i],16);tab.setTextColor(i==0?0xff4bc3f7:white);
     tab.setGravity(Gravity.CENTER);
     tabs.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
     tab.setOnClickListener(v->showDetailTab(content,id,index));
   }
   showDetailTab(content,id,0);
   TextView manage=label("운영자 관리 · 장르/악보/표지/권한",13);
   manage.setTextColor(teal);manage.setGravity(Gravity.CENTER);body.addView(manage);
   manage.setOnClickListener(v->new AlertDialog.Builder(this).setItems(new String[]{
     "앨범 표지 첨부","악보 첨부/보기","장르 수정","제목 수정","업로드 권한 확인","내 좋아요"
   },(d,index)->{
      if(index==0)pickCover(id);
      else if(index==1)score(id);
      else if(index==2)chooseGenre(id);
      else if(index==3)rename(id);
      else if(index==4)toggleRights(id);
      else{db.flag(id,"liked",t.liked==1?0:1);dialog.dismiss();details(id);}
   }).show());
   dialog.setContentView(shell);
   if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
   dialog.show();
 }
 private void iconAction(LinearLayout row,String icon,String label,Runnable click){
   LinearLayout c=vertical();c.setGravity(Gravity.CENTER);
   row.addView(c,new LinearLayout.LayoutParams(0,-1,1));
   TextView image=bigLabel(icon,25);image.setGravity(Gravity.CENTER);
   image.setBackground(round(0xff253752,0xff15243a,16));
   c.addView(image,new LinearLayout.LayoutParams(dp(56),dp(42)));
   TextView t=label(label,12);t.setGravity(Gravity.CENTER);c.addView(t);
   c.setOnClickListener(v->click.run());
 }
 private void showDetailTab(LinearLayout host,long id,int tab){
   host.removeAllViews();
   VaultDb.Track track=db.get(id);if(track==null)return;
   if(tab==1){
     heading(host,"곡정보");
     paragraph(host,"장르: "+track.genre+" · 분류: "+track.language);
     paragraph(host,"음원: "+track.filename+" · "+(track.size/1048576)+"MB");
     paragraph(host,"조회수는 현재 이 기기에서 미리듣기한 횟수입니다.");
     return;
   }
   if(tab==2){
     heading(host,"관련 노래");
     int n=0;
     for(VaultDb.Track song:db.tracks(track.language)){
       if(song.id==id)continue;
       btn(host,song.title,()->details(song.id));if(++n>=5)break;
     }
     if(n==0)paragraph(host,"같은 분류의 다른 노래가 없습니다.");
     return;
   }
   heading(host,"댓글 ("+db.comments(id).size()+")");
   for(VaultDb.Comment c:db.songCommentDetails(id)){
     LinearLayout card=vertical();card.setPadding(dp(12),dp(9),dp(12),dp(7));
     card.setBackground(round(0xff172940,0xff0b1a2c,13));
     LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
     cp.setMargins(0,dp(3),0,dp(5));host.addView(card,cp);
     TextView author=bigLabel("✿  "+c.author,14);card.addView(author);
     card.addView(label(c.body,15));
     TextView answer=label("↳ 답글",13);answer.setTextColor(teal);card.addView(answer);
     answer.setOnClickListener(v->songReply(id,c.id));
     for(VaultDb.Comment reply:db.songReplies(id)){
       if(reply.parentId==c.id)card.addView(label("↳ "+reply.author+": "+reply.body,13));
     }
   }
   LinearLayout entry=new LinearLayout(this);entry.setGravity(Gravity.CENTER_VERTICAL);
   host.addView(entry,new LinearLayout.LayoutParams(-1,dp(69)));
   EditText input=new EditText(this);input.setSingleLine(true);input.setHint("댓글을 남겨주세요...");
   input.setTextColor(white);input.setHintTextColor(0xff9da7ba);input.setTextSize(14);
   entry.addView(input,new LinearLayout.LayoutParams(0,-2,1));
   TextView send=bigLabel("➤",25);send.setTextColor(0xff30aaf9);send.setGravity(Gravity.CENTER);
   entry.addView(send,new LinearLayout.LayoutParams(dp(50),dp(55)));
   send.setOnClickListener(v->{
     String typed=input.getText().toString().trim();
     if(typed.isEmpty()){notice("댓글을 입력하세요.");return;}
     db.addComment(id,"호스트",typed);
     showDetailTab(host,id,0);
   });
 }
  private void shareSong(VaultDb.Track t){
   Intent share=new Intent(Intent.ACTION_SEND);
   share.setType("text/plain");
   share.putExtra(Intent.EXTRA_TEXT,"MARU 노래 보관함: "+t.title+" (앱 내부 등록곡 · 원본 재배포 링크는 아님)");
   startActivity(Intent.createChooser(share,"곡 정보 공유"));
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
   for(VaultDb.Comment c:db.songCommentDetails(id)){
     panel.addView(label("• "+c.author+" : "+c.body,14));
     btn(panel,"↳ "+c.author+"님에게 답글",()->songReply(id,c.id));
     for(VaultDb.Comment reply:db.songReplies(id)){
       if(reply.parentId==c.id){
         panel.addView(label("   ↳ "+reply.author+": "+reply.body,13));
         btn(panel,"↳ "+reply.author+"님에게 답글",()->songReply(id,reply.id));
       }
     }
   }
   paragraph(panel,"기본 댓글과 답글은 이 휴대폰에만 저장되며, 외부 공개용 서버 댓글과 분리되어 있습니다.");
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
 private void songReply(long id,long parent){
   LinearLayout p=vertical();p.setPadding(dp(15),dp(8),dp(15),dp(8));
   EditText name=edit(p,"닉네임","호스트",1),body=edit(p,"답글 입력","",3);
   new AlertDialog.Builder(this).setTitle("노래 댓글 답글").setView(p).setPositiveButton("저장",(d,w)->{
     if(!body.getText().toString().trim().isEmpty())db.addSongReply(id,parent,name.getText().toString(),body.getText().toString());
     notice("답글을 이 기기에 저장했습니다.");
   }).setNegativeButton("취소",null).show();
 }
 private void pickCover(long id){
   coverTarget=id;
   Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
   i.addCategory(Intent.CATEGORY_OPENABLE);
   i.setType("image/*");
   i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
   try{startActivityForResult(i,PICK_COVER);}catch(Exception e){notice("표지 사진 선택 불가");}
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
   initPage(2);
   heading(main,"사연 받기 · 관리");
   paragraph(main,"현재는 호스트 휴대폰 안에서 사연을 접수·승인합니다. 외부 청취자 웹 접수는 서버 연결 후 활성화됩니다.");
   EditText nick=edit(main,"사연 보낸 사람 닉네임",storyNickDraft,1);
   EditText body=edit(main,"사연 내용",storyBodyDraft,4);
   btn(main,"📎 사진·영상·음성·악보 첨부 (선택)",()->{
     storyNickDraft=nick.getText().toString();storyBodyDraft=body.getText().toString();
     Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
     i.setType("*/*");
     i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp","audio/mpeg","audio/mp4","video/mp4","application/pdf"});
     i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
     startActivityForResult(i,PICK_STORY_FILE);
   });
   if(!storyAttachment.isEmpty())paragraph(main,"첨부 선택됨: "+storyAttachment);
   btn(main,"사연 접수",()->{
     if(body.getText().toString().trim().isEmpty()){notice("사연 내용을 입력하세요.");return;}
     db.addStory(nick.getText().toString(),body.getText().toString(),storyAttachment);
     storyAttachment="";storyNickDraft="";storyBodyDraft="";
     notice("사연이 검토대기로 저장되었습니다.");renderStories();
   });
   heading(main,"사연함 "+db.stories().size()+"건");
   for(VaultDb.Story s:db.stories()){
     LinearLayout tile=vertical();tile.setPadding(dp(10),dp(8),dp(10),dp(8));tile.setBackgroundColor(card);
     LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));main.addView(tile,lp);
     tile.addView(label(s.author+" · "+s.status+(s.scheduledAt.isEmpty()?"":" · "+s.scheduledAt),15));
     tile.addView(label(s.body.length()>100?s.body.substring(0,100)+"…":s.body,13));
     btn(tile,"접수 상세 · 승인/예약 준비",()->storyDetail(s));
   }
 }
 private void storyDetail(VaultDb.Story original){
   db.storyViewed(original.id);
   VaultDb.Story s=findStory(original.id);
   if(s==null)return;
   ScrollView scroll=new ScrollView(this);
   LinearLayout box=vertical();box.setPadding(dp(16),dp(12),dp(16),dp(12));scroll.addView(box);
   box.addView(label(s.author+"님의 사연",17));box.addView(label(s.body,14));
   paragraph(box,"상태: "+s.status+" · 이 기기 조회수 "+s.views+" · 좋아요 "+s.liked);
   if(!s.scheduledAt.isEmpty())paragraph(box,"방송 예약 메모: "+s.scheduledAt+" (자동 송출 아님)");
   btn(box,"👁 사연 미리보기",()->new AlertDialog.Builder(this).setTitle(s.author+"님의 사연")
      .setMessage(s.body).setPositiveButton("확인",null).show());
   if(s.attachment!=null&&!s.attachment.isEmpty())btn(box,"📎 첨부파일 미리보기",()->{
     Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(Uri.parse(s.attachment),"*/*");
     i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
     try{startActivity(i);}catch(Exception e){notice("첨부파일 열기 실패");}
   });
   btn(box,s.liked==1?"♥ 좋아요 취소":"♡ 좋아요",()->{
     db.storyLike(s.id,s.liked!=1);notice("사연 좋아요 변경 · 다시 열면 반영됩니다.");
   });
   btn(box,"💬 댓글·답글",()->storyComments(s.id));
   btn(box,"⬇ 사연 다운로드(TXT)",()->saveStory(s));
   btn(box,"🕒 방송 예약 메모",()->scheduleStory(s.id));
   btn(box,"✓ 사연 승인",()->{
     db.storyStatus(s.id,"승인");notice("사연 승인 완료 · 서버 공개나 방송은 별도입니다.");renderStories();
   });
   btn(box,"✕ 거절",()->{db.storyStatus(s.id,"거절");notice("사연 거절 처리");renderStories();});
   btn(box,"📻 방송완료 표시",()->{
     db.storyStatus(s.id,"방송완료");notice("방송완료 메모 변경 (실제 송출 검증 아님)");renderStories();
   });
   btn(box,"📁 보관",()->{db.storyStatus(s.id,"보관");notice("사연 보관 완료");renderStories();});
   btn(box,"↗ 대본 작가국으로 공유",()->{
     Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
     send.putExtra(Intent.EXTRA_TEXT,"MARU 사연\n"+s.author+"\n"+s.body);
     startActivity(Intent.createChooser(send,"대본 작가국으로 공유"));
   });
   new AlertDialog.Builder(this).setTitle("사연 상세 · 하위 기능").setView(scroll)
     .setPositiveButton("닫기",(d,w)->renderStories()).show();
 }
 private VaultDb.Story findStory(long id){
   for(VaultDb.Story s:db.stories())if(s.id==id)return s;return null;
 }
 private void storyComments(long id){
   LinearLayout panel=vertical();panel.setPadding(dp(15),dp(8),dp(15),dp(8));
   paragraph(panel,"댓글은 현재 이 기기에서만 보관합니다.");
   for(VaultDb.Comment c:db.storyComments(id)){
     btn(panel,(c.parentId>0?"↳ ":"• ")+c.author+": "+c.body,()->writeStoryComment(id,c.id));
   }
   btn(panel,"+ 새 댓글",()->writeStoryComment(id,0));
   new AlertDialog.Builder(this).setTitle("사연 댓글").setView(panel).setPositiveButton("닫기",null).show();
 }
 private void writeStoryComment(long id,long parent){
   LinearLayout panel=vertical();panel.setPadding(dp(15),dp(10),dp(15),dp(10));
   EditText name=edit(panel,"닉네임","호스트",1),body=edit(panel,"댓글 또는 답글","",3);
   new AlertDialog.Builder(this).setTitle(parent>0?"답글 작성":"댓글 작성").setView(panel)
     .setPositiveButton("저장",(d,w)->{
       if(!body.getText().toString().trim().isEmpty())db.addStoryComment(id,parent,name.getText().toString(),body.getText().toString());
       notice("댓글이 로컬 보관함에 저장되었습니다.");
     }).setNegativeButton("취소",null).show();
 }
 private void scheduleStory(long id){
   EditText input=new EditText(this);
   input.setSingleLine(true);input.setHint("2026-11-09 19:00");
   new AlertDialog.Builder(this).setTitle("방송 예약 메모 (자동 방송 아님)")
     .setView(input).setPositiveButton("예약 메모 저장",(d,w)->{
       String when=input.getText().toString().trim();
       if(!when.matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d")){
         notice("날짜 형식 오류: YYYY-MM-DD HH:MM");return;
       }
       db.scheduleStory(id,when);notice("예약 메모만 저장됨: "+when);
       renderStories();
     }).setNegativeButton("취소",null).show();
 }
 private void saveStory(VaultDb.Story s){
   pendingStoryExport="MARU RADIO 사연\n작성자: "+s.author+"\n상태: "+s.status+"\n\n"+s.body+"\n";
   Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
   i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"MARU_STORY_"+s.id+".txt");
   try{startActivityForResult(i,EXPORT_STORY);}catch(Exception e){notice("TXT 저장 화면을 열 수 없습니다.");}
 }
 private void renderUpload(){
   initPage(3);
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
           }else{failed++;error="서버가 업로드를 확인하지 않았습니다.";break;}
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
   initPage(4);
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
   }else if(request==EXPORT_STORY){
     if(data.getData()!=null){
       try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
         if(out==null)throw new IOException("파일을 열 수 없습니다.");
         out.write(pendingStoryExport.getBytes(StandardCharsets.UTF_8));
         out.flush();notice("사연 TXT 저장 완료");
       }catch(Exception e){notice("사연 저장 오류: "+e.getMessage());}
     }
   }else if(request==PICK_SCORE){
     if(data.getData()!=null&&scoreTarget>0){
       persistRead(data.getData());db.update(scoreTarget,"score_uri",data.getData().toString());
       notice("악보 등록 완료");scoreTarget=-1;
     }
   }else if(request==PICK_COVER){
     if(data.getData()!=null&&coverTarget>0){
       persistRead(data.getData());
       db.update(coverTarget,"cover_uri",data.getData().toString());
       notice("앨범 표지 사진 등록 완료");coverTarget=-1;
     }
   }else if(request==PICK_STORY_FILE){
     if(data.getData()!=null){
       if(!safeStoryAttachment(data.getData())){
         notice("첨부 파일은 JPG/PNG/WebP, MP3/M4A/MP4, PDF (최대 25MB)만 가능합니다.");
         renderStories();return;
       }
       persistRead(data.getData());storyAttachment=data.getData().toString();
       notice("사연 첨부파일 선택 · 서버 공개 전 악성코드 검사 필수");renderStories();
     }
   }
 }
 @Override protected void onPause(){super.onPause();stopPlayer();}
 @Override protected void onDestroy(){stopPlayer();adminToken="";if(db!=null)db.close();super.onDestroy();}
}