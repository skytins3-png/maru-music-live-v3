package com.maru.musiclive;

import org.junit.Test;

import static org.junit.Assert.*;

public final class BigoRtmpEndpointPolicyTest {
    @Test public void acceptsOnlyExplicitStreamEndpoints() {
        assertTrue(BigoRtmpEndpointPolicy.validServer("rtmps://live.example.com/live"));
        assertTrue(BigoRtmpEndpointPolicy.validServer("rtmp://live.example.com:1935/app/"));
        assertFalse(BigoRtmpEndpointPolicy.validServer("https://live.example.com/live"));
        assertFalse(BigoRtmpEndpointPolicy.validServer("file:///etc/passwd"));
        assertFalse(BigoRtmpEndpointPolicy.validServer("rtmp://user:pass@live.example.com/live"));
        assertFalse(BigoRtmpEndpointPolicy.validServer("rtmp://live.example.com/live\ninjection"));
        assertFalse(BigoRtmpEndpointPolicy.validServer("rtmp://live.example.com/live?q=secret"));
        assertFalse(BigoRtmpEndpointPolicy.validServer(null));
    }

    @Test public void treatsStreamKeyAsRequiredSecret() {
        assertTrue(BigoRtmpEndpointPolicy.validStreamKey("private-stream-name_123-A"));
        assertFalse(BigoRtmpEndpointPolicy.validStreamKey(""));
        assertFalse(BigoRtmpEndpointPolicy.validStreamKey("bad key"));
        assertFalse(BigoRtmpEndpointPolicy.validStreamKey("bad\nkey"));
        assertFalse(BigoRtmpEndpointPolicy.validStreamKey("bad#key"));
        assertFalse(BigoRtmpEndpointPolicy.validStreamKey(null));
    }

    @Test public void joinsExactlyOneSlashAndNoRedactionByCallingCode() {
        assertEquals("rtmps://live.example.com/app/secret",
            BigoRtmpEndpointPolicy.join("rtmps://live.example.com/app", "secret"));
        assertEquals("rtmps://live.example.com/app/secret",
            BigoRtmpEndpointPolicy.join("rtmps://live.example.com/app/", "secret"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void cannotConstructLiveEndpointWithoutOfficialSecret() {
        BigoRtmpEndpointPolicy.join("rtmps://live.example.com/app", "");
    }
}
