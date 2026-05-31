package com.xanfiend.tftoverlay;

/*
 * ============================================================
 *  TRAIT DATA  --  THIS FILE CHANGES EVERY SET
 * ============================================================
 *  Format: {trait name, "N / N / N" breakpoints, short effect}
 *  Verify breakpoints and effects against Riot patch notes.
 * ============================================================
 */
public final class TraitData {

    // Set 17 "Space Gods" — verify all breakpoints per patch notes
    public static final String[][] TRAITS = {
        // Origin traits
        {"Dark Star",    "3 / 6 / 9",     "Gain AP/AD on ally death"},
        {"Stargazer",    "3 / 6 / 9",     "Econ & item bonus from constellations"},
        {"Anima Squad",  "3 / 5 / 7 / 9", "Hearts & shields on kills"},
        {"Mecha",        "3 / 5 / 7",     "Merges into giant mech unit"},
        {"NOVA",         "2 / 4",         "Bonus damage from high HP"},
        // Class traits
        {"Brawler",      "2 / 4 / 6 / 8", "Max HP bonus"},
        {"Vanguard",     "2 / 4 / 6",     "Armor for all"},
        {"Shepherd",     "2 / 4 / 6",     "AP per adjacent unit"},
        {"Slayer",       "2 / 4 / 6 / 8", "+Dmg & lifesteal at low HP"},
        {"Marksman",     "2 / 4 / 6",     "Attacks fire extra bolts"},
        {"Sorcerer",     "2 / 4 / 6",     "AP bonus for all"},
        {"Bastion",      "2 / 4 / 6 / 8", "Armor & MR stacking"},
        {"Duelist",      "2 / 4 / 6 / 8", "Attack speed on attack"},
        {"Invoker",      "2 / 4",         "Mana on ally cast"},
        {"Reaper",       "2 / 4",         "Execute threshold"},
        // Add / verify remaining Set 17 traits
    };

    private TraitData() {}
}
