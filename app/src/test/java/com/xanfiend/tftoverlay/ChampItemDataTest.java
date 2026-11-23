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
        // with two carries present, the higher-tier one is chosen and the other
        // is listed as a secondary — robust to which specific tier each holds
        CompAdvisor.Rec r = CompAdvisor.recommend(Arrays.asList("Sivir", "Aphelios"));
        assertTrue(r.carry.equals("Aphelios") || r.carry.equals("Sivir"));
        String other = r.carry.equals("Aphelios") ? "Sivir" : "Aphelios";
        assertTrue("chosen carry must rank >= the other",
            rank(ChampItemData.tierOf(r.carry)) >= rank(ChampItemData.tierOf(other)));
        assertTrue("the unchosen carry is a secondary", r.alsoCarries.contains(other));
    }

    private static int rank(String tier){
        switch(tier){ case "S": return 4; case "A": return 3; case "B": return 2; case "C": return 1; default: return 0; }
    }
}
