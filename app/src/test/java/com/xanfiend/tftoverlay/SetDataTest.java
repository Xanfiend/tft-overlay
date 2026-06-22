package com.xanfiend.tftoverlay;

import org.junit.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

/** Structural integrity of the set roster — guards manual + remote set updates. */
public class SetDataTest {

    @Test public void champsTableShape(){
        assertEquals("6 cost tiers incl. the unused index 0", 6, SetData.CHAMPS.length);
        assertEquals("tier 0 is the empty sentinel", 0, SetData.CHAMPS[0].length);
        for(int c = 1; c <= 5; c++)
            assertTrue("cost "+c+" tier must be non-empty", SetData.CHAMPS[c].length > 0);
    }

    @Test public void sizeTableMatchesTiers(){
        assertEquals(6, SetData.SIZE.length);
        assertEquals(0, SetData.SIZE[0]);
        for(int c = 1; c <= 5; c++)
            assertTrue("pool size for cost "+c+" must be positive", SetData.SIZE[c] > 0);
    }

    @Test public void oddsCoverEveryLevelAndCost(){
        // RollMath / odds tab index ODDS[level][cost-1] up to level 10, cost 5
        assertTrue("need rows up to level 10", SetData.ODDS.length >= 11);
        for(int lvl = 1; lvl < SetData.ODDS.length; lvl++){
            assertEquals("level "+lvl+" must list 5 cost columns", 5, SetData.ODDS[lvl].length);
            for(int i = 0; i < 5; i++){
                int pct = SetData.ODDS[lvl][i];
                assertTrue("odds % out of range at lvl "+lvl, pct >= 0 && pct <= 100);
            }
        }
    }

    @Test public void godsPresent(){
        assertTrue(SetData.GODS.length > 0);
    }

    @Test public void noChampionAppearsInTwoTiers(){
        List<String> all = new ArrayList<>();
        for(int c = 1; c <= 5; c++) for(String n : SetData.CHAMPS[c]) all.add(n);
        assertEquals("a champion must appear in exactly one cost tier",
            all.size(), new HashSet<>(all).size());
    }
}
