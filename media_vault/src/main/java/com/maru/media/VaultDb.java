package com.maru.media;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
public final class VaultDb extends SQLiteOpenHelper {
 public static final String TRACK_FIELDS="_id,title,filename,uri,sha,size,language,genre,artist,score_uri,views,liked,rights_ok,upload_state,cover_uri,lyricist,composer,style_finisher,suno_assist,is_original,artist_group_auto";
 public VaultDb(Context ctx){super(ctx,"maru_media_private_v1.db",null,5);}
 @Override public void onCreate(SQLiteDatabase d){
   d.execSQL("CREATE TABLE tracks(_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,filename TEXT,uri TEXT NOT NULL,sha TEXT NOT NULL UNIQUE,size INTEGER DEFAULT 0,language TEXT NOT NULL,genre TEXT NOT NULL DEFAULT '미확인',artist TEXT DEFAULT '',score_uri TEXT DEFAULT '',views INTEGER DEFAULT 0,liked INTEGER DEFAULT 0,rights_ok INTEGER DEFAULT 0,upload_state TEXT DEFAULT 'LOCAL',cover_uri TEXT DEFAULT '',lyricist TEXT DEFAULT '',composer TEXT DEFAULT '',style_finisher TEXT DEFAULT '',suno_assist TEXT DEFAULT '',is_original INTEGER DEFAULT 0,artist_group_auto INTEGER DEFAULT 0)");
   d.execSQL("CREATE INDEX idx_tracks_language ON tracks(language)");
   d.execSQL("CREATE TABLE comments(_id INTEGER PRIMARY KEY AUTOINCREMENT,track_id INTEGER NOT NULL,author TEXT,body TEXT,created_at INTEGER)");
   d.execSQL("CREATE TABLE stories(_id INTEGER PRIMARY KEY AUTOINCREMENT,author TEXT,body TEXT,attachment_uri TEXT,status TEXT NOT NULL DEFAULT '검토대기',created_at INTEGER, views INTEGER DEFAULT 0, liked INTEGER DEFAULT 0, scheduled_at TEXT DEFAULT '')");
   d.execSQL("CREATE TABLE story_comments(_id INTEGER PRIMARY KEY AUTOINCREMENT,story_id INTEGER NOT NULL,parent_id INTEGER DEFAULT 0,author TEXT,body TEXT,created_at INTEGER)");
   d.execSQL("CREATE INDEX idx_story_comments_story ON story_comments(story_id)");
   d.execSQL("CREATE TABLE song_comment_replies(_id INTEGER PRIMARY KEY AUTOINCREMENT,track_id INTEGER NOT NULL,parent_id INTEGER NOT NULL,author TEXT,body TEXT,created_at INTEGER)");
   d.execSQL("CREATE TABLE candidate_reviews(candidate_idx INTEGER PRIMARY KEY, rating_1 INTEGER NOT NULL DEFAULT 0, rating_2 INTEGER NOT NULL DEFAULT 0, chosen_version INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0)");
 }
 @Override public void onUpgrade(SQLiteDatabase d,int oldVersion,int newVersion){
   if(oldVersion<2){
     d.execSQL("ALTER TABLE stories ADD COLUMN views INTEGER DEFAULT 0");
     d.execSQL("ALTER TABLE stories ADD COLUMN liked INTEGER DEFAULT 0");
     d.execSQL("ALTER TABLE stories ADD COLUMN scheduled_at TEXT DEFAULT ''");
     d.execSQL("CREATE TABLE IF NOT EXISTS story_comments(_id INTEGER PRIMARY KEY AUTOINCREMENT,story_id INTEGER NOT NULL,parent_id INTEGER DEFAULT 0,author TEXT,body TEXT,created_at INTEGER)");
     d.execSQL("CREATE INDEX IF NOT EXISTS idx_story_comments_story ON story_comments(story_id)");
     d.execSQL("CREATE TABLE IF NOT EXISTS song_comment_replies(_id INTEGER PRIMARY KEY AUTOINCREMENT,track_id INTEGER NOT NULL,parent_id INTEGER NOT NULL,author TEXT,body TEXT,created_at INTEGER)");
   }
   if(oldVersion<3)d.execSQL("ALTER TABLE tracks ADD COLUMN cover_uri TEXT DEFAULT ''");
   if(oldVersion<4){
     d.execSQL("ALTER TABLE tracks ADD COLUMN lyricist TEXT DEFAULT ''");
     d.execSQL("ALTER TABLE tracks ADD COLUMN composer TEXT DEFAULT ''");
     d.execSQL("ALTER TABLE tracks ADD COLUMN style_finisher TEXT DEFAULT ''");
     d.execSQL("ALTER TABLE tracks ADD COLUMN suno_assist TEXT DEFAULT ''");
     d.execSQL("ALTER TABLE tracks ADD COLUMN is_original INTEGER DEFAULT 0");
     d.execSQL("ALTER TABLE tracks ADD COLUMN artist_group_auto INTEGER DEFAULT 0");
   }
  if(oldVersion<5)d.execSQL("CREATE TABLE IF NOT EXISTS candidate_reviews(candidate_idx INTEGER PRIMARY KEY, rating_1 INTEGER NOT NULL DEFAULT 0, rating_2 INTEGER NOT NULL DEFAULT 0, chosen_version INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0)");
 }
 /**
  * Host-only comparison of two Suno candidate versions. Ratings are user's subjective
  * audition results, NEVER audio analysis or proof of a Suno download. A tie is held.
  * This table contains no user account secrets or external song audio.
  */
 public synchronized String rateCandidate(int idx,int version,int stars){
   if(idx<0||idx>=9 || (version!=1&&version!=2) || stars<1||stars>5)
     throw new IllegalArgumentException("잘못된 평가 값입니다.");
   SQLiteDatabase d=getWritableDatabase();
   d.execSQL("INSERT OR IGNORE INTO candidate_reviews(candidate_idx) VALUES(?)",new Object[]{idx});
   ContentValues value=new ContentValues();
   value.put(version==1?"rating_1":"rating_2",stars);
   value.put("updated_at",System.currentTimeMillis());
   d.update("candidate_reviews",value,"candidate_idx=?",new String[]{String.valueOf(idx)});
   try(Cursor c=d.rawQuery("SELECT rating_1,rating_2 FROM candidate_reviews WHERE candidate_idx=?",
       new String[]{String.valueOf(idx)})){
     if(!c.moveToFirst())throw new IllegalStateException("평가 저장 실패");
     int a=c.getInt(0),b=c.getInt(1);
     int winner=a>0&&b>0?(a>b?1:b>a?2:0):0;
     ContentValues chosen=new ContentValues();
     chosen.put("chosen_version",winner);
     d.update("candidate_reviews",chosen,"candidate_idx=?",new String[]{String.valueOf(idx)});
     return winner==0?(a>0&&b>0?"평점 동점입니다. 어느 한 버전이 더 좋은지 다시 평가하세요.":"다른 버전도 평가하면 우수 버전을 표시합니다.")
       :"두 버전 비교 완료 · 우선 선택: "+winner+"번";
   }
 }
 public synchronized JSONObject candidateReviews(){
   JSONObject reviews=new JSONObject();
   try(Cursor c=getReadableDatabase().rawQuery(
       "SELECT candidate_idx,rating_1,rating_2,chosen_version FROM candidate_reviews ORDER BY candidate_idx",null)){
     while(c.moveToNext()){
       try{
         JSONObject item=new JSONObject();
         item.put("v1",c.getInt(1));item.put("v2",c.getInt(2));item.put("winner",c.getInt(3));
         reviews.put(String.valueOf(c.getInt(0)),item);
       }catch(JSONException e){throw new IllegalStateException(e);}
     }
   }
   return reviews;
 }
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
   if(!Arrays.asList("title","language","genre","artist","score_uri","upload_state","cover_uri","lyricist","composer","style_finisher","suno_assist").contains(field))throw new IllegalArgumentException("field");
   ContentValues v=new ContentValues();
   if("score_uri".equals(field)||"cover_uri".equals(field)){
     if(value==null||value.length()>4096)throw new IllegalArgumentException("URI exceeds limit");
     android.net.Uri uri=android.net.Uri.parse(value);
     if(!"content".equals(uri.getScheme())&&!"file".equals(uri.getScheme()))
       throw new IllegalArgumentException("Unsupported URI");
     v.put(field,value);
   }else v.put(field,CatalogLogic.cleanText(value,250));
   getWritableDatabase().update("tracks",v,"_id=?",new String[]{String.valueOf(id)});
 }
 public synchronized void flag(long id,String field,int value){
   if(!Arrays.asList("liked","rights_ok","is_original","artist_group_auto").contains(field))throw new IllegalArgumentException("flag");
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
 public synchronized List<Comment> songCommentDetails(long track){
   ArrayList<Comment> result=new ArrayList<>();
   try(Cursor c=getReadableDatabase().rawQuery("SELECT _id,author,body FROM comments WHERE track_id=? ORDER BY _id DESC LIMIT 40",new String[]{String.valueOf(track)})){
     while(c.moveToNext())result.add(new Comment(c.getLong(0),0,c.getString(1),c.getString(2)));
   }
   return result;
 }
 public synchronized long addSongReply(long track,long parent,String author,String body){
   ContentValues v=new ContentValues();v.put("track_id",track);v.put("parent_id",parent);
   v.put("author",CatalogLogic.cleanText(author,30));v.put("body",CatalogLogic.cleanText(body,700));
   v.put("created_at",System.currentTimeMillis());
   return getWritableDatabase().insert("song_comment_replies",null,v);
 }
 public synchronized List<Comment> songReplies(long track){
   List<Comment> result=new ArrayList<>();
   try(Cursor c=getReadableDatabase().rawQuery("SELECT _id,parent_id,author,body FROM song_comment_replies WHERE track_id=? ORDER BY _id ASC LIMIT 80",new String[]{String.valueOf(track)})){
     while(c.moveToNext())result.add(new Comment(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3)));
   }return result;
 }
 public synchronized long addStory(String author,String body,String uri){
   ContentValues v=new ContentValues();v.put("author",CatalogLogic.cleanText(author,30));
   v.put("body",CatalogLogic.cleanText(body,2800));v.put("attachment_uri",uri==null?"":uri);
   v.put("created_at",System.currentTimeMillis());
   return getWritableDatabase().insert("stories",null,v);
 }
 public synchronized void storyStatus(long id,String status){
   if(!Arrays.asList("검토대기","승인","거절","예약","방송완료","보관").contains(status))throw new IllegalArgumentException("status");
   ContentValues v=new ContentValues();v.put("status",status);getWritableDatabase().update("stories",v,"_id=?",new String[]{String.valueOf(id)});
 }
 public synchronized void storyLike(long id,boolean liked){
   ContentValues v=new ContentValues();v.put("liked",liked?1:0);
   getWritableDatabase().update("stories",v,"_id=?",new String[]{String.valueOf(id)});
 }
 public synchronized void storyViewed(long id){
   getWritableDatabase().execSQL("UPDATE stories SET views=views+1 WHERE _id=?",new Object[]{id});
 }
 public synchronized void scheduleStory(long id,String when){
   ContentValues v=new ContentValues();
   v.put("scheduled_at",CatalogLogic.cleanText(when,45));v.put("status","예약");
   getWritableDatabase().update("stories",v,"_id=?",new String[]{String.valueOf(id)});
 }
 public synchronized long addStoryComment(long id,long parent,String name,String body){
   ContentValues v=new ContentValues();v.put("story_id",id);v.put("parent_id",parent);
   v.put("author",CatalogLogic.cleanText(name,30));v.put("body",CatalogLogic.cleanText(body,700));v.put("created_at",System.currentTimeMillis());
   return getWritableDatabase().insert("story_comments",null,v);
 }
 public synchronized List<Comment> storyComments(long id){
   ArrayList<Comment> out=new ArrayList<>();
   try(Cursor c=getReadableDatabase().rawQuery("SELECT _id,parent_id,author,body FROM story_comments WHERE story_id=? ORDER BY _id ASC LIMIT 100",new String[]{String.valueOf(id)})){
     while(c.moveToNext())out.add(new Comment(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3)));
   }return out;
 }
 public synchronized List<Story> stories(){
   ArrayList<Story> out=new ArrayList<>();
   try(Cursor c=getReadableDatabase().rawQuery("SELECT _id,author,body,attachment_uri,status,views,liked,scheduled_at FROM stories ORDER BY _id DESC LIMIT 300",null)){
     while(c.moveToNext())out.add(new Story(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5),c.getInt(6),c.getString(7)));
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
       if(t.has("genre"))cv.put("genre",CatalogLogic.cleanText(t.optString("genre","미확인"),40));
       if(t.has("artist")){
         cv.put("artist",CatalogLogic.cleanText(t.optString("artist",""),60));
         cv.put("artist_group_auto",0);
       }
       // Never apply authorship by language or filename alone.
       if(t.optBoolean("confirmedOriginal",false)){
         cv.put("is_original",1);
         cv.put("lyricist","박영환");
         cv.put("composer","박영환");
         cv.put("style_finisher","박영환");
         cv.put("suno_assist","Suno AI");
       }
       changed+=d.update("tracks",cv,"sha=?",new String[]{sha});
     }
   }catch(JSONException e){throw new IllegalArgumentException("JSON 읽기 실패",e);}
   return changed;
 }
 /** Applies requested credits ONLY to confirmed self-written songs. */
 public synchronized void markOriginal(long id,boolean confirmed){
   ContentValues c=new ContentValues();
   c.put("is_original",confirmed?1:0);
   c.put("lyricist",confirmed?"박영환":"");
   c.put("composer",confirmed?"박영환":"");
   c.put("style_finisher",confirmed?"박영환":"");
   c.put("suno_assist",confirmed?"Suno AI":"");
   getWritableDatabase().update("tracks",c,"_id=?",new String[]{String.valueOf(id)});
 }
 /** Owner-confirmed bulk operation. Never overwrite unrelated creator credits. */
 public synchronized int markAllOriginals(){
   ContentValues c=new ContentValues();
   c.put("is_original",1);c.put("lyricist","박영환");c.put("composer","박영환");
   c.put("style_finisher","박영환");c.put("suno_assist","Suno AI");
   return getWritableDatabase().update("tracks",c,
      "(lyricist='' OR lyricist IS NULL) AND (composer='' OR composer IS NULL)",null);
 }
 public synchronized String suggestArtist(long id){
   Track t=get(id);if(t==null)return "";
   return SingerGroupRules.suggest(t.language,t.genre,t.title,t.filename);
 }
 public synchronized int fillMissingArtists(){
   int n=0;
   for(Track t:tracks("전체")){
     if(t.artist!=null&&!t.artist.trim().isEmpty())continue;
     String group=SingerGroupRules.suggest(t.language,t.genre,t.title,t.filename);
     ContentValues c=new ContentValues();c.put("artist",group);c.put("artist_group_auto",1);
     n+=getWritableDatabase().update("tracks",c,"_id=? AND (artist='' OR artist IS NULL)",new String[]{String.valueOf(t.id)});
   }return n;
 }
 public synchronized void setSingerGroup(long id,String group){
   if(!SingerGroupRules.isGroup(group))throw new IllegalArgumentException("허용되지 않은 가수 그룹");
   ContentValues c=new ContentValues();c.put("artist",group);c.put("artist_group_auto",1);
   getWritableDatabase().update("tracks",c,"_id=?",new String[]{String.valueOf(id)});
 }
 public static final class Track {
   public final long id,size;public final String title,filename,uri,sha,language,genre,artist,scoreUri,uploadState,coverUri,lyricist,composer,styleFinisher,sunoAssist;
   public final int isOriginal,artistGroupAuto;
   public final int views,liked,rightsOk;
   Track(Cursor c){id=c.getLong(0);title=c.getString(1);filename=c.getString(2);uri=c.getString(3);sha=c.getString(4);size=c.getLong(5);
     language=c.getString(6);genre=c.getString(7);artist=c.getString(8);scoreUri=c.getString(9);views=c.getInt(10);liked=c.getInt(11);rightsOk=c.getInt(12);uploadState=c.getString(13);coverUri=c.getString(14);
     lyricist=c.getString(15);composer=c.getString(16);styleFinisher=c.getString(17);sunoAssist=c.getString(18);
     isOriginal=c.getInt(19);artistGroupAuto=c.getInt(20);}
 }
 public static final class Comment {
   public final long id,parentId;public final String author,body;
   Comment(long id,long parent,String author,String body){
     this.id=id;this.parentId=parent;this.author=author;this.body=body;
   }
 }
 public static final class Story {
   public final long id;public final String author,body,attachment,status,scheduledAt;
   public final int views,liked;
   Story(long id,String author,String body,String attachment,String status,int views,int liked,String scheduledAt){
     this.id=id;this.author=author;this.body=body;this.attachment=attachment;this.status=status;
     this.views=views;this.liked=liked;this.scheduledAt=scheduledAt==null?"":scheduledAt;
   }
 }
}