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
}
