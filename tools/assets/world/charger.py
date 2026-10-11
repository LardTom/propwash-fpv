"""Battery charger block (propwash:battery_charger): multipart blockstate, three state models, per-bay models, textures, item.

Contract: docs/design/gameplay-loop-v2.md §11.1 and §11.4, docs/ASSETS.md "Ladegerät".
Model faces north (front at -Z); the blockstate rotates it with y = 0/90/180/270 for north/east/south/west.
Textures are 32 x 32 (2 texels per model pixel) and mostly use the default UV projection.
"""

from PIL import Image

from assetlib import ASSETS, hex_rgba, save_png, write_json

NS = "propwash"
TEX = 32
TPX = TEX / 16

STATES = ("idle", "charging", "done")
BAY_STATES = ("empty", "charging", "full")
BAY_LED = {"empty": "idle", "charging": "charging", "full": "done"}
INVENTORY_BAYS = ("full", "charging", "charging", "empty")
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
FACES = ("top", "side", "bottom", "front_idle", "front_charging", "front_done", "pack")
EMISSION = 12

SLOT_CENTERS = (3.5, 6.5, 9.5, 12.5)
SLOT_HALF_WIDTH = 1.25
SLOT_Z = (5.5, 10.5)
SLOT_FLOOR = 5.0
LID_TOP = 8.0
HOUSING = (1.0, 0.0, 2.0, 15.0, 8.0, 14.0)
PLATE = (1.5, 1.0, 1.5, 14.5, 6.0, 2.0)
DISPLAY = (2.0, 1.5, 1.4, 7.0, 3.0, 1.5)
LED_Y = (4.0, 5.0)
LED_HALF_WIDTH = 0.75
LED_Z = (1.4, 1.5)
BOUNDS = (1.0, 0.0, 1.4, 15.0, 8.0, 14.0)

ANTH = hex_rgba("343941")
ANTH_HI = hex_rgba("565e69")
ANTH_LO = hex_rgba("1b1e23")
DEEP = hex_rgba("111317")
METAL = hex_rgba("8c949e")
METAL_LO = hex_rgba("5f6770")
AMBER = hex_rgba("ffbb00")
AMBER_LO = hex_rgba("9a6a00")
LCD = hex_rgba("0e2a24")
LCD_PX = hex_rgba("7cffb2")
LED_YELLOW = hex_rgba("ffb000")
LED_GREEN = hex_rgba("3bff6a")
LED_OFF = hex_rgba("3a3f47")
RED = hex_rgba("ff3b3b")

PACK_HALF_WIDTH = 1.0
PACK_Z = (6.0, 10.0)
PACK_TOP = 9.5
PACK_WRAP = hex_rgba("f0f0f0")
PACK_WRAP_HI = hex_rgba("ffffff")
PACK_WRAP_LO = hex_rgba("b0b0b0")
PACK_LABEL = hex_rgba("4a4a4a")
PACK_COLORS = {"b1s_300_hv": 0x6B2FB3, "b4s_650": 0xF07C1E, "b6s_1100": 0xD4312A, "b6s_1300": 0x2F6FD6,
               "b6s_4000_lion": 0x1A8F8B}
INVENTORY_PACKS = ("b6s_1300", "b6s_1100", "b4s_650", "b6s_1300")
PACK_CAP = hex_rgba("1b1e23")
PACK_PLUG = hex_rgba("ffbb00")
PACK_LEAD = hex_rgba("ff3b3b")

LED_UV = (0.0, 0.0, 1.5, 1.0)
PACK_SIDE_UV = (0.0, 0.0, 4.0, 4.5)
PACK_FRONT_UV = (0.0, 0.0, 2.0, 4.5)
PACK_TOP_UV = (8.0, 0.0, 10.0, 4.0)
WALL_UV = (15.0, 0.0, 16.0, 1.5)


def slot_x(center):
    return center - SLOT_HALF_WIDTH, center + SLOT_HALF_WIDTH


def put(img, x0, y0, x1, y1, colour):
    px = img.load()
    for y in range(max(0, y0), min(img.height, y1)):
        for x in range(max(0, x0), min(img.width, x1)):
            px[x, y] = colour


def top_texture():
    img = Image.new("RGBA", (TEX, TEX), ANTH)
    put(img, 2, 4, 30, 5, ANTH_HI)
    put(img, 2, 27, 30, 28, ANTH_LO)
    put(img, 2, 4, 3, 28, ANTH_HI)
    put(img, 29, 4, 30, 28, ANTH_LO)
    put(img, 3, 3, 29, 4, ANTH_HI)
    z0, z1 = int(SLOT_Z[0] * TPX), int(SLOT_Z[1] * TPX)
    for c in SLOT_CENTERS:
        x0, x1 = slot_x(c)
        u0, u1 = int(round(x0 * TPX + 0.5)), int(round(x1 * TPX - 0.5))
        put(img, u0 - 1, z0 - 1, u1 + 1, z1 + 1, ANTH_LO)
        put(img, u0, z0, u1, z1, DEEP)
        put(img, u0, z0, u1, z0 + 1, METAL_LO)
        put(img, u0 + 1, z0, u0 + 2, z0 + 1, METAL)
        put(img, u1 - 2, z0, u1 - 1, z0 + 1, AMBER_LO)
        put(img, u0, z1 - 1, u1, z1, hex_rgba("1a1d22"))
    for u in range(4, 28, 3):
        put(img, u, 25, u + 2, 26, ANTH_LO)
    put(img, 4, 6, 8, 7, AMBER)
    put(img, 30, 0, 32, 3, DEEP)
    put(img, 30, 0, 32, 1, ANTH_LO)
    return img


def side_texture():
    img = Image.new("RGBA", (TEX, TEX), ANTH)
    put(img, 0, 16, TEX, 17, ANTH_HI)
    put(img, 0, 17, TEX, 18, hex_rgba("32373f"))
    put(img, 0, 18, TEX, 19, ANTH_LO)
    put(img, 0, 20, TEX, 21, AMBER_LO)
    for u in range(6, 26, 3):
        put(img, u, 23, u + 2, 28, DEEP)
        put(img, u, 23, u + 2, 24, ANTH_LO)
    put(img, 0, 31, TEX, 32, ANTH_LO)
    put(img, 0, 30, TEX, 31, hex_rgba("23272d"))
    put(img, 0, 0, TEX, 16, ANTH)
    return img


def bottom_texture():
    img = Image.new("RGBA", (TEX, TEX), ANTH_LO)
    for x, y in ((3, 5), (27, 5), (3, 25), (27, 25)):
        put(img, x, y, x + 2, y + 2, DEEP)
    put(img, 10, 13, 22, 19, hex_rgba("15181c"))
    return img


def front_texture(state):
    img = Image.new("RGBA", (TEX, TEX), ANTH)
    put(img, 0, 16, TEX, 17, ANTH_HI)
    put(img, 0, 17, TEX, 18, hex_rgba("32373f"))
    put(img, 0, 18, TEX, 19, ANTH_LO)
    put(img, 0, 30, TEX, 32, ANTH_LO)
    u0, u1 = int((16 - PLATE[3]) * TPX), int((16 - PLATE[0]) * TPX)
    v0, v1 = int((16 - PLATE[4]) * TPX), int((16 - PLATE[1]) * TPX)
    put(img, u0, v0, u1, v1, hex_rgba("23272d"))
    put(img, u0, v0, u1, v0 + 1, ANTH_HI)
    put(img, u0, v1 - 1, u1, v1, DEEP)
    put(img, u0, v0, u0 + 1, v1, ANTH_HI)
    put(img, u1 - 1, v0, u1, v1, DEEP)
    for c in SLOT_CENTERS:
        lu = int((16 - c) * TPX)
        put(img, lu - 2, 21, lu + 2, 25, DEEP)
    lit = {"idle": None, "charging": LED_YELLOW, "done": LED_GREEN}[state]
    led = LED_OFF if lit is None else lit
    put(img, 0, 0, 3, 2, led)
    if lit is not None:
        put(img, 0, 0, 1, 1, tuple(min(255, c + 60) for c in led[:3]) + (255,))
    du0, du1 = int((16 - DISPLAY[3]) * TPX), int((16 - DISPLAY[0]) * TPX)
    dv0, dv1 = int((16 - DISPLAY[4]) * TPX), int((16 - DISPLAY[1]) * TPX)
    put(img, du0 - 1, dv0 - 1, du1 + 1, dv1 + 1, DEEP)
    if state == "idle":
        put(img, du0, dv0, du1, dv1, hex_rgba("15191b"))
        put(img, du0 + 1, dv0 + 1, du0 + 2, dv0 + 2, hex_rgba("22282b"))
    else:
        bg, fg = (hex_rgba("2a1d00"), AMBER) if state == "charging" else (LCD, LCD_PX)
        put(img, du0, dv0, du1, dv1, bg)
        if state == "charging":
            symbol = ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2))
            filled = 2
        else:
            symbol = ((0, 1), (1, 2), (2, 1), (2, 0))
            filled = 4
        for sx, sy in symbol:
            img.putpixel((du0 + sx, dv0 + sy), fg)
        bx = du0 + 4
        for x in range(bx, bx + 5):
            img.putpixel((x, dv0), fg)
            img.putpixel((x, dv0 + 2), fg)
        img.putpixel((bx, dv0 + 1), fg)
        img.putpixel((bx + 5, dv0 + 1), fg)
        for x in range(bx + 1, bx + 1 + filled):
            img.putpixel((x, dv0 + 1), fg)
    put(img, u0 + 2, v1 - 3, u0 + 4, v1 - 2, RED if state != "idle" else hex_rgba("4a2626"))
    return img


def pack_texture():
    img = Image.new("RGBA", (TEX, TEX), PACK_CAP)
    put(img, 0, 0, 8, 9, PACK_WRAP)
    put(img, 0, 0, 8, 1, PACK_WRAP_HI)
    put(img, 0, 7, 8, 9, PACK_WRAP_LO)
    put(img, 1, 3, 7, 4, PACK_LABEL)
    put(img, 16, 0, 20, 8, PACK_CAP)
    put(img, 16, 5, 20, 7, PACK_PLUG)
    put(img, 17, 1, 18, 3, PACK_LEAD)
    put(img, 18, 1, 19, 3, hex_rgba("111317"))
    return img


def element(name, box, faces, emission=0):
    out = {"name": name, "from": list(box[:3]), "to": list(box[3:]), "faces": faces}
    if emission:
        out["light_emission"] = emission
    return out


def face(texture, uv=None, cull=None, tint=None):
    f = {"texture": texture}
    if uv is not None:
        f["uv"] = list(uv)
    if cull:
        f["cullface"] = cull
    if tint is not None:
        f["tintindex"] = tint
    return f


def elements(state):
    x0, y0, z0, x1, y1, z1 = HOUSING
    lit = state != "idle"
    out = [element("housing", (x0, y0, z0, x1, SLOT_FLOOR, z1), {
        "down": face("#bottom", cull="down"), "up": face("#top"),
        "north": face("#front"), "south": face("#side"), "east": face("#side"), "west": face("#side"),
    })]
    sz0, sz1 = SLOT_Z
    out.append(element("lid_front", (x0, SLOT_FLOOR, z0, x1, LID_TOP, sz0), {
        "up": face("#top"), "north": face("#front"), "east": face("#side"), "west": face("#side"), "south": face("#top", WALL_UV),
    }))
    out.append(element("lid_back", (x0, SLOT_FLOOR, sz1, x1, LID_TOP, z1), {
        "up": face("#top"), "south": face("#side"), "east": face("#side"), "west": face("#side"), "north": face("#top", WALL_UV),
    }))
    edges = [x0]
    for c in SLOT_CENTERS:
        a, b = slot_x(c)
        edges += [a, b]
    edges.append(x1)
    for index in range(0, len(edges), 2):
        a, b = edges[index], edges[index + 1]
        faces = {"up": face("#top")}
        if index > 0:
            faces["west"] = face("#top", WALL_UV)
        else:
            faces["west"] = face("#side")
        if index < len(edges) - 2:
            faces["east"] = face("#top", WALL_UV)
        else:
            faces["east"] = face("#side")
        out.append(element(f"lid_rib_{index // 2}", (a, SLOT_FLOOR, sz0, b, LID_TOP, sz1), faces))
    out.append(element("front_plate", PLATE, {
        "north": face("#front"), "east": face("#side"), "west": face("#side"), "up": face("#top"), "down": face("#bottom"),
    }))
    out.append(element("display", DISPLAY, {"north": face("#front")}, EMISSION if lit else 0))
    return out


def bay_center(index):
    return SLOT_CENTERS[len(SLOT_CENTERS) - 1 - index]


def bay_elements(index, bay_state, led="#led"):
    c = bay_center(index)
    lit = bay_state != "empty"
    out = [element(f"led_{index}", (c - LED_HALF_WIDTH, LED_Y[0], LED_Z[0], c + LED_HALF_WIDTH, LED_Y[1], LED_Z[1]),
                   {"north": face(led, LED_UV)}, EMISSION if lit else 0)]
    if lit:
        z0, z1 = PACK_Z
        side = face("#pack", PACK_SIDE_UV, tint=index)
        front = face("#pack", PACK_FRONT_UV, tint=index)
        out.append(element(f"pack_{index}", (c - PACK_HALF_WIDTH, SLOT_FLOOR, z0, c + PACK_HALF_WIDTH, PACK_TOP, z1), {
            "up": face("#pack", PACK_TOP_UV), "north": front, "south": front, "east": side, "west": side,
        }))
    return out


def bay_model(index, bay_state):
    return {
        "textures": {
            "particle": f"{NS}:block/battery_charger_side",
            "led": f"{NS}:block/battery_charger_front_{BAY_LED[bay_state]}",
            "pack": f"{NS}:block/battery_charger_pack",
        },
        "elements": bay_elements(index, bay_state),
    }


def inventory_model():
    elements_out = list(elements("charging"))
    for index, bay_state in enumerate(INVENTORY_BAYS):
        elements_out += bay_elements(index, bay_state, f"#led_{bay_state}")
    textures = dict(model("charging")["textures"])
    textures["pack"] = f"{NS}:block/battery_charger_pack"
    for bay_state in BAY_STATES:
        textures[f"led_{bay_state}"] = f"{NS}:block/battery_charger_front_{BAY_LED[bay_state]}"
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements_out}


def model(state):
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{NS}:block/battery_charger_side",
            "top": f"{NS}:block/battery_charger_top",
            "side": f"{NS}:block/battery_charger_side",
            "bottom": f"{NS}:block/battery_charger_bottom",
            "front": f"{NS}:block/battery_charger_front_{state}",
        },
        "elements": elements(state),
    }


def item_model():
    return {
        "parent": f"{NS}:block/battery_charger_inventory",
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 2.75, 0], "scale": [0.8, 0.8, 0.8]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


def blockstate():
    parts = []
    for facing, rot in FACINGS.items():
        for state in STATES:
            apply = {"model": f"{NS}:block/battery_charger_{state}"}
            if rot:
                apply["y"] = rot
            parts.append({"when": {"facing": facing, "state": state}, "apply": apply})
    for facing, rot in FACINGS.items():
        for index in range(len(SLOT_CENTERS)):
            for bay_state in BAY_STATES:
                apply = {"model": f"{NS}:block/battery_charger_bay_{index}_{bay_state}"}
                if rot:
                    apply["y"] = rot
                parts.append({"when": {"facing": facing, f"bay_{index}": bay_state}, "apply": apply})
    return {"multipart": parts}


def generate():
    tex = ASSETS / "textures" / "block"
    save_png(top_texture(), tex / "battery_charger_top.png")
    save_png(side_texture(), tex / "battery_charger_side.png")
    save_png(bottom_texture(), tex / "battery_charger_bottom.png")
    save_png(pack_texture(), tex / "battery_charger_pack.png")
    for state in STATES:
        save_png(front_texture(state), tex / f"battery_charger_front_{state}.png")
        write_json(model(state), ASSETS / "models" / "block" / f"battery_charger_{state}.json")
    for index in range(len(SLOT_CENTERS)):
        for bay_state in BAY_STATES:
            write_json(bay_model(index, bay_state), ASSETS / "models" / "block" / f"battery_charger_bay_{index}_{bay_state}.json")
    write_json(inventory_model(), ASSETS / "models" / "block" / "battery_charger_inventory.json")
    write_json(blockstate(), ASSETS / "blockstates" / "battery_charger.json")
    write_json(item_model(), ASSETS / "models" / "item" / "battery_charger.json")
    tints = [{"type": "minecraft:constant", "value": PACK_COLORS[key]} for key in INVENTORY_PACKS]
    write_json({"model": {"type": "minecraft:model", "model": f"{NS}:item/battery_charger", "tints": tints}},
               ASSETS / "items" / "battery_charger.json")
