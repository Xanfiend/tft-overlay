package com.xanfiend.tftoverlay;

/*
 * ============================================================
 *  ITEM DATA  --  update when Riot changes the item matrix
 * ============================================================
 *  Components are indexed 1–9 (index 0 unused).
 *  COMBOS[i][j] = full item name; matrix is symmetric.
 *  Spatula (7) combos are set-specific emblems — update per set.
 * ============================================================
 */
public final class ItemData {

    public static final String[] COMPONENTS = {
        "",                 // 0 unused
        "B.F. Sword",       // 1
        "Chain Vest",       // 2
        "Giant's Belt",     // 3
        "Needlessly Large Rod", // 4
        "Recurve Bow",      // 5
        "Sparring Gloves",  // 6
        "Spatula",          // 7
        "Tear of the Goddess", // 8
        "Negatron Cloak",   // 9
        "Frying Pan"        // 10 — second emblem maker alongside Spatula
    };

    // Short display names for the item-builder chips
    public static final String[] COMPONENT_SHORT = {
        "",
        "B.F.",
        "Chain",
        "Belt",
        "Rod",
        "Bow",
        "Gloves",
        "Spat",
        "Tear",
        "Negat.",
        "Pan"
    };

    public static final String[][] COMBOS = new String[11][11];

    static {
        // ---- B.F. Sword (1) ----
        set(1, 1, "Infinity Edge");
        set(1, 2, "Bloodthirster");
        set(1, 3, "Sterak's Gage");
        set(1, 4, "Deathblade");
        set(1, 5, "Kraken Slayer");
        set(1, 6, "Hextech Gunblade");
        set(1, 7, "B.F. Emblem");         // set-specific — update each set
        set(1, 8, "Spear of Shojin");
        set(1, 9, "Edge of Night");

        // ---- Chain Vest (2) ----
        set(2, 2, "Bramble Vest");
        set(2, 3, "Locket of the Iron Solari");
        set(2, 4, "Adaptive Helm");
        set(2, 5, "Quicksilver");
        set(2, 6, "Shroud of Stillness");
        set(2, 7, "Chain Emblem");        // set-specific — update each set
        set(2, 8, "Frozen Heart");
        set(2, 9, "Gargoyle Stoneplate");

        // ---- Giant's Belt (3) ----
        set(3, 3, "Warmog's Armor");
        set(3, 4, "Morellonomicon");
        set(3, 5, "Titan's Resolve");
        set(3, 6, "Crownguard");
        set(3, 7, "Belt Emblem");         // set-specific — update each set
        set(3, 8, "Zeke's Herald");
        set(3, 9, "Sunfire Cape");

        // ---- Needlessly Large Rod (4) ----
        set(4, 4, "Rabadon's Deathcap");
        set(4, 5, "Giant Slayer");
        set(4, 6, "Jeweled Lotus");
        set(4, 7, "Rod Emblem");          // set-specific — update each set
        set(4, 8, "Archangel's Staff");
        set(4, 9, "Ionic Spark");

        // ---- Recurve Bow (5) ----
        set(5, 5, "Guinsoo's Rageblade");
        set(5, 6, "Last Whisper");        // verify per set
        set(5, 7, "Bow Emblem");          // set-specific — update each set
        set(5, 8, "Statikk Shiv");        // verify per set
        set(5, 9, "Runaan's Hurricane");

        // ---- Sparring Gloves (6) ----
        set(6, 6, "Jeweled Gauntlet");
        set(6, 7, "Gloves Emblem");       // set-specific — update each set
        set(6, 8, "Thief's Gloves");
        set(6, 9, "Hand of Justice");

        // ---- Spatula (7) ----
        set(7, 7, "Tactician's Crown");
        set(7, 8, "Tear Emblem");         // set-specific — update each set
        set(7, 9, "Negat. Emblem");       // set-specific — update each set

        // ---- Tear of the Goddess (8) ----
        set(8, 8, "Blue Buff");
        set(8, 9, "Chalice of Power");

        // ---- Negatron Cloak (9) ----
        set(9, 9, "Dragon's Claw");

        // ---- Frying Pan (10) — emblem maker, like Spatula ----
        // Set-specific emblem outcomes; generic names until verified per set.
        set(10, 1,  "B.F. Pan Emblem");
        set(10, 2,  "Chain Pan Emblem");
        set(10, 3,  "Belt Pan Emblem");
        set(10, 4,  "Rod Pan Emblem");
        set(10, 5,  "Bow Pan Emblem");
        set(10, 6,  "Gloves Pan Emblem");
        set(10, 7,  "Tactician's Toolkit");
        set(10, 8,  "Tear Pan Emblem");
        set(10, 9,  "Negat. Pan Emblem");
        set(10, 10, "Tactician's Wok");
    }

    private static void set(int i, int j, String name) {
        COMBOS[i][j] = name;
        COMBOS[j][i] = name;
    }

    private ItemData() {}
}
