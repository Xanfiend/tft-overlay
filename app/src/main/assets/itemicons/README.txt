itemicons/ — bundled 2D full-item icons for the opponent scan (Phase 2).

Drop one PNG per completed item here, named after the item with no spaces or
punctuation, e.g.:

    InfinityEdge.png
    Bloodthirster.png
    GargoyleStoneplate.png
    DragonsClaw.png

Variants of the same item may be added as <Item>_2.png, <Item>_3.png, etc.
The loader strips the _suffix and matches the remaining key (case- and
punctuation-insensitive) against the canonical names in ItemData.fullItems().

Easiest way to populate this folder (dev machine with internet):

    pip install pillow
    python scripts/fetch_itemicons.py

That reads every completed-item name from ItemData.java, pulls the matching 2D
icon from CommunityDragon, and writes it here under the right filename. Emblems
and set-specific items may be reported missing — add those by hand if needed.

Source: CommunityDragon, at dev time only (never a runtime dependency).
Icons (c) Riot Games, used under the Legal Jibber Jabber policy; this is a
free, non-commercial fan project.

Until icons are added, ItemIcons.match() returns null and the app is
unaffected — enemy units simply carry no detected items (items[] stays empty),
exactly as before Phase 2.
