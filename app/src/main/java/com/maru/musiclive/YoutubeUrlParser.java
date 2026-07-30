package com.maru.musiclive;

import java.net.URI;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YoutubeUrlParser {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private YoutubeUrlParser() {}

    public static String videoId(String input) {
        if (input == null) return "";
        String value = input.trim();
        if (ID_PATTERN.matcher(value).matches()) return value;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null) return "";
            host = host.toLowerCase();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.equals("youtu.be") || host.endsWith(".youtu.be")) {
                return valid(firstSegment(path));
            }
            if (host.equals("youtube.com") || host.endsWith(".youtube.com")) {
                if (path.equals("/watch")) return valid(queryValue(uri.getRawQuery(), "v"));
                if (path.startsWith("/shorts/")) return valid(segmentAfter(path, "/shorts/"));
                if (path.startsWith("/embed/")) return valid(segmentAfter(path, "/embed/"));
                if (path.startsWith("/live/")) return valid(segmentAfter(path, "/live/"));
            }
        } catch (RuntimeException ignored) {}
        Matcher matcher = Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})")
                .matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String firstSegment(String path) {
        String value = path.startsWith("/") ? path.substring(1) : path;
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(0, slash) : value;
    }

    private static String segmentAfter(String path, String marker) {
        String value = path.substring(marker.length());
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(0, slash) : value;
    }

    private static String queryValue(String rawQuery, String key) {
        if (rawQuery == null) return "";
        for (String part : rawQuery.split("&")) {
            int split = part.indexOf('=');
            String name = split >= 0 ? part.substring(0, split) : part;
            if (!key.equals(decode(name))) continue;
            String value = split >= 0 ? part.substring(split + 1) : "";
            return decode(value);
        }
        return "";
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String valid(String value) {
        return value != null && ID_PATTERN.matcher(value).matches() ? value : "";
    }
}
