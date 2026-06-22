package com.xanfiend.tftoverlay;

import java.util.HashMap;
import java.util.Map;

/*
 * ============================================================
 *  POSITIONING ROLES  --  where each unit wants to stand
 * ============================================================
 *
 *  Feeds the POSITION sub-tab. Unlike item metas, a champion's
 *  positioning role barely moves patch-to-patch (a backline
 *  hypercarry stays backline; a tank stays front), so this list
 *  is far more durable than ChampItemData and does NOT need a
 *  per-patch refresh — only a per-SET refresh when the roster
 *  changes.
 *
 *  role:
 *    FRONT — tanks / bruisers: front two rows, soak damage.
 *    BACK  — ranged / AP carries: back corner, max distance.
 *    FLANK — assassins / divers / blink units: side or front
 *            corner so they reach the enemy backline fast.
 *
 *  Only units with a clear role are listed; everything else
 *  falls back to a guess from ChampItemData's role string, then
 *  to BACK (the safe default — better to over-protect a unit
 *  than to feed it to the front).
 * ============================================================
 */
public final class ThreatData {

    public static final String FRONT = "FRONT";
    public static final String BACK  = "BACK";
    public static final String FLANK = "FLANK";

    private static final Map<String,String> ROLE = new HashMap<>();
    private static void r(String champ, String role){ ROLE.put(champ, role); }

    static {
        // ---- backline carries / casters ----
        r("Corki",BACK); r("Jhin",BACK); r("Xayah",BACK); r("Kaisa",BACK);
        r("MissFortune",BACK); r("AurelionSol",BACK); r("Karma",BACK); r("Vex",BACK);
        r("Sona",BACK); r("Bard",BACK); r("Samira",BACK);
        // ---- frontline tanks / bruisers ----
        r("Rammus",FRONT); r("TahmKench",FRONT); r("Ornn",FRONT); r("Shen",FRONT);
        r("Blitzcrank",FRONT); r("Riven",FRONT);
        // ---- flankers: divers / assassins / blink that hunt the enemy backline ----
        // (kept small — only units that genuinely want a flank, not a back corner)
        r("Fiora",FLANK); r("Yasuo",FLANK); r("Akali",FLANK); r("Katarina",FLANK);
    }

    /** Positioning role for a champion. Falls back to ChampItemData's role
     *  string, then to BACK if nothing is known. Never returns null. */
    public static String roleOf(String champ){
        String role = ROLE.get(champ);
        if(role != null) return role;
        ChampItemData.Build b = ChampItemData.get(champ);
        if(b != null){
            String r = b.role == null ? "" : b.role.toLowerCase();
            if(r.contains("tank") || r.contains("bruiser") || r.contains("front")) return FRONT;
            if(r.contains("assassin") || r.contains("dive") || r.contains("flank")) return FLANK;
            return BACK; // any "carry" role defaults to backline
        }
        return BACK;
    }

    private ThreatData(){}
}
