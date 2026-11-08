package com.maru.media;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.media.MediaPlayer;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Eight-screen MARU UI. Local-only WebView, backed by the existing private media database. */
public final class ShowcaseActivity extends Activity {
  private static final int IMPORT=701,FOLDER=702,MANIFEST=703,UPLOAD_FILE=704,
          COVER=705,SCORE=706,STORY_FILE=707,SAVE_AUDIO=708,OPEN_STORY_FILE=709,
          EXPORT_METADATA=710,RESTORE_METADATA=711,EXPORT_SCORE=712;
  private final Handler ui=new Handler(Looper.getMainLooper());
  private WebView web; private VaultDb db; private MediaPlayer player;
  private long uploadId=-1,selectedTrack=-1,saveTrack=-1,exportScoreTrack=-1;
  private Uri storyFile;
  private String endpoint="",token="";
  private String noticeAfterImport="";
  private final String AUDIO_MIME="audio/*";

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(0xff071426);
    getWindow().setNavigationBarColor(0xff061223);
    db=new VaultDb(this);
    endpoint=getPreferences(MODE_PRIVATE).getString("serverEndpoint","");
    web=new WebView(this);
    web.setBackgroundColor(0xff071426);
    web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
    WebView.setWebContentsDebuggingEnabled(false);
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(false);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(false);
    s.setAllowFileAccessFromFileURLs(false);
    s.setAllowUniversalAccessFromFileURLs(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setCacheMode(WebSettings.LOAD_NO_CACHE);
    s.setTextZoom(100); // Avoid unexpected OS font auto-zoom
    s.setBlockNetworkLoads(true); // Only local packaged UI may access the native bridge.
    web.addJavascriptInterface(new Bridge(),"Maru");
    web.setWebChromeClient(new WebChromeClient());
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return true;}
      @Override public boolean shouldOverrideUrlLoading(WebView v,android.webkit.WebResourceRequest request){return true;}
    });
    android.widget.FrameLayout host=new android.widget.FrameLayout(this);
    host.setBackgroundColor(0xff061527);
    host.addView(web,new ViewGroup.LayoutParams(-1,-1));
    if(android.os.Build.VERSION.SDK_INT>=35){
      getWindow().setDecorFitsSystemWindows(false);
      host.setOnApplyWindowInsetsListener((view,insets)->{
        android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.statusBars()
             |android.view.WindowInsets.Type.navigationBars());
        host.setPadding(0,bars.top,0,bars.bottom);
        return insets;
      });
    }else if(android.os.Build.VERSION.SDK_INT>=30){
      getWindow().setDecorFitsSystemWindows(true);
    }
    setContentView(host,new ViewGroup.LayoutParams(-1,-1));
    web.loadUrl("file:///android_asset/studio/index.html");
  }
  private void callback(String js){ui.post(()->{
    if(web!=null)web.evaluateJavascript(js,null);
  });}
  private void say(String msg){callback("window.notifyNative("+JSONObject.quote(msg)+");");}
  private void changed(){callback("window.syncNative&&window.syncNative();");}
  private void launchPicker(int req,String type,String[] multipleMime){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType(type);
    if(multipleMime!=null)i.putExtra(Intent.EXTRA_MIME_TYPES,multipleMime);
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,req==IMPORT);
    try{startActivityForResult(i,req);}catch(Exception e){say("휴대폰 파일 선택 화면을 열 수 없습니다.");}
  }
  private void persist(Uri uri){
    try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
    catch(Exception ignored){}
  }
  private void importFiles(List<Uri> files,boolean forUpload){
    say("음원 SHA-256 검사 중입니다. 중복 음원은 등록하지 않습니다.");
    new Thread(()->{
      Set<Long> originalIds=new HashSet<>();
      if(forUpload)for(VaultDb.Track item:db.tracks("전체"))originalIds.add(item.id);
      LibraryImporter worker=new LibraryImporter(getApplicationContext(),db,null);
      worker.fromAudioUris(files);
      LibraryImporter.Stats x=worker.stats();
      String status="검사 "+x.scanned+"곡 / 새 등록 "+x.inserted+"곡 / 중복 "+x.duplicate+"곡 / 오류 "+x.invalid+"곡";
      if(forUpload){
        VaultDb.Track firstAdded=null;
        for(VaultDb.Track item:db.tracks("전체")){
          if(!originalIds.contains(item.id)){firstAdded=item;break;}
        }
        if(firstAdded!=null){
          uploadId=firstAdded.id;
          callback("window.songChosen&&window.songChosen("+firstAdded.id+","+JSONObject.quote(firstAdded.title)+");");
        }else{
          uploadId=-1;
          say("새 음원이 등록되지 않았습니다. 중복 파일이라면 기존 보관함에서 확인하세요.");
        }
      }
      say(status);changed();
    },"maru-private-import").start();
  }
  private void saveAudio(long id,Uri dest){
    VaultDb.Track track=db.get(id);
    if(track==null){say("음원을 찾을 수 없습니다.");return;}
    new Thread(()->{
      try(InputStream in=getContentResolver().openInputStream(Uri.parse(track.uri));
          OutputStream out=getContentResolver().openOutputStream(dest)){
        if(in==null||out==null)throw new IOException("원곡 또는 저장 대상을 열 수 없습니다.");
        byte[] buf=new byte[65536];int n;
        while((n=in.read(buf))!=-1)out.write(buf,0,n);
        out.flush();
        getPreferences(MODE_PRIVATE).edit().putBoolean("downloaded-"+track.sha,true).apply();
        say("음원 파일이 휴대폰에 저장됐습니다.");changed();
      }catch(Exception e){say("음원 저장 실패: "+e.getMessage());}
    },"maru-save-audio").start();
  }
  private void selectSave(VaultDb.Track track){
    if(track==null)return;saveTrack=track.id;
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("audio/*");
    i.putExtra(Intent.EXTRA_TITLE,track.filename==null?"MARU-song.mp3":track.filename);
    try{startActivityForResult(i,SAVE_AUDIO);}catch(Exception e){say("저장 화면을 열 수 없습니다.");}
  }
  private boolean allowedStoryAttachment(Uri uri){
    String name="";String mime=getContentResolver().getType(uri);
    long size=0;
    try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
      if(c!=null&&c.moveToFirst()){
        if(!c.isNull(0))name=c.getString(0);
        if(!c.isNull(1))size=c.getLong(1);
      }
    }catch(Exception ignored){}
    String ext=name.toLowerCase(Locale.ROOT);
    boolean ok=ext.endsWith(".jpg")||ext.endsWith(".jpeg")||ext.endsWith(".png")||
      ext.endsWith(".webp")||ext.endsWith(".mp3")||ext.endsWith(".m4a")||
      ext.endsWith(".mp4")||ext.endsWith(".pdf");
    if(size>25L*1024*1024)return false;
    return ok&&(mime==null||mime.startsWith("image/")||mime.startsWith("audio/")||
       mime.equals("video/mp4")||mime.equals("application/pdf")||mime.equals("application/octet-stream"));
  }
  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if(result!=RESULT_OK||data==null)return;
    Uri uri=data.getData();
    if(req==EXPORT_METADATA&&uri!=null){
      new Thread(()->{
        try(OutputStream out=getContentResolver().openOutputStream(uri)){
          if(out==null)throw new IOException("백업 파일 저장 실패");
          byte[] contents=VaultMetadataArchive.exportData(db).getBytes(StandardCharsets.UTF_8);
          out.write(contents);out.flush();
          say("노래 목록·사연·댓글 백업 완료. 실제 음원·사진은 포함되지 않습니다.");
        }catch(Exception e){say("목록 백업 실패: "+e.getMessage());}
      },"maru-metadata-export").start();return;
    }
    if(req==RESTORE_METADATA&&uri!=null){
      new Thread(()->{
        try(InputStream in=getContentResolver().openInputStream(uri);
            ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
          if(in==null)throw new IOException("백업 파일 열기 실패");
          byte[] part=new byte[8192];int k;
          while((k=in.read(part))!=-1){
            buffer.write(part,0,k);
            if(buffer.size()>6_000_000)throw new IOException("백업 파일은 6MB 이하만 가능합니다");
          }
          VaultMetadataArchive.Result restoreResult=VaultMetadataArchive.importData(db,buffer.toString("UTF-8"));
          say("목록 복구 완료: "+restoreResult.toString());changed();
        }catch(Exception e){say("목록 복구 실패: "+e.getMessage());}
      },"maru-metadata-restore").start();return;
    }
    if(req==FOLDER&&uri!=null){
      persist(uri);say("폴더의 음원을 검사하고 있습니다...");
      new Thread(()->{
        LibraryImporter lib=new LibraryImporter(getApplicationContext(),db,null);
        try{lib.fromTree(uri);LibraryImporter.Stats x=lib.stats();
          say("폴더 검사 "+x.scanned+"곡, 등록 "+x.inserted+"곡, 중복 "+x.duplicate+"곡, 오류 "+x.invalid+"곡");
        }catch(Exception e){say("폴더 검사 실패: "+e.getMessage());}
        changed();
      },"maru-folder-import").start();return;
    }
    if(req==IMPORT||req==UPLOAD_FILE){
      List<Uri> files=new ArrayList<>();
      if(uri!=null){files.add(uri);persist(uri);}
      if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++){
        Uri item=data.getClipData().getItemAt(i).getUri();
        if(!files.contains(item)){files.add(item);persist(item);}
      }
      if(!files.isEmpty())importFiles(files,req==UPLOAD_FILE);return;
    }
    if(req==MANIFEST&&uri!=null){
      try(InputStream in=getContentResolver().openInputStream(uri);
          ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>350000)throw new IOException("JSON 파일 350KB 제한");}
        int count=db.importManifest(out.toString(StandardCharsets.UTF_8.name()));
        say("107곡 분류표를 적용한 곡: "+count+"곡");changed();
      }catch(Exception e){say("분류표 적용 오류: "+e.getMessage());}
      return;
    }
    if(req==SCORE&&uri!=null&&selectedTrack>0){
      final long songId=selectedTrack;
      persist(uri);
      new Thread(()->{
        try(InputStream in=getContentResolver().openInputStream(uri)){
          String mime=ScoreFileValidator.validate(in);
          if(db.get(songId)==null)throw new IOException("선택한 노래를 찾지 못했습니다.");
          db.update(songId,"score_uri",uri.toString());
          say("악보 연결 완료 · "+mime+" · 다른 노래나 사연은 변경하지 않았습니다.");
          changed();
        }catch(Exception ex){say("악보 등록 거부: "+ex.getMessage());}
      },"maru-score-validate").start();return;
    }
    if(req==COVER&&uri!=null&&selectedTrack>0){
      persist(uri);db.update(selectedTrack,"cover_uri",uri.toString());
      say("앨범 표지를 저장했습니다.");changed();return;
    }
    if(req==STORY_FILE&&uri!=null){
      if(!allowedStoryAttachment(uri)){say("25MB 이하 사진·음성·MP4·PDF만 첨부할 수 있습니다.");return;}
      persist(uri);storyFile=uri;
      callback("window.storyFileChosen&&window.storyFileChosen("+JSONObject.quote(uri.getLastPathSegment()==null?"첨부파일":uri.getLastPathSegment())+");");
      say("첨부파일 선택됨. 외부 공개 전 서버 보안 검사가 필요합니다.");return;
    }
    if(req==EXPORT_SCORE&&uri!=null&&exportScoreTrack>0){
      long current=exportScoreTrack;exportScoreTrack=-1;
      new Thread(()->copyScoreOut(current,uri),"maru-score-export").start();return;
    }
    if(req==SAVE_AUDIO&&uri!=null&&saveTrack>0)saveAudio(saveTrack,uri);
  }
  private String scoreMime(String link)throws IOException{
    try(InputStream in=getContentResolver().openInputStream(Uri.parse(link))){
      if(in==null)throw new IOException("악보 파일 접근 권한이 없습니다.");
      String mime=ScoreFileValidator.validate(in);
      return mime;
    }
  }
  private void copyScoreOut(long trackId,Uri dest){
    VaultDb.Track t=db.get(trackId);
    if(t==null||t.scoreUri==null||t.scoreUri.isEmpty()){say("연결된 악보가 없습니다.");return;}
    try(InputStream in=getContentResolver().openInputStream(Uri.parse(t.scoreUri));
        OutputStream out=getContentResolver().openOutputStream(dest)){
      if(in==null||out==null)throw new IOException("파일을 열 수 없습니다.");
      byte[] buffer=new byte[65536];int n;long bytes=0;
      while((n=in.read(buffer))!=-1){
        bytes+=n;if(bytes>ScoreFileValidator.MAX_BYTES)throw new IOException("악보 40MB 제한 초과");
        out.write(buffer,0,n);
      }
      out.flush();say("악보 파일 저장 완료 · "+t.title);
    }catch(Exception ex){say("악보 저장 실패: "+ex.getMessage());}
  }
  private JSONObject trackJSON(VaultDb.Track t) throws Exception {
    JSONObject j=new JSONObject();
    j.put("id",t.id);j.put("title",t.title);j.put("artist",t.artist);j.put("filename",t.filename);
     j.put("lyricist",t.lyricist);j.put("composer",t.composer);
     j.put("styleFinisher",t.styleFinisher);j.put("sunoAssist",t.sunoAssist);
     j.put("isOriginal",t.isOriginal==1);j.put("artistGroupAuto",t.artistGroupAuto==1);
     j.put("suggestedArtist",SingerGroupRules.suggest(t.language,t.genre,t.title,t.filename));
    j.put("language",t.language);j.put("genre",t.genre);j.put("size",t.size);j.put("sha",t.sha);
    j.put("views",t.views);j.put("liked",t.liked);j.put("uploadState",t.uploadState);
    j.put("hasScore",t.scoreUri!=null&&!t.scoreUri.isEmpty());
    j.put("hasCover",t.coverUri!=null&&!t.coverUri.isEmpty());
    j.put("downloaded",getPreferences(MODE_PRIVATE).getBoolean("downloaded-"+t.sha,false));
    j.put("favorite",getPreferences(MODE_PRIVATE).getBoolean("favorite-"+t.sha,false));
    return j;
  }
  private JSONArray listComments(String kind,long id)throws Exception {
    JSONArray out=new JSONArray();
    List<VaultDb.Comment> direct="story".equals(kind)?db.storyComments(id):db.songCommentDetails(id);
    List<VaultDb.Comment> replies="story".equals(kind)?Collections.emptyList():db.songReplies(id);
    for(VaultDb.Comment c:direct){
      JSONObject j=new JSONObject().put("id",c.id).put("parent",c.parentId)
        .put("author",c.author).put("body",c.body);out.put(j);
    }
    for(VaultDb.Comment c:replies){
      JSONObject j=new JSONObject().put("id",c.id).put("parent",c.parentId)
        .put("author",c.author).put("body",c.body);out.put(j);
    }
    return out;
  }
  private JSONObject storyJSON(VaultDb.Story s)throws Exception {
    JSONObject j=new JSONObject();
    j.put("id",s.id).put("author",s.author).put("body",s.body).put("status",s.status);
    j.put("views",s.views).put("liked",s.liked).put("scheduledAt",s.scheduledAt);
    j.put("hasAttachment",s.attachment!=null&&!s.attachment.isEmpty());
    return j;
  }
  private String dataCover(long id){
    VaultDb.Track track=db.get(id);
    if(track==null)return "";
    byte[] bytes=null;
    if(track.coverUri!=null&&!track.coverUri.isEmpty()){
      try(InputStream in=getContentResolver().openInputStream(Uri.parse(track.coverUri))){
        if(in!=null){
          ByteArrayOutputStream output=new ByteArrayOutputStream();
          byte[] buf=new byte[8192];int size=0,n;
          while((n=in.read(buf))!=-1&&size<4_000_000){size+=n;output.write(buf,0,n);}
          if(size<=4_000_000)bytes=output.toByteArray();
        }
      }catch(Exception ignored){}
    }
    if(bytes==null){
      android.media.MediaMetadataRetriever retriever=new android.media.MediaMetadataRetriever();
      try{
        retriever.setDataSource(this,Uri.parse(track.uri));
        bytes=retriever.getEmbeddedPicture();
      }catch(Exception ignored){}
      finally{try{retriever.release();}catch(Exception ignored){}}
    }
    if(bytes==null||bytes.length==0)return "";
    try{
      BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
      BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
      int maxSide=Math.max(bounds.outWidth,bounds.outHeight);
      if(maxSide<=0)return "";
      BitmapFactory.Options o=new BitmapFactory.Options();
      o.inSampleSize=Math.max(1,Integer.highestOneBit(Math.max(1,maxSide/400)));
      Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
      if(bitmap==null)return "";
      int w=bitmap.getWidth(),h=bitmap.getHeight();
      float scale=Math.min(240f/w,180f/h);
      Bitmap resized=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(w*scale)),
         Math.max(1,(int)(h*scale)),true);
      if(resized!=bitmap)bitmap.recycle();
      ByteArrayOutputStream out=new ByteArrayOutputStream();
      resized.compress(Bitmap.CompressFormat.JPEG,75,out);resized.recycle();
      return "data:image/jpeg;base64,"+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);
    }catch(Exception ignored){return "";}
  }
  @Override public void onBackPressed(){
    callback("window.goBack&&window.goBack();");
  }
  @Override protected void onDestroy(){
    if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}
    if(web!=null){web.removeJavascriptInterface("Maru");web.destroy();web=null;}
    if(db!=null)db.close();
    token="";
    super.onDestroy();
  }
  private final class Bridge {
    @JavascriptInterface public String tracks(){
      try{JSONArray a=new JSONArray();for(VaultDb.Track t:db.tracks("전체"))a.put(trackJSON(t));return a.toString();}
      catch(Exception e){return "[]";}
    }
    @JavascriptInterface public String stories(){
      try{JSONArray a=new JSONArray();for(VaultDb.Story s:db.stories())a.put(storyJSON(s));return a.toString();}
      catch(Exception e){return "[]";}
    }
    @JavascriptInterface public String comments(String kind,long id){
      try{return listComments(kind,id).toString();}catch(Exception e){return "[]";}
    }
    @JavascriptInterface public String songCover(long id){return dataCover(id);}
    @JavascriptInterface public void identifyArtists(){
      say("실제 원곡의 ID3 가수·곡명 정보를 확인합니다.");
      new Thread(()->{
        int artists=0,titles=0,missing=0;
        for(VaultDb.Track track:db.tracks("전체")){
          if(track.uri==null||track.uri.isEmpty())continue;
          android.media.MediaMetadataRetriever tag=new android.media.MediaMetadataRetriever();
          try{
            tag.setDataSource(ShowcaseActivity.this,Uri.parse(track.uri));
            String artist=tag.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if(artist==null||artist.trim().isEmpty())
              artist=tag.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            String title=tag.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
            if(track.artist.trim().isEmpty()&&artist!=null&&!artist.trim().isEmpty()){
              db.update(track.id,"artist",artist.trim());artists++;
            }else if(track.artist.trim().isEmpty())missing++;
            if((track.title.trim().equalsIgnoreCase(CatalogLogic.base(track.filename))||track.title.trim().isEmpty())
               &&title!=null&&!title.trim().isEmpty()&&!title.trim().equalsIgnoreCase(track.title)){
              db.update(track.id,"title",title.trim());titles++;
            }
          }catch(Exception ignored){}
          finally{try{tag.release();}catch(Exception ignored){}}
        }
        changed();say("원곡 태그 검사 완료: 가수 "+artists+"곡, 제목 "+titles+"곡 반영 · 가수 정보 없는 곡 "+missing+"곡");
      },"maru-track-metadata").start();
    }
    /** Opens only an official Suno song page. Never logs in, scrapes or auto-downloads. */
    /** Candidate ratings persist in the existing private on-device database. */
    @JavascriptInterface public String rateCandidate(int idx,int version,int stars){
      try{
        String status=db.rateCandidate(idx,version,stars);
        changed();return status;
      }catch(Exception e){return "버전 평가 저장 실패. 평점을 1~5점으로 입력하세요.";}
    }
    @JavascriptInterface public String candidateReviews(){
      return db.candidateReviews().toString();
    }
    @JavascriptInterface public void openSuno(String songId){
      if(songId==null||!songId.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")){
        say("Suno 노래 주소가 올바르지 않습니다.");return;
      }
      ui.post(()->{
        Intent browser=new Intent(Intent.ACTION_VIEW,
          Uri.parse("https://suno.com/song/"+songId));
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        try{startActivity(browser);}
        catch(Exception ex){say("Suno 페이지를 열 수 없습니다. 브라우저를 확인하세요.");}
      });
    }
    @JavascriptInterface public void getFiles(){ui.post(()->launchPicker(IMPORT,"*/*",new String[]{"audio/mpeg","audio/mp4","application/zip","application/x-zip-compressed"}));}
    @JavascriptInterface public void getFolder(){ui.post(()->{
      Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
      try{startActivityForResult(i,FOLDER);}catch(Exception e){say("폴더 선택을 열 수 없습니다.");}
    });}
    @JavascriptInterface public void getManifest(){ui.post(()->launchPicker(MANIFEST,"application/json",null));}
    @JavascriptInterface public void exportMetadata(){ui.post(()->{
      Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
      i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
      i.putExtra(Intent.EXTRA_TITLE,"MARU_노래사연_목록백업.json");
      try{startActivityForResult(i,EXPORT_METADATA);}catch(Exception e){say("목록 백업 저장 화면을 열 수 없습니다.");}
    });}
    @JavascriptInterface public void restoreMetadata(){ui.post(()->launchPicker(RESTORE_METADATA,"application/json",null));}
    @JavascriptInterface public void chooseSong(){ui.post(()->launchPicker(UPLOAD_FILE,"*/*",new String[]{"audio/mpeg","audio/mp4","application/zip"}));}
    @JavascriptInterface public void pickArt(long id){selectedTrack=id;ui.post(()->launchPicker(COVER,"image/*",null));}
    @JavascriptInterface public void pickScore(long id){
      if(db.get(id)==null){say("악보와 연결할 노래를 먼저 등록하세요.");return;}
      selectedTrack=id;
      ui.post(()->launchPicker(SCORE,"*/*",
        new String[]{"application/pdf","image/jpeg","image/png","image/webp"}));
    }
    @JavascriptInterface public void exportScore(long id){
      VaultDb.Track t=db.get(id);
      if(t==null||t.scoreUri==null||t.scoreUri.isEmpty()){say("저장할 악보가 없습니다.");return;}
      ui.post(()->{
        try{
          String mime=scoreMime(t.scoreUri);
          String extension="application/pdf".equals(mime)?".pdf":"image/jpeg".equals(mime)?".jpg":
                "image/png".equals(mime)?".png":".webp";
          exportScoreTrack=id;
          String name=t.title.replaceAll("[\\\\/:*?\\\"<>|\\p{Cntrl}]","_");
          if(name.length()>80)name=name.substring(0,80);
          Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
          save.addCategory(Intent.CATEGORY_OPENABLE);
          save.setType(mime);
          save.putExtra(Intent.EXTRA_TITLE,name+"_악보"+extension);
          startActivityForResult(save,EXPORT_SCORE);
        }catch(Exception ex){say("악보 파일 형식을 확인할 수 없습니다.");}
      });
    }
    @JavascriptInterface public void pickStoryFile(){ui.post(()->launchPicker(STORY_FILE,"*/*",new String[]{"image/jpeg","image/png","image/webp","audio/mpeg","audio/mp4","video/mp4","application/pdf"}));}
    @JavascriptInterface public String submitSong(long id,String title,String artist,String genre,String lang,boolean rights){
      VaultDb.Track t=db.get(id);if(t==null)return "음원을 먼저 선택해 주세요.";
      if(title==null||title.trim().isEmpty())return "노래 제목을 입력해 주세요.";
      db.update(id,"title",title);db.update(id,"artist",artist==null?"":artist);db.flag(id,"artist_group_auto",0);
      db.update(id,"genre",genre);db.update(id,"language",lang);db.flag(id,"rights_ok",rights?1:0);
      changed();return "음원을 내 보관함에 등록했습니다. 서버 공개는 별도 승인과 연결이 필요합니다.";
    }
    @JavascriptInterface public String editTrack(long id,String title,String artist,String genre,String language){
      VaultDb.Track song=db.get(id);
      if(song==null)return "수정할 노래가 없습니다.";
      if(title==null||title.trim().isEmpty())return "곡 제목을 입력하세요.";
      db.update(id,"title",title);
      db.update(id,"artist",artist==null?"":artist);
      db.flag(id,"artist_group_auto",0);
      db.update(id,"genre",genre==null?"미확인":genre);
      if(language!=null&&java.util.Arrays.asList(CatalogLogic.LANGUAGES).contains(language))
        db.update(id,"language",language);
      changed();return "노래 정보를 저장했습니다.";
    }

    @JavascriptInterface public String setSongCredits(long id,boolean own){
      if(db.get(id)==null)return "곡을 찾지 못했습니다.";
      db.markOriginal(id,own);changed();
      return own?"작사·작곡·스타일 완성: 박영환 / 제작 도움: Suno AI":"자작곡 크레딧 해제됨";
    }
    @JavascriptInterface public String setAllSongCredits(){
      int updated=db.markAllOriginals();changed();return "자작곡 제작 표기를 적용한 곡: "+updated+"곡";
    }
    @JavascriptInterface public String fillSingerGroups(){
      int updated=db.fillMissingArtists();changed();return "가수명이 비어 있던 "+updated+"곡에 추천 그룹을 적용했습니다.";
    }
    @JavascriptInterface public String chooseSingerGroup(long id,String group){
      if(db.get(id)==null)return "곡을 찾지 못했습니다.";
      if(!SingerGroupRules.isGroup(group))return "허용된 그룹을 선택하세요.";
      db.setSingerGroup(id,group);changed();return "가수 그룹: "+group+" · 임시 분류로 저장";
    }
    @JavascriptInterface public String submitStory(String nick,String body,String request,String privacy){
      if(nick==null||nick.trim().length()<2)return "닉네임을 2자 이상 입력해 주세요.";
      if(body==null||body.trim().length()<5)return "사연을 5자 이상 작성해 주세요.";
      String full=body.trim();
      if(request!=null&&!request.trim().isEmpty())full+="\n[신청곡] "+request.trim();
      if("private".equals(privacy))full+="\n[공개범위: 비공개]";
      long id=db.addStory(nick,full,storyFile==null?"":storyFile.toString());
      storyFile=null;changed();
      return id>0?"사연 접수번호 #"+id+" · 기기 내부 검토대기로 저장했습니다. 청취자 서버 접수는 아직 연결되지 않았습니다.":"사연 저장 실패";
    }
    @JavascriptInterface public String comment(String kind,long id,long parent,String author,String body){
      if(body==null||body.trim().length()<2)return "댓글을 두 글자 이상 작성해 주세요.";
      if("story".equals(kind))db.addStoryComment(id,parent,author,body);
      else if(parent>0)db.addSongReply(id,parent,author,body);
      else db.addComment(id,author,body);
      changed();return "댓글을 기기에 저장했습니다.";
    }
    @JavascriptInterface public String like(String kind,long id){
      if("story".equals(kind)){for(VaultDb.Story s:db.stories())if(s.id==id){db.storyLike(id,s.liked!=1);break;}}
      else {VaultDb.Track t=db.get(id);if(t!=null)db.flag(id,"liked",t.liked==1?0:1);}
      changed();return "좋아요 상태 변경";
    }
    @JavascriptInterface public void viewed(String kind,long id){if("story".equals(kind))db.storyViewed(id);else db.view(id);}
    @JavascriptInterface public void download(long id){VaultDb.Track t=db.get(id);ui.post(()->selectSave(t));}
    @JavascriptInterface public void preview(long id){
      VaultDb.Track t=db.get(id);if(t==null)return;
      ui.post(()->{
        try{
          if(player!=null){player.release();player=null;}
          player=new MediaPlayer();player.setDataSource(ShowcaseActivity.this,Uri.parse(t.uri));
          player.setOnPreparedListener(p->{p.start();db.view(id);say("♪ 재생 중: "+t.title);changed();});
          player.setOnErrorListener((p,what,extra)->{say("재생할 수 없는 원곡입니다.");return true;});
          player.prepareAsync();
        }catch(Exception e){say("파일 재생 실패: "+e.getMessage());}
      });
    }
    @JavascriptInterface public void stop(){ui.post(()->{if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}say("재생 정지");});}
    @JavascriptInterface public String favorite(long id){
      VaultDb.Track t=db.get(id);if(t==null)return "곡을 찾을 수 없습니다.";
      boolean yes=!getPreferences(MODE_PRIVATE).getBoolean("favorite-"+t.sha,false);
      getPreferences(MODE_PRIVATE).edit().putBoolean("favorite-"+t.sha,yes).apply();
      changed();return yes?"내 보관함에 추가했어요.":"즐겨찾기를 해제했어요.";
    }
    @JavascriptInterface public String storyStatus(long id,String status){
      try{db.storyStatus(id,status);changed();return "사연 상태: "+status;}
      catch(Exception e){return "상태 변경 실패";}
    }
    @JavascriptInterface public String schedule(long id,String time){
      if(time==null||!time.matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d"))return "YYYY-MM-DD HH:MM 형식으로 입력해 주세요.";
      db.scheduleStory(id,time);changed();return "방송 예약 메모만 저장했습니다. 실제 자동 송출은 연결되지 않았습니다.";
    }
    @JavascriptInterface public String serverInfo(){
      return endpoint==null?"":endpoint;
    }
    @JavascriptInterface public String setServer(String address,String secret){
      try{
        java.net.URI uri=java.net.URI.create(address);
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)
          return "HTTPS 서버 주소만 입력해 주세요.";
        endpoint=address.replaceAll("/+$","");
        token=secret==null?"":secret.trim();
        getPreferences(MODE_PRIVATE).edit().putString("serverEndpoint",endpoint).apply();
        return "서버 주소 저장. 인증키는 앱을 종료하면 사라집니다.";
      }catch(Exception e){return "서버 주소를 확인하세요.";}
    }
    @JavascriptInterface public void uploadToServer(long id){
      VaultDb.Track t=db.get(id);
      if(t==null){say("등록된 음원을 선택하세요.");return;}
      if(t.rightsOk!=1){say("원곡 소유/방송 업로드 권한을 먼저 확인하세요.");return;}
      if(endpoint==null||endpoint.isEmpty()||token.length()<10){say("내정보에서 HTTPS 서버 주소와 인증키를 먼저 연결하세요.");return;}
      say("승인한 원곡을 HTTPS 서버로 업로드하는 중...");
      new Thread(()->{
        try{if(UploadClient.upload(getApplicationContext(),t,endpoint,token)){
          db.update(id,"upload_state","UPLOADED");
          say("서버가 파일 전송을 수락했습니다. 공개 여부는 운영자 승인 후 결정됩니다.");changed();
        }}catch(Exception e){say("서버 전송 실패: "+e.getMessage());}
      },"maru-cloud-upload").start();
    }
    @JavascriptInterface public String deviceInfo(){
      JSONObject j=new JSONObject();
      try{
        File root=getFilesDir();
        j.put("used",root.getTotalSpace()-root.getFreeSpace());
        j.put("total",root.getTotalSpace());
        j.put("songCount",db.count());
        j.put("uploadedCount",db.uploaded());
        j.put("storyCount",db.stories().size());
        j.put("appVersion","1.1.6 악보 관리자");
      }catch(Exception ignored){}
      return j.toString();
    }
    @JavascriptInterface public void shareSong(long id){VaultDb.Track t=db.get(id);if(t==null)return;ui.post(()->{
      Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");
      i.putExtra(Intent.EXTRA_TEXT,"MARU RADIO · "+t.title+" / "+t.artist);
      startActivity(Intent.createChooser(i,"노래 정보 공유"));
    });}
    @JavascriptInterface public void viewScore(long id){
      VaultDb.Track t=db.get(id);
      if(t==null||t.scoreUri==null||t.scoreUri.isEmpty()){say("등록된 악보가 없습니다. 먼저 악보를 첨부하세요.");return;}
      new Thread(()->{
        try{
          String mime=scoreMime(t.scoreUri);
          ui.post(()->{
            Intent show=new Intent(Intent.ACTION_VIEW);
            show.setDataAndType(Uri.parse(t.scoreUri),mime);
            show.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try{startActivity(Intent.createChooser(show,"악보 열기"));}
            catch(Exception e){say("악보를 볼 PDF·이미지 앱이 없습니다.");}
          });
        }catch(Exception ex){say("악보 보기 실패: "+ex.getMessage());}
      },"maru-score-view").start();
    }
    @JavascriptInterface public void logout(){
      token="";say("임시 서버 인증키를 비웠습니다. 이 앱은 아직 자체 로그인 기능이 없습니다.");
    }
  }
}
