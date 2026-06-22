package com.xanfiend.tftoverlay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Positioning coach. Turns the scanned board into a placement plan: which units
 * go front vs back, which corner the carry hides in, and the evergreen
 * fundamentals that win otherwise-unwinnable rounds.
 *
 * Deliberately meta-agnostic. It reads only slow-changing positioning roles
 * (ThreatData) and the same carry pick the COACH tab uses (ChampItemData tier),
 * so nothing here goes stale with item patches. The base plan is fundamentals +
 * own-board sorting; the {@link #plan(List, String, OppScout.Profile)} overload
 * adds rule-based counter-positioning once opponent boards have been scried.
 *
 * Pure logic, no Android — easy to unit test.
 */
public final class PositionAdvisor {

    public static final class Plan {
        public boolean hasBoard = false;
        public String carry = "";              // main carry to protect (may be "")
        public String carryCorner = "";        // human-readable corner for the carry
        public final List<String> backline  = new ArrayList<>(); // back two rows
        public final List<String> frontline = new ArrayList<>(); // front two rows
        public final List<String> flankers  = new ArrayList<>(); // side/front corner
        public final List<String> tips = new ArrayList<>();      // evergreen checklist
    }

    /**
     * As {@link #plan(List, String)} but folds in counter-positioning advice
     * derived from scouted opponent boards (OppScout). When opp has no data the
     * result is identical to the fundamentals-only plan — so the POSITION tab
     * upgrades automatically once enemies have been scried, with no behavior
     * change before that.
     */
    public static Plan plan(List<String> board, String stageRound, OppScout.Profile opp){
        Plan p = plan(board, stageRound);
        if(opp != null && opp.hasData()){
            for(String t : opp.tips) p.tips.add(t);
        }
        return p;
    }

    /** board = champion names on your board (stars stripped). stageRound = "3-2" etc (may be ""). */
    public static Plan plan(List<String> board, String stageRound){
        Plan p = new Plan();
        if(board == null || board.isEmpty()) return p;
        p.hasBoard = true;

        // sort unique units by positioning role
        for(String name : new LinkedHashSet<>(board)){
            String role = ThreatData.roleOf(name);
            if(ThreatData.FRONT.equals(role))      p.frontline.add(name);
            else if(ThreatData.FLANK.equals(role)) p.flankers.add(name);
            else                                   p.backline.add(name);
        }

        // carry = same pick the COACH tab makes (highest-tier marked carry present)
        CompAdvisor.Rec rec = CompAdvisor.recommend(board);
        p.carry = rec.carry;

        // alternate the carry corner round to round so a scouting opponent can't
        // pre-aim their assassin at the same corner every fight. Even round -> left.
        int round = roundOf(stageRound);
        boolean left = (round % 2) == 0;
        p.carryCorner = left ? "BACK-LEFT corner" : "BACK-RIGHT corner";

        // ---- evergreen fundamentals ----
        if(!p.carry.isEmpty())
            p.tips.add("Put " + p.carry + " in the " + p.carryCorner + ", on the very back row, hugging the wall — max distance from melee threats.");
        else
            p.tips.add("Put your main damage dealer in a back corner on the very back row — max distance from melee threats.");
        p.tips.add("Switch the carry corner every round (scout first): a fixed corner lets the enemy pre-aim assassins and blitz hooks at it.");
        if(!p.flankers.isEmpty())
            p.tips.add("Flankers (" + join(p.flankers) + ") go on the opposite side / front corner so they reach the enemy backline, not your own front.");
        p.tips.add("Spread 1 hex apart against AoE/Blitz boards; clump tight only when YOU have the AoE and they don't.");
        p.tips.add("Drop your tankiest unit next to the carry corner as a body-block so divers eat the tank first.");
        p.tips.add("Scout the lobby before the round: position to beat the board you're about to fight, then hit READY.");

        return p;
    }

    private static int roundOf(String stageRound){
        if(stageRound == null) return 0;
        int dash = stageRound.indexOf('-');
        if(dash < 0 || dash+1 >= stageRound.length()) return 0;
        try { return Integer.parseInt(stageRound.substring(dash+1).trim()); }
        catch(Exception e){ return 0; }
    }

    private static String join(List<String> xs){
        StringBuilder b = new StringBuilder();
        for(int i=0;i<xs.size();i++){ if(i>0) b.append(", "); b.append(xs.get(i)); }
        return b.toString();
    }

    private PositionAdvisor(){}
}
