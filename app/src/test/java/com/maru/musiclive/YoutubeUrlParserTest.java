package com.maru.musiclive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class YoutubeUrlParserTest {
    @Test public void watchUrl() {
        assertEquals("dQw4w9WgXcQ", YoutubeUrlParser.videoId(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test public void shortUrl() {
        assertEquals("dQw4w9WgXcQ", YoutubeUrlParser.videoId(
                "https://youtu.be/dQw4w9WgXcQ?t=5"));
    }

    @Test public void shortsUrl() {
        assertEquals("dQw4w9WgXcQ", YoutubeUrlParser.videoId(
                "https://www.youtube.com/shorts/dQw4w9WgXcQ"));
    }

    @Test public void invalidUrl() {
        assertEquals("", YoutubeUrlParser.videoId("not a video"));
    }
}
