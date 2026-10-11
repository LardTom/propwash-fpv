import numpy as np

import kit
import layout
import paint
from paint import hexc


def lens_glass(region, ctx):
    h, w = region.shape[:2]
    cx, cy = w / 2, h / 2
    r = min(w, h) / 2
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
    region[..., 3] = 0
    m = d <= r
    region[m, :3] = hexc("#141518")
    region[m, 3] = 255
    region[d <= r * 0.84, :3] = hexc("#0e2744")
    region[d <= r * 0.6, :3] = hexc("#081628")
    region[d <= r * 0.28, :3] = hexc("#1b3e6b")
    hl = np.hypot(xx + 0.5 - (cx - r * 0.35), yy + 0.5 - (cy - r * 0.35)) <= max(0.7, r * 0.16)
    region[hl, :3] = hexc("#d8efff")
    hl2 = np.hypot(xx + 0.5 - (cx + r * 0.3), yy + 0.5 - (cy + r * 0.28)) <= max(0.5, r * 0.08)
    region[hl2, :3] = hexc("#6f93c0")


def screw_decal(region, ctx):
    h, w = region.shape[:2]
    paint.disc(region, w * 0.5, h * 0.5, max(0.8, min(w, h) * 0.16), hexc("#8a9099"))
    paint.px(region, int(w * 0.5), int(h * 0.5), hexc("#2b2e33"))


def camera(m, w, h, depth, front_z, lens_d, lens_len, body="cam_body", bezel="cam_bezel", screw=True):
    m.box(-w / 2, -h / 2, front_z, w / 2, h / 2, front_z + depth, body, name="cam_body",
          decals={"east": screw_decal, "west": screw_decal} if screw else None,
          faces={"north": bezel})
    lz = front_z - lens_len
    m.cylinder_x(0.0, 0.0, front_z - lens_len / 2, lens_len, lens_d, "lens_barrel", end="lens_barrel", along="z",
                 name="lens_barrel", bevel=0.4)
    m.box(-lens_d / 2, -lens_d / 2, lz - 0.25, lens_d / 2, lens_d / 2, lz, "lens_glass", name="lens_glass",
          only=("north",))
    return lz


def antenna(m, x, y, z, rot, length, stalk_d, stalk="antenna_black", tip=None, tip_len=0.0, tip_d=0.0,
            tee=None):
    m.box(x - stalk_d / 2, y, z - stalk_d / 2, x + stalk_d / 2, y + length, z + stalk_d / 2, stalk, name="antenna",
          rot=rot, origin=(x, y, z), skip=("down",))
    if tip:
        m.box(x - tip_d / 2, y + length, z - tip_d / 2, x + tip_d / 2, y + length + tip_len, z + tip_d / 2, tip,
              name="antenna_tip", rot=rot, origin=(x, y, z))
    if tee:
        tw, tt, tmat = tee
        m.box(x - tw / 2, y + length - tt, z - tt / 2, x + tw / 2, y + length, z + tt / 2, tmat, name="antenna_tee",
              rot=rot, origin=(x, y, z))


def build_analog_nano():
    m = kit.Model("video", "analog_nano", density=10.0, gui=(20, 205, 0), gui_fill=14.0)
    camera(m, 14.0, 14.0, 12.0, -6.0, 9.0, 4.5, bezel="cam_bezel_orange")
    m.box(-6.0, -6.0, 6.0, 6.0, 6.0, 7.5, "pcb", name="cam_pcb")
    for i, mat in enumerate(("wire_yellow", "wire_red", "wire_black")):
        m.box(-2.4 + i * 1.6 - 0.5, -3.5, 7.5, -2.4 + i * 1.6 + 0.5, -2.5, 12.0, mat, name="cam_wire", skip=("north",))
    return m


def build_analog_race():
    m = kit.Model("video", "analog_race", density=6.5, gui=(20, 205, 0), gui_fill=15.0)
    camera(m, 19.0, 19.0, 17.0, -8.0, 12.0, 6.5, bezel="cam_bezel_red")
    m.box(-10.0, -8.0, 9.0, 10.0, -2.0, 30.0, "vtx_wrap", name="vtx", decals={"up": vtx_label})
    m.box(-2.2, -2.0, 24.0, 2.2, 1.0, 28.0, "brass", name="mmcx")
    antenna(m, 0.0, 1.0, 26.0, (42, 0, 0), 26.0, 2.4, stalk="antenna_black")
    tip_y = 1.0
    m.box(-5.5, tip_y + 26.0, 26.0 - 5.5, 5.5, tip_y + 33.0, 26.0 + 5.5, "antenna_red", name="lollipop",
          rot=(42, 0, 0), origin=(0.0, 1.0, 26.0))
    m.box(-4.0, tip_y + 25.0, 26.0 - 4.0, 4.0, tip_y + 34.2, 26.0 + 4.0, "antenna_red", name="lollipop_cap",
          rot=(42, 45, 0), origin=(0.0, 1.0, 26.0))
    for i, mat in enumerate(("wire_yellow", "wire_red", "wire_black")):
        m.box(-2.0 + i * 1.6 - 0.5, -5.0, 9.0 - 1.5, -2.0 + i * 1.6 + 0.5, -4.0, 9.0, mat, name="cam_wire")
    return m


def build_digital_lite():
    m = kit.Model("video", "digital_lite", density=6.0, gui=(20, 205, 0), gui_fill=15.0)
    camera(m, 15.0, 15.0, 13.0, -6.5, 11.0, 5.5, bezel="cam_bezel_grey")
    m.box(-10.5, -5.0, 7.0, 10.5, 1.0, 29.0, "vtx_lite", name="vtx", decals={"up": lite_label})
    m.box(-9.5, 1.0, 10.0, 9.5, 2.4, 26.0, "heatsink", name="vtx_heatsink")
    m.box(-1.5, -2.0, 6.5, 1.5, 0.0, 7.0, "coax", name="ribbon")
    for s in (-1, 1):
        antenna(m, s * 7.5, 1.0, 27.0, (58, s * 32, 0), 20.0, 1.6, stalk="antenna_black",
                tee=(14.0, 1.4, "antenna_white"))
    return m


def build_digital_hd():
    m = kit.Model("video", "digital_hd", density=5.5, gui=(20, 205, 0), gui_fill=15.0)
    camera(m, 20.0, 20.0, 18.0, -9.0, 16.0, 8.0, bezel="cam_bezel_dark")
    m.box(-1.8, 2.0, 9.0, 1.8, 4.0, 13.0, "coax", name="ribbon")
    m.box(-15.0, 2.0, 12.0, 15.0, 12.5, 46.0, "airunit", name="air_unit", decals={"east": hd_side, "west": hd_side})
    for i in range(6):
        x = -12.5 + i * 5.0
        m.box(x - 1.0, 12.5, 15.0, x + 1.0, 15.5, 43.0, "heatsink", name="fin")
    m.box(-9.0, 2.5, 46.0, 9.0, 10.5, 47.0, "airunit_port", name="port_panel")
    for s in (-1, 1):
        m.box(s * 11.0 - 2.0, 8.0, 44.0, s * 11.0 + 2.0, 11.0, 48.5, "brass", name="antenna_socket")
        antenna(m, s * 11.0, 11.0, 46.5, (64, s * 28, 0), 34.0, 3.4, stalk="antenna_black", tip="antenna_red",
                tip_len=8.0, tip_d=4.2)
    return m


def vtx_label(region, ctx):
    h, w = region.shape[:2]
    if w >= 12 and h >= 8:
        paint.text_centered(region, "VTX", hexc("#f2c230"))


def lite_label(region, ctx):
    h, w = region.shape[:2]
    if w >= 12 and h >= 8:
        paint.text_centered(region, "HD", hexc("#e4e6ea"))


def hd_side(region, ctx):
    h, w = region.shape[:2]
    if w >= 16 and h >= 7:
        paint.text_centered(region, "HD", hexc("#e4e6ea"))
        paint.rect(region, 1, h - 2, w - 1, h - 1, hexc("#d6352c"))


def register_materials():
    paint.register("cam_body", paint.plastic("#1d1e21"))
    paint.register("cam_bezel_orange", paint.alu("#e8761e"))
    paint.register("cam_bezel_red", paint.alu("#d6352c"))
    paint.register("cam_bezel_grey", paint.alu("#8d949c"))
    paint.register("cam_bezel_dark", paint.alu("#3a3e44"))
    paint.register("lens_barrel", paint.alu("#26282c"))
    paint.register("lens_glass", lens_glass)
    paint.register("vtx_wrap", paint.shrink("#2b3a55"))
    paint.register("vtx_lite", paint.alu("#3a3e44"))
    paint.register("airunit", paint.alu("#2b2e33"))
    paint.register("airunit_port", paint.plastic("#111214"))


def build_all():
    register_materials()
    return [build_analog_nano(), build_analog_race(), build_digital_lite(), build_digital_hd()]
