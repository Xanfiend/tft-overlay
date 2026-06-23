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
        // Aatrox=FRONT, Jinx=BACK, Talon=FLANK per ThreatData
        List<String> board = Arrays.asList("Aatrox", "Jinx", "Talon");
        PositionAdvisor.Plan p = PositionAdvisor.plan(board, "3-1");
        assertTrue(p.hasBoard);
        assertTrue(p.frontline.contains("Aatrox"));
        assertTrue(p.backline.contains("Jinx"));
        assertTrue(p.flankers.contains("Talon"));
    }

    @Test public void evenRoundCarryGoesBackLeft() {
        // "2-2" → round 2 (even) → BACK-LEFT
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Jinx"), "2-2");
        assertEquals("BACK-LEFT corner", p.carryCorner);
    }

    @Test public void oddRoundCarryGoesBackRight() {
        // "2-3" → round 3 (odd) → BACK-RIGHT
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Jinx"), "2-3");
        assertEquals("BACK-RIGHT corner", p.carryCorner);
    }

    @Test public void emptyStageRoundDefaultsToLeft() {
        // round = 0 (even) → BACK-LEFT
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Jinx"), "");
        assertEquals("BACK-LEFT corner", p.carryCorner);
    }

    @Test public void nullStageRoundDefaultsToLeft() {
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Jinx"), null);
        assertEquals("BACK-LEFT corner", p.carryCorner);
    }

    @Test public void tipsNonEmptyForAnyBoard() {
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Jinx", "Aatrox"), "3-1");
        assertFalse("at least the carry-corner tip should be present", p.tips.isEmpty());
    }

    @Test public void duplicateChampsDeduped() {
        // LinkedHashSet dedup in plan() means the same champ listed twice → once in its list
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Jinx", "Jinx"), "3-1");
        assertEquals(1, p.backline.size());
        assertEquals("Jinx", p.backline.get(0));
    }

    @Test public void nullOppGivesSameResultAsBase() {
        List<String> board = Arrays.asList("Aatrox", "Jinx");
        PositionAdvisor.Plan base = PositionAdvisor.plan(board, "3-1");
        PositionAdvisor.Plan withNull = PositionAdvisor.plan(board, "3-1", null);
        assertEquals(base.tips.size(), withNull.tips.size());
        assertEquals(base.carryCorner, withNull.carryCorner);
    }

    @Test public void oppWithNoDataDoesNotAddTips() {
        // boards=0 → hasData() returns false → tips not appended
        OppScout.Profile opp = new OppScout.Profile();
        opp.tips.add("should be ignored");
        List<String> board = Arrays.asList("Jinx");
        PositionAdvisor.Plan base = PositionAdvisor.plan(board, "3-1");
        PositionAdvisor.Plan enriched = PositionAdvisor.plan(board, "3-1", opp);
        assertEquals(base.tips.size(), enriched.tips.size());
    }

    @Test public void oppWithDataAddsTipsToEnd() {
        OppScout.Profile opp = new OppScout.Profile();
        opp.boards = 1; // hasData() = true
        opp.tips.add("Watch for hook threat");
        List<String> board = Arrays.asList("Jinx");
        PositionAdvisor.Plan base = PositionAdvisor.plan(board, "3-1");
        PositionAdvisor.Plan enriched = PositionAdvisor.plan(board, "3-1", opp);
        assertEquals(base.tips.size() + 1, enriched.tips.size());
        assertEquals("Watch for hook threat", enriched.tips.get(enriched.tips.size() - 1));
    }

    @Test public void flankerTipMentionsFlankers() {
        // When flankers are present, a tip should name them
        PositionAdvisor.Plan p = PositionAdvisor.plan(Arrays.asList("Talon", "Jinx"), "3-1");
        boolean found = false;
        for (String tip : p.tips) if (tip.contains("Talon")) { found = true; break; }
        assertTrue("flanker tip should name Talon", found);
    }
}
