package com.maru.ahwanvoice;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TalkWriterInstrumentedTest {
    private Button find(View view,String expected){
        if(view instanceof Button && ((Button)view).getText().toString().contains(expected))
            return (Button)view;
        if(view instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)view;
            for(int i=0;i<g.getChildCount();i++){
                Button match=find(g.getChildAt(i),expected);
                if(match!=null)return match;
            }
        }
        return null;
    }

    @Test public void canOpenWriterAndStoreFeedbackLocally() {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs=app.getSharedPreferences("ahwan_adaptive_writer_private_v1",Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        Intent intent=new Intent(app,TalkWriterActivity.class);
        intent.putExtra(TalkWriterActivity.EXTRA_SINGER_INDEX,1);
        try(ActivityScenario<TalkWriterActivity> scenario=ActivityScenario.launch(intent)){
            scenario.onActivity(a->{
                assertTrue(a.scriptForTest().contains("민도윤"));
                assertTrue(a.scriptForTest().contains("아환:"));
                Button like=find(a.getWindow().getDecorView(),"이 대화 마음에 들어");
                assertNotNull(like);
                like.performClick();
                assertTrue(a.scriptForTest().contains("가상 AI 가수"));
                assertEquals(1,AdaptiveTalkEngine.Memory.deserialize(
                    prefs.getString("ratings","")).episodes);
                Button unlike=find(a.getWindow().getDecorView(),"반복되거나 재미없어");
                assertNotNull(unlike);
                unlike.performClick();
                assertEquals(2,AdaptiveTalkEngine.Memory.deserialize(
                    prefs.getString("ratings","")).episodes);
            });
        }finally{prefs.edit().clear().commit();}
    }

    @Test public void generatedDialogueNotPassedToTtsOrBroadcast() {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent=new Intent(app,TalkWriterActivity.class);
        intent.putExtra(TalkWriterActivity.EXTRA_SINGER_INDEX,99);
        try(ActivityScenario<TalkWriterActivity> scenario=ActivityScenario.launch(intent)){
            scenario.onActivity(a->{
                assertTrue(a.scriptForTest().contains("정유라"));
                assertTrue(a.scriptForTest().contains("실제 등록된 승인 음원"));
                assertNotNull(find(a.getWindow().getDecorView(),"대본 승인"));
                assertNotNull(find(a.getWindow().getDecorView(),"학습 기록 초기화"));
            });
        }
    }
}
