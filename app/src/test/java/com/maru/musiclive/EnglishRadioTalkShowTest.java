package com.maru.musiclive;

import static org.junit.Assert.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class EnglishRadioTalkShowTest {
    @Test public void episodeHasNaturalEnglishHostAndExactlyOneGuest() {
        RadioConversationScript.Line[] dialogue = EnglishRadioTalkShow.beforeSong(
                "Rainy Road", 4, LocalDate.of(2026,10,30), 1, "", "", "", false);
        assertTrue(dialogue.length >= 40);
        Set<Integer> guests = new HashSet<>();
        StringBuilder full = new StringBuilder();
        for (RadioConversationScript.Line line : dialogue) {
            if (line.artist != null) guests.add(line.artist.id);
            full.append(line.text).append(" ");
            assertFalse("No Korean line in English radio", line.text.matches(".*[가-힣].*"));
            assertTrue("Short TTS utterances", line.text.length() < 240);
        }
        assertEquals("One guest per episode", 1, guests.size());
        assertTrue(full.toString().contains("Ha ha"));
        assertTrue(full.toString().contains("Rainy Road"));
        assertFalse(full.toString().contains("listener story the producer approved"));
    }
    @Test public void approvedListenerAndRequestAreDistinguishedFromFiction() {
        RadioConversationScript.Line[] episode = EnglishRadioTalkShow.beforeSong(
                "A Good Day", 0, LocalDate.of(2026,10,30), 1,
                "A listener", "I met an old friend after twenty long years.",
                "This is a producer-reviewed research note.", true);
        StringBuilder full = new StringBuilder();
        for(RadioConversationScript.Line line:episode)full.append(line.text);
        assertTrue(full.toString().contains("I met an old friend"));
        assertTrue(full.toString().contains("producer-reviewed research note"));
        assertTrue(full.toString().contains("listener request"));
    }
    @Test public void consecutiveEpisodesChangeTopicWithoutChangingHosts() {
        LocalDate day = LocalDate.of(2026,10,30);
        RadioConversationScript.Line[] a = EnglishRadioTalkShow.beforeSong(
                "One",0,day,1,"","","",false);
        RadioConversationScript.Line[] b = EnglishRadioTalkShow.beforeSong(
                "Two",1,day,1,"","","",false);
        assertNotEquals(a[2].text,b[2].text);
    }
}
