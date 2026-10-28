package com.maru.musiclive;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.*;
public final class SunoArtistProductionCatalogTest {
    @Test public void all32ArePlannedWithUniqueVocalInstructions(){
        assertEquals(32,SunoArtistProductionCatalog.count());
        Set<String> singerVoices=new HashSet<>();
        Set<String> songTitles=new HashSet<>();
        Map<String,Integer> byGenre=new HashMap<>();
        for(SunoArtistProductionCatalog.Profile p:SunoArtistProductionCatalog.all()){
            assertEquals(SunoArtistProductionCatalog.Status.DESIGN_ONLY,p.status);
            assertTrue(singerVoices.add(p.timbre+" / "+p.technique));
            assertTrue(songTitles.add(p.originalTitle));
            assertTrue(p.stylePrompt().length()<1000);
            assertFalse(p.stylePrompt().contains("김기덕"));
            assertFalse(p.stylePrompt().contains("imitate"));
            byGenre.merge(p.genre,1,Integer::sum);
        }
        assertEquals(Integer.valueOf(8),byGenre.get("발라드"));
        assertEquals(Integer.valueOf(7),byGenre.get("트로트"));
        assertEquals(Integer.valueOf(7),byGenre.get("팝송"));
        assertEquals(Integer.valueOf(5),byGenre.get("중국노래"));
        assertEquals(Integer.valueOf(5),byGenre.get("러시아노래"));
    }
}
