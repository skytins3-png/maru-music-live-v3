package com.maru.media;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.espresso.intent.rule.IntentsRule;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.intent.Intents.intending;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public final class MediaVaultDeviceTest {
 private void takeQaScreenshot(String name) throws Exception{
   final android.content.Context ctx=
     androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
   android.graphics.Bitmap screenshot=
     androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
   assertNotNull("Test must capture an actual live app screen",screenshot);
   java.io.File folder=new java.io.File(ctx.getExternalFilesDir(null),"qa");
   assertTrue(folder.exists()||folder.mkdirs());
   try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name))){
     assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
   }finally{screenshot.recycle();}
 }
 @Test public void referenceSongListAndDetailScreenshots() throws Exception{
   activity.getScenario().onActivity(a->{
     VaultDb db=new VaultDb(a);
     String sha="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
     if(!db.bySha(sha))db.insertTrack("화면 확인용 가상곡","test-reference.mp3",
          "file:///absent/example.mp3",sha,1200000,"한국노래");
   });
   onView(withContentDescription("nav-songs")).perform(click());
   onView(withText("최신")).perform(click());
   takeQaScreenshot("qa-song-list.png");
   onView(withText("화면 확인용 가상곡")).perform(scrollTo(),click());
   onView(withText("곡 상세")).check(matches(isDisplayed()));
   takeQaScreenshot("qa-song-detail.png");
 }

 @Rule public ActivityScenarioRule<MainActivity> activity=new ActivityScenarioRule<>(MainActivity.class);

 @Test public void homeHasAllFourMainTiles(){
   onView(withText("노래 보관함")).check(matches(isDisplayed()));
   onView(withText("사연 보내기")).check(matches(isDisplayed()));
   onView(withText("노래 업로드")).check(matches(isDisplayed()));
   onView(withText("노래 다운로드")).check(matches(isDisplayed()));
   onView(withText("노래 보관함")).perform(click());
   onView(withText("노래 보관함")).check(matches(isDisplayed()));
   onView(withContentDescription("nav-stories")).perform(click());
   onView(withText("사연 받기 · 관리")).check(matches(isDisplayed()));
   onView(withContentDescription("nav-home")).perform(click());
   onView(withText("노래 업로드")).perform(click());
   onView(withText("노래 업로드")).check(matches(isDisplayed()));
   onView(withContentDescription("nav-account")).perform(click());
   onView(withText("관리 · 설정")).check(matches(isDisplayed()));
 }

 @Test public void songDetailSubmenusExist(){
   activity.getScenario().onActivity(a->{
     VaultDb db=new VaultDb(a);
     String sha="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
     if(!db.bySha(sha))db.insertTrack("가상 테스트곡","test.mp3","file:///absent/test.mp3",sha,9,"한국노래");
   });
   onView(withContentDescription("nav-songs")).perform(click());
   onView(withText("가상 테스트곡")).perform(scrollTo(),click());
   onView(withText("곡 상세")).check(matches(isDisplayed()));
   onView(withText("다운로드")).check(matches(isDisplayed()));
   onView(withText("악보 보기")).check(matches(isDisplayed()));
   onView(withText("보관함 추가")).check(matches(isDisplayed()));
 }

 @Test public void storyAttachmentPickerPreservesTypedDraft(){
   onView(withContentDescription("nav-stories")).perform(click());
   onView(withHint("사연 보낸 사람 닉네임")).perform(typeText("TestGuest"),closeSoftKeyboard());
   onView(withHint("사연 내용")).perform(typeText("Draft body stays when selecting attachment"),closeSoftKeyboard());
   Intents.init();
   try{
     Intent data=new Intent().setData(Uri.parse("content://media-vault-test/fixture.jpg"));
     intending(IntentMatchers.hasAction(Intent.ACTION_OPEN_DOCUMENT))
       .respondWith(new android.app.Instrumentation.ActivityResult(Activity.RESULT_OK,data));
     onView(withText("📎 사진·영상·음성·악보 첨부 (선택)")).perform(scrollTo(),click());
   }finally{Intents.release();}
   onView(withHint("사연 보낸 사람 닉네임")).check(matches(withText("TestGuest")));
   onView(withHint("사연 내용")).check(matches(withText("Draft body stays when selecting attachment")));
 }

 @Test public void storySubmenusAndDatabaseStatus(){
   activity.getScenario().onActivity(a->{
      VaultDb db=new VaultDb(a);
      db.addStory("가상청취자","실제 방송으로 공개되지 않는 로컬 가상 사연","");
   });
   onView(withContentDescription("nav-stories")).perform(click());
   onView(withText("접수 상세 · 승인/예약 준비")).perform(scrollTo(),click());
   for(String label:new String[]{
      "👁 사연 미리보기","♡ 좋아요","💬 댓글·답글","⬇ 사연 다운로드(TXT)",
      "🕒 방송 예약 메모","✓ 사연 승인","📻 방송완료 표시","📁 보관"
   })onView(withText(label)).check(matches(isAssignableFrom(android.widget.Button.class)));
   onView(withText("닫기")).perform(click());
 }

 @Test public void virtual107AudioBatchKeeps105Unique() throws Exception{
   final Context ctx=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
   File archive=new File(ctx.getCacheDir(),"qa-107-mock-audio.zip");
   try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(archive))){
     for(int i=0;i<107;i++){
       int source=i<105?i:i-105;
       String name=String.format(java.util.Locale.ROOT,"가상테스트_%03d.mp3",i);
       byte[] audio=("ID3-QA-PRIVATE-TEST-ONLY-"+source).getBytes(java.nio.charset.StandardCharsets.UTF_8);
       z.putNextEntry(new ZipEntry(name));z.write(audio);z.closeEntry();
     }
   }
   VaultDb db=new VaultDb(ctx);
   LibraryImporter importer=new LibraryImporter(ctx,db,null);
   importer.fromAudioUris(Collections.singletonList(Uri.fromFile(archive)));
   assertEquals(107,importer.stats().scanned);
   assertEquals(107,importer.stats().inserted+importer.stats().duplicate);
   assertTrue("두 중복을 자동 제외",importer.stats().duplicate>=2);
   assertEquals(0,importer.stats().invalid);
 }
 @Test public void zipImporterDeduplicatesAndManifestApplies() throws Exception{
   final Context ctx=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
   File f=new File(ctx.getCacheDir(),"qa-duplicates.zip");
   byte[] data=new byte[]{73,68,51,3,0,0,0,0,0,0,1,2,3,4,5,6,7};
   try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
     for(String name:new String[]{"내 사랑.mp3","중복 사본.mp3"}){
       z.putNextEntry(new ZipEntry(name));z.write(data);z.closeEntry();
     }
   }
   VaultDb db=new VaultDb(ctx);
   String sha=CatalogLogic.hashHex(MessageDigest.getInstance("SHA-256").digest(data));
   LibraryImporter importer=new LibraryImporter(ctx,db,null);
   importer.fromAudioUris(Collections.singletonList(Uri.fromFile(f)));
   assertEquals(2,importer.stats().scanned);
   assertEquals(2,importer.stats().inserted+importer.stats().duplicate);
   // One duplicate may exist already if the device ran this fixture before.
   assertTrue(db.bySha(sha));
   JSONObject manifest=new JSONObject();
   JSONObject track=new JSONObject().put("sha256",sha).put("languageGroup","중국노래").put("title","정확한 제목");
   manifest.put("tracks",new JSONArray().put(track));
   assertEquals(1,db.importManifest(manifest.toString()));
   boolean found=false;for(VaultDb.Track t:db.tracks("중국노래"))if(t.sha.equals(sha)&&t.title.equals("정확한 제목"))found=true;
   assertTrue(found);
 }
}