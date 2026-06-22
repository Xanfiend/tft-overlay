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

    // Full Set 17 roster, every champion assigned by archetype. Keep in sync with
    // SetData.CHAMPS / data/setdata.json when the set changes.
    static {
        // ---- FRONT: tanks, bruisers, melee fighters (front two rows) ----
        r("Aatrox",FRONT); r("Briar",FRONT); r("Chogath",FRONT); r("Leona",FRONT);
        r("Nasus",FRONT); r("Poppy",FRONT); r("RekSai",FRONT); r("Belveth",FRONT);
        r("Gnar",FRONT); r("Gragas",FRONT); r("Gwen",FRONT); r("Jax",FRONT);
        r("Mordekaiser",FRONT); r("Pantheon",FRONT); r("Illaoi",FRONT); r("Maokai",FRONT);
        r("Ornn",FRONT); r("Rhaast",FRONT); r("Urgot",FRONT); r("Nunu",FRONT);
        r("Rammus",FRONT); r("Riven",FRONT); r("TahmKench",FRONT); r("MightyMech",FRONT);
        r("Blitzcrank",FRONT); r("Shen",FRONT);

        // ---- BACK: ranged carries, casters, enchanters (back two rows) ----
        r("Caitlyn",BACK); r("Ezreal",BACK); r("Lissandra",BACK); r("Teemo",BACK);
        r("TwistedFate",BACK); r("Veigar",BACK); r("Jinx",BACK); r("Meepsie",BACK);
        r("Milio",BACK); r("Zoe",BACK); r("Aurora",BACK); r("Kaisa",BACK);
        r("Lulu",BACK); r("MissFortune",BACK); r("Samira",BACK); r("Viktor",BACK);
        r("AurelionSol",BACK); r("Corki",BACK); r("Karma",BACK); r("Kindred",BACK);
        r("Morgana",BACK); r("Nami",BACK); r("Xayah",BACK); r("Bard",BACK);
        r("Graves",BACK); r("Jhin",BACK); r("Sona",BACK); r("Vex",BACK);

        // ---- FLANK: assassins / divers / blink that hunt the enemy backline ----
        r("Talon",FLANK); r("Akali",FLANK); r("Pyke",FLANK); r("Diana",FLANK);
        r("Fizz",FLANK); r("Leblanc",FLANK); r("MasterYi",FLANK); r("Fiora",FLANK);
    }

    // ---- threat archetypes (orthogonal to role) — drive counter-positioning ----
    // HOOK: single-target grab/pull that snipes an isolated carry (don't leave a
    //       carry alone in a corner). AOE: area magic that punishes clumping
    //       (spread out). Meta-stable per set, like the roles above.
    private static final java.util.Set<String> HOOK = new java.util.HashSet<>(java.util.Arrays.asList(
        "Blitzcrank", "Pyke"));
    private static final java.util.Set<String> AOE = new java.util.HashSet<>(java.util.Arrays.asList(
        "AurelionSol", "Gragas", "Viktor", "Vex", "Morgana", "Veigar", "Lissandra"));

    public static boolean isHook(String champ){ return HOOK.contains(champ); }
    public static boolean isAoe(String champ){ return AOE.contains(champ); }

    // ---- damage type of the carries — drives defensive itemization (MR vs Armor) ----
    // Only damage-dealing carries are classified; enchanters/utility are left blank.
    private static final java.util.Set<String> AP = new java.util.HashSet<>(java.util.Arrays.asList(
        "Lissandra", "Teemo", "TwistedFate", "Veigar", "Zoe", "Aurora", "Viktor",
        "AurelionSol", "Karma", "Vex", "Morgana", "Akali", "Diana", "Leblanc", "Fizz"));
    private static final java.util.Set<String> AD = new java.util.HashSet<>(java.util.Arrays.asList(
        "Caitlyn", "Jinx", "Kaisa", "MissFortune", "Samira", "Corki", "Kindred", "Xayah",
        "Graves", "Jhin", "Talon", "Pyke", "MasterYi", "Fiora"));

    /** "AP", "AD", or "" if the champ isn't a classified damage carry. */
    public static String damageType(String champ){
        if(AP.contains(champ)) return "AP";
        if(AD.contains(champ)) return "AD";
        return "";
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
