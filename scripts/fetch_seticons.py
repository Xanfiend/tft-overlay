#!/usr/bin/env python3
"""Fetch the current set's flat 2D champion icons from CommunityDragon and
write them into app/src/main/assets/seticons/ for Planner Scan to match
against. Run by the fetch-icons workflow (workflow_dispatch); commit the
result like any other set-data update.

Mapping: champion names are read from SetData.java, normalized (lowercase,
alphanumerics only), and matched against every set in CommunityDragon's TFT
data. The set with the best name overlap wins, so the script keeps working
even when the set numbering and the in-game branding drift apart.

Per champion it saves up to two 48x48 PNGs: <Champ>.png (tileIcon) and
<Champ>_2.png (squareIcon) — the planner tile art should equal one of them.
"""

import io
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SETDATA = ROOT / "app/src/main/java/com/xanfiend/tftoverlay/SetData.java"
OUTDIR = ROOT / "app/src/main/assets/seticons"
CDRAGON = "https://raw.communitydragon.org/latest"
ICON_SIZE = 48

try:
    from PIL import Image
except ImportError:
    print("pillow is required: pip install pillow", file=sys.stderr)
    sys.exit(1)


def norm(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "tft-scryer-icon-fetch"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def setdata_champs() -> list[str]:
    src = SETDATA.read_text()
    m = re.search(r"CHAMPS\s*=\s*\{(.*?)\n\s*\};", src, re.S)
    if not m:
        sys.exit("could not find CHAMPS block in SetData.java")
    return re.findall(r'"([A-Za-z0-9]+)"', m.group(1))


def icon_url(game_path: str) -> str:
    p = game_path.lower()
    p = re.sub(r"\.(dds|tex)$", ".png", p)
    return f"{CDRAGON}/game/{p}"


def main() -> None:
    champs = setdata_champs()
    want = {norm(c): c for c in champs}
    print(f"SetData.java lists {len(champs)} champions")

    data = json.loads(fetch(f"{CDRAGON}/cdragon/tft/en_us.json"))
    sets = data.get("sets", {})
    if not sets:
        sys.exit("no sets in cdragon tft data")

    # pick the set whose champion names overlap SetData best
    best_key, best_set, best_score = None, None, -1
    for key, s in sets.items():
        names = {norm(c.get("name", "")) for c in s.get("champions", [])}
        score = len(names & set(want))
        print(f"  set {key} ({s.get('name')}): {score} name matches")
        if score > best_score:
            best_key, best_set, best_score = key, s, score

    print(f"using set {best_key} ({best_set.get('name')}) with {best_score} matches")
    if best_score == 0:
        sys.exit("no set matches any SetData champion — aborting")

    OUTDIR.mkdir(parents=True, exist_ok=True)
    saved, missed = 0, []
    by_name = {}
    for c in best_set.get("champions", []):
        by_name.setdefault(norm(c.get("name", "")), c)

    for key, champ in want.items():
        entry = by_name.get(key)
        if entry is None:
            missed.append(champ)
            continue
        variants = []
        if entry.get("tileIcon"):
            variants.append((f"{champ}.png", entry["tileIcon"]))
        if entry.get("squareIcon") and entry.get("squareIcon") != entry.get("tileIcon"):
            variants.append((f"{champ}_2.png", entry["squareIcon"]))
        if not variants and entry.get("icon"):
            variants.append((f"{champ}.png", entry["icon"]))
        ok = False
        for fname, path in variants:
            try:
                raw = fetch(icon_url(path))
                img = Image.open(io.BytesIO(raw)).convert("RGB")
                img = img.resize((ICON_SIZE, ICON_SIZE), Image.LANCZOS)
                img.save(OUTDIR / fname, "PNG")
                ok = True
            except Exception as e:
                print(f"  WARN {champ} {fname}: {e}")
        if ok:
            saved += 1
        else:
            missed.append(champ)

    print(f"saved icons for {saved}/{len(champs)} champions into {OUTDIR}")
    if missed:
        print("missing (no icon found): " + ", ".join(sorted(missed)))


if __name__ == "__main__":
    main()
