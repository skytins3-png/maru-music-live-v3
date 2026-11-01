package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** One local, operator-approved image or video per approved story ID. */
public final class StoryMediaStore {
    private static final String PREFS="maru_story_visuals_v1";
    private StoryMediaStore() {}

    private static String key(String id) {
        try {
            MessageDigest d=MessageDigest.getInstance("SHA-256");
            byte[] hash=d.digest((id==null?"":id).getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder("story_");
            for(byte x:hash)s.append(String.format(java.util.Locale.ROOT,"%02x",x&255));
            return s.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
    public static String load(Context c,String storyId) {
        if(storyId==null||storyId.trim().isEmpty())return "";
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(key(storyId),"");
    }
    public static void save(Context c,String storyId,String uri) {
        if(storyId==null||storyId.trim().isEmpty())return;
        if(!SingleMediaPreviewPolicy.isLocalUri(uri))return;
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
          .putString(key(storyId),uri).apply();
    }
    public static void clear(Context c,String storyId) {
        if(storyId==null||storyId.trim().isEmpty())return;
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
          .remove(key(storyId)).apply();
    }
}
