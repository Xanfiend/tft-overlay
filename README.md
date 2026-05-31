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
2. Open it on Android and allow installing from unknown sources.
3. Open TFT Scryer, grant the overlay permission, and start the overlay.
4. Launch TFT. The floating sigil appears on screen.

## ✦ Tabs

| Tab | What it does |
|---|---|
| **▦ Grid** | Tap a champion to mark a copy as seen. Tap the number to remove one. The ◉ badge shows how many other players are going for that unit. Champions you recently marked show at the top. |
| **≡ Board** | Shows copies left in the pool, how contested each unit is, and your odds of hitting it per roll at your current level. Pin your carry to the top. Turn on bench-thinning to remove your bench units from the count for more accurate odds. |
| **❖ Augments** | S/A/B/C rating for every augment and the comps it works best with. Comp priorities, conflicts, armory rules, and backup options listed below. |
| **§ Economy** | Track your gold. Shows interest earned per round, the interest brackets (10/20/30/40/50g), win and loss streaks, and your expected income next round. Hold `+` or `-` to change gold quickly. |
| **⊞ Items** | Tap two components to see what item they make. Full trait breakpoints listed below. |
| **⚙ Settings** | Adjust overlay transparency (20-100%), toggle haptic feedback, choose which tab opens first, reset the button position. Tap Scan Now to read your screen and fill in your gold, level, and augments automatically. |

## ✦ Usage

- **Tap the sigil** to open the grid. **Long-press** to open the board.
- **Drag the sigil** to move it. Drag it onto **✕** to close the overlay.
- **Reset** between games to clear pool, gold, and contest data.

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

**A virus scanner flagged the APK.**
This is a common false positive for self-built APKs that are not signed by a known publisher. The full source code is in this repo. You can read it, build it yourself, or check the VirusTotal link below.

**The champion pool data is out of date.**
Pool data updates with each new version. Check the releases page for the latest APK. If a new patch just dropped and the app has not updated yet, open an issue.

**I want to report a bug or suggest something.**
Open an issue on this repo or message [@xanfiend](https://instagram.com/xanfiend).

## ✦ Is it safe?

Yes. The full source code is in this repo, so you can read exactly what it does or build it yourself.

[VirusTotal scan of the APK](https://www.virustotal.com/gui/file/effaa42fd316d6aa3a2948ee1b0370b176b2a6196d6da846e717dde3ae5f55f0/summary)

The app only needs the "draw over other apps" permission to show the overlay on top of TFT. The optional Scan Now feature asks for screen capture permission to read your gold, level, and augments. All of that stays on your phone and nothing is sent anywhere. No internet permission, no data collection. If a virus scanner flags it, that is a common false alarm for self-built APKs and not a real threat.

## ✦ Changelog

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
- Champion detection from the board (difficult on mobile, no promises)

Open an issue or ping me with ideas.

<div align="center">

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

by **[@xanfiend](https://instagram.com/xanfiend)**

</div>
