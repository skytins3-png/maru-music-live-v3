package com.maru.musiclive;

import java.net.URI;
import java.net.URISyntaxException;

/** External encoder uses only an account-owner supplied URL + secret stream key. */
public final class BigoRtmpEndpointPolicy {
    private BigoRtmpEndpointPolicy() {}

    public static boolean validServer(String value) {
        if (value == null || value.length() > 350
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                || value.indexOf(' ') >= 0) return false;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!"rtmp".equalsIgnoreCase(scheme) && !"rtmps".equalsIgnoreCase(scheme))
                return false;
            return uri.getHost() != null && !uri.getHost().isEmpty()
                    && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && !uri.getHost().contains("..");
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    public static boolean validStreamKey(String value) {
        if (value == null || value.isEmpty() || value.length() > 700) return false;
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (c <= ' ' || c == 127 || c == '#' || c == '\\') return false;
        }
        return true;
    }

    /** Never log, copy or save the full endpoint: it contains a secret. */
    public static String join(String server, String secret) {
        if (!validServer(server) || !validStreamKey(secret)) {
            throw new IllegalArgumentException("Invalid server URL or stream key");
        }
        return server.endsWith("/") ? server+secret : server+"/"+secret;
    }
}
