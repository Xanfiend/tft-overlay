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

    // Set 17 "Space Gods" — verified vs Riot patch notes 17.5
    // (17.5 changed trait stat numbers only — Arbiter, Meeple, Space
    //  Groove nerfs — breakpoints below are unchanged)
    public static final String[][] TRAITS = {
        // Origins
        {"Dark Star",    "2 / 4 / 6 / 9", "Black holes execute <10% HP; units gain AP/AD"},
        {"Stargazer",    "3 / 6 / 9",     "Econ & item bonus from constellations"},
        {"Anima",        "3 / 6 / 9",     "Loss-streak Tech; prototypes Anima Weapons at 100"},
        {"Space Groove", "3 / 5 / 7",     "Stacking AS & HP regen; stronger the longer fights last"},
        {"Primordian",   "1 / 3",         "Spawn Swarmlings on damage; drops 1/2-cost champ each round"},
        {"Arbiter",      "2 / 3",         "Choose cause + effect for a divine law; stronger at 3"},
        {"Mecha",        "2 / 4",         "Two units merge into giant mech with upgraded abilities"},
        {"NOVA",         "2 / 4",         "Bonus damage scaling from high HP"},
        // Classes
        {"Brawler",      "2 / 4 / 6 / 8", "Max HP bonus for whole team; Brawlers gain more"},
        {"Vanguard",     "2 / 4 / 6",     "Shield at combat start and at 50% HP; Durability while shielded"},
        {"Shepherd",     "2 / 4 / 6",     "Summons Bond of the Stars; power scales with Shepherd star level"},
        {"Slayer",       "2 / 4 / 6 / 8", "+Dmg & lifesteal at low HP"},
        {"Bastion",      "2 / 4 / 6 / 8", "Team Armor & MR; doubles in first 10 s of combat"},
        {"Psionic",      "2 / 4",         "Generate Psi-mods for any ally; at 4 Psionic units get bonus effects"},
        {"Sniper",       "2 / 3 / 4 / 5", "Damage amp vs targets farther away"},
        {"Challenger",   "2 / 4 / 6",     "Bonus AS; dash to new target on kill, +50% AS for 2.5 s"},
        {"Eradicator",   "2 / 4",         "Enemies have reduced Armor & MR"},
    };

    private TraitData() {}
}
