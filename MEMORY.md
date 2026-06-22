# MEMORY.md — running log

Standing memory across sessions (tablet/web setup, no claude-mem). Read at start, append as decisions land. Terse. Architecture lives in CLAUDE.md; this is *state*: shipped / deferred / decided. Per-version detail is in git + the in-app changelog — don't duplicate it here.

## Current state
- versionName "1.99.1"; versionCode 113 (plain int, bumps every CI build). VERSION NUMBERS ONLY GO FORWARD — the rolling GitHub release keys the updater on version numbers, so going backward breaks it (see below). Pre-2.0 dev builds march as 1.99.x.
- REAPER = **2.0.0**, cut ONLY when the opponent-scan headline is LIVE-TESTED and working ("everything works as intended", not a number). Caught + reverted a premature 2.0 cut on 06-22.
- **PATH B (semver relabel) REVERTED 06-22 — it was PREMATURE.** Backward-renumbering the live version (1.99→1.24.5) broke the updater: the rolling "latest" release accumulates every historical APK as an asset, and the old updater picked the HIGHEST asset number, so old assets v1.25–v1.99 (and a stray v2.0 from the reverted premature cut) all read as "newer" than 1.24.5 → permanent false "update available", showing phantom versions. Restored MainActivity/README/CLAUDE to pre-Path-B (01ff9a1) + forward versionName 1.99.1. **Do Path B AT the 2.0 cut** (fresh release, 2.0.0 supersedes everything, renumber is painless then) — exactly what the original plan said.
- **Updater hardened (v1.99.1):** parseLatestVersion now trusts the release TITLE first (the build always sets it to current versionName), assets only as fallback — so a stray asset can't create a phantom. Bootstrap: installed apps have the OLD asset-max updater, so they may still show "v2.0" once; the download is always the rolling tft-scryer.apk (= newest build), so updating once lands the fixed updater. The stray `tft-scryer-v2.0.apk` should be deleted from the GitHub release for cleanliness (can't do via MCP — needs manual/gh; harmless once updater is title-based, but MUST be gone before real 2.0.0 ships or it'd tie/block it).
- All post-1.99 work (COACH on-curve read, OPENER tab, both scan scaffolds, UI polish, DEV DIAGNOSTICS, updater fix) folds into the single combined 2.0.0 changelog.
- Active dev branch: `claude/test-coverage-analysis-PAGmD`. Always push `main` too (standing authorization).
- Dev device: Galaxy Tab A 2016 (32-bit, ~2GB) — can't run Termux/Claude Code locally. Web sessions only. Builds verify via CI (GitHub Actions); no local `./gradlew` (sandbox can't reach Maven Central).

## THE headline (blocks 2.0): opponent-scan automation — LIVE-TEST GATED
Needs a laptop/emulator + real game; can't verify in CI.
- **Phase 1 (champs+stars) = SCAFFOLDED, untested.** `startScanAllOpponents → scanAllStep (tap portrait i → wait SCANALL_SETTLE_MS → startAutoOppScan) → finishAutoTapScan (when scanAllMode → scanAllAdvance → next → finishScanAll)`. stopScanAll in stopActiveMode. Portrait cal: startOppCalibration + overlay records up to 7 taps (Pool cal_opp1..7). UI: "◉ SCRY THE LOBBY" in POOL (gated on hasOppPortraitCal) + "CALIBRATE PORTRAITS" in SETUP. **TUNE LIVE:** SCANALL_SETTLE_MS, portrait positions, whether the per-board sweep reliably reads after a portrait tap. Cosmetic rough edge: btnLabel/stop-button flicker between boards.
- **Phase 2 (enemy items) = SCAFFOLDED (06-22), untested.** `ItemIcons.java` mirrors SetIcons (cosine match on assets/itemicons/<Item>.png → ItemData.fullItems() names; MIN_SIM/MIN_MARGIN + SCALE=32 to TUNE LIVE). `ItemData.fullItems()` = distinct completed-item names. Pool gained `OppUnit{name,stars,items[]}` + `getOppUnits/setOppUnits` extending oppboard to "name|stars|item1,item2,item3" (backward-compatible; Map accessors + OppScout untouched). ItemIcons.load() called in buildSetup; item-icon count shown in DIAGNOSTICS. assets/itemicons/ has a README placeholder. No icons bundled yet → match() returns null, zero behavior change.
  - **Item-aware OppScout DONE (pure, dormant until live):** ItemData.isApItem/isAdItem/isHealItem (evergreen item-property tags, per-SET upkeep). Pool.getAllOppUnits(). OppScout.analyzeUnits(List<List<OppUnit>>) is now canonical (Map-based analyze() adapts to it); tallies apItems/adItems/healItems and adds "Item read:" tech tips (anti-heal if healing items; Armor/MR by AD/AP item skew). Both COACH+POSITION callers use analyzeUnits(getAllOppUnits()). items[] empty today → tips never fire → zero behavior change; auto-sharpens when the scan fills items.
  - **TODO on PC/emulator:** drop full-item PNGs (CommunityDragon) into assets/itemicons; tune ItemIcons thresholds + popup item-slot crop rects; in the per-board scan, crop each held-item slot → ItemIcons.match → setOppUnits with items. The OppScout consumption is already wired.

## Shipped & pure (do NOT rebuild)
- **OppScout.java** (pure): aggregates Pool.getAllOppBoards() → Profile (role mix, flankHeavy, hooks/aoe, AP/AD split, biggest-threat by star×cost, lobby-power snowball detection, top carries, counter tips, tech tips). Feeds POSITION "COUNTER THE LOBBY" + COACH "TECH vs LOBBY". Works off manual scries today, scales into the one-pass scan.
- **ThreatData.java**: FRONT/BACK/FLANK roles + isHook/isAoe/damageType archetypes. Per-SET refresh (meta-stable), not per-patch.
- **PositionAdvisor / CompAdvisor / OpenerData / RollMath**: all pure. COACH = comp+items+econ+roll-check(Monte-Carlo P(hit))+on-curve level read. POSITION = front/back/flank map + fundamentals. OPENER = stage arc + item-slam priority. GUIDE sub-tabs: COACH/POSITION/OPENER/AUGMENTS/ITEMS.
- **Planner Snapshot scan** + **Hex-mesh auto-calibrate** + **RemoteData set sync**: all DONE (plan file is stale on these).
- **Security pass COMPLETE**: R8 obfuscation, DeviceIntegrity root/emu heads-up, first-launch privacy notice.
- **Dev mode**: tap version label 7x → cfg_devmode → SCAN FROM IMAGE + DEBUG LOG ring buffer + copyable DIAGNOSTICS card (build/device/screen/integrity/state/cal dump).

## Branding
- Codename "TFT REAPER" (MainActivity.V2_CODENAME) — DISPLAY ONLY. App stays "TFT Scryer"; package `com.xanfiend.tftoverlay` must NEVER change (over-top updater orphans users otherwise). SETUP teaser card hypes 2.0, no spoilers.

## Versioning (settled)
- LIVE version number ONLY GOES FORWARD (rolling-release updater compares numbers). Pre-2.0 = 1.99.x. Target semver MAJOR.MINOR.PATCH lands AT the 2.0 cut: REAPER = 2.0.0, then 2.0.1 (fix), 2.1.0 (feature). versionCode = plain incrementing int.
- **Path B (full semver relabel of history) = DEFERRED to the 2.0 cut.** Tried it early 06-22, reverted — backward renumber broke the updater (see Current state). At the 2.0 cut, relabel the in-app changelog/README display AND ship a fresh 2.0.0 that supersedes all old assets — painless then. Don't touch the live number scheme before that.
- 2.0 ships as ONE combined changelog (counter-positioning + opponent scan + security recap). Never skip numbers; combine small work under a meaningful version.

## Decisions (don't re-pitch)
- Auto meta-data updates for builds/comps: **declined** (no reliable on-device "what pros run" source; hand-maintaining metas unwanted — COACH/BUILDS read the bundled ChampItemData snapshot, may go stale, accepted).
- Distribution: GitHub-only, no ads. Possible manual license later.
- Tap rate-limiting on dispatchGesture: **declined** — speed is everything (THE HUNT + planner auto-tap fight the 1-shot/sec ceiling).
- claude-mem: not viable here (needs persistent 64-bit machine) — this file instead.

## Future: key/license system (v2.x, NOT a 2.0 blocker)
- Offline signed keys: generate locally with a private key; app bundles only the public key, verifies on-device. No server/phone-home. Payload = tier + optional expiry. Rollout: free "founder" keys first (build base), paid/time-limited later, founders keep theirs. R8 (done) is the prerequisite.

## Tests (CI)
- JVM unit suite added 06-22 in `app/src/test/java/...`: CompAdvisorTest, PoolMathTest, ItemDataTest, OppScoutTest, RollMathTest, ChampItemDataTest (+CompAdvisor.recommend), ThreatDataTest, SetDataTest (roster integrity — incl. no champ in two tiers, ODDS shape), StaticDataTest (Opener/Augment/Trait well-formedness). Covers the pure logic (no Android calls → runs on the JVM with android.jar stubs). CI workflow runs `./gradlew testDebugUnitTest` before the APK build, so a logic OR set-data regression fails CI. `testImplementation junit:junit:4.13.2`.
- Locked-in facts worth knowing: streakBonus is ASYMMETRIC (win +1@3/+2@5/+3@6; loss +1@2/+2@4/+3@5) and expectedIncome adds a win-round +1 when streak>0. OppScout item tips only fire when items[] is populated.
- Add tests alongside new pure logic. Only test classes free of Android calls (the *Data/*Advisor/OppScout/RollMath/Pool static helpers + Pool.OppUnit); Pool instance methods need SharedPreferences → not JVM-testable without Robolectric.

## Gotchas / known bugs
- buildSetup() in MainActivity reuses local var names (tbl etc.) — new locals there must not collide (caused a build failure once).
- Level OCR misses level 1 (Tocker's) — partial fix; needs in-game confirm log.
- Gold/XP HUD occasionally misreads — needs `goldXp:` logcat to diagnose.
