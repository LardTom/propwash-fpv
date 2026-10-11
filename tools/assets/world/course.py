"""Course elements (race): race flag, slalom pole and dive cube - piece models, textures and item icons; icon of the freeform gate.

Contract: docs/design/course-elements.md and docs/ASSETS.md "Kurs-Elemente".
Pieces use the gate scale (8 model units per block, origin at (8, 0, 8)); the renderer fits them with the medium-gate
fit like the gate pieces and stacks or stretches them to the element size. +X is the pilot's left, +Z the flight
direction (the north face is the side the pilot sees when flying towards the element).
"""

import math

from PIL import Image, ImageDraw

from assetlib import ASSETS, hex_rgba, load_palette, save_png, write_json

NS = "propwash"
PX = 8
THICK = 0.25
TEX = 32
FLAG_TEX = 64

PAL = load_palette()


def col(symbol):
    return PAL[symbol]


def shade(rgba, factor):
    r, g, b, a = rgba
    return (max(0, min(255, round(r * factor))), max(0, min(255, round(g * factor))), max(0, min(255, round(b * factor))), a)


TUBE_SHADE = (0.72, 0.86, 1.0, 1.08, 1.08, 1.0, 0.86, 0.72)

POLE_REGIONS = {
    "slalom": (0, 0, 8, 16),
    "flagpole": (8, 0, 16, 16),
    "cap": (16, 0, 24, 8),
    "collar": (24, 0, 32, 8),
    "base_top": (16, 8, 32, 24),
    "base_side": (0, 16, 16, 18),
    "bar_v": (0, 18, 8, 32),
    "bar_h": (8, 24, 22, 32),
    "corner": (24, 24, 32, 32),
}
FLAG_REGIONS = {
    "cloth": (0, 0, 32, 64),
    "hem": (32, 0, 34, 64),
    "edge": (34, 0, 36, 64),
}
LIGHT_REGIONS = {
    "led_v": (0, 0, 2, 16),
    "led_h": (4, 0, 20, 2),
    "edge": (0, 20, 2, 22),
}
LIGHT_TEX = 32


def uv(region, regions, size, flip_u=False):
    x0, y0, x1, y1 = regions[region]
    unit = size / 16.0
    u0, v0, u1, v1 = x0 / unit, y0 / unit, x1 / unit, y1 / unit
    if flip_u:
        u0, u1 = u1, u0
    return [round(u0, 4), round(v0, 4), round(u1, 4), round(v1, 4)]


def pole_face(region, flip_u=False):
    return {"uv": uv(region, POLE_REGIONS, TEX, flip_u), "texture": "#pole"}


def flag_face(region, flip_u=False):
    return {"uv": uv(region, FLAG_REGIONS, FLAG_TEX, flip_u), "texture": "#flag"}


def light_face(region):
    return {"uv": uv(region, LIGHT_REGIONS, LIGHT_TEX), "texture": "#lights", "tintindex": 0}


def to_px(x, y, z):
    return [round(8 + x * PX, 4), round(y * PX, 4), round(8 + z * PX, 4)]


def element(box, faces, name, emissive=False):
    x0, y0, z0, x1, y1, z1 = box
    out = {"name": name, "from": to_px(x0, y0, z0), "to": to_px(x1, y1, z1), "faces": faces}
    if emissive:
        out["light_emission"] = 15
    return out


def tube(width, height, base, rnd_seed, band=None):
    img = Image.new("RGBA", (width, height))
    for y in range(height):
        for x in range(width):
            c = base if band is None else band(y)
            factor = TUBE_SHADE[x * 8 // width]
            if y == 0:
                factor *= 1.06
            img.putpixel((x, y), shade(c, factor * (1.0 + ((x * 7 + y * 13 + rnd_seed) % 5 - 2) * 0.012)))
    return img


def gen_pole_texture():
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    red = col("R")
    white = hex_rgba("eef1f4")

    def put(region, patch):
        x0, y0, x1, y1 = POLE_REGIONS[region]
        img.paste(patch, (x0, y0))

    put("slalom", tube(8, 16, red, 1, band=lambda y: red if y < 8 else white))
    put("flagpole", tube(8, 16, hex_rgba("2b3038"), 3))
    cap = Image.new("RGBA", (8, 8), col("g"))
    for y in range(8):
        for x in range(8):
            if x in (0, 7) or y in (0, 7):
                cap.putpixel((x, y), col("k"))
            elif x in (1, 6) or y in (1, 6):
                cap.putpixel((x, y), col("G"))
    put("cap", cap)
    collar = Image.new("RGBA", (8, 8), col("s"))
    for y in range(8):
        for x in range(8):
            if y in (0, 7):
                collar.putpixel((x, y), col("g"))
            elif (x + y) % 4 == 0:
                collar.putpixel((x, y), col("S"))
    put("collar", collar)
    base = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 8.2:
                c = col("k")
            elif d > 6.8:
                c = col("A") if (x + y) % 2 == 0 else col("a")
            elif d > 5.6:
                c = col("K")
            else:
                c = col("c") if ((x // 2) + (y // 2)) % 2 == 0 else col("C")
            base.putpixel((x, y), c)
    put("base_top", base)
    side = Image.new("RGBA", (16, 2), col("a"))
    for x in range(16):
        side.putpixel((x, 1), col("o"))
    put("base_side", side)
    put("bar_v", tube(8, 14, hex_rgba("2f353d"), 5))
    put("bar_h", tube(8, 14, hex_rgba("2f353d"), 5).transpose(Image.ROTATE_90))
    corner = Image.new("RGBA", (8, 8), col("A"))
    for y in range(8):
        for x in range(8):
            if x in (0, 7) or y in (0, 7):
                corner.putpixel((x, y), col("o"))
            elif x in (1, 6) or y in (1, 6):
                corner.putpixel((x, y), col("a"))
    corner.putpixel((3, 3), col("S"))
    corner.putpixel((4, 4), col("S"))
    put("corner", corner)
    save_png(img, ASSETS / "textures" / "item" / "course" / "pole.png")


def arrow_cloth():
    w, h = 32, 64
    cloth = Image.new("RGBA", (w, h), col("A"))
    draw = ImageDraw.Draw(cloth)
    for y in range(h):
        for x in range(w):
            stripe = ((x + y) // 3) % 2 == 0
            cloth.putpixel((x, y), shade(col("A"), 1.04 if stripe else 0.97))
    dark = col("K")
    for y in range(h):
        for x in (0, 1, w - 2, w - 1):
            cloth.putpixel((x, y), shade(col("a"), 0.9))
    for x in range(w):
        for y in (0, 1, h - 2, h - 1):
            cloth.putpixel((x, y), shade(col("a"), 0.9))
    cx, cy = 16.0, 30.0
    radius = 8.0
    for step in range(0, 181):
        a = math.radians(step)
        for r in (radius - 1.5, radius - 0.5, radius + 0.5, radius + 1.5):
            x = cx + math.cos(a) * r
            y = cy - math.sin(a) * r
            if 0 <= x < w and 0 <= y < h:
                cloth.putpixel((int(x), int(y)), dark)
    for y in range(int(cy), int(cy) + 14):
        for dx in (-1.5, -0.5, 0.5, 1.5):
            cloth.putpixel((int(cx + radius + dx), y), dark)
    for y in range(int(cy), int(cy) + 8):
        for dx in (-1.5, -0.5, 0.5, 1.5):
            cloth.putpixel((int(cx - radius + dx), y), dark)
    tip_x, tip_y = cx - radius, cy + 15
    draw.polygon([(tip_x - 5, tip_y - 7), (tip_x + 5, tip_y - 7), (tip_x, tip_y)], fill=dark)
    for y in range(6, 14):
        for x in range(10, 22):
            if (x // 2 + y // 2) % 2 == 0:
                cloth.putpixel((x, y), col("K"))
            else:
                cloth.putpixel((x, y), hex_rgba("eef1f4"))
    for y in range(h - 14, h - 6):
        for x in range(10, 22):
            if (x // 2 + y // 2) % 2 == 0:
                cloth.putpixel((x, y), col("K"))
            else:
                cloth.putpixel((x, y), hex_rgba("eef1f4"))
    return cloth


def gen_flag_texture():
    img = Image.new("RGBA", (FLAG_TEX, FLAG_TEX), (0, 0, 0, 0))
    img.paste(arrow_cloth(), (0, 0))
    hem = Image.new("RGBA", (2, 64), col("k"))
    for y in range(0, 64, 4):
        hem.putpixel((0, y), col("S"))
    img.paste(hem, (32, 0))
    img.paste(Image.new("RGBA", (2, 64), shade(col("a"), 0.9)), (34, 0))
    save_png(img, ASSETS / "textures" / "item" / "course" / "flag.png")


def tube_faces(region, cap="cap"):
    return {
        "north": pole_face(region), "south": pole_face(region), "east": pole_face(region), "west": pole_face(region),
        "up": pole_face(cap), "down": pole_face(cap),
    }


def pole_elements(region, length):
    t = THICK / 2.0
    return [element((-t, 0.0, -t, t, length, t), tube_faces(region), "tube")]


def base_elements():
    r = 0.375
    c = 0.1875
    plate = {
        "up": pole_face("base_top"), "down": pole_face("base_top"),
        "north": pole_face("base_side"), "south": pole_face("base_side"), "east": pole_face("base_side"), "west": pole_face("base_side"),
    }
    collar = {
        "up": pole_face("collar"), "north": pole_face("collar"), "south": pole_face("collar"), "east": pole_face("collar"),
        "west": pole_face("collar"),
    }
    return [element((-r, 0.0, -r, r, 0.125, r), plate, "plate"), element((-c, 0.125, -c, c, 0.3125, c), collar, "collar")]


def cap_elements():
    c = 0.15625
    faces = {name: light_face("edge") for name in ("north", "south", "east", "west", "up", "down")}
    return [element((-c, 0.0, -c, c, 0.125, c), faces, "light", emissive=True)]


def cloth_elements(sign):
    t = THICK / 2.0
    d = 0.03125
    width = 1.0
    height = 2.0
    x0, x1 = (t, t + width) if sign > 0 else (-t - width, -t)
    flip = sign < 0
    faces = {
        "north": flag_face("cloth", flip), "south": flag_face("cloth", not flip),
        "east": flag_face("hem"), "west": flag_face("hem"),
        "up": flag_face("edge"), "down": flag_face("edge"),
    }
    clip = {name: pole_face("collar") for name in ("north", "south", "east", "west", "up", "down")}
    cx = t + 0.03125 if sign > 0 else -t - 0.09375
    return [
        element((x0, 0.0, -d, x1, height, d), faces, "cloth"),
        element((cx, height - 0.125, -0.0625, cx + 0.0625, height, 0.0625), clip, "clip_top"),
        element((cx, 0.0, -0.0625, cx + 0.0625, 0.125, 0.0625), clip, "clip_bottom"),
    ]


def bar_faces():
    return {
        "north": pole_face("bar_h"), "south": pole_face("bar_h"), "up": pole_face("bar_h"), "down": pole_face("bar_h"),
        "east": pole_face("corner"), "west": pole_face("corner"),
    }


def post_faces():
    return {
        "north": pole_face("bar_v"), "south": pole_face("bar_v"), "east": pole_face("bar_v"), "west": pole_face("bar_v"),
        "up": pole_face("corner"), "down": pole_face("corner"),
    }


def cube_bar(length):
    t = THICK / 2.0
    return [element((0.0, -t, -t, length, t, t), bar_faces(), "bar")]


def cube_post(length):
    t = THICK / 2.0
    return [element((-t, 0.0, -t, t, length, t), post_faces(), "post")]


def cube_corner():
    c = THICK / 2.0 + 0.03125
    faces = {name: pole_face("corner") for name in ("north", "south", "east", "west", "up", "down")}
    return [element((-c, -c, -c, c, c, c), faces, "corner")]


def cube_led(length):
    t = THICK / 2.0
    lt = 0.03125
    w = 0.0625
    faces = {"up": light_face("led_h"), "north": light_face("edge"), "south": light_face("edge")}
    return [element((0.0, t, -w, length, t + lt, w), faces, "led", emissive=True)]


def race_item(name, model, tinted=False):
    definition = {"type": "minecraft:model", "model": model}
    if tinted:
        definition["tints"] = [{"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]
    write_json({"model": definition}, ASSETS / "items" / "race" / f"{name}.json")


def write_piece(name, textures, elements, tinted=False):
    model = {"textures": dict(textures), "elements": elements}
    model["textures"]["particle"] = next(iter(textures.values()))
    write_json(model, ASSETS / "models" / "course" / f"{name}.json")
    race_item(f"course_{name}", f"{NS}:course/{name}", tinted=tinted)


POLE_TEXTURES = {"pole": f"{NS}:item/course/pole"}
FLAG_TEXTURES = {"flag": f"{NS}:item/course/flag", "pole": f"{NS}:item/course/pole"}
LIGHT_TEXTURES = {"lights": f"{NS}:item/gate/lights"}

PIECES = (
    "slalom_tube", "slalom_tube_half", "flag_tube", "flag_tube_half", "pole_base", "pole_light", "flag_cloth_left", "flag_cloth_right",
    "cube_bar", "cube_bar_half", "cube_post", "cube_post_half", "cube_corner", "cube_led", "cube_led_half",
)
TINTED = ("pole_light", "cube_led", "cube_led_half")


def gen_pieces():
    write_piece("slalom_tube", POLE_TEXTURES, pole_elements("slalom", 1.0))
    write_piece("slalom_tube_half", POLE_TEXTURES, pole_elements("slalom", 0.5))
    write_piece("flag_tube", POLE_TEXTURES, pole_elements("flagpole", 1.0))
    write_piece("flag_tube_half", POLE_TEXTURES, pole_elements("flagpole", 0.5))
    write_piece("pole_base", POLE_TEXTURES, base_elements())
    write_piece("pole_light", LIGHT_TEXTURES, cap_elements(), tinted=True)
    write_piece("flag_cloth_left", FLAG_TEXTURES, cloth_elements(1))
    write_piece("flag_cloth_right", FLAG_TEXTURES, cloth_elements(-1))
    write_piece("cube_bar", POLE_TEXTURES, cube_bar(1.0))
    write_piece("cube_bar_half", POLE_TEXTURES, cube_bar(0.5))
    write_piece("cube_post", POLE_TEXTURES, cube_post(1.0))
    write_piece("cube_post_half", POLE_TEXTURES, cube_post(0.5))
    write_piece("cube_corner", POLE_TEXTURES, cube_corner())
    write_piece("cube_led", LIGHT_TEXTURES, cube_led(1.0), tinted=True)
    write_piece("cube_led_half", LIGHT_TEXTURES, cube_led(0.5), tinted=True)


def icon_canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def dot(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def slalom_icon():
    img = icon_canvas()
    for x, top in ((3, 2), (8, 4), (13, 2)):
        for y in range(top, 14):
            band = ((y - top) // 2) % 2 == 0
            dot(img, x - 1, y, col("K"))
            dot(img, x, y, col("R") if band else hex_rgba("eef1f4"))
            dot(img, x + 1, y, shade(col("R"), 0.7) if band else col("w"))
            dot(img, x + 2, y, col("K"))
        for dx in range(-1, 3):
            dot(img, x + dx, top - 1, col("K"))
        for dx in range(-2, 4):
            dot(img, x + dx, 14, col("a"))
            dot(img, x + dx, 15, col("o"))
    return img


FLAG_ICON = (
    "................",
    "..KK............",
    "..KsKKKKK.......",
    "..KsAAAAAKK.....",
    "..KsAAAAAAAK....",
    "..KsAKKKKAAK....",
    "..KsAKAAKAAAK...",
    "..KsKKKAKAAAK...",
    "..KsAKAAKAAAK...",
    "..KsAAAAKAAAK...",
    "..KsAAAAAAAAK...",
    "..KsHKHKHKHK....",
    "..KsKKKKKKK.....",
    "..Ks............",
    ".aaaaa..........",
    ".ooooo..........",
)

CUBE_ICON = (
    "........HH......",
    "........HH......",
    "......ANHHNNNNA.",
    ".....N.HHHH..NC.",
    "....N...HH..N.C.",
    "...ANNNNNNNA..C.",
    "...C.......C..C.",
    "...C.......C..C.",
    "...C.......C..C.",
    "...C.......C..A.",
    "...C.......C.C..",
    "...C.......CC...",
    "...ACCCCCCCA....",
    "...ACCCCCCCA....",
    "................",
    "................",
)


FREEFORM_ICON = (
    "................",
    ".....gGSSSGg....",
    "...gGSSwSSSSGg..",
    "..gSSHHHHHHSSSg.",
    ".gSSHLL...LHHSg.",
    ".gSHL.......LHSg",
    "gSSH.........HSg",
    "gSH...LL.....HSg",
    "gSH....LL....HSg",
    "gSSH....LL...HSg",
    "ggSH...LL....HSg",
    "gSSHL.LL....LHSg",
    "gSSSHHL...LHSSSg",
    "gSSSSSHHHHHSSSSg",
    "gGGGGGGGGGGGGGGg",
    "................",
)


def grid_icon(rows):
    img = icon_canvas()
    for y, row in enumerate(rows):
        for x, symbol in enumerate(row):
            if symbol != ".":
                img.putpixel((x, y), col(symbol))
    return img


def flag_icon():
    return grid_icon(FLAG_ICON)


def cube_icon():
    return grid_icon(CUBE_ICON)


def freeform_icon():
    return grid_icon(FREEFORM_ICON)


def gen_icons():
    for name, painter in (("slalom_pole", slalom_icon), ("race_flag", flag_icon), ("dive_cube", cube_icon), ("freeform_gate", freeform_icon)):
        save_png(painter(), ASSETS / "textures" / "item" / f"{name}.png")
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}},
                   ASSETS / "models" / "item" / f"{name}.json")
        write_json({"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}, ASSETS / "items" / f"{name}.json")


def generate():
    gen_pole_texture()
    gen_flag_texture()
    gen_pieces()
    gen_icons()
