package com.xanfiend.tftoverlay;

/*
 * ============================================================
 *  TFT SET DATA  --  THIS IS THE ONLY FILE TO EDIT EACH SET
 * ============================================================
 *
 *  When a new set drops:
 *    1. Change SET_NAME to the new set.
 *    2. Replace the champion names in CHAMPS (grouped by cost).
 *    3. Only touch SIZE if Riot changes pool sizes (rare).
 *    4. Save, push. The build makes a fresh APK automatically.
 *
 *  Do NOT edit Pool.java, OverlayService.java, or anything else
 *  for a normal set update -- everything reads from here.
 *
 *  CHAMPS layout: index = cost. Index 0 is empty on purpose so
 *  CHAMPS[1] is 1-cost, CHAMPS[2] is 2-cost, etc.
 *  Strip spaces and apostrophes from names (e.g. "Bel'Veth" -> "Belveth",
 *  "Twisted Fate" -> "TwistedFate"). Keep them readable but joined.
 * ============================================================
 */
public final class SetData {

    // Display name of the current set
    public static final String SET_NAME = "Set 17 - Space Gods";

    // Pool sizes by cost: [unused, 1-cost, 2-cost, 3-cost, 4-cost, 5-cost]
    // These have been stable across many sets. Only change if Riot does.
    public static final int[] SIZE = {0, 30, 25, 18, 10, 9};

    // Champions grouped by cost. Index 0 stays empty.
    public static final String[][] CHAMPS = {
        {}, // index 0 unused

        // 1-cost
        {"Aatrox","Briar","Caitlyn","Chogath","Ezreal","Leona","Lissandra",
         "Nasus","Poppy","RekSai","Talon","Teemo","TwistedFate","Veigar"},

        // 2-cost
        {"Akali","Belveth","Gnar","Gragas","Gwen","Jax","Jinx",
         "Meepsie","Milio","Mordekaiser","Pantheon","Pyke","Zoe"},

        // 3-cost
        {"Aurora","Diana","Fizz","Illaoi","Kaisa","Lulu","Maokai",
         "MissFortune","Ornn","Rhaast","Samira","Urgot","Viktor"},

        // 4-cost
        {"AurelionSol","Corki","Karma","Kindred","Leblanc","MasterYi","Nami",
         "Nunu","Rammus","Riven","TahmKench","MightyMech","Xayah"},

        // 5-cost
        {"Bard","Blitzcrank","Fiora","Graves","Jhin","Morgana","Shen","Sona","Vex"}
    };

    private SetData() {} // no instances
}
