import math

import numpy as np

import kit
import layout
import paint
from paint import hexc


def bell_side(color, vent_color="#c8733a"):
    base = hexc(color)

    def painter(region, ctx):
        h, w = region.shape[:2]
        paint.fill(region, base)
        for y in range(h):
            region[y, :, :3] = paint.shade(base, 1.12 - 0.3 * y / max(1, h - 1))
        if h >= 4:
            region[0, :, :3] = paint.shade(base, 1.4)
            vent_top = int(h * 0.55)
            for x in range(w):
                if x % 3 != 0:
                    region[vent_top:h - 1, x, :3] = hexc(vent_color) if (x + 1) % 3 == 0 else hexc("#1a1a1a")
            region[h - 1, :, :3] = paint.shade(base, 0.7)
        paint.noise(region, ctx, 0.02)

    return painter


def bell_top(color, spokes=5, hub="#c9ced5", accent="#1a1a1a"):
    base = hexc(color)

    def painter(region, ctx):
        h, w = region.shape[:2]
        cx, cy = w / 2, h / 2
        r = min(w, h) / 2
        yy, xx = np.mgrid[0:h, 0:w]
        dx = xx + 0.5 - cx
        dy = yy + 0.5 - cy
        d = np.hypot(dx, dy)
        ang = np.arctan2(dy, dx)
        paint.fill(region, base)
        region[..., :3] = paint.shade(base, 1.05)
        shade = 1.0 + 0.18 * np.cos(ang + 2.3)
        region[..., :3] = np.clip(region[..., :3] * shade[..., None], 0, 255)
        ring = (d > r * 0.34) & (d < r * 0.78)
        spoke = np.cos(spokes * ang) > 0.25
        hole = ring & ~spoke
        region[hole, :3] = hexc(accent)
        copper = hole & (d > r * 0.5)
        region[copper, :3] = hexc("#b0622b")
        region[d > r * 0.9, :3] = paint.shade(base, 1.35)
        region[d < r * 0.3, :3] = hexc(hub)
        region[d < r * 0.12, :3] = hexc("#6e747c")

    return painter


def base_top(color):
    base = hexc(color)

    def painter(region, ctx):
        h, w = region.shape[:2]
        paint.fill(region, base)
        paint.noise(region, ctx, 0.03)
        cx, cy = w / 2, h / 2
        paint.disc(region, cx, cy, min(w, h) * 0.3, paint.shade(base, 0.6))

    return painter


MOTORS = {
    "m0802_22000": dict(base_d=9.0, base_h=1.2, base_color="#1f2023", lug=None, stator_d=8.6, gap=0.8,
                        bell_d=10.5, bell_color="#8e44ec", shaft_d=1.0, shaft_len=1.6, spokes=4, lug_w=0,
                        vent="#9a5a2a"),
    "m1404_4600": dict(base_d=15.0, base_h=2.2, base_color="#1f2023", lug=(9.5, 3.4), stator_d=14.0, gap=1.2,
                       bell_d=18.0, bell_color="#18b2a2", shaft_d=1.6, shaft_len=2.5, spokes=5, lug_w=3.4,
                       vent="#c8733a"),
    "m2004_3000": dict(base_d=17.0, base_h=2.4, base_color="#1f2023", lug=(11.0, 3.8), stator_d=18.0, gap=1.4,
                       bell_d=22.5, bell_color="#e0662e", shaft_d=1.6, shaft_len=2.8, spokes=6, lug_w=3.8,
                       vent="#c8733a", top_color="#232427"),
    "m2207_1750": dict(base_d=21.0, base_h=3.2, base_color="#2a2b2f", lug=(13.8, 5.0), stator_d=23.0, gap=2.0,
                       bell_d=27.9, bell_color="#d6352c", shaft_d=5.0, shaft_len=9.5, spokes=5, lug_w=5.0,
                       vent="#c8733a", top_color="#1d1e21"),
    "m2306_1860": dict(base_d=21.0, base_h=3.0, base_color="#b9c0c8", lug=(13.8, 5.0), stator_d=23.0, gap=1.8,
                       bell_d=28.6, bell_color="#2f6fe0", shaft_d=5.0, shaft_len=9.0, spokes=6, lug_w=5.0,
                       vent="#c8733a"),
    "m2807_1300": dict(base_d=26.0, base_h=3.4, base_color="#1f2023", lug=(17.0, 5.5), stator_d=28.0, gap=2.2,
                       bell_d=34.0, bell_color="#e8961e", shaft_d=5.0, shaft_len=10.5, spokes=5, lug_w=5.5,
                       vent="#c8733a", top_color="#232427"),
}


def build_motor(key):
    p = MOTORS[key]
    seat = layout.MOTOR_SEAT_MM[key]
    small = p["bell_d"] < 12
    m = kit.Model("motor", key, density=8.0 if small else 5.0 if p["bell_d"] < 20 else 4.0, gui=(25, 225, 0),
                  gui_fill=14.0)
    base_mat = paint.register(f"motor_base_{key}", paint.alu(p["base_color"]))
    bell_mat = paint.register(f"motor_bell_{key}", bell_side(p["bell_color"], p["vent"]))
    top_color = p.get("top_color", p["bell_color"])
    bell_plain = paint.register(f"motor_bell_plain_{key}", paint.alu(p["bell_color"]))
    base_sheet_d = p["base_d"]
    base_sheet = m.sheet("base_top", -base_sheet_d / 2, -base_sheet_d / 2, base_sheet_d / 2, base_sheet_d / 2,
                         base_top(p["base_color"]))
    m.cylinder(0, 0, 0.0, p["base_h"], p["base_d"], base_mat, name="base", sheet_up=base_sheet, bevel=0.35)
    if p["lug"]:
        reach, lw = p["lug"]
        for yaw in (45, 135, 225, 315):
            m.bar(0, 0, yaw, reach - p["base_d"] / 2 + 1.0, lw, 0.0, p["base_h"] * 0.6, base_mat,
                  start=p["base_d"] / 2 - 1.0, name="lug", skip=("west",),
                  decals={"up": lug_hole})
    y_stator0 = p["base_h"]
    y_bell0 = p["base_h"] + p["gap"]
    m.cylinder(0, 0, y_stator0, y_bell0 + 0.4, p["stator_d"], "copper", name="stator", no_top=True, no_bottom=True,
               bevel=0.2)
    top_sheet = m.sheet("bell_top", -p["bell_d"] / 2, -p["bell_d"] / 2, p["bell_d"] / 2, p["bell_d"] / 2,
                        bell_top(top_color, p["spokes"]))
    m.cylinder(0, 0, y_bell0, seat, p["bell_d"], bell_mat, top=bell_plain, bottom="black", name="bell",
               sheet_up=top_sheet, bevel=max(0.5, p["bell_d"] * 0.05))
    sd = p["shaft_d"]
    if sd >= 3:
        m.cylinder(0, 0, seat, seat + p["shaft_len"], sd, "thread", name="shaft", bevel=0.3)
    else:
        m.box(-sd / 2, seat, -sd / 2, sd / 2, seat + p["shaft_len"], sd / 2, "steel", name="shaft")
    return m


def lug_hole(region, ctx):
    h, w = region.shape[:2]
    if w >= 3 and h >= 3:
        paint.px(region, w - 2, h // 2, hexc("#0c0c0d"))


def build_all():
    return [build_motor(k) for k in layout.MOTOR_KEYS]
