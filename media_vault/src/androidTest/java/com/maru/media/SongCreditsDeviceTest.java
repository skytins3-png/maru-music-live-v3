package com.maru.media;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.content.Context;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public final class SongCreditsDeviceTest{
 @Test public void dbAddsCreditsWithoutChangingMusicAndBackupRoundTrips()throws Exception{
   Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
   VaultDb db=new VaultDb(ctx);
   String sha=CatalogLogic.hashHex(MessageDigest.getInstance("SHA-256").digest(
       ("MARU-CREDITS-"+System.nanoTime()).getBytes(StandardCharsets.UTF_8)));
   long id=db.insertTrack("테스트 자작곡","test.mp3","file:///dummy/test.mp3",sha,4500,"한국노래");
   assertTrue(id>0);
   assertEquals(0,db.get(id).isOriginal);
   assertEquals("",db.get(id).composer);
   db.update(id,"artist","기존 가수");
   db.update(id,"genre","발라드");
   assertEquals(0,db.fillMissingArtists()); // The record already has a singer.
   assertEquals("기존 가수",db.get(id).artist);
   db.markOriginal(id,true);
   assertEquals(1,db.get(id).isOriginal);
   assertEquals("박영환",db.get(id).lyricist);
   assertEquals("박영환",db.get(id).composer);
   assertEquals("박영환",db.get(id).styleFinisher);
   assertEquals("Suno AI",db.get(id).sunoAssist);
   String backup=VaultMetadataArchive.exportData(db);
   assertTrue(backup.contains("style_finisher"));
   assertTrue(backup.contains("박영환"));
   db.markOriginal(id,false);
   assertEquals("",db.get(id).composer);
   VaultMetadataArchive.importData(db,backup);
   assertEquals("박영환",db.get(id).composer);
   assertEquals("기존 가수",db.get(id).artist);
   assertEquals(1,db.get(id).isOriginal);
 }
 @Test public void unknownSingerGetsGroupWithoutChangingExistingSong()throws Exception{
   Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
   VaultDb db=new VaultDb(ctx);
   String sha=CatalogLogic.hashHex(MessageDigest.getInstance("SHA-256").digest(
       ("MARU-SINGER-"+System.nanoTime()).getBytes(StandardCharsets.UTF_8)));
   long id=db.insertTrack("云南的清晨","china-test.mp3","file:///dummy/test2.mp3",sha,2300,"중국노래");
   assertEquals("",db.get(id).artist);
   db.fillMissingArtists();
   assertEquals("중국친구",db.get(id).artist);
   assertEquals(1,db.get(id).artistGroupAuto);
   assertEquals(0,db.get(id).isOriginal);
   db.setSingerGroup(id,"아환의 친구들");
   assertEquals("아환의 친구들",db.get(id).artist);
 }
}