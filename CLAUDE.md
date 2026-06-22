# TFT Scryer — CLAUDE.md

## Memory

At session start, read `MEMORY.md` (running cross-session log: current state, decisions, open threads). Append to it as decisions land or work ships. Keep it terse.

## Model

Use the best available model: `claude-fable-5` if available, otherwise `claude-opus-4-8`. Never use Sonnet.

## Git workflow

- Always push to `main`. This is standing authorization — never ask for confirmation.
- Branch `claude/test-coverage-analysis-PAGmD` is the active dev branch; push there too when CI verification is needed.
- Commit format: `v{N}: one-line description` (no em dashes).
- Never `--no-verify` or force-push without explicit instruction.
- Build validation goes through CI (GitHub Actions); do not attempt `./gradlew` locally — the sandbox cannot reach Maven Central.

## Coding style

Terse, but explain. Code first, then 1-2 plain lines: what it does and why. Flag non-obvious syntax inline. Assume self-taught gaps. No boilerplate or comments unless asked. Do not restate the task. One change at a time. If an error is pasted, fix the cause — do not guess broadly.

## Project layout

```
app/src/main/java/com/xanfiend/tftoverlay/
  OverlayService.java   — all overlay UI and scan state machine (~4 000 LOC)
  Pool.java             — SharedPreferences-backed pool state + pure math helpers
  ScreenScanner.java    — ML Kit OCR passes (shop / popup / bench)
  TFTAccessibilityService.java — takeScreenshot + dispatchGesture
  SetData.java          — champion names + pool sizes per cost tier (BUNDLED FALLBACK; per-set fields are non-final, overwritten by RemoteData at startup)
  RemoteData.java       — remote set-data sync: pulls data/setdata.json from GitHub on launch, caches to disk, falls back to bundled
  AugmentData.java      — augment tier ratings + comp tags
  ItemData.java         — 9-component combination matrix
  TraitData.java        — trait breakpoints
  ChampionTemplates.java — cosine-signature Visual ID templates
  SetIcons.java          — planner-scan 2D icon matching
  ChampItemData.java     — champion-to-item mapping + carry→comp/tier (BUILDS tab + COACH); update PATCH each patch
  CompAdvisor.java       — pure mid-game coach: board+econ → recommended comp/items + roll/level call (COACH sub-tab)
  PositionAdvisor.java   — pure positioning coach: board → front/back/flank map + carry corner + evergreen fundamentals (POSITION sub-tab)
  OppScout.java          — pure opponent threat model: aggregates remembered enemy boards (Pool slots 1-7) → lobby role mix + archetypes (hook/AoE) + AP/AD split + top carries + counter tips + tech tips. Feeds POSITION (COUNTER THE LOBBY) and COACH (TECH vs LOBBY)
  OpenerData.java        — evergreen early-game reference (stage arc + item-slam priority + principles); OPENER sub-tab under GUIDE
  ThreatData.java        — per-champ positioning role (FRONT/BACK/FLANK); meta-stable, per-SET refresh not per-patch
  RollMath.java          — hit-probability tables
  DeviceIntegrity.java   — passive root/emulator heuristics; SETUP heads-up only (never blocks, never reports)
  MainActivity.java      — permission screen + changelog (keep in sync with APP_VERSION)
  ScanPermActivity.java  — MediaProjection fallback (API < 30)
  ImageScanActivity.java — dev: pick a saved screenshot, run the scan pipeline on it (no TFT needed)
```

`assets/seticons/` — 48×48 PNG champion portraits for planner scan (bundle per set).
`data/setdata.json` — remote set data the app fetches on launch (champs/sizes/gods). Keep in sync with `SetData.java` bundled fallback.

## Set updates (remote data sync)

Since v1.21.0 a new set does **not** require an APK. Edit `data/setdata.json`, push to `main`, and the app pulls it on next launch (`RemoteData.syncAsync` → cache → applied at the *following* launch via `loadCachedOrBundled`). Network result never mutates `SetData` mid-session.

- Runtime source: `https://raw.githubusercontent.com/Xanfiend/tft-overlay/main/data/setdata.json` (GitHub only — preserves the updater's privacy promise).
- `SetData.java` is the **bundled fallback** (offline / pre-first-sync) — keep it current too.
- Dev tooling: `scripts/gen_setdata.py --from-cdragon` regenerates the JSON from CommunityDragon (carries over `size`/`gods`, which CDragon doesn't publish); `--validate` checks it against the same rules `RemoteData.validate()` enforces (run before committing). CDragon is a **dev-time** source only — never a runtime dependency.
- Schema: `{version, setName, patch, size[6], gods[], champs[6][]}` — `champs[0]` empty, `champs[1..5]` per cost tier, all non-empty. Bad/short payloads are rejected, never overwrite the set.
- `SetData` per-set fields (`SET_NAME`, `PATCH`, `SIZE`, `CHAMPS`, `GODS`) are non-final and treated as read-only outside `RemoteData`. `Pool.invalidateData()` drops the cost cache after a swap.

## SharedPreferences keys (Pool.java)

| Key | Default | Purpose |
|---|---|---|
| `cal_top` | 44 | board top edge (% height) |
| `cal_bot` | 72 | board bottom edge (% height) |
| `cal_bench` | 89 | bench row (% height) |
| `cal_rowf1` | 33 | first board row from front (% height) |
| `cal_rowf2` | 66 | second board row from front (% height) |
| `cal_tl/tr/bl/br` | — | landscape grid corner taps (x,y raw px) |
| `cal_opp1..7` | — | enemy-portrait tap positions for one-pass opponent scan (x,y raw px) |
| `cal_left/right` | — | landscape grid left/right edges |
| `cfg_smartnudge` | 0 | health-bar body-drop nudge (−8..+8 %) |
| `econ_gold` | 0 | tracked gold |
| `econ_streak` | 0 | win/loss streak (positive=wins, negative=losses) |
| `cfg_alpha` | 1.0 | overlay opacity |
| `cfg_haptic` | true | vibration on |
| `cfg_start` | 0 | 0=smart tab, 1=always grid |
| `cfg_devmode` | false | hidden dev tools (Scan From Image); unlock by tapping version label 7x |
| `cfg_privacy_seen` | false | first-launch privacy notice acknowledged (MainActivity) |

`pool.hasLandscapeGridCal()` — true when any of `cal_tl/tr/bl/br/cal_left/cal_right` exist.

## Known bugs / pending work

### Level OCR misses level 1 (Tocker's Trials)
Regex `[2-9]|10` cannot match "1". Returns `-1`. Fix: extend regex to `[1-9]|10` and guard the level-1 case where roll odds and XP bar don't apply. Needs a debug log line `auto-tap: gold=… level=…` from the user to confirm before patching.

### Gold/XP HUD reading wrong numbers
Need `goldXp: gold=… lvl=… xp=…/…` from logcat to diagnose OCR zone misalignment before touching zone constants.

### Hex-grid auto-calibration (hex-mesh detector)
Planned "AUTO-CALIBRATE FROM BOARD" button:
- During planning phase, teal/blue hex outlines form a connected mesh detectable via HSV threshold + connected-component analysis.
- Distance transform finds hex centers → fit 4×7 grid → save to `cal_tl/tr/bl/br`.
- Needs a clean planning-phase screenshot from the user to tune and validate the detector before implementation.
- Combat screenshots corrupt left/bottom edges (units occlude hexes) — require planning phase.

## Direction notes (decisions, so they aren't re-pitched)

- Remote/auto meta-data updates for builds/comps: **declined.** No reliable on-device source for "what pros run"; hand-maintaining item metas is unwanted upkeep. COACH/BUILDS read the bundled `ChampItemData` snapshot and may go stale — accepted limitation.
- Distribution stays GitHub-only; no ads; possible manual "license" monetization later. Automation (THE HUNT, planner auto-tap) stays.
- Candidate future feature: **positioning helper** (high value — positioning wins otherwise-unwinnable rounds). Depends on reading the opponent board; the evergreen/reliable part is fundamentals + rule-based counter-positioning (spread vs clump, corner the carry) keyed off slow-changing per-champ threat tags (diver/AoE/backline-access), NOT patch-specific item data.
- Deferred (big task): auto-scan that reads **every opponent in one planning phase**.

## Scan architecture

```
sigil tap → panel toggle (never stops a scan)
STOP button → only way to end Board / Opp / Auto / Planner scans

triggerPopupScan()
  ↓ takeScreenshot()  [AccessibilityService, API 30+]
  ↓ ScreenScanner.scanBitmap(MODE_POPUP)
  ↓ parsePopup() → ScanResult.detectedBoardUnit + detectedBoardStars
  → if oppScanMode:   applyOppPopupScanResult()
  → else:             applyPopupScanResult()

Auto Scan flow:
  1. detectHealthBarUnits() → positions + star levels
  2. Planner snapshot (3 taps: open, snapshot, close) → champion NAME LIST
  3. Merge by count; unresolved units fall back to tap-per-unit OCR
  4. Bench units: existing bench OCR pass

THE HUNT (auto-buy):
  huntBuyNext() fires a tap; does NOT immediately call pool.add()
  huntPendingBuys.put(name, timestamp) — deferred
  handleHuntResult() at next poll: if card gone from shop → pool.add(name,1); huntBuys.add(name)
                                   if still present → log "unaffordable", discard
  HUNT_CONFIRM_MS = 900 ms settle window

injecting boolean guard:
  Set true before dispatchGesture; cleared in callback or by clearInjecting().
  HyperOS/MIUI can drop the callback → stuck true → silent tap skips.
  startPlannerCalibration() calls clearInjecting() to reset it.
```

## UI color constants (OverlayService.java)

```java
VOID    = 0xFF0B0709   // darkest background
CARD    = 0xFF16100F   // card/panel bg
EDGE    = 0xFF3A2024   // borders
BLOOD   = 0xFF8B1A1A   // primary button bg
BLOODL  = 0xFFC1121F   // primary button border / danger text
BONE    = 0xFFD9C9C0   // main text
ASH     = 0xFF8A7A75   // secondary text
DIM     = 0xFF4A3A38   // hint / footer text
GOLD    = 0xFFC9A227   // gold accent / interest brackets
GREEN   = 0xFF4CAF50   // win streak / positive
```

`box(bg, radius, border, stroke)` — returns a `GradientDrawable`; used everywhere for buttons and cards.

## Version history (recent)

| Version | Change |
|---|---|
| v1.24.5 | First-launch privacy & permissions notice (re-openable from SETUP). v2.0 security step 3 (final) |
| v1.24.4 | Passive root/emulator heads-up on SETUP (DeviceIntegrity; informational, never blocks/reports). v2.0 security step 2 |
| v1.24.3 | R8 obfuscation + resource shrink on release build (proguard-rules.pro; ML Kit kept). First v2.0 security step |
| v1.24.2 | COACH roll check — Monte-Carlo P(hit carry) at current gold/level + ROLL/bank/HOLD call (RollMath) |
| v1.24.1 | Complete ThreatData roles for full Set 17 roster (all 62 champs front/back/flank) |
| v1.24.0 | POSITION sub-tab — front/back/flank map + carry corner + evergreen fundamentals (PositionAdvisor + ThreatData) |
| v1.23.3 | Cancel all pending overlay timers on shutdown (reliability) |
| v1.23.2 | Red circled hero pentacle + full-screen drawn launch background |
| v1.23.1 | Launch-screen occult symbols drawn as vector pentagrams (font-independent, can't tofu) |
| v1.23.0 | COACH tab — recommend comp/items + econ call from scanned board (CompAdvisor + ChampItemData) |
| v1.22.2 | Fix tofu glyphs on launch screen (tablet fonts) |
| v1.22.1 | SCAN FROM IMAGE moved behind hidden dev mode (tap version 7x); `cfg_devmode` |
| v1.22.0 | SCAN FROM IMAGE dev mode — run the scan pipeline on a saved screenshot (no game) |
| v1.21.0 | Remote set-data sync (RemoteData) — new sets via data/setdata.json, no APK rebuild |
| v1.20.11 | Fix level-1 OCR (Tocker's), fix gold fused-icon, AUTO-CALIBRATE FROM BOARD (hex mesh) |
| v1.20.10 | Aspect-ratio-aware fallback hex grid (no more 16:9-only dot positions) |
| v1.20.9 | Smart Scan dot-height nudge control + fix too-short landscape grid |
| v1.20.8 | THE HUNT no longer overcounts bought copies (deferred confirm) |
| v1.20.7 | Fix planner calibration not opening planner; sigil tap only opens panel |
| v1.20.6 | Sigil tap no longer cancels THE HUNT |
| v1.20.5 | One-tap clear of auto-buy marks |
| v1.20.4 | Fix bitmap leaks + planner-cal touch failsafe |
| v1.20.3 | Fix planner calibration skipping steps |
| v1.20.2 | Fix touch-blocking after scan + on orientation change |

## Constraints

- `minSdk 24` (Android 7) — `takeScreenshot` and `dispatchGesture` require API 30; guard all calls.
- All UI is programmatic Java — no XML layouts, no DI.
- ML Kit bundled (`text-recognition:16.0.1`); OCR/scan work fully offline.
- Network is GitHub-only and optional: self-update (`Updater`) + set-data sync (`RemoteData`). App runs fully offline on bundled/cached data.
- Set updates: edit `data/setdata.json` (runtime) AND `SetData.java` (bundled fallback). See "Set updates" above.
- `lintOptions { abortOnError false }` — lint warnings do not fail the build.
