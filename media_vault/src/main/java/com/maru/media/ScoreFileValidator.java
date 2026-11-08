package com.maru.media;
import java.io.*;

/** Small signature validator for PDF/photo scores; not a malware scanner. */
public final class ScoreFileValidator {
 private ScoreFileValidator(){}
 public static final long MAX_BYTES=40L*1024*1024;
 public static String identify(byte[] head){
   if(head==null||head.length<12)return "";
   if(prefix(head,0,"%PDF-"))return "application/pdf";
   if((head[0]&255)==0x89 && prefix(head,1,"PNG\r\n\u001a\n"))return "image/png";
   if((head[0]&255)==0xff&&(head[1]&255)==0xd8&&(head[2]&255)==0xff)return "image/jpeg";
   if(prefix(head,0,"RIFF")&&prefix(head,8,"WEBP"))return "image/webp";
   return "";
 }
 private static boolean prefix(byte[] b,int at,String p){
   if(at+p.length()>b.length)return false;
   for(int i=0;i<p.length();i++)if((b[at+i]&255)!=p.charAt(i))return false;
   return true;
 }
 /** Consumes and closes no caller stream. Disallow empty, overlarge, or disguised files. */
 public static String validate(InputStream input)throws IOException{
   if(input==null)throw new IOException("악보 파일을 읽을 수 없습니다.");
   byte[] first=new byte[16];int filled=0;long total=0;
   byte[] buf=new byte[65536];int n;
   while((n=input.read(buf))!=-1){
     total+=n;if(total>MAX_BYTES)throw new IOException("악보 최대 크기 40MB를 초과했습니다.");
     for(int i=0;i<n&&filled<first.length;i++)first[filled++]=buf[i];
   }
   if(total<12)throw new IOException("빈 악보 파일입니다.");
   String mime=identify(first);
   if(mime.isEmpty())throw new IOException("PDF·JPG·PNG·WEBP 악보 파일만 등록할 수 있습니다.");
   return mime;
 }
}