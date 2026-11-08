package com.maru.media;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.HashMap;
import java.util.Map;

/**
 * Portable PRIVATE metadata backup (not the original music bytes).
 * Includes track metadata, story drafts/status, comments, replies and likes.
 * Re-import the original MP3/ZIP collection before importing this file.
 * Never imports stale content:// URIs from a different phone.
 */
public final class VaultMetadataArchive {
 private static final String FORMAT="MARU_MEDIA_PRIVATE_METADATA_V1";
 private VaultMetadataArchive(){}
 public static final class Result {
   public int tracksRestored,tracksWithoutAudio,storiesRestored,commentsRestored;
   @Override public String toString(){
     return "곡정보 "+tracksRestored+"건 적용 / 음원 없는 곡 "+tracksWithoutAudio+
       "건 / 사연 "+storiesRestored+"건 / 댓글·답글 "+commentsRestored+"건 복구";
   }
 }
 private static JSONArray dump(SQLiteDatabase d,String table)throws JSONException {
   JSONArray array=new JSONArray();
   try(Cursor c=d.rawQuery("SELECT * FROM "+table,null)){
     while(c.moveToNext()){
       JSONObject obj=new JSONObject();
       for(int i=0;i<c.getColumnCount();i++){
         String key=c.getColumnName(i);
         if("uri".equals(key)||"score_uri".equals(key)||"cover_uri".equals(key)||"attachment_uri".equals(key))continue;
         switch(c.getType(i)){
           case Cursor.FIELD_TYPE_INTEGER:obj.put(key,c.getLong(i));break;
           case Cursor.FIELD_TYPE_FLOAT:obj.put(key,c.getDouble(i));break;
           case Cursor.FIELD_TYPE_NULL:obj.put(key,JSONObject.NULL);break;
           default:obj.put(key,c.getString(i));break;
         }
       }
       array.put(obj);
     }
   }
   return array;
 }
 public static synchronized String exportData(VaultDb db){
   try{
     JSONObject obj=new JSONObject();
     obj.put("format",FORMAT);
     obj.put("note","원곡 MP3·ZIP, 외부 사진·악보·첨부파일의 실제 바이트는 이 목록 백업에 포함되지 않습니다. 원곡을 먼저 가져오고 복원하세요.");
     obj.put("tracks",dump(db.getReadableDatabase(),"tracks"));
     obj.put("stories",dump(db.getReadableDatabase(),"stories"));
     obj.put("comments",dump(db.getReadableDatabase(),"comments"));
     obj.put("song_comment_replies",dump(db.getReadableDatabase(),"song_comment_replies"));
     obj.put("story_comments",dump(db.getReadableDatabase(),"story_comments"));
     String data=obj.toString(2);
     if(data.length()>6_000_000)throw new IllegalArgumentException("목록 백업 크기 제한 6MB");
     return data;
   }catch(JSONException e){throw new IllegalArgumentException("백업 데이터 생성 실패",e);}
 }
 private static long find(SQLiteDatabase d,String table,String where,String[] args){
   try(Cursor c=d.query(table,new String[]{"_id"},where,args,null,null,null,"1")){
     return c.moveToFirst()?c.getLong(0):-1;
   }
 }
 private static ContentValues cleanForComments(JSONObject r,String fk,long newFk,Long parent){
   ContentValues v=new ContentValues();
   v.put(fk,newFk);
   if(parent!=null)v.put("parent_id",parent);
   v.put("author",CatalogLogic.cleanText(r.optString("author",""),30));
   v.put("body",CatalogLogic.cleanText(r.optString("body",""),700));
   v.put("created_at",r.optLong("created_at",0));
   return v;
 }
 private static long commentRow(SQLiteDatabase d,String table,JSONObject r,String fk,long newFk,Long parent){
   String author=CatalogLogic.cleanText(r.optString("author",""),30);
   String body=CatalogLogic.cleanText(r.optString("body",""),700);
   long created=r.optLong("created_at",0);
   String where=fk+"=?"+(parent==null?"":" AND parent_id=?")+" AND author=? AND body=? AND created_at=?";
   String[] args=parent==null?new String[]{String.valueOf(newFk),author,body,String.valueOf(created)}
     :new String[]{String.valueOf(newFk),String.valueOf(parent),author,body,String.valueOf(created)};
   long old=find(d,table,where,args);
   if(old>0)return old;
   return d.insertWithOnConflict(table,null,cleanForComments(r,fk,newFk,parent),SQLiteDatabase.CONFLICT_IGNORE);
 }
 public static synchronized Result importData(VaultDb db,String content){
   if(content==null||content.length()>6_000_000)throw new IllegalArgumentException("백업 파일 6MB 제한");
   try{
     JSONObject backup=new JSONObject(content);
     if(!FORMAT.equals(backup.optString("format")))throw new IllegalArgumentException("MARU 보관함 목록 백업 파일이 아닙니다");
     JSONArray tracks=backup.optJSONArray("tracks");
     JSONArray stories=backup.optJSONArray("stories");
     JSONArray comments=backup.optJSONArray("comments");
     JSONArray replies=backup.optJSONArray("song_comment_replies");
     JSONArray storyComments=backup.optJSONArray("story_comments");
     if(tracks==null||stories==null||comments==null||replies==null||storyComments==null)
       throw new IllegalArgumentException("목록 백업 자료가 부족합니다");
     if(tracks.length()>3000||stories.length()>5000||comments.length()>100000||replies.length()>100000||storyComments.length()>100000)
       throw new IllegalArgumentException("백업 항목 수 제한");
     SQLiteDatabase d=db.getWritableDatabase();
     Result result=new Result();
     Map<Long,Long> songIds=new HashMap<>();
     Map<Long,Long> storyIds=new HashMap<>();
     Map<Long,Long> songComments=new HashMap<>();
     Map<Long,Long> storyCommentIds=new HashMap<>();
     d.beginTransaction();
     try{
       for(int i=0;i<tracks.length();i++){
         JSONObject t=tracks.optJSONObject(i);if(t==null)continue;
         String sha=t.optString("sha","");
         if(!CatalogLogic.sha256(sha))continue;
         long id=find(d,"tracks","sha=?",new String[]{sha});
         if(id<0){result.tracksWithoutAudio++;continue;}
         songIds.put(t.optLong("_id"),id);
         ContentValues v=new ContentValues();
         v.put("title",CatalogLogic.cleanText(t.optString("title",""),150));
         v.put("artist",CatalogLogic.cleanText(t.optString("artist",""),60));
         v.put("genre",CatalogLogic.cleanText(t.optString("genre","미확인"),40));
         String group=t.optString("language","검토필요");
         if(!java.util.Arrays.asList(CatalogLogic.LANGUAGES).contains(group))group="검토필요";
         v.put("language",group);
         v.put("views",Math.max(0,t.optInt("views",0)));
         v.put("liked",t.optInt("liked",0)!=0?1:0);
         if(t.has("is_original")){
           v.put("is_original",t.optInt("is_original",0)!=0?1:0);
           for(String field:new String[]{"lyricist","composer","style_finisher","suno_assist"})
             v.put(field,CatalogLogic.cleanText(t.optString(field,""),100));
         }
         if(t.has("artist_group_auto"))
           v.put("artist_group_auto",t.optInt("artist_group_auto",0)!=0?1:0);
         // Existing local upload rights and remote-published flags must not be silently restored.
         d.update("tracks",v,"_id=?",new String[]{String.valueOf(id)});
         result.tracksRestored++;
       }
       for(int i=0;i<stories.length();i++){
         JSONObject t=stories.optJSONObject(i);if(t==null)continue;
         String author=CatalogLogic.cleanText(t.optString("author",""),30);
         String body=CatalogLogic.cleanText(t.optString("body",""),2800);
         if(body.length()<2)continue;
         long created=t.optLong("created_at",0);
         long id=find(d,"stories","author=? AND body=? AND created_at=?",new String[]{author,body,String.valueOf(created)});
         if(id<0){
           ContentValues v=new ContentValues();
           v.put("author",author);v.put("body",body);
           v.put("attachment_uri",""); // Old device URI must never be imported.
           String status=t.optString("status","검토대기");
           if(!java.util.Arrays.asList("검토대기","승인","거절","예약","방송완료","보관").contains(status))status="검토대기";
           v.put("status",status);v.put("created_at",created);
           v.put("views",Math.max(0,t.optInt("views",0)));
           v.put("liked",t.optInt("liked",0)!=0?1:0);
           v.put("scheduled_at",CatalogLogic.cleanText(t.optString("scheduled_at",""),45));
           id=d.insertWithOnConflict("stories",null,v,SQLiteDatabase.CONFLICT_IGNORE);
           if(id>0)result.storiesRestored++;
         }
         if(id>0)storyIds.put(t.optLong("_id"),id);
       }
       for(int i=0;i<comments.length();i++){
         JSONObject c=comments.optJSONObject(i);if(c==null)continue;
         Long fk=songIds.get(c.optLong("track_id"));
         if(fk==null)continue;
         long existed=find(d,"comments","track_id=? AND author=? AND body=? AND created_at=?",new String[]{
           String.valueOf(fk),CatalogLogic.cleanText(c.optString("author",""),30),
           CatalogLogic.cleanText(c.optString("body",""),700),String.valueOf(c.optLong("created_at",0))});
         long id=commentRow(d,"comments",c,"track_id",fk,null);
         if(id>0){songComments.put(c.optLong("_id"),id);if(existed<0)result.commentsRestored++;}
       }
       for(int i=0;i<replies.length();i++){
         JSONObject c=replies.optJSONObject(i);if(c==null)continue;
         Long fk=songIds.get(c.optLong("track_id"));
         Long parent=songComments.get(c.optLong("parent_id"));
         if(fk==null||parent==null)continue;
         long existed=find(d,"song_comment_replies","track_id=? AND parent_id=? AND author=? AND body=? AND created_at=?",
           new String[]{String.valueOf(fk),String.valueOf(parent),CatalogLogic.cleanText(c.optString("author",""),30),
                        CatalogLogic.cleanText(c.optString("body",""),700),String.valueOf(c.optLong("created_at",0))});
         if(existed<0){
           long id=commentRow(d,"song_comment_replies",c,"track_id",fk,parent);
           if(id>0)result.commentsRestored++;
         }
       }
       for(int i=0;i<storyComments.length();i++){
         JSONObject c=storyComments.optJSONObject(i);if(c==null)continue;
         Long fk=storyIds.get(c.optLong("story_id"));if(fk==null)continue;
         Long parent=c.optLong("parent_id",0)==0?Long.valueOf(0):storyCommentIds.get(c.optLong("parent_id"));
         if(parent==null)continue;
         long existed=find(d,"story_comments","story_id=? AND parent_id=? AND author=? AND body=? AND created_at=?",
          new String[]{String.valueOf(fk),String.valueOf(parent),CatalogLogic.cleanText(c.optString("author",""),30),
             CatalogLogic.cleanText(c.optString("body",""),700),String.valueOf(c.optLong("created_at",0))});
         long id=commentRow(d,"story_comments",c,"story_id",fk,parent);
         if(id>0){storyCommentIds.put(c.optLong("_id"),id);if(existed<0)result.commentsRestored++;}
       }
       d.setTransactionSuccessful();
     }finally{d.endTransaction();}
     return result;
   }catch(JSONException e){throw new IllegalArgumentException("백업 JSON 형식 오류",e);}
 }
}
