# MEMORY.md — running log

Standing memory across sessions (tablet/web setup, no claude-mem). Read at start, append as decisions land. Terse. Architecture lives in CLAUDE.md; this is *state*: shipped / deferred / decided. Per-version detail is in git + the in-app changelog — don't duplicate it here.

## Current state
- versionName "1.24.5"; versionCode 111 (plain int, bumps every CI build). versionName stays at the last changelog'd release until REAPER ships.
- REAPER = **2.0.0**, cut ONLY when the opponent-scan headline is LIVE-TESTED and working ("everything works as intended", not a number). Caught + reverted a premature 2.0 cut on 06-22.
- All post-1.24.5 work (COACH on-curve read, OPENER tab, SCRY THE LOBBY scaffold, UI polish, DEV DIAGNOSTICS) is committed but UNlogged — it folds into the single combined 2.0.0 changelog.
- Active dev branch: `claude/test-coverage-analysis-PAGmD`. Always push `main` too (standing authorization).
- Dev device: Galaxy Tab A 2016 (32-bit, ~2GB) — can't run Termux/Claude Code locally. Web sessions only. Builds verify via CI (GitHub Actions); no local `./gradlew` (sandbox can't reach Maven Central).

## THE headline (blocks 2.0): opponent-scan automation — LIVE-TEST GATED
Needs a laptop/emulator + real game; can't verify in CI.
- **Phase 1 (champs+stars) = SCAFFOLDED, untested.** `startScanAllOpponents → scanAllStep (tap portrait i → wait SCANALL_SETTLE_MS → startAutoOppScan) → finishAutoTapScan (when scanAllMode → scanAllAdvance → next → finishScanAll)`. stopScanAll in stopActiveMode. Portrait cal: startOppCalibration + overlay records up to 7 taps (Pool cal_opp1..7). UI: "◉ SCRY THE LOBBY" in POOL (gated on hasOppPortraitCal) + "CALIBRATE PORTRAITS" in SETUP. **TUNE LIVE:** SCANALL_SETTLE_MS, portrait positions, whether the per-board sweep reliably reads after a portrait tap. Cosmetic rough edge: btnLabel/stop-button flicker between boards.
- **Phase 2 (enemy items) = SCAFFOLDED (06-22), untested.** `ItemIcons.java` mirrors SetIcons (cosine match on assets/itemicons/<Item>.png → ItemData.fullItems() names; MIN_SIM/MIN_MARGIN + SCALE=32 to TUNE LIVE). `ItemData.fullItems()` = distinct completed-item names. Pool gained `OppUnit{name,stars,items[]}` + `getOppUnits/setOppUnits` extending oppboard to "name|stars|item1,item2,item3" (backward-compatible; Map accessors + OppScout untouched). ItemIcons.load() called in buildSetup; item-icon count shown in DIAGNOSTICS. assets/itemicons/ has a README placeholder. No icons bundled yet → match() returns null, zero behavior change.
  - **TODO on PC/emulator:** drop full-item PNGs (CommunityDragon) into assets/itemicons; tune thresholds + the popup item-slot crop rects; in the per-board scan, crop each held-item slot → ItemIcons.match → setOppUnits with items; then enrich OppScout to weight threats by items.

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
- Semver MAJOR.MINOR.PATCH. MINOR (x.y.0) = real feature/new tab/scan mode; PATCH (x.y.z) = fix/polish. REAPER = 2.0.0; next fix 2.0.1, next feature 2.1.0. versionCode = plain incrementing int regardless.
- **Path B DONE (06-22):** full semver relabel of all history (changelog + README + CLAUDE.md table + version labels). v1.0→1.0.0 … v1.99→1.24.5. README probe-dot tail (old v1.17-v1.30) collapsed into v1.7.0+v1.7.1. Feature-highlight is now self-maintaining: version ends ".0" OR desc starts "NEW" → gold ✦ badge (FEATURE_VERS set deleted).
- 2.0 ships as ONE combined changelog (counter-positioning + opponent scan + security recap). Never skip numbers; combine small work under a meaningful version.

## Decisions (don't re-pitch)
- Auto meta-data updates for builds/comps: **declined** (no reliable on-device "what pros run" source; hand-maintaining metas unwanted — COACH/BUILDS read the bundled ChampItemData snapshot, may go stale, accepted).
- Distribution: GitHub-only, no ads. Possible manual license later.
- Tap rate-limiting on dispatchGesture: **declined** — speed is everything (THE HUNT + planner auto-tap fight the 1-shot/sec ceiling).
- claude-mem: not viable here (needs persistent 64-bit machine) — this file instead.

## Future: key/license system (v2.x, NOT a 2.0 blocker)
- Offline signed keys: generate locally with a private key; app bundles only the public key, verifies on-device. No server/phone-home. Payload = tier + optional expiry. Rollout: free "founder" keys first (build base), paid/time-limited later, founders keep theirs. R8 (done) is the prerequisite.

## Gotchas / known bugs
- buildSetup() in MainActivity reuses local var names (tbl etc.) — new locals there must not collide (caused a build failure once).
- Level OCR misses level 1 (Tocker's) — partial fix; needs in-game confirm log.
- Gold/XP HUD occasionally misreads — needs `goldXp:` logcat to diagnose.
