#!/usr/bin/env python3
"""Generate / validate data/setdata.json — the remote set data the app pulls on
launch (see RemoteData.java) so a new TFT set works without a new APK.

Two modes:

  gen_setdata.py --validate          (default)
      Check data/setdata.json against the same rules RemoteData enforces:
      6 champ rows (index 0 + 5 cost tiers), each tier non-empty, a 6-long
      size row, and a setName. Run this before committing — a payload that
      fails here would be rejected by the app and silently keep stale data.

  gen_setdata.py --from-cdragon
      Rebuild the champ table from CommunityDragon's TFT data (same source and
      set-picking heuristic as fetch_seticons.py), preserving the existing
      setName / patch / size / gods unless you edit them. CDragon does NOT
      publish pool-bag sizes or the set's god list, so those are carried over
      from the current file — eyeball them against the patch notes.

Run --from-cdragon only where CDragon is reachable, then --validate the result.
"""

import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data/setdata.json"
SETDATA = ROOT / "app/src/main/java/com/xanfiend/tftoverlay/SetData.java"
CDRAGON = "https://raw.communitydragon.org/latest"


def norm(name) -> str:
    return re.sub(r"[^a-z0-9]", "", (name or "").lower())


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "tft-scryer-setdata-gen"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def setdata_champs() -> list[str]:
    """Flat champion list from the bundled SetData.java — used to pick the right
    CDragon set even when set numbering and in-game branding have drifted."""
    src = SETDATA.read_text()
    m = re.search(r"CHAMPS\s*=\s*\{(.*?)\n\s*\};", src, re.S)
    if not m:
        sys.exit("could not find CHAMPS block in SetData.java")
    return re.findall(r'"([A-Za-z0-9]+)"', m.group(1))


def validate(doc: dict) -> list[str]:
    """Mirror RemoteData.validate(). Returns a list of problems ([] = ok)."""
    errs = []
    if not doc.get("setName"):
        errs.append("missing setName")
    champs = doc.get("champs")
    if not isinstance(champs, list) or len(champs) != 6:
        errs.append("champs must be 6 rows (index 0 + 5 cost tiers)")
    else:
        for c in range(1, 6):
            tier = champs[c]
            if not isinstance(tier, list) or not tier:
                errs.append(f"cost-{c} tier is empty")
    size = doc.get("size")
    if not isinstance(size, list) or len(size) != 6:
        errs.append("size must be a 6-long array [unused,1c,2c,3c,4c,5c]")
    return errs


def do_validate() -> None:
    if not OUT.exists():
        sys.exit(f"{OUT} does not exist")
    doc = json.loads(OUT.read_text())
    errs = validate(doc)
    if errs:
        print("INVALID:")
        for e in errs:
            print("  - " + e)
        sys.exit(1)
    total = sum(len(t) for t in doc["champs"])
    print(f"OK: {doc['setName']} — {total} champions, sizes {doc['size'][1:]}")


def do_generate() -> None:
    prev = json.loads(OUT.read_text()) if OUT.exists() else {}
    want = {norm(c) for c in setdata_champs()}

    data = json.loads(fetch(f"{CDRAGON}/cdragon/tft/en_us.json"))
    sets = data.get("sets", {})
    if not sets:
        sys.exit("no sets in cdragon tft data")

    best_key, best_set, best_score = None, None, -1
    for key, s in sets.items():
        names = {norm(c.get("name", "")) for c in s.get("champions", [])}
        score = len(names & want)
        if score > best_score:
            best_key, best_set, best_score = key, s, score
    print(f"using cdragon set {best_key} ({best_set.get('name')}): {best_score} name matches")
    if best_score < len(want) // 2:
        sys.exit(f"weak match ({best_score}/{len(want)}) — current set likely not on CDN yet")

    # group by cost into the 6-row table (index 0 unused)
    tiers: list[list[str]] = [[] for _ in range(6)]
    for c in best_set.get("champions", []):
        cost = c.get("cost")
        name = re.sub(r"[^A-Za-z0-9]", "", c.get("name", "") or "")
        if not name or not isinstance(cost, int) or not (1 <= cost <= 5):
            continue
        tiers[cost].append(name)
    for t in tiers:
        t.sort()

    doc = {
        "version": 1,
        "setName": prev.get("setName") or best_set.get("name") or best_key,
        "patch": prev.get("patch", ""),
        # CDragon has neither bag sizes nor the god list — carry the current ones
        "size": prev.get("size", [0, 30, 25, 18, 10, 9]),
        "gods": prev.get("gods", []),
        "champs": tiers,
    }
    errs = validate(doc)
    if errs:
        print("generated doc is INVALID — not writing:")
        for e in errs:
            print("  - " + e)
        sys.exit(1)
    OUT.write_text(json.dumps(doc, indent=2) + "\n")
    total = sum(len(t) for t in tiers)
    print(f"wrote {OUT} — {total} champions across 5 tiers")
    print("NOTE: verify size[] (pool bags) and gods[] against the patch notes")


def main() -> None:
    mode = sys.argv[1] if len(sys.argv) > 1 else "--validate"
    if mode == "--from-cdragon":
        do_generate()
    elif mode == "--validate":
        do_validate()
    else:
        sys.exit("usage: gen_setdata.py [--validate | --from-cdragon]")


if __name__ == "__main__":
    main()
