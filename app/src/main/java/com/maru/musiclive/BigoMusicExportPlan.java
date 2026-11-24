package com.maru.musiclive;

import java.util.Locale;

/**
 * Safe, deterministic exported names for user-owned audio files that can be
 * selected using BIGO's own music picker where that feature is available.
 * This does not inject music into the microphone or start a BIGO room.
 */
public final class BigoMusicExportPlan {
    public static final String ALBUM_PATH = "Music/MARU_BIGO/";
    private BigoMusicExportPlan() {}

    public static String safeName(String original, String sourceUri) {
        if (original == null || sourceUri == null || sourceUri.isEmpty())
            return "";
        String raw = original.trim();
        int dot = raw.lastIndexOf('.');
        if (dot < 1) return "";
        String ext = raw.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ext.matches("mp3|wav|m4a|aac|flac|ogg|opus"))
            return "";
        String stem = raw.substring(0, dot).replaceAll("[^a-zA-Z0-9가-힣\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana} _.-]", "_");
        if (stem.isEmpty()) stem = "MARU";
        if (stem.length() > 52) stem = stem.substring(0, 52);
        return "MARU_" + Integer.toUnsignedString(sourceUri.hashCode(), 16)
                + "_" + stem + "." + ext;
    }

    public static String mime(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".opus")) return "audio/opus";
        return "";
    }
}
