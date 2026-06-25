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
 *  Current data: Set 17 "Space Gods", patch 17.6 (verified vs
 *  Riot 17.5 + 17.6 patch notes: econ aug nerfs continued,
 *  combat/AP augments buffed; several new viable carry lines
 *  emerged: TF, Viktor, LeBlanc, Bard all meaningfully buffed).
 *  Augment rerolls are FREE (no gold cost).
 *  NOTE: Critical Success disabled in 17.6 — will not appear.
 * ============================================================
 */
public final class AugmentData {

    public static final String SET_LABEL = "Set 17 · patch 17.6";

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
        new AugmentEntry("Buried Treasures 3",     "S", "Reroll comps (17.5: back to 5 rounds)"),
        new AugmentEntry("Pandora's Items 3",      "A", "Any flexible comp"),
        new AugmentEntry("Spoils of War 3",        "B", ""),
        // --- Gold ---
        new AugmentEntry("Patience",               "S", "Reroll comps"),
        new AugmentEntry("Heart of the Swarm",     "A", "Rek'Sai comps (17.5: now 2* RekSai + Briar)"),
        new AugmentEntry("Climb the Ladder",       "A", "Vertical trait comps (17.5: 6% => 7%)"),
        new AugmentEntry("Early Learnings",        "A", "Reroll comps (17.5: 1% => 8% initial)"),
        new AugmentEntry("Blood Offering",         "A", "AP comps (17.6: now AP+AD, HP loss 20% => 15%)"),
        new AugmentEntry("Best Friends",           "A", "Any comp (17.6: now Armor + MR, not Armor only)"),
        new AugmentEntry("Double Trouble 1",       "A", "Stargazer Xayah"),
        new AugmentEntry("Celestial Blessing 2",   "A", "Any comp"),
        new AugmentEntry("Combat Caster",          "A", "Vex Fast 9, AP comps"),
        new AugmentEntry("Luden's Tempest",        "A", "Vex Fast 9, TF, Viktor"),
        new AugmentEntry("On a Roll",              "A", "Reroll comps"),
        new AugmentEntry("Spellweaver",            "A", "Vex Fast 9, Shepherd"),
        new AugmentEntry("Arbiter Emblem",          "A", "Arbiter comps"),
        new AugmentEntry("Loot Singularity",       "A", "Dark Star comps (17.6: earlier components, better cashout)"),
        new AugmentEntry("Giant Slayer",           "B", "Dark Stars, Corki"),
        new AugmentEntry("Risky Moves",            "B", "(17.5: econ nerf, Gold 30 => 26)"),
        new AugmentEntry("Save This Account",      "B", "(17.5: nerfed, no more free level 9)"),
        new AugmentEntry("Upward Mobility",        "B", "(17.5: lost free rerolls)"),
        new AugmentEntry("Rogue Emblem",           "B", ""),
        new AugmentEntry("Saving Grace",           "B", ""),
        new AugmentEntry("Gold Collector",         "B", ""),
        new AugmentEntry("Makeshift Armor 2",      "B", ""),
        new AugmentEntry("Expedition",             "C", "(17.6: nerfed, Gold 20 => 15)"),
        // --- Silver ---
        new AugmentEntry("Tiny Power",             "A", "Reroll comps"),
        new AugmentEntry("Academy",                "A", "Vex Fast 9, Vanguard Fast 9"),
        new AugmentEntry("Hustler",                "A", "Any econ-dependent comp"),
        new AugmentEntry("Little Buddies",         "B", "(17.5: HP 55 => 65, AS 6% => 7%)"),
        new AugmentEntry("Earth",                  "B", "Spatula comps"),
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
        "Armories at 2-1, 3-2, 4-2 · 3 choices each",
        "Rerolls are FREE · 1 reroll per slot",
        "Tiers: Silver < Gold < Prismatic",
        "Can't be offered 3 econ augments at once",
        "Some augments shift your NEXT armory tier up/down",
        "Critical Success DISABLED in 17.6 — will not appear"
    };

    // Comp priority profiles: for the current meta, what augment
    // TYPES to favour. Principle-based so it stays useful even as
    // specific augments shift. Each entry: comp name, then priority.
    public static final String[][] COMP_PRIORITIES = {
        // {comp, augment priority order} — updated for 17.6
        {"Shepherd Bard / Vex Fast 9 (S)", "17.5+17.6: Bard + Vex buffed. Econ > AP Combat (Combat Caster, Luden's, Spellweaver). Need econ to hit 9."},
        {"Meeple Corki / Fateweavers (S)", "Strongest AD comp. Econ > Item slam. Level 8 on 4-2, roll for Corki+Riven+Rammus."},
        {"Samira + Ornn Reroll (S)", "Slow-roll L7. Econ (Patience, Buried Treasures) > Combat. 3-star Samira + Ornn is the win condition."},
        {"Stargazer Xayah (A)", "Consistent AD carry line. Combat > Item. Don't need vertical Stargazer to function."},
        {"Dark Star A.Sol / Jhin (A)", "AP frontline or AD sniper. Combat + Dark Star trait scaling. Loot Singularity (17.6 reworked) is S-tier here."},
        {"Psionic TF / Viktor (A)", "17.6: TF + Viktor major buffs. AP Psionic. Luden's Tempest > Combat Caster. Watch for open lobby."},
        {"Stargazer LeBlanc (A)", "17.6: LeBlanc viable AP Shepherd carry. Needs JG + Rabadon's + Blue Buff. Fast 9 or AP flex."},
        {"Dark Star Karma (A)", "17.6: Karma Split Damage buffed — now real AP Shepherd flex. Goes with Kai'Sa or Bard board."},
        {"Bel'Veth / RekSai Reroll (A)", "Heart of the Swarm (17.5: now 2* RekSai + Briar) is the flagship augment. Econ + Patience."},
        {"Gnar Meeple Reroll (B)", "17.6: Gnar AD nerf + Meeple(7) econ nerf specifically targeted this. Less consistent gold to L9."},
        {"Corki Riven Meeple (B)", "Still playable. Combat > Item. Pairs with the S-tier Corki line."},
        {"Arbiter Shen / Graves (B)", "17.6: Shen AS buff reliability improved. Arbiter trait Last Turn bonuses improved."}
    };

    // Known exclusion / interaction rules (Riot patch notes verified).
    // Add to these as more are confirmed each patch.
    public static final String[] EXCLUSIONS = {
        "Hero Augment taken → board-replace augs (Cosmic Restart, Restart Mission) won't appear",
        "17.6: Restart Mission — open fort interaction removed (no longer pairs with fort-style augs)",
        "Makeshift Armor 1 & 2 are mutually exclusive",
        "Lucky Gloves & Invader Zed are mutually exclusive",
        "Golden Gamble versions exclude each other",
        "\"Tier higher/lower\" augs change your NEXT armory's tier"
    };

    // General principle when nothing else applies.
    public static final String FALLBACK =
        "No clear comp yet? Take the strongest econ or a flexible combat/item aug. "
      + "Emblem augments give direction. Don't force. Play what your augs + Realm pick + open pool allow.";

    private AugmentData() {}
}
