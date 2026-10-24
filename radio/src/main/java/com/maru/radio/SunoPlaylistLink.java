package com.maru.radio;
import java.net.URI;
/** Accept only official HTTPS playlist URLs, never arbitrary intent URLs. */
public final class SunoPlaylistLink {
    private SunoPlaylistLink() {}
    public static String normalize(String input) {
        try {
            URI uri = new URI(input.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equalsIgnoreCase("suno.com") || host.equalsIgnoreCase("www.suno.com"))
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !uri.getPath().matches("/playlist/[A-Za-z0-9_-]+/?")) return null;
            return new URI("https", null, "suno.com", -1, uri.getPath(), null, null).toString();
        } catch (Exception ignored) { return null; }
    }
}
