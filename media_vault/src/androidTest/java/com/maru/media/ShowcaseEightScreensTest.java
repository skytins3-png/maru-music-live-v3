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
     ref.set((WebView)root.getChildAt(0));
   });
   WebView web=ref.get();assertNotNull(web);
   CountDownLatch latch=new CountDownLatch(1);
   AtomicReference<String> result=new AtomicReference<>("");
   web.post(()->web.evaluateJavascript(js,value->{result.set(value);latch.countDown();}));
   assertTrue("JS response must return",latch.await(15,TimeUnit.SECONDS));
   return result.get();
 }
 private void screenshot(String name)throws Exception{
   Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
   File dir=new File(context.getExternalFilesDir(null),"eight-screens");
   assertTrue(dir.exists()||dir.mkdirs());
   Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
   assertNotNull("Virtual Android screen capture required",shot);
   try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){
     assertTrue(shot.compress(Bitmap.CompressFormat.PNG,100,out));
   }finally{shot.recycle();}
 }
 private void show(String target)throws Exception{
   String result=eval("window.go("+org.json.JSONObject.quote(target)+");document.querySelector('#page-"+target+"').classList.contains('active')");
   assertEquals("true",result);
   Thread.sleep(450);
 }
 @Test public void allEightScreensHaveActiveUIAndAreCaptured()throws Exception{
   String loaded=eval("document.querySelectorAll('.screen').length");
   assertTrue(Integer.parseInt(loaded)>=8);
   assertEquals("4",eval("document.querySelectorAll('#bottom-nav button').length"));
   screenshot("01-home");
   for(String page:new String[]{"songs","upload","downloads","story-compose","stories","profile"}){
     show(page);
     String content=eval("document.querySelector('#page-"+page+"').innerText.length");
     assertTrue(page+" content must be real text",Integer.parseInt(content)>35);
     screenshot("0"+(new String[]{"songs","upload","downloads","story-compose","stories","profile"}.length)+page.replace("-","_"));
   }
   // Demo data is clearly marked. It never writes fake statistics to local DB.
   assertEquals("true",eval("window.setDemo(true);window.go('songs');document.querySelector('.demo-flag')!==null"));
   assertEquals("true",eval("window.go('detail',-1);document.querySelector('#page-detail').innerText.includes('그리운 사람')"));
   screenshot("03-song-detail");
   assertEquals("true",eval("window.go('story-detail',-1);document.querySelector('#page-story-detail').innerText.includes('바람소리')"));
   screenshot("09-story-detail");
 }
 @Test public void nativeBridgePreservesActualPrivateData()throws Exception{
   assertEquals("true",eval("typeof window.Maru.tracks==='function'"));
   assertEquals("true",eval("typeof window.Maru.stories==='function'"));
   assertEquals("true",eval("typeof window.Maru.download==='function'"));
   assertEquals("true",eval("typeof window.Maru.submitStory==='function'"));
   assertEquals("true",eval("typeof window.Maru.chooseSong==='function'"));
   String before=eval("JSON.parse(Maru.stories()).length");
   String result=eval("Maru.submitStory('QA검수','안드로이드에서 실제 사연을 저장한 가상 테스트 내용입니다','','private').includes('접수번호')");
   assertEquals("true",result);
   String after=eval("JSON.parse(Maru.stories()).length");
   assertEquals(Integer.parseInt(before)+1,Integer.parseInt(after));
   assertEquals("true",eval("JSON.parse(Maru.stories())[0].status==='검토대기'"));
 }
}