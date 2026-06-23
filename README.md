<div align="center">

# ⦿ TFT SCRYER

### ✦ Champion pool & reroll-odds overlay for Teamfight Tactics ✦

*A floating overlay for TFT Mobile. Track the champion pool, reroll odds, gold, and your opponents without ever leaving the game.*

<br>

[![Download APK](https://img.shields.io/badge/⬇_DOWNLOAD_APK-C1121F?style=for-the-badge&logoColor=white)](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)

[![Build](https://img.shields.io/github/actions/workflow/status/Xanfiend/tft-overlay/build.yml?branch=main&style=flat-square&label=build&color=2E7D32)](https://github.com/Xanfiend/tft-overlay/actions)
[![Version](https://img.shields.io/badge/version-1.95-8B1A1A?style=flat-square)](https://github.com/Xanfiend/tft-overlay/releases)
[![Platform](https://img.shields.io/badge/platform-Android%207%2B-1A1A1A?style=flat-square&logo=android&logoColor=A4C639)](#-device-requirements)
[![Offline gameplay](https://img.shields.io/badge/gameplay-offline-2E7D32?style=flat-square)](#-is-it-safe)
[![No trackers](https://img.shields.io/badge/trackers-none-2E7D32?style=flat-square)](#-is-it-safe)
[![Set](https://img.shields.io/badge/Set%2017-Space%20Gods-C9A227?style=flat-square)](#)

</div>

<div align="center">

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

</div>

<div align="center">

**[Features](#-what-you-get) · [Install](#-install) · [Tabs](#-tabs) · [Usage](#-usage) · [Requirements](#-device-requirements) · [FAQ](#-faq) · [Safety](#-is-it-safe) · [Changelog](#-changelog)**

</div>

<div align="center">

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

</div>


> **Note:** Android only. Not for PC.

## ✦ What you get

<table>
<tr>
<td width="50%" valign="top">

**◇ Pool tracking**
Tap a champion to mark a copy seen, watch how many copies are left and how contested each unit is.

</td>
<td width="50%" valign="top">

**◇ Reroll odds**
Your real hit chance per roll at your current level, with your carry pinned to the top.

</td>
</tr>
<tr>
<td width="50%" valign="top">

**◇ Auto Scan**
Taps your board for you and reads every unit, star level, gold, and level straight off the screen. No typing.

</td>
<td width="50%" valign="top">

**◇ Opponent scan**
Tap through an enemy board to log their units and star levels, then see who you are contesting.

</td>
</tr>
<tr>
<td width="50%" valign="top">

**◇ Gold planner**
Live interest, the 10 to 50 gold bracket ladder, and win or loss streak income, all over the game.

</td>
<td width="50%" valign="top">

**◇ Augment and item guide**
S to C tier ratings, comp tags, the full component build chart, and trait breakpoints.

</td>
</tr>
<tr>
<td width="50%" valign="top">

**◇ Coach**
From your scanned board: the line to commit to, best-in-slot items for your carry, and your next econ/roll move.

</td>
<td width="50%" valign="top">

**◇ Positioning**
Sorts your board into front/back/flank, tells you which corner to hide your carry in, and the fundamentals that win unwinnable rounds.

</td>
</tr>
</table>

Everything runs on your phone. No accounts, no trackers, no data collection. The only time the app touches the network is the optional self-update check to GitHub (see [Auto-update](#-auto-update)); every tracking, scanning, and gameplay feature works fully offline.

## ✦ Install

1. **[Download the APK](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)**
2. Open the downloaded file. If your browser asks, tap **Open** or **Install anyway**.
3. Android will ask to allow installing from unknown sources. Tap **Settings**, enable it for your browser, then go back and tap **Install**.
4. **Play Protect warning**: Google may show a warning saying the app is not recognised. This is normal for any APK not distributed through the Play Store. Tap **Install anyway** (or **More details → Install anyway** on some devices). The app is safe. The full source code is in this repo.
5. Open TFT Scryer, grant the overlay permission, and tap **Start Overlay**.
6. Launch TFT. The floating sigil appears on screen.

> **Samsung devices**: if Install anyway is greyed out, go to Settings → Biometrics and security → Install unknown apps and enable it for your browser.

## ✦ Auto-update

Since the app is sideloaded (not on the Play Store), you have two ways to stay current:

**Built in (easiest).** TFT Scryer checks GitHub for a newer release when you open it, and there is a **Check for updates** button on the SETUP screen. If a newer version exists it offers to download and install it in one tap. The first time, Android asks you to allow "install unknown apps" for TFT Scryer. Updates install over the top with no uninstall (every release is signed with the same key). This is the only feature that uses the network, and it contacts only GitHub.

**Obtainium (keeps the app from ever touching the network).** If you would rather TFT Scryer never reach out on its own, use [Obtainium](https://github.com/ImranR98/Obtainium): add `https://github.com/Xanfiend/tft-overlay` as a source and Obtainium watches the releases and installs updates for you. (You can ignore the in-app check entirely if you go this route.)

## ✦ Tabs

| Tab | What it does |
|---|---|
| **POOL** | Tap a champion to mark a copy as seen. Tap the number to remove one. The ◉ badge shows how many other players are on that unit. Champions you recently marked show at the top. Tap **Auto Scan Board** to auto-detect all units on your board; tap **Opp Board** to scan an opponent's board with star levels. Both require Accessibility service. |
| **ODDS** | Shows copies left in the pool, how contested each unit is, and your odds of hitting per roll at your current level. Pin your carry to the top. Junk bench-thinning adjusts odds for units you are holding to block others. |
| **GUIDE** | Four sub-tabs. **COACH**: from your scanned board, the recommended comp, your carry's best-in-slot items, and your next econ/roll move. **POSITION**: sorts your board into front/back/flank, names the corner to hide your carry in, and lists the positioning fundamentals. **AUGMENTS**: S/A/B/C tier list with comp tags, priorities, exclusions, and armory rules. **ITEMS**: tap two components to see the combined item, full trait breakpoints below. |
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
| **Internet** | Only for the optional in-app update check (GitHub). All scanning, tracking, and gameplay work fully offline. |
| **Storage** | About 10 MB installed. |
| **Scan Now** | Works on Android 7.0+. On Android 14+ some devices (Xiaomi MIUI, some Samsung OneUI builds) may have issues with screen capture. The debug log in Settings will show the exact error if it fails. |
| **Board Scan / Silent Scan** | Requires the Accessibility service enabled (Android 12+ only). Go to Settings tab in the overlay, tap App settings, allow restricted settings, then go to Accessibility and turn on TFT Scryer. |
| **Tested on** | Android 10, 12, 13, 14. Should work on 7.0 and above. |

## ✦ FAQ

<details>
<summary><b>Does it work on iPhone?</b></summary><br>

No. Android only.
</details>

<details>
<summary><b>Does it work on PC?</b></summary><br>

No. It is a mobile overlay for TFT Mobile on Android.
</details>

<details>
<summary><b>The overlay does not appear over TFT.</b></summary><br>

Open TFT Scryer, tap Start Overlay, then switch to TFT. If it still does not show, go to Android Settings and make sure TFT Scryer has the "Display over other apps" permission.
</details>

<details>
<summary><b>The app crashes when I tap Scan Now.</b></summary><br>

This is a known issue on some devices (especially Xiaomi / MIUI). Update to the latest version from the releases page. If it still crashes, open Settings in the overlay after the next scan attempt and check the Debug Log card for the exact error.
</details>

<details>
<summary><b>Scan Now runs but detects nothing / wrong values.</b></summary><br>

The scan reads text from your screen using OCR. Make sure the TFT game screen is visible and not covered when you tap Scan. Bright HUD elements and high contrast help. Detection is best on a clean shop or augment screen.
</details>

<details>
<summary><b>Play Protect says the app is not recognised / blocks the install.</b></summary><br>

Tap **Install anyway** or **More details → Install anyway**. Play Protect warns about any APK not distributed through the Play Store, regardless of whether it is safe. This app collects nothing and contacts only GitHub, and only to check for and download its own updates. The full source code is in this repo. If the button is greyed out, go to Settings → Biometrics and security → Install unknown apps and enable it for your browser, then try again.
</details>

<details>
<summary><b>A virus scanner flagged the APK.</b></summary><br>

Same reason as above. It is a common false positive for APKs not signed by a Play Store publisher. Full source is here, check it yourself or see the VirusTotal link below.
</details>

<details>
<summary><b>The champion pool data is out of date.</b></summary><br>

Pool data updates with each new version. Check the releases page for the latest APK. If a new patch just dropped and the app has not updated yet, open an issue.
</details>

<details>
<summary><b>I want to report a bug or suggest something.</b></summary><br>

Open an issue on this repo or message [@xanfiend](https://instagram.com/xanfiend).
</details>

## ✦ Is it safe?

Yes. The full source code is in this repo, so you can read exactly what it does or build it yourself.

[VirusTotal scan of the APK](https://www.virustotal.com/gui/file/effaa42fd316d6aa3a2948ee1b0370b176b2a6196d6da846e717dde3ae5f55f0/summary)

The app only needs the "draw over other apps" permission to show the overlay on top of TFT. The optional Scan Now feature asks for screen capture permission to read your gold, level, and augments. All of that stays on your phone. The only network use is the optional self-update check, which contacts GitHub and nothing else — no analytics, no accounts, no data collection. If a virus scanner flags it, that is a common false alarm for self-built APKs and not a real threat.

## ✦ Changelog

### v1.99.1 · 2026-06-22
- Update-check reliability fix. The in-app updater identifies the latest version more robustly; it still downloads the same single latest build. No other change.

### v1.99 · 2026-06-22
- One-time privacy and permissions notice on first launch. It plainly states what each permission is for and confirms there are no accounts, no analytics and no tracking, and that nothing is ever sent anywhere. Re-readable any time from SETUP. Disclosure only — no data collection was added.

### v1.98 · 2026-06-22
- Passive device-integrity heads-up on SETUP: on a rooted device or an emulator it suggests installing only from the official GitHub releases. Informational only — nothing is blocked and nothing is reported; a normal phone shows nothing.

### v1.97 · 2026-06-22
- Release build hardened and slimmed down. No behavior change.

### v1.96 · 2026-06-22
- COACH now includes a roll check: your real chance of hitting your recommended carry if you roll at your current gold and level, with a clear ROLL / bank / HOLD call.

### v1.94–v1.95 · 2026-06-22
- **NEW POSITION tab** (under GUIDE). After a scan it sorts your board into a front / back / flank placement map, names the corner to hide your carry in (and to switch corners each round), and lists the positioning fundamentals that win close rounds. Covers the full Set 17 roster.

### v1.90–v1.93 · 2026-06-21
- **NEW COACH tab** (under GUIDE): after a scan it recommends a comp built around your strongest carry, that carry's best items and a one-line plan, plus a NEXT MOVE econ and tempo call from your gold, level and stage. Plus reliability and launch-screen polish.

### v1.86–v1.89 · 2026-06-21
- **NEW automatic set updates**: a new TFT set no longer needs a new app build. The current set's champion list and pool sizes are fetched from this project's GitHub on launch and cached on your phone, with the built-in data as an offline fallback. Plus launch-screen fixes.

### v1.74–v1.85 · 2026-06-17
- **NEW BUILDS tab**: tap any champion to see the items that are meta on them right now, the comp they carry and a one-line tip. Real itemizers are flagged so you know who to slam items on, and each champion shows its unit tier. Plus scanning accuracy improvements.

### v1.64–v1.73 · 2026-06-16
- **NEW one-pass board read** and one-tap auto-update. Reads your whole board quickly without tapping every unit, adds an optional in-game HUD that keeps your gold, income and gold-to-next-level visible, and lets the app update itself from this project's GitHub — the only feature that uses the network.

### v1.60–v1.63 · 2026-06-12
- **NEW auto-buy (THE HUNT)**: mark up to 5 champions you're chasing and the overlay buys them from the shop the moment they appear, so you can reroll freely and never miss your unit. A large on-screen STOP button controls it. Plus the in-game HUD.

### v1.58–v1.59 · 2026-06-11
- Redesigned around automatic scanning, plus a big feature update: a roll-down forecast on ODDS (your chance to find 1, 2 or 3 copies if you roll now), one-press reading of level, XP, gold and stage, per-opponent board memory, live trait synergies, a Realm of Gods tracker, remembered augments with tiers and a one-tap new-game reset. Economy math corrected to match real TFT.

### v1.44–v1.57 · 2026-06-11
- Game data refreshed for the current TFT patch, plus a long run of scanning reliability and visual polish: scanning is faster and more accurate across devices and screen shapes, with on-screen setup helpers and clearer status.

### v1.31–v1.43 · 2026-06-09
- Automatic scanning made faster and more reliable, with many device-specific fixes and smoother overlay animations.

### v1.9–v1.30 · 2026-06-07
- Automatic board and opponent scanning introduced and steadily refined — fewer taps, better accuracy, and on-screen calibration tools.

### v1.6–v1.8 · 2026-06-03
- Opponent board scanning, wider scan coverage, tighter name matching, and the occult app theme.

### v1.3–v1.5 · 2026-06-02
- Silent on-device scanning after a one-time setup, plus board, opponent and bench scan modes.

### v1.1–v1.2 · 2026-05-31
- Settings (transparency, haptics, start tab), an economy tab (interest, streak, expected income), the item builder, augment tiers, and the dark launch screen.

### v1.0 · 2026-05-30
- First release: champion pool tracking with contest badges, a draggable overlay, level memory and recent champions.

## ✦ Roadmap

- Set data updated each patch
- Better scan accuracy: improved detection zones and augment name matching
- Opponent board scanning (no OCR path exists for opponent boards on mobile)

Open an issue or ping me with ideas.

<div align="center">

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

Made by **[@ravriks](https://instagram.com/ravriks)** aka **@xanfiend**

</div>
