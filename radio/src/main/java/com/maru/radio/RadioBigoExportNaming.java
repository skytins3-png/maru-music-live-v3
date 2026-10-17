package com.maru.radio;

import java.util.Locale;

/** Deterministic shared-Music export names for user-owned originals. */
public final class RadioBigoExportNaming {
    private RadioBigoExportNaming() {}

    public static String fileName(String sha256, String title) {
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("Invalid original SHA-256");
        String input = title == null ? "" : title;
        String lower = input.toLowerCase(Locale.ROOT);
        String ext = extension(lower);
        String base = ext.isEmpty() ? input : input.substring(0, input.length() - ext.length());
        if (ext.isEmpty()) ext = ".mp3"; // other import formats require their own extension
        base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}\\p{Cf}]", "_")
                .replaceAll("\\.{2,}", "_")
                .replaceAll("^\\.+", "")
                .replaceAll("\\s+", " ").trim();
        if (base.length() > 64) base = base.substring(0, 64).trim();
        if (base.isEmpty()) base = "Original";
        return "MARU-" + sha256.substring(0, 16).toLowerCase(Locale.ROOT)
                + "-" + base + ext;
    }

    public static String extension(String lowercaseName) {
        if (lowercaseName == null) return "";
        for (String ext : new String[]{".mp3", ".wav", ".flac", ".m4a", ".ogg", ".aac"}) {
            if (lowercaseName.endsWith(ext)) return ext;
        }
        return "";
    }

    public static String mimeType(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".aac")) return "audio/aac";
        return "audio/mpeg";
    }
}
