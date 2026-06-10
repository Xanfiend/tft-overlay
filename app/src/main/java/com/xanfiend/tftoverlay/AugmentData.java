package com.xanfiend.tftoverlay;

/*
 * ============================================================
 *  AUGMENT REFERENCE  --  UPDATE THIS FILE EACH SET / PATCH
 * ============================================================
 *
 *  Augments and the meta change every patch and are fully
 *  replaced every set. When a new set/patch lands:
 *    1. Update COMP_PRIORITIES with the current meta comps and
 *       what augment TYPES each wants (from a tier list you trust).
 *    2. Update EXCLUSIONS with any "pick X, lose Y" rules
 *       (Riot patch notes are the source of truth for these).
 *    3. MECHANICS rarely changes (armory timing, free rerolls).
 *
 *  Current data: Set 17 "Space Gods", patch 17.5 (verified vs
 *  Riot 17.5 patch notes: big augment pass — econ augments
 *  nerfed across the board, combat augments buffed).
 *  Augment rerolls are FREE (no gold cost).
 * ============================================================
 */
public final class AugmentData {

    public static final String SET_LABEL = "Set 17 \u00b7 patch 17.5";

    // ---- Per-augment tier list ----
    // Each entry: display name, tier (S/A/B/C), comma-separated comp tags ("" = universal).
    // Update every patch. Tiers are editorial; verify against current tier lists.
    public static final class AugmentEntry {
        public final String name;
        public final String tier;
        public final String comps;
        public AugmentEntry(String name, String tier, String comps) {
            this.name = name; this.tier = tier; this.comps = comps;
        }
    }

    public static final AugmentEntry[] AUGMENTS = {
        // --- Prismatic ---
        new AugmentEntry("Binary Airdrop",        "S", "Any comp"),
        new AugmentEntry("Radiant Relics",         "S", "Any itemized carry"),
        new AugmentEntry("Cybernetic Uplink 3",    "S", "Vex Fast 9, Vanguard Fast 9"),
        new AugmentEntry("Celestial Blessing 3",   "S", "Any comp"),
        new AugmentEntry("Pandora's Items 3",      "A", "Any flexible comp"),
        new AugmentEntry("Buried Treasures 3",     "S", "Reroll comps (17.5: back to 5 rounds)"),
        new AugmentEntry("Spoils of War 3",        "B", ""),
        // --- Gold ---
        new AugmentEntry("Patience",               "S", "Brawler Yi, Reroll"),
        new AugmentEntry("Double Trouble 1",       "A", "Stargazer Xayah (17.5: Serpent bugfix nerf)"),
        new AugmentEntry("Celestial Blessing 2",   "A", "Any comp"),
        new AugmentEntry("Combat Caster",          "A", "Vex Fast 9 (17.5: Vex rebuffed)"),
        new AugmentEntry("Luden's Tempest",        "A", "Vex Fast 9, Viktor"),
        new AugmentEntry("On a Roll",              "A", "Reroll comps"),
        new AugmentEntry("Spellweaver",            "A", "Vex Fast 9, Shepherd Viktor"),
        new AugmentEntry("Heart of the Swarm",     "A", "Rek'Sai comps (17.5: now 2-star Rek'Sai + Briar)"),
        new AugmentEntry("Climb the Ladder",       "A", "Vertical trait comps (17.5: buffed)"),
        new AugmentEntry("Early Learnings",        "A", "Reroll comps (17.5: buffed back to normal)"),
        new AugmentEntry("Giant Slayer",           "B", "Dark Stars, Corki Riven"),
        new AugmentEntry("Arbiter Emblem",          "A", "Arbiter comps"),
        new AugmentEntry("Risky Moves",            "B", "(17.5: econ nerf)"),
        new AugmentEntry("Save This Account",      "B", "(17.5: nerfed, no more free level 9)"),
        new AugmentEntry("Upward Mobility",        "B", "(17.5: lost free rerolls)"),
        new AugmentEntry("Slam and Plus",          "B", "(17.5: trimmed at 3-2)"),
        new AugmentEntry("Rogue Emblem",           "B", ""),
        new AugmentEntry("Saving Grace",           "B", ""),
        new AugmentEntry("Gold Collector",         "B", ""),
        new AugmentEntry("Makeshift Armor 2",      "B", ""),
        // --- Silver ---
        new AugmentEntry("Tiny Power",             "A", "Reroll comps"),
        new AugmentEntry("Academy",                "A", "Vex Fast 9, Vanguard Fast 9"),
        new AugmentEntry("Little Buddies",         "B", "(17.5: buffed)"),
        new AugmentEntry("Earth",                  "B", "Spatula comps (17.5: stat buff)"),
        new AugmentEntry("Hustler",                "A", "Any econ-dependent comp"),
        new AugmentEntry("Component Grab Bag",     "B", ""),
        new AugmentEntry("Lucky Gloves",           "B", ""),
        new AugmentEntry("Meditation",             "B", ""),
        new AugmentEntry("Pandora's Items 1",      "B", ""),
        new AugmentEntry("Stand Behind Me",        "C", ""),
        new AugmentEntry("Health Plus",            "C", ""),
        new AugmentEntry("Makeshift Armor 1",      "C", ""),
    };

    // Timeless-ish mechanics. Rarely changes between sets.
    public static final String[] MECHANICS = {
        "Armories at 2-1, 3-2, 4-2 \u00b7 3 choices each",
        "Rerolls are FREE \u00b7 1 reroll per slot",
        "Tiers: Silver < Gold < Prismatic",
        "Can't be offered 3 econ augments at once",
        "Some augments shift your NEXT armory tier up/down"
    };

    // Comp priority profiles: for the current meta, what augment
    // TYPES to favour. Principle-based so it stays useful even as
    // specific augments shift. Each entry: comp name, then priority.
    public static final String[][] COMP_PRIORITIES = {
        // {comp, augment priority order} — updated for 17.5
        {"Vex Fast 9 (S)", "17.5: Vex spell damage rebuffed. Econ > Combat > Item. Need econ to hit 9."},
        {"Vanguard Fast 9 (A)", "Still strong, but 17.5 nerfed the econ augs that carried fast 9. Econ > Combat."},
        {"Stargazer Xayah (B)", "17.5: Serpent poison bugfix nerf (20/40/60 to 18/35/50) + Mountain trims. Worse payoff."},
        {"Space Groove / Ornn (B)", "17.5: Ornn nerfed at 3/5/7 Groove, regen 0.95 to 0.85. No longer unkillable."},
        {"Meeple Rammus (B)", "17.5: 5-Meeple innate bonuses nerfed; champions buffed instead. 7-Meeple now viable."},
        {"Dark Stars / Jhin (A)", "Combat + Dark Star scaling. Heavily contested when lobby knows it."},
        {"Bel'Veth Reroll (A)", "Reroll econ + Patience. Slow-roll L6/L7."},
        {"Samira / Lissandra Reroll (A)", "Reroll econ + Buried Treasures (rebuffed). Stay under-levelled."},
        {"Corki Riven (B)", "Still playable post-17.4 nerf. Combat > Item."},
        {"Brawler / NOVA Yi (B)", "Reroll econ (Patience, On a Roll) > Combat. Slow-roll L7/L8."},
        {"Bard / Veigar / Fizz comps", "17.5: meaningful buffs to Bard, Veigar, Fizz, Caitlyn, Mordekaiser. Watch the meta."},
        {"7 Shepherd / Shepherd Viktor (B)", "Emblem augs for direction > Combat. AP scaling."}
    };

    // Known exclusion / interaction rules (Riot patch notes verified).
    // Add to these as more are confirmed each patch.
    public static final String[] EXCLUSIONS = {
        "Hero Augment taken \u2192 board-replace augs (Cosmic Restart, Restart Mission) won't appear",
        "Makeshift Armor 1 & 2 are mutually exclusive",
        "Lucky Gloves & Invader Zed are mutually exclusive",
        "Golden Gamble versions exclude each other",
        "\"Tier higher/lower\" augs change your NEXT armory's tier"
    };

    // General principle when nothing else applies.
    public static final String FALLBACK =
        "No clear comp yet? Take the strongest econ or a flexible combat/item aug. "
      + "Emblem augments give direction. Don't force. Play what your augs + Stargazer + open pool allow.";

    private AugmentData() {}
}
