package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TrilingualRadioIntroTest {
    @Test public void threeLanguagesAlwaysOrderedForEverySong() {
        TrilingualRadioIntro.Segment[] a =
                TrilingualRadioIntro.beforeSong("그대라서 좋아", 0, "", "", false);
        assertEquals(3, a.length);
        assertEquals(GreetingLanguage.ENGLISH, a[0].language);
        assertEquals(GreetingLanguage.KOREAN, a[1].language);
        assertEquals(GreetingLanguage.CHINESE, a[2].language);
        assertTrue(a[1].text.contains("그대라서 좋아"));
        assertFalse(a[0].text.contains("그대라서 좋아"));
        assertFalse(a[2].text.contains("그대라서 좋아"));
    }

    @Test public void approvedKoreanStoryReadOnlyOnceBetweenKoreanAndChinese() {
        TrilingualRadioIntro.Segment[] a =
                TrilingualRadioIntro.beforeSong("가을바다", 2,
                        "청취자", "오랫동안 기다린 편지가 도착했어요.", true);
        assertEquals(GreetingLanguage.ENGLISH, a[0].language);
        assertEquals(GreetingLanguage.KOREAN, a[1].language);
        int count = 0;
        for (TrilingualRadioIntro.Segment line : a) {
            if (line.originalStory) {
                count++;
                assertEquals(GreetingLanguage.KOREAN, line.language);
                assertTrue(line.text.contains("편지"));
            }
        }
        assertEquals(1, count);
        assertEquals(GreetingLanguage.CHINESE, a[a.length - 1].language);
        assertFalse(a[0].text.contains("편지"));
    }

    @Test public void chineseStoryIsReadOnlyWithChineseVoice() {
        TrilingualRadioIntro.Segment[] a =
                TrilingualRadioIntro.beforeSong("云南的清晨", 1,
                        "听众", "我想念远方的朋友。", false);
        assertEquals(GreetingLanguage.CHINESE, a[a.length - 1].language);
        assertEquals(GreetingLanguage.CHINESE, TrilingualRadioIntro.storyLanguage("我想念远方的朋友。"));
        int count = 0;
        for (TrilingualRadioIntro.Segment line : a) {
            if (line.originalStory) {
                count++;
                assertEquals(GreetingLanguage.CHINESE, line.language);
            }
        }
        assertEquals(1, count);
    }

    @Test public void englishStoryAndMissingStoryDoNotInventTranslations() {
        assertEquals(GreetingLanguage.ENGLISH,
                TrilingualRadioIntro.storyLanguage("I miss my friends"));
        assertFalse(TrilingualRadioIntro.canReadStory(""));
        TrilingualRadioIntro.Segment[] a =
                TrilingualRadioIntro.beforeSong("Autumn Sea", 0,
                        "", "", false);
        assertEquals(3, a.length);
        for (TrilingualRadioIntro.Segment line : a) assertFalse(line.originalStory);
    }

    @Test public void longStoryHasSafeOriginalLanguageChunks() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 30; i++) text.append("내 이야기를 들어주세요. ");
        TrilingualRadioIntro.Segment[] a =
                TrilingualRadioIntro.beforeSong("노래", 0, "청취자", text.toString(), false);
        int chunks = 0;
        for (TrilingualRadioIntro.Segment line : a) {
            if (line.originalStory) {
                chunks++;
                assertTrue(line.text.length() <= 95);
                assertEquals(GreetingLanguage.KOREAN, line.language);
            }
        }
        assertTrue(chunks > 1);
    }
}
