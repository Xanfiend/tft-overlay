package com.xanfiend.tftoverlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opponent threat model — aggregates the remembered enemy boards (Pool slots
 * 1-7) into one lobby-wide profile and turns it into counter-positioning advice.
 *
 * This is the brain the v2.0 opponent scan feeds. Like PositionAdvisor it reads
 * only slow-changing positioning roles (ThreatData), so it never goes stale with
 * item patches. Pure logic, no Android — unit-testable without a game.
 *
 * The automation that fills these boards (scan every enemy in one planning
 * phase) is a separate, live-tested piece; this consumes whatever boards are
 * already remembered, so it works today off manually-scried opponents and scales
 * straight into the one-pass scan later.
 */
public final class OppScout {

    public static final class Profile {
        public int boards = 0;                 // how many non-empty enemy boards seen
        public int front = 0, back = 0, flank = 0;   // role counts across the lobby
        public boolean flankHeavy = false;     // assassin/diver-heavy lobby
        public final List<String> topCarries = new ArrayList<>(); // most common backline threats
        public final List<String> tips = new ArrayList<>();       // counter-positioning advice
        public boolean hasData(){ return boards > 0; }
    }

    /** boards = each enemy board as name->stars; null/empty entries are skipped. */
    public static Profile analyze(List<Map<String,Integer>> boards){
        Profile p = new Profile();
        if(boards == null) return p;

        // count roles across every scouted board; tally how often each backline
        // carry shows up so the most-contested threats float to the top
        Map<String,Integer> backFreq = new LinkedHashMap<>();
        for(Map<String,Integer> board : boards){
            if(board == null || board.isEmpty()) continue;
            p.boards++;
            for(String name : board.keySet()){
                String role = ThreatData.roleOf(name);
                if(ThreatData.FRONT.equals(role))      p.front++;
                else if(ThreatData.FLANK.equals(role)) p.flank++;
                else { p.back++; backFreq.merge(name, 1, Integer::sum); }
            }
        }
        if(p.boards == 0) return p;

        // assassin-heavy = flankers are a meaningful slice of the lobby's units
        int total = p.front + p.back + p.flank;
        p.flankHeavy = p.flank >= 3 || (total > 0 && p.flank * 100 / total >= 25);

        // top enemy carries = most frequently seen backline threats (max 3)
        List<Map.Entry<String,Integer>> es = new ArrayList<>(backFreq.entrySet());
        es.sort((a,b) -> b.getValue() - a.getValue());
        for(int i = 0; i < es.size() && i < 3; i++) p.topCarries.add(es.get(i).getKey());

        // ---- counter-positioning advice (rule-based, evergreen) ----
        if(p.flankHeavy)
            p.tips.add("Lobby is assassin/diver-heavy (" + p.flank + " flankers seen). Body-block your carry corner with a tank and keep a second-row guard so divers can't drop straight onto your backline.");
        else
            p.tips.add("Few divers seen so far — a standard back-corner carry is safe, but still switch corners each round so it can't be pre-aimed.");

        if(!p.topCarries.isEmpty())
            p.tips.add("Most common enemy carry: " + p.topCarries.get(0) + ". Position to survive its damage and consider teching/itemizing against it.");

        if(p.front >= p.back && p.front > 0)
            p.tips.add("Frontline-heavy lobby — expect long fights; spread a hex so their tanks can't clump your team for AoE.");

        return p;
    }

    private OppScout(){}
}
