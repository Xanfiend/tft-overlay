package com.xanfiend.tftoverlay;

import static org.junit.Assert.*;
import org.junit.Test;

/*
 * Validates the hex-grid projection model against TFT-OCR-BOT's hand-measured
 * 1920x1080 hex-center table (28 player board positions) — real coordinates
 * from the live game client. The model gets only the four outer anchors of the
 * back and front rows; everything else (middle-row spacing, per-row pitch,
 * honeycomb stagger) must come out of the projection.
 */
public class HexGridTest {

    // TFT-OCR-BOT screen_coords BOARD_LOC, 1920x1080, rows front->back.
    private static final float[][] FRONT = {{581,651},{707,651},{839,651},{966,651},{1091,651},{1222,651},{1349,651}};
    private static final float[][] ROW1  = {{532,571},{660,571},{776,571},{903,571},{1022,571},{1147,571},{1275,571}};
    private static final float[][] ROW2  = {{609,494},{723,494},{841,494},{962,494},{1082,494},{1198,494},{1318,494}};
    private static final float[][] BACK  = {{557,423},{673,423},{791,423},{907,423},{1019,423},{1138,423},{1251,423}};

    private static float[][][] pcGrid() {
        // anchors: outer hex centers of back and front rows, auto row fractions
        return HexGrid.player(557, 1251, 423, 581, 1349, 651, -1, -1);
    }

    @Test public void anchorsReproducedExactly() {
        float[][][] g = pcGrid();
        assertEquals(557,  g[0][0][0], 0.5f);  // back-left
        assertEquals(1251, g[0][6][0], 0.5f);  // back-right
        assertEquals(581,  g[3][0][0], 0.5f);  // front-left
        assertEquals(1349, g[3][6][0], 0.5f);  // front-right
        assertEquals(423,  g[0][0][1], 0.5f);
        assertEquals(651,  g[3][0][1], 0.5f);
    }

    @Test public void allTwentyEightHexesWithinEightPixels() {
        float[][][] g = pcGrid();
        float[][][] truth = {BACK, ROW2, ROW1, FRONT};   // rows back->front
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 7; c++) {
                assertEquals("row " + r + " col " + c + " x",
                        truth[r][c][0], g[r][c][0], 8f);
                assertEquals("row " + r + " col " + c + " y",
                        truth[r][c][1], g[r][c][1], 8f);
            }
    }

    @Test public void staggerAlternatesHalfPitchLeft() {
        float[][][] g = pcGrid();
        // aligned rows (front, 2nd-from-back) center near 964; offset rows
        // (back, 2nd-from-front) sit roughly half a pitch left of them
        float cBack  = (g[0][0][0] + g[0][6][0]) / 2f;
        float cRow2  = (g[1][0][0] + g[1][6][0]) / 2f;
        float cRow1  = (g[2][0][0] + g[2][6][0]) / 2f;
        float cFront = (g[3][0][0] + g[3][6][0]) / 2f;
        assertTrue("back row is an offset row",  cBack  < cRow2 - 40);
        assertTrue("row1 is an offset row",      cRow1  < cFront - 40);
        assertEquals("aligned rows share a centerline", cRow2, cFront, 8f);
    }

    @Test public void autoRowFractionsMatchMeasuredSpacing() {
        // PC truth: rows at y 423/494/571/651 -> fractions 31.1% and 64.9%
        float[] f = HexGrid.autoRowFractions(1251 - 557, 1349 - 581);
        assertEquals(0.311f, f[0], 0.02f);
        assertEquals(0.649f, f[1], 0.02f);
    }

    @Test public void pitchGrowsTowardTheFront() {
        float[][][] g = pcGrid();
        for (int r = 1; r < 4; r++) {
            float prev = g[r - 1][1][0] - g[r - 1][0][0];
            float cur  = g[r][1][0] - g[r][0][0];
            assertTrue("pitch must grow back->front", cur > prev);
        }
    }

    @Test public void explicitFractionsRespected() {
        float[][][] g = HexGrid.player(557, 1251, 423, 581, 1349, 651, 0.5f, 0.75f);
        assertEquals(423 + 0.5f  * 228, g[1][0][1], 0.5f);
        assertEquals(423 + 0.75f * 228, g[2][0][1], 0.5f);
    }

    @Test public void oppRowsSitAboveAndCompress() {
        float[][][] opp = HexGrid.opp(557, 1251, 423, 581, 1349, 651, -1, -1);
        // their front row is just above the player's back row, rows going up
        assertTrue(opp[3][0][1] < 423);
        for (int r = 1; r < 4; r++)
            assertTrue("opp rows ordered top->down", opp[r - 1][0][1] < opp[r][0][1]);
        // gaps shrink with distance: top gap smaller than bottom gap
        float gTop = opp[1][0][1] - opp[0][0][1];
        float gBot = opp[3][0][1] - opp[2][0][1];
        assertTrue("perspective compresses far rows", gTop < gBot);
        // pitch keeps shrinking above the board
        float pOppBack  = opp[0][1][0] - opp[0][0][0];
        float pOppFront = opp[3][1][0] - opp[3][0][0];
        assertTrue(pOppBack < pOppFront);
        assertTrue(pOppFront < (1251 - 557) / 6f + 1f);
    }

    @Test public void degenerateInputsAreSafe() {
        // zero spans fall back to even fractions and produce finite points
        float[] f = HexGrid.autoRowFractions(0, 0);
        assertEquals(1f/3f, f[0], 0.001f);
        float[][][] g = HexGrid.player(100, 100, 200, 100, 100, 200, -1, -1);
        for (float[][] row : g) for (float[] pt : row) {
            assertFalse(Float.isNaN(pt[0]) || Float.isInfinite(pt[0]));
            assertFalse(Float.isNaN(pt[1]) || Float.isInfinite(pt[1]));
        }
    }
}
