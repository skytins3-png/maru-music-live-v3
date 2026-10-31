package com.maru.musiclive;

import java.net.URI;

/** Restricts in-app JavaScript browser navigation to the public MARU site. */
public final class TrustedWebStudio {
    private TrustedWebStudio() {}

    public static final String URL = "https://skytins3-png.github.io/maru-ai-song-studio/";

    public static boolean mayNavigateInside(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return false;
        try {
            URI parsed = URI.create(rawUrl);
            if (!"https".equalsIgnoreCase(parsed.getScheme())) return false;
            if (!"skytins3-png.github.io".equalsIgnoreCase(parsed.getHost())) return false;
            if (parsed.getRawUserInfo() != null) return false;
            if (parsed.getPort() != -1 && parsed.getPort() != 443) return false;
            // Never accept encoded separators/dots; normalization does not
            // treat %2e%2e as path traversal until it is URL-decoded.
            String rawPath = parsed.getRawPath();
            if (rawPath == null || rawPath.contains("\\\\")
                    || rawPath.toLowerCase(java.util.Locale.ROOT).contains("%2e")
                    || rawPath.toLowerCase(java.util.Locale.ROOT).contains("%2f")
                    || rawPath.toLowerCase(java.util.Locale.ROOT).contains("%5c"))
                return false;
            URI clean = parsed.normalize();
            String path = clean.getPath();
            if (path == null) return false;
            return path.equals("/maru-ai-song-studio")
                    || path.startsWith("/maru-ai-song-studio/");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
