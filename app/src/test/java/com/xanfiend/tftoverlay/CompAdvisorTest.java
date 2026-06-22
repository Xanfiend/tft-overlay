package com.xanfiend.tftoverlay;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure-logic tests for the COACH stage/level helpers. No Android. */
public class CompAdvisorTest {

    @Test public void stageAndRoundParse(){
        assertEquals(3, CompAdvisor.stageOf("3-2"));
        assertEquals(2, CompAdvisor.roundOf("3-2"));
        assertEquals(0, CompAdvisor.stageOf(null));
        assertEquals(0, CompAdvisor.roundOf("garbage"));
        assertEquals(0, CompAdvisor.roundOf("4-"));   // missing round
    }

    @Test public void expectedLevelBenchmarks(){
        assertEquals(4, CompAdvisor.expectedLevel("2-1"));
        assertEquals(5, CompAdvisor.expectedLevel("2-5"));
        assertEquals(6, CompAdvisor.expectedLevel("3-2"));
        assertEquals(7, CompAdvisor.expectedLevel("4-1"));
        assertEquals(8, CompAdvisor.expectedLevel("4-5"));
        assertEquals(9, CompAdvisor.expectedLevel("5-5"));
        assertEquals(0, CompAdvisor.expectedLevel(""));   // unknown stage
    }

    @Test public void levelCurveClassifies(){
        // ahead of the 3-2 benchmark (L6) -> "Ahead"
        assertTrue(CompAdvisor.levelCurve(7, "3-2").startsWith("Ahead"));
        // exactly on curve
        assertTrue(CompAdvisor.levelCurve(6, "3-2").startsWith("On curve"));
        // one under
        assertTrue(CompAdvisor.levelCurve(5, "3-2").startsWith("Slightly behind"));
        // far under
        assertTrue(CompAdvisor.levelCurve(4, "4-1").startsWith("Behind"));
        // unknown stage or level -> empty (no advice)
        assertEquals("", CompAdvisor.levelCurve(6, ""));
        assertEquals("", CompAdvisor.levelCurve(0, "3-2"));
    }

    @Test public void econCallAlwaysReturnsGuidance(){
        assertFalse(CompAdvisor.econCall(6, 50, "3-2").isEmpty());
        assertFalse(CompAdvisor.econCall(8, 0, "4-5").isEmpty());
    }
}
