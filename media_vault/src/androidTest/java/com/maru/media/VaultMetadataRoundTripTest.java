package com.maru.media;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.security.MessageDigest;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public final class VaultMetadataRoundTripTest{
 @Test public void exportRestoreKeepsStoriesCommentsAndMetadataWithoutInventingAudio()throws Exception{
   android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
   VaultDb db=new VaultDb(c);
   String sha=CatalogLogic.hashHex(MessageDigest.getInstance("SHA-256").digest(
       ("MARU-roundtrip-"+System.nanoTime()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   long track=db.insertTrack("원본 노래 제목","restore_test.mp3","file:///invalid/restore_test.mp3",sha,12345,"한국노래");
   assertTrue(track>0);
   db.update(track,"artist","가상 아티스트");
   long comment=db.addComment(track,"사연청취자","오리지널 댓글입니다");
   db.addSongReply(track,comment,"호스트","댓글에 대한 답변입니다");
   long story=db.addStory("테스트청취자","앱 내부 보관 및 복구를 확인하는 유효 사연입니다","");
   db.addStoryComment(story,0,"청취자","사연에 쓴 댓글입니다");
   String exported=VaultMetadataArchive.exportData(db);
   assertTrue(exported.contains("MARU_MEDIA_PRIVATE_METADATA_V1"));
   assertFalse("old device URI is not portable and must be removed",exported.contains("file:///invalid"));
   db.update(track,"title","잘못 수정한 제목");
   VaultMetadataArchive.Result result=VaultMetadataArchive.importData(db,exported);
   assertTrue(result.tracksRestored>=1);
   assertEquals("원본 노래 제목",db.get(track).title);
   assertEquals("가상 아티스트",db.get(track).artist);
   assertTrue(db.comments(track).stream().anyMatch(x->x.contains("오리지널 댓글")));
   assertTrue(db.songReplies(track).stream().anyMatch(x->x.body.contains("답변")));
   int storyCount=db.stories().size(),comments=db.songReplies(track).size();
   VaultMetadataArchive.importData(db,exported);
   assertEquals("repeated restore should not duplicate stories",storyCount,db.stories().size());
   assertEquals("repeated restore should not duplicate replies",comments,db.songReplies(track).size());
 }
}