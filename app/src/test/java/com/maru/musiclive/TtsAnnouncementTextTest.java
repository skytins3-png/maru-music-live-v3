package com.maru.musiclive;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TtsAnnouncementTextTest {
    @Test public void koreanGreetingContainsName() {
        assertTrue(
                TtsAnnouncementText.greeting(
                        "영환",
                        "ko")
                        .contains("영환님"));
    }

    @Test public void englishGreeting() {
        assertTrue(
                TtsAnnouncementText.greeting(
                        "John",
                        "en")
                        .contains("welcome to the live"));
    }

    @Test public void chineseGreeting() {
        assertTrue(
                TtsAnnouncementText.greeting(
                        "小雨",
                        "zh")
                        .contains("欢迎来到直播间"));
    }

    @Test public void japaneseGreeting() {
        assertTrue(
                TtsAnnouncementText.greeting(
                        "さくら",
                        "ja")
                        .contains("いらっしゃいませ"));
    }

    @Test public void songTitleAnnouncement() {
        assertTrue(
                TtsAnnouncementText.songTitle(
                        "별빛 아래서",
                        "ko")
                        .contains("다음 곡은"));
    }
}
