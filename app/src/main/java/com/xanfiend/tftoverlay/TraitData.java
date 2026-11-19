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

    // Set 18 "Enchanted Wilds" — breakpoints from the game data (CommunityDragon).
    public static final String[][] TRAITS = {
        // Origins
        {"Apex Predator",  "1", "Elder Dragon takes 2 slots and grants +2 Riftbeast"},
        {"Attuned",        "1", "Alune cycles moon phases; team gains Durability"},
        {"Avatar",         "1", "Lux takes on the trait of the Avatar you own"},
        {"Blackthorn",     "2 / 4 / 6", "Sacrifice the ally on the Blackthorn hex for team Health"},
        {"Blossom",        "3 / 5 / 7 / 9 / 11", "Wisps empower after combat: AD, AP and max Health"},
        {"Bounty Seeker",  "1", "Draven completes bounties for rewards"},
        {"Caustic",        "1", "Damage Shreds and Sunders enemies"},
        {"Coven",          "3 / 4 / 5 / 7", "Gather Essence from kills and losses; cash out for rewards"},
        {"Elderwood",      "3 / 5 / 7 / 9 / 11", "Placeable Elderwood plants that empower your board"},
        {"Emerald Aspect", "1", "Pair an ally with Taric for his ability bonuses"},
        {"Fae",            "2 / 4", "Pixies from damage and healing grant Fae AD and AP"},
        {"Flora Fatalis",  "1 / 2", "Takedowns grant Mana and heal the lowest ally"},
        {"Greenfather",    "1", "Ivern grows bonus hexes from seeds"},
        {"Inferno",        "2 / 3 / 5 / 7", "Damage Burns and Wounds enemies"},
        {"Lunar",          "2 / 3 / 4 / 5", "AS and AP for Lunar units and adjacent allies"},
        {"Monolith",       "1", "Armor and MR for each enemy targeting it"},
        {"Old Growth",     "1", "Permanent max Health from nearby enemy deaths"},
        {"Primal",         "2 / 4", "Choose one of four Primal Blessings"},
        {"Riftbeast",      "3 / 5 / 7 / 10", "Alpha Mark buffs; shops get overrun with Riftbeasts"},
        {"Rival",          "1 / 2", "Rivals collect takedowns; only one can be active"},
        {"Solar",          "3", "Max-Health shield and bonus magic damage; scales with 3-stars"},
        {"Sprykin",        "3 / 5 / 7", "The Big Furry Friend carries a Sprykin rider"},
        {"Thornmaiden",    "1", "Team Durability, more with Zyra plants alive"},
        // Classes
        {"Adaptor",        "2 / 3 / 4", "Gain AD or AP, whichever is higher; abilities adapt"},
        {"Brawler",        "2 / 4 / 6", "Max Health for the team; Brawlers gain more"},
        {"Defender",       "2 / 4 / 6", "Armor and MR for the team; Defenders gain more"},
        {"Executioner",    "2 / 3 / 4", "Crit chance; enemies bleed true damage at higher tiers"},
        {"Hunter",         "2 / 3 / 4 / 5", "AD; Damage Amp when sticking to one target"},
        {"Invoker",        "2 / 3 / 4 / 5", "Team Mana Regen; Invokers gain more"},
        {"Juggernaut",     "2 / 4 / 6", "Team Durability; Juggernauts gain more"},
        {"Rapidfire",      "2 / 3 / 4 / 5", "Team AS; Rapidfire units stack more per attack"},
        {"Ravager",        "2 / 4 / 6", "Omnivamp and bonus damage vs low-Health enemies"},
        {"Spellweaver",    "2 / 4 / 6", "Team AP; Spellweavers gain more per cast"},
        {"Summoner",       "2 / 3", "Empowers each Summoner's summons"},
        {"Vanguard",       "2 / 4 / 6", "Max-Health shield at combat start and below half Health"},
    };

    private TraitData() {}
}
