package com.maru.musiclive;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pulls only creator-authorized tracks from the fixed MARU public catalog.
 * Never fetches a listener's unreviewed private submissions or Suno account pages.
 * Original bytes are kept untouched; caller must ask user to select the song.
 */
public final class PublicCatalogImporter {
    public static final String BASE="https://skytins3-png.github.io/maru-ai-song-studio/music-share/";
    private static final int MAX_CATALOG_BYTES=1_000_000;
    private static final long MAX_AUDIO_BYTES=95L*1024*1024;
    public static final class Entry {
        public final String title,artist,path,region,style,source;
        Entry(String title,String artist,String path,String region,String style,String source){
            this.title=title;this.artist=artist;this.path=path;
            this.region=region;this.style=style;this.source=source;
        }
    }
    private PublicCatalogImporter() {}
    public static boolean trustedRelativeAudio(String path) {
        return path!=null && path.matches(
          "tracks/[A-Za-z0-9가-힣_-]{1,150}\\.(?i:mp3|wav|m4a|aac|ogg|flac)");
    }
    public static List<Entry> parseCatalog(String json) throws Exception {
        JSONArray list=new JSONArray(json);
        List<Entry> accepted=new ArrayList<>();
        for(int i=0;i<list.length()&&accepted.size()<300;i++){
            JSONObject row=list.optJSONObject(i);
            if(row==null||!row.optBoolean("downloadAllowed",false)
                    ||!row.optBoolean("rightsAttested",false))continue;
            String path=row.optString("audio","");
            if(!trustedRelativeAudio(path))continue;
            String source=row.optString("source","owner-original");
            // Public listener suggestions are NOT approved merely by claiming rights.
            if("listener-approved".equals(source)){
                if(!row.optBoolean("producerApproved",false)
                        ||!row.optBoolean("securityVerified",false)
                        ||!row.optBoolean("rightsVerified",false))continue;
            }else if(!"owner-original".equals(source))continue;
            String title=row.optString("title","");
            if(title.trim().isEmpty())continue;
            String region=row.optString("genre","");
            String style=row.optString("style","");
            if(!MusicCollection.isRegion(region))region=MusicCollection.detectRegion(title);
            if(!MusicCollection.isStyle(style))style=MusicCollection.detectStyle(title);
            accepted.add(new Entry(title,row.optString("artist","MARU"),path,
                region,style,"listener-approved".equals(source)
                    ?MusicCollection.LISTENER:MusicCollection.PLATFORM));
        }
        return accepted;
    }
    private static HttpURLConnection open(String url) throws Exception {
        URL parsed=new URL(url);
        if(!"https".equals(parsed.getProtocol())
                ||!"skytins3-png.github.io".equalsIgnoreCase(parsed.getHost()))
            throw new java.io.IOException("허용되지 않은 음악 서버 주소");
        HttpURLConnection connection=(HttpURLConnection)parsed.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept","application/octet-stream,application/json");
        if(connection.getResponseCode()!=200){
            connection.disconnect();
            throw new java.io.IOException("공개 음악 서버 응답 오류");
        }
        return connection;
    }
    private static byte[] readLimited(InputStream in,int limit) throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){
            if(out.size()+n>limit)throw new java.io.IOException("음악 목록이 너무 큽니다.");
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }
    public static List<Entry> fetch() throws Exception {
        HttpURLConnection c=open(BASE+"catalog.json");
        try(InputStream in=c.getInputStream()){
            return parseCatalog(new String(readLimited(in,MAX_CATALOG_BYTES),
                    java.nio.charset.StandardCharsets.UTF_8));
        }finally{c.disconnect();}
    }
    public static String download(Context context,Entry e) throws Exception {
        if(e==null||!trustedRelativeAudio(e.path))throw new java.io.IOException("음원 경로 오류");
        String encoded=android.net.Uri.encode(e.path,"/-_.");
        HttpURLConnection c=open(BASE+encoded);
        File dir=new File(context.getFilesDir(),"maru_public_originals");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("보관함 생성 실패");
        File tmp=File.createTempFile("pending-",".tmp",dir);
        long total=0;byte[] start=new byte[16];int head=0;
        try{
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){
                byte[] buf=new byte[64*1024];int n;
                while((n=in.read(buf))!=-1){
                    if(n==0)continue;
                    total+=n;
                    if(total>MAX_AUDIO_BYTES)throw new java.io.IOException("95MB 초과 음원");
                    for(int i=0;i<n&&head<start.length;i++)start[head++]=buf[i];
                    sha.update(buf,0,n);out.write(buf,0,n);
                }
                out.flush();out.getFD().sync();
            }
            if(total<16||!validMagic(e.path,start))throw new java.io.IOException("음원 형식 검사 실패");
            String digest=hex(sha.digest());
            String extension=e.path.substring(e.path.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            File[] files=dir.listFiles();
            if(files!=null)for(File f:files){
                if(f.isFile()&&f.getName().startsWith(digest+"--"))
                    return Uri.fromFile(f).toString();
            }
            File finalFile=new File(dir,digest+"--"+safeName(e.title)+extension);
            if(!tmp.renameTo(finalFile))throw new java.io.IOException("원본 저장 실패");
            return Uri.fromFile(finalFile).toString();
        }finally{
            c.disconnect();
            if(tmp.exists())tmp.delete();
        }
    }
    private static String safeName(String name){
        String s=name.replaceAll("[^가-힣A-Za-z0-9_-]","-").replaceAll("-+","-");
        return s.isEmpty()?"MARU":s.substring(0,Math.min(55,s.length()));
    }
    static boolean validMagic(String path,byte[] b){
        path = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if(path.endsWith(".mp3"))return eq(b,0,"ID3")||(b[0]&255)==255&&((b[1]&224)==224);
        if(path.endsWith(".wav"))return eq(b,0,"RIFF")&&eq(b,8,"WAVE");
        if(path.endsWith(".flac"))return eq(b,0,"fLaC");
        if(path.endsWith(".ogg"))return eq(b,0,"OggS");
        if(path.endsWith(".m4a"))return eq(b,4,"ftyp");
        if(path.endsWith(".aac"))return (b[0]&255)==255&&((b[1]&240)==240);
        return false;
    }
    private static boolean eq(byte[] b,int at,String value) {
        if(b==null||b.length<at+value.length())return false;
        for(int i=0;i<value.length();i++)if((b[at+i]&255)!=value.charAt(i))return false;
        return true;
    }
    private static String hex(byte[] bytes){
        StringBuilder s=new StringBuilder();
        for(byte b:bytes)s.append(Character.forDigit((b>>4)&15,16))
                .append(Character.forDigit(b&15,16));
        return s.toString();
    }
}
