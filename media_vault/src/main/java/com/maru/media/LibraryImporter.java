package com.maru.media;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Import from local media folder / standard ZIP. Never deletes source files. */
public final class LibraryImporter {
 public interface Progress { void update(int scanned,int inserted,int duplicated,String name); }
 public static final class Stats {
   public int scanned,inserted,duplicate,invalid,manifestUpdated;
   public String last="";
 }
 private final Context context;
 private final VaultDb db;
 private final Progress progress;
 private final Stats stats=new Stats();
 private static final int MAX_TRACKS=1500;
 private static final long MAX_AUDIO_BYTES=55L*1024*1024;
 private static final long MAX_ARCHIVE_BYTES=1300L*1024*1024;
 private long archiveBytes=0;
 public LibraryImporter(Context ctx,VaultDb db,Progress progress){
   this.context=ctx.getApplicationContext();this.db=db;this.progress=progress;
 }
 public Stats stats(){return stats;}
 private void update(String name){
   stats.last=name;
   if(progress!=null)progress.update(stats.scanned,stats.inserted,stats.duplicate,name);
 }
 /**
  * One-pass import, not merely a temporary content:// pointer. The user's original
  * source is left untouched; a private managed file survives source removal.
  */
 private void importManaged(String name,Uri original)throws Exception{
   File directory=new File(context.getFilesDir(),"music");
   if(!directory.exists()&&!directory.mkdirs())throw new IOException("음원 보관 폴더 생성 실패");
   String extension=name.toLowerCase(Locale.ROOT).endsWith(".m4a")?".m4a":".mp3";
   String uuid=UUID.randomUUID().toString();
   File temp=new File(directory,uuid+".part");
   File target=new File(directory,uuid+extension);
   long count=0;
   boolean retained=false;
   String sha;
   try{
     MessageDigest digest=MessageDigest.getInstance("SHA-256");
     try(InputStream in=new BufferedInputStream(context.getContentResolver().openInputStream(original));
         OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
       if(in==null)throw new IOException("원본 파일 열기 실패");
       byte[] buf=new byte[65536];int n;
       while((n=in.read(buf))!=-1){
         count+=n;
         if(count>MAX_AUDIO_BYTES)throw new IOException("음원은 55MB 이하만 등록할 수 있습니다.");
         out.write(buf,0,n);digest.update(buf,0,n);
       }
     }
     if(count<=0)throw new IOException("빈 음원은 등록할 수 없습니다.");
     sha=CatalogLogic.hashHex(digest.digest());
     if(db.bySha(sha)){stats.duplicate++;return;}
     if(!temp.renameTo(target))throw new IOException("보관 파일 확정 실패");
     long added=db.insertTrack(CatalogLogic.base(name),name,Uri.fromFile(target).toString(),sha,count,CatalogLogic.group(name));
     if(added<0){stats.duplicate++;return;}
     retained=true;
     stats.inserted++;
   }finally{
     if(temp.exists())temp.delete();
     if(!retained&&target.exists())target.delete();
   }
 }
 public void fromAudioUris(List<Uri> files){
   for(Uri uri:files){
     if(stats.scanned>=MAX_TRACKS)break;
     String name=displayName(uri);
     try{
       if(CatalogLogic.zip(name))fromZip(uri);
       else if(CatalogLogic.audio(name)){
         stats.scanned++;
         importManaged(name,uri);
       }
     }catch(Exception ex){stats.invalid++;}
     update(name);
   }
 }
 public void fromTree(Uri tree)throws Exception{
   String rootId=DocumentsContract.getTreeDocumentId(tree);
   traverse(tree,rootId,0);
 }
 private void traverse(Uri tree,String parentId,int depth)throws Exception{
   if(depth>7 || stats.scanned>=MAX_TRACKS)return;
   Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId);
   try(Cursor cur=context.getContentResolver().query(children,new String[]{
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE},null,null,null)){
     if(cur==null)throw new IOException("폴더 목록을 읽을 수 없습니다");
     while(cur.moveToNext()&&stats.scanned<MAX_TRACKS){
       String id=cur.getString(0),name=cur.getString(1),mime=cur.getString(2);
       Uri child=DocumentsContract.buildDocumentUriUsingTree(tree,id);
       if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){traverse(tree,id,depth+1);continue;}
       if(!CatalogLogic.audio(name)&&!CatalogLogic.zip(name))continue;
       try{
         if(CatalogLogic.zip(name))fromZip(child);
         else{
           stats.scanned++;
           importManaged(name,child);
         }
       }catch(Exception e){stats.invalid++;}
       update(name);
     }
   }
 }
 private void fromZip(Uri zipUri)throws Exception{
   File directory=new File(context.getFilesDir(),"music");
   if(!directory.exists()&&!directory.mkdirs())throw new IOException("파일 공간 없음");
   try(InputStream raw=context.getContentResolver().openInputStream(zipUri)){
     if(raw==null)throw new IOException("ZIP을 열 수 없습니다");
     try(ZipInputStream z=new ZipInputStream(new BufferedInputStream(raw))){
       ZipEntry entry;
       String catalogJson=null;
       while((entry=z.getNextEntry())!=null&&stats.scanned<MAX_TRACKS){
         if(entry.isDirectory())continue;
         String original=entry.getName().replace('\\','/');
         if("MARU_CATALOG.json".equalsIgnoreCase(original)){
           ByteArrayOutputStream json=new ByteArrayOutputStream();
           byte[] buffer=new byte[8192];int n;
           while((n=z.read(buffer))!=-1){
             json.write(buffer,0,n);
             if(json.size()>350000)throw new IOException("분류표 JSON 350KB 제한");
           }
           catalogJson=json.toString("UTF-8");
           z.closeEntry();continue;
         }
         String name=original.substring(original.lastIndexOf('/')+1);
         if(!CatalogLogic.audio(name))continue;
         stats.scanned++;
         String ext=name.toLowerCase(Locale.ROOT).endsWith(".m4a")?".m4a":".mp3";
         File target=new File(directory,UUID.randomUUID().toString()+ext);
         MessageDigest digest=MessageDigest.getInstance("SHA-256");
         long copied=0;
         try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
           byte[] buf=new byte[65536];int n;
           while((n=z.read(buf))!=-1){
             copied+=n;archiveBytes+=n;
             if(copied>MAX_AUDIO_BYTES||archiveBytes>MAX_ARCHIVE_BYTES)throw new IOException("ZIP 크기 제한");
             digest.update(buf,0,n);out.write(buf,0,n);
           }
         }catch(Exception e){target.delete();stats.invalid++;update(name);continue;}
         String hex=CatalogLogic.hashHex(digest.digest());
         if(db.bySha(hex)){target.delete();stats.duplicate++;}
         else{
           long added=db.insertTrack(CatalogLogic.base(name),name,Uri.fromFile(target).toString(),hex,copied,CatalogLogic.group(name));
           if(added<0){target.delete();stats.duplicate++;}else stats.inserted++;
         }
         z.closeEntry();update(name);
       }
       if(catalogJson!=null){
         try{stats.manifestUpdated+=db.importManifest(catalogJson);}
         catch(IllegalArgumentException ex){stats.invalid++;}
       }
     }
   }
 }
 private long size(Uri uri){
   try(Cursor c=context.getContentResolver().query(uri,new String[]{DocumentsContract.Document.COLUMN_SIZE},null,null,null)){
     if(c!=null&&c.moveToFirst()&&!c.isNull(0))return c.getLong(0);
   }catch(Exception ignored){}
   return 0;
 }
 private String displayName(Uri uri){
   try(Cursor c=context.getContentResolver().query(uri,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
     if(c!=null&&c.moveToFirst())return c.getString(0);
   }catch(Exception ignored){}
   return uri.getLastPathSegment()==null?"audio.mp3":uri.getLastPathSegment();
 }
}