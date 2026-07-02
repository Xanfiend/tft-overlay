package com.xanfiend.tftoverlay;

import java.util.List;

/*
 * Pure fuzzy champion-name matching for the autoscan. The live ScreenScanner
 * matcher is containment-based (exact substring / word overlap), so a single
 * misread letter — "Jhln" for "Jhin", "Samlra" for "Samira" — fails to match.
 * This adds Levenshtein edit-distance tolerance, scaled by name length and
 * guarded against short-name false positives and ambiguous ties, so one or two
 * OCR slips on a longer name still resolve to the right champion.
 *
 * GROUNDWORK (dormant): built + tested; wiring it into ScreenScanner as a
 * fallback AFTER the existing exact/contains checks is a device-verified step
 * (it must not start mis-resolving real reads, which only a screen can confirm).
 */
public final class NameMatch {

    /* lowercase a-z only — mirrors ScreenScanner.norm() so apostrophes, spaces,
     * digits and case never block a match (Kai'Sa -> kaisa, Bel'Veth -> belveth). */
    public static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c >= 'a' && c <= 'z') b.append(c);
        }
        return b.toString();
    }

    /* Classic Levenshtein edit distance (two-row, O(n*m) time, O(m) space). */
    public static int editDistance(String a, String b) {
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[m];
    }

    /* Allowed edits for a normalized target of the given length. Conservative:
     * short names need exact (fuzzy on 3-4 chars false-positives badly). */
    public static int tolerance(int len) {
        if (len <= 4) return 0;   // Zoe / Vex / Jhin / Fizz / Nami / Ornn
        if (len <= 7) return 1;
        return 2;
    }

    /* Best champion match for an OCR string among candidates, or null when none
     * is within tolerance or the winner is ambiguous (tied with a runner-up).
     * Matching is on normalized forms; exact and high-overlap containment rank
     * ahead of distant edits. */
    public static String bestMatch(String ocr, List<String> candidates) {
        if (candidates == null) return null;
        String[] names = new String[candidates.size()];
        String[] norms = new String[candidates.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = candidates.get(i);
            norms[i] = norm(names[i]);
        }
        return bestMatch(ocr, names, norms);
    }

    /* Core matcher against a roster whose normalized forms are precomputed
     * (parallel arrays). Callers that match many OCR blocks per screenshot
     * (ScreenScanner) pass their cached arrays so the roster is never
     * re-normalized per block. A length gate skips the O(n*m) edit distance
     * for candidates whose length difference alone exceeds tolerance. */
    public static String bestMatch(String ocr, String[] names, String[] norms) {
        String o = norm(ocr);
        if (o.length() < 3 || names == null) return null;
        String best = null;
        int bestD = Integer.MAX_VALUE, secondD = Integer.MAX_VALUE;
        for (int i = 0; i < names.length; i++) {
            String t = norms[i];
            if (t.isEmpty()) continue;
            int tol = tolerance(t.length());
            int d;
            if (o.equals(t)) {
                d = 0;
            } else if (t.length() >= 6 && (t.contains(o) || o.contains(t))
                    && Math.min(o.length(), t.length()) * 10 >= Math.max(o.length(), t.length()) * 8) {
                d = 1;
            } else if (Math.abs(o.length() - t.length()) > tol) {
                continue;
            } else {
                d = editDistance(o, t);
            }
            if (d > tol) continue;
            if (d < bestD) { secondD = bestD; bestD = d; best = names[i]; }
            else if (d < secondD) { secondD = d; }
        }
        if (best != null && bestD == secondD) return null;   // ambiguous — don't guess
        return best;
    }

    private NameMatch() {}
}
