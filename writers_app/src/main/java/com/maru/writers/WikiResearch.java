package com.maru.writers;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;
/** Narrow, read-only, on-demand public encyclopedia lookup. Not an AI crawler. */
public final class WikiResearch{
 public static final class Result{
   public final String title,summary,source;
   public Result(String title,String summary,String source){this.title=title;this.summary=summary;this.source=source;}
 }
 private WikiResearch(){}
 public static Result lookup(String term)throws Exception{
   if(term==null||term.trim().isEmpty())throw new IOException("조사할 주제를 입력하세요.");
   String q=URLEncoder.encode(term.trim(),"UTF-8");
   String url="https://ko.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exintro=1&exchars=900&redirects=1&formatversion=2&format=json&titles="+q;
   HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();
   c.setRequestMethod("GET");c.setConnectTimeout(9000);c.setReadTimeout(9000);
   c.setRequestProperty("User-Agent","MARUWriterStudio/1.0 (independent educational radio research; contact via GitHub skytins3-png)");
   c.setRequestProperty("Accept","application/json");
   int status=c.getResponseCode();
   if(status!=200){c.disconnect();throw new IOException("공개 자료 조회 실패 (HTTP "+status+")");}
   try(InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()){
     byte[] bytes=new byte[4096];int n;
     while((n=in.read(bytes))>0){b.write(bytes,0,n);if(b.size()>25000)throw new IOException("응답이 너무 큽니다");}
     JSONObject response=new JSONObject(b.toString(StandardCharsets.UTF_8.name()));
     JSONArray pages=response.getJSONObject("query").getJSONArray("pages");
     if(pages.length()==0)throw new IOException("관련 문서를 찾지 못했습니다.");
     JSONObject page=pages.getJSONObject(0);
     String extract=page.optString("extract","").trim();
     if(page.optBoolean("missing")||extract.isEmpty())throw new IOException("본문을 찾지 못했습니다. 다른 주제로 검색하세요.");
     String title=page.optString("title",term);
     String source="https://ko.wikipedia.org/wiki/"+URLEncoder.encode(title,"UTF-8").replace("+","%20");
     return new Result(title,extract.length()>700?extract.substring(0,700):extract,source);
   }finally{c.disconnect();}
 }
}