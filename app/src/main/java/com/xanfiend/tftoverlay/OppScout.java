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
        public int hooks = 0, aoe = 0;         // archetype counts (grab / area casters)
        public int apCarries = 0, adCarries = 0; // damage-type split across the lobby
        public int apItems = 0, adItems = 0, healItems = 0; // item-evidence split (Phase 2)
        public boolean flankHeavy = false;     // assassin/diver-heavy lobby
        public String topThreat = "";          // single scariest unit (star x cost)
        public int topThreatStars = 0;
        public int topBoardVal = 0, avgBoardVal = 0; // strongest vs average enemy board strength
        public final List<String> topCarries = new ArrayList<>(); // most common backline threats
        public final List<String> tips = new ArrayList<>();       // counter-positioning advice (POSITION)
        public final List<String> techTips = new ArrayList<>();   // defensive itemization advice (COACH)
        public boolean hasData(){ return boards > 0; }
    }

    /** Map-based entry (name->stars, no items): adapts to the unit-based analyzer
     *  so callers that don't have item data keep working unchanged. */
    public static Profile analyze(List<Map<String,Integer>> boards){
        List<List<Pool.OppUnit>> u = new ArrayList<>();
        if(boards != null) for(Map<String,Integer> b : boards){
            if(b == null) continue;
            List<Pool.OppUnit> bu = new ArrayList<>();
            for(Map.Entry<String,Integer> e : b.entrySet())
                bu.add(new Pool.OppUnit(e.getKey(), e.getValue()==null?1:e.getValue(), null));
            u.add(bu);
        }
        return analyzeUnits(u);
    }

    /** boards = each enemy board as rich units (name/stars/items); null/empty
     *  entries skipped. Item fields sharpen the tech read when present (Phase 2),
     *  and are simply absent until the scan fills them — identical output to the
     *  Map-based path when no items are known. */
    public static Profile analyzeUnits(List<List<Pool.OppUnit>> boards){
        Profile p = new Profile();
        if(boards == null) return p;

        // count roles across every scouted board; tally how often each backline
        // carry shows up so the most-contested threats float to the top
        Map<String,Integer> backFreq = new LinkedHashMap<>();
        int bestThreatWeight = -1;   // star x cost — picks the single fed carry to respect
        int sumBoardVal = 0;         // for the lobby-power read (snowball detection)
        for(List<Pool.OppUnit> board : boards){
            if(board == null || board.isEmpty()) continue;
            p.boards++;
            int boardVal = 0;
            for(Pool.OppUnit un : board){
                String name = un.name;
                int stars = Math.max(1, un.stars);
                int cost = Math.max(1, Pool.costOf(name));
                for(String it : un.items){
                    if(ItemData.isApItem(it)) p.apItems++;
                    else if(ItemData.isAdItem(it)) p.adItems++;
                    if(ItemData.isHealItem(it)) p.healItems++;
                }
                // board strength ≈ cost x star multiplier (each star ~3x the unit)
                boardVal += cost * (stars >= 3 ? 9 : stars == 2 ? 3 : 1);
                String role = ThreatData.roleOf(name);
                if(ThreatData.FRONT.equals(role))      p.front++;
                else if(ThreatData.FLANK.equals(role)) p.flank++;
                else { p.back++; backFreq.merge(name, 1, Integer::sum); }
                if(ThreatData.isHook(name)) p.hooks++;
                if(ThreatData.isAoe(name))  p.aoe++;
                String dt = ThreatData.damageType(name);
                if("AP".equals(dt))      p.apCarries++;
                else if("AD".equals(dt)) p.adCarries++;
                // biggest single threat: weight star level by cost, skip pure tanks
                if(!ThreatData.FRONT.equals(role)){
                    int w = stars * 10 + cost;
                    if(w > bestThreatWeight){
                        bestThreatWeight = w; p.topThreat = name; p.topThreatStars = stars;
                    }
                }
            }
            sumBoardVal += boardVal;
            if(boardVal > p.topBoardVal) p.topBoardVal = boardVal;
        }
        if(p.boards == 0) return p;
        p.avgBoardVal = sumBoardVal / p.boards;

        // assassin-heavy = flankers are a meaningful slice of the lobby's units
        int total = p.front + p.back + p.flank;
        p.flankHeavy = p.flank >= 3 || (total > 0 && p.flank * 100 / total >= 25);

        // top enemy carries = most frequently seen backline threats (max 3)
        List<Map.Entry<String,Integer>> es = new ArrayList<>(backFreq.entrySet());
        es.sort((a,b) -> b.getValue() - a.getValue());
        for(int i = 0; i < es.size() && i < 3; i++) p.topCarries.add(es.get(i).getKey());

        // ---- counter-positioning advice (rule-based, evergreen) ----
        if(!p.topThreat.isEmpty()){
            StringBuilder st = new StringBuilder();
            for(int i = 0; i < p.topThreatStars; i++) st.append("★");
            p.tips.add("Biggest threat: " + p.topThreat + (p.topThreatStars >= 2 ? " " + st : "")
                + " — focus it, body-block its access, and tech against its damage.");
        }
        if(p.flankHeavy)
            p.tips.add("Lobby is assassin/diver-heavy (" + p.flank + " flankers seen). Body-block your carry corner with a tank and keep a second-row guard so divers can't drop straight onto your backline.");
        else
            p.tips.add("Few divers seen so far — a standard back-corner carry is safe, but still switch corners each round so it can't be pre-aimed.");

        if(p.hooks > 0)
            p.tips.add("Hook threat in the lobby (" + p.hooks + " — Blitzcrank/Pyke). Don't leave your carry alone in a corner: keep a unit next to it so the grab can pull the wrong target.");

        if(p.aoe >= 2)
            p.tips.add("Multiple AoE casters (" + p.aoe + "). Spread your team a hex apart so a single cast can't catch your whole board.");
        else if(p.aoe == 1)
            p.tips.add("An AoE caster is around — don't stack your whole team in one corner where one spell hits everyone.");

        if(!p.topCarries.isEmpty())
            p.tips.add("Most common enemy carry: " + p.topCarries.get(0) + ". Position to survive its damage and consider teching/itemizing against it.");

        if(p.front >= p.back && p.front > 0)
            p.tips.add("Frontline-heavy lobby — expect long fights; spread a hex so their tanks can't clump your team for AoE.");

        // lobby-power read: one board clearly above the field = a snowballing player
        if(p.boards >= 2 && p.avgBoardVal > 0 && p.topBoardVal >= p.avgBoardVal * 3 / 2)
            p.tips.add("One opponent is ahead of the lobby on board strength — don't take that fight with a thin board; dodge to a weaker matchup when you can.");

        // ---- defensive itemization advice (COACH) ----
        if(p.apCarries > 0 || p.adCarries > 0){
            if(p.apCarries >= 2 && p.apCarries > p.adCarries * 2)
                p.techTips.add("Lobby skews AP (" + p.apCarries + " magic carries). Slam Magic Resist on your front — Dragon's Claw on the most-targeted unit, Spectre's/Adaptive from Negatron.");
            else if(p.adCarries >= 2 && p.adCarries > p.apCarries * 2)
                p.techTips.add("Lobby skews AD (" + p.adCarries + " physical carries). Build Armor — Bramble Vest on melee-targeted units, Gargoyle on your tank.");
            else
                p.techTips.add("Mixed damage (" + p.adCarries + " AD / " + p.apCarries + " AP). Gargoyle Stoneplate, or split one Bramble + one Dragon's Claw across your front.");
        }
        if(p.front >= 4)
            p.techTips.add("Tanky lobby — pack anti-heal (Morellonomicon / Sunfire Cape) so their frontline actually dies.");

        // ---- item-evidence tech read (Phase 2) — fires only once the scan has read
        // enemy items; until then these counts are 0 and nothing extra is added ----
        if(p.healItems > 0)
            p.techTips.add("Item read: enemy carries hold healing items (" + p.healItems + " — Bloodthirster/Gunblade/Hand of Justice). Anti-heal (Morellonomicon/Sunfire) is high value here, not optional.");
        if(p.adItems > p.apItems && p.adItems > 0)
            p.techTips.add("Item read: enemy damage items skew AD (" + p.adItems + " AD / " + p.apItems + " AP). Prioritize Armor over MR.");
        else if(p.apItems > p.adItems && p.apItems > 0)
            p.techTips.add("Item read: enemy damage items skew AP (" + p.apItems + " AP / " + p.adItems + " AD). Prioritize Magic Resist over Armor.");

        return p;
    }

    private OppScout(){}
}
