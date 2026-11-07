package com.maru.musiclive;
import org.junit.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

public final class WriterRoomTest {
 @Test public void writerNamesNeverOverlapWithSingersOrHosts(){
   List<String> occupied=new ArrayList<>();
   for(VirtualArtistCatalog.Artist a:VirtualArtistCatalog.all())occupied.add(a.name);
   occupied.add("아환");occupied.add("윤솔");
   assertEquals(5,WriterRoomCatalog.all().size());
   assertTrue(WriterRoomCatalog.noOverlap(occupied));
   assertFalse(WriterRoomCatalog.noOverlap(Arrays.asList("서하린","문세필")));
   assertEquals("WR-01",WriterRoomCatalog.all().get(0).id);
 }
 @Test public void writersCannotSpeakAsPresenters(){
   for(int i=0;i<15;i++){
     RadioConversationScript.Line[] cues=WriterRoomScript.beforeSong(
       "등록한 원곡",i,LocalDate.of(2026,11,8),1,"","",null,false);
     assertTrue(cues.length>=5);
     for(RadioConversationScript.Line cue:cues)
       for(WriterRoomCatalog.Writer writer:WriterRoomCatalog.all())
         assertNotEquals(writer.name,cue.speaker.name());
     assertEquals(RadioConversationScript.Speaker.YUNSOL,cues[cues.length-1].speaker);
     assertTrue(cues[cues.length-2].text.contains("등록한 원곡"));
   }
 }
 @Test public void noInventedListenerOrUnreviewedResearch(){
   RadioConversationScript.Line[] cues=WriterRoomScript.beforeSong(
     "가을 바다",0,LocalDate.of(2026,11,8),1,"","","믿을 수 없는 거짓 사실",false);
   for(RadioConversationScript.Line cue:cues){
     assertFalse(cue.text.contains("믿을 수 없는 거짓 사실"));
     assertFalse(cue.text.contains("님의 사연"));
   }
 }
 @Test public void approvedStoryInsertedWithLengthLimit(){
   StringBuilder s=new StringBuilder();
   for(int i=0;i<800;i++)s.append('가');
   RadioConversationScript.Line[] cues=WriterRoomScript.beforeSong(
     "다음 곡",0,LocalDate.of(2026,11,8),1,"청취자",""+s,null,false);
   boolean hasStory=false;
   for(RadioConversationScript.Line line:cues)if(line.text.contains("청취자님의 이야기"))hasStory=true;
   assertTrue(hasStory);
 }
}
