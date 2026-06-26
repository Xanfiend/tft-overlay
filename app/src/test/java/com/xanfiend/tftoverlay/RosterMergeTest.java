package com.xanfiend.tftoverlay;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/* Pure JVM tests for multi-pass roster reconciliation. */
public class RosterMergeTest {

    private static final List<String> ROSTER = Arrays.asList(
        "Jhin", "Vex", "Corki", "Samira", "Kai'Sa", "AurelionSol");

    @Test public void tallyCanonicalizesAndCounts() {
        // "Corkl" is a 1-letter OCR slip of Corki; "zzz" is junk and dropped.
        LinkedHashMap<String,Integer> t = RosterMerge.tally(
            Arrays.asList("Jhin", "Corkl", "Corki", "zzz"), ROSTER);
        assertEquals(Integer.valueOf(1), t.get("Jhin"));
        assertEquals(Integer.valueOf(2), t.get("Corki"));
        assertFalse(t.containsKey("zzz"));
        assertEquals(2, t.size());
    }

    @Test public void tallyHandlesNulls() {
        assertTrue(RosterMerge.tally(null, ROSTER).isEmpty());
        assertTrue(RosterMerge.tally(Arrays.asList("Jhin"), null).isEmpty());
    }

    @Test public void starsFromCopies() {
        assertEquals(0, RosterMerge.starsFromCopies(0));
        assertEquals(1, RosterMerge.starsFromCopies(1));
        assertEquals(1, RosterMerge.starsFromCopies(2));
        assertEquals(2, RosterMerge.starsFromCopies(3));
        assertEquals(2, RosterMerge.starsFromCopies(8));
        assertEquals(3, RosterMerge.starsFromCopies(9));
    }

    @Test public void unresolvedCount() {
        LinkedHashMap<String,Integer> t = RosterMerge.tally(
            Arrays.asList("Jhin", "Corki"), ROSTER);   // 2 resolved
        assertEquals(2, RosterMerge.unresolvedCount(4, t));   // 4 detected - 2 named
        assertEquals(0, RosterMerge.unresolvedCount(2, t));
        assertEquals(0, RosterMerge.unresolvedCount(1, t));   // never negative
    }

    @Test public void mergeNoDoubleCountPrimaryWins() {
        LinkedHashMap<String,Integer> primary = new LinkedHashMap<>();
        primary.put("Jhin", 1);
        primary.put("Samira", 2);
        LinkedHashMap<String,Integer> secondary = new LinkedHashMap<>();
        secondary.put("Jhin", 3);   // same unit, different pass — should NOT add
        secondary.put("Corki", 1);  // new unit — should appear
        LinkedHashMap<String,Integer> merged = RosterMerge.mergeNoDoubleCount(primary, secondary);
        assertEquals(Integer.valueOf(1), merged.get("Jhin"));   // primary count preserved
        assertEquals(Integer.valueOf(2), merged.get("Samira")); // primary-only
        assertEquals(Integer.valueOf(1), merged.get("Corki"));  // secondary-only added
        assertEquals(3, merged.size());
    }

    @Test public void mergeNoDoubleCountNulls() {
        LinkedHashMap<String,Integer> primary = new LinkedHashMap<>();
        primary.put("Jhin", 1);
        // null secondary → no change
        LinkedHashMap<String,Integer> r1 = RosterMerge.mergeNoDoubleCount(primary, null);
        assertEquals(1, r1.size());
        assertEquals(Integer.valueOf(1), r1.get("Jhin"));
        // null primary → secondary only
        LinkedHashMap<String,Integer> secondary = new LinkedHashMap<>();
        secondary.put("Corki", 2);
        LinkedHashMap<String,Integer> r2 = RosterMerge.mergeNoDoubleCount(null, secondary);
        assertEquals(Integer.valueOf(2), r2.get("Corki"));
        // both null → empty
        assertTrue(RosterMerge.mergeNoDoubleCount(null, null).isEmpty());
    }

    @Test public void mergeNoDoubleCountOrderPreserved() {
        LinkedHashMap<String,Integer> primary = new LinkedHashMap<>();
        primary.put("Jhin", 1);
        primary.put("Samira", 1);
        LinkedHashMap<String,Integer> secondary = new LinkedHashMap<>();
        secondary.put("Corki", 1);
        LinkedHashMap<String,Integer> merged = RosterMerge.mergeNoDoubleCount(primary, secondary);
        // insertion order: Jhin, Samira (from primary), then Corki (from secondary)
        List<String> keys = new java.util.ArrayList<>(merged.keySet());
        assertEquals("Jhin",   keys.get(0));
        assertEquals("Samira", keys.get(1));
        assertEquals("Corki",  keys.get(2));
    }
}
