package com.xanfiend.tftoverlay;

/*
 * Early-game opener reference for the OPENER sub-tab. Deliberately EVERGREEN —
 * stage-by-stage tempo/econ fundamentals and item-slam priorities that hold
 * across patches and sets, so this needs no per-patch upkeep (unlike the
 * ChampItemData build metas). Pure static data, no Android.
 */
public final class OpenerData {

    // {heading, body} — the standard arc of a game by stage.
    public static final String[][] PHASES = {
        {"Stage 1  ·  econ & components",
            "Take components and the strongest unit on every carousel — favour a component for your likely carry. Don't roll. Hold any pairs. Let interest build."},
        {"Stage 2  ·  strongest board + one streak",
            "Play your strongest board each round. Pick ONE streak and commit — win-streak for tempo gold or lose-streak to save — never flip-flop, that loses both bonuses. Slam 1-2 strong tempo items if you're bleeding HP."},
        {"Stage 3  ·  stabilize or bank",
            "Aim for 50 gold (max interest) if HP allows. Level to 6 by 3-2. If you're low (under ~40 HP), roll a little at 3-2 to stabilize. Start committing to your final comp."},
        {"Stage 4  ·  main roll-down",
            "The big one. Level 7 at 4-1 (or 8 at 4-5 for a higher-cost comp) and roll for your 2-stars. Slam your carry's items now — this is the fight that decides your game."},
        {"Stage 5+  ·  finish the board",
            "Level 8-9, hunt upgrades and 5-costs. Stabilize a strong board first, then push levels only while healthy. 3-stars only if you're already winning."},
    };

    // {carry type, what to hold / slam} — tempo item priority.
    public static final String[][] SLAMS = {
        {"AD carry",       "Hold B.F. Sword — Deathblade / Infinity Edge / Giant Slayer are safe slams."},
        {"AP carry",       "Hold Rod — Hextech Gunblade / Jeweled Gauntlet / Archangel's."},
        {"Attack speed",   "Hold Bow — Guinsoo's / Last Whisper / Runaan's."},
        {"Mana carry",     "Hold Tear — Spear of Shojin / Blue Buff / Archangel's."},
        {"Tank",           "Hold Belt + Vest — Warmog's, Bramble, Sunfire, Gargoyle are always useful."},
        {"Always good",    "Morellonomicon, Hand of Justice, Sunfire — slam freely to hold tempo."},
    };

    // One-line evergreen principles.
    public static final String[] PRINCIPLES = {
        "Slam tempo items — losing a streak costs more than a slightly-wrong item.",
        "Commit to ONE streak (win or loss); flip-flopping loses both bonuses.",
        "Don't roll on econ unless stabilizing low HP — interest compounds.",
        "Scout the lobby every round — scry enemies and read COUNTER THE LOBBY / TECH vs LOBBY.",
    };

    private OpenerData(){}
}
