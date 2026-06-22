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

    @Test public void biggestThreatIsHighestStarNonTank(){
        // two backline carries; the 3-star one outweighs the 1-star one
        List<Pool.OppUnit> b = Arrays.asList(unit("Jhin", 3), unit("Xayah", 1));
        OppScout.Profile p = OppScout.analyzeUnits(lobby(b));
        assertEquals("Jhin", p.topThreat);
        assertEquals(3, p.topThreatStars);
        assertTrue(anyContains(p.tips, "Biggest threat: Jhin"));
    }

    @Test public void flankHeavyLobbyIsFlagged(){
        List<Pool.OppUnit> b = Arrays.asList(
            unit("Talon", 2), unit("Akali", 1), unit("Pyke", 2)); // all FLANK
        OppScout.Profile p = OppScout.analyzeUnits(lobby(b));
        assertEquals(3, p.flank);
        assertTrue(p.flankHeavy);
        assertTrue(anyContains(p.tips, "assassin"));
    }

    @Test public void snowballingOpponentIsCalledOut(){
        // one fed board (5-cost 3-star) well above a thin board -> dodge advice
        List<Pool.OppUnit> strong = Arrays.asList(unit(champ(5), 3));
        List<Pool.OppUnit> weak   = Arrays.asList(unit(champ(1), 1));
        OppScout.Profile p = OppScout.analyzeUnits(lobby(strong, weak));
        assertEquals(2, p.boards);
        assertTrue(p.topBoardVal >= p.avgBoardVal * 3 / 2);
        assertTrue(anyContains(p.tips, "ahead of the lobby"));
    }

    @Test public void hookAndAoeProduceSpecificTips(){
        OppScout.Profile hook = OppScout.analyzeUnits(lobby(
            Arrays.asList(unit("Blitzcrank", 2))));
        assertTrue(hook.hooks > 0);
        assertTrue(anyContains(hook.tips, "Hook"));

        OppScout.Profile aoe = OppScout.analyzeUnits(lobby(
            Arrays.asList(unit("AurelionSol", 2), unit("Gragas", 2))));
        assertEquals(2, aoe.aoe);
        assertTrue(anyContains(aoe.tips, "AoE"));
    }

    private static boolean anyContains(List<String> xs, String sub){
        for(String x : xs) if(x.contains(sub)) return true;
        return false;
    }
}
