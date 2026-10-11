import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kit

CATEGORIES = ("frame", "stack", "motor", "prop", "video", "battery", "accessory")
GUI_FILL = {"motor": 14.0, "prop": 15.5}
BLOCK_PX = 16.0
ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "common/src/main/resources/assets/propwash/models/item/part_base"


def base_display():
    return json.loads((OUT.parent / "part_base.json").read_text(encoding="utf-8"))["display"]


def display(category):
    out = json.loads(json.dumps(base_display()))
    for ctx in kit.DISPLAY_CONTEXTS:
        entry = out[ctx]
        if ctx == "gui":
            target = GUI_FILL.get(category)
        else:
            target = kit.CATEGORY_TARGET_PX.get((category, ctx))
        if target is not None:
            scale = round(target / BLOCK_PX, 4)
            entry["scale"] = [scale, scale, scale]
        rotation = kit.CATEGORY_ROTATION.get((category, ctx))
        if rotation is not None:
            entry["rotation"] = list(rotation)
        offset = kit.CATEGORY_OFFSET_PX.get((category, ctx))
        if offset is not None:
            entry["translation"] = list(offset)
    return out


def model(category):
    return {
        "parent": "propwash:item/part_base",
        "display": display(category),
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description="Schreibt die Kategorie-Basen propwash:item/part_base/<kategorie> für den Sonder-Renderer propwash:part.")
    parser.add_argument("--check", action="store_true", help="nur prüfen, ob die Dateien aktuell sind")
    args = parser.parse_args(argv)
    stale = []
    for category in CATEGORIES:
        path = OUT / f"{category}.json"
        text = json.dumps(model(category), indent=2) + "\n"
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != text:
                stale.append(path.name)
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    if args.check:
        if stale:
            print("veraltet: " + ", ".join(stale))
            return 1
        print(f"{len(CATEGORIES)} Kategorie-Basen aktuell")
        return 0
    print(f"{len(CATEGORIES)} Kategorie-Basen nach {OUT.relative_to(ROOT)} geschrieben")
    return 0


if __name__ == "__main__":
    sys.exit(main())
