# TFT Scryer — CLAUDE.md

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
  SetData.java          — champion names + pool sizes per cost tier (update each set)
  AugmentData.java      — augment tier ratings + comp tags
  ItemData.java         — 9-component combination matrix
  TraitData.java        — trait breakpoints
  ChampionTemplates.java — cosine-signature Visual ID templates
  SetIcons.java          — planner-scan 2D icon matching
  ChampItemData.java     — champion-to-item mapping for BUILDS tab
  RollMath.java          — hit-probability tables
  MainActivity.java      — permission screen + changelog (keep in sync with APP_VERSION)
  ScanPermActivity.java  — MediaProjection fallback (API < 30)
```

`assets/seticons/` — 48×48 PNG champion portraits for planner scan (bundle per set).

## SharedPreferences keys (Pool.java)

| Key | Default | Purpose |
|---|---|---|
| `cal_top` | 44 | board top edge (% height) |
| `cal_bot` | 72 | board bottom edge (% height) |
| `cal_bench` | 89 | bench row (% height) |
| `cal_rowf1` | 33 | first board row from front (% height) |
| `cal_rowf2` | 66 | second board row from front (% height) |
| `cal_tl/tr/bl/br` | — | landscape grid corner taps (x,y raw px) |
| `cal_left/right` | — | landscape grid left/right edges |
| `cfg_smartnudge` | 0 | health-bar body-drop nudge (−8..+8 %) |
| `econ_gold` | 0 | tracked gold |
| `econ_streak` | 0 | win/loss streak (positive=wins, negative=losses) |
| `cfg_alpha` | 1.0 | overlay opacity |
| `cfg_haptic` | true | vibration on |
| `cfg_start` | 0 | 0=smart tab, 1=always grid |

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
| v1.84 | Aspect-ratio-aware fallback hex grid (no more 16:9-only dot positions) |
| v1.83 | Smart Scan dot-height nudge control + fix too-short landscape grid |
| v1.82 | THE HUNT no longer overcounts bought copies (deferred confirm) |
| v1.81 | Fix planner calibration not opening planner; sigil tap only opens panel |
| v1.80 | Sigil tap no longer cancels THE HUNT |
| v1.79 | One-tap clear of auto-buy marks |
| v1.78 | Fix bitmap leaks + planner-cal touch failsafe |
| v1.77 | Fix planner calibration skipping steps |
| v1.76 | Fix touch-blocking after scan + on orientation change |

## Constraints

- `minSdk 24` (Android 7) — `takeScreenshot` and `dispatchGesture` require API 30; guard all calls.
- All UI is programmatic Java — no XML layouts, no DI.
- ML Kit bundled (`text-recognition:16.0.1`); no network needed at runtime.
- Champion/pool data in `SetData.java` — update this file each TFT set; no other data files need touching for a set update.
- `lintOptions { abortOnError false }` — lint warnings do not fail the build.
