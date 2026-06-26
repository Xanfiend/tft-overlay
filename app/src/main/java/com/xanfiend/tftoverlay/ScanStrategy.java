package com.xanfiend.tftoverlay;

/*
 * Pure tap-budget decision layer for autoscan. Given detected unit count and
 * how many are already resolved (by tally, visual-ID, or previous pass),
 * decides whether planner snapshot or straight tap-per-unit is cheaper, and
 * estimates wall-clock scan time.
 *
 * GROUNDWORK (dormant): pure + tested. OverlayService wires this in once the
 * planner-snapshot calibration path is live on-device.
 */
public final class ScanStrategy {

    /** Fixed taps to open planner, trigger snapshot, and close it. */
    public static final int PLANNER_TAP_COST = 3;

    /**
     * True when planner snapshot is cheaper than tapping every unresolved unit
     * individually. Planner costs PLANNER_TAP_COST taps and identifies every
     * fielded unit at once; tap-per-unit costs one tap per unresolved unit.
     * Break-even at unresolved == PLANNER_TAP_COST, so we only switch at > 3.
     */
    public static boolean shouldUsePlanner(int detectedUnits, int resolvedNames,
                                           boolean plannerCalibrated) {
        if (!plannerCalibrated) return false;
        int unresolved = Math.max(0, detectedUnits - resolvedNames);
        return unresolved > PLANNER_TAP_COST;
    }

    /**
     * Units still needing individual tap-OCR after a planner scan has identified
     * all fielded units. Bench units (not in the planner snapshot) always need
     * individual taps.
     */
    public static int remainingTaps(int detectedUnits, int plannerResolved, int benchUnits) {
        int boardUnresolved = Math.max(0, detectedUnits - plannerResolved);
        return boardUnresolved + Math.max(0, benchUnits);
    }

    /**
     * Estimated wall-clock scan time in milliseconds.
     *   screenshotMs — time per accessibility screenshot (device-specific, ~800-1300 ms)
     *   tapMs         — time per tap including settle window (~1200-1500 ms)
     *
     * Planner path: 2 screenshots (health-bar + planner) + PLANNER_TAP_COST + leftover taps.
     * Naive path:   1 screenshot  (health-bar) + tapCount taps.
     */
    public static int estimatedMs(boolean usePlanner, int tapCount, int screenshotMs, int tapMs) {
        int shots = usePlanner ? 2 : 1;
        int taps  = usePlanner ? PLANNER_TAP_COST + tapCount : tapCount;
        return shots * screenshotMs + taps * tapMs;
    }

    /**
     * Human-readable summary of the chosen path for debug logging.
     */
    public static String describe(boolean usePlanner, int tapCount) {
        if (usePlanner) {
            return "planner(" + PLANNER_TAP_COST + " taps) + " + tapCount + " fallback tap(s)";
        }
        return "tap-per-unit: " + tapCount + " tap(s)";
    }

    private ScanStrategy() {}
}
