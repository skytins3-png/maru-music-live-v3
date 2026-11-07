package com.maru.ahwanvoice;

import static org.junit.Assert.*;
import org.junit.Test;

public class ShowScenePlanTest {
    @Test public void sixCuesSwitchBetweenMCInterviewAndSingers(){
        assertEquals(ShowScenePlan.Mode.BRIDGE,ShowScenePlan.modeForCue(-1));
        assertEquals(ShowScenePlan.Mode.BRIDGE,ShowScenePlan.modeForCue(0));
        assertEquals(ShowScenePlan.Mode.TALK,ShowScenePlan.modeForCue(1));
        assertEquals(ShowScenePlan.Mode.STAGE,ShowScenePlan.modeForCue(2));
        assertEquals(ShowScenePlan.Mode.TALK,ShowScenePlan.modeForCue(3));
        assertEquals(ShowScenePlan.Mode.STAGE,ShowScenePlan.modeForCue(4));
        assertEquals(ShowScenePlan.Mode.BRIDGE,ShowScenePlan.modeForCue(5));
    }
    @Test public void eightFictionalDossiersDoNotPretendToBeCompletedSingers(){
        assertEquals(8,ShowScenePlan.CAST.length);
        assertArrayEquals(new String[]{"아환"},
                ShowScenePlan.peopleForCue(0,"강태준",0));
        assertArrayEquals(new String[]{"아환","윤솔"},
                ShowScenePlan.peopleForCue(1,"강태준",0));
        assertArrayEquals(new String[]{"강태준"},
                ShowScenePlan.peopleForCue(2,"강태준",0));
        assertArrayEquals(new String[]{"아환","하린"},
                ShowScenePlan.peopleForCue(3,"강태준",0));
        assertArrayEquals(new String[]{"아환","민혁"},
                ShowScenePlan.peopleForCue(3,"민도윤",1));
        assertArrayEquals(new String[]{"서하린"},
                ShowScenePlan.peopleForCue(4,"서하린",20));
        assertArrayEquals(new String[]{"아환"},
                ShowScenePlan.peopleForCue(5,"강태준",0));
    }
    @Test public void invalidSingerNameNotDisplayedAsMarkup(){
        assertArrayEquals(new String[]{"가상 AI 가수 후보"},
                ShowScenePlan.peopleForCue(2,null,0));
        assertArrayEquals(new String[]{"가수"},
                ShowScenePlan.peopleForCue(2,"<가수>",0));
    }
}
