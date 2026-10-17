"""Generate every Propwash world asset (assets-world module).

Run: python3 tools/assets/world/generate.py
Sources: assets-src/world/ (palette + pixel art). Outputs go to
common/src/main/resources (see docs/ASSETS.md, sections "Welt-Assets").
"""

import json
import math
import random

from PIL import Image, ImageDraw, ImageFilter

from assetlib import ASSETS, RES, SRC, load_palette, load_pixel_sheets, save_png, write_json, hex_rgba
from guikit import GUI, inset, panel, preview_field, rect, slot_well
import charger
import course
import maps
import materials
import menus
import remote
import repeater

NS = "propwash"
PAL = load_palette()


def col(symbol):
    return PAL[symbol]


def shade(rgba, factor):
    r, g, b, a = rgba
    return (max(0, min(255, round(r * factor))), max(0, min(255, round(g * factor))),
            max(0, min(255, round(b * factor))), a)


def mix(c1, c2, t):
    return tuple(round(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))


PIXEL = {}
for sheet in ("slots", "items", "block", "font"):
    PIXEL.update(load_pixel_sheets(SRC / "pixel" / f"{sheet}.txt", PAL))


def tex_path(kind, name):
    return ASSETS / "textures" / kind / f"{name}.png"


def item_def(item_id, model):
    write_json({"model": {"type": "minecraft:model", "model": model}}, ASSETS / "items" / f"{item_id}.json")


def flat_item_model(name, parent="minecraft:item/generated"):
    write_json({"parent": parent, "textures": {"layer0": f"{NS}:item/{name}"}}, ASSETS / "models" / "item" / f"{name}.json")


WORKBENCH_FACES = ("top", "front", "side", "bottom")


def gen_workbench():
    for face in WORKBENCH_FACES:
        save_png(PIXEL[f"drone_workbench_{face}"], tex_path("block", f"drone_workbench_{face}"))
    tex = {
        "particle": f"{NS}:block/drone_workbench_front",
        "down": f"{NS}:block/drone_workbench_bottom",
        "up": f"{NS}:block/drone_workbench_top",
        "north": f"{NS}:block/drone_workbench_front",
        "south": f"{NS}:block/drone_workbench_front",
        "east": f"{NS}:block/drone_workbench_side",
        "west": f"{NS}:block/drone_workbench_side",
    }
    write_json({"parent": "minecraft:block/cube", "textures": tex}, ASSETS / "models" / "block" / "drone_workbench.json")
    write_json({"variants": {"": {"model": f"{NS}:block/drone_workbench"}}}, ASSETS / "blockstates" / "drone_workbench.json")
    item_def("drone_workbench", f"{NS}:block/drone_workbench")


GOGGLES = ("analog", "digital", "creative")

GOGGLE_TEX = 64
GOGGLE_TPX = 2
GOGGLE_UNIT = 1.6
FACES = ("north", "south", "east", "west", "up", "down")

STRAP_Y = (3.25, 4.75)
STRAP_INNER = 4.5
STRAP_OUTER = 5.0


def goggle_colours(kind):
    if kind == "digital":
        return {
            "body": col("W"), "light": col("H"), "dark": col("w"), "edge": col("S"),
            "plate": col("k"), "lens_hi": col("L"), "lens": col("y"), "lens_dark": col("b"),
            "accent": col("Y"), "accent_dark": col("y"), "strap": col("g"), "strap_hi": col("G"), "strap_dark": col("c"),
            "metal": col("s"), "cap": col("W"), "cap_dark": col("w"), "led": col("Y"),
        }
    if kind == "creative":
        return {
            "body": col("p"), "light": col("P"), "dark": hex_rgba("4f1d80"), "edge": col("K"),
            "plate": col("k"), "lens_hi": col("L"), "lens": col("M"), "lens_dark": col("m"),
            "accent": col("A"), "accent_dark": col("a"), "strap": col("p"), "strap_hi": col("P"), "strap_dark": hex_rgba("4f1d80"),
            "metal": col("A"), "cap": col("M"), "cap_dark": col("m"), "led": col("N"),
        }
    return {
        "body": col("C"), "light": col("G"), "dark": col("c"), "edge": col("K"),
        "plate": col("k"), "lens_hi": col("L"), "lens": col("l"), "lens_dark": col("b"),
        "accent": col("A"), "accent_dark": col("a"), "strap": col("k"), "strap_hi": col("c"), "strap_dark": col("K"),
        "metal": col("G"), "cap": col("A"), "cap_dark": col("o"), "led": col("R"),
    }


def strap_parts():
    y0, y1 = STRAP_Y
    return [
        ("strap_right", (STRAP_INNER, y0, -4.5, STRAP_OUTER, y1, STRAP_INNER), ("north", "east", "up", "down"), "strap"),
        ("strap_left", (-STRAP_OUTER, y0, -4.5, -STRAP_INNER, y1, STRAP_INNER), ("north", "west", "up", "down"), "strap"),
        ("strap_back", (-STRAP_OUTER, y0, STRAP_INNER, STRAP_OUTER, y1, STRAP_OUTER), ("south", "east", "west", "up", "down"), "strap_back"),
    ]


def goggle_parts(kind):
    visor_faces = ("north", "east", "west", "up", "down")
    if kind == "digital":
        parts = [
            ("visor", (-5.0, 2.0, -7.5, 5.0, 6.0, -4.5), visor_faces, "visor"),
            ("antenna_left", (-4.75, 6.0, -6.25, -3.75, 8.5, -5.75), ("north", "south", "east", "west", "up"), "paddle"),
            ("antenna_right", (3.75, 6.0, -6.25, 4.75, 8.5, -5.75), ("north", "south", "east", "west", "up"), "paddle"),
        ]
    elif kind == "creative":
        parts = [
            ("visor", (-4.75, 2.5, -7.0, 4.75, 6.0, -4.5), visor_faces, "visor"),
            ("receiver", (-2.0, 6.0, -6.5, 2.0, 7.0, -4.5), ("north", "east", "west", "up"), "receiver"),
            ("mast_left", (-1.75, 7.0, -5.75, -1.25, 9.5, -5.25), ("north", "south", "east", "west"), "mast"),
            ("mast_right", (1.25, 7.0, -5.75, 1.75, 9.5, -5.25), ("north", "south", "east", "west"), "mast"),
            ("cap_left", (-2.25, 9.5, -6.25, -0.75, 10.0, -4.75), FACES, "cap"),
            ("cap_right", (0.75, 9.5, -6.25, 2.25, 10.0, -4.75), FACES, "cap"),
        ]
    else:
        parts = [
            ("visor", (-4.75, 2.5, -7.0, 4.75, 6.0, -4.5), visor_faces, "visor"),
            ("receiver", (1.0, 6.0, -6.5, 4.0, 7.0, -4.5), ("north", "east", "west", "up"), "receiver"),
            ("mast", (2.25, 7.0, -5.75, 2.75, 10.0, -5.25), ("north", "south", "east", "west"), "mast"),
            ("cap", (1.5, 10.0, -6.5, 3.5, 10.5, -4.5), FACES, "cap"),
        ]
    return parts + strap_parts()


def face_size(box, face):
    x0, y0, z0, x1, y1, z1 = box
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    w, h = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]
    tw, th = w * GOGGLE_TPX, h * GOGGLE_TPX
    if abs(tw - round(tw)) > 1e-6 or abs(th - round(th)) > 1e-6:
        raise ValueError(f"Fläche {face} von {box} nicht auf Texel ausgerichtet")
    return int(round(tw)), int(round(th))


def goggle_model_px(x, y, z):
    u = GOGGLE_UNIT
    return [round(8 + x * u, 4), round(8 + (y - 4.0) * u, 4), round(8 + z * u, 4)]


def paint_visor(c, kind, face, w, h):
    img = Image.new("RGBA", (w, h), c["body"])
    px = img.load()
    if face == "north":
        for x in range(w):
            px[x, 0] = c["edge"]
            px[x, h - 1] = c["edge"]
        for y in range(h):
            px[0, y] = c["edge"]
            px[w - 1, y] = c["edge"]
        for x in range(1, w - 1):
            px[x, 1] = c["light"]
        if kind == "digital":
            for y in range(2, h - 1):
                for x in range(1, w - 1):
                    px[x, y] = c["plate"]
            lens_w = (w - 6) // 2
            for side, x0 in (("l", 2), ("r", w - 2 - lens_w)):
                for y in range(3, h - 2):
                    for x in range(x0, x0 + lens_w):
                        t = (y - 3) / max(1, h - 6)
                        px[x, y] = c["lens_hi"] if (y == 3 and x == x0 + 1) else (c["lens"] if t < 0.5 else c["lens_dark"])
            px[w // 2 - 1, h - 2] = c["light"]
            px[w // 2, h - 2] = c["light"]
            px[w - 2, 2] = c["led"]
        else:
            for x in range(1, w - 1):
                px[x, h - 2] = c["accent"]
            px[1, h - 2] = c["accent_dark"]
            px[w - 2, h - 2] = c["accent_dark"]
            gap = 3 if (w - 2) % 2 == 1 else 2
            lens_w = (w - 4 - gap) // 2
            for x0 in (2, w - 2 - lens_w):
                for y in range(2, h - 2):
                    for x in range(x0, x0 + lens_w):
                        row = y - 2
                        rows = h - 4
                        if kind == "creative":
                            colour = (c["lens_hi"], col("P"), c["lens"], c["lens_dark"])[min(3, row * 4 // max(1, rows))]
                        else:
                            colour = c["lens"] if row < rows - 1 else c["lens_dark"]
                        px[x, y] = colour
                px[x0, 2] = c["lens_hi"]
                px[x0 + 1, 2] = c["lens_hi"]
            if kind == "analog":
                px[w // 2, 3] = c["led"]
    elif face in ("east", "west"):
        front_right = face == "east"
        for x in range(w):
            px[x, 0] = c["light"]
            px[x, h - 1] = c["dark"]
        back_col = 0 if front_right else w - 1
        front_col = w - 1 if front_right else 0
        for y in range(h):
            px[back_col, y] = c["dark"]
            px[front_col, y] = c["edge"]
        mid = h // 2
        px[w // 2, mid] = c["accent"]
        px[w // 2, mid - 1] = c["accent_dark"] if kind != "digital" else c["dark"]
    elif face == "up":
        for x in range(w):
            px[x, 0] = c["edge"]
            px[x, 1] = c["light"]
            px[x, h - 1] = c["dark"]
        for x in range(3, w - 3, 3):
            for y in range(2, h - 1):
                px[x, y] = c["dark"]
    elif face == "down":
        for y in range(h):
            for x in range(w):
                px[x, y] = c["dark"]
        for x in range(w):
            px[x, h - 1] = c["edge"]
        for x in range(w // 2 - 2, w // 2 + 2):
            for y in range(0, max(1, h - 2)):
                px[x, y] = c["edge"]
    return img


def paint_strap(c, kind, face, w, h, back=False):
    img = Image.new("RGBA", (w, h), c["strap"])
    px = img.load()
    if w == 1 or h == 1 or face in ("up", "down"):
        for y in range(h):
            for x in range(w):
                px[x, y] = c["strap_dark"]
        return img
    for x in range(w):
        px[x, 0] = c["strap_hi"]
        px[x, h - 1] = c["strap_dark"]
        if x % 4 == 3 and h > 2:
            px[x, 1] = c["strap_dark"]
    if back and face == "south":
        for x in range(w // 2 - 2, w // 2 + 2):
            for y in range(h):
                px[x, y] = c["metal"]
        px[w // 2 - 2, 0] = c["light"]
    elif face in ("east", "west") and w > 8:
        front_right = face == "east"
        x0 = w - 5 if front_right else 1
        for x in range(x0, x0 + 4):
            px[x, 1] = c["accent"] if kind != "digital" else c["strap_hi"]
    return img


def paint_block(base, light, dark, edge, w, h, face, label=None):
    img = Image.new("RGBA", (w, h), base)
    px = img.load()
    if face == "up":
        for y in range(h):
            for x in range(w):
                px[x, y] = light
        return img
    if face == "down":
        for y in range(h):
            for x in range(w):
                px[x, y] = dark
        return img
    if h > 1:
        for x in range(w):
            px[x, 0] = light
        for x in range(w):
            px[x, h - 1] = dark
    if w > 2 and h > 1 and edge is not None:
        px[0, h - 1] = edge
        px[w - 1, h - 1] = edge
    if label is not None and w > 4:
        for x in range(1, w - 1):
            px[x, h // 2 if h > 1 else 0] = label
    return img


def paint_part(c, kind, painter, face, w, h):
    if painter == "visor":
        return paint_visor(c, kind, face, w, h)
    if painter == "strap":
        return paint_strap(c, kind, face, w, h)
    if painter == "strap_back":
        return paint_strap(c, kind, face, w, h, back=True)
    if painter == "receiver":
        label = c["accent"] if face == "north" else None
        return paint_block(c["metal"], shade(c["metal"], 1.2), shade(c["metal"], 0.7), c["edge"], w, h, face, label)
    if painter == "mast":
        colour = c["accent"] if kind == "creative" else c["edge"]
        return paint_block(colour, shade(colour, 1.25), shade(colour, 0.8), None, w, h, face)
    if painter == "cap":
        return paint_block(c["cap"], shade(c["cap"], 1.15), c["cap_dark"], c["cap_dark"], w, h, face)
    if painter == "paddle":
        img = paint_block(c["cap"], c["light"], c["cap_dark"], c["edge"], w, h, face)
        if face in ("north", "south") and h > 3:
            img.putpixel((w // 2, 1), c["led"] if face == "north" else c["dark"])
        return img
    raise ValueError(painter)


def pack_faces(requests):
    placed = {}
    order = sorted(requests, key=lambda r: (-r[2], -r[1], r[0]))
    x, y, row_h = 0, 0, 0
    for key, w, h in order:
        if x + w > GOGGLE_TEX:
            x, y, row_h = 0, y + row_h, 0
        if y + h > GOGGLE_TEX:
            raise ValueError("Brillen-Textur zu klein")
        placed[key] = (x, y, w, h)
        x += w
        row_h = max(row_h, h)
    return placed


def worn_goggles(kind):
    c = goggle_colours(kind)
    parts = goggle_parts(kind)
    requests = []
    for name, box, faces, _ in parts:
        for face in faces:
            w, h = face_size(box, face)
            requests.append(((name, face), w, h))
    placed = pack_faces(requests)
    tex = Image.new("RGBA", (GOGGLE_TEX, GOGGLE_TEX), (0, 0, 0, 0))
    unit = GOGGLE_TEX / 16.0
    elements = []
    texture_ref = f"{NS}:item/goggles_{kind}_worn"
    for name, box, faces, painter in parts:
        x0, y0, z0, x1, y1, z1 = box
        element_faces = {}
        for face in faces:
            tx, ty, w, h = placed[(name, face)]
            tex.paste(paint_part(c, kind, painter, face, w, h), (tx, ty))
            element_faces[face] = {"uv": [round(tx / unit, 4), round(ty / unit, 4), round((tx + w) / unit, 4), round((ty + h) / unit, 4)],
                                   "texture": "#goggles"}
        elements.append({"name": name, "from": goggle_model_px(x0, y0, z0), "to": goggle_model_px(x1, y1, z1), "faces": element_faces})
    model = {
        "texture_size": [GOGGLE_TEX, GOGGLE_TEX],
        "textures": {"goggles": texture_ref, "particle": f"{NS}:item/goggles_{kind}"},
        "elements": elements,
        "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
    }
    return model, tex


def gen_goggles():
    for kind in GOGGLES:
        name = f"goggles_{kind}"
        save_png(PIXEL[name], tex_path("item", name))
        flat_item_model(name)
        model, tex = worn_goggles(kind)
        save_png(tex, tex_path("item", f"{name}_worn"))
        write_json(model, ASSETS / "models" / "item" / f"{name}_worn.json")
        write_json({"model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": [{"when": ["head"], "model": {"type": "minecraft:model", "model": f"{NS}:item/{name}_worn"}}],
            "fallback": {"type": "minecraft:model", "model": f"{NS}:item/{name}"},
        }}, ASSETS / "items" / f"{name}.json")


GATE_PX_PER_BLOCK = 8
GATE_FRAME = 0.5
GATE_DEPTH = 0.5
GATE_CORNER_DEPTH = 0.625
GATE_FOOT_DEPTH = 1.0
GATE_FOOT_HEIGHT = 0.125
GATE_FOOT_INSET = 0.0625
GATE_LIGHT_WIDTH = 0.25
GATE_LIGHT_INSET = 0.125
GATE_LIGHT_THICKNESS = 0.03125
GATE_TEE_GROW = 0.03125
GATE_BASES = ("bar", "legs", "stacked", "mount")
GATE_HANG_DROP = 1.0
GATE_HANGER_ROD = 0.125
GATE_HANGER_CLAMP = (0.3125, 0.125, 0.375)
GATE_HANGER_PLATE = (0.5, 0.0625, 0.5)
GATE_PLATE = (0.5, 0.125, 1.0)
GATE_BOLT = 0.125
GATES = {"small": (3, 3), "medium": (4, 4), "large": (6, 4)}
GATE_BANNER_HEIGHT = 1.0
GATE_BANNER_DEPTH = 0.125
GATE_BANNER_CAP = 0.5
CHECK_DARK = (0x17, 0x19, 0x1d, 255)
CHECK_LIGHT = (0xec, 0xee, 0xf1, 255)
TUBE_SHADE = (0.72, 0.86, 1.0, 1.08, 1.08, 1.0, 0.86, 0.72)
BANNER_TEX = 32
BANNER_REGIONS = {
    "cap": (0, 0, 8, 16),
    "panel": (8, 0, 24, 16),
    "edge_v": (24, 0, 26, 16),
    "edge_h": (0, 16, 16, 18),
    "edge_cap": (0, 18, 8, 20),
}

FRAME_TEX = 64
LIGHT_TEX = 32
FRAME_REGIONS = {
    "tube_v": (0, 0, 8, 16),
    "front_v": (8, 0, 16, 16),
    "tube_h": (16, 0, 32, 8),
    "front_h": (16, 8, 32, 16),
    "conn_sq": (32, 0, 40, 8),
    "conn_ew": (40, 0, 50, 8),
    "conn_ud": (32, 8, 40, 18),
    "conn_cap": (50, 0, 60, 10),
    "foot_top": (0, 16, 6, 32),
    "foot_ns": (8, 16, 14, 18),
    "foot_ew": (16, 16, 32, 18),
}
LIGHT_REGIONS = {
    "led_v": (0, 0, 2, 16),
    "led_h": (4, 0, 20, 2),
    "edge": (0, 20, 2, 22),
}


def gen_frame_texture():
    rnd = random.Random(7)
    img = Image.new("RGBA", (FRAME_TEX, FRAME_TEX), (0, 0, 0, 0))
    across = [col("k"), col("c"), col("C"), col("g"), col("g"), col("C"), col("c"), col("k")]

    tube = Image.new("RGBA", (8, 16))
    for y in range(16):
        for x in range(8):
            base = across[x]
            if y == 0:
                base = shade(base, 1.18)
            elif y == 15:
                base = shade(base, 0.62)
            elif y in (7, 8) and 2 <= x <= 5:
                base = shade(base, 0.86)
            tube.putpixel((x, y), shade(base, 1.0 + rnd.uniform(-0.05, 0.05)))

    carbon_a = hex_rgba("22262c")
    carbon_b = hex_rgba("2d323a")
    front = Image.new("RGBA", (8, 16))
    for y in range(16):
        for x in range(8):
            twill = ((x // 2) + (y // 2)) % 2 == 0
            base = carbon_b if twill else carbon_a
            if x in (0, 7):
                base = shade(base, 0.7)
            if y == 0:
                base = shade(base, 1.2)
            elif y == 15:
                base = shade(base, 0.6)
            front.putpixel((x, y), base)

    def put(region, patch):
        x0, y0, x1, y1 = FRAME_REGIONS[region]
        img.paste(patch.resize((x1 - x0, y1 - y0), Image.NEAREST) if patch.size != (x1 - x0, y1 - y0) else patch, (x0, y0))

    put("tube_v", tube)
    put("front_v", front)
    put("tube_h", tube.transpose(Image.ROTATE_90))
    put("front_h", front.transpose(Image.ROTATE_90))

    def connector(w, h):
        patch = Image.new("RGBA", (w, h))
        light = col("s")
        for y in range(h):
            for x in range(w):
                c = col("G")
                if x == 0 or y == 0:
                    c = light
                if x == w - 1 or y == h - 1:
                    c = col("k")
                patch.putpixel((x, y), c)
        for y in range(2, h - 2):
            for x in range(2, w - 2):
                patch.putpixel((x, y), col("g"))
        for x in range(2, w - 2):
            patch.putpixel((x, 2), col("c"))
        for y in range(2, h - 2):
            patch.putpixel((2, y), col("c"))
        for bx, by in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            patch.putpixel((bx, by), col("S"))
        return patch

    put("conn_sq", connector(8, 8))
    put("conn_ew", connector(10, 8))
    put("conn_ud", connector(8, 10))
    put("conn_cap", connector(10, 10))

    foot_top = Image.new("RGBA", (6, 16))
    for y in range(16):
        for x in range(6):
            stripe = ((x + y) // 2) % 2 == 0
            c = col("A") if stripe else col("k")
            if x in (0, 5) or y in (0, 15):
                c = col("s")
            foot_top.putpixel((x, y), c)
    put("foot_top", foot_top)
    put("foot_ns", Image.new("RGBA", (6, 2), col("g")))
    put("foot_ew", Image.new("RGBA", (16, 2), col("g")))
    save_png(img, tex_path("item", "gate/frame"))
    return img


def checker(width, height, cell, phase=0):
    patch = Image.new("RGBA", (width, height))
    for y in range(height):
        for x in range(width):
            dark = ((x // cell) + (y // cell) + phase) % 2 == 0
            patch.putpixel((x, y), CHECK_DARK if dark else CHECK_LIGHT)
    return patch


def gen_start_frame_texture(frame):
    img = frame.copy()
    tube = checker(8, 16, 2)
    for y in range(16):
        for x in range(8):
            factor = TUBE_SHADE[x]
            if y == 0:
                factor *= 1.12
            elif y == 15:
                factor *= 0.7
            tube.putpixel((x, y), shade(tube.getpixel((x, y)), factor))
    front = checker(8, 16, 2, 1)
    for y in range(16):
        for x in range(8):
            if x in (0, 7) or y == 15:
                front.putpixel((x, y), shade(front.getpixel((x, y)), 0.78))

    def put(region, patch):
        x0, y0, x1, y1 = FRAME_REGIONS[region]
        img.paste(patch, (x0, y0))

    put("tube_v", tube)
    put("front_v", front)
    put("tube_h", tube.transpose(Image.ROTATE_90))
    put("front_h", front.transpose(Image.ROTATE_90))
    save_png(img, tex_path("item", "gate/start_frame"))


def gen_banner_texture():
    img = Image.new("RGBA", (BANNER_TEX, BANNER_TEX), (0, 0, 0, 0))

    def put(region, patch):
        x0, y0, x1, y1 = BANNER_REGIONS[region]
        img.paste(patch, (x0, y0))

    cap = checker(8, 16, 2)
    for y in range(16):
        for x in range(8):
            if y in (0, 15) or x in (0, 7):
                cap.putpixel((x, y), col("C") if y == 0 else col("k"))
    put("cap", cap)
    panel = Image.new("RGBA", (16, 16))
    carbon_a = hex_rgba("1c2026")
    carbon_b = hex_rgba("23282f")
    for y in range(16):
        for x in range(16):
            if y in (1, 2, 13, 14):
                c = CHECK_DARK if ((x // 2) + (y - 1) // 2) % 2 == 0 else CHECK_LIGHT
            elif y in (0, 15):
                c = col("C") if y == 0 else col("k")
            elif y in (3, 12):
                c = hex_rgba("3a4049")
            else:
                c = carbon_b if ((x // 2) + (y // 2)) % 2 == 0 else carbon_a
            panel.putpixel((x, y), c)
    put("panel", panel)
    put("edge_v", Image.new("RGBA", (2, 16), col("C")))
    put("edge_h", Image.new("RGBA", (16, 2), col("C")))
    put("edge_cap", Image.new("RGBA", (8, 2), col("g")))
    save_png(img, tex_path("item", "gate/banner"))


def led_value(position):
    return (0xF2, 0xFF, 0xF2, 0xC2)[position % 4]


def gen_light_texture():
    img = Image.new("RGBA", (LIGHT_TEX, LIGHT_TEX), (0xE0, 0xE0, 0xE0, 255))
    x0, y0, x1, y1 = LIGHT_REGIONS["led_v"]
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = led_value(y - y0)
            img.putpixel((x, y), (v, v, v, 255))
    x0, y0, x1, y1 = LIGHT_REGIONS["led_h"]
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = led_value(x - x0)
            img.putpixel((x, y), (v, v, v, 255))
    x0, y0, x1, y1 = LIGHT_REGIONS["edge"]
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), (0xD0, 0xD0, 0xD0, 255))
    save_png(img, tex_path("item", "gate/lights"))


def uv_of(region, regions, size, flip_u=False, flip_v=False):
    x0, y0, x1, y1 = regions[region]
    unit = size / 16.0
    u0, v0, u1, v1 = x0 / unit, y0 / unit, x1 / unit, y1 / unit
    if flip_u:
        u0, u1 = u1, u0
    if flip_v:
        v0, v1 = v1, v0
    return [round(u0, 4), round(v0, 4), round(u1, 4), round(v1, 4)]


def to_px(x, y, z):
    s = GATE_PX_PER_BLOCK
    return [round(8 + x * s, 4), round(y * s, 4), round(8 + z * s, 4)]


def element(box, faces, name=None, emissive=False):
    x0, y0, z0, x1, y1, z1 = box
    out = {"from": to_px(x0, y0, z0), "to": to_px(x1, y1, z1), "faces": faces}
    if name:
        out = {"name": name, **out}
    if emissive:
        out["light_emission"] = 15
    return out


def banner_face(region):
    return {"uv": uv_of(region, BANNER_REGIONS, BANNER_TEX), "texture": "#banner"}


def gate_banner_elements(width):
    half = width / 2.0
    cap = GATE_BANNER_CAP
    h = GATE_BANNER_HEIGHT
    d = GATE_BANNER_DEPTH / 2.0
    elements = []
    for side, (x0, x1) in (("left", (-half, -half + cap)), ("right", (half - cap, half))):
        faces = {
            "north": banner_face("cap"), "south": banner_face("cap"),
            "east": banner_face("edge_v"), "west": banner_face("edge_v"),
            "up": banner_face("edge_cap"), "down": banner_face("edge_cap"),
        }
        elements.append(element((x0, 0.0, -d, x1, h, d), faces, f"banner_cap_{side}"))
    x = -half + cap
    index = 0
    while x < half - cap - 1e-9:
        faces = {
            "north": banner_face("panel"), "south": banner_face("panel"),
            "up": banner_face("edge_h"), "down": banner_face("edge_h"),
        }
        elements.append(element((x, 0.0, -d, x + 1.0, h, d), faces, f"banner_panel_{index}"))
        x += 1.0
        index += 1
    return elements


def frame_face(region, flip_u=False):
    return {"uv": uv_of(region, FRAME_REGIONS, FRAME_TEX, flip_u=flip_u), "texture": "#frame"}


def light_face(region):
    return {"uv": uv_of(region, LIGHT_REGIONS, LIGHT_TEX), "texture": "#lights", "tintindex": 0}


def gate_layout(width, height):
    half = width / 2.0
    inner = half - GATE_FRAME
    return half, inner


def gate_variant_suffix(base):
    return "" if base == "bar" else f"_{base}"


def gate_frame_elements(width, height, base="bar"):
    half, inner = gate_layout(width, height)
    t = GATE_FRAME
    d = GATE_DEPTH / 2.0
    dc = GATE_CORNER_DEPTH / 2.0
    elements = []
    for side, (px0, px1) in (("left", (-half, -inner)), ("right", (inner, half))):
        for level, (py0, py1) in (("bottom", (0.0, t)), ("top", (height - t, height))):
            if level == "bottom" and base == "stacked":
                g = GATE_TEE_GROW
                faces = {
                    "north": frame_face("conn_sq"), "south": frame_face("conn_sq"),
                    "east": frame_face("conn_ew"), "west": frame_face("conn_ew"),
                    "up": frame_face("conn_ud"), "down": frame_face("conn_ud"),
                }
                tx0, tx1 = (px0 + g / 2, px1 + g) if side == "left" else (px0 - g, px1 - g / 2)
                elements.append(element((tx0, py0 - g, -dc - g, tx1, py1 + g, dc + g), faces, f"tee_{side}"))
                continue
            faces = {
                "north": frame_face("conn_sq"), "south": frame_face("conn_sq"),
                "east": frame_face("conn_ew"), "west": frame_face("conn_ew"),
                "up": frame_face("conn_ud"), "down": frame_face("conn_ud"),
            }
            elements.append(element((px0, py0, -dc, px1, py1, dc), faces, f"corner_{level}_{side}"))
        y = t
        index = 0
        qx0, qx1 = (px0 + GATE_LIGHT_THICKNESS, px1) if side == "left" else (px0, px1 - GATE_LIGHT_THICKNESS)
        while y < height - t - 1e-9:
            faces = {
                "north": frame_face("front_v"), "south": frame_face("front_v"),
                "east": frame_face("tube_v"), "west": frame_face("tube_v"),
            }
            elements.append(element((qx0, y, -d, qx1, y + 1.0, d), faces, f"post_{side}_{index}"))
            y += 1.0
            index += 1
    for level, (py0, py1) in (("bottom", (0.0, t)), ("top", (height - t, height - GATE_LIGHT_THICKNESS))):
        if level == "bottom" and base not in ("bar", "mount"):
            continue
        x = -inner
        index = 0
        while x < inner - 1e-9:
            faces = {
                "north": frame_face("front_h"), "south": frame_face("front_h"),
                "up": frame_face("tube_h"), "down": frame_face("tube_h"),
            }
            elements.append(element((x, py0, -d, x + 1.0, py1, d), faces, f"bar_{level}_{index}"))
            x += 1.0
            index += 1
    for side, (px0, px1) in (("left", (-half, -inner)), ("right", (inner, half))):
        if base in ("stacked", "mount"):
            continue
        fd = GATE_FOOT_DEPTH / 2.0
        faces = {
            "up": frame_face("foot_top"),
            "north": frame_face("foot_ns"), "south": frame_face("foot_ns"),
            "east": frame_face("foot_ew"), "west": frame_face("foot_ew"),
        }
        elements.append(element((px0 + GATE_FOOT_INSET, 0.0, -fd, px1 - GATE_FOOT_INSET, GATE_FOOT_HEIGHT, fd), faces, f"foot_{side}"))
    return elements


def gate_light_elements(width, height, base="bar"):
    half, inner = gate_layout(width, height)
    t = GATE_FRAME
    d = GATE_DEPTH / 2.0
    lw = GATE_LIGHT_WIDTH
    li = GATE_LIGHT_INSET
    lt = GATE_LIGHT_THICKNESS
    hw = lw / 2.0
    elements = []

    def vertical(name, x0, x1, z0, z1, main, sides):
        y = t
        index = 0
        while y < height - t - 1e-9:
            faces = {main: light_face("led_v")}
            for side in sides:
                faces[side] = light_face("edge")
            elements.append(element((x0, y, z0, x1, y + 1.0, z1), faces, f"{name}_{index}", emissive=True))
            y += 1.0
            index += 1

    def horizontal(name, y0, y1, z0, z1, main, sides):
        x = -inner
        index = 0
        while x < inner - 1e-9:
            faces = {main: light_face("led_h")}
            for side in sides:
                faces[side] = light_face("edge")
            elements.append(element((x, y0, z0, x + 1.0, y1, z1), faces, f"{name}_{index}", emissive=True))
            x += 1.0
            index += 1

    for sign, side in ((-1, "left"), (1, "right")):
        near = sign * inner
        a = near + sign * li
        b = near + sign * (li + lw)
        x0, x1 = min(a, b), max(a, b)
        vertical(f"led_{side}_front", x0, x1, d, d + lt, "south", ("east", "west"))
        vertical(f"led_{side}_back", x0, x1, -d - lt, -d, "north", ("east", "west"))
        inner_face = "east" if sign < 0 else "west"
        ix0, ix1 = (near, near + lt) if sign < 0 else (near - lt, near)
        vertical(f"led_{side}_inner", ix0, ix1, -hw, hw, inner_face, ("north", "south"))
        outer_face = "west" if sign < 0 else "east"
        far = sign * half
        ox0, ox1 = (far, far + lt) if sign < 0 else (far - lt, far)
        vertical(f"led_{side}_outer", ox0, ox1, -hw, hw, outer_face, ("north", "south"))

    by0, by1 = t - li - lw, t - li
    if base in ("bar", "mount"):
        horizontal("led_bottom_front", by0, by1, d, d + lt, "south", ("up", "down"))
        horizontal("led_bottom_back", by0, by1, -d - lt, -d, "north", ("up", "down"))
    if base != "legs":
        horizontal("led_bottom_inner", t, t + lt, -hw, hw, "up", ("north", "south"))
    ty0, ty1 = height - t + li, height - t + li + lw
    horizontal("led_top_front", ty0, ty1, d, d + lt, "south", ("up", "down"))
    horizontal("led_top_back", ty0, ty1, -d - lt, -d, "north", ("up", "down"))
    horizontal("led_top_inner", height - t - lt, height - t, -hw, hw, "down", ("north", "south"))
    horizontal("led_top_outer", height - lt, height, -hw, hw, "up", ("north", "south"))
    return elements


def mount_plate_elements():
    pw, pt, pd = GATE_PLATE
    faces = {
        "up": frame_face("foot_top"), "down": frame_face("foot_top"),
        "north": frame_face("foot_ns"), "south": frame_face("foot_ns"),
        "east": frame_face("foot_ew"), "west": frame_face("foot_ew"),
    }
    elements = [element((-pw / 2, 0.0, -pd / 2, pw / 2, pt, pd / 2), faces, "plate")]
    b = GATE_BOLT / 2
    for side, z in (("near", -pd / 2 + 0.125), ("far", pd / 2 - 0.125)):
        bolt = {
            "north": frame_face("conn_sq"), "south": frame_face("conn_sq"),
            "east": frame_face("conn_sq"), "west": frame_face("conn_sq"),
            "up": frame_face("conn_sq"),
        }
        elements.append(element((-b, pt, z - b, b, pt + GATE_BOLT / 2, z + b), bolt, f"bolt_{side}"))
    return elements


def hanger_elements():
    r = GATE_HANGER_ROD / 2
    cw, ch, cd = GATE_HANGER_CLAMP
    pw, ph, pd = GATE_HANGER_PLATE
    drop = GATE_HANG_DROP
    conn = {
        "north": frame_face("conn_sq"), "south": frame_face("conn_sq"),
        "east": frame_face("conn_ew"), "west": frame_face("conn_ew"),
        "up": frame_face("conn_ud"), "down": frame_face("conn_ud"),
    }
    rod = {
        "north": frame_face("tube_v"), "south": frame_face("tube_v"),
        "east": frame_face("tube_v"), "west": frame_face("tube_v"),
    }
    plate = {
        "north": frame_face("foot_ns"), "south": frame_face("foot_ns"),
        "east": frame_face("foot_ns"), "west": frame_face("foot_ns"),
        "up": frame_face("conn_sq"), "down": frame_face("foot_top"),
    }
    return [
        element((-cw / 2, 0.0, -cd / 2, cw / 2, ch, cd / 2), conn, "clamp"),
        element((-r, ch, -r, r, drop - ph, r), rod, "strap"),
        element((-pw / 2, drop - ph, -pd / 2, pw / 2, drop, pd / 2), plate, "ceiling_plate"),
    ]


def race_item(name, model, tinted=False):
    definition = {"type": "minecraft:model", "model": model}
    if tinted:
        definition["tints"] = [{"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]
    write_json({"model": definition}, ASSETS / "items" / "race" / f"{name}.json")


def gen_gate_mounts():
    plate = {
        "textures": {"frame": f"{NS}:item/gate/frame", "particle": f"{NS}:item/gate/frame"},
        "elements": mount_plate_elements(),
    }
    write_json(plate, ASSETS / "models" / "gate" / "mount_plate.json")
    race_item("gate_mount_plate", f"{NS}:gate/mount_plate")
    hanger = {
        "textures": {"frame": f"{NS}:item/gate/frame", "particle": f"{NS}:item/gate/frame"},
        "elements": hanger_elements(),
    }
    write_json(hanger, ASSETS / "models" / "gate" / "hanger.json")
    race_item("gate_hanger", f"{NS}:gate/hanger")


GATE_PIECE_REFERENCE = (4, 4)
GATE_PIECES_FRAME = {
    "corner_left": ("bar", ("corner_bottom_left",), (2.0, 0.0), None),
    "corner_right": ("bar", ("corner_bottom_right",), (-2.0, 0.0), None),
    "tee_left": ("stacked", ("tee_left",), (2.0, 0.0), None),
    "tee_right": ("stacked", ("tee_right",), (-2.0, 0.0), None),
    "post_left": ("bar", ("post_left_0",), (2.0, -0.5), "y"),
    "post_right": ("bar", ("post_right_0",), (-2.0, -0.5), "y"),
    "bar_bottom": ("bar", ("bar_bottom_0",), (1.5, 0.0), "x"),
    "bar_top": ("bar", ("bar_top_0",), (1.5, -4.0), "x"),
    "foot_left": ("bar", ("foot_left",), (2.0, 0.0), None),
    "foot_right": ("bar", ("foot_right",), (-2.0, 0.0), None),
}
GATE_PIECES_LIGHTS = {
    "led_post_left": ("bar", ("led_left_front_0", "led_left_back_0", "led_left_inner_0", "led_left_outer_0"), (2.0, -0.5), "y"),
    "led_post_right": ("bar", ("led_right_front_0", "led_right_back_0", "led_right_inner_0", "led_right_outer_0"), (-2.0, -0.5), "y"),
    "led_bottom_outer": ("bar", ("led_bottom_front_0", "led_bottom_back_0"), (1.5, 0.0), "x"),
    "led_bottom_inner": ("bar", ("led_bottom_inner_0",), (1.5, 0.0), "x"),
    "led_top": ("bar", ("led_top_front_0", "led_top_back_0", "led_top_inner_0", "led_top_outer_0"), (1.5, -4.0), "x"),
}
GATE_PIECES_BANNER = {
    "banner_cap_left": (("banner_cap_left",), (2.0, 0.0), None),
    "banner_cap_right": (("banner_cap_right",), (-2.0, 0.0), None),
    "banner_panel": (("banner_panel_0",), (1.5, 0.0), "x"),
}
GATE_HANGER_PIECES = ("clamp", "strap", "ceiling_plate")
GATE_POLE_PIECES = ("pole_end", "pole_tee", "pole_foot")
GATE_POLE_FOOT_LOW = 0.09375


def moved_element(element, dx, dy):
    s = GATE_PX_PER_BLOCK
    out = json.loads(json.dumps(element))
    out["from"] = [round(out["from"][0] + dx * s, 4), round(out["from"][1] + dy * s, 4), out["from"][2]]
    out["to"] = [round(out["to"][0] + dx * s, 4), round(out["to"][1] + dy * s, 4), out["to"][2]]
    return out


def halved_element(element, axis):
    out = json.loads(json.dumps(element))
    index = 1 if axis == "y" else 0
    out["from"][index] = round(out["from"][index], 4)
    out["to"][index] = round(out["from"][index] + (out["to"][index] - out["from"][index]) / 2.0, 4)
    ends = ("up", "down") if axis == "y" else ("east", "west")
    for direction, face in out["faces"].items():
        if direction in ends:
            continue
        u0, v0, u1, v1 = face["uv"]
        if axis == "y":
            face["uv"] = [u0, v0, u1, round(v0 + (v1 - v0) / 2.0, 4)]
        else:
            face["uv"] = [u0, v0, round(u0 + (u1 - u0) / 2.0, 4), v1]
    if "name" in out:
        out["name"] = out["name"] + "_half"
    return out


def piece_elements(source, names, offset, half_axis=None):
    by_name = {e.get("name"): e for e in source}
    elements = [moved_element(by_name[name], offset[0], offset[1]) for name in names]
    if half_axis:
        elements = [halved_element(e, half_axis) for e in elements]
    return elements


def write_piece(name, texture_key, texture, elements, tinted=False):
    model = {
        "textures": {texture_key: texture, "particle": texture},
        "elements": elements,
    }
    write_json(model, ASSETS / "models" / "gate" / "piece" / f"{name}.json")
    race_item(f"gate_piece_{name}", f"{NS}:gate/piece/{name}", tinted=tinted)


def gate_piece_names():
    names = []
    for name, (_, _, _, half) in GATE_PIECES_FRAME.items():
        for prefix in ("", "start_"):
            names.append(prefix + name)
            if half:
                names.append(prefix + name + "_half")
    for name, (_, _, _, half) in GATE_PIECES_LIGHTS.items():
        names.append(name)
        if half:
            names.append(name + "_half")
    for name, (_, _, half) in GATE_PIECES_BANNER.items():
        names.append(name)
        if half:
            names.append(name + "_half")
    names.extend(f"hanger_{part}" for part in GATE_HANGER_PIECES)
    names.extend(GATE_POLE_PIECES)
    return names


def pole_connector_faces():
    return {
        "north": frame_face("conn_ew"), "south": frame_face("conn_ew"),
        "east": frame_face("conn_ew"), "west": frame_face("conn_ew"),
        "up": frame_face("conn_cap"), "down": frame_face("conn_cap"),
    }


def pole_end_elements():
    c = GATE_CORNER_DEPTH / 2.0
    return [element((-c, 0.0, -c, c, GATE_FRAME, c), pole_connector_faces(), "end")]


def pole_tee_elements():
    g = GATE_TEE_GROW
    c = GATE_CORNER_DEPTH / 2.0 + g
    return [element((-c, -GATE_FRAME - g, -c, c, g, c), pole_connector_faces(), "tee")]


def pole_foot_elements():
    fd = GATE_FOOT_DEPTH / 2.0
    w = GATE_FRAME / 2.0 - GATE_FOOT_INSET
    along_z = {
        "up": frame_face("foot_top"),
        "north": frame_face("foot_ns"), "south": frame_face("foot_ns"),
        "east": frame_face("foot_ew"), "west": frame_face("foot_ew"),
    }
    top = frame_face("foot_top")
    top["rotation"] = 90
    along_x = {
        "up": top,
        "north": frame_face("foot_ew"), "south": frame_face("foot_ew"),
        "east": frame_face("foot_ns"), "west": frame_face("foot_ns"),
    }
    return [
        element((-w, 0.0, -fd, w, GATE_FOOT_HEIGHT, fd), along_z, "foot_z"),
        element((-fd, 0.0, -w, fd, GATE_POLE_FOOT_LOW, w), along_x, "foot_x"),
    ]


def gen_gate_pole():
    texture = f"{NS}:item/gate/frame"
    write_piece("pole_end", "frame", texture, pole_end_elements())
    write_piece("pole_tee", "frame", texture, pole_tee_elements())
    write_piece("pole_foot", "frame", texture, pole_foot_elements())
    save_png(gate_pole_icon(), tex_path("item", "gate_pole"))
    flat_item_model("gate_pole")
    item_def("gate_pole", f"{NS}:item/gate_pole")


def gate_pole_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    led_hi = hex_rgba("a8ffcf")
    led = col("N")

    def connector(y0, y1):
        for y in range(y0, y1 + 1):
            for x in range(4, 11):
                edge = x in (4, 10) or y in (y0, y1)
                img.putpixel((x, y), col("K") if edge else col("A"))
        img.putpixel((5, y0 + 1), col("a"))
        img.putpixel((6, y0 + 1), col("a"))

    for y in range(3, 12):
        img.putpixel((5, y), col("K"))
        img.putpixel((6, y), col("C"))
        img.putpixel((7, y), led_hi if y % 2 == 0 else led)
        img.putpixel((8, y), col("C"))
        img.putpixel((9, y), col("K"))
    connector(1, 3)
    connector(11, 13)
    for x in range(1, 15):
        img.putpixel((x, 14), col("s"))
        img.putpixel((x, 15), col("K"))
    return img


def gen_gate_pieces():
    width, height = GATE_PIECE_REFERENCE
    frames = {base: gate_frame_elements(width, height, base) for base in ("bar", "stacked")}
    lights = gate_light_elements(width, height, "bar")
    for name, (base, names, offset, half) in GATE_PIECES_FRAME.items():
        for prefix, texture in (("", f"{NS}:item/gate/frame"), ("start_", f"{NS}:item/gate/start_frame")):
            write_piece(prefix + name, "frame", texture, piece_elements(frames[base], names, offset))
            if half:
                write_piece(prefix + name + "_half", "frame", texture, piece_elements(frames[base], names, offset, half))
    for name, (base, names, offset, half) in GATE_PIECES_LIGHTS.items():
        write_piece(name, "lights", f"{NS}:item/gate/lights", piece_elements(lights, names, offset), tinted=True)
        if half:
            write_piece(name + "_half", "lights", f"{NS}:item/gate/lights", piece_elements(lights, names, offset, half), tinted=True)
    banner = gate_banner_elements(width)
    for name, (names, offset, half) in GATE_PIECES_BANNER.items():
        write_piece(name, "banner", f"{NS}:item/gate/banner", piece_elements(banner, names, offset))
        if half:
            write_piece(name + "_half", "banner", f"{NS}:item/gate/banner", piece_elements(banner, names, offset, half))
    hanger = {e["name"]: e for e in hanger_elements()}
    for part in GATE_HANGER_PIECES:
        element = json.loads(json.dumps(hanger[part]))
        lift = element["from"][1]
        element["from"][1] = 0.0
        element["to"][1] = round(element["to"][1] - lift, 4)
        write_piece(f"hanger_{part}", "frame", f"{NS}:item/gate/frame", [element])


def gen_gates():
    frame_texture = gen_frame_texture()
    gen_start_frame_texture(frame_texture)
    gen_banner_texture()
    gen_light_texture()
    for size, (width, height) in GATES.items():
        for base in GATE_BASES:
            suffix = gate_variant_suffix(base)
            frame = {
                "textures": {"frame": f"{NS}:item/gate/frame", "particle": f"{NS}:item/gate/frame"},
                "elements": gate_frame_elements(width, height, base),
            }
            write_json(frame, ASSETS / "models" / "gate" / f"{size}_frame{suffix}.json")
            lights = {
                "textures": {"lights": f"{NS}:item/gate/lights", "particle": f"{NS}:item/gate/lights"},
                "elements": gate_light_elements(width, height, base),
            }
            write_json(lights, ASSETS / "models" / "gate" / f"{size}_lights{suffix}.json")
            start = {
                "textures": {"frame": f"{NS}:item/gate/start_frame", "particle": f"{NS}:item/gate/start_frame"},
                "elements": gate_frame_elements(width, height, base),
            }
            write_json(start, ASSETS / "models" / "gate" / f"{size}_start_frame{suffix}.json")
            race_item(f"gate_{size}_frame{suffix}", f"{NS}:gate/{size}_frame{suffix}")
            race_item(f"gate_{size}_lights{suffix}", f"{NS}:gate/{size}_lights{suffix}", tinted=True)
            race_item(f"gate_{size}_start_frame{suffix}", f"{NS}:gate/{size}_start_frame{suffix}")
        race_item(f"gate_{size}_banner", f"{NS}:gate/{size}_banner")
        banner = {
            "textures": {"banner": f"{NS}:item/gate/banner", "particle": f"{NS}:item/gate/banner"},
            "elements": gate_banner_elements(width),
        }
        write_json(banner, ASSETS / "models" / "gate" / f"{size}_banner.json")
        name = f"gate_{size}"
        save_png(gate_icon(width, height), tex_path("item", name))
        flat_item_model(name)
        item_def(name, f"{NS}:item/{name}")


def gate_icon(width, height):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    scale = min(15.0 / width, 13.0 / height, 3.6)
    ow = max(7, round(width * scale))
    oh = max(7, round(height * scale))
    if ow % 2 == 1:
        ow += 1 if ow < 16 else -1
    thick = 2
    x0 = (16 - ow) // 2
    y1 = 14
    y0 = y1 - oh + 1
    x1 = x0 + ow - 1
    draw = ImageDraw.Draw(img)
    draw.rectangle((x0, y0, x1, y1), fill=col("C"))
    draw.rectangle((x0 + thick, y0 + thick, x1 - thick, y1 - thick), fill=(0, 0, 0, 0))
    for x in range(x0, x1 + 1):
        img.putpixel((x, y0), col("K"))
        img.putpixel((x, y1), col("K"))
    for y in range(y0, y1 + 1):
        img.putpixel((x0, y), col("K"))
        img.putpixel((x1, y), col("K"))
    ix0, iy0, ix1, iy1 = x0 + thick, y0 + thick, x1 - thick, y1 - thick
    led_hi = hex_rgba("a8ffcf")
    led = col("N")
    for x in range(ix0 - 1, ix1 + 2):
        img.putpixel((x, iy0 - 1), led_hi if (x - ix0) % 2 == 0 else led)
        img.putpixel((x, iy1 + 1), led_hi if (x - ix0) % 2 == 0 else led)
    for y in range(iy0 - 1, iy1 + 2):
        img.putpixel((ix0 - 1, y), led_hi if (y - iy0) % 2 == 0 else led)
        img.putpixel((ix1 + 1, y), led_hi if (y - iy0) % 2 == 0 else led)
    for cx, cy in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
        img.putpixel((cx, cy), col("a"))
        img.putpixel((cx + 1, cy), col("A"))
        img.putpixel((cx, cy + 1), col("A"))
        img.putpixel((cx + 1, cy + 1), col("A"))
    for x in range(x0, x0 + 2):
        img.putpixel((x, y1 + 1), col("s"))
    for x in range(x1 - 1, x1 + 1):
        img.putpixel((x, y1 + 1), col("s"))
    for x in range(ix0, ix1 + 1):
        for y in range(iy0, iy1 + 1):
            img.putpixel((x, y), (0, 0, 0, 0))
    return img


def gen_waterproof_spray():
    save_png(PIXEL["waterproof_spray"], tex_path("item", "waterproof_spray"))
    flat_item_model("waterproof_spray")
    item_def("waterproof_spray", f"{NS}:item/waterproof_spray")


def gen_course_tool():
    save_png(PIXEL["course_tool"], tex_path("item", "course_tool"))
    flat_item_model("course_tool", parent="minecraft:item/handheld")
    item_def("course_tool", f"{NS}:item/course_tool")


SLOT_CATEGORIES = ("frame", "stack", "motor", "prop", "video", "battery", "drone", "accessory_top", "accessory_bottom", "accessory_front")


def silhouette(test, threshold=0.45, sub=8):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    grey = col("X")
    for py in range(16):
        for px in range(16):
            hits = 0
            for sy in range(sub):
                for sx in range(sub):
                    if test(px + (sx + 0.5) / sub - 8.0, py + (sy + 0.5) / sub - 8.0):
                        hits += 1
            if hits >= sub * sub * threshold:
                img.putpixel((px, py), grey)
    return img


def prop_silhouette():
    def test(x, y):
        r = math.hypot(x, y)
        if r < 2.2:
            return r > 0.9
        for k in range(3):
            angle = -math.pi / 2 + k * 2 * math.pi / 3
            along = x * math.cos(angle) + y * math.sin(angle)
            across = -x * math.sin(angle) + y * math.cos(angle)
            if 1.0 < along < 7.7:
                f = (along - 1.0) / 6.7
                width = 0.55 + 1.35 * math.sin(math.pi * min(1.0, f * 0.9 + 0.08)) ** 0.9
                offset = 0.35 * f
                if -width + offset < across < width * 0.75 + offset:
                    return True
        return False

    return silhouette(test)


def motor_silhouette():
    def test(x, y):
        r = math.hypot(x, y)
        if r < 1.2:
            return True
        if r < 2.0:
            return False
        if r < 3.0:
            return True
        if r < 5.3:
            theta = (math.degrees(math.atan2(y, x)) + 8.0) % 60.0
            return theta < 16.0
        return r < 6.9

    return silhouette(test, threshold=0.5)


def gen_slot_sprites():
    procedural = {"prop": prop_silhouette, "motor": motor_silhouette}
    for category in SLOT_CATEGORIES:
        img = procedural[category]() if category in procedural else PIXEL[f"slot_{category}"]
        save_png(img, ASSETS / "textures" / "gui" / "sprites" / "gui" / "slot" / f"{category}.png")


GUI_W, GUI_H = 312, 224
GUI_TEX_W, GUI_TEX_H = 512, 256
PART_SLOTS = {
    "frame": (80, 64),
    "stack": (80, 42),
    "motor_nose_left": (34, 30), "motor_nose_right": (126, 30),
    "motor_tail_left": (34, 98), "motor_tail_right": (126, 98),
    "prop_nose_left": (10, 30), "prop_nose_right": (150, 30),
    "prop_tail_left": (10, 98), "prop_tail_right": (150, 98),
    "video": (80, 20),
    "battery": (80, 87),
    "accessory_top": (52, 64),
    "accessory_bottom": (108, 64),
    "accessory_front": (30, 64),
}
RESULT_SLOT = (202, 100)
SALVAGE_SLOT = (234, 100)
ARROW = (171, 100, 24, 16)
INVENTORY = (8, 142)
HOTBAR_OFFSET = 58
BLUEPRINT = (7, 17, 169, 128)
PREVIEW = (176, 17, 305, 91)
STATS = (176, 124, 305, 219)
MOTOR_SPIN = {"nose_left": "cw", "nose_right": "ccw", "tail_left": "ccw", "tail_right": "cw"}

def disc(img, cx, cy, radius, fill, ring):
    draw = ImageDraw.Draw(img)
    draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=fill)
    steps = 72
    for i in range(steps):
        if i % 6 in (4, 5):
            continue
        a = 2 * math.pi * i / steps
        x = round(cx + radius * math.cos(a))
        y = round(cy + radius * math.sin(a))
        img.putpixel((x, y), ring)


def spin_arrow(img, cx, cy, radius, clockwise, top):
    draw = ImageDraw.Draw(img)
    a0, a1 = (-122, -58) if top else (58, 122)
    box = (cx - radius, cy - radius, cx + radius, cy + radius)
    draw.arc(box, a0, a1, fill=GUI["amber"], width=2)
    end = a1 if clockwise else a0
    a = math.radians(end)
    direction = 1 if clockwise else -1
    tangent = (-math.sin(a) * direction, math.cos(a) * direction)
    normal = (math.cos(a), math.sin(a))
    mid = radius - 0.5
    base = (cx + mid * math.cos(a), cy + mid * math.sin(a))
    tip = (base[0] + tangent[0] * 4.0, base[1] + tangent[1] * 4.0)
    left = (base[0] + normal[0] * 2.8, base[1] + normal[1] * 2.8)
    right = (base[0] - normal[0] * 2.8, base[1] - normal[1] * 2.8)
    draw.polygon([tip, left, right], fill=GUI["amber"])


def thick_line(draw, p0, p1, width, fill):
    draw.line((p0, p1), fill=fill, width=width)


def gen_gui():
    img = Image.new("RGBA", (GUI_TEX_W, GUI_TEX_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    panel(draw, GUI_W, GUI_H)

    bx0, by0, bx1, by1 = BLUEPRINT
    inset(draw, BLUEPRINT, GUI["blueprint"])
    for gx in range(bx0 + 8, bx1 - 1, 8):
        rect(draw, (gx, by0 + 1, gx + 1, by1 - 1), GUI["grid_major"] if (gx - bx0) % 32 == 0 else GUI["grid"])
    for gy in range(by0 + 8, by1 - 1, 8):
        rect(draw, (bx0 + 1, gy, bx1 - 1, gy + 1), GUI["grid_major"] if (gy - by0) % 32 == 0 else GUI["grid"])

    center = (88, 72)
    motors = {k[6:]: (x + 8, y + 8) for k, (x, y) in PART_SLOTS.items() if k.startswith("motor_")}
    for pos, (mx, my) in motors.items():
        disc(img, mx, my, 16, GUI["disc"], GUI["disc_ring"])
    for pos, (mx, my) in motors.items():
        thick_line(draw, center, (mx, my), 9, GUI["arm_edge"])
    for pos, (mx, my) in motors.items():
        thick_line(draw, center, (mx, my), 7, GUI["arm"])
    for pos, (mx, my) in motors.items():
        spin_arrow(img, mx, my, 19, MOTOR_SPIN[pos] == "cw", pos.startswith("nose"))
        px, py = PART_SLOTS["prop_" + pos]
        pcx = px + 8
        sign = 1 if pcx > mx else -1
        for x in range(min(pcx - sign * 10, mx + sign * 17), max(pcx - sign * 10, mx + sign * 17) + 1):
            if x % 2 == 0:
                img.putpixel((x, my), GUI["disc_ring"])
    body = (75, 28, 101, 109)
    rect(draw, body, GUI["body_edge"])
    rect(draw, (body[0] + 1, body[1] + 1, body[2] - 1, body[3] - 1), GUI["body"])
    for hx, hy in ((77, 30), (98, 30), (77, 106), (98, 106)):
        img.putpixel((hx, hy), GUI["body_edge"])
    rect(draw, (84, 18, 92, 19), GUI["amber"])
    rect(draw, (86, 19, 90, 20), GUI["amber_dim"])

    for key, (sx, sy) in PART_SLOTS.items():
        slot_well(draw, sx, sy, GUI["slot_ring"])

    preview_field(img, PREVIEW, spokes=7, spacing=22)

    sx, sy = SALVAGE_SLOT
    inset(draw, (sx - 5, sy - 6, PREVIEW[2], sy + 22), GUI["well"])
    slot_well(draw, sx, sy, GUI["amber_dim"])
    ax, ay, aw, ah = ARROW
    shaft = (ax + 2, ay + 6, ax + 15, ay + 10)
    rect(draw, shaft, GUI["arrow"])
    for i in range(8):
        rect(draw, (ax + 15 + i, ay + 1 + i, ax + 16 + i, ay + 15 - i), GUI["arrow"])
    rx, ry = RESULT_SLOT
    rect(draw, (rx - 6, ry - 6, rx + 22, ry + 22), GUI["amber"])
    rect(draw, (rx - 5, ry - 5, rx + 21, ry + 21), GUI["outline"])
    inset(draw, (rx - 4, ry - 4, rx + 20, ry + 20), GUI["slot"])

    inset(draw, STATS, GUI["well"])
    rect(draw, (STATS[0] + 1, STATS[1] + 1, STATS[2] - 1, STATS[1] + 2), GUI["amber_dim"])

    ix, iy = INVENTORY
    for row in range(3):
        for column in range(9):
            slot_well(draw, ix + column * 18, iy + row * 18)
    for column in range(9):
        slot_well(draw, ix + column * 18, iy + HOTBAR_OFFSET)
    save_png(img, ASSETS / "textures" / "gui" / "drone_workbench.png")


FONT = {
    "P": ["1111.", "1...1", "1...1", "1111.", "1....", "1....", "1...."],
    "R": ["1111.", "1...1", "1...1", "1111.", "1.1..", "1..1.", "1...1"],
    "O": [".111.", "1...1", "1...1", "1...1", "1...1", "1...1", ".111."],
    "W": ["1...1", "1...1", "1...1", "1.1.1", "1.1.1", "11.11", "1...1"],
    "A": [".111.", "1...1", "1...1", "11111", "1...1", "1...1", "1...1"],
    "S": [".1111", "1....", "1....", ".111.", "....1", "....1", "1111."],
    "H": ["1...1", "1...1", "1...1", "11111", "1...1", "1...1", "1...1"],
    "F": ["11111", "1....", "1....", "1111.", "1....", "1....", "1...."],
    "V": ["1...1", "1...1", "1...1", "1...1", ".1.1.", ".1.1.", "..1.."],
}


def draw_word(draw, word, x, y, cell, colours):
    for i, ch in enumerate(word):
        glyph = FONT[ch]
        colour = colours[i] if isinstance(colours, (list, tuple)) and len(colours) == len(word) else colours
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == "1":
                    draw.rectangle((x + (i * 6 + gx) * cell, y + gy * cell, x + (i * 6 + gx + 1) * cell - 1, y + (gy + 1) * cell - 1), fill=colour)


def render_icon(size):
    ss = 4
    s = size * ss
    k = s / 256.0
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    bg = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    bgd = ImageDraw.Draw(bg)
    for y in range(s):
        t = y / s
        c = mix(hex_rgba("20252d"), hex_rgba("0d0f12"), t)
        bgd.line(((0, y), (s, y)), fill=c)
    grid = ImageDraw.Draw(bg)
    step = round(16 * k)
    for g in range(0, s, step):
        grid.line(((g, 0), (g, s)), fill=(255, 255, 255, 10))
        grid.line(((0, g), (s, g)), fill=(255, 255, 255, 10))
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s - 1, s - 1), radius=round(44 * k), fill=255)
    img.paste(bg, (0, 0), mask)
    draw = ImageDraw.Draw(img, "RGBA")

    cx, cy = 128 * k, 104 * k
    arm = 58 * k
    prop_r = 40 * k
    motors = [(cx + dx * arm, cy + dy * arm) for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1))]

    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for mx, my in motors:
        gd.ellipse((mx - prop_r, my - prop_r, mx + prop_r, my + prop_r), fill=(255, 187, 0, 60))
    glow = glow.filter(ImageFilter.GaussianBlur(10 * k))
    img.alpha_composite(glow)

    for mx, my in motors:
        draw.ellipse((mx - prop_r, my - prop_r, mx + prop_r, my + prop_r), fill=(255, 255, 255, 22), outline=(255, 255, 255, 70), width=max(1, round(2 * k)))
    for (mx, my), spin in zip(motors, (1, -1, -1, 1)):
        for blade in range(2):
            base = blade * 180 + (35 if spin > 0 else -35)
            for j in range(10):
                a0 = base - spin * j * 5
                alpha = int(150 * (1 - j / 10))
                box = (mx - prop_r * 0.92, my - prop_r * 0.92, mx + prop_r * 0.92, my + prop_r * 0.92)
                start, end = (a0 - 8, a0 + 8)
                draw.pieslice(box, start, end, fill=(255, 212, 90, alpha // 3))
            box = (mx - prop_r * 0.92, my - prop_r * 0.92, mx + prop_r * 0.92, my + prop_r * 0.92)
            draw.pieslice(box, base - 6, base + 6, fill=(255, 187, 0, 235))

    def arm_poly(x0, y0, x1, y1, w):
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy)
        nx, ny = -dy / length * w / 2, dx / length * w / 2
        return [(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)]

    for mx, my in motors:
        draw.polygon(arm_poly(cx, cy, mx, my, 24 * k), fill=hex_rgba("0b0d10"))
    for mx, my in motors:
        draw.polygon(arm_poly(cx, cy, mx, my, 18 * k), fill=hex_rgba("2e343d"))
        draw.polygon(arm_poly(cx, cy, mx, my, 6 * k), fill=hex_rgba("3c434e"))
    for mx, my in motors:
        r = 14 * k
        draw.ellipse((mx - r - 3 * k, my - r - 3 * k, mx + r + 3 * k, my + r + 3 * k), fill=hex_rgba("0b0d10"))
        draw.ellipse((mx - r, my - r, mx + r, my + r), fill=hex_rgba("3a414b"), outline=col("A"), width=max(1, round(3 * k)))
        draw.ellipse((mx - 4 * k, my - 4 * k, mx + 4 * k, my + 4 * k), fill=hex_rgba("d4dae1"))
    bw, bh = 34 * k, 62 * k
    draw.rounded_rectangle((cx - bw / 2 - 3 * k, cy - bh / 2 - 3 * k, cx + bw / 2 + 3 * k, cy + bh / 2 + 3 * k), radius=10 * k, fill=hex_rgba("0b0d10"))
    draw.rounded_rectangle((cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), radius=8 * k, fill=hex_rgba("30363f"))
    draw.rounded_rectangle((cx - bw / 2 + 5 * k, cy - bh / 2 + 16 * k, cx + bw / 2 - 5 * k, cy + bh / 2 - 6 * k), radius=4 * k, fill=col("A"))
    draw.rounded_rectangle((cx - bw / 2 + 8 * k, cy - bh / 2 + 19 * k, cx + bw / 2 - 8 * k, cy + bh / 2 - 9 * k), radius=3 * k, fill=hex_rgba("c98f00"))
    lens = 7 * k
    ly = cy - bh / 2 + 7 * k
    draw.ellipse((cx - lens, ly - lens, cx + lens, ly + lens), fill=hex_rgba("0b0d10"))
    draw.ellipse((cx - lens * 0.6, ly - lens * 0.6, cx + lens * 0.6, ly + lens * 0.6), fill=hex_rgba("45c9f5"))
    draw.ellipse((cx - lens * 0.55, ly - lens * 0.55, cx - lens * 0.05, ly - lens * 0.05), fill=hex_rgba("dff6ff"))

    cell = 4 * k
    word = "PROPWASH"
    width = (len(word) * 6 - 1) * cell
    x = (s - width) / 2
    y = 206 * k
    draw_word(draw, word, round(x + cell * 0.5), round(y + cell * 0.5), round(cell), (0, 0, 0, 150))
    colours = [hex_rgba("f4f6f8")] * 4 + [col("A")] * 4
    draw_word(draw, word, round(x), round(y), round(cell), colours)
    return img.resize((size, size), Image.LANCZOS)


def gen_icons():
    save_png(render_icon(256), ASSETS / "icon.png")
    save_png(render_icon(128), RES / "pack.png")


KEY_FONT_GLYPHS = (("option_key", "\u2325"),)


def gen_key_font():
    providers = []
    for name, char in KEY_FONT_GLYPHS:
        glyph = PIXEL[name]
        if glyph.size[1] != 8:
            raise ValueError(f"font glyph {name} must be 8 px high, is {glyph.size[1]}")
        save_png(glyph, tex_path("font", name))
        providers.append({"type": "bitmap", "file": f"{NS}:font/{name}.png", "ascent": 7, "chars": [char]})
    providers.append({"type": "reference", "id": "minecraft:default"})
    write_json({"providers": providers}, ASSETS / "font" / "keys.json")


def main():
    gen_workbench()
    gen_goggles()
    gen_gates()
    gen_gate_mounts()
    gen_gate_pieces()
    gen_gate_pole()
    gen_course_tool()
    gen_waterproof_spray()
    gen_slot_sprites()
    gen_gui()
    gen_icons()
    gen_key_font()
    remote.generate()
    charger.generate()
    repeater.generate()
    course.generate()
    menus.generate()
    materials.generate()
    maps.generate()
    print("generated world assets")


if __name__ == "__main__":
    main()
