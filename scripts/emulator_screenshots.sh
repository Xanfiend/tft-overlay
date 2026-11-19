#!/usr/bin/env bash
# Captures README screenshots from the real APK on an Android emulator.
# Run by .github/workflows/emulator-screenshots.yml (adb already connected).
# Loads sample game data, starts the overlay, opens each tab and screencaps it.
set -u
PKG=com.xanfiend.tftoverlay
OUT=docs/screenshots
mkdir -p "$OUT"

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell settings put system accelerometer_rotation 0

shot(){ sleep 2; adb exec-out screencap -p > "$OUT/$1.png"; echo "saved $1"; }

# sample mid-game state (Set 18), opening on tab $1
prefs(){
  cat > /tmp/tft_pool.xml <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="d">Ahri|4|2;Morgana|2|1;Sett|3|0;Karma|5|1;Yorick|6|0;Teemo|3|0;Kayle|2|0;Cassiopeia|3|0;Lillia|1|1</string>
    <string name="recent">Ahri,Karma,Sett,Morgana,Yorick,Teemo</string>
    <string name="pinned">Ahri</string>
    <string name="stage_round">4-2</string>
    <string name="traits_Ahri">Blossom,Spellweaver</string>
    <string name="traits_Karma">Blossom,Spellweaver</string>
    <string name="traits_Sett">Blossom,Brawler</string>
    <string name="traits_Yorick">Blossom,Juggernaut,Summoner</string>
    <string name="traits_Morgana">Coven,Invoker</string>
    <int name="econ_gold" value="42" />
    <int name="level" value="7" />
    <int name="xp_cur" value="12" />
    <int name="xp_need" value="48" />
    <int name="econ_streak" value="3" />
    <int name="econ_hp" value="68" />
    <int name="econ_wins" value="5" />
    <int name="econ_losses" value="3" />
    <int name="cfg_start" value="2" />
    <int name="cfg_lasttab" value="$1" />
    <int name="cfg_paneltimeout" value="0" />
    <boolean name="cfg_privacy_seen" value="true" />
</map>
EOF
  adb shell am force-stop $PKG
  adb shell "run-as $PKG mkdir -p shared_prefs"
  adb shell "run-as $PKG sh -c 'cat > shared_prefs/tft_pool.xml'" < /tmp/tft_pool.xml
}

# tap the on-screen element whose text contains $1 (via a UI dump)
tap_text(){
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import re,sys
s=open('/tmp/ui.xml',encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*>',s):
    n=m.group(0)
    t=re.search(r'text="([^"]*)"',n)
    if t and sys.argv[1] in t.group(1):
        b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
        x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; else echo "not found: $1"; fi
}

# launch screen, portrait
adb shell settings put system user_rotation 0
prefs 0
adb shell am start -n $PKG/.MainActivity
sleep 4
shot launch

# find the floating sigil by its red ring (top-left area, above the HUD chips)
# in a fresh screenshot, and tap its center
tap_sigil(){
  adb exec-out screencap -p > /tmp/s.png
  local xy
  xy=$(python3 - <<'PY'
from PIL import Image
im=Image.open('/tmp/s.png').convert('RGB'); w,h=im.size; px=im.load()
xs=[];ys=[]
for y in range(0,int(h*0.7),2):
    for x in range(0,int(w*0.3),2):
        r,g,b=px[x,y]
        if r>150 and g<60 and b<70: xs.append(x); ys.append(y)
if xs: print(sum(xs)//len(xs), sum(ys)//len(ys))
PY
)
  if [ -n "$xy" ]; then echo "sigil at $xy"; adb shell input tap $xy; else echo "sigil not found"; fi
}

python3 -m pip install -q pillow 2>/dev/null || true

# overlay tabs, landscape like the game. Android Settings hides third-party
# overlays and the home screen won't rotate, so: open the panel over the home
# screen in portrait, then rotate with the app's own (dark) launch screen in
# front as the backdrop — the overlay rebuilds the open panel for landscape.
i=0
for name in pool odds guide gold setup builds; do
  adb shell settings put system user_rotation 0
  prefs $i
  adb shell am start -n $PKG/.MainActivity
  sleep 4
  tap_text "Start Overlay"          # starts the overlay, app goes to the background
  sleep 4
  tap_sigil                         # opens the panel on the tab saved in the prefs
  sleep 2
  adb shell settings put system user_rotation 1
  adb shell am start -n $PKG/.MainActivity
  sleep 5
  shot "tab-$name"
  i=$((i+1))
done
adb logcat -d -b crash > "$OUT/dbg-crash.txt" 2>&1
adb logcat -d | grep -iE "tftoverlay|AndroidRuntime|OverlayService" | tail -200 > "$OUT/dbg-logcat.txt" 2>&1
ls -la "$OUT"
