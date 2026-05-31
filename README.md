<div align="center">

# ⦿ TFT SCRYER

### ✦ Champion pool & reroll-odds overlay for Teamfight Tactics ✦
#### Android / mobile only

[![Download APK](https://img.shields.io/badge/⬇_DOWNLOAD_APK-C1121F?style=for-the-badge&logoColor=white)](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)

![Downloads](https://img.shields.io/github/downloads/Xanfiend/tft-overlay/total?style=flat-square&color=C1121F&label=downloads)
![Stars](https://img.shields.io/github/stars/Xanfiend/tft-overlay?style=flat-square&color=B8954A)
![Platform](https://img.shields.io/badge/platform-Android-1A1A1A?style=flat-square)

</div>

<div align="center">

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

</div>

Floating overlay for **TFT Mobile** on Android. Tracks pool state and reroll odds so you can make informed roll/save decisions during the planning phase without switching apps.

> **Note:** Android only — not for PC.

## ✦ Install

1. **[Download the APK](https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk)**
2. Open it on Android, allow installing from unknown sources.
3. Open TFT Scryer, grant the overlay permission, start the overlay.
4. Launch TFT. The floating sigil appears.

## ✦ Tabs

| Tab | What it does |
|---|---|
| **▦ Grid** | Tap a champion to mark a copy seen; tap the count to subtract. ◉ badge tracks how many players are contesting each unit. Recent champions surface at the top. |
| **≡ Board** | Copies remaining, contest pressure, and per-roll hit % at your current level for everything you're watching. Pin your carry to the top. Bench-thinning mode removes bench junk from the pool count to sharpen odds. |
| **❖ Augments** | S/A/B/C tier badge per augment and the comps it enables. Comp priorities, exclusions, armory mechanics, and fallback lines below. |
| **§ Economy** | Gold tracker with interest calculation, interest-bracket ladder (10/20/30/40/50g), win/loss streak tracking, and expected next-round income. Hold `+`/`−` to scroll quickly. |
| **⊞ Items** | Tap two components to see the combined item. Full trait breakpoint reference below. |
| **⚙ Settings** | Overlay transparency (40 / 60 / 80 / 100 %), haptic feedback toggle, opening-tab preference, button position reset. |

## ✦ Usage

- **Tap the sigil** → grid. **Long-press** → board.
- **Drag the sigil** to reposition. Drag onto **✕** to close the overlay completely.
- **Reset** between games clears pool, econ, and contest data.

## ✦ Is it safe?

Yes. The full source is in this repo, so you can read exactly what it does or build it yourself.

[VirusTotal scan of the APK](https://www.virustotal.com/gui/file/effaa42fd316d6aa3a2948ee1b0370b176b2a6196d6da846e717dde3ae5f55f0/summary)

It only asks for the "draw over other apps" permission so the overlay can show on top of TFT. No internet permission, collects nothing, reads nothing from the game. If a scanner shows a flag or two, that's a generic warning common to self-built APKs, not a real detection.

## ✦ Roadmap

- Set data kept current each patch
- Experimenting with auto-detection of board champions - hard problem on mobile, no promises

Open an issue or ping me with ideas.

<div align="center">

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

by **[@xanfiend](https://instagram.com/xanfiend)**

</div>
