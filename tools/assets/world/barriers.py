"""Drone barriers (propwash:drone_pass_barrier, propwash:drone_block_barrier): icons, block models, blockstates, items.

Contract: docs/barriers.md. Run: python3 tools/assets/world/barriers.py (needs Pillow).
Both blocks are invisible like the vanilla barrier: the block model only names the particle texture, which the marker
particle shows while a player in creative mode holds one of the two blocks. The item uses the same 16 x 16 icon.
Drone-Passable Barrier: green ring around a drone (only drones pass). Drone Barrier: red ring with a slash over a drone
(drones are stopped).
"""

from PIL import Image

from assetlib import ASSETS, hex_rgba, save_png, write_json

NS = "propwash"
SIZE = 16

RING = [
    "................",
    "...ABBBBBBBBA...",
    "..BBBBBBBBBBBC..",
    ".ABBA......ABBC.",
    ".BBA........ABC.",
    ".BB..........BC.",
    ".BB..........BC.",
    ".BB..........BC.",
    ".BB..........BC.",
    ".BB..........BC.",
    ".BB..........BC.",
    ".BBA........ABC.",
    ".ABBA......ABBC.",
    "..CBBBBBBBBBBC..",
    "...CCCCCCCCCC...",
    "................",
]

DRONE = [
    "PP....PP",
    "PPa..aPP",
    ".aKLLKa.",
    "..KKKK..",
    "..KKKK..",
    ".aKKKKa.",
    "PPa..aPP",
    "PP....PP",
]
DRONE_AT = (4, 4)

DRONE_COLOURS = {
    "P": hex_rgba("c9d1db"),
    "a": hex_rgba("6b737d"),
    "K": hex_rgba("2e3238"),
    "L": hex_rgba("ffbb00"),
}

KINDS = {
    "drone_pass_barrier": {
        "ring": {"A": hex_rgba("22a849"), "B": hex_rgba("2fd25c"), "C": hex_rgba("1a8238")},
        "slash": False,
    },
    "drone_block_barrier": {
        "ring": {"A": hex_rgba("be0101"), "B": hex_rgba("e30000"), "C": hex_rgba("b10000")},
        "slash": True,
    },
}


def inside(x, y):
    row = RING[y]
    walls = [i for i, c in enumerate(row) if c != "."]
    return bool(walls) and walls[0] < x < walls[-1] and row[x] == "."


def icon(kind):
    spec = KINDS[kind]
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y, row in enumerate(RING):
        for x, symbol in enumerate(row):
            if symbol != ".":
                img.putpixel((x, y), spec["ring"][symbol])
    dx, dy = DRONE_AT
    for y, row in enumerate(DRONE):
        for x, symbol in enumerate(row):
            if symbol != ".":
                img.putpixel((dx + x, dy + y), DRONE_COLOURS[symbol])
    if spec["slash"]:
        for y in range(SIZE):
            for x in range(SIZE):
                if x + y in (14, 15) and (inside(x, y) or RING[y][x] == "A"):
                    img.putpixel((x, y), spec["ring"]["B"])
                elif x + y == 16 and inside(x, y):
                    img.putpixel((x, y), spec["ring"]["A"])
    return img


def generate():
    for kind in KINDS:
        save_png(icon(kind), ASSETS / "textures" / "item" / f"{kind}.png")
        write_json({"textures": {"particle": f"{NS}:item/{kind}"}}, ASSETS / "models" / "block" / f"{kind}.json")
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{kind}"}},
                   ASSETS / "models" / "item" / f"{kind}.json")
        write_json({"model": {"type": "minecraft:model", "model": f"{NS}:item/{kind}"}}, ASSETS / "items" / f"{kind}.json")
        write_json({"variants": {"": {"model": f"{NS}:block/{kind}"}}}, ASSETS / "blockstates" / f"{kind}.json")


if __name__ == "__main__":
    generate()
    print("generated drone barrier assets")
