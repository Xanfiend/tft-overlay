package com.xanfiend.tftoverlay;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Carry build data + the CompAdvisor.recommend logic that reads it. Pure. */
public class ChampItemDataTest {

    @Test public void knownCarryHasUsableBuild(){
        assertTrue(ChampItemData.has("Corki"));
        ChampItemData.Build b = ChampItemData.get("Corki");
        assertNotNull(b);
        assertTrue("a carry must list items", b.items.length > 0);
        assertFalse("a carry must name a comp", b.comp.isEmpty());
    }

    @Test public void unknownChampIsNotACarry(){
        assertFalse(ChampItemData.has("__nobody__"));
        assertNull(ChampItemData.get("__nobody__"));
        assertEquals("", ChampItemData.tierOf("__nobody__"));
    }

    @Test public void tiersAreValidLabels(){
        for(String champ : new String[]{"Corki","Jhin","Samira","Xayah"}){
            String t = ChampItemData.tierOf(champ);
            assertTrue("tier '"+t+"' for "+champ+" must be S/A/B/C or empty",
                Arrays.asList("", "S", "A", "B", "C").contains(t));
        }
    }

    @Test public void recommendBuildsAroundTheCarry(){
        CompAdvisor.Rec r = CompAdvisor.recommend(Arrays.asList("Corki"));
        assertTrue(r.hasBoard);
        assertEquals("Corki", r.carry);
        assertTrue(r.items.length > 0);
        assertFalse(r.comp.isEmpty());
    }

    @Test public void recommendOnEmptyBoardHasNothing(){
        CompAdvisor.Rec r = CompAdvisor.recommend(Collections.<String>emptyList());
        assertFalse(r.hasBoard);
        assertEquals("", r.carry);
    }

    @Test public void recommendWithNoCarriesStillReadsBoard(){
        CompAdvisor.Rec r = CompAdvisor.recommend(Arrays.asList("__filler__"));
        assertTrue(r.hasBoard);     // a board was scanned
        assertEquals("", r.carry);  // but no itemizable carry on it
    }
}
