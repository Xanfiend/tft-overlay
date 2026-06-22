package com.xanfiend.tftoverlay;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Opponent threat aggregation + the Phase-2 item-aware tech read. Pure. */
public class OppScoutTest {

    private static String champ(int cost){
        String[] t = SetData.CHAMPS[cost];
        return t.length > 0 ? t[0] : "Unknown";
    }
    private static Pool.OppUnit unit(String name, int stars, String... items){
        return new Pool.OppUnit(name, stars, new ArrayList<>(Arrays.asList(items)));
    }
    private static List<List<Pool.OppUnit>> lobby(List<Pool.OppUnit>... boards){
        return new ArrayList<>(Arrays.asList(boards));
    }

    @Test public void emptyInputHasNoData(){
        assertFalse(OppScout.analyzeUnits(null).hasData());
        assertFalse(OppScout.analyzeUnits(new ArrayList<>()).hasData());
        assertFalse(OppScout.analyze(null).hasData());
    }

    @Test public void everyUnitGetsExactlyOneRole(){
        List<Pool.OppUnit> b = Arrays.asList(
            unit(champ(1), 2), unit(champ(2), 1), unit(champ(5), 3));
        OppScout.Profile p = OppScout.analyzeUnits(lobby(b));
        assertTrue(p.hasData());
        assertEquals(1, p.boards);
        assertEquals(3, p.front + p.back + p.flank); // partition is total units
        assertTrue(p.topBoardVal > 0);
        assertTrue(p.avgBoardVal > 0);
    }

    @Test public void healingItemsRaiseAntiHealTip(){
        List<Pool.OppUnit> b = Arrays.asList(unit(champ(4), 2, "Bloodthirster"));
        OppScout.Profile p = OppScout.analyzeUnits(lobby(b));
        assertEquals(1, p.healItems);
        assertTrue("expected an anti-heal item-read tip",
            anyContains(p.techTips, "healing"));
    }

    @Test public void adItemSkewIsCalled(){
        List<Pool.OppUnit> b = Arrays.asList(
            unit(champ(4), 2, "Infinity Edge"),
            unit(champ(3), 1, "Deathblade"));
        OppScout.Profile p = OppScout.analyzeUnits(lobby(b));
        assertEquals(2, p.adItems);
        assertEquals(0, p.apItems);
        assertTrue(anyContains(p.techTips, "skew AD"));
    }

    @Test public void noItemsMeansNoItemReadTips(){
        List<Pool.OppUnit> b = Arrays.asList(unit(champ(1), 1), unit(champ(2), 2));
        OppScout.Profile p = OppScout.analyzeUnits(lobby(b));
        assertEquals(0, p.adItems + p.apItems + p.healItems);
        for(String tip : p.techTips)
            assertFalse("no item-read tip without scanned items", tip.startsWith("Item read:"));
    }

    @Test public void mapAdapterMatchesUnitPathWhenNoItems(){
        // The Map-based analyze() must produce the same core read as analyzeUnits
        // for item-less boards (it just wraps maps as item-less units).
        java.util.Map<String,Integer> m = new java.util.LinkedHashMap<>();
        m.put(champ(1), 2); m.put(champ(5), 1);
        OppScout.Profile viaMap = OppScout.analyze(Arrays.asList(m));
        assertTrue(viaMap.hasData());
        assertEquals(2, viaMap.front + viaMap.back + viaMap.flank);
    }

    private static boolean anyContains(List<String> xs, String sub){
        for(String x : xs) if(x.contains(sub)) return true;
        return false;
    }
}
