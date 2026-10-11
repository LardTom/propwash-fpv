import numpy as np

import kit
import layout
import paint
from paint import hexc
from stacks import lead, xt_connector


def wrap(base_color, band_color, text_top, text_bottom, text_color="#ffffff", stripe=None, under=False):
    base = hexc(base_color)
    band = hexc(band_color)
    label_side = "bottom" if under else "top"

    def painter(region, ctx):
        h, w = region.shape[:2]
        paint.fill(region, base)
        for y in range(h):
            region[y, :, :3] = paint.shade(base, 1.08 - 0.2 * y / max(1, h - 1))
        paint.noise(region, ctx, 0.015)
        if ctx.side == "side" and w > h:
            if stripe and h >= 4:
                region[0, :, :3] = hexc(stripe)
                region[h - 1, :, :3] = hexc(stripe)
        elif ctx.side == label_side:
            paint.edges(region, paint.shade(base, 1.25), paint.shade(base, 0.8))
            if w >= 10 and h >= 20:
                paint.rect(region, 2, h // 2 - 3, w - 2, h // 2 + 3, band)
        else:
            region[..., :3] = paint.shade(region[..., :3], 0.85)
            if w >= 6 and h >= 5:
                paint.rect(region, 1, h - 2, w - 1, h - 1, band)

    return painter


def label_decal(band_color, text_top, text_bottom, text_color, front_right):
    band = hexc(band_color)

    def decal(region, ctx):
        h, w = region.shape[:2]
        center = int(w * (0.6 if front_right else 0.4))
        one_line = text_top + " " + text_bottom
        if h >= 17:
            y0 = h // 2 - 7
            paint.rect(region, 0, y0, w, y0 + 15, band)
            region[y0, :, :3] = paint.shade(band, 1.2)
            region[y0 + 14, :, :3] = paint.shade(band, 0.75)
            for text, ty in ((text_top, y0 + 2), (text_bottom, y0 + 8)):
                paint.text(region, center - paint.text_width(text) // 2, ty, text, hexc(text_color))
        elif h >= 7:
            text = one_line if paint.text_width(one_line) <= w - 2 else text_bottom
            y0 = (h - 7) // 2
            paint.rect(region, 0, y0, w, y0 + 7, band)
            x = max(1, min(w - paint.text_width(text) - 1, center - paint.text_width(text) // 2))
            paint.text(region, x, y0 + 1, text, hexc(text_color))

    decal.__name__ = f"label_{band_color}_{text_top}_{text_bottom}_{'r' if front_right else 'l'}"
    return decal


UNDER_SUFFIX = "_under"
STRAP_PLATE_MM = 3.3

BATTERIES = {
    "b1s_300_hv": dict(w=12.5, h=7.5, l=55.0, base="#6b2fb3", band="#f2f2f2", t1="1S", t2="300", text="#6b2fb3",
                       connector="bt", density=8.0, strap=False),
    "b4s_650": dict(w=31.0, h=27.0, l=58.0, base="#202226", band="#f07c1e", t1="4S", t2="650", text="#15161a",
                    connector="xt30", density=6.0, strap=False),
    "b6s_1100": dict(w=34.0, h=31.0, l=74.0, base="#1c1d21", band="#d4312a", t1="6S", t2="1100", text="#ffffff",
                     connector="xt60", density=5.0, strap=True),
    "b6s_1300": dict(w=36.0, h=34.0, l=78.0, base="#1c1d21", band="#2a6de0", t1="6S", t2="1300", text="#ffffff",
                     connector="xt60", density=5.0, strap=True),
}


def balance_lead(m, x, y_top, z_rear, cells):
    lead(m, x, y_top - 3.0, z_rear, z_rear + 7.0, 2.0, "wire_black")
    m.box(x - 1.5, y_top - 5.0, z_rear + 7.0, x + 1.5, y_top - 1.0, z_rear + 7.0 + 2.5 + cells * 0.6, "bt_white",
          name="balance_plug")


def strap_band(m, w, h, z, width=18.0):
    t = 0.9
    m.box(-w / 2 - t, h, z - width / 2, w / 2 + t, h + t, z + width / 2, "strap", name="strap")
    for s in (-1, 1):
        m.box(s * w / 2, -3.3, z - width / 2, s * (w / 2 + t), h, z + width / 2, "strap", name="strap")
    m.box(-w / 2 - t, -3.3, z - width / 2, w / 2 + t, -2.5, z + width / 2, "strap", name="strap", only=("down", "north", "south"))
    m.box(-6.0, h + t, z - width / 2 - 0.6, 6.0, h + t + 1.6, z + width / 2 + 0.6, "strap_buckle", name="buckle")


def strap_band_under(m, w, h, z, width=18.0):
    t = 0.9
    plate = STRAP_PLATE_MM
    m.box(-w / 2 - t, -h - t, z - width / 2, w / 2 + t, -h, z + width / 2, "strap", name="strap")
    for s in (-1, 1):
        m.box(s * w / 2, -h - t, z - width / 2, s * (w / 2 + t), plate, z + width / 2, "strap", name="strap")
    m.box(-w / 2 - t, plate - 0.8, z - width / 2, w / 2 + t, plate, z + width / 2, "strap", name="strap", only=("up", "north", "south"))
    m.box(-6.0, -h - t - 1.6, z - width / 2 - 0.6, 6.0, -h - t, z + width / 2 + 0.6, "strap_buckle", name="buckle")


def build_lipo(key, under=False):
    p = BATTERIES[key]
    suffix = UNDER_SUFFIX if under else ""
    m = kit.Model("battery", key + suffix, density=p["density"], gui=(30, 225, 0))
    mat = paint.register(f"wrap_{key}{suffix}", wrap(p["base"], p["band"], p["t1"], p["t2"], p["text"], under=under))
    w, h, l = p["w"], p["h"], p["l"]
    y0 = -h if under else 0.0
    top = y0 + h
    m.box(-w / 2, y0, -l / 2, w / 2, top, l / 2, mat, name="pack",
          faces={"north": "pack_end", "south": "pack_end"},
          decals={"east": label_decal(p["band"], p["t1"], p["t2"], p["text"], True),
                  "west": label_decal(p["band"], p["t1"], p["t2"], p["text"], False)})
    zr = l / 2
    if p["connector"] == "bt":
        lead(m, -1.2, top - 2.0, zr, zr + 7.0, 1.0, "wire_red")
        lead(m, 1.2, top - 2.0, zr, zr + 7.0, 1.0, "wire_black")
        m.box(-3.2, top - 5.0, zr + 7.0, 3.2, top - 0.5, zr + 12.0, "bt_white", name="bt_connector")
    else:
        xt = p["connector"] == "xt60"
        dia = 3.0 if xt else 2.0
        lead(m, -dia * 0.8, top - 4.0, zr, zr + 14.0, dia, "wire_red")
        lead(m, dia * 0.8, top - 4.0, zr, zr + 14.0, dia, "wire_black")
        if xt:
            xt_connector(m, zr + 14.0, top - 8.0, 16.0, 8.0, 15.5)
        else:
            xt_connector(m, zr + 14.0, top - 7.0, 10.2, 5.6, 11.0)
        cells = 6 if key.startswith("b6s") else 4
        balance_lead(m, w / 2 - 5.0, top, zr, cells)
    if p["strap"]:
        if under:
            strap_band_under(m, w, h, l / 2 - 15.0, 16.0)
        else:
            strap_band(m, w, h, l / 2 - 15.0, 16.0)
    return m


def strapped(key):
    return key in BATTERIES and BATTERIES[key]["strap"]


def build_lion():
    key = "b6s_4000_lion"
    m = kit.Model("battery", key, density=4.0, gui=(30, 225, 0))
    d = 21.0
    l = 70.0
    for ix in (-1, 0, 1):
        for iy in (0, 1):
            m.cylinder_x(ix * d, d / 2 + iy * d, 0.0, l, d, "cell_wrap", end="cell_end", along="z", name="cell",
                         bevel=1.2)
    m.box(-3 * d / 2 - 0.4, -0.4, -13.0, 3 * d / 2 + 0.4, 2 * d + 0.4, 13.0, "lion_band", name="label_wrap",
          faces={"north": None, "south": None})
    for ix in (-1, 0, 1):
        for iy in (0, 1):
            for zc, face in ((-l / 2 - 0.15, "north"),):
                m.box(ix * d - d / 2, iy * d, zc - 0.05, ix * d + d / 2, iy * d + d, zc + 0.05, "cell_cap",
                      name="cell_cap", only=(face,))
    for zc in (-30.0, 30.0):
        m.box(-3 * d / 2 - 0.3, -0.3, zc - 3.0, 3 * d / 2 + 0.3, 2 * d + 0.3, zc + 3.0, "shrink_clear", name="shrink",
              faces={"north": None, "south": None})
    zr = l / 2
    m.box(-3 * d / 2, 2.0, zr, 3 * d / 2, 2 * d - 2.0, zr + 0.8, "nickel", name="nickel_strip")
    lead(m, -2.4, 2 * d - 5.0, zr + 0.8, zr + 14.0, 3.0, "wire_red")
    lead(m, 2.4, 2 * d - 5.0, zr + 0.8, zr + 14.0, 3.0, "wire_black")
    xt_connector(m, zr + 14.0, 2 * d - 9.0, 16.0, 8.0, 15.5)
    balance_lead(m, d, 2 * d, zr + 0.8, 6)
    return m


def cell_end(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#b8bec6"))
    paint.noise(region, ctx, 0.04)
    paint.edges(region, hexc("#dfe3e8"), hexc("#7d838b"))


def lion_band(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#17191d"))
    paint.noise(region, ctx, 0.02)
    teal = hexc("#18a3a0")
    if ctx.side == "top" and h >= 13:
        paint.rect(region, 0, 1, w, 2, teal)
        paint.rect(region, 0, h - 2, w, h - 1, teal)
        paint.text_centered(region, "LI-ION", hexc("#ffffff"), y=h // 2 - 6)
        paint.text_centered(region, "6S 4000", teal, y=h // 2 + 1)
    elif ctx.side == "side":
        paint.rect(region, 0, 1, w, 3, teal)
        if h >= 12 and w >= 8:
            paint.text_centered(region, "6S", hexc("#ffffff"), y=h // 2 - 2)


def cell_cap(region, ctx):
    h, w = region.shape[:2]
    cx, cy = w / 2, h / 2
    r = min(w, h) / 2
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
    region[..., 3] = 0
    m = d <= r
    region[m, :3] = hexc("#1a8f8b")
    region[m, 3] = 255
    region[d <= r * 0.8, :3] = hexc("#d9cfae")
    region[d <= r * 0.55, :3] = hexc("#b8bec6")
    region[d <= r * 0.3, :3] = hexc("#e1e5ea")


def register_materials():
    paint.register("pack_end", paint.plastic("#26282c"))
    paint.register("cell_wrap", paint.shrink("#1a8f8b"))
    paint.register("cell_end", cell_end)
    paint.register("cell_cap", cell_cap)
    paint.register("lion_band", lion_band)
    paint.register("shrink_clear", paint.shrink("#2d6db8"))
    paint.register("nickel", paint.alu("#c4c9cf"))
    paint.register("strap_buckle", paint.alu("#d6352c"))


def build_all():
    register_materials()
    return [build_lipo(k) for k in ("b1s_300_hv", "b4s_650", "b6s_1100", "b6s_1300")] + [build_lion()]


def build_under():
    return [build_lipo(k, under=True) for k in BATTERIES if strapped(k)]
