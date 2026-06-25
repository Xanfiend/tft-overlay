<div align="center">

# ⦿ TFT SCRYER

### ✦ Champion pool & reroll-odds overlay for Teamfight Tactics ✦

*A floating overlay for TFT Mobile. Track the pool, reroll odds, gold, augments, and your opponents — without ever leaving the game.*

<br>

[![Download APK](https://img.shields.io/badge/⬇_DOWNLOAD_APK-C1121F?style=for-the-badge&logoColor=white)](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)

[![Build](https://img.shields.io/github/actions/workflow/status/Xanfiend/tft-overlay/build.yml?branch=main&style=flat-square&label=build)](https://github.com/Xanfiend/tft-overlay/actions)
[![Latest release](https://img.shields.io/github/v/release/Xanfiend/tft-overlay?style=flat-square&label=version&color=8B1A1A)](https://github.com/Xanfiend/tft-overlay/releases)
[![Platform](https://img.shields.io/badge/Android-7%2B-1A1A1A?style=flat-square&logo=android&logoColor=A4C639)](#-requirements)
[![Offline](https://img.shields.io/badge/gameplay-100%25_offline-2E7D32?style=flat-square)](#-is-it-safe)
[![Set 17](https://img.shields.io/badge/Set%2017-Space%20Gods-C9A227?style=flat-square)](#)

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

**[Features](#-what-you-get) · [Install](#-install) · [Tabs](#-tabs) · [Usage](#-usage) · [Requirements](#-requirements) · [Safety](#-is-it-safe)**

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

</div>

> **Android only — not for PC or iPhone.** Built for TFT Mobile.

## ✦ What you get

<table>
<tr>
<td width="50%" valign="top">

**◇ Pool tracking**
Mark copies seen, watch how many are left and how contested each unit is.

</td>
<td width="50%" valign="top">

**◇ Reroll odds**
Your real hit chance per roll at your level, carry pinned to the top.

</td>
</tr>
<tr>
<td width="50%" valign="top">

**◇ Auto Scan**
Taps your board and reads every unit, star level, gold, and level off the screen. No typing.

</td>
<td width="50%" valign="top">

**◇ Opponent scan**
Log an enemy board's units and stars, then see who you're contesting.

</td>
</tr>
<tr>
<td width="50%" valign="top">

**◇ Gold planner**
Live interest, the 10–50g bracket ladder, and streak income over the whole game.

</td>
<td width="50%" valign="top">

**◇ Augments & items**
S–C tier ratings, comp tags, the full component chart, and trait breakpoints.

</td>
</tr>
<tr>
<td width="50%" valign="top">

**◇ Coach**
From your scanned board: the line to commit to, best-in-slot items, and your next econ/roll move.

</td>
<td width="50%" valign="top">

**◇ Positioning**
Front/back/flank map, which corner to hide your carry in, and the fundamentals that win close rounds.

</td>
</tr>
</table>

Everything runs on your phone — no accounts, no trackers, no data collection. The only network use is the optional self-update check to GitHub. Every gameplay feature works fully offline.

## ✦ Install

1. **[Download the APK](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)**
2. Open the file. If Android blocks it, allow **install from unknown sources** for your browser, then tap **Install**.
3. **Play Protect** may warn that the app is unrecognised — normal for any non-Play-Store APK. Tap **Install anyway**.
4. Open TFT Scryer, grant the overlay permission, tap **Start Overlay**, then launch TFT. The floating sigil appears.

> **Samsung:** if *Install anyway* is greyed out, enable Settings → Biometrics and security → Install unknown apps for your browser.
>
> **Updates:** the app checks GitHub on launch and updates in one tap (same signing key, installs over the top). Prefer it never touches the network? Add the repo as a source in [Obtainium](https://github.com/ImranR98/Obtainium).

## ✦ Tabs

| Tab | What it does |
|---|---|
| **POOL** | Tap a champion to mark a copy seen; tap the number to remove one. The ◉ badge shows how many players are contesting it. **Auto Scan Board** detects every unit on your board; **Opp Board** scans an enemy board with star levels. (Scans need the Accessibility service.) |
| **ODDS** | Copies left, contest pressure, and your real per-roll hit chance at your level. Pin your carry. Bench-thinning adjusts odds for units you hold to block others. |
| **GUIDE** | **COACH** — recommended comp, carry's best-in-slot items, next econ/roll move. **POSITION** — front/back/flank map + carry corner. **OPENER** — stage-by-stage opener + item-slam priority. **AUGMENTS** — S/A/B/C tiers with comp tags. **ITEMS** — tap components to see the item; trait breakpoints below. |
| **GOLD** | Gold with live interest, the 10/20/30/40/50g ladder, win/loss streak income, and expected next-round gold. Hold +/− to change fast. |
| **SETUP** | Accessibility status + setup, Scan Now, transparency/haptics/start-tab, calibration, and the debug log. |

## ✦ Usage

- **Tap** the sigil for the grid · **long-press** for the board · **hold 1.5s** to scan instantly.
- **Auto Scan Board** taps your hexes and bench, reads each popup, and marks everything found — typically 5–15s. Known units (from past scans) are identified instantly from one screenshot. Tap the sigil to stop.
- **Opp Board**: open an enemy board in TFT, tap **Opp Board**, tap each unit — reads name + stars (e.g. `Jinx ★★`) and bumps their contest badge.
- **Drag** the sigil to move it; drop it on **✕** to close. **Reset** between games.

## ✦ Requirements

| | |
|---|---|
| **OS** | Android 7.0+ (API 24+). Scans need the Accessibility service (Android 12+). |
| **Permissions** | Draw over other apps (required) · screen capture for Scan Now (optional). |
| **Internet** | Optional update check (GitHub only). Everything else is offline. |
| **Size** | ~10 MB installed. Tested on Android 10–14. |

## ✦ Is it safe?

Yes. The full source is in this repo — read it or build it yourself. The app needs only "draw over other apps" to show the overlay; Scan Now optionally adds screen capture to read your gold/level/augments. Everything stays on your phone — no analytics, no accounts, no data collection. The only network call is the self-update check to GitHub.

A Play Protect warning or antivirus flag is a common false positive for self-signed APKs, not a real threat.

[VirusTotal scan](https://www.virustotal.com/gui/file/effaa42fd316d6aa3a2948ee1b0370b176b2a6196d6da846e717dde3ae5f55f0/summary) · Bug or idea? [Open an issue](https://github.com/Xanfiend/tft-overlay/issues) or ping [@xanfiend](https://instagram.com/xanfiend).

<div align="center">

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

Full version history on the **[Releases](https://github.com/Xanfiend/tft-overlay/releases)** page.

Made by **[@xanfiend](https://instagram.com/xanfiend)**

</div>
