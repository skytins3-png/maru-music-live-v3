package com.maru.ahwanvoice;

import static org.junit.Assert.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class LiveShowInstrumentedTest {
    private static final String PREFS="ahwan_live_show_v1";

    private static void writeLE16(FileOutputStream out,int n) throws IOException {
        out.write(n&255);out.write((n>>8)&255);
    }
    private static void writeLE32(FileOutputStream out,int n) throws IOException {
        writeLE16(out,n&0xffff);writeLE16(out,n>>>16);
    }
    private File tone(Context ctx) throws IOException {
        File sound=new File(ctx.getFilesDir(),"test-show-original-tone.wav");
        int rate=16000, count=4800; // 0.3 seconds, never pretend to be a singer
        try(FileOutputStream out=new FileOutputStream(sound)){
            out.write(new byte[]{'R','I','F','F'});
            writeLE32(out,36+count*2);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            writeLE32(out,16);writeLE16(out,1);writeLE16(out,1);
            writeLE32(out,rate);writeLE32(out,rate*2);writeLE16(out,2);writeLE16(out,16);
            out.write(new byte[]{'d','a','t','a'});
            writeLE32(out,count*2);
            for(int i=0;i<count;i++){
                int sample=(int)(Math.sin(2*Math.PI*440*i/rate)*5000);
                writeLE16(out,sample&0xffff);
            }
        }
        return sound;
    }
    private Button button(View view,String expected){
        if(view instanceof Button) {
            Button b=(Button)view;
            if(b.getText().toString().contains(expected))return b;
        }
        if(view instanceof ViewGroup) {
            ViewGroup v=(ViewGroup)view;
            for(int i=0;i<v.getChildCount();i++) {
                Button found=button(v.getChildAt(i),expected);
                if(found!=null)return found;
            }
        }
        return null;
    }

    @Test public void thirtyDistinctNamedGuestsConfigured() {
        assertEquals(30,LiveShowActivity.SINGERS.length);
        assertEquals("강태준",LiveShowActivity.SINGERS[0]);
        assertEquals("정유라",LiveShowActivity.SINGERS[29]);
        assertEquals(30,new java.util.HashSet<>(java.util.Arrays.asList(LiveShowActivity.SINGERS)).size());
    }

    @Test public void noAudioCannotInventVoice() {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        Intent i=new Intent(app,LiveShowActivity.class);
        try(ActivityScenario<LiveShowActivity> scenario=ActivityScenario.launch(i)) {
            scenario.onActivity(activity -> {
                Button play=button(activity.getWindow().getDecorView(),"전체 방송 순차 재생");
                assertNotNull("Play-all button must exist",play);
                play.performClick();
                assertTrue(activity.statusForTest().contains("등록된 다음 코너가 없습니다"));
                assertEquals(0,activity.finishedCueCountForTest());
            });
        }
    }

    @Test public void actualRecordedAudioCuesPlaySequentially() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File audio=tone(app);
        SharedPreferences preferences=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        SharedPreferences.Editor edit=preferences.edit().clear().putInt("last_singer",0);
        for(int j=0;j<ShowQueue.CUE_COUNT;j++){
            edit.putString("s0_c"+j+"_uri",Uri.fromFile(audio).toString())
                .putString("s0_c"+j+"_title","합법 시연 파일 "+j);
        }
        assertTrue(edit.commit());
        Intent intent=new Intent(app,LiveShowActivity.class);
        try(ActivityScenario<LiveShowActivity> scenario=ActivityScenario.launch(intent)) {
            scenario.onActivity(a->{
                Button play=button(a.getWindow().getDecorView(),"전체 방송 순차 재생");
                assertNotNull(play);
                play.performClick();
            });
            AtomicInteger count=new AtomicInteger();
            AtomicReference<String> status=new AtomicReference<>("");
            long deadline=SystemClock.uptimeMillis()+25000;
            do{
                SystemClock.sleep(500);
                scenario.onActivity(a-> {
                    count.set(a.finishedCueCountForTest());
                    status.set(a.statusForTest());
                });
                if(count.get()==6)break;
            }while(SystemClock.uptimeMillis()<deadline);
            assertEquals("Real audio must finish all six cues, current: "+status.get(),6,count.get());
            assertTrue("Explicit show end message required: "+status.get(),status.get().contains("리허설 종료"));
            scenario.onActivity(a->{
                Button stop=button(a.getWindow().getDecorView(),"긴급 중단");
                assertNotNull(stop);
                stop.performClick();
                assertTrue(a.statusForTest().contains("중단"));
            });
        }finally {
            preferences.edit().clear().commit();
            audio.delete();
        }
    }
}
