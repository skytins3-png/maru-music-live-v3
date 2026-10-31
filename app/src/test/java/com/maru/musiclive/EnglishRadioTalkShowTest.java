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
    @Test public void compactEnglishTalkHasExactlyFourBanterTurnsAndOneSongCue() {
        LocalDate day = LocalDate.of(2026, 10, 31);
        RadioConversationScript.Line[] first =
                EnglishRadioTalkShow.shortBeforeSong("Autumn Road", 0, day, 1);
        RadioConversationScript.Line[] next =
                EnglishRadioTalkShow.shortBeforeSong("Spring Road", 1, day, 1);
        assertEquals(5, first.length);
        assertEquals(5, next.length);
        assertEquals(RadioConversationScript.Speaker.AHWAN, first[0].speaker);
        assertEquals(RadioConversationScript.Speaker.GUEST, first[1].speaker);
        assertEquals(RadioConversationScript.Speaker.AHWAN, first[2].speaker);
        assertEquals(RadioConversationScript.Speaker.GUEST, first[3].speaker);
        assertEquals(RadioConversationScript.Speaker.AHWAN, first[4].speaker);
        int words=0;
        for (RadioConversationScript.Line line : first) {
            assertTrue("one concise utterance", line.text.length() < 125);
            words += line.text.trim().split("\\s+").length;
            assertFalse("do not repeat welcome message", line.text.toLowerCase().contains("welcome back"));
        }
        assertTrue("less than 70 words", words < 70);
        assertTrue(first[4].text.contains("Autumn Road"));
        assertNotEquals("fresh conversation next song", first[0].text, next[0].text);
        assertNotNull(first[1].artist);
        assertEquals(first[1].artist.id, first[3].artist.id);
    }

    @Test public void producerApprovedStoryStaysShortAndIsNotInvented() {
        LocalDate day=LocalDate.of(2026, 10, 31);
        RadioConversationScript.Line[] approved =
                EnglishRadioTalkShow.shortBeforeSong(
                    "Family Song", 8, day, 1, "Rose",
                    "My childhood friend sent me a letter and made me smile all day.");
        assertEquals(5, approved.length);
        String joined="";
        for (RadioConversationScript.Line line : approved) joined+=line.text+" ";
        assertTrue(joined.contains("Rose"));
        assertTrue(joined.contains("childhood friend"));
        assertTrue(joined.contains("Family Song"));
        RadioConversationScript.Line[] noStory =
                EnglishRadioTalkShow.shortBeforeSong(
                    "Family Song", 8, day, 1, "", "");
        String plain="";
        for (RadioConversationScript.Line line : noStory) plain+=line.text+" ";
        assertFalse(plain.contains("childhood friend"));
    }

}
