package com.xanfiend.tftoverlay;

import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pool's SharedPreferences-backed state, run under Robolectric so the real
 * Android framework SharedPreferences works on the JVM (no device needed).
 * Each test gets a fresh app context, so prefs start empty.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PoolTest {

    private Pool pool;

    @Before public void setUp(){
        pool = new Pool(ApplicationProvider.getApplicationContext());
    }

    @Test public void econStatePersistsAndGoldClampsAtZero(){
        // clamp check first, then set the value we expect to persist
        pool.setGold(-5); assertEquals("gold never goes negative", 0, pool.getGold());
        pool.setGold(42); assertEquals(42, pool.getGold());
        pool.setStreak(-3); assertEquals(-3, pool.getStreak());
        pool.setLevel(7);  assertEquals(7, pool.getLevel());
        pool.setStageRound("3-2"); assertEquals("3-2", pool.getStageRound());
        // a fresh Pool over the same context sees the persisted values
        Pool reopened = new Pool(ApplicationProvider.getApplicationContext());
        assertEquals(42, reopened.getGold());
        assertEquals(7, reopened.getLevel());
        assertEquals("3-2", reopened.getStageRound());
    }

    @Test public void privacyFlagDefaultsFalseThenSticks(){
        assertFalse(pool.getPrivacySeen());
        pool.setPrivacySeen(true);
        assertTrue(pool.getPrivacySeen());
    }

    @Test public void oppUnitsRoundTripWithItems(){
        List<Pool.OppUnit> board = Arrays.asList(
            new Pool.OppUnit("Jhin", 3, new ArrayList<>(Arrays.asList("Infinity Edge", "Last Whisper"))),
            new Pool.OppUnit("Aatrox", 2, new ArrayList<String>())); // no items
        pool.setOppUnits(1, board);

        List<Pool.OppUnit> back = pool.getOppUnits(1);
        assertEquals(2, back.size());
        assertEquals("Jhin", back.get(0).name);
        assertEquals(3, back.get(0).stars);
        assertEquals(Arrays.asList("Infinity Edge", "Last Whisper"), back.get(0).items);
        assertEquals("Aatrox", back.get(1).name);
        assertTrue("a unit with no items round-trips to empty", back.get(1).items.isEmpty());
    }

    @Test public void legacyNameStarStringStillParses(){
        // older format written by the Map-based setter (no items field)
        Map<String,Integer> legacy = new java.util.LinkedHashMap<>();
        legacy.put("Corki", 2);
        pool.setOppBoard(2, legacy);

        // the rich reader tolerates the 2-field format -> empty items
        List<Pool.OppUnit> units = pool.getOppUnits(2);
        assertEquals(1, units.size());
        assertEquals("Corki", units.get(0).name);
        assertEquals(2, units.get(0).stars);
        assertTrue(units.get(0).items.isEmpty());
    }

    @Test public void mapReaderIgnoresItemsField(){
        // a rich write with items is still readable by the Map accessor (name->stars)
        pool.setOppUnits(3, Arrays.asList(
            new Pool.OppUnit("Vex", 2, new ArrayList<>(Arrays.asList("Rabadon's Deathcap")))));
        Map<String,Integer> m = pool.getOppBoard(3);
        assertEquals(Integer.valueOf(2), m.get("Vex"));
    }

    @Test public void aggregatorsSkipEmptySlots(){
        pool.setOppUnits(1, Arrays.asList(new Pool.OppUnit("Jhin", 1, null)));
        pool.setOppUnits(4, Arrays.asList(new Pool.OppUnit("Vex", 2, null)));
        assertEquals(2, pool.getAllOppUnits().size());   // slots 1 and 4 only
        assertEquals(2, pool.getAllOppBoards().size());
        pool.clearOppBoard(1);
        assertEquals(1, pool.getAllOppUnits().size());
    }

    @Test public void portraitCalibrationRoundTrips(){
        assertFalse(pool.hasOppPortraitCal());
        assertEquals(0, pool.oppPortraitCount());
        pool.setOppPortrait(1, 120, 240);
        pool.setOppPortrait(2, 300, 240);
        assertTrue(pool.hasOppPortraitCal());
        assertEquals(2, pool.oppPortraitCount());
        assertArrayEquals(new int[]{120, 240}, pool.getOppPortrait(1));
        pool.clearOppPortraits();
        assertEquals(0, pool.oppPortraitCount());
        assertNull(pool.getOppPortrait(1));
    }

    @Test public void resetClearsEconAndOppBoards(){
        pool.setGold(50);
        pool.setStreak(4);
        pool.setStageRound("4-2");
        pool.setOppUnits(1, Arrays.asList(new Pool.OppUnit("Jinx", 2, null)));
        pool.setOppUnits(3, Arrays.asList(new Pool.OppUnit("Aatrox", 1, null)));

        pool.reset();

        assertEquals("gold cleared", 0, pool.getGold());
        assertEquals("streak cleared", 0, pool.getStreak());
        assertEquals("stageRound cleared", "", pool.getStageRound());
        assertEquals("opp boards cleared", 0, pool.getAllOppUnits().size());
    }

    @Test public void resetPreservesLevelAndPrivacyFlag(){
        pool.setLevel(6);
        pool.setPrivacySeen(true);

        pool.reset();

        assertEquals("level survives reset", 6, pool.getLevel());
        assertTrue("privacy flag survives reset", pool.getPrivacySeen());
    }

    @Test public void addAndSeenCountAndIsEmpty(){
        assertTrue(pool.isEmpty());
        pool.add("Jinx", 1);
        assertFalse(pool.isEmpty());
        assertEquals(1, pool.seenCount("Jinx"));
        pool.add("Jinx", 2);
        assertEquals(3, pool.seenCount("Jinx"));
        // subtract back to zero removes the entry
        pool.add("Jinx", -3);
        assertEquals(0, pool.seenCount("Jinx"));
        assertTrue(pool.isEmpty());
    }

    @Test public void addNeverGoesNegative(){
        pool.add("Corki", 1);
        pool.add("Corki", -99);
        assertEquals(0, pool.seenCount("Corki"));
    }

    @Test public void recentListCapsAtSix(){
        // Pool.RECENT_MAX = 6: only the 6 most-recently-touched champs are kept
        String[] champs = {"A","B","C","D","E","F","G"};
        for (String c : champs) pool.add(c, 1);
        List<String> recent = pool.recentList();
        assertEquals("capped at 6", 6, recent.size());
        assertEquals("most recent first", "G", recent.get(0));
        assertFalse("oldest evicted", recent.contains("A"));
    }

    @Test public void addOppCapsAtSeven(){
        for (int i = 0; i < 10; i++) pool.addOpp("Jinx", 1);
        assertEquals("opp count capped at 7", 7, pool.oppCount("Jinx"));
    }
}
