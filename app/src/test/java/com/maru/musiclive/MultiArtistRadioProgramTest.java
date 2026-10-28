package com.maru.musiclive;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MultiArtistRadioProgramTest {
    @Test public void fullCastSpeaksInSixteenSongCycle() {
        LocalDate day = LocalDate.of(2026,10,28);
        Set<String> spoken = new HashSet<>();
        for(int song=0;song<16;song++) {
            RadioConversationScript.Line[] lines = MultiArtistRadioProgram.beforeSong(
                "다시 태어나면",song,day,true,Collections.emptyList(),Collections.emptyList());
            assertEquals(9,lines.length);
            assertEquals(RadioConversationScript.Speaker.AHWAN,lines[0].speaker);
            assertTrue(lines[8].text.contains("다시 태어나면"));
            for(RadioConversationScript.Line line:lines) {
                assertFalse(line.text.contains("김기덕"));
                if(line.speaker == RadioConversationScript.Speaker.GUEST) {
                    assertNotNull(line.artist);
                    spoken.add(line.artist.name);
                } else assertNull(line.artist);
            }
        }
        assertEquals(32,spoken.size());
    }

    @Test public void noFabricatedThanksAndNoPrivateNicknames() {
        LocalDate day = LocalDate.of(2026,10,28);
        RadioConversationScript.Line[] quiet=MultiArtistRadioProgram.beforeSong(
             "초원",1,day,true,Collections.emptyList(),Collections.emptyList());
        assertEquals(9,quiet.length);
        RadioConversationScript.Line[] thanks=MultiArtistRadioProgram.beforeSong(
             "초원",1,day,true,Collections.singletonList("SECRET-GIFT"),
             Collections.singletonList("SECRET-FOLLOW"));
        assertEquals(10,thanks.length);
        assertTrue(thanks[1].text.contains("선물과 팔로우"));
        for(RadioConversationScript.Line line:thanks) {
            assertFalse(line.text.contains("SECRET-GIFT"));
            assertFalse(line.text.contains("SECRET-FOLLOW"));
        }
    }

    @Test public void explicitSingerAssignmentIsHonoredWithoutDuplicateGuests() {
        LocalDate d=LocalDate.of(2026,10,28);
        for(int singer=1;singer<=32;singer++) {
            RadioConversationScript.Line[] spoken =
                    MultiArtistRadioProgram.beforeSong(
                            "초원",4,d,true,Collections.emptyList(),
                            Collections.emptyList(),singer);
            assertEquals(singer,spoken[1].artist.id);
            assertNotEquals(spoken[1].artist.id,spoken[5].artist.id);
        }
    }

    @Test public void dailyConversationAndGuestOrderRotate() {
        LocalDate date=LocalDate.of(2026,10,28);
        RadioConversationScript.Line[] a=MultiArtistRadioProgram.beforeSong(
             "아침",0,date,true,Collections.emptyList(),Collections.emptyList());
        RadioConversationScript.Line[] b=MultiArtistRadioProgram.beforeSong(
             "아침",0,date.plusDays(1),true,Collections.emptyList(),Collections.emptyList());
        assertNotEquals(a[1].artist.name,b[1].artist.name);
        assertNotEquals(a[0].text,b[0].text);
    }
}
