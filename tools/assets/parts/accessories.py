import numpy as np

import kit
import paint
import video
from paint import hexc
from stacks import lead

HINGE_CLEARANCE = 5.0
FINGER = 3.0
LEG = 6.3

LED_STRIP = dict(width=32.0, height=3.0, depth=8.0, leds=8)
LIGHT = dict(width=46.0, height=30.0, depth=26.0, lens_len=7.0, lens_x=0.0)
HEAVY = dict(width=60.0, height=45.0, depth=35.0, lens_len=6.0, lens_x=-11.0)
GRIPPER = dict(width=24.0, height=20.0, depth=22.0)
FLASHBACK = dict(width=42.0, height=28.0, depth=24.0, lens_len=5.0, lens_x=0.0)
FB_ACCENT = "#ffbb00"
MOUNT_TPU = "tpu_red"


def camera_fingers(m, material):
    for sx in (-1, 1):
        x0 = sx * (FINGER / 2 + 0.1)
        x1 = sx * (FINGER * 1.5 - 0.1)
        m.box(x0, -2.6, -4.5, x1, HINGE_CLEARANCE, 4.5, material, name="cam_finger")


def lens(m, cx, cy, z_front, length, d, barrel="acc_lens_barrel"):
    m.cylinder_x(cx, cy, z_front + length / 2, length, d, barrel, end=barrel, along="z", name="lens_barrel", bevel=0.5)
    m.box(cx - d * 0.38, cy - d * 0.38, z_front - 0.25, cx + d * 0.38, cy + d * 0.38, z_front, "acc_lens_glass",
          name="lens_glass", only=("north",))


def front_screen(region, ctx):
    h, w = region.shape[:2]
    if w < 12 or h < 8:
        return
    x0, x1 = int(w * 0.08), int(w * 0.38)
    y0, y1 = int(h * 0.30), int(h * 0.68)
    paint.rect(region, x0 - 1, y0 - 1, x1 + 1, y1 + 1, hexc("#0c0d0f"))
    paint.rect(region, x0, y0, x1, y1, hexc("#1d3346"))
    paint.rect(region, x0 + 1, y0 + 1, x1 - 1, y0 + 2, hexc("#6fb6e8"))
    paint.px(region, x0 + 1, y1 - 2, hexc("#e0473b"))
    paint.px(region, int(w * 0.92), int(h * 0.12), hexc("#e0473b"))
    for i in range(3):
        paint.px(region, int(w * 0.12) + i * 2, int(h * 0.12), hexc("#15161a"))


def back_screen(region, ctx):
    h, w = region.shape[:2]
    if w < 8 or h < 6:
        return
    paint.rect(region, 1, 1, w - 1, h - 1, hexc("#0c0d0f"))
    paint.rect(region, 2, 2, w - 2, h - 2, hexc("#15222e"))
    for y in range(2, h - 2):
        t = (y - 2) / max(1, h - 5)
        region[y, 2:w - 2, :3] = paint.mix(hexc("#22384c"), hexc("#101a24"), t)
    paint.rect(region, 3, 3, w // 3, 4, hexc("#8cc8f0"))
    paint.px(region, w - 4, 3, hexc("#e0473b"))


def side_door(region, ctx):
    h, w = region.shape[:2]
    if w < 8 or h < 8:
        return
    x0, x1 = int(w * 0.2), int(w * 0.8)
    y0, y1 = int(h * 0.22), int(h * 0.85)
    dark = hexc("#141518")
    paint.rect(region, x0, y0, x1, y0 + 1, dark)
    paint.rect(region, x0, y1 - 1, x1, y1, dark)
    paint.rect(region, x0, y0, x0 + 1, y1, dark)
    paint.rect(region, x1 - 1, y0, x1, y1, dark)
    paint.rect(region, (x0 + x1) // 2 - 1, y0 + 2, (x0 + x1) // 2 + 1, y0 + 3, hexc("#ffbb00"))


def heavy_top(region, ctx):
    h, w = region.shape[:2]
    paint.edges(region, hexc("#44474d"), hexc("#1a1b1e"))
    if w >= 10 and h >= 4:
        paint.px(region, int(w * 0.2), h // 2, hexc("#e0473b"))


def rewind_icon(region, x, y, size, color):
    for tri in range(2):
        ox = x + tri * size
        for col in range(size):
            span = max(0, (col + 1) // 2)
            for row in range(-span, span + 1):
                paint.px(region, ox + col, y + row, color)


def fb_front(region, ctx):
    h, w = region.shape[:2]
    if w < 16 or h < 10:
        return
    paint.edges(region, hexc("#3c3850"), hexc("#14121c"))
    size = max(3, h // 7)
    rewind_icon(region, int(w * 0.06), int(h * 0.16) + size // 2, size, hexc(FB_ACCENT))
    paint.px(region, int(w * 0.92), int(h * 0.14), hexc("#e0473b"))


def fb_back(region, ctx):
    h, w = region.shape[:2]
    if w < 16 or h < 10:
        return
    x0, x1 = int(w * 0.12), int(w * 0.88)
    y0, y1 = int(h * 0.18), int(h * 0.72)
    paint.rect(region, x0 - 1, y0 - 1, x1 + 1, y1 + 1, hexc("#0c0b10"))
    for y in range(y0, y1):
        t = (y - y0) / max(1, y1 - y0 - 1)
        region[y, x0:x1, :3] = paint.mix(hexc("#231f33"), hexc("#120f1b"), t)
    line_y = (y0 + y1) // 2 + 1
    paint.rect(region, x0 + 2, line_y, x1 - 2, line_y + 1, hexc("#6a6488"))
    span = x1 - x0 - 6
    for i in range(5):
        kx = x0 + 3 + (span * i) // 4
        paint.px(region, kx, line_y - 1, hexc(FB_ACCENT))
        paint.px(region, kx - 1, line_y, hexc(FB_ACCENT))
        paint.px(region, kx, line_y, hexc(FB_ACCENT))
        paint.px(region, kx + 1, line_y, hexc(FB_ACCENT))
        paint.px(region, kx, line_y + 1, hexc(FB_ACCENT))
    head = x0 + 3 + (span * 5) // 8
    paint.rect(region, head, y0 + 2, head + 1, y1 - 2, hexc("#e0473b"))
    paint.rect(region, x0 + 2, y0 + 2, x0 + 2 + max(3, span // 4), y0 + 3, hexc("#b9b2d8"))
    paint.px(region, int(w * 0.9), int(h * 0.86), hexc("#e0473b"))


def fb_top(region, ctx):
    h, w = region.shape[:2]
    paint.edges(region, hexc("#423d58"), hexc("#16141e"))
    if w >= 12 and h >= 6:
        paint.rect(region, 2, h // 2 - 1, max(3, w // 3), h // 2, hexc(FB_ACCENT))


def fb_side(region, ctx):
    h, w = region.shape[:2]
    if w < 10 or h < 8:
        return
    dark = hexc("#0f0e14")
    cx, cy = int(w * 0.32), int(h * 0.62)
    paint.rect(region, cx - 3, cy - 1, cx + 3, cy + 1, dark)
    for i in range(4):
        vx = int(w * 0.55) + i * 3
        paint.rect(region, vx, int(h * 0.25), vx + 1, int(h * 0.75), hexc("#1b1925"))


def core_label(region, ctx):
    h, w = region.shape[:2]
    if w >= 8 and h >= 5:
        paint.rect(region, 1, h // 2 - 1, w - 1, h // 2 + 1, hexc("#ffbb00"))


def naked_label(region, ctx):
    h, w = region.shape[:2]
    if w >= 14 and h >= 6:
        paint.text_centered(region, "HD", hexc("#ffbb00"))


def led_face(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#fbfcff"))
    if w >= 2 and h >= 2:
        paint.px(region, 0, 0, hexc("#ffffff"))
        region[h - 1, :, :3] = hexc("#cfd6e0")


def led_diffuser(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#2a2d33"))
    paint.noise(region, ctx, 0.02)
    if h >= 3:
        region[0, :, :3] = hexc("#9aa1aa")
        region[h - 1, :, :3] = hexc("#9aa1aa")


def build_led_strip():
    p = LED_STRIP
    m = kit.Model("accessory", "led_strip", density=10.0, gui=(-38, 30, 0), gui_fill=15.0)
    half_w, h, half_d = p["width"] / 2, p["height"], p["depth"] / 2
    m.box(-half_w, -h, -half_d, half_w, 0.0, half_d, "acc_led_channel", name="channel",
          faces={"down": "acc_led_diffuser"}, lowres={"down": 2.0})
    m.box(-half_w - 1.0, -h + 0.4, -half_d + 0.6, -half_w, -0.4, half_d - 0.6, "acc_led_cap", name="end_cap")
    m.box(half_w, -h + 0.4, -half_d + 0.6, half_w + 1.0, -0.4, half_d - 0.6, "acc_led_cap", name="end_cap")
    spacing = p["width"] / p["leds"]
    for i in range(p["leds"]):
        x = -half_w + spacing * (i + 0.5)
        m.box(x - 1.2, -h - 0.4, -1.2, x + 1.2, -h, 1.2, "acc_led", name="led", only=("down", "north", "south", "east", "west"))
    lead(m, half_w + 1.0 - 0.5, -h / 2, half_d - 3.0, half_d + 2.0, 1.0, "wire_red")
    lead(m, half_w + 1.0 - 0.5, -h / 2, half_d - 1.5, half_d + 3.5, 1.0, "wire_black")
    return m


def build_light():
    p = LIGHT
    m = kit.Model("accessory", "action_cam_light", density=8.0, gui=(20, 205, 0), gui_fill=14.5)
    half_w, half_d = p["width"] / 2, p["depth"] / 2
    y0 = HINGE_CLEARANCE
    top = y0 + p["height"]
    camera_fingers(m, "acc_cage")
    m.box(-half_w, y0, -half_d, half_w, y0 + 2.0, half_d, "acc_cage", name="cage_plate")
    m.box(-half_w - 1.2, y0 - 1.6, -half_d + 2.0, half_w + 1.2, y0, half_d - 2.0, MOUNT_TPU, name="tpu_cradle")
    for sx in (-1, 1):
        m.box(sx * half_w, y0, -half_d + 2.0, sx * (half_w + 1.2), y0 + 3.4, half_d - 2.0, MOUNT_TPU, name="tpu_lip")
    m.box(-half_w - 1.2, y0 + 3.4, -3.0, half_w + 1.2, y0 + 4.2, 3.0, "strap", name="strap")
    m.box(-half_w + 1.0, y0 + 2.0, -half_d + 1.0, half_w - 1.0, y0 + 3.6, half_d - 1.0, "pcb", name="board_main")
    m.box(-9.0, y0 + 3.6, -6.0, 9.0, y0 + 6.0, 7.0, "chip", name="processor")
    m.box(11.0, y0 + 3.6, -8.0, 17.0, y0 + 5.2, 0.0, "chip", name="memory")
    m.box(-half_w + 1.0, y0 + 6.0, -half_d + 1.0, half_w - 1.0, y0 + 7.6, half_d - 1.0, "pcb", name="board_io")
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box(sx * (half_w - 3.4) - 1.0, y0 + 2.0, sz * (half_d - 3.4) - 1.0,
                  sx * (half_w - 3.4) + 1.0, top - 9.0, sz * (half_d - 3.4) + 1.0, "brass", name="standoff",
                  skip=("up", "down"))
    m.box(-half_w, top - 9.0, -half_d, half_w, top - 6.0, half_d, "acc_heatsink_base", name="heatsink_base",
          decals={"south": naked_label})
    fins = 7
    for i in range(fins):
        x = -half_w + 4.0 + i * (p["width"] - 8.0) / (fins - 1)
        m.box(x - 0.9, top - 6.0, -half_d + 1.5, x + 0.9, top, half_d - 1.5, "heatsink", name="fin")
    m.box(-9.0, y0 + 6.0, -half_d, 9.0, y0 + 24.0, -half_d + 6.0, "acc_cam_black", name="lens_module")
    m.box(-15.0, y0 + 7.6, -half_d + 6.0, 15.0, top - 9.0, half_d - 4.5, "acc_core", name="core",
          decals={"east": core_label, "west": core_label})
    m.box(-6.0, y0 + 16.0, -half_d + 5.5, 6.0, y0 + 17.0, -half_d + 6.0, "coax", name="ribbon")
    lens(m, p["lens_x"], y0 + p["height"] / 2, -half_d - p["lens_len"], p["lens_len"], 13.0)
    m.box(half_w, y0 + 2.2, 2.0, half_w + 3.0, y0 + 5.6, 9.0, "xt_black", name="power_plug")
    for i, mat in enumerate(("wire_red", "wire_black")):
        z = 4.0 + i * 2.2
        m.box(half_w + 0.9, -4.0, z - 0.6, half_w + 2.1, y0 + 2.2, z + 0.6, mat, name="wire", skip=("up",))
    m.box(half_w - 5.0, y0 + 7.6, half_d - 4.0, half_w - 3.0, y0 + 8.6, half_d - 2.0, "led_red", name="status_led")
    return m


def build_heavy():
    p = HEAVY
    m = kit.Model("accessory", "action_cam_heavy", density=5.5, gui=(20, 205, 0), gui_fill=15.0)
    half_w, half_d = p["width"] / 2, p["depth"] / 2
    y0 = HINGE_CLEARANCE
    top = y0 + p["height"]
    camera_fingers(m, "acc_housing")
    m.box(-half_w + 1.5, y0 + 1.5, -half_d + 1.0, half_w - 1.5, top - 1.5, half_d - 1.0, "acc_heavy_body", name="body",
          faces={"north": "acc_heavy_front", "south": "acc_heavy_back", "up": "acc_heavy_top"},
          decals={"north": front_screen, "south": back_screen, "east": side_door, "up": heavy_top})
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box(sx * half_w - (3.0 if sx > 0 else 0.0), y0, sz * half_d - (2.5 if sz > 0 else 0.0),
                  sx * half_w + (0.0 if sx > 0 else 3.0), top, sz * half_d + (0.0 if sz > 0 else 2.5),
                  "acc_housing", name="housing_edge")
        m.box(sx * half_w - (3.0 if sx > 0 else 0.0), y0, -half_d + 2.5, sx * half_w + (0.0 if sx > 0 else 3.0), y0 + 1.5,
              half_d - 2.5, "acc_housing", name="housing_floor", skip=("up",))
        m.box(sx * half_w - (3.0 if sx > 0 else 0.0), top - 1.5, -half_d + 2.5, sx * half_w + (0.0 if sx > 0 else 3.0), top,
              half_d - 2.5, "acc_housing", name="housing_roof", skip=("down",))
    m.box(-half_w + 3.0, y0, -half_d + 2.5, half_w - 3.0, y0 + 1.5, half_d - 2.5, "acc_housing", name="housing_base", skip=("up",))
    lx, ly = p["lens_x"], y0 + p["height"] / 2
    m.box(lx - 11.5, ly - 11.5, -half_d - p["lens_len"] + 1.5, lx + 11.5, ly + 11.5, -half_d, "acc_housing", name="lens_mount")
    lens(m, lx, ly, -half_d - p["lens_len"], 1.5, 18.0, barrel="acc_lens_cover")
    m.cylinder(18.0, 0.0, top, top + 2.2, 9.0, "acc_shutter", name="shutter")
    m.box(-half_w - 0.8, ly + 6.0, -4.0, -half_w, ly + 14.0, 4.0, "acc_housing", name="mode_button")
    return m


def servo_label(region, ctx):
    h, w = region.shape[:2]
    if w >= 10 and h >= 5:
        paint.rect(region, 1, h // 2 - 1, w - 1, h // 2 + 1, hexc("#e8e8e4"))
        paint.px(region, w - 3, 1, hexc("#ffbb00"))


def jaw_grip(region, ctx):
    h, w = region.shape[:2]
    for y in range(0, h, 2):
        region[y, :, :3] = paint.mix(region[y, :, :3], hexc("#5d6268"), 0.5)


def build_gripper():
    p = GRIPPER
    m = kit.Model("accessory", "gripper", density=10.0, gui=(10, 210, 0), gui_fill=14.0)
    half_w, h, half_d = p["width"] / 2, p["height"], p["depth"] / 2
    m.box(-8.0, -1.6, -half_d + 2.0, 8.0, 0.0, half_d - 2.0, MOUNT_TPU, name="mount_plate")
    for sz in (-1, 1):
        m.box(-8.0, -2.8, sz * (half_d - 2.0) - (1.4 if sz > 0 else 0.0), 8.0, -1.6,
              sz * (half_d - 2.0) + (0.0 if sz > 0 else 1.4), MOUNT_TPU, name="mount_lip")
    m.box(-5.5, -10.0, -8.5, 5.5, -1.6, 7.5, "acc_gripper_body", name="servo", decals={"east": servo_label, "west": servo_label})
    for sz in (-1, 1):
        m.box(-5.5, -4.2, sz * 7.5 + (0.0 if sz > 0 else -2.4) - (1.0 if sz < 0 else 0.0), 5.5, -3.0,
              sz * 7.5 + (2.4 if sz > 0 else 0.0) - (1.0 if sz < 0 else 0.0), "acc_gripper_body", name="servo_tab")
    m.box(5.5, -8.4, -5.5, 6.6, -6.2, 1.5, "white", name="servo_horn")
    m.box(-half_w + 1.0, -11.6, -3.0, half_w - 1.0, -10.0, 3.0, "acc_claw_frame", name="claw_bar")
    for sx in (-1, 1):
        x_out = sx * (half_w - 0.6)
        x_in = sx * (half_w - 2.4)
        m.box(min(x_out, x_in), -16.0, -2.5, max(x_out, x_in), -10.4, 2.5, "acc_claw", name="jaw",
              decals={"east" if sx > 0 else "west": jaw_grip})
        cx = sx * 8.7
        m.box(cx - 0.9, -20.4, -2.5, cx + 0.9, -14.6, 2.5, "acc_claw", name="jaw_finger", rot=(0, 0, -sx * 45.0),
              origin=(cx, -17.5, 0.0))
        tip = sx * 6.4
        m.box(tip - 1.2, -h, -2.2, tip + 1.2, -18.4, 2.2, "acc_claw_pad", name="jaw_pad")
        m.cylinder_x(sx * (half_w - 1.5), -11.0, 0.0, 6.4, 2.2, "steel", name="jaw_pivot", along="z")
    lead(m, 4.0, -5.4, half_d - 4.0, half_d + 0.5, 0.9, "wire_red")
    lead(m, 4.0, -6.6, half_d - 4.0, half_d + 0.5, 0.9, "wire_black")
    lead(m, 4.0, -7.8, half_d - 4.0, half_d + 0.5, 0.9, "wire_yellow")
    return m


def build_flashback():
    p = FLASHBACK
    m = kit.Model("accessory", "action_cam_flashback", density=6.0, gui=(20, 205, 0), gui_fill=14.0)
    half_w, half_d = p["width"] / 2, p["depth"] / 2
    y0 = HINGE_CLEARANCE
    top = y0 + p["height"]
    camera_fingers(m, "acc_fb_shell")
    m.box(-half_w + 1.0, y0, -half_d + 1.0, half_w - 1.0, y0 + 1.5, half_d - 1.0, "acc_fb_shell", name="base_plate", skip=("up",))
    m.box(-half_w + 1.2, y0 + 1.5, -half_d, half_w - 1.2, top, half_d, "acc_fb_body", name="body",
          faces={"north": "acc_fb_front", "south": "acc_fb_back", "up": "acc_fb_top"},
          decals={"north": fb_front, "south": fb_back, "up": fb_top, "east": fb_side})
    for sx in (-1, 1):
        x0 = sx * (half_w - 1.2)
        x1 = sx * half_w
        m.box(x0, y0 + 1.5, -half_d - 0.4, x1, top + 0.4, half_d + 0.4, "acc_fb_shell", name="side_rail")
    lx, ly = p["lens_x"], y0 + p["height"] / 2
    m.box(lx - 8.5, ly - 8.5, -half_d - 1.2, lx + 8.5, ly + 8.5, -half_d, "acc_fb_ring", name="lens_ring")
    lens(m, lx, ly, -half_d - p["lens_len"], p["lens_len"], 13.0)
    m.cylinder(11.0, 3.0, top, top + 1.8, 6.5, "acc_shutter", name="shutter")
    m.box(-15.0, top, -4.0, -5.0, top + 1.0, 4.0, "acc_fb_shell", name="mode_button")
    m.box(half_w, y0 + 2.2, 2.0, half_w + 3.0, y0 + 5.6, 9.0, "xt_black", name="power_plug")
    for i, mat in enumerate(("wire_red", "wire_black")):
        z = 4.0 + i * 2.2
        m.box(half_w + 0.9, -4.0, z - 0.6, half_w + 2.1, y0 + 2.2, z + 0.6, mat, name="wire", skip=("up",))
    return m


def build_mount_top():
    m = kit.Model("accessory", "mount_top", density=10.0, gui=(30, 225, 0))
    m.box(-14.6, -8.7, -4.0, 14.6, -6.0, 26.0, MOUNT_TPU, name="foot")
    for sx in (-1, 1):
        m.box(sx * 12.8, -8.7 - LEG, 2.0, sx * 14.6, -8.7, 24.0, MOUNT_TPU, name="clamp_leg")
    m.box(-8.5, -6.0, -6.0, 8.5, -3.0, 8.0, MOUNT_TPU, name="riser")
    m.box(-8.5, -6.0, 8.0, 8.5, -4.2, 20.0, MOUNT_TPU, name="brace")
    for x in (-6.0, 0.0, 6.0):
        m.box(x - FINGER / 2, -3.0, -4.5, x + FINGER / 2, 3.0, 4.5, MOUNT_TPU, name="mount_finger")
    m.cylinder_x(0.0, 0.0, 0.0, 17.0, 3.0, "steel", name="bolt")
    m.cylinder_x(10.5, 0.0, 0.0, 4.0, 8.5, "acc_knob", name="thumb_screw")
    m.cylinder_x(-9.5, 0.0, 0.0, 2.0, 5.5, "dark_steel", name="nut")
    return m


def register_materials():
    paint.register("acc_led_channel", paint.alu("#b9c0c8"))
    paint.register("acc_led_diffuser", led_diffuser)
    paint.register("acc_led_cap", paint.plastic("#1d1f23"))
    paint.register("acc_led", led_face)
    paint.register("acc_cage", paint.plastic("#1b1c1f"))
    paint.register("acc_heatsink_base", paint.alu("#3f6f8c"))
    paint.register("acc_cam_black", paint.plastic("#141518"))
    paint.register("acc_lens_barrel", paint.alu("#26282c"))
    paint.register("acc_lens_cover", paint.alu("#5d6268"))
    paint.register("acc_core", paint.plastic("#26282d"))
    paint.register("acc_lens_glass", video.lens_glass)
    paint.register("acc_heavy_body", paint.plastic("#3b3e44", 0.04))
    paint.register("acc_heavy_front", paint.plastic("#34373c", 0.03))
    paint.register("acc_heavy_back", paint.plastic("#34373c", 0.03))
    paint.register("acc_heavy_top", paint.plastic("#3b3e44", 0.04))
    paint.register("acc_housing", paint.plastic("#1c1d21", 0.06))
    paint.register("acc_shutter", paint.plastic("#d6352c"))
    paint.register("acc_knob", paint.alu("#e8a312"))
    paint.register("acc_gripper_body", paint.plastic("#2a5db0", 0.04))
    paint.register("acc_claw", paint.alu("#b9c0c8"))
    paint.register("acc_claw_frame", paint.alu("#3d4147"))
    paint.register("acc_claw_pad", paint.plastic("#1b1c1f", 0.08))
    paint.register("acc_fb_body", paint.plastic("#2d2a3a", 0.04))
    paint.register("acc_fb_front", paint.plastic("#27243a", 0.03))
    paint.register("acc_fb_back", paint.plastic("#27243a", 0.03))
    paint.register("acc_fb_top", paint.plastic("#2d2a3a", 0.04))
    paint.register("acc_fb_shell", paint.plastic("#16151c", 0.05))
    paint.register("acc_fb_ring", paint.alu("#d9a21a"))


def build_all():
    register_materials()
    return [build_led_strip(), build_light(), build_heavy(), build_flashback(), build_gripper()]


def build_bases():
    register_materials()
    return [build_mount_top()]
