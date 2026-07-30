package com.maru.musiclive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SongTitleFormatterTest {
    @Test public void removesExtension() {
        assertEquals(
                "별빛 아래서",
                SongTitleFormatter.clean(
                        "별빛_아래서.mp3"));
    }

    @Test public void removesTrackNumber() {
        assertEquals(
                "Hello World",
                SongTitleFormatter.clean(
                        "01 - Hello World.wav"));
    }
}
