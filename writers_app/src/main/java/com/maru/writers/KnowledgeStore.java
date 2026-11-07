package com.maru.writers;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.time.LocalDateTime;
import java.util.*;
public final class KnowledgeStore {
 private static final String PREFS="maru_editorial_records_v1";
 private KnowledgeStore(){}
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 public static void keep(Context c,String topic,String summary,String source) throws JSONException {
   JSONArray a=records(c),b=new JSONArray();
   for(int i=Math.max(0,a.length()-28);i<a.length();i++)b.put(a.getJSONObject(i));
   JSONObject obj=new JSONObject();
   obj.put("topic",topic);obj.put("summary",summary);obj.put("source",source);
   obj.put("approvedAt",LocalDateTime.now().toString());b.put(obj);
   prefs(c).edit().putString("research",b.toString()).apply();
 }
 public static JSONArray records(Context c){
   try{return new JSONArray(prefs(c).getString("research","[]"));}
   catch(Exception e){return new JSONArray();}
 }
 public static JSONObject latestFor(Context c,String topic){
   JSONArray a=records(c);
   for(int i=a.length()-1;i>=0;i--){
     JSONObject v=a.optJSONObject(i);
     if(v!=null&&topic.trim().equals(v.optString("topic")))return v;
   }
   return null;
 }
 public static void saveScript(Context c,String script){
   prefs(c).edit().putString("script",script).apply();
 }
 public static String loadScript(Context c){return prefs(c).getString("script","");}
}