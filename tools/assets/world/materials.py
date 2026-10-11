"""Crafting materials (survival): flat 16x16 item icons, item models and item definitions.

Source: assets-src/world/pixel/materials.txt (one section per material id).
Contract: docs/ASSETS.md "Zwischenteile"; the ids are content.CraftingMaterial keys.
"""

from assetlib import ASSETS, SRC, load_palette, load_pixel_sheets, save_png, write_json

NS = "propwash"
MATERIALS = ("carbon_plate", "copper_coil", "circuit_board", "lipo_cell", "hd_chip")


def sheets():
    loaded = load_pixel_sheets(SRC / "pixel" / "materials.txt", load_palette())
    missing = [name for name in MATERIALS if name not in loaded]
    if missing:
        raise ValueError(f"materials.txt: fehlende Abschnitte {missing}")
    extra = sorted(set(loaded) - set(MATERIALS))
    if extra:
        raise ValueError(f"materials.txt: unbekannte Abschnitte {extra}")
    return loaded


def generate():
    for name, img in sheets().items():
        if img.size != (16, 16):
            raise ValueError(f"materials.txt: {name} ist {img.size}, erwartet 16x16")
        save_png(img, ASSETS / "textures" / "item" / f"{name}.png")
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}},
                   ASSETS / "models" / "item" / f"{name}.json")
        write_json({"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}},
                   ASSETS / "items" / f"{name}.json")


if __name__ == "__main__":
    generate()
    print("generated material icons")
