# TFT Overlay (Set 17) — auto-OCR build via GitHub Actions

A floating overlay for TFT: tap SCAN to auto-read an opponent board with on-device
OCR (ML Kit), or use the manual champ grid. Tracks the shared pool + shows reroll odds.

## How to build the APK (no PC needed)

### 1. Make a GitHub account
Go to github.com, sign up (free).

### 2. Create a new repository
- Tap + (top right) > New repository
- Name it: tft-overlay
- Public
- Create

### 3. Upload these files
Keep the EXACT folder structure. On github.com:
- "uploading an existing file" > drag the whole contents of this zip
- OR use the GitHub mobile app / a git client

The structure must stay:
```
.github/workflows/build.yml
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/com/xanfiend/tftoverlay/*.java
app/src/main/res/values/strings.xml
build.gradle
settings.gradle
gradle.properties
```

### 4. The build runs automatically
- Every push triggers it. Or go to the Actions tab > Build APK > Run workflow.
- Wait ~3-5 min. Green check = done.

### 5. Download your APK
- Click the finished run > scroll to Artifacts > download "tft-overlay-apk"
- Unzip it on your phone, install the APK (allow unknown sources).

### 6. Use it
- Open TFT Overlay > grant overlay permission > start overlay + screen scan (allow).
- In TFT: switch to an opponent board, TAP the sigil. A 2s countdown runs,
  then it reads the board and adds champs to the pool.
- Long-press the sigil = pool summary with reroll odds.
- The manual grid is still there if OCR misreads.

## Notes
- OCR reads on-screen TEXT. TFT champion names show on the board/bench labels.
  Accuracy depends on what text is visible — if a board has no name labels,
  use the manual grid instead.
- Set 17 roster + pool sizes (30/25/18/10/9) baked in.
- Edit Pool.java champion names if a patch changes the roster, then push again.
