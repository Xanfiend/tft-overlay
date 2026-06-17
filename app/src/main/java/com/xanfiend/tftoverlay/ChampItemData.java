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
 *  Data verified against the patch 17.5b meta snapshot
 *  (tactics.tools / mobalytics / metatft, June 2026).
 * ============================================================
 */
public final class ChampItemData {

    // Live patch this build data reflects. Shown in the BUILDS tab.
    public static final String PATCH = "17.5b";

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
        put("Corki", "AD Carry",
            new String[]{"Deathblade","Blue Buff","Last Whisper"},
            "Meeple Fateweavers",
            "Slam swords early; level 8 on 4-2 and roll for Corki + Riven.");
        put("Kaisa", "AD / On-hit Carry",
            new String[]{"Infinity Edge","Striker's Flail","Spear of Shojin"},
            "Shepherd / Rogues Kai'Sa",
            "Red Buff and Blue Buff are strong flex slots if IE is slow.");
        put("Jhin", "AD Carry",
            new String[]{"Infinity Edge","Last Whisper","Giant Slayer"},
            "Dark Star Sniper",
            "Highest-cap 5-cost — slot him the moment you hit, Deathblade also great.");
        put("Samira", "AD Reroll Carry",
            new String[]{"Deathblade","Infinity Edge","Spear of Shojin"},
            "Samira + Ornn Reroll",
            "Two-tank reroll: pair with Ornn, slow-roll level 7 for 3-star.");
        put("Xayah", "AD Carry",
            new String[]{"Kraken Slayer","Guinsoo's Rageblade","Deathblade"},
            "Stargazer Xayah",
            "Red Buff over Deathblade if you need the burn/anti-heal.");
        put("Riven", "AD Bruiser Carry",
            new String[]{"Bloodthirster","Striker's Flail","Edge of Night"},
            "Meeple Fateweavers",
            "Secondary carry — takes leftover AD behind Corki.");
        put("MissFortune", "AP/AD Reroll Carry",
            new String[]{"Blue Buff","Jeweled Gauntlet","Rabadon's Deathcap"},
            "Miss Fortune Reroll",
            "B-tier this patch — strong board but out-capped by the top comps.");

        // ---------- AP carries ----------
        put("AurelionSol", "AP Carry",
            new String[]{"Jeweled Gauntlet","Rabadon's Deathcap","Striker's Flail"},
            "Dark Star A.Sol",
            "Non-Mech wants Searing Shortbow for mana; crit + amp is the core.");
        put("Karma", "AP Carry",
            new String[]{"Jeweled Gauntlet","Rabadon's Deathcap","Spear of Shojin"},
            "Dark Star (Kai'Sa duo)",
            "Adaptive Helm is the top utility slot if you need to flex AP backline.");
        put("Vex", "AP Carry",
            new String[]{"Guinsoo's Rageblade","Jeweled Gauntlet","Striker's Flail"},
            "Shepherd Vex Fast 9",
            "Void Staff over Striker's into heavy armor stacking.");

        // ---------- Tanks / frontline item holders ----------
        put("Rammus", "Tank",
            new String[]{"Warmog's Armor","Bramble Vest","Gargoyle Stoneplate"},
            "Meeple Fateweavers",
            "Primary tank — slam defensives early alongside Corki.");
        put("TahmKench", "Tank",
            new String[]{"Warmog's Armor","Gargoyle Stoneplate","Dragon's Claw"},
            "Dark Star frontline",
            "Dragon's Claw vs heavy AP, Bramble vs crit/AD boards.");
    }

    // ---- unit tier list (patch 17.5b) ----
    // S = meta-defining (anchors an S-tier comp or tops the unit win-rate list),
    // A = strong / reliable secondary, B = playable / situational, C = weak.
    // Only units with real evidence are ranked; the rest stay unranked (no badge).
    private static final Map<String,String> TIER = new HashMap<>();
    private static void tier(String champ, String t){ TIER.put(champ, t); }
    static {
        // S — carries of the current S-tier comps + top-stat 5-costs
        tier("Corki","S");        // Meeple Fateweavers
        tier("Samira","S");       // Space Groove Samira reroll (75% top4)
        tier("Xayah","S");        // Stargazer Xayah
        tier("Vex","S");          // Vanguard / Shepherd Vex
        tier("AurelionSol","S");  // Dark Star A.Sol
        tier("Jhin","S");         // highest-cap 5-cost
        tier("Shen","S");         // top-stat 5-cost frontline
        tier("Bard","S");         // top-stat 5-cost
        tier("Blitzcrank","S");   // top-stat 5-cost
        // A — strong carries / key support+tank of S comps
        tier("Kaisa","A");        // Shepherd / Rogues Kai'Sa
        tier("Karma","A");        // Dark Star duo carry
        tier("Riven","A");        // Fateweavers secondary carry
        tier("Rammus","A");       // Fateweavers primary tank
        tier("Sona","A");         // key support 5-cost in multiple S comps
        tier("Ornn","A");         // Samira reroll second tank
        // B — playable but out-capped
        tier("MissFortune","B");  // MF reroll, B-tier this patch
        tier("TahmKench","B");    // Dark Star frontline filler
    }

    /** Tier letter ("S"/"A"/"B"/"C") for a champion, or "" if unranked this patch. */
    public static String tierOf(String champ){ String t=TIER.get(champ); return t==null?"":t; }

    /** Verified meta build for a champion, or null if it has no entry this patch. */
    public static Build get(String champ){ return M.get(champ); }

    /** True if the champion is a marked meta carry/itemizer this patch. */
    public static boolean has(String champ){ return M.containsKey(champ); }

    private ChampItemData() {}
}
