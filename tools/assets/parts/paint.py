import numpy as np


class Ctx:
    def __init__(self, w, h, side, rng, mm_per_px=2.0):
        self.w = w
        self.h = h
        self.side = side
        self.rng = rng
        self.mm_per_px = mm_per_px


def hexc(value):
    value = value.lstrip("#")
    return np.array([int(value[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)


def shade(color, factor):
    return np.clip(np.asarray(color, dtype=np.float64) * factor, 0, 255)


def mix(a, b, t):
    return np.asarray(a, dtype=np.float64) * (1 - t) + np.asarray(b, dtype=np.float64) * t


def fill(region, color, alpha=255):
    region[..., :3] = color
    region[..., 3] = alpha


def noise(region, ctx, amount):
    n = ctx.rng.normal(0.0, amount, size=region.shape[:2])
    region[..., :3] = np.clip(region[..., :3] * (1 + n[..., None]), 0, 255)


def edges(region, light, dark, width=1):
    h, w = region.shape[:2]
    if h < 3 or w < 3:
        return
    region[:width, :, :3] = light if light is not None else region[:width, :, :3]
    region[:, :width, :3] = light if light is not None else region[:, :width, :3]
    region[h - width:, :, :3] = dark if dark is not None else region[h - width:, :, :3]
    region[:, w - width:, :3] = dark if dark is not None else region[:, w - width:, :3]


def px(region, x, y, color, alpha=255):
    h, w = region.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        region[y, x, :3] = color
        region[y, x, 3] = alpha


def rect(region, x0, y0, x1, y1, color, alpha=255):
    h, w = region.shape[:2]
    x0, x1 = max(0, int(x0)), min(w, int(x1))
    y0, y1 = max(0, int(y0)), min(h, int(y1))
    if x1 > x0 and y1 > y0:
        region[y0:y1, x0:x1, :3] = color
        region[y0:y1, x0:x1, 3] = alpha


def disc(region, cx, cy, r, color, alpha=255):
    h, w = region.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    m = (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r
    region[m, :3] = color
    region[m, 3] = alpha


def carbon(region, ctx):
    h, w = region.shape[:2]
    if ctx.side == "side":
        fill(region, hexc("#2c3036"))
        for y in range(h):
            f = 0.85 + 0.25 * ((y % 2) == 0)
            region[y, :, :3] = shade(hexc("#2c3036"), f)
        noise(region, ctx, 0.05)
        if h >= 3:
            region[0, :, :3] = hexc("#50565e")
        return
    base = hexc("#1b1d20")
    fill(region, base)
    yy, xx = np.mgrid[0:h, 0:w]
    weave = ((xx // 2 + yy // 2) % 2) == 0
    region[weave, :3] = hexc("#25282d")
    sheen = ((xx + yy) % 7) == 0
    region[sheen & weave, :3] = hexc("#30343a")
    noise(region, ctx, 0.04)
    edges(region, hexc("#474c54"), hexc("#111214"))


def carbon_plain(region, ctx):
    carbon(region, ctx)


def alu(color):
    base = hexc(color)

    def painter(region, ctx):
        fill(region, base)
        h, w = region.shape[:2]
        for y in range(h):
            region[y, :, :3] = shade(base, 1.0 + 0.05 * ctx.rng.normal())
        noise(region, ctx, 0.03)
        if ctx.side == "top":
            edges(region, shade(base, 1.3), shade(base, 0.7))
        elif ctx.side == "side":
            if h >= 3:
                region[0, :, :3] = shade(base, 1.35)
                region[h - 1, :, :3] = shade(base, 0.7)
        else:
            region[..., :3] = shade(region[..., :3], 0.8)

    return painter


def plastic(color, speck=0.03):
    base = hexc(color)

    def painter(region, ctx):
        fill(region, base)
        noise(region, ctx, speck)
        if ctx.side == "top":
            edges(region, shade(base, 1.18), shade(base, 0.8))
        elif ctx.side == "side":
            h = region.shape[0]
            if h >= 3:
                region[0, :, :3] = shade(base, 1.18)
                region[h - 1, :, :3] = shade(base, 0.78)
        else:
            region[..., :3] = shade(region[..., :3], 0.85)

    return painter


def flat(color, alpha=255):
    base = hexc(color)

    def painter(region, ctx):
        fill(region, base, alpha)

    return painter


def glossy(color):
    base = hexc(color)

    def painter(region, ctx):
        fill(region, base)
        h, w = region.shape[:2]
        for y in range(h):
            region[y, :, :3] = shade(base, 1.15 - 0.3 * y / max(1, h - 1))
        noise(region, ctx, 0.02)

    return painter


def copper(region, ctx):
    h, w = region.shape[:2]
    fill(region, hexc("#b8672e"))
    for x in range(w):
        c = ["#d88a43", "#b8672e", "#8a4519", "#c77838"][x % 4]
        region[:, x, :3] = hexc(c)
    noise(region, ctx, 0.05)


def steel(region, ctx):
    fill(region, hexc("#b8bec6"))
    h, w = region.shape[:2]
    for y in range(h):
        region[y, :, :3] = shade(hexc("#b8bec6"), 1 + 0.06 * ctx.rng.normal())
    if ctx.side == "top":
        edges(region, hexc("#e4e8ee"), hexc("#80868e"))


def dark_steel(region, ctx):
    fill(region, hexc("#5d636b"))
    noise(region, ctx, 0.05)
    if ctx.side == "top":
        edges(region, hexc("#8a9099"), hexc("#3a3e44"))


def thread(region, ctx):
    h, w = region.shape[:2]
    fill(region, hexc("#9aa1aa"))
    for y in range(h):
        if y % 2:
            region[y, :, :3] = hexc("#6e747c")
    if ctx.side == "top":
        fill(region, hexc("#c9ced5"))


def rubber(region, ctx):
    fill(region, hexc("#2b2c2e"))
    noise(region, ctx, 0.08)


def pcb(region, ctx):
    fill(region, hexc("#17191c"))
    noise(region, ctx, 0.03)
    if ctx.side == "side":
        fill(region, hexc("#3d4a3a"))
        return
    edges(region, hexc("#2e3238"), hexc("#0f1012"))


def gold(region, ctx):
    fill(region, hexc("#d9a33a"))
    noise(region, ctx, 0.05)
    if ctx.side == "top":
        edges(region, hexc("#f5cf72"), hexc("#9c7020"))


def chip(region, ctx):
    fill(region, hexc("#222428"))
    noise(region, ctx, 0.03)
    if ctx.side == "top":
        edges(region, hexc("#3a3d42"), hexc("#151618"))
        h, w = region.shape[:2]
        if w >= 4 and h >= 4:
            px(region, 1, 1, hexc("#6a6f76"))
    else:
        h, w = region.shape[:2]
        for x in range(0, w, 2):
            region[-1:, x, :3] = hexc("#c9ced5")


def component(color):
    base = hexc(color)

    def painter(region, ctx):
        fill(region, base)
        noise(region, ctx, 0.04)
        h, w = region.shape[:2]
        if ctx.side == "side" and w >= 2:
            region[:, 0, :3] = hexc("#c9ced5")
            region[:, -1, :3] = hexc("#c9ced5")

    return painter


def lens(region, ctx):
    h, w = region.shape[:2]
    fill(region, hexc("#0b1320"))
    if ctx.side == "side":
        fill(region, hexc("#151618"))
        noise(region, ctx, 0.05)
        return
    cx, cy = w / 2, h / 2
    r = min(w, h) / 2
    disc(region, cx, cy, r, hexc("#1b1c1f"))
    disc(region, cx, cy, r * 0.8, hexc("#122a4a"))
    disc(region, cx, cy, r * 0.55, hexc("#0a1a30"))
    disc(region, cx - r * 0.3, cy - r * 0.3, max(0.6, r * 0.18), hexc("#bfe3ff"))
    if w >= 6:
        disc(region, cx + r * 0.25, cy + r * 0.3, max(0.5, r * 0.1), hexc("#5f7fa6"))


def prop_material(color):
    base = hexc(color)

    def painter(region, ctx):
        h, w = region.shape[:2]
        fill(region, base)
        if ctx.side == "side":
            region[..., :3] = shade(base, 0.8)
            return
        for x in range(w):
            region[:, x, :3] = shade(base, 0.85 + 0.3 * x / max(1, w - 1))
        if h >= 3:
            region[0, :, :3] = shade(base, 1.35)
            region[-1, :, :3] = shade(base, 0.7)
        noise(region, ctx, 0.015)
        if ctx.side == "bottom":
            region[..., :3] = shade(region[..., :3], 0.85)

    return painter


def wire(color):
    base = hexc(color)

    def painter(region, ctx):
        fill(region, base)
        h, w = region.shape[:2]
        if h >= 2 and ctx.side == "side":
            region[0, :, :3] = shade(base, 1.3)

    return painter


def strap(region, ctx):
    fill(region, hexc("#1a1a1c"))
    h, w = region.shape[:2]
    for x in range(w):
        if x % 2 == 0:
            region[:, x, :3] = hexc("#26262a")
    if h >= 4:
        region[0, :, :3] = hexc("#b0263a")
        region[-1, :, :3] = hexc("#b0263a")


def heatsink(region, ctx):
    h, w = region.shape[:2]
    fill(region, hexc("#8d949c"))
    for x in range(w):
        if x % 2:
            region[:, x, :3] = hexc("#5f656d")
    if ctx.side == "top":
        for x in range(w):
            region[:, x, :3] = hexc("#b7bec6") if x % 2 == 0 else hexc("#4d5259")


def shrink(color):
    base = hexc(color)

    def painter(region, ctx):
        fill(region, base)
        h, w = region.shape[:2]
        for y in range(h):
            region[y, :, :3] = shade(base, 1.1 - 0.25 * abs(y - h / 2) / max(1, h / 2))
        noise(region, ctx, 0.02)

    return painter


MATERIALS = {
    "carbon": carbon,
    "copper": copper,
    "steel": steel,
    "dark_steel": dark_steel,
    "thread": thread,
    "rubber": rubber,
    "pcb": pcb,
    "gold": gold,
    "chip": chip,
    "lens": lens,
    "strap": strap,
    "heatsink": heatsink,
    "black": plastic("#1c1d20"),
    "black_matte": plastic("#232428", 0.05),
    "dark_grey": plastic("#3a3d42"),
    "white": plastic("#e8e8e4"),
    "cap_black": plastic("#1e2530"),
    "cap_blue": plastic("#1f4e9c"),
    "cap_top": alu("#aeb5bd"),
    "wire_red": wire("#c62828"),
    "wire_black": wire("#1c1c1c"),
    "wire_yellow": wire("#f2c230"),
    "xt_yellow": plastic("#e8b923"),
    "xt_black": plastic("#262626"),
    "bt_white": plastic("#dedbd2"),
    "led_blue": glossy("#4aa3ff"),
    "led_red": glossy("#ff4a4a"),
    "cap_smd": component("#b58a52"),
    "res_smd": component("#1f1f1f"),
    "coax": wire("#2a2a2a"),
    "antenna_red": plastic("#c0392b"),
    "antenna_black": plastic("#1b1b1d"),
    "antenna_white": plastic("#e6e6e6"),
    "tpu_orange": plastic("#f2702a", 0.05),
    "tpu_red": plastic("#d6333a", 0.05),
    "tpu_black": plastic("#262628", 0.06),
    "tpu_teal": plastic("#1fa39a", 0.05),
    "tpu_blue": plastic("#2f7de0", 0.05),
    "brass": alu("#c9a24a"),
}


def register(name, painter):
    MATERIALS[name] = painter
    return name


FONT = {
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "011", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "111"],
    "A": ["010", "101", "111", "101", "101"],
    "B": ["110", "101", "110", "101", "110"],
    "C": ["011", "100", "100", "100", "011"],
    "D": ["110", "101", "101", "101", "110"],
    "E": ["111", "100", "110", "100", "111"],
    "F": ["111", "100", "110", "100", "100"],
    "G": ["011", "100", "101", "101", "011"],
    "H": ["101", "101", "111", "101", "101"],
    "I": ["111", "010", "010", "010", "111"],
    "K": ["101", "101", "110", "101", "101"],
    "L": ["100", "100", "100", "100", "111"],
    "M": ["101", "111", "111", "101", "101"],
    "N": ["110", "101", "101", "101", "101"],
    "O": ["010", "101", "101", "101", "010"],
    "P": ["110", "101", "110", "100", "100"],
    "R": ["110", "101", "110", "101", "101"],
    "S": ["011", "100", "010", "001", "110"],
    "T": ["111", "010", "010", "010", "010"],
    "U": ["101", "101", "101", "101", "111"],
    "V": ["101", "101", "101", "101", "010"],
    "W": ["101", "101", "111", "111", "101"],
    "X": ["101", "101", "010", "101", "101"],
    "Y": ["101", "101", "010", "010", "010"],
    "-": ["000", "000", "111", "000", "000"],
    ".": ["000", "000", "000", "000", "010"],
    "+": ["000", "010", "111", "010", "000"],
    " ": ["000", "000", "000", "000", "000"],
    "/": ["001", "001", "010", "100", "100"],
}

FONT_SMALL = {
    "0": ["11", "11", "11"],
    "1": ["01", "01", "01"],
    "S": ["11", "01", "11"],
}


def text_width(text, spacing=1):
    return len(text) * (3 + spacing) - spacing


def text(region, x, y, value, color, spacing=1, alpha=255):
    cx = x
    for ch in value.upper():
        glyph = FONT.get(ch, FONT[" "])
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == "1":
                    px(region, cx + gx, y + gy, color, alpha)
        cx += 3 + spacing


def text_centered(region, value, color, y=None, spacing=1):
    h, w = region.shape[:2]
    tw = text_width(value, spacing)
    x = (w - tw) // 2
    if y is None:
        y = (h - 5) // 2
    text(region, x, y, value, color, spacing)
