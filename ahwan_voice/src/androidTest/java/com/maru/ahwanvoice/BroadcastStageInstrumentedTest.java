package com.maru.ahwanvoice;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class BroadcastStageInstrumentedTest {
    private Context app(){
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
    private SharedPreferences pref(){return app().getSharedPreferences("ahwan_live_show_v1",Context.MODE_PRIVATE);}
    private File createPicture() throws Exception{
        File result=new File(app().getFilesDir(),"test-stage-background.png");
        Bitmap bmp=Bitmap.createBitmap(240,480,Bitmap.Config.ARGB_8888);
        bmp.eraseColor(Color.rgb(90,110,200));
        try(FileOutputStream out=new FileOutputStream(result)){
            assertTrue(bmp.compress(Bitmap.CompressFormat.PNG,90,out));
        }
        bmp.recycle();
        return result;
    }
    private static void writeLE16(FileOutputStream out,int n) throws Exception{
        out.write(n&255);out.write((n>>>8)&255);
    }
    private static void writeLE32(FileOutputStream out,int n) throws Exception{
        writeLE16(out,n&0xffff);writeLE16(out,n>>>16);
    }
    private File sound() throws Exception{
        File file=new File(app().getFilesDir(),"stage-test-original.wav");
        int hz=16000,count=6400; // Test beep ONLY. Never a human AI voice.
        try(FileOutputStream out=new FileOutputStream(file)){
            out.write(new byte[]{'R','I','F','F'});
            writeLE32(out,36+count*2);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            writeLE32(out,16);
            writeLE16(out,1);writeLE16(out,1);
            writeLE32(out,hz);writeLE32(out,hz*2);writeLE16(out,2);writeLE16(out,16);
            out.write(new byte[]{'d','a','t','a'});
            writeLE32(out,count*2);
            for(int i=0;i<count;i++){
                int signal=(int)(4200*Math.sin(2*Math.PI*480*i/hz));
                writeLE16(out,signal);
            }
        }
        return file;
    }
    private File sampleMp4() throws Exception{
        File dest=new File(app().getFilesDir(),"stage-test-loop-video.mp4");
        try(InputStream input=InstrumentationRegistry.getInstrumentation()
                    .getContext().getAssets().open("stage_loop.mp4");
            FileOutputStream out=new FileOutputStream(dest)){
            byte[] buffer=new byte[4096];
            int n;
            while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
        }
        assertTrue(dest.length()>512);
        return dest;
    }
    private Intent launch(){
        Intent intent=new Intent(app(),BroadcastStageActivity.class);
        intent.putExtra(BroadcastStageActivity.EXTRA_SINGER,0);
        return intent;
    }

    @Test public void noApprovedMediaShowsFallbackAndNeverTriggersCapture(){
        pref().edit().clear().commit();
        try(ActivityScenario<BroadcastStageActivity> scenario=ActivityScenario.launch(launch())){
            scenario.onActivity(stage->{
                assertEquals(-1,stage.currentCueForTest());
                assertEquals(0,stage.completedForTest());
                assertTrue(stage.screenOnForTest());
                assertTrue(stage.overlayForTest().contains("미등록"));
                assertEquals(View.GONE,stage.videoVisibilityForTest());
                assertNotNull(stage.getWindow().getDecorView().findViewWithTag("stage_controls"));
                View scene=stage.getWindow().getDecorView().findViewWithTag("scene_fallback");
                assertNotNull("Without licensed video we must still draw a scene",scene);
                assertEquals(View.VISIBLE,scene.getVisibility());
                assertEquals("BRIDGE",((ShowSceneFallbackView)scene).sceneModeForTest());
            });
        }
    }
    @Test public void defaultImageStaysVisibleAndSixAudiosReachEnding() throws Exception {
        File picture=createPicture(),audio=sound();
        SharedPreferences.Editor edit=pref().edit().clear().putInt("last_singer",0);
        edit.putString(BroadcastStageActivity.DEFAULT_VISUAL_KEY,Uri.fromFile(picture).toString());
        edit.putString(BroadcastStageActivity.DEFAULT_VISUAL_MIME,"image/png");
        for(int i=0;i<ShowQueue.CUE_COUNT;i++){
            edit.putString("s0_c"+i+"_uri",Uri.fromFile(audio).toString())
                    .putString("s0_c"+i+"_title","테스트 음원 "+i);
        }
        assertTrue(edit.commit());
        try(ActivityScenario<BroadcastStageActivity> scenario=ActivityScenario.launch(launch())){
            AtomicInteger count=new AtomicInteger();
            AtomicReference<String> state=new AtomicReference<>("");
            long deadline=SystemClock.uptimeMillis()+30000;
            do{
                SystemClock.sleep(450);
                scenario.onActivity(stage->{
                    count.set(stage.completedForTest());
                    state.set(stage.overlayForTest());
                    assertTrue(stage.screenOnForTest());
                    assertEquals(View.GONE,stage.videoVisibilityForTest());
                });
                if(count.get()==6)break;
            }while(SystemClock.uptimeMillis()<deadline);
            assertEquals("Six actual audio cues must finish: "+state.get(),6,count.get());
            assertTrue(state.get().contains("방송 종료"));
            scenario.onActivity(stage->{
                View fallback=stage.getWindow().getDecorView().findViewWithTag("fallback_message");
                assertNotNull(fallback);
                assertEquals("Default image should occupy stage",View.GONE,fallback.getVisibility());
                View scene=stage.getWindow().getDecorView().findViewWithTag("scene_fallback");
                assertNotNull(scene);
                assertEquals("User-approved image must replace illustration",View.GONE,scene.getVisibility());
            });
        }finally{
            pref().edit().clear().commit();
            audio.delete();
            picture.delete();
        }
    }
    @Test public void registeredSilentVideoFillsStageInPortraitMode() throws Exception{
        File clip=sampleMp4(),beep=sound();
        SharedPreferences.Editor edit=pref().edit().clear();
        edit.putString("s0_c0_uri",Uri.fromFile(beep).toString());
        edit.putString(BroadcastStageActivity.DEFAULT_VISUAL_KEY,Uri.fromFile(clip).toString());
        edit.putString(BroadcastStageActivity.DEFAULT_VISUAL_MIME,"video/mp4");
        assertTrue(edit.commit());
        try(ActivityScenario<BroadcastStageActivity> scenario=ActivityScenario.launch(launch())){
            AtomicBoolean visible=new AtomicBoolean(false);
            AtomicBoolean actualFrame=new AtomicBoolean(false);
            long deadline=SystemClock.uptimeMillis()+18000;
            do{
                SystemClock.sleep(500);
                scenario.onActivity(stage->{
                    visible.set(stage.videoVisibilityForTest()==View.VISIBLE);
                    actualFrame.set(stage.videoFrameRenderedForTest());
                    assertTrue("Stage must remain awake",stage.screenOnForTest());
                });
                if(actualFrame.get())break;
            }while(SystemClock.uptimeMillis()<deadline);
            assertTrue("Video canvas must be visible",visible.get());
            assertTrue("Must show decoded BLUE video frame, not black or blank",actualFrame.get());
            scenario.onActivity(stage->{
                FrameLayout root=(FrameLayout)stage.getWindow().findViewById(android.R.id.content);
                assertTrue("Portrait video screen",root.getHeight()>root.getWidth());
            });
        }finally{
            pref().edit().clear().commit();
            clip.delete();
            beep.delete();
        }
    }
}
