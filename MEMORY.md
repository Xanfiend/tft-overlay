# MEMORY.md — running log

Standing memory across sessions (tablet/web setup, no claude-mem). Read at session start, append as decisions land. Keep terse — bullets, not prose. Architecture/instructions live in CLAUDE.md; this is *state*: what shipped, what's deferred, open threads.

## Current state
- Version: v1.99 (versionCode 99). Security pass toward 2.0 COMPLETE: (1) R8 obfuscation + resource shrink (v1.97, CI green); (2) DeviceIntegrity root/emulator heads-up on SETUP (v1.98); (3) first-launch privacy & permissions notice, re-openable from SETUP "Privacy & data" (v1.99, cfg_privacy_seen). All three are tap-path-free.
- Next: 1.99.x = release-candidate runway (polish/bugfix only). Then the v2.0 HEADLINE = opponent board scan (auto-scan every enemy in one planning phase). Needs live-game testing.

## Branding / teaser
- v2.0 release CODENAME = "TFT REAPER" (MainActivity.V2_CODENAME, one-line swap). Codename only — app stays "TFT Scryer" (package com.xanfiend.tftoverlay must NEVER change or the over-top updater orphans users). "TFT Killer" direction wanted; Reaper chosen (less cheat-tool read, fits THE HUNT/REAPING/pentacle lexicon).
- SETUP teaser card (top of buildSetup): "⛧ TFT REAPER · v2.0 INCOMING" + vague hype, NO feature spoilers. versionCode bumped to 100, versionName stays "1.99" (teaser is the announcement, not a logged changelog feature).

## Opponent-scan groundwork (started, invisible — no UI/version bump yet)
- OppScout.java (pure): Pool.getAllOppBoards() → Profile (role mix front/back/flank, flankHeavy flag, top enemy carries, counter tips). Consumes the EXISTING per-opp board storage (setOppBoard/getOppBoard slots 1-7), so it works off manually-scried enemies today and scales into the one-pass scan later.
- PositionAdvisor.plan(board, stageRound, OppScout.Profile) overload: appends counter-positioning tips when opp data exists; identical to fundamentals-only plan when it doesn't (no behavior change until wired).
- DONE: POSITION tab now renders a "COUNTER THE LOBBY" section from OppScout.analyze(pool.getAllOppBoards()) — role-mix line + counter tips, shown only when enemies have been scried (absent otherwise, so no behavior change for non-scriers). Works off manual scries today. versionCode 101, still versionName 1.99 (folds into the 2.0 changelog).
- Still TODO for the headline: the one-pass automation = tap through the up-to-7 enemy portraits in a single planning phase, scry each board, file into slots — THIS needs live-game testing on the tablet (gesture timing, portrait positions, planning-phase detection — can't verify in CI).
- Gotcha learned: buildSetup() in MainActivity reuses local var names (tbl etc.) — new locals there must not collide. (Caused the v2.0-teaser build failure; fixed.)
- v1.96: COACH roll check (RollMath P(hit) + ROLL/bank/HOLD), pool-aware via pool.remaining().
- Marching toward v2.0. Plan: small verifiable-without-a-game QoL through v1.9x; reserve opponent-board scan (per-enemy positioning) as the v2.0 headline. At v2.0 write a consolidated "what's new" reel re-highlighting COACH/POSITION/roll-check.
- Active dev branch: `claude/test-coverage-analysis-PAGmD`. Always push main too.
- Dev device: Galaxy Tab A 2016 — 32-bit Android, ~2GB RAM. Cannot run Termux/Claude Code/claude-mem locally (no 32-bit Bun/Node20 build). Work happens in web sessions only.

## Future direction: key/license system (v2.x, NOT a v2.0 blocker)
- Offline signed keys: generate keys locally with a private key; app bundles only the public key and verifies signature on-device. No server, no phone-home — keeps the offline + no-telemetry promise.
- Key payload encodes tier + optional expiry; checked locally. Revocation (if ever needed) could ride the existing GitHub-only RemoteData channel — defer.
- Rollout: Phase 1 = free no-expiry "founder" keys to build base/goodwill; Phase 2 = new keys paid/time-limited, founders keep theirs. Early-adopter loyalty.
- R8 obfuscation (v1.97) is the prerequisite — a key check is pointless if the APK is trivially patched to skip it.

## Decisions (so they aren't re-pitched)
- Auto meta-data updates for builds/comps: **declined** — no reliable on-device "what pros run" source; hand-maintaining metas unwanted.
- Distribution: GitHub-only, no ads. Possible manual license monetization later.
- claude-mem: **not viable** on this setup (needs persistent 64-bit machine). Using this file instead.
- Tap rate-limiting on accessibility dispatchGesture: **declined** — speed is everything; THE HUNT + planner auto-tap already fight the 1-shot/sec ceiling, a forced delay works against us.

## v2.0 plan
- 2.0 = "everything works as intended" milestone, NOT just a number. Don't release until it runs clean.
- Versioning (settled): next PUBLIC release is 2.0 — adjacent to 1.99, so NO numbers are skipped. NO 1.99.x (looks stalled). NO long silent gap framed as "hidden" (looks stopped). Instead 2.0 is ONE big COMBINED changelog: counter-positioning wiring + opponent scan + a recap line of the security pass — several bullet sections under one version. Internally keep bumping versionCode for CI test builds (invisible); don't cut a GitHub release until 2.0 is ready.
- Rule going forward: combine related small work under a meaningful version, never skip version numbers.
- VERSIONING SCHEME = semver MAJOR.MINOR.PATCH. MAJOR=landmark (2.0.0 = REAPER reveal), MINOR=real feature (new tab/scan mode), PATCH=bugfix/polish nested under its feature. This also kills the "1.99.x looks stalled" worry: with a consistent scheme, a patch number reads as a patch, not a stall. REAPER = 2.0.0; next fix 2.0.1, next feature 2.1.0. versionCode keeps incrementing as a plain int regardless.
- Changelog plan = PATH A now, PATH B right before v2.0:
  - A (DONE): keep history numbered as shipped (no backward renumber), collapsed the redundant probe-dot calibration tail (v1.17-v1.30, 14 entries → 2 range-labeled lines). Feature entries now HIGHLIGHTED in the changelog UI: gold border + "✦ FEATURE" badge + gold version label. Detection: desc starts with "NEW" OR version in MainActivity.FEATURE_VERS (v1.0/1.1/1.3/1.4/1.5/1.59 — the majors that don't say NEW).
  - B (TODO, do RIGHT BEFORE cutting v2.0 for a professional look): full semver relabel of all history (features x.y.0, fixes x.y.z). Accept that current number drops 1.99 → ~1.12; harmless (no users) and REAPER stays 2.0.0. Doing it just-in-time avoids the backward-jump optics mattering.
- Headline: opponent board scan (auto-scan every enemy in one planning phase → per-player threat map). The deferred big task.
- Security (required before 2.0): (1) ProGuard/R8 obfuscation — flip minifyEnabled true + rules; (2) root/emulator passive warning; (3) first-launch privacy notice (overlay+accessibility+screenshot, no telemetry/accounts, GitHub-only net). Tap rate-limit dropped.
- Reel angles: roll check ("know who to roll before you roll"), POSITION, enemy scan, "offline, no login, no ads."

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
