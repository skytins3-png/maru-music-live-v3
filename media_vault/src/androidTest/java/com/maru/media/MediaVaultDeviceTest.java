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
 @Rule public ActivityScenarioRule<MainActivity> activity=new ActivityScenarioRule<>(MainActivity.class);

 @Test public void homeHasAllFourMainTiles(){
   onView(withText("🎵 노래 보관함")).check(matches(isDisplayed()));
   onView(withText("✉ 사연 받기")).check(matches(isDisplayed()));
   onView(withText("⬆ 노래 업로드")).check(matches(isDisplayed()));
   onView(withText("⬇ 노래 다운로드")).check(matches(isDisplayed()));
   onView(withText("🎵 노래 보관함")).perform(click());
   onView(withText("노래 보관함")).check(matches(isDisplayed()));
   onView(withText("사연")).perform(click());
   onView(withText("사연 받기 · 관리")).check(matches(isDisplayed()));
   onView(withText("업로드")).perform(click());
   onView(withText("노래 업로드")).check(matches(isDisplayed()));
   onView(withText("설정")).perform(click());
   onView(withText("관리 · 설정")).check(matches(isDisplayed()));
 }

 @Test public void songDetailSubmenusExist(){
   activity.getScenario().onActivity(a->{
     VaultDb db=new VaultDb(a);
     String sha="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
     if(!db.bySha(sha))db.insertTrack("가상 테스트곡","test.mp3","file:///absent/test.mp3",sha,9,"한국노래");
   });
   onView(withText("노래")).perform(click());
   onView(withText("상세 · 댓글")).perform(scrollTo(),click());
   for(String label:new String[]{
     "▶ 미리보기","⬇ 파일 저장 / 다운로드","💬 댓글·답글 보기·작성",
     "🎼 악보 첨부 또는 보기","공유","🎶 장르 변경","✎ 제목 수정"
   })onView(withText(label)).check(matches(isAssignableFrom(android.widget.Button.class)));
   onView(withText("닫기")).perform(click());
 }

 @Test public void storyAttachmentPickerPreservesTypedDraft(){
   onView(withText("사연")).perform(click());
   onView(withHint("사연 보낸 사람 닉네임")).perform(typeText("가명청취자"),closeSoftKeyboard());
   onView(withHint("사연 내용")).perform(typeText("사연 작성 중 입력한 내용 보존 확인"),closeSoftKeyboard());
   Intents.init();
   try{
     Intent data=new Intent().setData(Uri.parse("content://media-vault-test/fixture.jpg"));
     intending(IntentMatchers.hasAction(Intent.ACTION_OPEN_DOCUMENT))
       .respondWith(new android.app.Instrumentation.ActivityResult(Activity.RESULT_OK,data));
     onView(withText("📎 사진·영상·음성·악보 첨부 (선택)")).perform(scrollTo(),click());
   }finally{Intents.release();}
   onView(withHint("사연 보낸 사람 닉네임")).check(matches(withText("가명청취자")));
   onView(withHint("사연 내용")).check(matches(withText("사연 작성 중 입력한 내용 보존 확인")));
 }

 @Test public void storySubmenusAndDatabaseStatus(){
   activity.getScenario().onActivity(a->{
      VaultDb db=new VaultDb(a);
      db.addStory("가상청취자","실제 방송으로 공개되지 않는 로컬 가상 사연","");
   });
   onView(withText("사연")).perform(click());
   onView(withText("접수 상세 · 승인/예약 준비")).perform(scrollTo(),click());
   for(String label:new String[]{
      "👁 사연 미리보기","♡ 좋아요","💬 댓글·답글","⬇ 사연 다운로드(TXT)",
      "🕒 방송 예약 메모","✓ 사연 승인","📻 방송완료 표시","📁 보관"
   })onView(withText(label)).check(matches(isAssignableFrom(android.widget.Button.class)));
   onView(withText("닫기")).perform(click());
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