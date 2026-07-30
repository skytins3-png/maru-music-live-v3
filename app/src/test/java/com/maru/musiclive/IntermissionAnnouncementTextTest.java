package com.maru.musiclive;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public final class IntermissionAnnouncementTextTest {
    @Test public void supportsFiveBroadcastLanguages() {
        assertTrue(IntermissionAnnouncementText.build(
                "별빛 아래서", "ko", Arrays.asList("민지"), Collections.singletonList("하늘"))
                .contains("좋아요 한 번 눌러 주세요"));
        assertTrue(IntermissionAnnouncementText.build(
                "Starlight", "en", Arrays.asList("Minji"), Collections.singletonList("Sky"))
                .contains("Let's listen together"));
        assertTrue(IntermissionAnnouncementText.build(
                "星光之下", "zh", Arrays.asList("小雨"), Collections.singletonList("明月"))
                .contains("让我们一起欣赏"));
        assertTrue(IntermissionAnnouncementText.build(
                "星空の下で", "ja", Arrays.asList("さくら"), Collections.singletonList("そら"))
                .contains("一緒に聴きましょう"));
        assertTrue(IntermissionAnnouncementText.build(
                "Под звёздами", "ru", Arrays.asList("Анна"), Collections.singletonList("Иван"))
                .contains("Давайте послушаем вместе"));
    }

    @Test public void fixedAnnouncementOrderContainsAllFiveLanguages() {
        assertArrayEquals(
                new String[]{"ko", "en", "zh", "ja", "ru"},
                IntermissionAnnouncementText.orderedLanguages());
    }
}
