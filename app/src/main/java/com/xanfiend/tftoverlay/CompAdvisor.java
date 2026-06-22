package com.xanfiend.tftoverlay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Mid-game coach. Turns the scanned board + econ state into a recommendation:
 * which line to commit to, the items to build on the carry, and the next
 * tempo/econ move. Reads the verified per-patch carry table in ChampItemData
 * (carry -> comp / items / note / tier), so the comp advice reflects the same
 * meta snapshot the BUILDS tab uses rather than anything invented here.
 *
 * Pure logic, no Android — easy to unit test.
 */
public final class CompAdvisor {

    /** The recommendation for the current board. */
    public static final class Rec {
        public boolean hasBoard = false;     // false = nothing scanned yet
        public String comp = "";             // recommended comp (from the best carry present)
        public String carry = "";            // the carry it is built around
        public String[] items = new String[0];
        public String note = "";             // verified one-line plan for that carry/comp
        public String tier = "";             // carry tier S/A/B/C
        public final List<String> alsoCarries = new ArrayList<>(); // other itemizable carries you have
    }

    private static int tierRank(String t){
        if(t==null) return 0;
        switch(t){ case "S": return 4; case "A": return 3; case "B": return 2; case "C": return 1; default: return 0; }
    }

    /** board = champion names currently on your board (stars already stripped). */
    public static Rec recommend(List<String> board){
        Rec r = new Rec();
        if(board == null || board.isEmpty()) return r;
        r.hasBoard = true;
        String bestCarry = null; int bestRank = -1;
        for(String name : new LinkedHashSet<>(board)){
            if(!ChampItemData.has(name)) continue;       // only marked meta carries
            r.alsoCarries.add(name);
            int rk = tierRank(ChampItemData.tierOf(name));
            if(rk > bestRank){ bestRank = rk; bestCarry = name; }
        }
        if(bestCarry != null){
            ChampItemData.Build b = ChampItemData.get(bestCarry);
            r.carry = bestCarry; r.comp = b.comp; r.items = b.items; r.note = b.note;
            r.tier = ChampItemData.tierOf(bestCarry);
            r.alsoCarries.remove(bestCarry);
        }
        return r;
    }

    /** First number of a "3-2" stage string, or 0 if unknown. */
    public static int stageOf(String stageRound){
        if(stageRound == null) return 0;
        int dash = stageRound.indexOf('-');
        String s = dash > 0 ? stageRound.substring(0, dash) : stageRound;
        try { return Integer.parseInt(s.trim()); } catch(Exception e){ return 0; }
    }

    /** Second number of a "3-2" stage string, or 0 if unknown. */
    public static int roundOf(String stageRound){
        if(stageRound == null) return 0;
        int dash = stageRound.indexOf('-');
        if(dash < 0 || dash + 1 >= stageRound.length()) return 0;
        try { return Integer.parseInt(stageRound.substring(dash + 1).trim()); }
        catch(Exception e){ return 0; }
    }

    /** Roughly the level a standard player holds at this stage-round (greedy-but-safe
     *  benchmarks: L4 @2-1, L5 @2-5, L6 @3-2, L7 @4-1, L8 @4-5, L9 @5-5). 0 if unknown. */
    public static int expectedLevel(String stageRound){
        int stage = stageOf(stageRound), round = roundOf(stageRound);
        if(stage <= 0) return 0;
        if(stage == 1) return 3;
        if(stage == 2) return round >= 5 ? 5 : 4;
        if(stage == 3) return round >= 2 ? 6 : 5;
        if(stage == 4) return round >= 5 ? 8 : 7;
        if(stage == 5) return round >= 5 ? 9 : 8;
        return 9; // stage 6+
    }

    /** "Are you on curve?" — your level vs the stage benchmark. "" if stage unknown. */
    public static String levelCurve(int level, String stageRound){
        int exp = expectedLevel(stageRound);
        if(exp <= 0 || level <= 0) return "";
        int d = level - exp;
        if(d >= 1)  return "Ahead of curve (L" + level + " vs ~L" + exp + " standard) — press your HP lead; bank or push for 5-costs.";
        if(d == 0)  return "On curve (L" + level + ") — standard tempo for " + stageRound + ".";
        if(d == -1) return "Slightly behind (L" + level + " vs ~L" + exp + ") — get back on level unless you're slow-rolling on purpose.";
        return "Behind curve (L" + level + " vs ~L" + exp + ") — stabilize HP first, then catch levels.";
    }

    /** Standard tempo guidance from gold / level / stage. Meta-agnostic — the
     *  comp-specific roll plan comes from the carry note shown alongside this. */
    public static String econCall(int level, int gold, String stageRound){
        int stage = stageOf(stageRound);
        StringBuilder s = new StringBuilder();
        // econ band
        if(gold >= 50)      s.append("50+ gold = max interest: you can roll down to 50 freely.  ");
        else if(gold >= 30) s.append("Bank toward 50 for max interest unless you must stabilize.  ");
        else if(gold >= 20) s.append("Hold interest where you can.  ");
        // stage/level tempo
        if(stage <= 2)       s.append("Stage 2: build econ, play your strongest board, don't roll.");
        else if(stage == 3)  s.append("Stage 3: aim level 6 by 3-2; only roll if you're weak or low HP.");
        else if(stage == 4)  s.append("Stage 4: the commit spot. Reroll lines slow-roll now; fast-8 lines level to 8 then roll.");
        else if(stage >= 5)  s.append("Stage 5+: complete your final board and push levels for 5-costs.");
        else if(level <= 6)  s.append("Level on curve; don't roll your econ away early.");
        else                 s.append("Play to your board strength and HP.");
        return s.toString();
    }

    private CompAdvisor(){}
}
