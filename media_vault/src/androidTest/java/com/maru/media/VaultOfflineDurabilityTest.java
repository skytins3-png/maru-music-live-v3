package com.maru.media;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class VaultOfflineDurabilityTest{
 @Test public void importedMp3IsPrivateDurableAndDeduplicated()throws Exception{
   android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
   byte[] bytes=("ID3-MARU-OFFLINE-DURABILITY-"+System.nanoTime()).getBytes(StandardCharsets.UTF_8);
   File original=new File(c.getCacheDir(),"source_original.mp3");
   try(FileOutputStream o=new FileOutputStream(original)){o.write(bytes);}
   VaultDb db=new VaultDb(c);
   LibraryImporter first=new LibraryImporter(c,db,null);
   first.fromAudioUris(Collections.singletonList(Uri.fromFile(original)));
   assertEquals("new source should add one track",1,first.stats().inserted);
   assertEquals(0,first.stats().invalid);
   VaultDb.Track imported=null;
   for(VaultDb.Track t:db.tracks("전체"))
     if("source_original.mp3".equals(t.filename))imported=t;
   assertNotNull("track must be in private database",imported);
   assertTrue("managed file URI required",imported.uri.startsWith("file:"));
   File managed=new File(Uri.parse(imported.uri).getPath());
   assertTrue("managed copy must physically exist",managed.isFile());
   assertTrue("the original file should remain untouched",original.isFile());
   assertTrue("managed copy must not be the original file",!managed.getCanonicalPath().equals(original.getCanonicalPath()));
   assertTrue("simulate moving/removing original source",original.delete());
   ByteArrayOutputStream data=new ByteArrayOutputStream();
   try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(imported.uri))){
     byte[] buf=new byte[128];int n;while((n=in.read(buf))!=-1)data.write(buf,0,n);
   }
   assertArrayEquals("song must still play/save after original is removed",bytes,data.toByteArray());
   File same=new File(c.getCacheDir(),"duplicate_copy.mp3");
   try(FileOutputStream out=new FileOutputStream(same)){out.write(bytes);}
   LibraryImporter dup=new LibraryImporter(c,db,null);
   dup.fromAudioUris(Collections.singletonList(Uri.fromFile(same)));
   assertEquals(0,dup.stats().inserted);
   assertEquals(1,dup.stats().duplicate);
   assertTrue("source remains untouched on duplicate",same.exists());
   assertTrue("first managed copy survives duplicate",managed.exists());
 }
}