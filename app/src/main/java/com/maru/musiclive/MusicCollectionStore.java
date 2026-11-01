package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Stores labels only, not audio bytes. No existing songs/settings are replaced. */
public final class MusicCollectionStore {
    private static final String PREFS = "maru_music_collections_v1";
    private MusicCollectionStore() {}
    private static String key(String uri,String facet) {
        if(uri==null||uri.trim().isEmpty())return "";
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                    .digest(uri.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(facet+"_");
            for(byte b:bytes)out.append(Character.forDigit((b>>4)&15,16))
                    .append(Character.forDigit(b&15,16));
            return out.toString();
        }catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    public static String region(Context c,String uri,String title) {
        return prefs(c).getString(key(uri,"region"),MusicCollection.detectRegion(title));
    }
    public static String style(Context c,String uri,String title) {
        return prefs(c).getString(key(uri,"style"),MusicCollection.detectStyle(title));
    }
    public static String source(Context c,String uri) {
        String value=prefs(c).getString(key(uri,"source"),"");
        if(MusicCollection.isSource(value))return value;
        if(uri!=null&&uri.contains("/shared_original_audio/"))return MusicCollection.SUNO;
        return MusicCollection.DEVICE;
    }
    public static void setRegion(Context c,String uri,String value) {
        if(MusicCollection.isRegion(value)&&!key(uri,"region").isEmpty())
            prefs(c).edit().putString(key(uri,"region"),value).apply();
    }
    public static void setStyle(Context c,String uri,String value) {
        if(MusicCollection.isStyle(value)&&!key(uri,"style").isEmpty())
            prefs(c).edit().putString(key(uri,"style"),value).apply();
    }
    public static void setSource(Context c,String uri,String value) {
        if(MusicCollection.isSource(value)&&!key(uri,"source").isEmpty())
            prefs(c).edit().putString(key(uri,"source"),value).apply();
    }
    public static void clear(Context c,String uri) {
        SharedPreferences.Editor e=prefs(c).edit();
        for(String facet:new String[]{"region","style","source"}){
            String k=key(uri,facet);
            if(!k.isEmpty())e.remove(k);
        }
        e.apply();
    }
}
