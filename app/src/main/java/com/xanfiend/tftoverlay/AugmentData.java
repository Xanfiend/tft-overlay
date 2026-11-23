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
 *  Current data: Set 18 "Enchanted Wilds", patch 18.7 (Nov 2026).
 *  Sources: OP.GG, Blitz.gg, PivotTFT, SeeMeta, MetaTFT.
 *  Augment rerolls are FREE (no gold cost).
 * ============================================================
 */
public final class AugmentData {

    public static final String SET_LABEL = "Set 18 · patch 18.7";

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
        new AugmentEntry("Baron's Lair",          "S", "Any comp"),
        new AugmentEntry("Spreading Roots",        "S", "Elderwood Aphelios / Khazix"),
        new AugmentEntry("Beast Within",           "S", "Riftbeast Pebbles reroll"),
        new AugmentEntry("Epic Rolldown",          "S", "Any reroll comp"),
        new AugmentEntry("Booster Pack+",          "S", "Any comp"),
        new AugmentEntry("Call To Chaos",          "A", "Any comp"),
        new AugmentEntry("Pandora's Items 3",      "A", "Any flexible comp"),
        new AugmentEntry("Sun and Moon",           "A", "Lunar Nidalee"),
        // --- Gold ---
        new AugmentEntry("Big Grab Bag",           "S", "Any comp — 3 random components"),
        new AugmentEntry("Explosive Growth",       "S", "Reroll comps, Elderwood"),
        new AugmentEntry("Item Grab Bag I",        "S", "Any itemized carry"),
        new AugmentEntry("Swordsmith",             "S", "Any AD or flex carry"),
        new AugmentEntry("Bronze For Life II",     "A", "Best at stage 3-2"),
        new AugmentEntry("Component Quest",        "A", "Any carry — complete for Prismatic item"),
        new AugmentEntry("Branching Out",          "A", "Elderwood Aphelios, Khazix"),
        new AugmentEntry("Seraphim's Staff",       "A", "AP comps"),
        new AugmentEntry("Focused Fire",           "A", "Hunter Sivir, Aphelios"),
        new AugmentEntry("Gilded Steel",           "A", "AD comps"),
        new AugmentEntry("Trade Sector",           "A", "Reroll comps (early econ)"),
        new AugmentEntry("Small Furry Friend",     "A", "Pebbles reroll, Sprykin Teemo"),
        new AugmentEntry("Portable Forge",         "A", "Any itemized carry"),
        new AugmentEntry("Pandora's Items 2",      "B", ""),
        new AugmentEntry("Clockwork Accelerator",  "B", "Sprykin comps"),
        new AugmentEntry("Frontline Foundation",   "B", "Tank-heavy boards"),
        new AugmentEntry("Exclusive Customization","B", ""),
        // --- Silver ---
        new AugmentEntry("Pandora's Items 1",      "B", ""),
        new AugmentEntry("Component Grab Bag",     "B", ""),
        new AugmentEntry("Lucky Gloves",           "B", ""),
        new AugmentEntry("Meditation",             "B", ""),
        new AugmentEntry("Heroic Grab Bag",        "C", ""),
    };

    // Timeless mechanics. Verify per set.
    public static final String[] MECHANICS = {
        "Armories at 2-1, 3-2, 4-2 · 3 choices each",
        "Rerolls are FREE · 1 reroll per slot",
        "Tiers: Silver < Gold < Prismatic",
        "Can't be offered 3 econ augments at once",
        "Some augments shift your NEXT armory tier up/down",
    };

    // Comp priority profiles for Set 18 patch 18.7.
    // Each entry: comp name, then augment priority guidance.
    public static final String[][] COMP_PRIORITIES = {
        {"Adaptor Master Yi Reroll (S)", "Econ first (Spreading Roots, Epic Rolldown). After 3-star Yi, slot AD Combat. Slow-roll 7-8."},
        {"Riftbeast Pebbles Reroll (S)", "1-cost reroll. Slow-roll level 7. Beast Within is S-tier here. Econ > Combat."},
        {"Elderwood Aphelios (S)", "AD Fast 8. Elderwood trait scaling. Branching Out + Spreading Roots top picks. AD Combat > Econ."},
        {"Vanguard Morgana Fast 9 (A)", "AP carry + Vanguard tanks. Fast 9. Econ > AP Combat. Baron's Lair strong."},
        {"Hunter Sivir (A)", "AD carry Fast 8. Hunter trait. Focused Fire + Component Quest top picks."},
        {"Lunar Nidalee (A)", "AP carry. Lunar trait. AP Combat (JG, Rabadon's) > Econ. Sun and Moon is trait-specific S-tier."},
        {"Spellweaver Veigar Reroll (A)", "1-cost AP reroll. Level 6-7. Spellweaver vertical. AP Combat > econ."},
        {"Sprykin Teemo Reroll (A)", "2-cost AP reroll. Sprykin trait. Small Furry Friend is trait-specific. AP Combat > econ."},
        {"Elderwood Kha'Zix / Ezreal (A)", "AD assassin or flex ADC. Branching Out + Elderwood. Fast 8. Pivot to Ezreal if Khazix gone."},
    };

    // Known exclusion / interaction rules.
    public static final String[] EXCLUSIONS = {
        "Pandora's Items tiers 1 and 2 can both appear in the same game",
        "Big Grab Bag and Item Grab Bag share a category — usually see one per game",
        "\"Tier higher/lower\" augs change your NEXT armory's tier",
        "Trait-specific augs (Beast Within, Spreading Roots, Sun and Moon) need 4+ of that trait to pay off",
    };

    // General principle when nothing else applies.
    public static final String FALLBACK =
        "No clear comp yet? Take the strongest econ, a component grab bag, or a flexible combat aug. "
      + "Emblem augments give direction. Don't force. Play what your augs + current board + open pool allow.";

    private AugmentData() {}
}
