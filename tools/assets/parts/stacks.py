import math

import numpy as np

import kit
import layout
import paint
from paint import hexc


def pcb_sheet(x0, z0, x1, z1, pads=(), label=None, label_pos=None, holes=(), traces=True, silk_box=()):
    def painter(region, ctx):
        h, w = region.shape[:2]
        mmpp = (x1 - x0) / w
        paint.fill(region, hexc("#141619"))
        paint.noise(region, ctx, 0.03)
        yy, xx = np.mgrid[0:h, 0:w]
        mx = x0 + (xx + 0.5) * mmpp
        mz = z0 + (yy + 0.5) * mmpp
        if traces:
            lines = ((xx * 3 + yy * 5) % 23 == 0) | ((xx * 7 + yy * 2) % 31 == 0)
            region[lines, :3] = hexc("#1d2126")
        paint.edges(region, hexc("#2a2e34"), hexc("#0c0d0f"))
        for hx, hz, r in holes:
            d2 = (mx - hx) ** 2 + (mz - hz) ** 2
            region[d2 <= (r + 0.9) ** 2, :3] = hexc("#d9a33a")
            region[d2 <= r * r, :3] = hexc("#08090a")
        for px0, pz0, px1, pz1, color in pads:
            m = (mx >= px0) & (mx <= px1) & (mz >= pz0) & (mz <= pz1)
            region[m, :3] = hexc(color)
        for bx0, bz0, bx1, bz1 in silk_box:
            m = (((np.abs(mx - bx0) < mmpp * 0.6) | (np.abs(mx - bx1) < mmpp * 0.6)) & (mz >= bz0) & (mz <= bz1)) | \
                (((np.abs(mz - bz0) < mmpp * 0.6) | (np.abs(mz - bz1) < mmpp * 0.6)) & (mx >= bx0) & (mx <= bx1))
            region[m, :3] = hexc("#6f757d")
        if label:
            lx, lz = label_pos if label_pos else (0.0, 0.0)
            tx = int((lx - x0) / mmpp - paint.text_width(label) / 2)
            ty = int((lz - z0) / mmpp - 2.5)
            paint.text(region, tx, ty, label, hexc("#e4e6ea"))

    return painter


def corner_pads(half, size, color="#d9a33a", n=3):
    pads = []
    for sx in (-1, 1):
        for sz in (-1, 1):
            for i in range(n):
                cx = sx * (half - size * 0.9)
                cz = sz * (half - size * 0.9 - i * size * 1.3)
                pads.append((cx - size / 2, cz - size / 2, cx + size / 2, cz + size / 2, color))
    return pads


def grommet(m, x, z, y0, y1, d, color="rubber"):
    m.box(x - d / 2, y0, z - d / 2, x + d / 2, y1, z + d / 2, color, name="grommet", skip=("down",))


def lead(m, x, y, z0, z1, d, mat):
    m.box(x - d / 2, y - d / 2, z0, x + d / 2, y + d / 2, z1, mat, name="wire", skip=("north",))


def xt_connector(m, z0, y0, w, h, length, body="xt_yellow"):
    m.box(-w / 2, y0, z0, w / 2, y0 + h, z0 + length, body, name="connector", decals={"south": xt_face})
    m.box(-w / 2 + 0.8, y0 + h, z0 + 1.0, w / 2 - 0.8, y0 + h + 0.8, z0 + length * 0.55, body, name="connector_grip")


def xt_face(region, ctx):
    h, w = region.shape[:2]
    if w >= 5 and h >= 3:
        paint.px(region, w // 3, h // 2, hexc("#3a2a08"))
        paint.px(region, w - 1 - w // 3, h // 2, hexc("#3a2a08"))


def cap(m, x, y, z, d, length, along="z"):
    m.cylinder_x(x, y, z, length, d, "cap_body", end="cap_top", along=along, name="capacitor", no_start=False)


def build_aio_whoop():
    m = kit.Model("stack", "aio_whoop", density=12.0, gui=(50, 225, 0))
    half = 12.0
    for sx in (-1, 1):
        for sz in (-1, 1):
            grommet(m, sx * 10.2, sz * 10.2, 0.0, 1.4, 2.4)
    sheet = m.sheet("pcb", -half, -half, half, half,
                    pcb_sheet(-half, -half, half, half, pads=corner_pads(half, 1.1, n=2),
                              holes=[(sx * 10.2, sz * 10.2, 0.8) for sx in (-1, 1) for sz in (-1, 1)],
                              silk_box=[(-4.5, -4.5, 4.5, 4.5)]))
    m.box(-half, 1.4, -half, half, 2.2, half, "pcb", name="pcb", sheet_up=sheet)
    m.box(-2.6, 2.2, -2.6, 2.6, 2.9, 2.6, "chip", name="mcu", rot=(0, 45, 0), origin=(0, 2.2, 0))
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box(sx * 7.0 - 1.3, 2.2, sz * 5.0 - 1.3, sx * 7.0 + 1.3, 2.8, sz * 5.0 + 1.3, "chip", name="esc_fet")
            m.box(sx * 9.4 - 1.2, 2.2, sz * 9.4 - 0.9, sx * 9.4 + 1.2, 3.6, sz * 9.4 + 0.9, "bt_white",
                  name="motor_plug", rot=(0, 45 * sx * sz, 0))
    m.box(-2.2, 2.2, 6.0, 2.2, 3.0, 9.5, "chip", name="receiver")
    m.box(2.8, 2.2, 7.2, 3.8, 2.7, 8.2, "led_blue", name="led")
    m.box(-11.0, 2.2, -2.0, -8.5, 3.6, 2.0, "steel", name="usb")
    m.box(-0.8, 2.3, 9.5, 0.8, 2.8, 16.0, "coax", name="antenna_lead")
    m.box(-0.5, 2.4, 16.0, 0.5, 2.9, 25.0, "antenna_white", name="antenna")
    lead(m, -1.1, 2.5, 12.0, 17.5, 1.0, "wire_red")
    lead(m, 1.1, 2.5, 12.0, 17.5, 1.0, "wire_black")
    m.box(-3.2, 1.0, 17.5, 3.2, 4.4, 22.0, "bt_white", name="bt_connector", decals={"south": xt_face})
    return m


def build_aio_20():
    m = kit.Model("stack", "aio_20", density=6.0, gui=(50, 225, 0))
    half = 13.5
    for sx in (-1, 1):
        for sz in (-1, 1):
            grommet(m, sx * 10.0, sz * 10.0, 0.0, 3.0, 3.6)
    sheet = m.sheet("pcb", -half, -half, half, half,
                    pcb_sheet(-half, -half, half, half, pads=corner_pads(half, 1.8),
                              holes=[(sx * 10.0, sz * 10.0, 1.1) for sx in (-1, 1) for sz in (-1, 1)],
                              silk_box=[(-5.5, -5.5, 5.5, 5.5)], label="F4", label_pos=(0.0, -9.0)))
    m.box(-half, 3.0, -half, half, 4.6, half, "pcb", name="pcb", sheet_up=sheet)
    m.box(-3.4, 4.6, -3.4, 3.4, 5.5, 3.4, "chip", name="mcu", rot=(0, 45, 0), origin=(0, 4.6, 0))
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box(sx * 8.2 - 1.6, 4.6, sz * 5.2 - 2.0, sx * 8.2 + 1.6, 5.5, sz * 5.2 + 2.0, "chip", name="esc_fet")
    for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        grommet(m, sx * 10.0, sz * 10.0, 4.6, 6.0, 3.4, color="dark_grey")
    m.box(4.0, 4.6, 7.0, 5.4, 5.3, 8.4, "led_red", name="led")
    m.box(-2.0, 4.6, 6.0, 2.0, 5.6, 9.0, "chip", name="receiver")
    m.box(-14.5, 3.4, -3.0, -11.0, 6.4, 3.0, "steel", name="usb")
    cap(m, -5.5, 7.8, 16.0, 6.3, 8.0)
    lead(m, -2.2, 6.0, 13.5, 25.0, 2.0, "wire_red")
    lead(m, 2.2, 6.0, 13.5, 25.0, 2.0, "wire_black")
    xt_connector(m, 25.0, 3.2, 10.2, 5.6, 11.0)
    m.box(-0.6, 5.0, -13.5, 0.6, 5.8, -18.0, "coax", name="antenna_lead")
    return m


def build_stack_30():
    m = kit.Model("stack", "stack_30", density=5.5, gui=(48, 225, 0))
    half = 18.0
    hole = 15.25
    for sx in (-1, 1):
        for sz in (-1, 1):
            grommet(m, sx * hole, sz * hole, 0.0, 3.0, 4.2)
    esc_sheet = m.sheet("esc", -half, -half, half, half,
                        pcb_sheet(-half, -half, half, half, pads=corner_pads(half, 2.4) +
                                  [(-6.0, 13.5, -2.0, 17.0, "#d9a33a"), (2.0, 13.5, 6.0, 17.0, "#d9a33a")],
                                  holes=[(sx * hole, sz * hole, 1.5) for sx in (-1, 1) for sz in (-1, 1)],
                                  label="ESC", label_pos=(0.0, 0.0)))
    m.box(-half, 3.0, -half, half, 4.6, half, "pcb", name="esc_pcb", sheet_up=esc_sheet)
    for sx in (-1, 1):
        for sz in (-1, 1):
            for i in range(2):
                cx = sx * 10.5
                cz = sz * (5.5 + i * 5.2) * 1.0
                m.box(cx - 2.4, 4.6, cz - 2.0, cx + 2.4, 5.7, cz + 2.0, "chip", name="fet")
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box(sx * hole - 2.2, 4.6, sz * hole - 2.2, sx * hole + 2.2, 11.0, sz * hole + 2.2, "black",
                  name="nylon_standoff", skip=("up", "down"))
    fc_sheet = m.sheet("fc", -half, -half, half, half,
                       pcb_sheet(-half, -half, half, half, pads=corner_pads(half, 1.6, color="#e7e9ec", n=2),
                                 holes=[(sx * hole, sz * hole, 1.5) for sx in (-1, 1) for sz in (-1, 1)],
                                 silk_box=[(-6.5, -6.5, 6.5, 6.5), (-13.0, 8.0, -8.0, 13.0)], label="F7",
                                 label_pos=(0.0, -11.5)))
    m.box(-half, 11.0, -half, half, 12.6, half, "pcb", name="fc_pcb", sheet_up=fc_sheet)
    m.box(-4.0, 12.6, -4.0, 4.0, 13.6, 4.0, "chip", name="mcu", rot=(0, 45, 0), origin=(0, 12.6, 0))
    m.box(-12.0, 12.6, 9.0, -9.0, 13.4, 12.0, "chip", name="gyro")
    m.box(5.0, 12.6, 8.0, 10.0, 16.8, 13.0, "black", name="buzzer", decals={"up": buzzer_top})
    m.box(9.0, 12.6, -12.0, 11.0, 13.3, -10.0, "led_blue", name="led")
    m.box(-19.0, 11.4, -3.2, -15.5, 14.4, 3.2, "steel", name="usb")
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box(sx * hole - 2.6, 12.6, sz * hole - 2.6, sx * hole + 2.6, 14.4, sz * hole + 2.6, "dark_grey",
                  name="nut", rot=(0, 45, 0))
    for sx, color in ((-1, "#e7e9ec"), (1, "#e7e9ec")):
        m.box(sx * 13.0 - 2.5, 12.6, -2.0, sx * 13.0 + 2.5, 14.2, 3.0, "bt_white", name="sh_connector")
    cap(m, -7.0, 8.0, 27.0, 10.0, 16.0)
    cap(m, 7.0, 8.0, 27.0, 10.0, 16.0)
    for sx in (-1, 1):
        m.box(sx * 7.0 - 0.4, 4.6, 17.5, sx * 7.0 + 0.4, 5.4, 19.2, "steel", name="cap_leg")
    lead(m, -2.8, 6.2, 17.0, 38.0, 3.0, "wire_red")
    lead(m, 2.8, 6.2, 17.0, 38.0, 3.0, "wire_black")
    xt_connector(m, 38.0, 2.2, 16.0, 8.0, 15.5)
    return m


def creative_sheet(x0, z0, x1, z1, holes):
    def painter(region, ctx):
        h, w = region.shape[:2]
        mmpp = (x1 - x0) / w
        paint.fill(region, hexc("#2a1240"))
        paint.noise(region, ctx, 0.03)
        yy, xx = np.mgrid[0:h, 0:w]
        mx = x0 + (xx + 0.5) * mmpp
        mz = z0 + (yy + 0.5) * mmpp
        lines = ((xx * 3 + yy * 5) % 19 == 0) | ((xx * 5 + yy * 2) % 29 == 0)
        region[lines, :3] = hexc("#43205f")
        ring = np.abs(np.hypot(mx, mz) - 7.6) < mmpp * 0.7
        region[ring, :3] = hexc("#d9a33a")
        paint.edges(region, hexc("#6b3a8f"), hexc("#12061c"))
        for hx, hz, r in holes:
            d2 = (mx - hx) ** 2 + (mz - hz) ** 2
            region[d2 <= (r + 0.9) ** 2, :3] = hexc("#d9a33a")
            region[d2 <= r * r, :3] = hexc("#08090a")
        for px0, pz0, px1, pz1, color in corner_pads((x1 - x0) / 2, 1.1, n=2):
            m = (mx >= px0) & (mx <= px1) & (mz >= pz0) & (mz <= pz1)
            region[m, :3] = hexc(color)
        tx = int((0.0 - x0) / mmpp - paint.text_width("CR") / 2)
        ty = int((-10.0 - z0) / mmpp - 2.5)
        paint.text(region, tx, ty, "CR", hexc("#f3d9ff"))

    return painter


def build_creative_fc():
    m = kit.Model("stack", "creative_fc", density=12.0, gui=(50, 225, 0))
    half = 12.0
    hole = 10.2
    holes = [(sx * hole, sz * hole, 0.8) for sx in (-1, 1) for sz in (-1, 1)]
    for sx in (-1, 1):
        for sz in (-1, 1):
            grommet(m, sx * hole, sz * hole, 0.0, 1.4, 2.4)
    sheet = m.sheet("pcb", -half, -half, half, half, creative_sheet(-half, -half, half, half, holes))
    m.box(-half, 1.4, -half, half, 2.2, half, "pcb", name="pcb", sheet_up=sheet)
    m.box(-2.8, 2.2, -2.8, 2.8, 3.1, 2.8, "chip", name="mcu", rot=(0, 45, 0), origin=(0, 2.2, 0))
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box(sx * 6.6 - 1.3, 2.2, sz * 4.6 - 1.3, sx * 6.6 + 1.3, 2.8, sz * 4.6 + 1.3, "chip", name="esc_fet")
            m.box(sx * 9.4 - 1.2, 2.2, sz * 9.4 - 0.9, sx * 9.4 + 1.2, 3.6, sz * 9.4 + 0.9, "bt_white",
                  name="motor_plug", rot=(0, 45 * sx * sz, 0))
    for x, color in ((-3.4, "led_magenta"), (0.0, "led_cyan"), (3.4, "led_gold")):
        m.box(x - 0.6, 2.2, 6.2, x + 0.6, 2.8, 7.4, color, name="led")
    m.box(-11.0, 2.2, -2.0, -8.5, 3.6, 2.0, "steel", name="usb")
    m.box(-0.8, 2.3, 9.5, 0.8, 2.8, 16.0, "coax", name="antenna_lead")
    m.box(-0.5, 2.4, 16.0, 0.5, 2.9, 25.0, "antenna_white", name="antenna")
    lead(m, -1.1, 2.5, 12.0, 17.5, 1.0, "wire_red")
    lead(m, 1.1, 2.5, 12.0, 17.5, 1.0, "wire_black")
    m.box(-3.2, 1.0, 17.5, 3.2, 4.4, 22.0, "bt_white", name="bt_connector", decals={"south": xt_face})
    return m


def buzzer_top(region, ctx):
    h, w = region.shape[:2]
    paint.disc(region, w / 2, h / 2, min(w, h) * 0.2, hexc("#050505"))


def register_materials():
    paint.register("cap_body", cap_body)
    paint.register("cap_top", paint.alu("#b3bac2"))
    paint.register("led_magenta", paint.glossy("#ff4ad8"))
    paint.register("led_cyan", paint.glossy("#4af2ff"))
    paint.register("led_gold", paint.glossy("#ffcf3a"))


def cap_body(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#1f2a44"))
    if ctx.side == "side":
        for x in range(w):
            region[:, x, :3] = hexc("#1f2a44") if x % 6 not in (0, 1) else hexc("#c9cfd8")
    paint.noise(region, ctx, 0.03)


def build_all():
    register_materials()
    return [build_aio_whoop(), build_aio_20(), build_stack_30(), build_creative_fc()]
