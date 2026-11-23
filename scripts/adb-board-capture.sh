#!/usr/bin/env bash
# adb-board-capture.sh
# Captures a full-resolution TFT board screenshot from a connected Android device.
#
# Usage:
#   1. Enable USB debugging on your phone (Settings → Developer Options).
#   2. Connect via USB and accept the RSA key prompt.
#   3. Open TFT and get to a state where units are visible on the board.
#   4. Run: bash scripts/adb-board-capture.sh
#
# The PNG is saved to the project root as tft-board-<timestamp>.png.
# Send that file to Claude to tune the star-level detector.

set -euo pipefail

if ! command -v adb &>/dev/null; then
    echo "adb not found. Install Android Platform Tools:"
    echo "  macOS:   brew install android-platform-tools"
    echo "  Linux:   sudo apt install adb"
    echo "  Windows: https://developer.android.com/tools/releases/platform-tools"
    exit 1
fi

DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l | tr -d ' ')
if [[ "$DEVICES" -eq 0 ]]; then
    echo "No device connected. Enable USB debugging and try again."
    exit 1
fi
if [[ "$DEVICES" -gt 1 ]]; then
    echo "Multiple devices connected. Disconnect all but one and re-run."
    adb devices
    exit 1
fi

OUT="tft-board-$(date +%s).png"
echo "Capturing board screenshot..."
adb exec-out screencap -p > "$OUT"
SIZE=$(du -sh "$OUT" | cut -f1)
echo "Saved: $OUT  ($SIZE)"
echo ""
echo "Board should show champions on hexes with star indicators."
echo "Send $OUT to Claude to calibrate the star-level detector."
