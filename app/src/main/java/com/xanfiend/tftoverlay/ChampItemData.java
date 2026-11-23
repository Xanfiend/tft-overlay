package com.xanfiend.tftoverlay;

import java.util.HashMap;
import java.util.Map;

/*
 * ============================================================
 *  CHAMPION META BUILDS  --  best-in-slot items per carry
 * ============================================================
 *
 *  Feeds the BUILDS tab: tap a champion -> see the items that
 *  are meta on them THIS patch, the comp they carry, and a
 *  one-line tip.
 *
 *  ---- KEEPING THIS CURRENT ----
 *  BIS shifts every patch. When the meta moves (or a new set
 *  drops) update three things:
 *    1. PATCH below, to the live patch.
 *    2. The Build entries (items / comp / note) for each carry.
 *    3. Add/remove carries as the meta changes.
 *  Only the units players actually itemize need an entry; every
 *  other champion falls back to a generic frontline/backline tip
 *  in the UI, so the table never has to be exhaustive.
 *
 *  Item names are the in-game display names; they do not have to
 *  match ItemData's component matrix (this is a reference list,
 *  not the builder).
 *
 *  Data sourced from MetaTFT, Blitz, OP.GG, PivotTFT and
 *  SeeMeta for Set 18 patch 18.7 (November 2026).
 * ============================================================
 */
public final class ChampItemData {

    // Live patch this build data reflects. Shown in the BUILDS tab.
    public static final String PATCH = "18.7";
    // Set this data was written for; the BUILDS tab warns when the live set differs.
    public static final String SET_LABEL = "Set 18";

    /** One champion's meta build. */
    public static final class Build {
        public final String role;    // "AD Carry", "AP Carry", "Tank", "Bruiser"
        public final String[] items; // best-in-slot items, best first (usually 3)
        public final String comp;    // headline comp this carry anchors
        public final String note;    // one-line tip, "" if none
        public Build(String role, String[] items, String comp, String note){
            this.role=role; this.items=items; this.comp=comp; this.note=note;
        }
    }

    private static final Map<String,Build> M = new HashMap<>();

    private static void put(String champ, String role, String[] items, String comp, String note){
        M.put(champ, new Build(role, items, comp, note));
    }

    static {
        // ---------- AD carries ----------
        put("MasterYi", "AD Carry",
            new String[]{"Deathblade","Rageblade","Giant Slayer"},
            "Adaptor Yi Reroll",
            "Top S-tier. Slow-roll level 7-8 for 3-star. Adaptor trait adapts to your board.");
        put("Pebbles", "AD Bruiser Reroll",
            new String[]{"Rageblade","Warmog's Armor","Deathblade"},
            "Riftbeast Pebbles Reroll",
            "1-cost reroll. Slow-roll level 7. Beast Within is the flagship augment here.");
        put("Aphelios", "AD Carry",
            new String[]{"Deathblade","Rageblade","Last Whisper"},
            "Elderwood Aphelios Fast 8",
            "S-tier 4-cost. Elderwood trait scaling. Fast 8, slam AD items early.");
        put("Sivir", "AD Carry",
            new String[]{"Deathblade","Rageblade","Infinity Edge"},
            "Hunter Sivir",
            "Hunter trait maximised with Sivir at 2-star. Fast 8.");
        put("Tristana", "AD Reroll Carry",
            new String[]{"Deathblade","Rageblade","Infinity Edge"},
            "Tristana Reroll",
            "3-cost reroll. Slow-roll level 7 for 3-star.");
        put("Khazix", "AD Assassin Carry",
            new String[]{"Deathblade","Infinity Edge","Giant Slayer"},
            "Elderwood Kha'Zix",
            "Elderwood assassin carry. Fast 8. Pivot to Ezreal if Khazix is contested.");
        put("Ezreal", "AD Flex Carry",
            new String[]{"Deathblade","Rageblade","Rabadon's Deathcap"},
            "Elderwood Fast 9",
            "Flex into Elderwood. Rabadon's if the board has AP synergy.");
        put("Draven", "AD Carry",
            new String[]{"Deathblade","Infinity Edge","Giant Slayer"},
            "Draven Fast 9",
            "5-cost AD carry. Highest AD cap. Slam swords early for the spill.");
        put("Ashe", "AD Carry",
            new String[]{"Deathblade","Rageblade","Last Whisper"},
            "Ashe Fast 9",
            "5-cost consistent AD carry. Last Whisper essential vs tank frontlines.");
        put("Rengar", "AD Assassin",
            new String[]{"Deathblade","Infinity Edge","Edge of Night"},
            "Elderwood Rengar",
            "Assassin dive. Edge of Night first so he doesn't get CC'd mid-leap.");

        // ---------- AP carries ----------
        put("Veigar", "AP Reroll Carry",
            new String[]{"Jeweled Gauntlet","Rabadon's Deathcap","Blue Buff"},
            "Spellweaver Veigar Reroll",
            "1-cost AP reroll. Slow-roll level 6-7. Spellweaver vertical amplifies AP.");
        put("Teemo", "AP Poison Carry",
            new String[]{"Blue Buff","Jeweled Gauntlet","Morellonomicon"},
            "Sprykin Teemo Reroll",
            "Sprykin poison damage. Morellonomicon syncs with the DoT.");
        put("Nidalee", "AP Carry",
            new String[]{"Jeweled Gauntlet","Rabadon's Deathcap","Morellonomicon"},
            "Lunar Nidalee",
            "Lunar trait AP carry. 4-cost — fast 8, slot JG + Rabadon's.");
        put("Alune", "AP Carry",
            new String[]{"Jeweled Gauntlet","Rabadon's Deathcap","Spear of Shojin"},
            "Vanguard Alune Fast 9",
            "5-cost AP carry. Vanguard + AP traits. Spear for mana cycling.");
        put("Lux", "AP Carry",
            new String[]{"Jeweled Gauntlet","Rabadon's Deathcap","Spear of Shojin"},
            "Lux Fast 9",
            "5-cost high single-target AP. JG crit is core.");
        put("Ahri", "AP Carry",
            new String[]{"Jeweled Gauntlet","Rabadon's Deathcap","Morellonomicon"},
            "Invoker Ahri",
            "Invoker trait gives mana on ally spell cast — stack AP and roll early.");
        put("Karma", "AP Support Carry",
            new String[]{"Jeweled Gauntlet","Spear of Shojin","Blue Buff"},
            "AP Karma Fast 9",
            "AP support with shielding. Pairs with Invoker or Spellweaver boards.");
        put("Cassiopeia", "AP Carry",
            new String[]{"Jeweled Gauntlet","Rabadon's Deathcap","Morellonomicon"},
            "AP Cassiopeia",
            "AP damage + grievous wounds. Morellonomicon core for her DoT.");
        put("Diana", "AP Carry",
            new String[]{"Jeweled Gauntlet","Rabadon's Deathcap","Blue Buff"},
            "Diana AP Flex",
            "Burst AP caster. Blue Buff for fast mana cycling.");

        // ---------- Tanks / frontline item holders ----------
        put("Morgana", "AP Tank",
            new String[]{"Warmog's Armor","Dragon's Claw","Jeweled Gauntlet"},
            "Vanguard Morgana Fast 9",
            "Vanguard tank that deals AP damage. Dragon's Claw vs heavy AP boards.");
        put("Malphite", "AP Tank",
            new String[]{"Warmog's Armor","Gargoyle Stoneplate","Rabadon's Deathcap"},
            "Malphite Fast 8",
            "AP tank — Rabadon's amplifies his shockwave. Pairs with Nidalee or Alune.");
        put("Alistar", "Tank",
            new String[]{"Warmog's Armor","Gargoyle Stoneplate","Bramble Vest"},
            "Elderwood / Vanguard frontline",
            "Primary tank in Aphelios and Morgana boards. Slam defensives early.");
        put("Rammus", "Tank",
            new String[]{"Warmog's Armor","Bramble Vest","Gargoyle Stoneplate"},
            "Riftbeast / Adaptor frontline",
            "Top defensive unit in Yi and Pebbles boards. Thorns counters crit.");
        put("Warwick", "Bruiser",
            new String[]{"Warmog's Armor","Bramble Vest","Gargoyle Stoneplate"},
            "Elderwood bruiser",
            "Elderwood heal synergy. Warm entry tank + healing vs AD boards.");
        put("Shen", "Frontline",
            new String[]{"Warmog's Armor","Gargoyle Stoneplate","Redemption"},
            "Vanguard frontline",
            "Vanguard entry tank. Redemption heal peel for nearby carries.");
        put("Hecarim", "Bruiser",
            new String[]{"Warmog's Armor","Gargoyle Stoneplate","Bramble Vest"},
            "Riftbeast bruiser",
            "Fast-moving frontline. Good in Pebbles reroll boards for the Riftbeast count.");
    }

    // ---- unit tier list (patch 18.7) ----
    // S = meta-defining (anchors an S-tier comp or tops the unit win-rate list),
    // A = strong / reliable secondary, B = playable / situational, C = weak.
    // Only units with real evidence are ranked; the rest stay unranked (no badge).
    private static final Map<String,String> TIER = new HashMap<>();
    private static void tier(String champ, String t){ TIER.put(champ, t); }
    static {
        // S — anchors current S-tier comps
        tier("MasterYi","S");     // Adaptor Yi reroll, top comp
        tier("Pebbles","S");      // Riftbeast 1-cost reroll
        tier("Aphelios","S");     // Elderwood AD flex carry
        tier("Morgana","S");      // Vanguard Fast 9 AP tank carry
        // A — strong carries / key support+tank
        tier("Sivir","A");        // Hunter Sivir
        tier("Nidalee","A");      // Lunar Nidalee AP carry
        tier("Alune","A");        // 5-cost AP carry
        tier("Tristana","A");     // Tristana reroll
        tier("Veigar","A");       // Spellweaver AP reroll
        tier("Teemo","A");        // Sprykin AP poison reroll
        tier("Ezreal","A");       // Elderwood flex carry
        tier("Khazix","A");       // Elderwood assassin carry
        tier("Alistar","A");      // top frontline tank
        tier("Rammus","A");       // key tank in Yi / Pebbles boards
        // B — playable
        tier("Malphite","B");     // AP tank / fast 8 line
        tier("Ahri","B");         // Invoker Ahri
        tier("Draven","B");       // 5-cost AD
        tier("Ashe","B");         // 5-cost AD flex
        tier("Warwick","B");      // Elderwood bruiser
        tier("Rengar","B");       // Elderwood assassin
        tier("Cassiopeia","B");   // AP DoT flex
        tier("Diana","B");        // AP burst flex
        tier("Lux","B");          // 5-cost AP niche
        tier("Karma","B");        // AP support niche
    }

    /** Tier letter ("S"/"A"/"B"/"C") for a champion, or "" if unranked this patch. */
    public static String tierOf(String champ){ String t=TIER.get(champ); return t==null?"":t; }

    /** Verified meta build for a champion, or null if it has no entry this patch. */
    public static Build get(String champ){ return M.get(champ); }

    /** True if the champion is a marked meta carry/itemizer this patch. */
    public static boolean has(String champ){ return M.containsKey(champ); }

    private ChampItemData() {}
}
