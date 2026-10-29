package com.maru.musiclive;

import static org.junit.Assert.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.Test;

public class GuestTalkShowTest {
    @Test public void extendedFictionalInterviewReadsOnlyProvidedStoryAndSong() {
        RadioConversationScript.Line[] lines = GuestTalkShow.beforeSong(
                "우리들의 노래", 6, LocalDate.of(2026, 10, 30), 1,
                "익명", "연락 못 한 친구에게 안부를 전하고 싶습니다.",
                "운영자 승인한 참고 요약입니다.", true);
        assertTrue(lines.length >= 40);
        StringBuilder all = new StringBuilder();
        int singers = 0;
        for (RadioConversationScript.Line line : lines) {
            assertTrue(line.text.length() < 220);
            all.append(line.text);
            if (line.artist != null) singers++;
        }
        assertTrue(singers >= 20);
        assertTrue(all.toString().contains("연락 못 한 친구"));
        assertTrue(all.toString().contains("운영자 승인한 참고 요약"));
        assertTrue(all.toString().contains("신청곡"));
        assertTrue(all.toString().contains("우리들의 노래"));
    }

    @Test public void noMadeUpRequestsOrStoriesWhenMissing() {
        RadioConversationScript.Line[] lines = GuestTalkShow.beforeSong(
                "고요한 바다", 1, LocalDate.of(2026, 10, 30), 0,
                "", "", "", false);
        StringBuilder full = new StringBuilder();
        for (RadioConversationScript.Line line : lines) full.append(line.text);
        assertFalse(full.toString().contains("청취자 사연을 읽겠습니다"));
        assertFalse(full.toString().contains("접수한 신청곡"));
        assertTrue(full.toString().contains("고요한 바다"));
    }

    @Test public void splitsLongApprovedTextForTtsWatchdog() {
        String s = "가".repeat(350);
        List<String> chunks = GuestTalkShow.chunks(s, 90);
        assertEquals(4, chunks.size());
        for (String line : chunks) assertTrue(line.length() <= 90);
        assertEquals(s, String.join("", chunks));
    }

    @Test public void dateAndEpisodeChangeTalkTopics() {
        RadioConversationScript.Line[] a = GuestTalkShow.beforeSong(
                "노래", 0, LocalDate.of(2026, 10, 30), 0, "", "", "", false);
        RadioConversationScript.Line[] b = GuestTalkShow.beforeSong(
                "노래", 1, LocalDate.of(2026, 10, 30), 0, "", "", "", false);
        assertNotEquals(a[3].text, b[3].text);
    }
}
