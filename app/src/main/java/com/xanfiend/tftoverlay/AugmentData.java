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
 *  Current data: Set 17 "Space Gods", patch 17.4 (verified vs
 *  Riot patch notes + BunnyMuffins/TFT Academy 17.4 tier lists).
 *  Augment rerolls are FREE (no gold cost).
 * ============================================================
 */
public final class AugmentData {

    public static final String SET_LABEL = "Set 17 \u00b7 patch 17.4";

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
        // {comp, augment priority order}
        {"Vex Fast 9 (S)", "Econ > Combat > Item. Need econ to hit 9, 1 combat aug to win out."},
        {"Vanguard Fast 9 (S)", "Econ > Combat. Tempo + econ augs; frontline holds while you scale."},
        {"Stargazer Xayah (A)", "Double Trouble first, then Combat/Item. Check constellation."},
        {"Corki Riven (A)", "Combat > Item > Econ. Strong stage 4, good loss-streak contest."},
        {"Dark Stars / Jhin (A)", "Combat + Dark Star scaling. Heavily contested when lobby knows it."},
        {"Brawler / NOVA Yi (B)", "Reroll econ (Patience, On a Roll) > Combat. Slow-roll L7/L8."},
        {"Reroll comps (Lulu, MF, Samira)", "Reroll econ + free-roll augs > Combat. Stay under-levelled."},
        {"Anima / Mecha reroll", "Loss-streak econ (Anima cashout) > Combat. Needs uncontested Viktor."},
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
