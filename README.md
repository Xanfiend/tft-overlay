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

> **Note:** Android only. Not for PC.

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
| **⚙ Settings** | Overlay transparency slider (20-100%), haptic feedback toggle, opening-tab preference, button position reset. Auto-scan button requests screen-capture permission and fills gold/level/augments via on-device OCR. |

## ✦ Usage

- **Tap the sigil** → grid. **Long-press** → board.
- **Drag the sigil** to reposition. Drag onto **✕** to close the overlay completely.
- **Reset** between games clears pool, econ, and contest data.

## ✦ Is it safe?

Yes. The full source is in this repo, so you can read exactly what it does or build it yourself.

[VirusTotal scan of the APK](https://www.virustotal.com/gui/file/effaa42fd316d6aa3a2948ee1b0370b176b2a6196d6da846e717dde3ae5f55f0/summary)

It asks for the "draw over other apps" permission so the overlay can show on top of TFT. The optional auto-scan feature requests screen-capture permission to read gold, level, and augments via on-device OCR; all processing stays on your phone and nothing leaves the device. No internet permission, collects nothing, sends nothing. If a scanner shows a flag or two, that is a generic warning common to self-built APKs, not a real detection.

## ✦ Changelog

### v1.2 - 2026-05-31
- Screen scan reworked: single "Scan now" tap requests permission and scans immediately (Android 14 compatible - FGS starts in `onStartCommand` right after permission grant, before `createVirtualDisplay`); full-resolution capture; panel reopens with results after scan
- Transparency is now a slider (20-100%) replacing four preset buttons
- No-flash panel updates: all tabs refresh in-place without window flicker
- Versioned APK filename in releases (`tft-scryer-v1.2.apk`) alongside stable `tft-scryer.apk`
- In-app changelog visible in Settings tab

### v1.1 - 2026-05-31
- Settings tab: transparency control, haptic toggle, opening-tab preference, button position reset, experimental screen scan
- Economy tab: interest bracket ladder, win/loss streak tracker, expected next-round income, hold-to-repeat gold buttons
- Items tab: tap two components to see the combined item; full trait breakpoint reference
- Augments tab: S/A/B/C tier badges and comp tags for each augment
- Dark-themed launch screen; overlay footer credits @xanfiend

### v1.0 - 2026-05-30
- Grid tab: champion pool tracking with contest badges, recent champions surfaced at top
- Board tab: copies remaining, per-roll hit odds at current level, bench-thinning mode, pin carry
- Drag the sigil to reposition; drag onto X to close
- Level memory, version footer with tap-to-check-release

## ✦ Roadmap

- Set data kept current each patch
- Auto-scan accuracy improvements: zone tuning per TFT UI layout, better augment matching
- Champion board detection (hard problem on mobile, no promises)

Open an issue or ping me with ideas.

<div align="center">

`❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦`

by **[@xanfiend](https://instagram.com/xanfiend)**

</div>
