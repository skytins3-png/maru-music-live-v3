package com.maru.media;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

public class AudioHeaderTest {
 private File sample(String extension,byte[] data)throws Exception{
   File f=File.createTempFile("maru-test-",extension);
   try(FileOutputStream out=new FileOutputStream(f)){out.write(data);}
   f.deleteOnExit();
   return f;
 }
 @Test public void checksMP3AndRejectsDisguisedExecutable()throws Exception{
   byte[] mp3=new byte[40];mp3[0]='I';mp3[1]='D';mp3[2]='3';
   assertTrue(AudioHeader.valid(sample(".mp3",mp3),"track.mp3"));
   byte[] fake=new byte[40];fake[0]='M';fake[1]='Z';
   assertFalse(AudioHeader.valid(sample(".mp3",fake),"malware.mp3"));
 }
 @Test public void checksM4AAndWAV()throws Exception{
   byte[] m4a=new byte[40];m4a[4]='f';m4a[5]='t';m4a[6]='y';m4a[7]='p';
   assertTrue(AudioHeader.valid(sample(".m4a",m4a),"sound.m4a"));
   byte[] wav=new byte[40];
   System.arraycopy("RIFF".getBytes("US-ASCII"),0,wav,0,4);
   System.arraycopy("WAVE".getBytes("US-ASCII"),0,wav,8,4);
   assertTrue(AudioHeader.valid(sample(".wav",wav),"sound.wav"));
   assertFalse(AudioHeader.valid(sample(".mp3",wav),"sound.mp3"));
 }
 @Test public void rejectsUnknownExtensions()throws Exception{
   try{AudioHeader.extension("installer.apk");fail("must reject");}
   catch(IllegalArgumentException expected){}
 }
}
