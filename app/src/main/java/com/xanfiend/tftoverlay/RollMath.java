package com.xanfiend.tftoverlay;

import java.util.Random;

/*
 * Rolldown math: Monte Carlo simulation of the real TFT shop process.
 *
 * Each 2-gold roll shows 5 slots. Each slot first rolls a cost tier from the
 * level's odds row (SetData.ODDS), then draws a champion uniformly from that
 * tier's remaining bag. Buying a found copy shrinks both the champion's
 * remaining count and the tier total, so later slots get slightly worse —
 * the simulation models exactly that, which closed-form binomials don't.
 *
 * A few thousand iterations of a 25-roll game is well under 10ms on any
 * phone, so this runs synchronously when the ODDS tab is built.
 */
public final class RollMath {

    private static final int ITERATIONS = 3000;
    private static final Random RNG = new Random();

    // Memoized results. The panel rebuilds on every tap and re-runs the sims for
    // every tracked champion, almost always with unchanged inputs — the cache makes
    // those rebuilds O(1) and keeps the displayed odds stable between rebuilds
    // instead of jittering a couple of percent on each Monte Carlo re-run.
    private static final java.util.HashMap<Long, double[]> HIT_CACHE = new java.util.HashMap<>();
    private static final java.util.HashMap<Long, Integer> GOLD_CACHE = new java.util.HashMap<>();
    private static final int CACHE_MAX = 512;

    private static long key(int level, int cost, int rem, int total, int gold, int extra) {
        long k = level;
        k = k * 11 + cost;
        k = k * 512 + Math.min(511, rem);
        k = k * 4096 + Math.min(4095, total);
        k = k * 256 + Math.min(255, gold);
        return k * 16 + extra;
    }

    /*
     * P(finding at least 1..maxCopies copies of one champion) when rolling
     * down `gold` gold at `level`.
     *   remTarget  = copies of the champion left in the shared pool
     *   tierTotal  = total remaining units of that cost tier (incl. target)
     * Returns double[maxCopies], [0] = P(>=1 copy), [1] = P(>=2), ...
     */
    public static double[] hitChances(int level, int cost, int remTarget,
                                      int tierTotal, int gold, int maxCopies){
        double[] out = new double[Math.max(1, maxCopies)];
        if (level < 1 || level > 10 || cost < 1 || cost > 5) return out;
        int rolls = gold / 2;
        if (rolls <= 0 || remTarget <= 0 || tierTotal <= 0) return out;
        int slotPct = SetData.ODDS[level][cost - 1];
        if (slotPct <= 0) return out;

        long k = key(level, cost, remTarget, tierTotal, gold, out.length);
        synchronized (HIT_CACHE) {
            double[] hit = HIT_CACHE.get(k);
            if (hit != null) return hit.clone();
        }

        int[] hitsAtLeast = new int[out.length];
        for (int it = 0; it < ITERATIONS; it++) {
            int rem = remTarget, total = tierTotal, found = 0;
            for (int roll = 0; roll < rolls && found < out.length; roll++) {
                for (int slot = 0; slot < 5; slot++) {
                    if (RNG.nextInt(100) >= slotPct) continue;   // slot rolled another tier
                    if (RNG.nextInt(total) < rem) {              // slot is the target champ
                        found++; rem--; total--;
                        if (rem <= 0 || found >= out.length) break;
                    }
                }
            }
            for (int i = 0; i < found && i < out.length; i++) hitsAtLeast[i]++;
        }
        for (int i = 0; i < out.length; i++) out[i] = hitsAtLeast[i] / (double) ITERATIONS;
        synchronized (HIT_CACHE) {
            if (HIT_CACHE.size() >= CACHE_MAX) HIT_CACHE.clear();
            HIT_CACHE.put(k, out.clone());
        }
        return out;
    }

    /*
     * Average gold spent rolling until the first copy appears, capped at
     * `goldCap`. Returns -1 when more than half the simulations never hit
     * within the cap (i.e. "don't bother" territory).
     */
    public static int expectedGoldToFirst(int level, int cost, int remTarget,
                                          int tierTotal, int goldCap){
        if (level < 1 || level > 10 || cost < 1 || cost > 5) return -1;
        if (remTarget <= 0 || tierTotal <= 0) return -1;
        int slotPct = SetData.ODDS[level][cost - 1];
        if (slotPct <= 0) return -1;
        int maxRolls = Math.max(1, goldCap / 2);

        long k = key(level, cost, remTarget, tierTotal, goldCap, 0);
        synchronized (GOLD_CACHE) {
            Integer cached = GOLD_CACHE.get(k);
            if (cached != null) return cached;
        }

        long goldSum = 0; int hits = 0;
        for (int it = 0; it < ITERATIONS; it++) {
            int rem = remTarget, total = tierTotal;
            for (int roll = 1; roll <= maxRolls; roll++) {
                boolean hit = false;
                for (int slot = 0; slot < 5; slot++) {
                    if (RNG.nextInt(100) >= slotPct) continue;
                    if (RNG.nextInt(total) < rem) { hit = true; break; }
                }
                if (hit) { goldSum += roll * 2L; hits++; break; }
            }
        }
        int result = hits < ITERATIONS / 2 ? -1 : (int) (goldSum / hits);
        synchronized (GOLD_CACHE) {
            if (GOLD_CACHE.size() >= CACHE_MAX) GOLD_CACHE.clear();
            GOLD_CACHE.put(k, result);
        }
        return result;
    }

    private RollMath() {}
}
