package com.maru.media;

import android.content.Context;
import android.net.Uri;
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
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DownloadPreviewPlaybackTest {
 @Rule public ActivityScenarioRule<ShowcaseActivity> rule=new ActivityScenarioRule<>(ShowcaseActivity.class);

 private String eval(String js)throws Exception{
   AtomicReference<WebView> web=new AtomicReference<>();
   rule.getScenario().onActivity(a->{
     ViewGroup root=a.findViewById(android.R.id.content);
     android.view.View first=root.getChildAt(0);
     web.set(first instanceof WebView?(WebView)first:
       (WebView)((ViewGroup)first).getChildAt(0));
   });
   CountDownLatch done=new CountDownLatch(1);
   AtomicReference<String> result=new AtomicReference<>();
   web.get().post(()->web.get().evaluateJavascript(js,v->{result.set(v);done.countDown();}));
   assertTrue("WebView response",done.await(12,TimeUnit.SECONDS));
   return result.get();
 }
 private File sineWav(Context ctx)throws Exception{
   File output=new File(ctx.getCacheDir(),"maru-preview-test-4s.wav");
   int rate=44100,count=rate*4;
   ByteBuffer bytes=ByteBuffer.allocate(44+count*2).order(ByteOrder.LITTLE_ENDIAN);
   bytes.put(new byte[]{'R','I','F','F'}).putInt(36+count*2);
   bytes.put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16);
   bytes.putShort((short)1).putShort((short)1).putInt(rate).putInt(rate*2);
   bytes.putShort((short)2).putShort((short)16);
   bytes.put(new byte[]{'d','a','t','a'}).putInt(count*2);
   for(int i=0;i<count;i++)bytes.putShort((short)(Math.sin(i*2*Math.PI*440/rate)*8000));
   try(FileOutputStream stream=new FileOutputStream(output)){stream.write(bytes.array());}
   return output;
 }
 @Test public void aRealLocalAudioPreviewCanPlayPauseResumeAndStop()throws Exception{
   Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
   File file=sineWav(context);
   String sha=CatalogLogic.hashHex(MessageDigest.getInstance("SHA-256").digest(
       java.nio.file.Files.readAllBytes(file.toPath())));
   VaultDb db=new VaultDb(context);
   long id=db.insertTrack("가상 미리듣기 확인용","test.wav",Uri.fromFile(file).toString(),sha,file.length(),"한국노래");
   if(id<0){
     id=-1;
     for(VaultDb.Track t:db.tracks("전체"))if(sha.equals(t.sha)){id=t.id;break;}
   }
   assertTrue(id>0);
   db.close();
   try{
     String target=String.valueOf(id);
     // WebView and playback engine work on different threads. Poll actual callback status.
     assertEquals("true",eval("typeof Maru.preview==='function'&&typeof Maru.togglePlayback==='function'"));
     assertEquals("true",eval("Maru.preview("+target+");true"));
     boolean started=false;
     for(int i=0;i<50;i++){
       if("true".equals(eval("playbackTrackId==="+target+"&&playbackState==='playing'"))){started=true;break;}
       Thread.sleep(100);
     }
     assertTrue("Prepared WAV must report actual playback start",started);
     eval("Maru.togglePlayback();true");
     boolean paused=false;
     for(int i=0;i<25;i++){
       if("true".equals(eval("playbackState==='paused'"))){paused=true;break;}
       Thread.sleep(100);
     }
     assertTrue("WAV preview must pause",paused);
     eval("Maru.togglePlayback();true");
     boolean resumed=false;
     for(int i=0;i<25;i++){
       if("true".equals(eval("playbackState==='playing'"))){resumed=true;break;}
       Thread.sleep(100);
     }
     assertTrue("WAV preview must resume",resumed);
     eval("Maru.stop();true");
     boolean stopped=false;
     for(int i=0;i<25;i++){
       if("true".equals(eval("playbackState==='stopped'"))){stopped=true;break;}
       Thread.sleep(100);
     }
     assertTrue("WAV preview must stop",stopped);
   }finally{file.delete();}
 }
}
