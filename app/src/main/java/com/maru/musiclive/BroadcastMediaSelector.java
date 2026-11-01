package com.maru.musiclive;

/** Visible priority: approved story being read > current song > default video.
 * Manual pop-up previews are never treated as broadcast approval.
 */
public final class BroadcastMediaSelector {
    private BroadcastMediaSelector() {}
    public static String choose(String storyId,String storyMedia,String songMedia,String fallbackVideo){
        if(storyId!=null&&!storyId.isEmpty()&&
                SingleMediaPreviewPolicy.isLocalUri(storyMedia))return storyMedia;
        if(SingleMediaPreviewPolicy.isLocalUri(songMedia))return songMedia;
        if(SingleMediaPreviewPolicy.isLocalUri(fallbackVideo))return fallbackVideo;
        return "";
    }
}
