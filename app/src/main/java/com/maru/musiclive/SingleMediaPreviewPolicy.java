package com.maru.musiclive;

/** Restrict the full-screen popup to explicitly selected local images and videos.
 * It does not read BIGO content, accept arbitrary network URLs, or request overlay permission. */
public final class SingleMediaPreviewPolicy {
    private SingleMediaPreviewPolicy() {}
    public static boolean isLocalUri(String uri) {
        return uri != null && (uri.startsWith("content://")
                || uri.startsWith("android.resource://"));
    }
    public static boolean isVideo(String mime, String uri) {
        if (!isLocalUri(uri)) return false;
        if (mime != null && mime.startsWith("video/")) return true;
        if (mime != null && !mime.isEmpty() && !mime.equals("application/octet-stream"))
            return false;
        return uri.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|m4v|webm|3gp)(\\?.*)?$");
    }
    public static boolean isImage(String mime, String uri) {
        if (!isLocalUri(uri)) return false;
        if (mime != null && mime.startsWith("image/")) return true;
        if (mime != null && !mime.isEmpty() && !mime.equals("application/octet-stream"))
            return false;
        return uri.toLowerCase(java.util.Locale.ROOT)
                .matches(".*\\.(jpg|jpeg|png|webp|gif|heic|heif)(\\?.*)?$");
    }
    public static boolean accept(String mime, String uri) {
        return isImage(mime, uri) || isVideo(mime, uri);
    }
}
