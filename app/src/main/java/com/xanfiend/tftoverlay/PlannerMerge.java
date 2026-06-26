package com.xanfiend.tftoverlay;

import java.util.ArrayList;
import java.util.List;

/*
 * Final pure reconciliation step for autoscan's planner path.
 *
 * Health-bar detection gives unit POSITIONS + STAR LEVELS.
 * Planner snapshot gives unit NAMES (no stars — the planner never shows them).
 * This class pairs them by left-to-right index order: both scans traverse the
 * board left→right, so indices align in the equal-count case. Mismatches are
 * handled explicitly:
 *
 *   positions > names  — trailing positions become unresolved tap-OCR fallback
 *   names > positions  — trailing names are "floating" (unit died between scans)
 *   empty name at i    — tile was unrecognized (SetIcons below threshold); that
 *                        position becomes an unresolved tap-OCR fallback
 *
 * GROUNDWORK (dormant): pure + tested. Wire in when the planner slot crop zone
 * and health-bar left-to-right ordering have been confirmed on a real device.
 */
public final class PlannerMerge {

    /** One fully-identified board unit. */
    public static final class BoardUnit {
        public final String name;
        public final int x;
        public final int y;
        /** Star level from health bar (0 = not detected). */
        public final int stars;

        public BoardUnit(String name, int x, int y, int stars) {
            this.name = name; this.x = x; this.y = y; this.stars = stars;
        }

        @Override public String toString() {
            return name + "(" + x + "," + y + "," + stars + "★)";
        }
    }

    public static final class MergeResult {
        /** Units that were successfully named — name from planner, position + stars from health bar. */
        public final List<BoardUnit> resolved;
        /**
         * Health-bar positions that had no matching planner name (unrecognized tile or
         * planner count < health-bar count). Each entry is int[]{x, y, stars}.
         * These need individual tap-OCR to get a name.
         */
        public final int[][] unresolved;

        public MergeResult(List<BoardUnit> resolved, int[][] unresolved) {
            this.resolved = resolved; this.unresolved = unresolved;
        }
        public int resolvedCount()   { return resolved.size(); }
        public int unresolvedCount() { return unresolved.length; }
    }

    /**
     * Pair planner names with health-bar positions by index order (left→right).
     *
     * @param plannerNames  Ordered champion names from the planner snapshot.
     *                      An empty string at index i means the tile was not
     *                      recognised; that position will be unresolved.
     *                      Null is treated as an empty list.
     * @param healthBarUnits Each int[]{x, y, stars} from health-bar scan,
     *                       sorted ascending by x. Null is treated as empty.
     */
    public static MergeResult merge(List<String> plannerNames, int[][] healthBarUnits) {
        int nameCount = plannerNames   == null ? 0 : plannerNames.size();
        int posCount  = healthBarUnits == null ? 0 : healthBarUnits.length;

        List<BoardUnit> resolved       = new ArrayList<>();
        List<int[]>     unresolvedList = new ArrayList<>();

        for (int i = 0; i < posCount; i++) {
            String name = (i < nameCount) ? plannerNames.get(i) : null;
            int[] pos   = healthBarUnits[i];
            if (name != null && !name.isEmpty()) {
                resolved.add(new BoardUnit(name, pos[0], pos[1], pos.length >= 3 ? pos[2] : 0));
            } else {
                unresolvedList.add(pos);
            }
        }

        return new MergeResult(resolved, unresolvedList.toArray(new int[0][]));
    }

    /**
     * Names that the planner identified beyond the health-bar position count.
     * This happens when a unit died or was sold between the two scans. These
     * names are logged but cannot be placed on the board.
     */
    public static List<String> floatingNames(List<String> plannerNames, int[][] healthBarUnits) {
        List<String> out = new ArrayList<>();
        // No position data at all means detection failed, not that every unit died —
        // there is no real count to "exceed", so nothing is floating.
        if (healthBarUnits == null) return out;
        int posCount  = healthBarUnits.length;
        int nameCount = plannerNames   == null ? 0 : plannerNames.size();
        for (int i = posCount; i < nameCount; i++) {
            String n = plannerNames.get(i);
            if (n != null && !n.isEmpty()) out.add(n);
        }
        return out;
    }

    private PlannerMerge() {}
}
