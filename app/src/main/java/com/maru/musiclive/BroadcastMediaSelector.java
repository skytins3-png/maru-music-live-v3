package com.maru.musiclive;

/** Visible priority: approved story being read > current song > default video.
 * Manual pop-up previews are never treated as broadcast approval.
 */
public final class BroadcastMediaSelector {
    private BroadcastMediaSelector() {}
    public static String choose(String storyId,String storyMedia,String songMedia,String fallbackVideo){
        if(storyId!=null&&!storyId.isEmpty()){
            // An unmapped story must NEVER display the unrelated next song's picture.
            return SingleMediaPreviewPolicy.isLocalUri(storyMedia)
                    ? storyMedia : safeFallback(fallbackVideo);
        }
        if(SingleMediaPreviewPolicy.isLocalUri(songMedia))return songMedia;
        return safeFallback(fallbackVideo);
    }
    private static String safeFallback(String video){
        return SingleMediaPreviewPolicy.isLocalUri(video)?video:"";
    }
}
