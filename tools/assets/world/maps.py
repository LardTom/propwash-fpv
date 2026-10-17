"""Bando map: flat 16x16 item icon, item model, item definition and the 8x8 map decoration.

Source: assets-src/world/pixel/maps.txt (sections "bando_map" and "bando").
Contract: docs/ASSETS.md "Welt-Assets"; ids from bando.BandoModule (item propwash:bando_map, decoration type
propwash:bando with asset id propwash:bando, read by the vanilla map_decorations atlas from textures/map/decorations).
"""

from assetlib import ASSETS, SRC, load_palette, load_pixel_sheets, save_png, write_json

NS = "propwash"
ITEM = "bando_map"
DECORATION = "bando"
SIZES = {ITEM: (16, 16), DECORATION: (8, 8)}


def sheets():
    loaded = load_pixel_sheets(SRC / "pixel" / "maps.txt", load_palette())
    if set(loaded) != set(SIZES):
        raise ValueError(f"maps.txt: erwartet {sorted(SIZES)}, gefunden {sorted(loaded)}")
    for name, size in SIZES.items():
        if loaded[name].size != size:
            raise ValueError(f"maps.txt: {name} ist {loaded[name].size}, erwartet {size}")
    return loaded


def generate():
    loaded = sheets()
    save_png(loaded[ITEM], ASSETS / "textures" / "item" / f"{ITEM}.png")
    save_png(loaded[DECORATION], ASSETS / "textures" / "map" / "decorations" / f"{DECORATION}.png")
    write_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{ITEM}"}},
               ASSETS / "models" / "item" / f"{ITEM}.json")
    write_json({"model": {"type": "minecraft:model", "model": f"{NS}:item/{ITEM}"}}, ASSETS / "items" / f"{ITEM}.json")


if __name__ == "__main__":
    generate()
    print("generated bando map")
