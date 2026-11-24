package com.xanfiend.tftoverlay;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Carry build data + the CompAdvisor.recommend logic that reads it. Pure. */
public class ChampItemDataTest {

    @Test public void knownCarryHasUsableBuild(){
        assertTrue(ChampItemData.has("Aphelios"));
        ChampItemData.Build b = ChampItemData.get("Aphelios");
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
        for(String champ : new String[]{"MasterYi","Pebbles","Aphelios","Sivir"}){
            assertTrue(champ+" must be a known carry", ChampItemData.has(champ));
            String t = ChampItemData.tierOf(champ);
            assertTrue("tier '"+t+"' for "+champ+" must be S/A/B/C or empty",
                Arrays.asList("", "S", "A", "B", "C").contains(t));
        }
    }

    @Test public void recommendBuildsAroundTheCarry(){
        CompAdvisor.Rec r = CompAdvisor.recommend(Arrays.asList("Aphelios"));
        assertTrue(r.hasBoard);
        assertEquals("Aphelios", r.carry);
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

    @Test public void recommendPicksTheHigherTierCarry(){
        // Aphelios is S, Sivir is A: the S carry must win, the other is secondary
        CompAdvisor.Rec r = CompAdvisor.recommend(Arrays.asList("Sivir", "Aphelios"));
        assertEquals("Aphelios", r.carry);
        assertTrue("the unchosen carry is a secondary", r.alsoCarries.contains("Sivir"));
    }
}
