<div align="center">

# TFT Scryer

### Champion pool tracker & reroll-odds overlay for Teamfight Tactics

[![Download APK](https://img.shields.io/badge/⬇_DOWNLOAD_APK-C1121F?style=for-the-badge&logoColor=white)](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)

</div>

---

Scout opponent boards, track the shared champion pool, and know your real odds of hitting before you roll. A floating overlay that stays on top of TFT.

## Features

- **Fast scout grid** — tap a champ to mark it seen, long-press to subtract. Big targets, haptic feedback, made to keep up with the planning timer.
- **Live reroll odds** — per champion, by level (4–10), adjusted for how many copies the lobby has already taken.
- **Pool view** — seen and remaining counts for every unit, flagged when a champ is contested or dead.
- **Current set** roster and pool sizes built in.
- Runs offline. Only needs the "draw over other apps" permission.

## Install

1. **[Download the APK](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)**
2. Open it on Android, allow installing from unknown sources.
3. Open TFT Scryer → grant overlay permission → start the overlay.
4. Launch TFT — the floating button appears.

## Usage

- Tap the floating button to open the scout grid.
- Tap champions as you see them on opponent boards (long-press a chip to subtract).
- Toggle to **Pool** (top-right, or long-press the button) for odds and remaining counts.
- Drag the button anywhere. **Reset All** clears the pool between games.

## Updating each set

When a new set drops, edit the champion list and pool sizes in `app/src/main/java/com/xanfiend/tftoverlay/Pool.java`, then push — a fresh APK builds automatically. More features planned.

---

<div align="center">

by **@ravriks**

</div>
