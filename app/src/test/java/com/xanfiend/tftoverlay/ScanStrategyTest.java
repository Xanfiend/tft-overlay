package com.xanfiend.tftoverlay;

import static org.junit.Assert.*;
import org.junit.Test;

/* Pure JVM tests for the scan-path decision layer. */
public class ScanStrategyTest {

    // ---- shouldUsePlanner ----

    @Test public void plannerBeatsNTaps() {
        // 8 units unresolved → 8 tap-OCRs vs 3 planner taps → use planner
        assertTrue(ScanStrategy.shouldUsePlanner(8, 0, true));
    }

    @Test public void plannerBreakEvenFallsBack() {
        // exactly 3 unresolved = break-even → don't bother with planner
        assertFalse(ScanStrategy.shouldUsePlanner(3, 0, true));
    }

    @Test public void plannerJustTipsOver() {
        // 4 unresolved saves 1 tap → worth it
        assertTrue(ScanStrategy.shouldUsePlanner(4, 0, true));
    }

    @Test public void plannerNotCalibrated() {
        assertFalse(ScanStrategy.shouldUsePlanner(10, 0, false));
    }

    @Test public void previousTallyMakesNaiveCheaper() {
        // 8 units, 5 already resolved by visual-ID → only 3 need taps = break-even
        assertFalse(ScanStrategy.shouldUsePlanner(8, 5, true));
    }

    @Test public void allResolvedSkipsPlanner() {
        // nothing unresolved — planner would cost 3 taps for zero gain
        assertFalse(ScanStrategy.shouldUsePlanner(8, 8, true));
    }

    // ---- remainingTaps ----

    @Test public void remainingTapsBenchOnly() {
        // planner fully identifies 8 board units; 2 bench still need taps
        assertEquals(2, ScanStrategy.remainingTaps(8, 8, 2));
    }

    @Test public void remainingTapsPartialPlanner() {
        // planner resolves 6 of 8 board units; 2 missed + 2 bench
        assertEquals(4, ScanStrategy.remainingTaps(8, 6, 2));
    }

    @Test public void remainingTapsNoBench() {
        assertEquals(0, ScanStrategy.remainingTaps(8, 8, 0));
    }

    @Test public void remainingTapsNeverNegative() {
        // planner "over-resolved" (shouldn't happen, but be safe)
        assertEquals(0, ScanStrategy.remainingTaps(4, 6, 0));
    }

    // ---- estimatedMs ----

    @Test public void estimatedMsPlannerPath() {
        // 2 screenshots (1000ms each) + 3 planner taps + 2 leftover taps (1300ms each)
        assertEquals(2 * 1000 + 5 * 1300, ScanStrategy.estimatedMs(true, 2, 1000, 1300));
    }

    @Test public void estimatedMsNaivePath() {
        // 1 screenshot + 8 individual taps
        assertEquals(1000 + 8 * 1300, ScanStrategy.estimatedMs(false, 8, 1000, 1300));
    }

    // ---- describe ----

    @Test public void describeMatchesPaths() {
        String p = ScanStrategy.describe(true, 2);
        assertTrue(p.contains("planner"));
        assertTrue(p.contains("2"));
        String n = ScanStrategy.describe(false, 5);
        assertTrue(n.contains("5"));
        assertFalse(n.contains("planner"));
    }
}
