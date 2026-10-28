package com.maru.musiclive;

import org.junit.Test;
import java.time.LocalDate;
import java.util.Collections;
import static org.junit.Assert.*;

public final class DailyRadioProgramTest {
    @Test public void thirtyOneConsecutiveDaysHaveUniqueOpeningTopics() {
        LocalDate start = LocalDate.of(2026, 10, 28);
        java.util.Set<String> openings = new java.util.HashSet<>();
        for (int i = 0; i < 31; i++) {
            RadioConversationScript.Line[] lines =
                    DailyRadioProgram.beforeSong("가을바다", 0, start.plusDays(i));
            assertEquals(5, lines.length);
            assertTrue(openings.add(lines[0].text));
            assertTrue(lines[2].text.contains("가을바다"));
            assertEquals(RadioConversationScript.Speaker.AHWAN, lines[0].speaker);
            assertEquals(RadioConversationScript.Speaker.YUNSOL, lines[1].speaker);
            assertEquals(RadioConversationScript.Speaker.AHWAN, lines[2].speaker);
            assertEquals(RadioConversationScript.Speaker.YUNSOL, lines[3].speaker);
            assertEquals(RadioConversationScript.Speaker.AHWAN, lines[4].speaker);
        }
    }

    @Test public void sameDateAndSongProduceIdenticalOfflineOutput() {
        LocalDate day = LocalDate.of(2026, 10, 28);
        for (int i = 0; i < 12; i++) {
            RadioConversationScript.Line[] a = DailyRadioProgram.beforeSong("초원", i, day);
            RadioConversationScript.Line[] b = DailyRadioProgram.beforeSong("초원", i, day);
            for (int j = 0; j < a.length; j++) assertEquals(a[j].text, b[j].text);
        }
    }

    @Test public void yesterdayAndTomorrowDifferWithoutInternetOrReinstall() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        assertNotEquals(
                DailyRadioProgram.beforeSong("가을바다", 2, today)[0].text,
                DailyRadioProgram.beforeSong("가을바다", 2, today.plusDays(1))[0].text);
    }

    @Test public void nobodyIsMadeUpAndNoNicknameLeaks() {
        RadioConversationScript.Line[] quiet = DailyRadioProgram.beforeSong(
                "초원", 3, LocalDate.of(2026, 10, 28),
                Collections.emptyList(), Collections.emptyList());
        assertEquals(5, quiet.length);
        RadioConversationScript.Line[] thanks = DailyRadioProgram.beforeSong(
                "초원", 3, LocalDate.of(2026, 10, 28),
                Collections.singletonList("private-gift-name"),
                Collections.singletonList("private-follower-name"));
        assertEquals(6, thanks.length);
        for (RadioConversationScript.Line line : thanks) {
            assertFalse(line.text.contains("private-gift-name"));
            assertFalse(line.text.contains("private-follower-name"));
            assertFalse(line.text.contains("김기덕"));
        }
    }
}
