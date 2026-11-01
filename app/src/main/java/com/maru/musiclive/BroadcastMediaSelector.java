package com.maru.musiclive;

/** Exactly one visible visual: approved story > host greeting/talk > current song > default.
 * Unassigned stories or host segments use the default video, never a different song's media.
 */
public final class BroadcastMediaSelector {
    public static final String GREETING = "greeting";
    public static final String TALK = "talk";
    private BroadcastMediaSelector() {}

    public static String choose(String storyId, String storyMedia, String songMedia,
                                String fallbackVideo) {
        return chooseForScene(storyId, storyMedia, "", "", songMedia, fallbackVideo);
    }

    public static String chooseForScene(String storyId, String storyMedia,
                                       String hostScene, String hostMedia,
                                       String songMedia, String fallbackVideo) {
        if (storyId != null && !storyId.isEmpty())
            return safe(storyMedia, fallbackVideo);
        if (GREETING.equals(hostScene) || TALK.equals(hostScene))
            return safe(hostMedia, fallbackVideo);
        return safe(songMedia, fallbackVideo);
    }

    private static String safe(String media, String fallback) {
        if (SingleMediaPreviewPolicy.isLocalUri(media)) return media;
        return SingleMediaPreviewPolicy.isLocalUri(fallback) ? fallback : "";
    }
}
