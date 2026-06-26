package com.xanfiend.tftoverlay;

import java.util.LinkedHashMap;
import java.util.List;

/*
 * Pure reconciliation of raw OCR champion-name reads into a clean roster with
 * copy counts. The autoscan gathers names from several passes (planner snapshot,
 * popup taps, bench) that are noisy and duplicated; this canonicalizes each raw
 * read to a real champion via NameMatch, drops junk, and tallies copies.
 *
 * GROUNDWORK (dormant): pure + tested. ScreenScanner / OverlayService can adopt
 * it to fold multi-pass reads into one board roster. The merge itself is fully
 * deterministic (tested here); only the raw reads upstream are OCR/device-bound.
 */
public final class RosterMerge {

    /* Canonicalize + tally. Each raw read is resolved against the roster with
     * NameMatch; unresolved reads are dropped (live, those get a tap-OCR retry).
     * Returns canonical-name -> copies, insertion-ordered by first sighting. */
    public static LinkedHashMap<String,Integer> tally(List<String> rawReads, List<String> roster) {
        LinkedHashMap<String,Integer> out = new LinkedHashMap<>();
        if (rawReads == null || roster == null) return out;
        for (String raw : rawReads) {
            String canon = NameMatch.bestMatch(raw, roster);
            if (canon == null) continue;
            out.merge(canon, 1, Integer::sum);
        }
        return out;
    }

    /* Star level implied by copies under TFT's 3-merge rule: 3 = 2-star,
     * 9 = 3-star. Clamped; 0 copies = not on board. */
    public static int starsFromCopies(int copies) {
        if (copies >= 9) return 3;
        if (copies >= 3) return 2;
        if (copies >= 1) return 1;
        return 0;
    }

    /* Of `detectedUnits` positions found (e.g. from health bars), how many were
     * NOT resolved to a name by the tally — i.e. still need a tap-OCR fallback. */
    public static int unresolvedCount(int detectedUnits, LinkedHashMap<String,Integer> tally) {
        int resolved = 0;
        if (tally != null) for (int v : tally.values()) resolved += v;
        return Math.max(0, detectedUnits - resolved);
    }

    /* Combine two tallies from different scan passes without double-counting.
     * Primary wins: a champion already present in `primary` is NOT incremented
     * by `secondary`, because both passes saw the same physical unit.
     * Champions only in `secondary` are added at their secondary count.
     * Null inputs are treated as empty maps. */
    public static LinkedHashMap<String,Integer> mergeNoDoubleCount(
            LinkedHashMap<String,Integer> primary,
            LinkedHashMap<String,Integer> secondary) {
        LinkedHashMap<String,Integer> out = new LinkedHashMap<>();
        if (primary != null) out.putAll(primary);
        if (secondary != null) {
            for (java.util.Map.Entry<String,Integer> e : secondary.entrySet()) {
                if (!out.containsKey(e.getKey())) out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private RosterMerge() {}
}
