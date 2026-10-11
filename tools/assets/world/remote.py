"""Remote control (propwash:remote): EdgeTX-style handheld radio, item models, textures, gimbal texture.

remote_body.json leaves out the sticks and the four switches: RemoteGimbalModel draws them (and the three push
buttons under the display) so they can move in the hand and in the receiver wizard.

Contract: docs/design/gameplay-loop-v2.md §11.1-§11.3 and docs/ASSETS.md "Funke".
Front (screen, gimbals) faces +Z, antenna +Y. All coordinates in model pixels (16 = 1 block).
"""

from PIL import Image

from assetlib import ASSETS, hex_rgba, save_png, write_json

NS = "propwash"
TEX = 64
UNIT = TEX / 16.0

ANTH = hex_rgba("2b2f36")
ANTH_HI = hex_rgba("3a4049")
ANTH_LO = hex_rgba("1f2228")
RUBBER = hex_rgba("1b1e23")
RUBBER_HI = hex_rgba("262a30")
RUBBER_LO = hex_rgba("121418")
METAL = hex_rgba("8c949e")
METAL_HI = hex_rgba("b4bbc4")
METAL_LO = hex_rgba("5f6770")
AMBER = hex_rgba("ffbb00")
AMBER_LO = hex_rgba("b07f00")
LCD = hex_rgba("0e2a24")
LCD_PX = hex_rgba("7cffb2")
LCD_DIM = hex_rgba("1f5a44")
BLACK = hex_rgba("0b0c0e")
LED_RED = hex_rgba("ff3b3b")

PIVOT_LEFT = (4.0, 6.5, 10.25)
PIVOT_RIGHT = (12.0, 6.5, 10.25)
PLATE_LEFT = (2.25, 4.75, 10.0, 5.75, 8.25, 10.25)
PLATE_RIGHT = (10.25, 4.75, 10.0, 13.75, 8.25, 10.25)


def stick_boxes(cx):
    shaft = (cx - 0.25, 6.25, 10.25, cx + 0.25, 6.75, 12.5)
    cap = (cx - 0.5, 6.0, 12.5, cx + 0.5, 7.0, 13.25)
    return shaft, cap


STICK_LEFT = stick_boxes(PIVOT_LEFT[0])
STICK_RIGHT = stick_boxes(PIVOT_RIGHT[0])

ALL = ("north", "south", "east", "west", "up", "down")

FACE_AXES = {
    "north": ((-1, 0, 0), (0, 1, 0)),
    "south": ((1, 0, 0), (0, 1, 0)),
    "east": ((0, 0, -1), (0, 1, 0)),
    "west": ((0, 0, 1), (0, 1, 0)),
    "up": ((1, 0, 0), (0, 0, -1)),
    "down": ((1, 0, 0), (0, 0, 1)),
}


def image_side(face, direction):
    right, up = FACE_AXES[face]
    if direction == right:
        return "right"
    if direction == tuple(-v for v in right):
        return "left"
    if direction == up:
        return "top"
    if direction == tuple(-v for v in up):
        return "bottom"
    return None


def fill(w, h, colour):
    return Image.new("RGBA", (w, h), colour)


def bevel(img, hi, lo, top=True, bottom=True, left=True, right=True):
    w, h = img.size
    px = img.load()
    if top:
        for x in range(w):
            px[x, 0] = hi
    if bottom and h > 1:
        for x in range(w):
            px[x, h - 1] = lo
    if left:
        for y in range(h):
            px[0, y] = hi if y < h - 1 else lo
    if right and w > 1:
        for y in range(h):
            px[w - 1, y] = lo
    return img


def paint_body(face, w, h):
    img = fill(w, h, ANTH)
    px = img.load()
    if face == "south":
        bevel(img, ANTH_HI, ANTH_LO)
        for x in range(2, w - 2):
            px[x, 1] = hex_rgba("323740")
        for bx in (10, 13):
            for y in (10, 11):
                px[bx, y] = METAL_LO
                px[bx + 1, y] = METAL_LO
            px[bx, 10] = METAL
        px[16, 10] = AMBER_LO
        px[17, 10] = AMBER
        px[16, 11] = AMBER_LO
        px[17, 11] = AMBER_LO
        for x in (1, 2, w - 3, w - 2):
            px[x, 12] = ANTH_LO
        px[w // 2 - 1, 12] = LED_RED
        return img
    if face == "north":
        bevel(img, ANTH_HI, ANTH_LO)
        for x in range(6, w - 6):
            px[x, 3] = ANTH_LO
            px[x, h - 3] = ANTH_LO
        for y in range(3, h - 2):
            px[6, y] = ANTH_LO
            px[w - 7, y] = ANTH_LO
        for x in range(8, w - 8, 2):
            for y in range(5, h - 4):
                px[x, y] = RUBBER
        px[w - 9, 4] = AMBER
        px[w - 10, 4] = AMBER_LO
        return img
    if face == "up":
        for x in range(w):
            px[x, h - 1] = ANTH_HI
            px[x, 0] = ANTH_LO
        for x in range(9, w - 9):
            px[x, 2] = ANTH_LO
        return img
    if face == "down":
        return fill(w, h, ANTH_LO)
    bevel(img, ANTH_HI, ANTH_LO)
    for y in range(3, h - 2, 2):
        for x in range(1, w - 1):
            px[x, y] = RUBBER_HI
    return img


def paint_grip(face, w, h):
    if face == "down":
        return fill(w, h, RUBBER_LO)
    img = fill(w, h, RUBBER)
    px = img.load()
    for x in range(w):
        px[x, 0] = ANTH
        px[x, h - 1] = RUBBER_LO
    for y in range(1, h - 1):
        for x in range(1 if face in ("north", "south") else 0, w, 2):
            px[x, y] = RUBBER_HI
    return img


QUAD_ICON = ["10001", "01110", "01110", "01110", "10001"]


def paint_display(face, w, h):
    img = fill(w, h, LCD)
    px = img.load()
    for x in range(w):
        px[x, 0] = LCD_PX
        px[x, 1] = LCD_PX
    for x in (1, 2, 3, 5, 6):
        px[x, 1] = LCD
    for i, x in enumerate(range(w - 5, w - 1)):
        for y in range(2 - min(2, i), 2):
            px[x, y] = LCD
    for gy, row in enumerate(QUAD_ICON):
        for gx, bit in enumerate(row):
            if bit == "1":
                px[1 + gx, 3 + gy] = LCD_PX
    for ty in (4, 6):
        for x in range(8, w - 1):
            px[x, ty] = LCD_DIM
        centre = 8 + (w - 9) // 2
        px[centre, ty] = LCD_PX
        px[centre, ty - 1] = LCD_PX
    for xx in range(1, w - 1):
        px[xx, 9] = LCD_PX if xx < 11 else LCD_DIM
        px[xx, 10] = LCD_PX if xx < 11 else LCD_DIM
    px[w - 1, 9] = LCD_DIM
    return img


def paint_plate(face, w, h):
    if face != "south":
        return fill(w, h, AMBER_LO if face in ("up", "east") else hex_rgba("8a6400"))
    img = fill(w, h, RUBBER)
    px = img.load()
    c = (w - 1) / 2.0
    for y in range(h):
        for x in range(w):
            r = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            if r > c + 0.3:
                px[x, y] = ANTH_LO
            elif r > c - 0.9:
                px[x, y] = AMBER if (x + y) % 5 else AMBER_LO
            elif r > c - 1.9:
                px[x, y] = BLACK
            else:
                shade = METAL_HI if (x < c and y < c - 1) else METAL
                px[x, y] = shade
    for i in range(3, w - 3):
        for j in (int(c), int(c) + 1):
            if px[i, j] != BLACK and px[i, j] not in (AMBER, AMBER_LO):
                px[i, j] = METAL_LO
            if px[j, i] != BLACK and px[j, i] not in (AMBER, AMBER_LO):
                px[j, i] = METAL_LO
    return img


def paint_shaft(face, w, h):
    img = fill(w, h, METAL)
    px = img.load()
    for x in range(w):
        px[x, 0] = METAL_HI if face in ("up", "west") else METAL
        px[x, h - 1] = METAL_LO
    return img


def paint_cap(face, w, h):
    img = fill(w, h, RUBBER)
    px = img.load()
    if face == "south":
        for y in range(h):
            for x in range(w):
                if (x + y) % 2 == 0:
                    px[x, y] = RUBBER_HI
        return img
    if face == "north":
        return fill(w, h, AMBER)
    side = image_side(face, (0, 0, -1))
    for i in range(max(w, h)):
        if side == "left" and i < h:
            px[0, i] = AMBER
        elif side == "right" and i < h:
            px[w - 1, i] = AMBER
        elif side == "top" and i < w:
            px[i, 0] = AMBER
        elif side == "bottom" and i < w:
            px[i, h - 1] = AMBER
    return img


def paint_metal(face, w, h):
    img = fill(w, h, METAL)
    if face == "up":
        return fill(w, h, METAL_HI)
    if face == "down":
        return fill(w, h, METAL_LO)
    px = img.load()
    for x in range(w):
        px[x, h - 1] = METAL_LO
    return img


def paint_switch(face, w, h):
    if face == "up":
        return fill(w, h, AMBER)
    img = fill(w, h, METAL_LO)
    px = img.load()
    for x in range(w):
        px[x, 0] = AMBER
    return img


def paint_joint(face, w, h):
    img = fill(w, h, hex_rgba("4a525e"))
    px = img.load()
    for x in range(w):
        px[x, 0] = METAL
    return img


def paint_antenna(face, w, h):
    if face == "up":
        return fill(w, h, RUBBER_HI)
    img = fill(w, h, RUBBER)
    px = img.load()
    for y in range(h):
        px[0, y] = RUBBER_HI
    for x in range(w):
        px[x, 0] = ANTH_HI
    return img


def parts():
    body = [
        ("housing", (1.5, 3.0, 7.0, 14.5, 10.0, 10.0), ALL, paint_body, 2, 0),
        ("grip_left", (1.5, 0.5, 7.0, 5.0, 3.0, 10.0), ("north", "south", "east", "west", "down"), paint_grip, 2, 0),
        ("grip_right", (11.0, 0.5, 7.0, 14.5, 3.0, 10.0), ("north", "south", "east", "west", "down"), paint_grip, 2, 0),
        ("display", (6.0, 6.5, 10.0, 10.0, 9.5, 10.1), ("south",), paint_display, 4, 6),
        ("gimbal_plate_left", PLATE_LEFT, ("south", "east", "west", "up", "down"), paint_plate, 4, 0),
        ("gimbal_plate_right", PLATE_RIGHT, ("south", "east", "west", "up", "down"), paint_plate, 4, 0),
        ("handle_left", (5.0, 10.0, 8.0, 5.5, 11.5, 9.0), ("north", "south", "east", "west"), paint_metal, 2, 0),
        ("handle_right", (10.5, 10.0, 8.0, 11.0, 11.5, 9.0), ("north", "south", "east", "west"), paint_metal, 2, 0),
        ("handle_bar", (5.0, 11.5, 8.0, 11.0, 12.0, 9.0), ALL, paint_metal, 2, 0),
    ]
    for index, x in enumerate((2.5, 4.0, 12.0, 13.5)):
        body.append((f"switch_{index}", (x - 0.25, 10.0, 9.0, x + 0.25, 11.0, 9.5), ("north", "south", "east", "west", "up"), paint_switch, 2, 0))
    body += [
        ("antenna_joint", (12.75, 10.0, 7.75, 13.75, 11.0, 8.75), ("north", "south", "east", "west", "up"), paint_joint, 2, 0),
        ("antenna", (12.875, 11.0, 7.875, 13.625, 15.0, 8.625), ("north", "south", "east", "west", "up"), paint_antenna, 2, 0),
    ]
    sticks = []
    for side, (shaft, cap) in (("left", STICK_LEFT), ("right", STICK_RIGHT)):
        sticks.append((f"gimbal_{side}_shaft", shaft, ("east", "west", "up", "down"), paint_shaft, 4, 0))
        sticks.append((f"gimbal_{side}_cap", cap, ALL, paint_cap, 4, 0))
    return body, sticks


def face_dims(box, face):
    x0, y0, z0, x1, y1, z1 = box
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def texel_size(box, face, density):
    w, h = face_dims(box, face)
    return max(1, int(round(w * density + 0.49))), max(1, int(round(h * density + 0.49)))


def pack(requests):
    placed = {}
    order = sorted(requests, key=lambda r: (-r[2], -r[1], r[0]))
    x, y, row_h = 0, 0, 0
    for key, w, h in order:
        if x + w > TEX:
            x, y, row_h = 0, y + row_h, 0
        if y + h > TEX:
            raise ValueError("Funken-Textur zu klein")
        placed[key] = (x, y, w, h)
        x += w
        row_h = max(row_h, h)
    return placed


def build():
    body, sticks = parts()
    everything = body + sticks
    requests = []
    for name, box, faces, _, density, _ in everything:
        for face in faces:
            w, h = texel_size(box, face, density)
            requests.append(((name, face), w, h))
    placed = pack(requests)
    tex = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    elements = {}
    for name, box, faces, painter, density, emission in everything:
        element_faces = {}
        for face in faces:
            tx, ty, w, h = placed[(name, face)]
            tex.paste(painter(face, w, h), (tx, ty))
            element_faces[face] = {"uv": [round(tx / UNIT, 4), round(ty / UNIT, 4), round((tx + w) / UNIT, 4), round((ty + h) / UNIT, 4)],
                                   "texture": "#remote"}
        element = {"name": name, "from": list(box[:3]), "to": list(box[3:]), "faces": element_faces}
        if emission:
            element["light_emission"] = emission
        elements[name] = element
    body_elements = [elements[p[0]] for p in body]
    stick_elements = [elements[p[0]] for p in sticks]
    return body_elements, stick_elements, tex


def is_switch(element):
    return element["name"].startswith("switch_")


DISPLAY = {
    "thirdperson_righthand": {"rotation": [22, 0, 0], "translation": [-2.9, 3, 1.7], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"rotation": [22, 0, 0], "translation": [-2.9, 3, 1.7], "scale": [0.6, 0.6, 0.6]},
    "firstperson_righthand": {"rotation": [-34, -26, 0], "translation": [-2.5, 4.0, -2], "scale": [0.36, 0.36, 0.36]},
    "firstperson_lefthand": {"rotation": [-34, 26, 0], "translation": [2.5, 4.0, -2], "scale": [0.36, 0.36, 0.36]},
    "gui": {"rotation": [22, 0, 0], "translation": [0, 0.25, 0], "scale": [0.92, 0.92, 0.92]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 1.5, 0], "scale": [0.24, 0.24, 0.24]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    "on_shelf": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"rotation": [-78, 180, 0], "translation": [0, 5.25, 0.5], "scale": [0.4, 0.4, 0.4]},
}

GIMBAL_TEX = 16


def gimbal_texture():
    img = Image.new("RGBA", (GIMBAL_TEX, GIMBAL_TEX), METAL)
    px = img.load()
    for y in range(8):
        px[0, y] = METAL_LO
        px[GIMBAL_TEX - 1, y] = METAL_LO
        if y == 0:
            for x in range(1, GIMBAL_TEX - 1):
                px[x, y] = METAL_HI
    for y in range(8, 16):
        for x in range(GIMBAL_TEX):
            px[x, y] = AMBER if y == 8 else (RUBBER_HI if (x + y) % 2 == 0 and y > 9 else RUBBER)
    for x, y, colour in GIMBAL_REGIONS:
        px[x, y] = colour
    return img


def region(x0, y0, w, h, colour):
    return [(x, y, colour) for y in range(y0, y0 + h) for x in range(x0, x0 + w)]


SWITCH_BASE_UV = (6, 0)
SWITCH_TIP_UV = (6, 2)
BUTTON_UV = (9, 0)
BUTTON_AMBER_UV = (12, 0)
GIMBAL_REGIONS = (region(*SWITCH_BASE_UV, 2, 1, METAL_LO) + region(*SWITCH_TIP_UV, 2, 1, AMBER)
                  + region(*BUTTON_UV, 3, 2, METAL_LO) + region(*BUTTON_AMBER_UV, 3, 2, AMBER))


def item_definition():
    return {"model": {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [
            {"when": ["gui", "ground", "fixed", "on_shelf"],
             "model": {"type": "minecraft:model", "model": f"{NS}:item/remote"}},
        ],
        "fallback": {
            "type": "minecraft:composite",
            "models": [
                {"type": "minecraft:model", "model": f"{NS}:item/remote_body"},
                {"type": "minecraft:special", "base": f"{NS}:item/remote_body", "model": {"type": f"{NS}:remote_gimbals"}},
            ],
        },
    }}


def groups(elements):
    names = [e["name"] for e in elements]
    out = [{"name": "body", "origin": [8, 8, 8], "color": 0,
            "children": [i for i, n in enumerate(names) if not n.startswith("gimbal_left_") and not n.startswith("gimbal_right_")]}]
    for side, pivot in (("left", PIVOT_LEFT), ("right", PIVOT_RIGHT)):
        children = [i for i, n in enumerate(names) if n.startswith(f"gimbal_{side}_")]
        if children:
            out.append({"name": f"gimbal_{side}", "origin": list(pivot), "color": 0, "children": children})
    return out


def model(elements):
    return {
        "texture_size": [TEX, TEX],
        "textures": {"remote": f"{NS}:item/remote", "particle": f"{NS}:item/remote"},
        "elements": elements,
        "groups": groups(elements),
        "display": DISPLAY,
    }


def generate():
    body, sticks, tex = build()
    save_png(tex, ASSETS / "textures" / "item" / "remote.png")
    save_png(gimbal_texture(), ASSETS / "textures" / "entity" / "remote" / "gimbal.png")
    write_json(model(body + sticks), ASSETS / "models" / "item" / "remote.json")
    write_json(model([e for e in body if not is_switch(e)]), ASSETS / "models" / "item" / "remote_body.json")
    write_json(item_definition(), ASSETS / "items" / "remote.json")
