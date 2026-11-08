package com.maru.media;
import java.io.*;
import java.util.Locale;

/** Basic media signature check. Does not replace a malware scanner or decode verification. */
public final class AudioHeader {
 private AudioHeader(){}
 public static String extension(String name){
   if(name==null)throw new IllegalArgumentException("filename");
   String lower=name.toLowerCase(Locale.ROOT);
   if(lower.endsWith(".wav"))return ".wav";
   if(lower.endsWith(".m4a"))return ".m4a";
   if(lower.endsWith(".mp3"))return ".mp3";
   throw new IllegalArgumentException("Unsupported media extension");
 }
 private static boolean ascii(byte[] b,int offset,String word){
   if(b.length<offset+word.length())return false;
   for(int i=0;i<word.length();i++)if((b[offset+i]&255)!=(int)word.charAt(i))return false;
   return true;
 }
 public static boolean valid(File file,String originalName)throws IOException{
   if(file==null||!file.isFile()||file.length()<12)return false;
   byte[] header=new byte[12];int got=0;
   try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
     while(got<header.length){
       int n=in.read(header,got,header.length-got);
       if(n<0)break;
       got+=n;
     }
   }
   if(got<12)return false;
   String ext=extension(originalName);
   if(".wav".equals(ext))return ascii(header,0,"RIFF")&&ascii(header,8,"WAVE");
   if(".m4a".equals(ext))return ascii(header,4,"ftyp");
   // MP3 may begin with an ID3v2 tag or with an MPEG audio-frame sync.
   return ascii(header,0,"ID3")||((header[0]&255)==255&&
     (header[1]&0xe0)==0xe0&&(header[1]&0x18)!=0x08&&(header[1]&0x06)!=0);
 }
}
