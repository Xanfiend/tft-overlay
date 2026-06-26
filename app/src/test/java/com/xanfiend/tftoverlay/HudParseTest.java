package com.xanfiend.tftoverlay;

import static org.junit.Assert.*;
import org.junit.Test;

/* Pure JVM tests for the autoscan HUD number parser. */
public class HudParseTest {

    // ---- level ----
    @Test public void levelInRange() {
        assertEquals(1, HudParse.level("1"));      // Tocker's start
        assertEquals(9, HudParse.level("9"));
        assertEquals(10, HudParse.level("10"));
    }
    @Test public void levelRejectsOutOfRange() {
        assertEquals(HudParse.NONE, HudParse.level("0"));
        assertEquals(HudParse.NONE, HudParse.level("11"));
        assertEquals(HudParse.NONE, HudParse.level("99"));
        assertEquals(HudParse.NONE, HudParse.level(""));
        assertEquals(HudParse.NONE, HudParse.level("abc"));
    }
    @Test public void levelFixesOcrConfusions() {
        assertEquals(1, HudParse.level("l"));     // lowercase L -> 1
        assertEquals(1, HudParse.level("I"));     // capital i -> 1
        assertEquals(10, HudParse.level("lO"));   // "lO" -> "10"
        assertEquals(5, HudParse.level("S"));     // S -> 5
    }

    // ---- gold ----
    @Test public void goldBasic() {
        assertEquals(50, HudParse.gold("50"));
        assertEquals(0, HudParse.gold("0"));
        assertEquals(100, HudParse.gold("100"));
    }
    @Test public void goldHandlesIconAndConfusions() {
        assertEquals(50, HudParse.gold("50g"));   // 'g' suffix NOT remapped
        assertEquals(50, HudParse.gold("5O"));    // O -> 0
        assertEquals(82, HudParse.gold("8Z"));    // Z -> 2
        assertEquals(HudParse.NONE, HudParse.gold("rat"));  // no digits, no mappable chars
    }

    // ---- xp ----
    @Test public void xpValid() {
        assertArrayEquals(new int[]{4,6}, HudParse.xp("4/6"));
        assertArrayEquals(new int[]{4,6}, HudParse.xp("4 / 6"));
        assertArrayEquals(new int[]{10,20}, HudParse.xp("10/20"));
        assertArrayEquals(new int[]{0,2}, HudParse.xp("0/2"));
    }
    @Test public void xpRejectsInvalid() {
        assertNull(HudParse.xp("7/6"));   // cur > need
        assertNull(HudParse.xp("no xp"));
    }

    // ---- stage-round ----
    @Test public void stageRoundValid() {
        assertArrayEquals(new int[]{2,1}, HudParse.stageRound("2-1"));
        assertArrayEquals(new int[]{4,2}, HudParse.stageRound("Stage 4-2"));
        assertArrayEquals(new int[]{3,7}, HudParse.stageRound("3-7"));
    }
    @Test public void stageRoundRejectsOutOfRange() {
        assertNull(HudParse.stageRound("8-1"));   // stage > 7
        assertNull(HudParse.stageRound("nope"));
    }

    // ---- normalization ----
    @Test public void normalizeMapsKnownConfusions() {
        assertEquals("0", HudParse.normalizeDigits("O"));
        assertEquals("1", HudParse.normalizeDigits("l"));
        assertEquals("5", HudParse.normalizeDigits("S"));
        assertEquals("8", HudParse.normalizeDigits("B"));
        assertEquals("2", HudParse.normalizeDigits("Z"));
        assertEquals("100", HudParse.normalizeDigits("1O0"));
        assertEquals("", HudParse.normalizeDigits(null));
    }
}
