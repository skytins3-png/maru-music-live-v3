package com.maru.musiclive;

import org.junit.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

public final class VirtualArtistCatalogTest {
    @Test public void atLeastThirtyTwoDistinctFictionalSingersExist() {
        assertEquals(32, VirtualArtistCatalog.all().size());
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        int male = 0, female = 0;
        for (VirtualArtistCatalog.Artist p : VirtualArtistCatalog.all()) {
            assertTrue(ids.add(p.id));
            assertTrue(names.add(p.name));
            assertFalse(p.motto.isEmpty());
            assertFalse(p.viewpoint.isEmpty());
            assertFalse(p.genre.isEmpty());
            if(p.vocalType == VirtualArtistCatalog.VocalType.MALE) male++;
            else female++;
        }
        assertEquals(20,male);
        assertEquals(12,female);
    }
    @Test public void sixteenSongsVisitAllThirtyTwoGuestsWithoutRepeat() {
        for(long day: new long[]{0L,20800L,20801L}) {
            Set<String> visited = new HashSet<>();
            for(int i=0;i<16;i++){
                VirtualArtistCatalog.Artist[] pair=VirtualArtistCatalog.pairFor(day,i);
                assertNotEquals(pair[0].name,pair[1].name);
                assertTrue(visited.add(pair[0].name));
                assertTrue(visited.add(pair[1].name));
            }
            assertEquals(32,visited.size());
        }
    }
    @Test public void thirtyTwoPersonScheduleChangesEachDay() {
        long day=LocalDate.of(2026,10,28).toEpochDay();
        assertNotEquals(VirtualArtistCatalog.pairFor(day,0)[0].name,
                VirtualArtistCatalog.pairFor(day+1,0)[0].name);
    }
}
