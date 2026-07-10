package com.xanfiend.tftoverlay;

/*
 * The TFT board's true screen geometry, from four calibration anchors.
 *
 * The board is a honeycomb: 8 rows x 7 hexes, alternate rows offset by HALF a
 * hex pitch to the LEFT. Verified against TFT-OCR-BOT's hand-measured
 * 1920x1080 hex table (28 player hexes): counting rows from the player's FRONT
 * row (i=0), odd rows carry the -0.5 pitch offset, even rows are aligned.
 * A column-aligned 4x7 grid is therefore off by half a hex on two of the four
 * rows no matter how well the corners are calibrated — this class replaces it.
 *
 * Projection model (validated on the same table, max error ~6px at 1080p):
 *   - hex pitch varies LINEARLY with screen Y (pinhole camera over a ground
 *     plane: image scale is linear in image y), so pitch and the aligned-row
 *     centerline are interpolated/extrapolated linearly in y;
 *   - the on-screen gap between adjacent rows is proportional to the local
 *     pitch, which pins the two middle rows' y positions from the anchors
 *     alone (autoRowFractions) — no hand-tuned spacing needed.
 *
 * Anchors are the measured OUTER HEX CENTERS (columns 0 and 6) of the player's
 * BACK row and FRONT row — exactly what tap calibration and ADJUST GRID save.
 * Each anchor row's own phase is baked into its measurement and is unwound
 * here (the back row is an offset row, the front row is aligned).
 */
public final class HexGrid {

    public static final int ROWS = 4, COLS = 7;

    /* Phase of player row r counted BACK(0) -> FRONT(3), in pitch units.
     * Board row i from the player's front: phase = (i % 2 == 1) ? -0.5 : 0.
     * Player rows back->front are board rows 3,2,1,0. */
    static float playerPhase(int row) { return (row % 2 == 0) ? -0.5f : 0f; }

    /* Opponent rows counted TOP(0=their back, board row 7) -> 3 (their front,
     * board row 4). Board rows 7,6,5,4 -> phases -0.5, 0, -0.5, 0. */
    static float oppPhase(int row) { return (row % 2 == 0) ? -0.5f : 0f; }

    /* Middle-row y fractions {f1, f2} of the back->front span, derived from the
     * anchor spans: row gaps grow proportionally to the local pitch. Solved by
     * three fixed-point passes of gap_i = k * pitch(midpoint of gap_i). */
    public static float[] autoRowFractions(float backSpan, float frontSpan) {
        if (backSpan <= 0 || frontSpan <= 0) return new float[]{1f/3f, 2f/3f};
        float s0 = backSpan, ds = frontSpan - backSpan;   // pitch ∝ span
        float f1 = 1f/3f, f2 = 2f/3f;
        for (int pass = 0; pass < 3; pass++) {
            float g0 = s0 + ds * (0f + f1) / 2f;   // gap back..row1, pitch at midpoint
            float g1 = s0 + ds * (f1 + f2) / 2f;
            float g2 = s0 + ds * (f2 + 1f) / 2f;
            float total = g0 + g1 + g2;
            f1 = g0 / total;
            f2 = (g0 + g1) / total;
        }
        return new float[]{f1, f2};
    }

    /* 28 player hex centers as [row][col][x,y], rows BACK(0)->FRONT(3).
     * blx/brx/by: back-row outer hex centers + y.  flx/frx/fy: front row.
     * f1/f2: middle-row fractions; pass <0 to derive them projectively. */
    public static float[][][] player(float blx, float brx, float by,
                                     float flx, float frx, float fy,
                                     float f1, float f2) {
        float pBack  = (brx - blx) / 6f;
        float pFront = (frx - flx) / 6f;
        // unwind each anchor row's own phase to get the aligned centerline
        float cBack  = (blx + brx) / 2f - playerPhase(0) * pBack;
        float cFront = (flx + frx) / 2f - playerPhase(3) * pFront;
        if (f1 < 0 || f2 <= f1) {
            float[] f = autoRowFractions(pBack * 6f, pFront * 6f);
            f1 = f[0]; f2 = f[1];
        }
        float[] tt = {0f, f1, f2, 1f};
        float[][][] out = new float[ROWS][COLS][2];
        for (int r = 0; r < ROWS; r++) {
            float t = tt[r];
            float y = by + t * (fy - by);
            float p = pBack + t * (pFront - pBack);
            float c = cBack + t * (cFront - cBack) + playerPhase(r) * p;
            for (int col = 0; col < COLS; col++) {
                out[r][col][0] = c + (col - 3) * p;
                out[r][col][1] = y;
            }
        }
        return out;
    }

    /* 28 opponent hex centers as [row][col][x,y], rows TOP(0=their back)->3
     * (their front, adjacent to the player's back row). Positions continue the
     * SAME projection above the player's back row: pitch and centerline are
     * extrapolated linearly in y, and row gaps keep shrinking with the local
     * pitch — no mirrored-zone guess. Same anchors as player(). */
    public static float[][][] opp(float blx, float brx, float by,
                                  float flx, float frx, float fy,
                                  float f1, float f2) {
        float pBack  = (brx - blx) / 6f;
        float pFront = (frx - flx) / 6f;
        float cBack  = (blx + brx) / 2f - playerPhase(0) * pBack;
        float cFront = (flx + frx) / 2f - playerPhase(3) * pFront;
        if (f1 < 0 || f2 <= f1) {
            float[] f = autoRowFractions(pBack * 6f, pFront * 6f);
            f1 = f[0]; f2 = f[1];
        }
        // gap constant k from the player half: back->front span split by pitch
        float[] tt = {0f, f1, f2, 1f};
        float sumPitchMid = 0f;
        for (int i = 0; i < 3; i++)
            sumPitchMid += pBack + (pFront - pBack) * (tt[i] + tt[i + 1]) / 2f;
        float k = (fy - by) / sumPitchMid;   // gap = k * pitch(mid)

        // walk upward from the player's back row, 4 more rows
        float dPdY = (fy != by) ? (pFront - pBack) / (fy - by) : 0f;
        float dCdY = (fy != by) ? (cFront - cBack) / (fy - by) : 0f;
        float[] ys = new float[4];
        float y = by;
        for (int i = 0; i < 4; i++) {
            float pHere = pBack + dPdY * (y - by);
            // midpoint iteration: gap uses pitch halfway into the step
            float gap = k * pHere;
            for (int it = 0; it < 2; it++) {
                float pMid = pBack + dPdY * (y - gap / 2f - by);
                if (pMid < pHere * 0.3f) pMid = pHere * 0.3f;   // extrapolation guard
                gap = k * pMid;
            }
            y -= gap;
            ys[3 - i] = y;   // filling their-front(3) first ... their-back(0) last
        }
        float[][][] out = new float[ROWS][COLS][2];
        for (int r = 0; r < ROWS; r++) {
            float ry = ys[r];
            float p = pBack + dPdY * (ry - by);
            if (p < pBack * 0.3f) p = pBack * 0.3f;
            float c = cBack + dCdY * (ry - by) + oppPhase(r) * p;
            for (int col = 0; col < COLS; col++) {
                out[r][col][0] = c + (col - 3) * p;
                out[r][col][1] = ry;
            }
        }
        return out;
    }

    private HexGrid() {}
}
