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
 *  When a new set drops you no longer need to ship an APK: edit
 *  data/setdata.json in the repo and the app pulls it on next launch
 *  (see RemoteData). The values below are the BUNDLED FALLBACK used
 *  offline / before the first successful sync, so keep them current too.
 *
 *  The per-set fields (SET_NAME, PATCH, SIZE, CHAMPS, GODS) are non-final:
 *  RemoteData overwrites them at startup from the cached remote JSON. Treat
 *  them as read-only everywhere else.
 *
 *  CHAMPS layout: index = cost. Index 0 is empty on purpose so
 *  CHAMPS[1] is 1-cost, CHAMPS[2] is 2-cost, etc.
 *  Strip spaces and apostrophes from names (e.g. "Bel'Veth" -> "Belveth",
 *  "Twisted Fate" -> "TwistedFate"). Keep them readable but joined.
 * ============================================================
 */
public final class SetData {

    // Display name of the current set
    public static String SET_NAME = "Set 17 - Space Gods";

    // Patch the bundled/synced data corresponds to (for the staleness banner).
    public static String PATCH = "";

    // Pool sizes by cost: [unused, 1-cost, 2-cost, 3-cost, 4-cost, 5-cost]
    // Set 17 bag sizes: 29 / 22 / 18 / 10 / 9 (smaller 1c/2c bags than the
    // classic 30/25). Verify against the patch notes when a new set drops.
    public static int[] SIZE = {0, 29, 22, 18, 10, 9};

    // Shop odds per level: row = player level (0-10), columns = 1c..5c, percent.
    // Set 17 values; verify rows 7-10 each set, Riot tunes these often.
    public static final int[][] ODDS = {
        {0,0,0,0,0},{100,0,0,0,0},{100,0,0,0,0},{75,25,0,0,0},
        {55,30,15,0,0},{45,33,20,2,0},{30,40,25,5,0},{19,30,40,10,1},
        {18,25,32,22,3},{10,20,25,35,10},{5,10,20,40,25}
    };

    // XP needed to advance FROM each level (index = current level).
    // Buying XP is always 4 gold for 4 XP; passive +2 XP per round.
    // Standard recent-set values — verify per set.
    public static final int[] XP_TO_NEXT = {0, 2, 2, 6, 10, 20, 36, 48, 80, 84, 0};
    public static final int PASSIVE_XP_PER_ROUND = 2;

    // Base player damage on a round loss, by stage (index = stage, stage 7+ uses
    // the last entry). Total damage = base + 1 per surviving enemy unit.
    public static final int[] STAGE_BASE_DMG = {0, 0, 2, 5, 8, 10, 12, 17};

    // Set 17 mechanic: 2 of these 9 gods appear per game in the Realm (replaces
    // carousels at 2-4 / 3-4 / 4-4). Picking one god's offerings 2+ times earns
    // their Boon armory at 4-7 plus recurring loot afterwards.
    public static String[] GODS = {
        "Ahri","AurelionSol","Ekko","Evelynn","Kayle","Soraka","Thresh","Varus","Yasuo"
    };

    // Champions grouped by cost. Index 0 stays empty.
    public static String[][] CHAMPS = {
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
        {"AurelionSol","Corki","Karma","Kindred","Leblanc","MasterYi","Morgana",
         "Nami","Nunu","Rammus","Riven","TahmKench","MightyMech","Xayah"},

        // 5-cost
        {"Bard","Blitzcrank","Fiora","Graves","Jhin","Shen","Sona","Vex"}
    };

    private SetData() {} // no instances
}
