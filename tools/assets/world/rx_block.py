"""RX block (propwash:rx_block): receiver box with two antennas and a status LED, textures, item.

Contract: docs/design/rx-block.md. Run: python3 tools/assets/world/rx_block.py (needs Pillow).
Multipart blockstate: one housing model per LED state (link=none, linked, lost) plus one signal meter model per power
level (rx_block_power_0 ... _15, each with its own texture, glow = power), so resource packs can restyle every level.
facing (north, east, south, west) turns the whole block (y = 0, 90, 180, 270); the models face north. output=front adds
one output plug (rx_block_output, arrow on top) on the facing side, output=all (blocks saved before the block had a
direction) adds the plug on all four sides. The item uses rx_block_inventory (housing without link, the meter at power 0
and the plug facing north). Textures are 32 x 32 (2 texels per model pixel) and use the default UV projection like the
signal repeater; the meter and plug faces map explicit UVs.
Creative RX block (propwash:creative_rx_block): same blockstate layout and models; its housing models only swap the
body textures (purple stripe instead of amber), the meter and plug models are shared with the RX block.
"""

from PIL import Image

from assetlib import ASSETS, hex_rgba, save_png, write_json

NS = "propwash"
TEX = 32
TPX = TEX // 16
NAME = "rx_block"
CREATIVE = "creative_rx_block"
CREATIVE_TEXTURES = ("body_side", "body_top")
EMISSION = 12
LINKS = {"none": "", "linked": "_linked", "lost": "_lost"}
TEXTURES = ("base", "body_side", "body_top", "antenna")

BASE = (2.0, 0.0, 2.0, 14.0, 1.0, 14.0)
HOUSING = (3.0, 1.0, 3.0, 13.0, 6.0, 13.0)
ANTENNAS = ((4.0, 6.0, 11.0, 5.0, 12.5, 12.0), (11.0, 6.0, 11.0, 12.0, 12.5, 12.0))
LED = (7.0, 6.0, 4.5, 9.0, 6.75, 6.5)
SHAPE = (2.0, 0.0, 2.0, 14.0, 6.0, 14.0)
POWER_LEVELS = 16
SEGMENTS = POWER_LEVELS - 1
METER = (3.75, 6.0, 7.25, 12.25, 6.5, 9.75)
METER_UV = {
    "up": (0.0, 0.0, 8.5, 2.5),
    "north": (0.0, 2.5, 8.5, 3.0),
    "south": (0.0, 3.0, 8.5, 3.5),
    "east": (0.0, 3.5, 2.5, 4.0),
    "west": (0.0, 3.5, 2.5, 4.0),
}
METER_TICKS = (4, 9, 14)
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
PLUG = (6.5, 1.0, 1.0, 9.5, 3.5, 3.0)
PLUG_UV = {
    "up": (0.0, 0.0, 3.0, 2.0),
    "north": (0.0, 2.5, 3.0, 5.0),
    "east": (4.0, 2.5, 6.0, 5.0),
    "west": (4.0, 2.5, 6.0, 5.0),
    "down": (4.0, 0.0, 7.0, 2.0),
}

ANTH = hex_rgba("2e3238")
ANTH_HI = hex_rgba("4d545e")
ANTH_LO = hex_rgba("181b1f")
DEEP = hex_rgba("0f1114")
METAL = hex_rgba("8c949e")
METAL_HI = hex_rgba("aab2bc")
METAL_LO = hex_rgba("5f6770")
AMBER = hex_rgba("ffbb00")
AMBER_LO = hex_rgba("9a6a00")
PURPLE = hex_rgba("c060ff")
PURPLE_LO = hex_rgba("7a35b8")
RED_DUST = hex_rgba("b3201c")
COAX = hex_rgba("1c1e22")
COAX_HI = hex_rgba("33363c")
TIP = hex_rgba("e6e6e6")
TIP_LO = hex_rgba("b5b5b5")
METER_FRAME = hex_rgba("181b1f")
METER_TICK = hex_rgba("5f6770")
METER_OFF = hex_rgba("2c0f0d")
METER_OFF_HI = hex_rgba("3a1613")
METER_LOW = (0xa8, 0x14, 0x0c)
METER_HIGH = (0xff, 0x5c, 0x30)
COPPER = hex_rgba("c06c3e")
COPPER_HI = hex_rgba("e39a6c")
COPPER_LO = hex_rgba("7a3f22")
PLUG_RED = hex_rgba("e3241b")
PLUG_RED_HI = hex_rgba("ff6a55")
LED_COLOURS = {
    "none": (hex_rgba("1e2e1e"), hex_rgba("34463a")),
    "linked": (hex_rgba("3bff6a"), hex_rgba("b8ffcb")),
    "lost": (hex_rgba("ff3b30"), hex_rgba("ffb3ad")),
}


def put(img, x0, y0, x1, y1, colour):
    px = img.load()
    for y in range(max(0, y0), min(img.height, y1)):
        for x in range(max(0, x0), min(img.width, x1)):
            px[x, y] = colour


def rows(y_top, y_bottom):
    """Texel rows for a model height range (v grows downwards, v = 16 - y)."""
    return int(round((16 - y_top) * TPX)), int(round((16 - y_bottom) * TPX))


def base_texture():
    img = Image.new("RGBA", (TEX, TEX), METAL)
    x0, x1 = int(BASE[0] * TPX), int(BASE[3] * TPX)
    put(img, x0, x0, x1, x0 + 1, METAL_HI)
    put(img, x0, x1 - 1, x1, x1, METAL_LO)
    put(img, x0, x0, x0 + 1, x1, METAL_HI)
    put(img, x1 - 1, x0, x1, x1, METAL_LO)
    for x, y in ((x0 + 1, x0 + 1), (x1 - 3, x0 + 1), (x0 + 1, x1 - 3), (x1 - 3, x1 - 3)):
        put(img, x, y, x + 2, y + 2, METAL_LO)
        put(img, x, y, x + 1, y + 1, DEEP)
    v0, v1 = rows(1.0, 0.0)
    put(img, 0, v0, TEX, v0 + 1, METAL_HI)
    put(img, 0, v1 - 1, TEX, v1, METAL_LO)
    return img


def body_side_texture(stripe=AMBER, stripe_lo=AMBER_LO):
    img = Image.new("RGBA", (TEX, TEX), ANTH)
    v0, v1 = rows(6.0, 1.0)
    put(img, 0, v0, TEX, v0 + 1, ANTH_HI)
    put(img, 0, v0 + 3, TEX, v0 + 4, stripe)
    put(img, 0, v0 + 4, TEX, v0 + 5, stripe_lo)
    put(img, 0, v1 - 1, TEX, v1, ANTH_LO)
    for u in range(9, 23, 3):
        put(img, u, v0 + 6, u + 2, v1 - 2, DEEP)
    put(img, 24, v1 - 4, 26, v1 - 2, RED_DUST)
    return img


def body_top_texture(stripe=AMBER):
    img = Image.new("RGBA", (TEX, TEX), ANTH)
    x0, z0, x1, z1 = int(HOUSING[0] * TPX), int(HOUSING[2] * TPX), int(HOUSING[3] * TPX), int(HOUSING[5] * TPX)
    put(img, x0, z0, x1, z0 + 1, ANTH_HI)
    put(img, x0, z0, x0 + 1, z1, ANTH_HI)
    put(img, x0, z1 - 1, x1, z1, ANTH_LO)
    put(img, x1 - 1, z0, x1, z1, ANTH_LO)
    lx0, lz0, lx1, lz1 = int(LED[0] * TPX), int(LED[2] * TPX), int(LED[3] * TPX), int(LED[5] * TPX)
    put(img, lx0 - 1, lz0 - 1, lx1 + 1, lz1 + 1, DEEP)
    put(img, x0 + 3, z0 + 18, x1 - 3, z0 + 19, stripe)
    for u in range(x0 + 4, x1 - 4, 2):
        put(img, u, z0 + 10, u + 1, z0 + 15, DEEP)
    for ax in ANTENNAS:
        ax0, az0 = int(ax[0] * TPX), int(ax[2] * TPX)
        put(img, ax0 - 1, az0 - 1, ax0 + 3, az0 + 3, ANTH_LO)
    return img


def antenna_texture():
    img = Image.new("RGBA", (TEX, TEX), COAX)
    tip = rows(12.5, 9.5)
    put(img, 0, tip[0], TEX, tip[1], TIP)
    for v in range(tip[0] + 1, tip[1], 2):
        put(img, 0, v, TEX, v + 1, TIP_LO)
    cable = rows(9.5, 6.0)
    for v in range(cable[0], cable[1], 3):
        put(img, 0, v, TEX, v + 1, COAX_HI)
    return img


def led_texture(link):
    core, glint = LED_COLOURS[link]
    img = Image.new("RGBA", (16, 16), core)
    put(img, 3, 3, 7, 6, glint)
    return img


def meter_colour(segment):
    t = segment / (SEGMENTS - 1)
    return tuple(round(a + (b - a) * t) for a, b in zip(METER_LOW, METER_HIGH)) + (255,)


def glint(colour):
    return tuple(round(c + (255 - c) * 0.35) for c in colour[:3]) + (255,)


def meter_texture(power):
    img = Image.new("RGBA", (TEX, TEX), METER_FRAME)
    for segment in range(SEGMENTS):
        lit = segment < power
        base = meter_colour(segment) if lit else METER_OFF
        top = glint(base) if lit else METER_OFF_HI
        east = SEGMENTS - segment
        put(img, east, 1, east + 1, 2, top)
        put(img, east, 2, east + 1, 4, base)
        put(img, 1 + segment, 5, 2 + segment, 6, base)
        put(img, east, 6, east + 1, 7, base)
    for segment in METER_TICKS:
        east = SEGMENTS - segment
        put(img, east, 0, east + 1, 1, METER_TICK)
        put(img, east, 4, east + 1, 5, METER_TICK)
    return img


def plug_texture():
    img = Image.new("RGBA", (TEX, TEX), COPPER)
    up_w, up_h = 6, 4
    put(img, 0, 0, up_w, up_h, COPPER_LO)
    for row, (x0, x1) in enumerate(((2, 4), (1, 5), (0, 6))):
        put(img, x0, row, x1, row + 1, PLUG_RED_HI if row == 0 else PLUG_RED)
    put(img, 2, 3, 4, 4, PLUG_RED)
    put(img, 0, 5, 6, 10, COPPER_LO)
    put(img, 1, 6, 5, 9, COPPER)
    put(img, 2, 7, 4, 8, PLUG_RED_HI)
    put(img, 8, 5, 12, 10, COPPER)
    put(img, 8, 5, 12, 6, COPPER_HI)
    put(img, 8, 9, 12, 10, COPPER_LO)
    put(img, 8, 0, 14, 4, COPPER_LO)
    return img


def element(name, box, faces, emission=0):
    out = {"name": name, "from": list(box[:3]), "to": list(box[3:]), "faces": faces}
    if emission:
        out["light_emission"] = emission
    return out


def face(texture, cull=None, uv=None):
    f = {"texture": texture}
    if uv is not None:
        f["uv"] = list(uv)
    if cull:
        f["cullface"] = cull
    return f


def sides(texture):
    return {d: face(texture) for d in ("north", "south", "east", "west")}


def elements(link):
    out = [element("base", BASE, {"down": face("#base", "down"), "up": face("#base"), **sides("#base")}),
           element("housing", HOUSING, {"up": face("#body_top"), **sides("#body_side")})]
    for index, box in enumerate(ANTENNAS):
        out.append(element(f"antenna_{index}", box, {"up": face("#antenna"), **sides("#antenna")}))
    out.append(element("led", LED, {"up": face("#led"), **sides("#led")}, 0 if link == "none" else EMISSION))
    return out


def model(link):
    textures = {"particle": f"{NS}:block/{NAME}_body_side"}
    for key in TEXTURES:
        textures[key] = f"{NS}:block/{NAME}_{key}"
    textures["led"] = f"{NS}:block/{NAME}_led{LINKS[link] or '_off'}"
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements(link)}


def meter_element(power, texture="#meter"):
    return element("meter", METER, {side: face(texture, uv=uv) for side, uv in METER_UV.items()}, power)


def plug_model():
    return {
        "textures": {"particle": f"{NS}:block/{NAME}_body_side", "plug": f"{NS}:block/{NAME}_output"},
        "elements": [element("output", PLUG, {side: face("#plug", uv=uv) for side, uv in PLUG_UV.items()})],
    }


def power_model(power):
    return {
        "textures": {"particle": f"{NS}:block/{NAME}_body_side", "meter": f"{NS}:block/{NAME}_power_{power}"},
        "elements": [meter_element(power)],
    }


def inventory_model():
    out = model("none")
    out["textures"]["meter"] = f"{NS}:block/{NAME}_power_0"
    out["textures"]["plug"] = f"{NS}:block/{NAME}_output"
    out["elements"].append(meter_element(0))
    out["elements"].append(plug_model()["elements"][0])
    return out


def apply(model_name, y):
    out = {"model": f"{NS}:block/{model_name}"}
    if y:
        out["y"] = y
    return out


def blockstate(housing=NAME):
    parts = []
    for facing, y in FACINGS.items():
        parts += [{"when": {"facing": facing, "link": link}, "apply": apply(f"{housing}{suffix}", y)} for link, suffix in LINKS.items()]
    for facing, y in FACINGS.items():
        parts += [{"when": {"facing": facing, "power": str(power)}, "apply": apply(f"{NAME}_power_{power}", y)} for power in range(POWER_LEVELS)]
    parts += [{"when": {"facing": facing, "output": "front"}, "apply": apply(f"{NAME}_output", y)} for facing, y in FACINGS.items()]
    parts += [{"when": {"output": "all"}, "apply": apply(f"{NAME}_output", y)} for y in FACINGS.values()]
    return {"multipart": parts}


def item_model():
    return {
        "parent": f"{NS}:block/{NAME}_inventory",
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 1.5, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.38, 0.38, 0.38]},
            "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -4], "scale": [1.0, 1.0, 1.0]},
            "on_shelf": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 4, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 4, 0], "scale": [0.5, 0.5, 0.5]},
        },
    }


def creative_textures():
    textures = {key: f"{NS}:block/{CREATIVE}_{key}" for key in CREATIVE_TEXTURES}
    textures["particle"] = textures["body_side"]
    return textures


def creative_model(parent):
    return {"parent": parent, "textures": creative_textures()}


def generate_creative():
    tex = ASSETS / "textures" / "block"
    save_png(body_side_texture(PURPLE, PURPLE_LO), tex / f"{CREATIVE}_body_side.png")
    save_png(body_top_texture(PURPLE), tex / f"{CREATIVE}_body_top.png")
    for suffix in (*LINKS.values(), "_inventory"):
        write_json(creative_model(f"{NS}:block/{NAME}{suffix}"), ASSETS / "models" / "block" / f"{CREATIVE}{suffix}.json")
    write_json(blockstate(CREATIVE), ASSETS / "blockstates" / f"{CREATIVE}.json")
    write_json(creative_model(f"{NS}:item/{NAME}"), ASSETS / "models" / "item" / f"{CREATIVE}.json")
    write_json({"model": {"type": "minecraft:model", "model": f"{NS}:item/{CREATIVE}"}}, ASSETS / "items" / f"{CREATIVE}.json")


def generate():
    tex = ASSETS / "textures" / "block"
    save_png(base_texture(), tex / f"{NAME}_base.png")
    save_png(body_side_texture(), tex / f"{NAME}_body_side.png")
    save_png(body_top_texture(), tex / f"{NAME}_body_top.png")
    save_png(antenna_texture(), tex / f"{NAME}_antenna.png")
    for link, suffix in LINKS.items():
        save_png(led_texture(link), tex / f"{NAME}_led{suffix or '_off'}.png")
        write_json(model(link), ASSETS / "models" / "block" / f"{NAME}{suffix}.json")
    for power in range(POWER_LEVELS):
        save_png(meter_texture(power), tex / f"{NAME}_power_{power}.png")
        write_json(power_model(power), ASSETS / "models" / "block" / f"{NAME}_power_{power}.json")
    save_png(plug_texture(), tex / f"{NAME}_output.png")
    write_json(plug_model(), ASSETS / "models" / "block" / f"{NAME}_output.json")
    write_json(inventory_model(), ASSETS / "models" / "block" / f"{NAME}_inventory.json")
    write_json(blockstate(), ASSETS / "blockstates" / f"{NAME}.json")
    write_json(item_model(), ASSETS / "models" / "item" / f"{NAME}.json")
    write_json({"model": {"type": "minecraft:model", "model": f"{NS}:item/{NAME}"}}, ASSETS / "items" / f"{NAME}.json")
    generate_creative()


if __name__ == "__main__":
    generate()
