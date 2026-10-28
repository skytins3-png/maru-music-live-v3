package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RadioConversationScriptTest {
    @Test public void alternatingPresentersAlwaysIntroduceRealSongTitle() {
        for (int i = 0; i < 8; i++) {
            RadioConversationScript.Line[] lines =
                    RadioConversationScript.beforeSong("다시 태어나면", i);
            assertEquals(5, lines.length);
            assertEquals(RadioConversationScript.Speaker.AHWAN, lines[0].speaker);
            assertEquals(RadioConversationScript.Speaker.YUNSOL, lines[1].speaker);
            assertEquals(RadioConversationScript.Speaker.AHWAN, lines[2].speaker);
            assertEquals(RadioConversationScript.Speaker.YUNSOL, lines[3].speaker);
            assertEquals(RadioConversationScript.Speaker.AHWAN, lines[4].speaker);
            assertTrue(lines[2].text.contains("다시 태어나면"));
            for (RadioConversationScript.Line line : lines) assertTrue(line.text.length() <= 120);
        }
    }

    @Test public void scriptsDoNotInventViewerEventsOrImpersonateOldShows() {
        for (int i = 0; i < 4; i++) {
            for (RadioConversationScript.Line line :
                    RadioConversationScript.beforeSong("새 곡", i)) {
                assertFalse(line.text.contains("김기덕"));
                assertFalse(line.text.contains("2시의 데이트"));
                assertFalse(line.text.contains("선물을 보내주신"));
                assertFalse(line.text.contains("사연을 보내주신"));
            }
        }
    }

    @Test public void unexpectedTitlesAreSanitizedAndLimited() {
        assertEquals("다음 음악", RadioConversationScript.cleanTitle(null));
        assertEquals("다음 음악", RadioConversationScript.cleanTitle("  "));
        assertEquals("가을 바다", RadioConversationScript.cleanTitle("가을\n바다"));
        assertEquals(60, RadioConversationScript.cleanTitle("아".repeat(200)).length());
    }
}
