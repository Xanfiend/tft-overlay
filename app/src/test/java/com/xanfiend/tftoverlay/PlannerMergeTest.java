package com.xanfiend.tftoverlay;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/* Pure JVM tests for planner-snapshot × health-bar merge. */
public class PlannerMergeTest {

    private static int[] pos(int x, int y, int stars) { return new int[]{x, y, stars}; }

    // ---- happy path ----

    @Test public void happyPath8Units() {
        // 8 names, 8 positions — 8 resolved, 0 unresolved
        List<String> names = Arrays.asList(
            "Annie","Corki","Jhin","Vex","Samira","Kai'Sa","TwistedFate","Lux");
        int[][] pos = {pos(100,50,1),pos(200,50,2),pos(300,50,1),pos(400,50,3),
                       pos(500,50,1),pos(600,50,1),pos(700,50,2),pos(800,50,1)};
        PlannerMerge.MergeResult r = PlannerMerge.merge(names, pos);
        assertEquals(8, r.resolvedCount());
        assertEquals(0, r.unresolvedCount());
        assertEquals("Annie", r.resolved.get(0).name);
        // stars come from health bar, not planner
        assertEquals(2, r.resolved.get(1).stars);
    }

    @Test public void starsPreservedFromHealthBar() {
        List<String> names = Arrays.asList("Jinx");
        int[][] pos = {pos(300, 60, 3)};
        PlannerMerge.MergeResult r = PlannerMerge.merge(names, pos);
        assertEquals(3, r.resolved.get(0).stars);
    }

    @Test public void positionPreservedFromHealthBar() {
        List<String> names = Arrays.asList("Jhin");
        int[][] pos = {pos(450, 120, 2)};
        PlannerMerge.MergeResult r = PlannerMerge.merge(names, pos);
        assertEquals(450, r.resolved.get(0).x);
        assertEquals(120, r.resolved.get(0).y);
    }

    // ---- count mismatches ----

    @Test public void plannerCountLower_trailingPositionsUnresolved() {
        // planner identified 5 of 7 units — 2 trailing positions need tap-OCR
        List<String> names = Arrays.asList("Annie","Corki","Jhin","Vex","Samira");
        int[][] pos = {pos(100,50,1),pos(200,50,1),pos(300,50,1),pos(400,50,1),
                       pos(500,50,1),pos(600,50,1),pos(700,50,1)};
        PlannerMerge.MergeResult r = PlannerMerge.merge(names, pos);
        assertEquals(5, r.resolvedCount());
        assertEquals(2, r.unresolvedCount());
        assertEquals(600, r.unresolved[0][0]);
        assertEquals(700, r.unresolved[1][0]);
    }

    @Test public void emptyNameAtIndex_thatPositionUnresolved() {
        // tile 1 wasn't recognised (empty string) → position 1 goes to unresolved
        List<String> names = Arrays.asList("Annie", "", "Jhin");
        int[][] pos = {pos(100,50,1), pos(200,50,1), pos(300,50,1)};
        PlannerMerge.MergeResult r = PlannerMerge.merge(names, pos);
        assertEquals(2, r.resolvedCount());
        assertEquals(1, r.unresolvedCount());
        assertEquals(200, r.unresolved[0][0]);
    }

    @Test public void emptyPlannerNames_allUnresolved() {
        int[][] pos = {pos(100,50,2), pos(200,50,1)};
        PlannerMerge.MergeResult r = PlannerMerge.merge(Collections.emptyList(), pos);
        assertEquals(0, r.resolvedCount());
        assertEquals(2, r.unresolvedCount());
    }

    @Test public void nullInputs_safeEmptyResult() {
        PlannerMerge.MergeResult r = PlannerMerge.merge(null, null);
        assertEquals(0, r.resolvedCount());
        assertEquals(0, r.unresolvedCount());
    }

    // ---- floating names (planner > health-bar count) ----

    @Test public void floatingNamesWhenPlannerCountHigher() {
        // 3 names but only 2 positions (unit died between scans)
        List<String> names = Arrays.asList("Annie","Corki","Jhin");
        int[][] pos = {pos(100,50,1), pos(200,50,1)};
        PlannerMerge.MergeResult r = PlannerMerge.merge(names, pos);
        assertEquals(2, r.resolvedCount());  // only 2 positions exist
        assertEquals(0, r.unresolvedCount());

        List<String> floating = PlannerMerge.floatingNames(names, pos);
        assertEquals(1, floating.size());
        assertEquals("Jhin", floating.get(0));
    }

    @Test public void floatingNamesEmptyWhenNoExcess() {
        List<String> names = Arrays.asList("Annie","Corki");
        int[][] pos = {pos(100,50,1), pos(200,50,1), pos(300,50,1)};
        assertTrue(PlannerMerge.floatingNames(names, pos).isEmpty());
    }

    @Test public void floatingNamesSkipsEmptyStrings() {
        // trailing empty name at excess index shouldn't count as floating
        List<String> names = Arrays.asList("Annie","");
        int[][] pos = {pos(100,50,1)};
        List<String> floating = PlannerMerge.floatingNames(names, pos);
        assertTrue(floating.isEmpty());
    }

    @Test public void floatingNamesNullSafe() {
        assertTrue(PlannerMerge.floatingNames(null, null).isEmpty());
        assertTrue(PlannerMerge.floatingNames(Arrays.asList("Annie"), null).isEmpty());
    }

    // ---- toString ----

    @Test public void boardUnitToString() {
        PlannerMerge.BoardUnit u = new PlannerMerge.BoardUnit("Corki", 200, 80, 2);
        String s = u.toString();
        assertTrue(s.contains("Corki"));
        assertTrue(s.contains("200"));
        assertTrue(s.contains("2"));
    }
}
