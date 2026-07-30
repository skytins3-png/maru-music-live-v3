package com.maru.musiclive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GreetingLanguageTest {
    @Test public void korean() {
        assertEquals(
                GreetingLanguage.KOREAN,
                GreetingLanguage.normalize("ko-KR"));
    }

    @Test public void english() {
        assertEquals(
                GreetingLanguage.ENGLISH,
                GreetingLanguage.normalize("en-US"));
    }

    @Test public void chinese() {
        assertEquals(
                GreetingLanguage.CHINESE,
                GreetingLanguage.normalize("zh-CN"));
    }

    @Test public void japanese() {
        assertEquals(
                GreetingLanguage.JAPANESE,
                GreetingLanguage.normalize("ja-JP"));
    }

    @Test public void otherLanguageUsesEnglish() {
        assertEquals(
                GreetingLanguage.ENGLISH,
                GreetingLanguage.normalize("vi"));
        assertEquals(
                GreetingLanguage.ENGLISH,
                GreetingLanguage.normalize("id"));
    }

    @Test public void emptyDefaultsToKorean() {
        assertEquals(
                GreetingLanguage.KOREAN,
                GreetingLanguage.normalize(null));
        assertEquals(
                GreetingLanguage.KOREAN,
                GreetingLanguage.normalize("   "));
    }

    @Test public void labelsDoNotForceGender() {
        assertEquals("한국어 음성", GreetingLanguage.koreanLabel("ko"));
        assertEquals("영어 음성", GreetingLanguage.koreanLabel("en"));
    }
}
