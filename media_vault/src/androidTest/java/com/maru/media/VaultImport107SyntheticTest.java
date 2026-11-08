package com.maru.media;
import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import static org.junit.Assert.*;

/** Uses tiny synthetic files only, never the user's private 105-song archive. */
@RunWith(AndroidJUnit4.class)
public class VaultImport107SyntheticTest {
 private static byte[] fakeMp3(int n){
   byte[] bytes=new byte[64];
   bytes[0]='I';bytes[1]='D';bytes[2]='3';
   bytes[12]=(byte)(n>>>24);bytes[13]=(byte)(n>>>16);
   bytes[14]=(byte)(n>>>8);bytes[15]=(byte)n;
   return bytes;
 }
 private static Uri write(File dir,String title,byte[] content)throws Exception{
   File file=new File(dir,title);
   try(FileOutputStream out=new FileOutputStream(file)){out.write(content);}
   return Uri.fromFile(file);
 }
 @Test public void imports105UniqueFrom107AndSkipsDisguisedFile()throws Exception{
   Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
   File folder=new File(context.getCacheDir(),"qa-import-"+System.nanoTime());
   assertTrue(folder.mkdirs());
   VaultDb db=new VaultDb(context);
   int before=db.count();
   ArrayList<Uri> files=new ArrayList<>();
   try{
     int salt=(int)System.nanoTime();
     for(int i=0;i<105;i++)files.add(write(folder,"original-"+i+".mp3",fakeMp3(salt+i)));
     files.add(write(folder,"same-original-10.mp3",fakeMp3(salt+10)));
     files.add(write(folder,"same-original-40.mp3",fakeMp3(salt+40)));
     byte[] fake=new byte[64];fake[0]='M';fake[1]='Z';
     files.add(write(folder,"unsafe-name.mp3",fake));
     LibraryImporter importer=new LibraryImporter(context,db,null);
     importer.fromAudioUris(files);
     LibraryImporter.Stats stats=importer.stats();
     assertEquals(108,stats.scanned);
     assertEquals(105,stats.inserted);
     assertEquals(2,stats.duplicate);
     assertEquals(1,stats.invalid);
     assertEquals(before+105,db.count());
     LibraryImporter second=new LibraryImporter(context,db,null);
     second.fromAudioUris(files.subList(0,107));
     assertEquals(0,second.stats().inserted);
     assertEquals(107,second.stats().duplicate);
     assertEquals(before+105,db.count());
   }finally{
     for(File f:Objects.requireNonNull(folder.listFiles()))f.delete();
     folder.delete();
     db.close();
   }
 }
}
