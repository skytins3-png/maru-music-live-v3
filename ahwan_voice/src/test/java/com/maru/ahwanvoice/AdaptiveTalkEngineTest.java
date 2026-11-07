package com.maru.ahwanvoice;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.HashSet;
import java.util.Set;

public class AdaptiveTalkEngineTest {
    @Test public void twelveDistinctDailyLifeAndHumorCategories(){
        assertEquals(12,AdaptiveTalkEngine.topicCount());
        assertEquals(12,new HashSet<>(java.util.Arrays.asList(AdaptiveTalkEngine.TOPICS)).size());
        assertTrue(java.util.Arrays.asList(AdaptiveTalkEngine.TOPICS).contains("작은 생활 개그"));
    }
    @Test public void newTopicsRotateAndNeverRepeatTheRecentSix(){
        AdaptiveTalkEngine.Memory memory=new AdaptiveTalkEngine.Memory();
        Set<Integer> first=new HashSet<>();
        for(int i=0;i<12;i++){
            AdaptiveTalkEngine.Suggestion suggestion=
                    AdaptiveTalkEngine.suggest(memory,AdaptiveTalkEngine.AUTO_TOPIC,5,"가상 가수");
            if(i<7)assertTrue("early topic repeated",first.add(suggestion.topic));
            AdaptiveTalkEngine.record(memory,suggestion.topic,0);
        }
        assertEquals(12,memory.episodes);
        assertEquals(6,memory.recent.size());
    }
    @Test public void explicitlyChosenTopicsStayUnderProducerControl(){
        AdaptiveTalkEngine.Memory memory=new AdaptiveTalkEngine.Memory();
        for(int i=0;i<3;i++){
            AdaptiveTalkEngine.Suggestion s=AdaptiveTalkEngine.suggest(memory,3,0,"강태준");
            assertEquals(3,s.topic);
            assertTrue(s.script.contains("아환:"));
            assertTrue(s.script.contains("강태준"));
            assertTrue(s.script.contains("가상 AI 가수"));
            assertTrue(s.script.contains("승인 음원"));
            AdaptiveTalkEngine.record(memory,3,-1);
        }
        assertEquals(3,memory.disliked[3]);
    }
    @Test public void learnsFromLikesAndDislikesWithoutRecordingRawChat(){
        AdaptiveTalkEngine.Memory memory=new AdaptiveTalkEngine.Memory();
        for(int i=0;i<5;i++)AdaptiveTalkEngine.record(memory,1,1);
        for(int i=0;i<5;i++)AdaptiveTalkEngine.record(memory,2,-1);
        assertEquals(5,memory.liked[1]);
        assertEquals(5,memory.disliked[2]);
        String stored=memory.serialize();
        assertFalse(stored.contains("private listener"));
        AdaptiveTalkEngine.Memory parsed=AdaptiveTalkEngine.Memory.deserialize(stored);
        assertEquals(stored,parsed.serialize());
        assertEquals(5,parsed.liked[1]);
        assertEquals(5,parsed.disliked[2]);
        assertTrue(parsed.recent.size() <= 6); // bounded recent list
    }
    @Test public void ratingsSaturateAndBadStoredStateCannotCrash(){
        AdaptiveTalkEngine.Memory memory=new AdaptiveTalkEngine.Memory();
        for(int i=0;i<30;i++)AdaptiveTalkEngine.record(memory,0,1);
        assertEquals(5,memory.liked[0]);
        assertEquals(0,AdaptiveTalkEngine.Memory.deserialize("malformed").episodes);
        assertEquals(0,AdaptiveTalkEngine.Memory.deserialize(null).episodes);
        assertEquals(0,AdaptiveTalkEngine.Memory.deserialize("v1|boom|x|x").episodes);
        assertEquals(0,AdaptiveTalkEngine.Memory.deserialize("v1|1|"+ "bad".repeat(1000)+"|0").episodes);
    }
    @Test public void topicSelectionProvidesSafeOriginalFictionNotMadeUpNews(){
        for(int i=0;i<AdaptiveTalkEngine.topicCount();i++){
            AdaptiveTalkEngine.Suggestion s=AdaptiveTalkEngine.suggest(
                    new AdaptiveTalkEngine.Memory(),i,8,"류하준");
            assertTrue(s.script.contains("가상 이야기"));
            assertFalse(s.script.contains("실시간 속보"));
            assertFalse(s.script.contains("오늘 기온"));
            assertTrue(s.script.contains("제작자 확인"));
        }
    }
    @Test public void naturalInterviewRotatesShortOpeningAndNeverAssumesUnregisteredAudio(){
        AdaptiveTalkEngine.Memory m=new AdaptiveTalkEngine.Memory();
        java.util.List<String> scripts=new java.util.ArrayList<>();
        for(int i=0;i<3;i++){
            AdaptiveTalkEngine.Suggestion next=AdaptiveTalkEngine.suggest(m,2,0,"강태준");
            scripts.add(next.script);
            assertTrue(next.script.contains("미리 작성된 창작 초대석"));
            assertTrue(next.script.contains("실시간 인터뷰가 아닙니다"));
            assertTrue(next.script.contains("실제 등록된 승인 음원이 준비된 경우에만"));
            assertTrue(next.script.contains("선곡 파일이 없다면"));
            assertFalse("Do not announce a song is loaded without checking",next.script.contains("**실제 등록된 승인 음원**"));
            assertTrue(next.script.contains("아환:"));
            assertTrue(next.script.contains("강태준 (가상 이야기):"));
            AdaptiveTalkEngine.record(m,next.topic,0);
        }
        assertNotEquals("Opening must not use identical boilerplate",scripts.get(0),scripts.get(1));
        assertNotEquals("Next interview also differs",scripts.get(1),scripts.get(2));
    }
    @Test public void untrustedSingerLabelCannotAddScriptLinesOrHtml(){
        AdaptiveTalkEngine.Suggestion s=AdaptiveTalkEngine.suggest(
                new AdaptiveTalkEngine.Memory(),0,0,"<강태준>\n가짜방송:");
        assertFalse(s.script.contains("<강태준>"));
        assertFalse(s.script.contains("\n가짜방송:"));
        assertTrue(s.script.contains("강태준가짜방송:"));
    }
    @Test(expected=IllegalArgumentException.class)
    public void fakeMarkupOnlySingerLabelRejected(){
        AdaptiveTalkEngine.suggest(new AdaptiveTalkEngine.Memory(),0,0,"<>\n");
    }
    @Test(expected=IllegalArgumentException.class)
    public void unknownRatingRejected(){
        AdaptiveTalkEngine.record(new AdaptiveTalkEngine.Memory(),-1,1);
    }
    @Test(expected=IllegalArgumentException.class)
    public void unknownTopicRejected(){
        AdaptiveTalkEngine.suggest(new AdaptiveTalkEngine.Memory(),80,0,"강태준");
    }
}
