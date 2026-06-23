#!/usr/bin/env python3
"""Fetch the completed-item 2D icons from CommunityDragon into
app/src/main/assets/itemicons/ for the opponent-scan item ID (Phase 2).

The item names come straight from ItemData.java (the strings in every
`set(i, j, "Name")` call), so the script always targets exactly the items the
app knows how to combine — no separate list to keep in sync. Each name is
normalized (lowercase, alphanumerics only) and matched against CommunityDragon's
TFT item table; the icon is saved as <Name with punctuation removed>.png, which
is what ItemIcons.findItemName() expects.

Run at dev time only (mirrors fetch_seticons.py); commit the PNGs like any other
asset update. CommunityDragon is never a runtime dependency.

    pip install pillow
    python scripts/fetch_itemicons.py

Icons (c) Riot Games, used under the Legal Jibber Jabber policy — free,
non-commercial fan project.
"""

import io
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ITEMDATA = ROOT / "app/src/main/java/com/xanfiend/tftoverlay/ItemData.java"
OUTDIR = ROOT / "app/src/main/assets/itemicons"
CDRAGON = "https://raw.communitydragon.org/latest"
ICON_SIZE = 48

try:
    from PIL import Image
except ImportError:
    print("pillow is required: pip install pillow", file=sys.stderr)
    sys.exit(1)


def norm(name) -> str:
    return re.sub(r"[^a-z0-9]", "", (name or "").lower())


def fname_for(name: str) -> str:
    # ItemIcons strips punctuation and lowercases on both sides, so any
    # punctuation-free spelling works; keep the readable CamelCase form.
    return re.sub(r"[^A-Za-z0-9]", "", name) + ".png"


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "tft-scryer-icon-fetch"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def itemdata_names() -> list[str]:
    src = ITEMDATA.read_text()
    # every completed item appears as set(i, j, "Name");
    names = re.findall(r'set\(\s*\d+\s*,\s*\d+\s*,\s*"([^"]+)"\s*\)', src)
    # de-dup, preserve order
    seen, out = set(), []
    for n in names:
        if n not in seen:
            seen.add(n)
            out.append(n)
    return out


def icon_url(game_path: str) -> str:
    p = game_path.lower()
    p = re.sub(r"\.(dds|tex)$", ".png", p)
    return f"{CDRAGON}/game/{p}"


def main() -> None:
    names = itemdata_names()
    want = {norm(n): n for n in names}
    print(f"ItemData.java lists {len(names)} completed items")

    data = json.loads(fetch(f"{CDRAGON}/cdragon/tft/en_us.json"))
    items = data.get("items", [])
    if not items:
        sys.exit("no items in cdragon tft data")

    by_name = {}
    for it in items:
        k = norm(it.get("name", ""))
        if k and k not in by_name:
            by_name[k] = it

    OUTDIR.mkdir(parents=True, exist_ok=True)
    saved, missed = 0, []
    for key, display in want.items():
        entry = by_name.get(key)
        if entry is None:
            missed.append(display)
            continue
        path = entry.get("icon") or entry.get("squareIcon")
        if not path:
            missed.append(display)
            continue
        try:
            raw = fetch(icon_url(path))
            img = Image.open(io.BytesIO(raw)).convert("RGB")
            img = img.resize((ICON_SIZE, ICON_SIZE), Image.LANCZOS)
            img.save(OUTDIR / fname_for(display), "PNG")
            saved += 1
        except Exception as e:
            print(f"  WARN {display}: {e}")
            missed.append(display)

    print(f"saved icons for {saved}/{len(names)} items into {OUTDIR}")
    if missed:
        print("missing (no icon found — emblems & set-specific items often are): "
              + ", ".join(sorted(missed)))


if __name__ == "__main__":
    main()
