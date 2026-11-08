package com.maru.media;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
/** Real HTTP audio upload to a separately provisioned HTTPS /api/upload backend. */
public final class UploadClient {
 private UploadClient(){}
 private static void part(OutputStream out,String bound,String field,String value)throws IOException{
   String text="--"+bound+"\r\nContent-Disposition: form-data; name=\""+field+"\"\r\n\r\n"
     +value+"\r\n";
   out.write(text.getBytes(StandardCharsets.UTF_8));
 }
 private static String headerFilename(String name){
   if(name==null)return "audio.mp3";
   return name.replaceAll("[^A-Za-z0-9._-]","_").substring(0,Math.min(70,name.length()));
 }
 public static boolean upload(Context context,VaultDb.Track track,String baseUrl,String uploadToken)throws Exception{
   if(track==null||track.rightsOk!=1)throw new SecurityException("업로드 권한 미승인");
   if(uploadToken==null||uploadToken.trim().length()<10)throw new SecurityException("업로드 인증키가 필요합니다.");
   URI base=URI.create(baseUrl);
   if(!"https".equalsIgnoreCase(base.getScheme())||base.getHost()==null
        ||base.getUserInfo()!=null||base.getFragment()!=null)
     throw new SecurityException("HTTPS 서버 주소만 허용합니다.");
   String endpoint=baseUrl.replaceAll("/+$","")+"/api/upload";
   HttpsURLConnection con=(HttpsURLConnection)new URL(endpoint).openConnection();
   con.setConnectTimeout(20000);con.setReadTimeout(90000);con.setRequestMethod("POST");
   con.setDoOutput(true);
   con.setRequestProperty("X-MARU-ADMIN",uploadToken.trim());
   String boundary="MARU-"+java.util.UUID.randomUUID().toString();
   con.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
   con.setChunkedStreamingMode(65536);
   Uri uri=Uri.parse(track.uri);
   try{
     try(OutputStream out=con.getOutputStream()){
       part(out,boundary,"title",track.title);
       part(out,boundary,"sha256",track.sha);
       part(out,boundary,"language",track.language);
       part(out,boundary,"genre",track.genre);
       part(out,boundary,"rights_confirmed","true");
       String suffix="--"+boundary+"\r\nContent-Disposition: form-data; name=\"audio\"; filename=\""
        +headerFilename(track.filename)+"\"\r\nContent-Type: application/octet-stream\r\n\r\n";
       out.write(suffix.getBytes(StandardCharsets.UTF_8));
       try(InputStream in=context.getContentResolver().openInputStream(uri)){
         if(in==null)throw new IOException("원곡을 열 수 없습니다.");
         byte[] buf=new byte[65536];int n;
         while((n=in.read(buf))!=-1)out.write(buf,0,n);
       }
       out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
     }
     int code=con.getResponseCode();
     if(code==200||code==201)return true;
     String message="";
     try(InputStream err=con.getErrorStream()){
       if(err!=null){byte[] b=new byte[512];int n=err.read(b);if(n>0)message=new String(b,0,n,StandardCharsets.UTF_8);}
     }
     throw new IOException("서버 응답 "+code+" "+message);
   }finally{con.disconnect();}
 }
}