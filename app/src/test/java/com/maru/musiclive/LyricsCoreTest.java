package com.maru.musiclive;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class LyricsCoreTest {
    @Test public void noLyricsIsEmpty() {
        assertEquals(
                "",
                LyricsCore.twoLines(
                        Collections.emptyList(),
                        10_000L));
    }

    @Test public void finalLineExpires() {
        LyricsCore.Line line =
                new LyricsCore.Line(
                        25_000L,
                        "마지막 줄");
        assertEquals(
                "마지막 줄",
                LyricsCore.twoLines(
                        Collections.singletonList(line),
                        30_000L));
        assertEquals(
                "",
                LyricsCore.twoLines(
                        Collections.singletonList(line),
                        32_000L));
    }

    @Test public void nextLineOnlyAppearsNearItsTime() {
        assertEquals(
                "첫 줄",
                LyricsCore.twoLines(
                        Arrays.asList(
                                new LyricsCore.Line(
                                        0L,
                                        "첫 줄"),
                                new LyricsCore.Line(
                                        10_000L,
                                        "둘째 줄")),
                        2_000L));
        assertEquals(
                "첫 줄\n둘째 줄",
                LyricsCore.twoLines(
                        Arrays.asList(
                                new LyricsCore.Line(
                                        0L,
                                        "첫 줄"),
                                new LyricsCore.Line(
                                        10_000L,
                                        "둘째 줄")),
                        9_000L));
    }
}
