"""Signal repeater block (propwash:signal_repeater): one model for floor, wall and ceiling, textures, animated LED, item.

Contract: docs/ASSETS.md "Signal-Repeater", docs/design/signal-repeater.md.
The model is built for the floor (attached at y = 0, top up); the blockstate turns it like a vanilla button:
face=floor uses y = 0/90/180/270 for north/east/south/west, face=wall adds x = 90 (top points away from the wall),
face=ceiling uses x = 180 and the facing turned by 180 degrees. No uvlock, the textures turn with the model.
Textures are 32 x 32 (2 texels per model pixel) and use the default UV projection, so every element samples the
texture where it sits in the block; the LED texture is animated (slow heartbeat).
"""

from PIL import Image

from assetlib import ASSETS, hex_rgba, save_png, write_json

NS = "propwash"
TEX = 32
TPX = TEX // 16
NAME = "signal_repeater"
EMISSION = 12
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
FACES = ("floor", "wall", "ceiling")
TEXTURES = ("base", "body_side", "body_top", "antenna")

BASE = (3.0, 0.0, 4.0, 13.0, 1.0, 12.0)
HOUSING = (4.0, 1.0, 5.0, 12.0, 3.5, 11.0)
ANTENNAS = ((5.0, 3.5, 7.5, 6.0, 8.0, 8.5), (10.0, 3.5, 7.5, 11.0, 8.0, 8.5))
LED = (7.25, 3.5, 9.25, 8.75, 3.6, 10.25)
BOUNDS = (3.0, 0.0, 4.0, 13.0, 8.0, 12.0)
ANTENNA_HEIGHT = 6.0
SHAPE_BODY = (3.0, 0.0, 4.0, 13.0, 3.5, 12.0)
SHAPE_ANTENNA = (5.0, 3.5, 7.5, 11.0, 8.0, 8.5)

ANTH = hex_rgba("343941")
ANTH_HI = hex_rgba("565e69")
ANTH_LO = hex_rgba("1b1e23")
DEEP = hex_rgba("111317")
METAL = hex_rgba("8c949e")
METAL_HI = hex_rgba("aab2bc")
METAL_LO = hex_rgba("5f6770")
AMBER = hex_rgba("ffbb00")
AMBER_LO = hex_rgba("9a6a00")
GOLD = hex_rgba("d9a520")
GOLD_LO = hex_rgba("8a6410")
RUBBER = hex_rgba("1e2126")
RUBBER_HI = hex_rgba("3a3f47")
CAP = hex_rgba("e8442a")
LED_ON = hex_rgba("3bff6a")
LED_ON_HI = hex_rgba("b8ffcb")
LED_DIM = hex_rgba("1f7a3a")
LED_DIM_HI = hex_rgba("2f9a4e")


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
    put(img, 0, 0, TEX, TEX, METAL)
    put(img, 6, 8, 26, 9, METAL_HI)
    put(img, 6, 23, 26, 24, METAL_LO)
    put(img, 6, 8, 7, 24, METAL_HI)
    put(img, 25, 8, 26, 24, METAL_LO)
    for x, y in ((7, 9), (23, 9), (7, 21), (23, 21)):
        put(img, x, y, x + 2, y + 2, METAL_LO)
        put(img, x, y, x + 1, y + 1, DEEP)
    v0, v1 = rows(1.0, 0.0)
    put(img, 0, v0, TEX, v1, METAL)
    put(img, 0, v0, TEX, v0 + 1, METAL_HI)
    put(img, 0, v1 - 1, TEX, v1, METAL_LO)
    return img


def body_side_texture():
    img = Image.new("RGBA", (TEX, TEX), ANTH)
    v0, v1 = rows(3.5, 1.0)
    put(img, 0, v0, TEX, v0 + 1, ANTH_HI)
    put(img, 0, v0 + 2, TEX, v0 + 3, AMBER)
    put(img, 0, v1 - 1, TEX, v1, ANTH_LO)
    put(img, 0, v0 + 3, TEX, v0 + 4, AMBER_LO)
    return img


def body_top_texture():
    img = Image.new("RGBA", (TEX, TEX), ANTH)
    x0, z0, x1, z1 = int(HOUSING[0] * TPX), int(HOUSING[2] * TPX), int(HOUSING[3] * TPX), int(HOUSING[5] * TPX)
    put(img, x0, z0, x1, z0 + 1, ANTH_HI)
    put(img, x0, z0, x0 + 1, z1, ANTH_HI)
    put(img, x0, z1 - 1, x1, z1, ANTH_LO)
    put(img, x1 - 1, z0, x1, z1, ANTH_LO)
    put(img, x0 + 2, z1 - 3, x0 + 5, z1 - 2, AMBER)
    for u in range(x0 + 5, x1 - 4, 2):
        put(img, u, z0 + 2, u + 1, z0 + 4, DEEP)
    lx0, lz0, lx1, lz1 = int(LED[0] * TPX), int(LED[2] * TPX), int(round(LED[3] * TPX)), int(round(LED[5] * TPX))
    put(img, lx0 - 1, lz0 - 1, lx1 + 1, lz1 + 1, DEEP)
    return img


def antenna_texture():
    img = Image.new("RGBA", (TEX, TEX), RUBBER)
    top = rows(8.0, 7.25)
    sleeve = rows(7.25, 4.5)
    sma = rows(4.5, 3.5)
    put(img, 0, top[0], TEX, top[1], CAP)
    put(img, 0, sleeve[0], TEX, sleeve[1], RUBBER)
    for v in range(sleeve[0] + 1, sleeve[1], 2):
        put(img, 0, v, TEX, v + 1, RUBBER_HI)
    put(img, 0, sma[0], TEX, sma[1], GOLD)
    put(img, 0, sma[1] - 1, TEX, sma[1], GOLD_LO)
    z0, z1 = int(ANTENNAS[0][2] * TPX), int(ANTENNAS[0][5] * TPX)
    put(img, 0, z0, TEX, z1, CAP)
    return img


def led_texture():
    img = Image.new("RGBA", (16, 32), LED_DIM)
    put(img, 0, 0, 16, 16, LED_ON)
    put(img, 2, 2, 6, 5, LED_ON_HI)
    put(img, 0, 16, 16, 32, LED_DIM)
    put(img, 2, 18, 6, 21, LED_DIM_HI)
    return img


def led_meta():
    return {"animation": {"frametime": 1, "interpolate": False, "frames": [{"index": 0, "time": 30}, {"index": 1, "time": 6}]}}


def element(name, box, faces, emission=0):
    out = {"name": name, "from": list(box[:3]), "to": list(box[3:]), "faces": faces}
    if emission:
        out["light_emission"] = emission
    return out


def face(texture, cull=None):
    f = {"texture": texture}
    if cull:
        f["cullface"] = cull
    return f


def sides(texture):
    return {d: face(texture) for d in ("north", "south", "east", "west")}


def elements():
    out = [element("base", BASE, {"down": face("#base", "down"), "up": face("#base"), **sides("#base")}),
           element("housing", HOUSING, {"up": face("#body_top"), **sides("#body_side")})]
    for index, box in enumerate(ANTENNAS):
        out.append(element(f"antenna_{index}", box, {"up": face("#antenna"), **sides("#antenna")}))
    out.append(element("led", LED, {"up": face("#led"), **sides("#led")}, EMISSION))
    return out


def model():
    textures = {"particle": f"{NS}:block/{NAME}_body_side"}
    for key in TEXTURES:
        textures[key] = f"{NS}:block/{NAME}_{key}"
    textures["led"] = f"{NS}:block/{NAME}_led"
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements()}


def rotation(face_name, facing):
    y = FACINGS[facing]
    if face_name == "floor":
        return 0, y
    if face_name == "wall":
        return 90, y
    return 180, (y + 180) % 360


def blockstate():
    variants = {}
    for face_name in FACES:
        for facing in FACINGS:
            x, y = rotation(face_name, facing)
            apply = {"model": f"{NS}:block/{NAME}"}
            if x:
                apply["x"] = x
            if y:
                apply["y"] = y
            variants[f"face={face_name},facing={facing}"] = apply
    return {"variants": variants}


def item_model():
    return {
        "parent": f"{NS}:block/{NAME}",
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 1.5, 0], "scale": [1.0, 1.0, 1.0]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.45, 0.45, 0.45]},
            "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -5], "scale": [1.1, 1.1, 1.1]},
            "on_shelf": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 3.5, 0], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 3.5, 0], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 5, 0], "scale": [0.55, 0.55, 0.55]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 5, 0], "scale": [0.55, 0.55, 0.55]},
        },
    }


def generate():
    tex = ASSETS / "textures" / "block"
    save_png(base_texture(), tex / f"{NAME}_base.png")
    save_png(body_side_texture(), tex / f"{NAME}_body_side.png")
    save_png(body_top_texture(), tex / f"{NAME}_body_top.png")
    save_png(antenna_texture(), tex / f"{NAME}_antenna.png")
    save_png(led_texture(), tex / f"{NAME}_led.png")
    write_json(led_meta(), tex / f"{NAME}_led.png.mcmeta")
    write_json(model(), ASSETS / "models" / "block" / f"{NAME}.json")
    write_json(blockstate(), ASSETS / "blockstates" / f"{NAME}.json")
    write_json(item_model(), ASSETS / "models" / "item" / f"{NAME}.json")
    write_json({"model": {"type": "minecraft:model", "model": f"{NS}:item/{NAME}"}}, ASSETS / "items" / f"{NAME}.json")
