package com.maru.media;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
public final class VaultDb extends SQLiteOpenHelper {
 public static final String TRACK_FIELDS="_id,title,filename,uri,sha,size,language,genre,artist,score_uri,views,liked,rights_ok,upload_state";
 public VaultDb(Context ctx){super(ctx,"maru_media_private_v1.db",null,1);}
 @Override public void onCreate(SQLiteDatabase d){
   d.execSQL("CREATE TABLE tracks(_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,filename TEXT,uri TEXT NOT NULL,sha TEXT NOT NULL UNIQUE,size INTEGER DEFAULT 0,language TEXT NOT NULL,genre TEXT NOT NULL DEFAULT '미확인',artist TEXT DEFAULT '',score_uri TEXT DEFAULT '',views INTEGER DEFAULT 0,liked INTEGER DEFAULT 0,rights_ok INTEGER DEFAULT 0,upload_state TEXT DEFAULT 'LOCAL')");
   d.execSQL("CREATE INDEX idx_tracks_language ON tracks(language)");
   d.execSQL("CREATE TABLE comments(_id INTEGER PRIMARY KEY AUTOINCREMENT,track_id INTEGER NOT NULL,author TEXT,body TEXT,created_at INTEGER)");
   d.execSQL("CREATE TABLE stories(_id INTEGER PRIMARY KEY AUTOINCREMENT,author TEXT,body TEXT,attachment_uri TEXT,status TEXT NOT NULL DEFAULT '검토대기',created_at INTEGER)");
 }
 @Override public void onUpgrade(SQLiteDatabase d,int oldVersion,int newVersion){}
 public synchronized long insertTrack(String title,String fileName,String uri,String sha,long size,String language){
   if(!CatalogLogic.sha256(sha))throw new IllegalArgumentException("sha256");
   ContentValues v=new ContentValues();
   v.put("title",CatalogLogic.cleanText(title,150));v.put("filename",CatalogLogic.cleanText(fileName,160));
   v.put("uri",uri);v.put("sha",sha);v.put("size",size);
   v.put("language",language);v.put("genre","미확인");
   return getWritableDatabase().insertWithOnConflict("tracks",null,v,SQLiteDatabase.CONFLICT_IGNORE);
 }
 public synchronized List<Track> tracks(String filter){
   ArrayList<Track> result=new ArrayList<>();
   String where="전체".equals(filter)||filter==null?null:"(language=? OR genre=?)";
   String[] args=where==null?null:new String[]{filter,filter};
   try(Cursor c=getReadableDatabase().query("tracks",TRACK_FIELDS.split(","),where,args,null,null,"_id ASC")){
     while(c.moveToNext())result.add(new Track(c));
   }
   return result;
 }
 public synchronized Track get(long id){
   try(Cursor c=getReadableDatabase().query("tracks",TRACK_FIELDS.split(","),"_id=?",new String[]{String.valueOf(id)},null,null,null)){
     return c.moveToFirst()?new Track(c):null;
   }
 }
 public synchronized boolean bySha(String sha){
   try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM tracks WHERE sha=? LIMIT 1",new String[]{sha})){return c.moveToFirst();}
 }
 public synchronized void update(long id,String field,String value){
   if(!Arrays.asList("title","language","genre","artist","score_uri","upload_state").contains(field))throw new IllegalArgumentException("field");
   ContentValues v=new ContentValues();v.put(field,CatalogLogic.cleanText(value,250));getWritableDatabase().update("tracks",v,"_id=?",new String[]{String.valueOf(id)});
 }
 public synchronized void flag(long id,String field,int value){
   if(!Arrays.asList("liked","rights_ok").contains(field))throw new IllegalArgumentException("flag");
   ContentValues v=new ContentValues();v.put(field,value);getWritableDatabase().update("tracks",v,"_id=?",new String[]{String.valueOf(id)});
 }
 public synchronized void view(long id){getWritableDatabase().execSQL("UPDATE tracks SET views=views+1 WHERE _id=?",new Object[]{id});}
 public synchronized void approvedAll(boolean approved){
   ContentValues v=new ContentValues();v.put("rights_ok",approved?1:0);getWritableDatabase().update("tracks",v,null,null);
 }
 public synchronized long addComment(long track,String author,String body){
   ContentValues v=new ContentValues();v.put("track_id",track);
   v.put("author",CatalogLogic.cleanText(author,30));v.put("body",CatalogLogic.cleanText(body,700));
   v.put("created_at",System.currentTimeMillis());
   return getWritableDatabase().insert("comments",null,v);
 }
 public synchronized List<String> comments(long track){
   ArrayList<String> result=new ArrayList<>();
   try(Cursor c=getReadableDatabase().rawQuery("SELECT author,body FROM comments WHERE track_id=? ORDER BY _id DESC LIMIT 40",new String[]{String.valueOf(track)})){
     while(c.moveToNext())result.add(c.getString(0)+" : "+c.getString(1));
   }
   return result;
 }
 public synchronized long addStory(String author,String body,String uri){
   ContentValues v=new ContentValues();v.put("author",CatalogLogic.cleanText(author,30));
   v.put("body",CatalogLogic.cleanText(body,2800));v.put("attachment_uri",uri==null?"":uri);
   v.put("created_at",System.currentTimeMillis());
   return getWritableDatabase().insert("stories",null,v);
 }
 public synchronized void storyStatus(long id,String status){
   if(!Arrays.asList("검토대기","승인","거절","방송완료").contains(status))throw new IllegalArgumentException("status");
   ContentValues v=new ContentValues();v.put("status",status);getWritableDatabase().update("stories",v,"_id=?",new String[]{String.valueOf(id)});
 }
 public synchronized List<Story> stories(){
   ArrayList<Story> out=new ArrayList<>();
   try(Cursor c=getReadableDatabase().rawQuery("SELECT _id,author,body,attachment_uri,status FROM stories ORDER BY _id DESC LIMIT 300",null)){
     while(c.moveToNext())out.add(new Story(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)));
   }
   return out;
 }
 public synchronized int count(){
   try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tracks",null)){c.moveToFirst();return c.getInt(0);}
 }
 public synchronized int uploaded(){
   try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tracks WHERE upload_state='UPLOADED'",null)){c.moveToFirst();return c.getInt(0);}
 }
 /** Private on-device manifest: never commits user tracks to public source. */
 public synchronized int importManifest(String json){
   int changed=0;
   try{
     JSONObject obj=new JSONObject(json);
     JSONArray tracks=obj.optJSONArray("tracks");
     if(tracks==null||tracks.length()>300)throw new IllegalArgumentException("bad manifest");
     SQLiteDatabase d=getWritableDatabase();
     for(int i=0;i<tracks.length();i++){
       JSONObject t=tracks.optJSONObject(i);if(t==null)continue;
       String sha=t.optString("sha256","");
       if(!CatalogLogic.sha256(sha))continue;
       String g=t.optString("languageGroup","검토필요");
       if(!Arrays.asList(CatalogLogic.LANGUAGES).contains(g))g="검토필요";
       ContentValues cv=new ContentValues();
       cv.put("language",g);
       cv.put("title",CatalogLogic.cleanText(t.optString("title",t.optString("filename","")),150));
       changed+=d.update("tracks",cv,"sha=?",new String[]{sha});
     }
   }catch(JSONException e){throw new IllegalArgumentException("JSON 읽기 실패",e);}
   return changed;
 }
 public static final class Track {
   public final long id,size;public final String title,filename,uri,sha,language,genre,artist,scoreUri,uploadState;
   public final int views,liked,rightsOk;
   Track(Cursor c){id=c.getLong(0);title=c.getString(1);filename=c.getString(2);uri=c.getString(3);sha=c.getString(4);size=c.getLong(5);
     language=c.getString(6);genre=c.getString(7);artist=c.getString(8);scoreUri=c.getString(9);views=c.getInt(10);liked=c.getInt(11);rightsOk=c.getInt(12);uploadState=c.getString(13);}
 }
 public static final class Story {
   public final long id;public final String author,body,attachment,status;
   Story(long id,String author,String body,String attachment,String status){this.id=id;this.author=author;this.body=body;this.attachment=attachment;this.status=status;}
 }
}