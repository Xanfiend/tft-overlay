<div align="center">

# ⦿ TFT SCRYER

### ✦ Champion pool & reroll-odds overlay for Teamfight Tactics ✦
#### Android / mobile only

[![Download APK](https://img.shields.io/badge/⬇_DOWNLOAD_APK-C1121F?style=for-the-badge&logoColor=white)](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)

![Platform](https://img.shields.io/badge/platform-Android-1A1A1A?style=flat-square)

</div>

<div align="center">

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

</div>

A floating overlay for TFT Mobile on Android. Tracks the champion pool and reroll odds so you always know whether to roll or save, without switching apps.

> **Note:** Android only. Not for PC.

## ✦ Install

1. **[Download the APK](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)**
2. Open the downloaded file. If your browser asks, tap **Open** or **Install anyway**.
3. Android will ask to allow installing from unknown sources — tap **Settings**, enable it for your browser, then go back and tap **Install**.
4. **Play Protect warning**: Google may show a warning saying the app is not recognised. This is normal for any APK not distributed through the Play Store. Tap **Install anyway** (or **More details → Install anyway** on some devices). The app is safe — the full source code is in this repo.
5. Open TFT Scryer, grant the overlay permission, and tap **Start Overlay**.
6. Launch TFT. The floating sigil appears on screen.

> **Samsung devices**: if Install anyway is greyed out, go to Settings → Biometrics and security → Install unknown apps and enable it for your browser.

## ✦ Tabs

| Tab | What it does |
|---|---|
| **POOL** | Tap a champion to mark a copy as seen. Tap the number to remove one. The ◉ badge shows how many other players are on that unit. Champions you recently marked show at the top. Tap **Auto Scan Board** to auto-detect all units on your board; tap **Opp Board** to scan an opponent's board with star levels. Both require Accessibility service. |
| **ODDS** | Shows copies left in the pool, how contested each unit is, and your odds of hitting per roll at your current level. Pin your carry to the top. Junk bench-thinning adjusts odds for units you are holding to block others. |
| **GUIDE** | Two sub-tabs: AUGMENTS (S/A/B/C tier list with comp tags, priorities, exclusions, and armory rules) and ITEMS (tap two components to see the combined item, full trait breakpoints below). |
| **GOLD** | Track your gold with live interest calculation, the 10/20/30/40/50g bracket ladder, win/loss streak tracking with bonus scale, and expected income next round. Hold + or - to change gold quickly. |
| **SETUP** | Accessibility service status with setup instructions. Scan Now fills gold, level, and augments. Transparency, haptic, start-tab choice, button position reset, calibration controls, and debug log. |

## ✦ Usage

- **Tap the sigil** to open the grid. **Long-press** to open the board. **Hold 1.5s** to trigger a scan instantly.
- **Auto Scan Board**: tap Auto Scan Board in the grid tab. The app taps all board hexes (front row first) and bench slots, reads the popup after each, and marks all found champions. Stops after 5 consecutive empty board hexes, then scans the bench and stops after 3 empty bench slots. If templates are saved from previous scans, known units are identified instantly from a single screenshot before any tapping begins. Typical time is 5-15 seconds. Tap the sigil to stop early.
- **My Board scan**: tap My Board in the grid tab, then tap each unit on your board manually. The app reads the name from the stat popup and marks it in your pool. 25 second window.
- **Opp Board scan**: navigate to an opponent's board in TFT, tap Opp Board, then tap each of their units. Reads name and star level (shown as Jinx ★★) and increments their contest badge. 30 second window. All three scans require the Accessibility service (one-time setup in Settings).
- **Drag the sigil** to move it. Drag it onto **✕** to close the overlay.
- **Reset** between games to clear pool, gold, and contest data.

## ✦ Device requirements

| | |
|---|---|
| **OS** | Android 7.0 or later (API 24+) |
| **Permissions** | Draw over other apps (required). Screen capture via Scan Now (optional). |
| **Internet** | Not required. The app is fully offline. |
| **Storage** | About 10 MB installed. |
| **Scan Now** | Works on Android 7.0+. On Android 14+ some devices (Xiaomi MIUI, some Samsung OneUI builds) may have issues with screen capture. The debug log in Settings will show the exact error if it fails. |
| **Board Scan / Silent Scan** | Requires the Accessibility service enabled (Android 12+ only). Go to Settings tab in the overlay, tap App settings, allow restricted settings, then go to Accessibility and turn on TFT Scryer. |
| **Tested on** | Android 10, 12, 13, 14. Should work on 7.0 and above. |

## ✦ FAQ

**Does it work on iPhone?**
No. Android only.

**Does it work on PC?**
No. It is a mobile overlay for TFT Mobile on Android.

**The overlay does not appear over TFT.**
Open TFT Scryer, tap Start Overlay, then switch to TFT. If it still does not show, go to Android Settings and make sure TFT Scryer has the "Display over other apps" permission.

**The app crashes when I tap Scan Now.**
This is a known issue on some devices (especially Xiaomi / MIUI). Update to the latest version from the releases page. If it still crashes, open Settings in the overlay after the next scan attempt and check the Debug Log card for the exact error.

**Scan Now runs but detects nothing / wrong values.**
The scan reads text from your screen using OCR. Make sure the TFT game screen is visible and not covered when you tap Scan. Bright HUD elements and high contrast help. Detection is best on a clean shop or augment screen.

**Play Protect says the app is not recognised / blocks the install.**
Tap **Install anyway** or **More details → Install anyway**. Play Protect warns about any APK not distributed through the Play Store, regardless of whether it is safe. This app has no internet permission and collects nothing. The full source code is in this repo. If the button is greyed out, go to Settings → Biometrics and security → Install unknown apps and enable it for your browser, then try again.

**A virus scanner flagged the APK.**
Same reason as above — common false positive for APKs not signed by a Play Store publisher. Full source is here, check it yourself or see the VirusTotal link below.

**The champion pool data is out of date.**
Pool data updates with each new version. Check the releases page for the latest APK. If a new patch just dropped and the app has not updated yet, open an issue.

**I want to report a bug or suggest something.**
Open an issue on this repo or message [@xanfiend](https://instagram.com/xanfiend).

## ✦ Is it safe?

Yes. The full source code is in this repo, so you can read exactly what it does or build it yourself.

[VirusTotal scan of the APK](https://www.virustotal.com/gui/file/effaa42fd316d6aa3a2948ee1b0370b176b2a6196d6da846e717dde3ae5f55f0/summary)

The app only needs the "draw over other apps" permission to show the overlay on top of TFT. The optional Scan Now feature asks for screen capture permission to read your gold, level, and augments. All of that stays on your phone and nothing is sent anywhere. No internet permission, no data collection. If a virus scanner flags it, that is a common false alarm for self-built APKs and not a real threat.

## ✦ Changelog

### v1.33 - 2026-06-09
- Fix: Auto Scan was missing most units after the v1.31 speed update. Android only allows an accessibility service to take one screenshot per second, and the speed update made the scan tap and shoot faster than that, so the system was quietly rejecting most of the screenshots and those units never got read.
- The scan now spaces its screenshots out to stay under the one per second limit, and if a screenshot still gets rejected it waits and retries the same hex instead of skipping it. Auto Scan reads every unit again.
- The tradeoff is that the one per second screenshot limit sets a hard floor of about a second per unit, so a full board takes a little longer, but it is accurate now. A smarter version that only taps hexes with a unit on them is coming next.

### v1.32 - 2026-06-08
- Auto Scan now reads your gold and level too, not just champions. Right before it starts tapping hexes, it takes one extra screenshot of the board and reads the gold count and level number out of the corners, the same way the regular Scan Now button does.
- Those numbers are saved straight into your gold tracker and level, and shown at the top of the auto scan results once the scan finishes, so you do not have to type them in by hand.

### v1.31 - 2026-06-07
- Speed: Auto Scan is noticeably faster. The board scan taps each hex, waits for the unit popup, screenshots, and reads the name dozens of times in a row, so every small delay adds up across the whole board.
- Trimmed the per-tap timing: shorter tap, shorter wait for the popup to show up, and a shorter gap before the next tap.
- The screenshot is now shrunk down before the text reader looks at it, so there are far fewer pixels to process per unit. The champion name stays just as readable, so detection accuracy does not change.
- Removed repeated text cleanup that was running on every single name comparison, and made the visual board matching read pixels in one batch instead of one at a time.
- If you ever notice a unit getting missed, it likely means a popup did not finish appearing in time. The timing values are now in one place in the code and easy to nudge back up.

### v1.30 - 2026-06-07
- Fix: the probe dots were landing in the right place all along, the dots themselves were just drawn too big to tell. The TFT board is shown at an angle, so the back rows sit closer together on screen than the front rows. The dots had a fixed size that did not shrink for the back rows, so neighbouring dots overlapped into what looked like a tangled mesh, even though their centers were correctly placed in a clean grid.
- Dots now shrink to fit the actual gap between neighbouring points, so the back rows look as clean as the front rows, and nothing overlaps.
- This was the real cause of the crisscross look reported after both v1.28 and v1.29. The placement math in those versions was already fine, which is why changing it did not fix the look of the dots.

### v1.29 - 2026-06-07
- Fix: the v1.28 sideways stagger correction made probe dots worse on TFT Mobile, not better. It worked out a sideways shift from real measured PC board coordinates and applied it to alternating rows, but on phones that shift came out too large and turned the dots into a dense crisscross mesh instead of clean rows lined up on the hexes.
- Removed that correction and went back to plain smooth interpolation between the four corners measured during calibration. Users confirmed this lines up well.
- The 5-step calibration guide from v1.28 stays, since measuring the front-left corner directly is still more accurate than guessing it from symmetry. Only the math that places the dots between the four corners changed.
- If your dots still look slightly off, re-run TAP TO CALIBRATE in the SETUP tab and try to tap as close to the exact center of each unit as you can — small taps on the wrong spot get stretched across the whole grid.

### v1.28 - 2026-06-07
- Fix: probe dots were still landing slightly off to the side on every other row. We dug into real measured TFT board coordinates and found the board uses a staggered hex grid (the hexes are pointy-top, and every other row is shifted sideways by about half a hex width), stacked on top of the front-to-back perspective that v1.27 already handled.
- The old calibration also worked out the front-left corner using left-right symmetry, which does not hold once you account for the stagger, so that corner could be off.
- Calibration is now a 5-step guide that taps all four board corners directly: back row left, back row right, front row left, front row right, then bench. The probe grid works out the sideways stagger from those four points and applies it to each row, on top of the existing perspective spacing.
- After updating, re-run TAP TO CALIBRATE in the SETUP tab so the new 5-step guide can take real measurements of your screen.

### v1.27 - 2026-06-06
- Fix: probe dots still did not land on the hexes. The grid was generating 5 rows when a standard board has 4, so an extra row floated off the board and every row drifted out of place.
- Fix: rows were spaced evenly down the screen, but the TFT board is drawn in perspective (back rows compressed, front rows spread apart), so the middle dots fell into the gaps between hexes. The grid now uses 4 rows with perspective spacing (gaps grow toward the front) and interpolates the trapezoid left and right edges with the same curve.
- Calibration now stores the front row directly. After updating, re-run TAP TO CALIBRATE in the SETUP tab for the tightest fit.

### v1.26 - 2026-06-06
- UI redesign: 5 tabs instead of 6. GRID renamed to POOL. BOARD renamed to ODDS. AUGS and ITEMS merged into a single GUIDE tab with sub-tabs (switch between AUGMENTS and ITEMS at the top of the tab).
- SETUP tab (was SETTINGS) now shows Accessibility permission status as the first thing, with a clear card showing enabled or disabled and step-by-step instructions if it needs to be turned on.
- Scan buttons in POOL tab now sit side by side instead of stacked.
- ODDS tab shows a step-by-step guide when nothing is tracked yet instead of a blank message.
- GOLD tab shows the interest formula (1g per 10g saved, max 5g) and the streak bonus scale (2+ streak = +1g, 4+ = +2g, 6+ = +3g) inline under the relevant sections.
- Calibration guide text corrected from "3-step" to "4-step" to match the actual calibration flow.

### v1.25 - 2026-06-06
- Fix: probe dots were not landing on hexes across all rows because the TFT board is trapezoidal in screen space (the front row is wider than the back row). The previous grid assumed all rows had the same left and right edges, which was wrong.
- Calibration now collects 4 points: back-left unit (top row), back-right unit (top row), front-right unit (bottom row), then bench. The probe grid interpolates left and right edges per row between the calibrated corners so dots land on hexes from top to bottom. The front-left corner is inferred from board symmetry, so you only tap 3 board points.
- After calibrating, the settings screen shows the computed corner percentages so you can verify the values are reasonable.

### v1.24 - 2026-06-06
- Fix: tap-to-calibrate was offset to the right. The capture overlay used raw touch coordinates against full-screen metrics, but in landscape the status bar / notch inset shifted everything sideways. The capture overlay now shares the exact same full-screen window and coordinate space as the probe dots, so a tap maps 1:1 to where the dot is drawn.
- Debug aid: during tap calibration each tap draws a green crosshair where it registered, with a live readout showing the tap pixel position, the percent values, and the view size. This makes any coordinate offset immediately visible on screen.

### v1.23 - 2026-06-06
- Setup screen now shows both permission statuses with one-tap fix buttons. After an update, open the app and both the overlay permission and Accessibility service status are immediately visible with direct buttons to restore them. Android always disables the Accessibility service on update (this is an Android security policy, not a bug) - the card explains this and shows a 30-second restore path.

### v1.22 - 2026-06-06
- Fix: tap-to-calibrate probe dots were landing between board rows instead of on them. Column positions were calculated using hex centers instead of board edges (shifted inward). Row step used /4 instead of /3, placing rows 1 and 2 between actual hex rows. Both fixed - probe dots now land on hex centers after tap calibration.

### v1.21 - 2026-06-06
- Tap to calibrate: tap TAP TO CALIBRATE in Settings, then tap 3 actual units in TFT (top-left board unit, bottom-right board unit, any bench unit) to set all 5 calibration values in one go. No more slider-guessing from wrong defaults.
- After the 3 taps the probe dots appear automatically to confirm the positions are correct.
- Skip button on step 3 if you want to calibrate only the board and leave the bench at its current value.
- Screen rotation during calibration cancels safely.
- Sliders still available for fine-tuning after tap calibration.

### v1.20 - 2026-06-06
- Fix: probe dots and Auto Scan were completely outside the board in portrait mode. The board starts at ~22% screen height in portrait, not 39%.
- Scan coordinates are now stored separately for portrait and landscape orientation.
- Calibrate Scan in Settings now labels which orientation is being adjusted (PORTRAIT or LANDSCAPE) and RESET only resets the current orientation.
- Portrait defaults: board top 22%, bottom 65%, left 12%, right 88%, bench 75%.

### v1.19 - 2026-06-05
- Fix: Auto Scan was missing units in Tocker's Trials and on boards where units sit lower on screen (player board at y 50-72%).
- Scan now uses 5 probe rows instead of 4, extending bottom coverage from 65% to 72% of screen height.
- Early-stop threshold for consecutive board misses raised from 5 to 8. One fully empty probe row (7 columns) no longer aborts the scan before reaching the units below it.
- Default Board bottom calibration updated from 65% to 72%.

### v1.18 - 2026-06-05
- Fix: default probe grid was too narrow. Board left edge moved from 28% to 8% — units in the left 3 columns were never tapped. Board right extended from 70% to 88%. Front row bottom corrected from 60% to 65%. Bench moved from 72% to 80%.
- Use Settings -> Calibrate Scan -> SHOW DOTS to fine-tune for your specific device.

### v1.17 - 2026-06-05
- Calibrate Scan (Settings tab): nudge board top, bottom, left edge, right edge, and bench row by 1% increments until the probe dots land on your board hexes.
- SHOW DOTS button closes the panel and draws numbered circles over TFT — red for board probes, blue for bench probes, in scan order. Fades after 5 seconds.
- Calibration values persist across restarts. RESET returns to defaults.
- Tabs reordered to Grid, Econ, Board, Augs, Items, Settings and labelled with words instead of symbols. Tap targets are taller.
- My Board manual scan button removed from Grid tab (Auto Scan covers it). Opp Board promoted to a full-width button.

### v1.16 - 2026-06-05
- Fix: Auto Scan was detecting Leona (and other 5-letter champion names) when they were not on the board. The 80% partial-match rule let a 4-letter OCR fragment like "leon" count as a match for "Leona". Short names (under 6 letters) now require a full match.
- Debug: when the scan sees a popup but finds no champion name in it, the raw OCR text is now written to the scan log. This makes it possible to diagnose what the text reader actually saw for missed units.

### v1.15 - 2026-06-05
- Fix: Auto Scan was tapping outside the board area, hitting the Buy XP button, Refresh button, and the trait panel. Board and bench probe coordinates now use confirmed TFT Mobile hex positions.
- Board x range narrowed to 28-70% of screen width to clear the trait panel on the left and the health bar on the right.
- Bench probe moved from 80% to 72% screen height, placing it on the actual bench row instead of the shop button zone.
- All four board rows now use exact y positions. The previous estimate placed back-row probes in empty air above the board.

### v1.14 - 2026-06-05
- Fix: Auto Scan was tapping into the augment panel at the top of the screen. The board probe zone now starts lower, keeping all four board rows clear of the HUD and augment area.
- Fix: when the board had no units, all 28 board probes ran before the bench was reached because the early-stop only triggered after the first hit. Any 5 consecutive board misses now skip directly to the bench instead of stopping the whole scan.

### v1.13 - 2026-06-05
- Scan speed and accuracy pass. The text reader is now built once and reused instead of being rebuilt on every hex. Each screenshot is cropped to the unit popup band before reading, which means fewer pixels to process and no shop, bench, or trait text in view to misread. Per-hex logging was trimmed so the scan does not spend time writing logs. The tap gesture and the gap between taps were shortened. The detection zone is unchanged, so accuracy holds while a full board scan finishes noticeably faster.

### v1.12 - 2026-06-05
- Fix: Auto Scan was stopping early after tapping on an item. The scan now distinguishes between an empty hex (no popup appeared) and a non-champion popup (item description, ability text). Only truly empty hexes count toward the miss streak that stops the scan. Tapping an item is skipped without penalty so the scan continues.
- Bench probe row moved slightly lower on screen to avoid the item bench row in TFT Mobile.

### v1.11 - 2026-06-05
- Fix: Auto Scan was detecting champions that were not on the board. The template-first pass introduced in v1.10 was matching empty hex crops to saved templates with a similarity threshold that was too low, producing false positives. The popup wait was also reduced too far (250ms), so OCR could fire before the stat popup had appeared and pick up background UI text as champion names. Template-first pass removed. Popup wait restored to 350ms.
- Gap between probes kept at 50ms and the empty-hex bitmap copy eliminated from v1.10 as those are safe improvements.

### v1.10 - 2026-06-05
- Auto Scan Board speed improvements. Popup wait reduced to 250ms, gap between probes to 50ms. Template bitmaps only created when a champion is detected. Champion list cached across OCR calls.

### v1.9 - 2026-06-05
- Auto Scan Board now taps every hex automatically. Scans front row first so it finds your units sooner. Stops after 5 consecutive empty board hexes, then sweeps the 9 bench slots and stops after 3 empty bench slots in a row. Typical scan time is 8-15 seconds. Tap the sigil to stop early. Results show in the grid with star counts.
- Fix: accessibility service no longer shows as malfunctioning on Samsung/OnePlus/Xiaomi devices (was caused by a missing description string and accessibility flags in the service config).
- Fix: Scan Now was scanning the overlay panel instead of TFT when the accessibility path was used. The overlay now closes and waits 350ms before taking the screenshot.
- Fix: popup OCR now ignores the trait sidebar (left 12% of screen in landscape). Previously the Brawler/Eradicator trait list was being picked up as candidate champion names.

### v1.8 - 2026-06-03
- Auto Scan Board now uses template matching instead of OCR. My Board and Opp Board scans automatically save a portrait crop of each champion detected via the stat popup. Once templates are saved, Auto Scan compares hex crops against them to identify board units without user input. Template count shown on the Auto Scan button. Templates survive restarts and can be cleared from Settings.

### v1.7 - 2026-06-02
- Fix: debug scan now closes the overlay before taking the screenshot so it captures TFT, not the app itself. Settings tab reopens automatically after the scan so you can read the log.
- Fix: overlay hides automatically when you leave TFT and reappears when you return. Requires Accessibility service enabled.

### v1.6 - 2026-06-02
- Fix: board scan was detecting ghost champions (Lissandra on every scan regardless of board). Scan zone now covers the full screen width so the popup is found whether it appears on the left or right side of the screen.
- Fix: OCR fuzzy matching tightened. Short text fragments like "issa" or "sandra" no longer match champion names. A minimum of 5 characters is required and partial matches must cover at least 80% of the target name.
- Fix: overlay permission status now refreshes when you return from Android Settings. Previously the "not granted" card stayed even after granting permission.
- Fix: Morgana moved to 4-cost pool. She was incorrectly listed as 5-cost since the app launched. Riot moved her to 4-cost in patch 17.3.
- Occult theme added to the app UI: inverted pentagram sigil, repeating symbol background pattern, occult dividers.

### v1.5 - 2026-06-02
- Opponent board scan: tap Opp Board in the grid tab, navigate to an opponent's board in TFT, then tap each unit. The app reads the champion name and star level from the stat popup and shows the results with star counts (Jinx ★★, TwistedFate ★). Contest badges increment automatically. 30 second window. Requires Accessibility service.
- My Board scan button renamed from Board Scan to My Board to distinguish from Opp Board.
- Star level detection added to the popup OCR engine.

### v1.4 - 2026-06-01
- My board scan: tap My Board in the grid tab, then tap each unit on your board. The app reads the champion name from the stat popup and marks it in your pool automatically. 25 second scan window, vibrates once per unit detected. Requires Accessibility service.
- Bench detection: full scan now reads champion names from the bench row below the board and logs them.

### v1.3 - 2026-05-31
- Scan Now fixed: the overlay now moves aside automatically after you grant permission so TFT is on screen when the capture runs. No need to manually switch apps.
- Level and gold detection zones corrected for TFT Mobile layout (level is top-left, gold is bottom-right). Level detection now covers levels 2 and 3 as well.
- Portrait mode support added to the scan zones.
- Quick scan: hold the floating sigil for 1.5 seconds to trigger a scan without opening any tab.
- Scan shortcut added to the Economy tab header so you can fill gold without going to Settings.

### v1.2 - 2026-05-31
- Screen scan fixed: scan now runs entirely inside the permission Activity, removing the foreground service dependency that crashed the overlay on Xiaomi/MIUI devices. Tap Scan Now, grant permission, the scan runs immediately in the background, and the Settings panel reopens with the result.
- Full-resolution screen capture for better text detection accuracy
- Transparency control replaced with a smooth slider (20-100%) instead of four preset buttons
- Fixed touch inputs being blocked at low transparency values
- All tabs now update in place with no flicker when tapping buttons
- Versioned APK in releases (`tft-scryer-v1.2.apk`) alongside the stable download link
- Changelog now visible inside the app under Settings

### v1.1 - 2026-05-31
- Added Settings tab: transparency slider, haptic toggle, opening tab choice, button position reset, Scan Now button
- Added Economy tab: interest brackets, win/loss streak tracker, expected income next round, hold to repeat on gold buttons
- Added Items tab: tap two components to see the combined item, full trait breakpoints below
- Augments tab now shows S/A/B/C tier ratings and comp tags for each augment
- Dark-themed launch screen
- Overlay footer shows @xanfiend

### v1.0 - 2026-05-30
- Grid and board tabs with champion pool tracking and contest badges
- Drag the sigil to move it, drag onto X to close
- Level memory, recent champions at the top of the grid, version footer

## ✦ Roadmap

- Set data updated each patch
- Better scan accuracy: improved detection zones and augment name matching
- Opponent board scanning (no OCR path exists for opponent boards on mobile)

Open an issue or ping me with ideas.

<div align="center">

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

by **[@xanfiend](https://instagram.com/xanfiend)**

</div>
