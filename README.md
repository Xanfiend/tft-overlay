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
| **▦ Grid** | Tap a champion to mark a copy as seen. Tap the number to remove one. The ◉ badge shows how many other players are going for that unit. Champions you recently marked show at the top. Tap **Board Scan** to auto-detect every unit on your board by tapping each one. |
| **≡ Board** | Shows copies left in the pool, how contested each unit is, and your odds of hitting it per roll at your current level. Pin your carry to the top. Turn on bench-thinning to remove your bench units from the count for more accurate odds. |
| **❖ Augments** | S/A/B/C rating for every augment and the comps it works best with. Comp priorities, conflicts, armory rules, and backup options listed below. |
| **§ Economy** | Track your gold. Shows interest earned per round, the interest brackets (10/20/30/40/50g), win and loss streaks, and your expected income next round. Hold `+` or `-` to change gold quickly. |
| **⊞ Items** | Tap two components to see what item they make. Full trait breakpoints listed below. |
| **⚙ Settings** | Adjust overlay transparency (20-100%), toggle haptic feedback, choose which tab opens first, reset the button position. Tap Scan Now to auto-fill gold, level, and augments. Enable Silent Scan (Accessibility service) to scan without switching apps. |

## ✦ Usage

- **Tap the sigil** to open the grid. **Long-press** to open the board. **Hold 1.5s** to trigger a scan instantly.
- **Board Scan**: tap Board Scan in the Grid tab, then tap each unit on the board one by one. The app reads the name from the stat popup and marks it automatically. Requires the Accessibility service (one-time setup in Settings).
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

### v1.4 - 2026-06-01
- Board scan mode: tap Board Scan in the grid tab, then tap each unit on the board. The app reads the champion name from the stat popup and marks it in your pool automatically. 25 second scan window, vibrates once per unit detected. Requires Accessibility service.
- Bench detection: full scan now reads champion names from the bench row below the board and logs them.
- Version bump to 1.4.

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
