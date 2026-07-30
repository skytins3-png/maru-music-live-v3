package com.maru.musiclive;

public final class SongTitleFormatter {
    private SongTitleFormatter() {}

    public static String clean(String rawTitle) {
        if (rawTitle == null) return "음악";

        String value = rawTitle.trim();
        value = value.replaceAll(
                "(?i)\\.(mp3|wav|m4a|aac|flac|ogg|opus)$",
                "");
        value = value.replace('_', ' ');
        value = value.replaceAll(
                "^\\s*\\d{1,3}\\s*[-._)]\\s*",
                "");
        value = value.replaceAll("\\s+", " ").trim();

        if ("actual music".equalsIgnoreCase(value)
                || "actual_music".equalsIgnoreCase(value)) {
            return "테스트 음악";
        }
        return value.isEmpty() ? "음악" : value;
    }
}
