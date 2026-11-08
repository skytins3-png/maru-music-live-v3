package com.maru.media;
import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.WebView;
import android.view.ViewGroup;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ShowcaseEightScreensTest {
 @Rule public ActivityScenarioRule<ShowcaseActivity> rule=new ActivityScenarioRule<>(ShowcaseActivity.class);
 private String eval(String js)throws Exception{
   AtomicReference<WebView> ref=new AtomicReference<>();
   rule.getScenario().onActivity(a->{
     ViewGroup root=a.findViewById(android.R.id.content);
     android.view.View first=root.getChildAt(0);
     if(first instanceof WebView)ref.set((WebView)first);
     else if(first instanceof ViewGroup)ref.set((WebView)((ViewGroup)first).getChildAt(0));
   });
   WebView web=ref.get();assertNotNull(web);
   CountDownLatch latch=new CountDownLatch(1);
   AtomicReference<String> result=new AtomicReference<>("");
   web.post(()->web.evaluateJavascript(js,value->{result.set(value);latch.countDown();}));
   assertTrue("JS response must return",latch.await(15,TimeUnit.SECONDS));
   return result.get();
 }
 private void visualCommitted()throws Exception{
   AtomicReference<WebView> webRef=new AtomicReference<>();
   rule.getScenario().onActivity(a->{
     ViewGroup root=a.findViewById(android.R.id.content);
     android.view.View first=root.getChildAt(0);
     if(first instanceof WebView)webRef.set((WebView)first);
     else if(first instanceof ViewGroup)webRef.set((WebView)((ViewGroup)first).getChildAt(0));
   });
   CountDownLatch painted=new CountDownLatch(1);
   WebView web=webRef.get();
   assertNotNull(web);
   web.post(()->web.postVisualStateCallback(System.nanoTime(),new WebView.VisualStateCallback(){
     @Override public void onComplete(long requestId){painted.countDown();}
   }));
   assertTrue("WebView compositor failed to draw changed screen",painted.await(12,TimeUnit.SECONDS));
   Thread.sleep(380);
 }
 private void screenshot(String name)throws Exception{
   visualCommitted();
   Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
   File dir=new File(context.getFilesDir(),"eight-screens");
   assertTrue(dir.exists()||dir.mkdirs());
   AtomicReference<android.app.Activity> activity=new AtomicReference<>();
   rule.getScenario().onActivity(a->activity.set(a));
   android.app.Activity current=activity.get();
   assertNotNull(current);
   ViewGroup window=current.findViewById(android.R.id.content);
   int width=window.getWidth(),height=window.getHeight();
   assertTrue("Activity view must be measurable",width>200&&height>400);
   Bitmap shot=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
   CountDownLatch done=new CountDownLatch(1);
   AtomicReference<Integer> result=new AtomicReference<>(-1);
   android.view.PixelCopy.request(current.getWindow(),shot,status->{result.set(status);done.countDown();},
       new android.os.Handler(android.os.Looper.getMainLooper()));
   assertTrue("PixelCopy callback must finish",done.await(15,TimeUnit.SECONDS));
   assertEquals("PixelCopy must get real app pixels",android.view.PixelCopy.SUCCESS,(int)result.get());
   {
     File rawFile=new File(dir,name+".png");
     try(FileOutputStream out=new FileOutputStream(rawFile)){
       assertTrue(shot.compress(Bitmap.CompressFormat.PNG,100,out));
     }
   }
   if("01-home".equals(name)){
     int colorfulPixels=0;
     for(int yy=shot.getHeight()/6;yy<shot.getHeight()*4/5;yy+=Math.max(1,shot.getHeight()/28)){
       for(int xx=shot.getWidth()/9;xx<shot.getWidth()*8/9;xx+=Math.max(1,shot.getWidth()/22)){
         int color=shot.getPixel(xx,yy);
         int red=android.graphics.Color.red(color),green=android.graphics.Color.green(color),blue=android.graphics.Color.blue(color);
         if((red>135&&red>green*1.2&&blue>55)||(blue>145&&blue>red*1.25&&green>45))colorfulPixels++;
       }
     }
     assertTrue("App home screenshot has no colored menu tiles: "+colorfulPixels,colorfulPixels>=9);
   }
   shot.recycle();
 }
 private void ready() throws Exception{
   String status="";
   for(int i=0;i<100;i++){
     status=eval("document.querySelectorAll('.screen').length+':'+document.querySelectorAll('#bottom-nav button').length+':'+Math.round(document.querySelector('.phone')?.getBoundingClientRect().height||0)");
     String[] p=status.replace("\"","").split(":");
     if(p.length==3&&"10".equals(p[0])&&"4".equals(p[1])&&Integer.parseInt(p[2])>500){
       // The photo asset is decoded asynchronously by WebView; DOM navigation alone is insufficient.
       for(int w=0;w<40;w++){
         if("true".equals(eval("document.querySelector('.exact-home img')?.complete && document.querySelector('.exact-home img')?.naturalWidth>150")))break;
         Thread.sleep(120);
       }
       Thread.sleep(750);
       return;
     }
     Thread.sleep(200);
   }
   fail("Visible WebView UI failed to load on emulator: "+status);
 }
  private void show(String target)throws Exception{
   String result=eval("window.go("+org.json.JSONObject.quote(target)+");document.querySelector('#page-"+target+"').classList.contains('active')");
   assertEquals("true",result);
   visualCommitted();
 }
 @Test public void allEightScreensHaveActiveUIAndAreCaptured()throws Exception{
   ready();
   String loaded=eval("document.querySelectorAll('.screen').length");
   assertTrue(Integer.parseInt(loaded)>=10);
   assertEquals("4",eval("document.querySelectorAll('#bottom-nav button').length"));
   screenshot("01-home");
   for(String page:new String[]{"songs","upload","downloads","scores","story-compose","stories","profile"}){
     show(page);
     String content=eval("document.querySelector('#page-"+page+"').innerText.length");
     assertTrue(page+" content must be real text",Integer.parseInt(content)>35);
     String filename=page.equals("songs")?"02-songs":page.equals("upload")?"04-upload":page.equals("downloads")?"05-downloads":page.equals("scores")?"10-scores":
       page.equals("story-compose")?"06-story-compose":page.equals("stories")?"07-stories":"09-profile";
     screenshot(filename);
   }
   // Demo data is clearly marked. It never writes fake statistics to local DB.
   assertEquals("true",eval("window.setDemo(true);window.go('songs');document.querySelector('.demo-flag')!==null"));
   assertEquals("true",eval("window.go('detail',-1);document.querySelector('#page-detail').classList.contains('active') && document.querySelector('#page-detail').innerText.includes('그리운 사람')"));
   screenshot("03-song-detail");
   assertEquals("true",eval("window.go('story-detail',-1);document.querySelector('#page-story-detail').classList.contains('active') && document.querySelector('#page-story-detail').innerText.includes('바람소리')"));
   screenshot("08-story-detail");
 }
 @Test public void candidateVersionSelectionSurvivesAppDatabaseReload()throws Exception{
   ready();
   assertEquals("true",eval("typeof Maru.rateCandidate==='function'&&typeof Maru.candidateReviews==='function'"));
   assertEquals("true",eval("Maru.rateCandidate(0,1,3).length>0"));
   assertEquals("true",eval("Maru.rateCandidate(0,2,5).includes('2번')"));
   assertEquals("true",eval("JSON.parse(Maru.candidateReviews())['0'].winner===2"));
   // A tie never silently selects a random version.
   assertEquals("true",eval("Maru.rateCandidate(0,1,5).includes('동점')"));
   assertEquals("true",eval("JSON.parse(Maru.candidateReviews())['0'].winner===0"));
   assertEquals("true",eval("Maru.rateCandidate(0,1,4).includes('2번')"));
   assertEquals("true",eval("JSON.parse(Maru.candidateReviews())['0'].winner===2"));
   // Re-open same SQLite database from another helper instance.
   Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
   VaultDb reopened=new VaultDb(ctx);
   assertEquals(2,reopened.candidateReviews().getJSONObject("0").getInt("winner"));
   reopened.close();
   // The UI must communicate exclusion and must not represent a Suno account scrape.
   assertEquals("true",eval("downloadTab='Suno 후보 9곡';window.go('downloads');document.querySelector('#page-downloads').innerText.includes('추모곡')"));
   assertEquals("true",eval("document.querySelectorAll('#page-downloads [data-action=\"rateSuno\"]').length===18"));
 }
 @Test public void scoreCatalogHasRealLinksOnly()throws Exception{
  ready();
  assertEquals("true",eval("window.go('scores');document.querySelector('#page-scores').innerText.includes('악보 관리')"));
  assertEquals("true",eval("document.querySelector('#page-scores').innerText.includes('MP3만으로 악보를 자동 채보')"));
  assertEquals("true",eval("typeof Maru.pickScore==='function' && typeof Maru.viewScore==='function' && typeof Maru.exportScore==='function'"));
  assertEquals("true",eval("window.go('home');document.querySelector('#page-home [data-go=\"scores\"]')!==null"));
 }
 @Test public void downloadListOffersRealPreviewAndHonestScoreActions()throws Exception{
   ready();
   final String sha="abababababababababababababababababababababababababababababababab";
   rule.getScenario().onActivity(a->{
     VaultDb db=new VaultDb(a);
     if(!db.bySha(sha))db.insertTrack("QA 다운로드 미리듣기","qa-preview.wav",
       "file:///missing/qa-preview.wav",sha,2048,"한국노래");
     db.close();
   });
   assertEquals("true",eval("window.syncNative();window.go('downloads');document.querySelectorAll('#page-downloads [data-action=\"preview\"]').length>=1"));
   assertEquals("true",eval("document.querySelector('#page-downloads [data-action=\"togglePlayback\"]')!==null"));
   assertEquals("true",eval("document.querySelector('#page-downloads [data-action=\"stop\"]')!==null"));
   assertEquals("true",eval("document.querySelector('#page-downloads [data-action=\"scoreAttach\"]')!==null"));
   assertEquals("true",eval("document.querySelector('#page-downloads').innerText.includes('악보 없음')"));
   assertEquals("true",eval("typeof Maru.preview==='function'&&typeof Maru.togglePlayback==='function'&&typeof Maru.stop==='function'"));
   assertEquals("true",eval("window.go('scores');document.querySelector('#page-scores').innerText.includes('MP3에서 자동으로 만들어지지 않습니다')"));
 }
 @Test public void nativeBridgePreservesActualPrivateData()throws Exception{
   ready();
   assertEquals("true",eval("typeof window.Maru.tracks==='function'"));
   assertEquals("true",eval("typeof window.Maru.stories==='function'"));
   assertEquals("true",eval("typeof window.Maru.download==='function'"));
   assertEquals("true",eval("typeof window.Maru.submitStory==='function'"));
   assertEquals("true",eval("typeof window.Maru.chooseSong==='function'"));
   assertEquals("true",eval("typeof window.Maru.exportScore==='function'"));
   assertEquals("true",eval("typeof window.Maru.pickScore==='function'"));
   String before=eval("JSON.parse(Maru.stories()).length");
   String result=eval("Maru.submitStory('QA검수','안드로이드에서 실제 사연을 저장한 가상 테스트 내용입니다','','private').includes('접수번호')");
   assertEquals("true",result);
   String after=eval("JSON.parse(Maru.stories()).length");
   assertEquals(Integer.parseInt(before)+1,Integer.parseInt(after));
   assertEquals("true",eval("JSON.parse(Maru.stories())[0].status==='검토대기'"));
 }
}