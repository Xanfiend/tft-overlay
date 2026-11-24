package com.xanfiend.tftoverlay;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * PositionAdvisor is pure logic (ThreatData roles + CompAdvisor carry pick),
 * so it runs directly on the JVM with no device or Robolectric needed.
 */
public class PositionAdvisorTest {

    @Test public void emptyBoardHasBoardFalse() {
        PositionAdvisor.Plan p = PositionAdvisor.plan(Collections.<String>emptyList(), "3-2");
        assertFalse(p.hasBoard);
        assertTrue(p.tips.isEmpty());
        assertTrue(p.frontline.isEmpty());
        assertTrue(p.backline.isEmpty());
        assertTrue(p.flankers.isEmpty());
    }

    @Test public void nullBoardHasBoardFalse() {
        PositionAdvisor.Plan p = PositionAdvisor.plan(null, "2-1");
        assertFalse(p.hasBoard);
    }

    @Test public void knownRolesSortIntoCorrectLists() {
        // Leona=FRONT, Caitlyn=BACK, Khazix=FLANK per ThreatData
        List<String> board = Arrays.asList("Leona", "Caitlyn", "Khazix");
        PositionAdvisor.Plan p = PositionAdvisor.plan(board, "3-1");
        assertTrue(p.hasBoard);
        assertTrue(p.frontline.contains("Leona"));
        assertTrue(p.backline.contains("Caitlyn"));
        assertTrue(p.flankers.contains("Khazix"));
    }

    @Test public void evenRoundCarryGoesBackLeft() {
        // "2-2" → round 2 (even) → BACK-LEFT
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Caitlyn"), "2-2");
        assertEquals("BACK-LEFT corner", p.carryCorner);
    }

    @Test public void oddRoundCarryGoesBackRight() {
        // "2-3" → round 3 (odd) → BACK-RIGHT
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Caitlyn"), "2-3");
        assertEquals("BACK-RIGHT corner", p.carryCorner);
    }

    @Test public void emptyStageRoundDefaultsToLeft() {
        // round = 0 (even) → BACK-LEFT
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Caitlyn"), "");
        assertEquals("BACK-LEFT corner", p.carryCorner);
    }

    @Test public void nullStageRoundDefaultsToLeft() {
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Caitlyn"), null);
        assertEquals("BACK-LEFT corner", p.carryCorner);
    }

    @Test public void tipsNonEmptyForAnyBoard() {
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Caitlyn", "Leona"), "3-1");
        assertFalse("at least the carry-corner tip should be present", p.tips.isEmpty());
    }

    @Test public void duplicateChampsDeduped() {
        // LinkedHashSet dedup in plan() means the same champ listed twice → once in its list
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Caitlyn", "Caitlyn"), "3-1");
        assertEquals(1, p.backline.size());
        assertEquals("Caitlyn", p.backline.get(0));
    }

    @Test public void flankerTipMentionsFlankers() {
        // When flankers are present, a tip should name them
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Khazix", "Caitlyn"), "3-1");
        boolean found = false;
        for (String tip : p.tips) if (tip.contains("Khazix")) { found = true; break; }
        assertTrue("flanker tip should name Khazix", found);
    }
}
