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

    // Keep in sync with SetData.CHAMPS / data/setdata.json when the set changes.
    static {
        // ---- Set 18 "Enchanted Wilds": melee FRONT, ranged BACK, assassins/divers FLANK ----
        r("Alistar",FRONT); r("Amumu",FRONT); r("Brambleback",FRONT); r("Diana",FRONT); r("ElderDragon",FRONT);
        r("Elise",FRONT); r("Fiddlesticks",FRONT); r("Gnar",FRONT); r("Hecarim",FRONT); r("Kennen",FRONT);
        r("Kobuko",FRONT); r("Krug",FRONT); r("Leona",FRONT); r("Lillia",FRONT); r("Malphite",FRONT);
        r("Maokai",FRONT); r("Murkwolf",FRONT); r("Ornn",FRONT); r("Rakan",FRONT); r("Rammus",FRONT);
        r("RekSai",FRONT); r("Scuttlecrab",FRONT); r("Sejuani",FRONT); r("Sentinel",FRONT); r("Sett",FRONT);
        r("Shen",FRONT); r("Taric",FRONT); r("Vi",FRONT); r("Warwick",FRONT); r("Yorick",FRONT);
        r("Ahri",BACK); r("Alune",BACK); r("Aphelios",BACK); r("Ashe",BACK); r("Azir",BACK);
        r("Caitlyn",BACK); r("Cassiopeia",BACK); r("Cinderling",BACK); r("Draven",BACK); r("Ezreal",BACK);
        r("Gromp",BACK); r("Ivern",BACK); r("Karma",BACK); r("Kayle",BACK); r("Kogmaw",BACK);
        r("Leblanc",BACK); r("Lux",BACK); r("MamaBeak",BACK); r("Morgana",BACK); r("Pebbles",BACK);
        r("Sivir",BACK); r("Soraka",BACK); r("Teemo",BACK); r("Tristana",BACK); r("Varus",BACK);
        r("Veigar",BACK); r("Xayah",BACK); r("Yunara",BACK); r("Zyra",BACK);
        r("Akali",FLANK); r("Camille",FLANK); r("Khazix",FLANK); r("MasterYi",FLANK); r("Nidalee",FLANK);
        r("Rengar",FLANK);
    }

    // ---- threat archetypes (orthogonal to role) — drive counter-positioning ----
    // HOOK: single-target grab/pull that snipes an isolated carry (don't leave a
    //       carry alone in a corner). AOE: area magic that punishes clumping
    //       (spread out). Meta-stable per set, like the roles above.
    private static final java.util.Set<String> HOOK = new java.util.HashSet<>(java.util.Arrays.asList(
        ));
    private static final java.util.Set<String> AOE = new java.util.HashSet<>(java.util.Arrays.asList(
        "Morgana", "Veigar"));

    public static boolean isHook(String champ){ return HOOK.contains(champ); }
    public static boolean isAoe(String champ){ return AOE.contains(champ); }

    // ---- damage type of the carries — drives defensive itemization (MR vs Armor) ----
    // Only damage-dealing carries are classified; enchanters/utility are left blank.
    private static final java.util.Set<String> AP = new java.util.HashSet<>(java.util.Arrays.asList(
        "Teemo", "Veigar", "Karma", "Morgana", "Akali", "Diana", "Leblanc"));
    private static final java.util.Set<String> AD = new java.util.HashSet<>(java.util.Arrays.asList(
        "Caitlyn", "Xayah", "MasterYi"));

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

    /** Every champion named in any table here; for roster-consistency tests. */
    static java.util.Set<String> names(){
        java.util.Set<String> all = new java.util.HashSet<>(ROLE.keySet());
        all.addAll(HOOK); all.addAll(AOE); all.addAll(AP); all.addAll(AD);
        return all;
    }

    private ThreatData(){}
}
