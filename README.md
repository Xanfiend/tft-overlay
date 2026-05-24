<div align="center">

# TFT Scryer

### Champion pool tracker & reroll-odds overlay for Teamfight Tactics (Android / mobile only)

[![Download APK](https://img.shields.io/badge/⬇_DOWNLOAD_APK-C1121F?style=for-the-badge&logoColor=white)](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)

![Downloads](https://img.shields.io/github/downloads/Xanfiend/tft-overlay/total?style=flat-square&color=C1121F&label=downloads)
![Stars](https://img.shields.io/github/stars/Xanfiend/tft-overlay?style=flat-square&color=B8954A)

</div>

---

A floating overlay for **TFT on Android** (the mobile app). Scout opponent boards, track the shared champion pool, and know your real odds of hitting before you roll.

> **Note:** this is an Android overlay app. It works with TFT Mobile on your phone. It is not for PC.

## Features

- **Fast scout grid.** Tap a champ to mark it seen, long-press to subtract. Big targets, haptic feedback, made to keep up with the planning timer.
- **Live reroll odds** per champion, by level (4 to 10), adjusted for how many copies the lobby has already taken.
- **Contest board** with seen and remaining counts for every unit, flagged when a champ is contested or dead.
- **Current set** roster and pool sizes built in.
- Runs offline. Only needs the "draw over other apps" permission.

## Install

1. **[Download the APK](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)**
2. Open it on Android, allow installing from unknown sources.
3. Open TFT Scryer → grant overlay permission → start the overlay.
4. Launch TFT. The floating button appears.

## Usage

You don't need to track everything. Mark the champions you're contesting or chasing, and Scryer tells you whether to keep rolling or pivot off.

- Long-press the floating button to open the grid, then tap the champs you're fighting for (long-press a chip to subtract).
- Tap the button for the contest board: each champ gets a verdict (roll, risky, or pivot) plus copies left and your odds.
- Drag the button anywhere. Reset All clears everything between games.

## Is it safe?

Yes. The full source code is in this repo, so you can read exactly what it does or build it yourself.

[VirusTotal scan of the APK](https://www.virustotal.com/gui/file/effaa42fd316d6aa3a2948ee1b0370b176b2a6196d6da846e717dde3ae5f55f0/summary)

The app only asks for the "draw over other apps" permission so the overlay can show on top of TFT. It has no internet permission, collects nothing, and reads nothing from the game. If a scanner shows a flag or two, that's a generic warning common to all unsigned APKs, not a real detection. Scan it yourself if you want to be sure.

## Roadmap

Actively working on this. Planned:
- Set data kept up to date each patch
- Quality-of-life improvements to the grid and contest board
- Features based on what people actually ask for

Also experimenting with auto-detection of board champions, but no promises. It's a hard problem on mobile and may not pan out. For now the manual tap grid is fast and 100% accurate.

Open an issue or ping me with ideas.

---

<div align="center">

by **@ravriks**

</div>
