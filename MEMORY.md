# MEMORY.md — running log

Standing memory across sessions (tablet/web setup, no claude-mem). Read at session start, append as decisions land. Keep terse — bullets, not prose. Architecture/instructions live in CLAUDE.md; this is *state*: what shipped, what's deferred, open threads.

## Current state
- Version: v1.95 (versionCode 95). POSITION sub-tab + full Set 17 ThreatData roles (all 62 champs).
- Active dev branch: `claude/test-coverage-analysis-PAGmD`. Always push main too.
- Dev device: Galaxy Tab A 2016 — 32-bit Android, ~2GB RAM. Cannot run Termux/Claude Code/claude-mem locally (no 32-bit Bun/Node20 build). Work happens in web sessions only.

## Decisions (so they aren't re-pitched)
- Auto meta-data updates for builds/comps: **declined** — no reliable on-device "what pros run" source; hand-maintaining metas unwanted.
- Distribution: GitHub-only, no ads. Possible manual license monetization later.
- claude-mem: **not viable** on this setup (needs persistent 64-bit machine). Using this file instead.

## Shipped recently
- v1.93 reliability: cancel all pending overlay timers on shutdown.
- v1.92 launch screen: red circled hero pentacle + full-screen drawn background.
- v1.90 COACH tab: comp/items + econ call from scanned board (CompAdvisor + ChampItemData).
- v1.88 SCAN FROM IMAGE behind dev mode (tap version 7x).
- v1.86 RemoteData: new sets via data/setdata.json, no APK rebuild.

## Already shipped (do NOT rebuild — the plan file is stale)
- **Planner Snapshot scan**: DONE. SetIcons.java + 122 bundled seticons PNGs + startPlannerScan/calibration in OverlayService. Own-board ID in ~3 taps.
- **Hex-mesh auto-calibrate**: DONE (v1.85). hexAutoCalibrate/applyHexCalibration/isTealHex in OverlayService; "AUTO-CALIBRATE FROM BOARD" button in SETUP.

## Open threads / next candidates
- **Positioning helper**: DONE (v1.94). ThreatData.java (FRONT/BACK/FLANK roles) + PositionAdvisor.java (pure) + POSITION sub-tab under GUIDE. To extend per set: add champs to ThreatData.ROLE. To improve: opponent-board read would enable per-enemy counter-positioning (currently fundamentals + own-board sort only).
- Deferred (big): scan every opponent in one planning phase (unlocks per-enemy positioning).

## Known bugs (detail in CLAUDE.md)
- Level OCR misses level 1 (Tocker's) — partial fix shipped; needs in-game confirm log.
- Gold/XP HUD occasionally misreads — needs `goldXp:` logcat to diagnose.
