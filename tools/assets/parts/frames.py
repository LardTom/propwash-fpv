import math

import numpy as np

import ducts
import kit
import layout
import paint
from paint import hexc


WHOOP_LENS_BASE = (9.7, -17.8)
WHOOP_LENS = (16.0, -20.0)
CINEWHOOP_LENS = (30.0, -48.0)
RACE_PAD_MM = -2.8


def plate_sheet(x0, z0, x1, z1, holes=(), slots=(), text=None, text_color="#9aa0a8", weave=True):
    def painter(region, ctx):
        h, w = region.shape[:2]
        mmpp = (x1 - x0) / w
        mmpz = (z1 - z0) / h
        ctx.side = "top"
        paint.carbon(region, ctx)
        yy, xx = np.mgrid[0:h, 0:w]
        mx = x0 + (xx + 0.5) * mmpp
        mz = z0 + (yy + 0.5) * mmpz
        for hx, hz, r in holes:
            d2 = (mx - hx) ** 2 + (mz - hz) ** 2
            ring = d2 <= (r + mmpp) ** 2
            region[ring, :3] = hexc("#4a4f57")
            inner = d2 <= r * r
            region[inner, :3] = hexc("#0a0b0c")
        for sx0, sz0, sx1, sz1 in slots:
            m = (mx >= sx0 - mmpp) & (mx <= sx1 + mmpp) & (mz >= sz0 - mmpz) & (mz <= sz1 + mmpz)
            region[m, :3] = hexc("#4a4f57")
            m2 = (mx >= sx0) & (mx <= sx1) & (mz >= sz0) & (mz <= sz1)
            region[m2, :3] = hexc("#0a0b0c")
        if text:
            paint.text_centered(region, text, hexc(text_color), y=h - 8 if h > 20 else None)

    return painter


def pad_top(color_ring):
    def painter(region, ctx):
        h, w = region.shape[:2]
        ctx.side = "top"
        paint.carbon(region, ctx)
        cx, cy = w / 2, h / 2
        r = min(w, h) / 2
        paint.disc(region, cx, cy, r * 0.42, hexc("#0a0b0c"))
        for ang in (45, 135, 225, 315):
            a = math.radians(ang)
            paint.disc(region, cx + math.cos(a) * r * 0.62, cy + math.sin(a) * r * 0.62, max(0.7, r * 0.1),
                       hexc(color_ring))

    return painter


def x_arms(m, frame_key, root_w, tip_w, thick, split, pad_d, top=-0.6, pad_top_y=-0.3, material="carbon",
           arm_start=None, guard=None):
    hx, hz = layout.motor_axes(frame_key)
    reach = math.hypot(hx, hz)
    y0 = top - thick
    start = arm_start if arm_start is not None else root_w / 2 + 0.2
    for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        cx, cz = sx * hx, sz * hz
        yaw = round(math.degrees(math.atan2(-cz, cx)), 3)
        if yaw == int(yaw):
            yaw = int(yaw)
        m.bar(0, 0, yaw, split - start, root_w, y0, top, material, start=start, name="arm",
              skip=("west",))
        m.bar(0, 0, yaw, reach - split, tip_w, y0, top, material, start=split, name="arm", skip=("west",))
        if guard is not None:
            length = pad_d * 0.8
            m.bar(0, 0, yaw, length, tip_w + 1.2, y0 - 0.4, top + 0.4, guard[0], start=reach - pad_d / 2 - length,
                  name="arm_guard")
        sheet = m.sheet(f"pad{sx}{sz}", cx - pad_d / 2, cz - pad_d / 2, cx + pad_d / 2, cz + pad_d / 2,
                        pad_top(guard[1] if guard is not None else "#8c939c"))
        m.cylinder(cx, cz, y0 - 0.3, pad_top_y, pad_d, "carbon", name="motor_pad", sheet_up=sheet)


def standoff(m, x, z, y0, y1, d, material):
    m.box(x - d / 2, y0, z - d / 2, x + d / 2, y1, z + d / 2, material, name="standoff", skip=("up", "down"))


def carbon_plate(m, x0, z0, x1, z1, y0, y1, name, holes=(), slots=(), text=None, both_sides=False):
    sheet = m.sheet(name, x0, z0, x1, z1, plate_sheet(x0, z0, x1, z1, holes, slots, text))
    m.box(x0, y0, z0, x1, y1, z1, "carbon", name=name, sheet_up=sheet, sheet_down=sheet if both_sides else None)


def camera_cage(m, inner_x, thick, y0, y1, z0, z1, material="carbon"):
    for s in (-1, 1):
        xa, xb = s * inner_x, s * (inner_x + thick)
        m.box(xa, y0, z0 + 4, xb, y1, z1, material, name="cam_plate")
        m.box(xa, y0, z0, xb, y1 - 6, z0 + 4, material, name="cam_plate_nose")
        m.box(xa, y0 + (y1 - y0) * 0.45, z0 + 0.6, xb, y0 + (y1 - y0) * 0.45 + 2.2, z0 + 2.8, "dark_steel", name="cam_screw")


def build_whoop():
    m = kit.Model("frame", "whoop_65", density=9.0, gui=(45, 205, 0))
    fk = "whoop_65"
    h = layout.half_axis(fk)
    my = layout.FRAMES[fk]["motor_y"]
    prop = 31.0
    frame_mat = "black_matte"
    canopy = "tpu_red"
    bottom = my - 1.9
    top = ducts.top(my + layout.FRAMES[fk]["prop_height"], prop)
    duct_r = ducts.radius(prop)
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = sx * h, sz * h
            ducts.ring(m, cx, cz, bottom, top, prop, frame_mat)
            m.cylinder(cx, cz, bottom, my, 11.5, frame_mat, name="motor_cup")
            yaw_out = math.degrees(math.atan2(-sz, sx))
            for off in (90, 180, 270):
                m.bar(cx, cz, yaw_out + off, duct_r - 5.0, 1.6, bottom, bottom + 1.2, frame_mat, start=5.4, name="spoke")
            yaw_in = math.degrees(math.atan2(sz, -sx))
            m.bar(cx, cz, yaw_in, math.hypot(h, h) - 12.0 - 5.5, 5.0, my - 1.7, my + 0.5, frame_mat, start=5.5, name="arm")
    for sx, sz in ((0, -1), (0, 1)):
        m.box(-6.5, bottom, sz * (h + 0.5) - 1.6, 6.5, 3.0, sz * (h + 0.5) + 1.6, frame_mat, name="bridge")
    for sx in (-1, 1):
        m.box(sx * (h + 0.5) - 1.6, bottom, -6.5, sx * (h + 0.5) + 1.6, 3.0, 6.5, frame_mat, name="bridge")
    sheet = m.sheet("floor", -12.5, -12.5, 12.5, 12.5, floor_sheet)
    m.box(-12.5, -1.6, -12.5, 12.5, 0.0, 12.5, frame_mat, name="floor", sheet_up=sheet)
    for sx in (-1, 1):
        m.box(sx * 7.6, -4.2, -9.0, sx * 6.4, -1.6, 9.0, frame_mat, name="battery_rail")
        m.box(sx * 7.6, -4.9, -9.0, sx * 5.4, -4.2, 9.0, frame_mat, name="battery_clip")
    lens_y, lens_z = WHOOP_LENS
    tilt = math.radians(layout.FRAMES[fk]["camera_tilt_default"])
    dy = round(lens_y - WHOOP_LENS_BASE[0], 2)
    dz = round(lens_z - WHOOP_LENS_BASE[1], 2)
    lift = max(dy, round(lens_y + 7.0 * math.cos(tilt) - 6.0 * math.sin(tilt) + 0.2 - 14.0, 2))
    for sx in (-1, 1):
        m.box(sx * 12.1, 0.0, -7.5, sx * 13.3, 14.0 + lift, 8.0, canopy, name="canopy_side")
    m.box(-13.3, 0.0, 8.0, 13.3, 12.6 + lift, 9.2, canopy, name="canopy_back")
    m.box(-13.3, 14.0 + lift, -2.0 + dz, 13.3, 15.2 + lift, 9.2, canopy, name="canopy_top")
    m.box(-13.3, 12.6 + lift, 9.2, 13.3, 14.0 + lift, 7.4, canopy, name="canopy_back_top", skip=("down",))
    m.box(-12.1, 12.8 + lift, -2.0 + dz, 12.1, 14.0 + lift, -1.0 + dz, canopy, name="canopy_brow")
    low = round(top + 0.3, 2)
    for sx in (-1, 1):
        m.box(sx * 7.7, low, -13.0 + dz, sx * 9.0, 13.0 + dy, -2.0 + dz, canopy, name="cam_cheek")
        m.box(sx * 9.0, low, -9.0 + dz, sx * 12.1, low + 4.0, max(-3.0 + dz, -7.0), canopy, name="cam_cheek_base")
    m.box(-7.7, 7.0 + dy, -3.0 + dz, 7.7, 8.2 + dy, -2.0 + dz, "dark_grey", name="cam_axle_left", skip=("up", "down"))
    return m


def floor_sheet(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#232428"))
    paint.noise(region, ctx, 0.04)
    paint.edges(region, hexc("#3b3d42"), hexc("#141517"))
    for cx, cy in ((0.2, 0.2), (0.8, 0.2), (0.2, 0.8), (0.8, 0.8)):
        paint.disc(region, cx * w, cy * h, max(0.8, w * 0.05), hexc("#0c0c0d"))


def build_cinewhoop():
    m = kit.Model("frame", "cinewhoop_3", density=4.2, gui=(45, 205, 0))
    fk = "cinewhoop_3"
    h = layout.half_axis(fk)
    x_arms(m, fk, 14.0, 11.0, 3.0, 32.0, 16.0)
    prop = 76.0
    my = layout.FRAMES[fk]["motor_y"]
    bottom = -0.6
    top = ducts.top(my + layout.FRAMES[fk]["prop_height"], prop)
    duct_r = ducts.radius(prop)
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = sx * h, sz * h
            ducts.ring(m, cx, cz, bottom, top, prop, "tpu_black", lip_material="tpu_teal", lip_name="duct_rim")
            yaw_out = math.degrees(math.atan2(-sz, sx))
            for off in (90, 270):
                m.bar(cx, cz, yaw_out + off, duct_r - 8.0 + 0.4, 4.0, -3.4, bottom, "carbon", start=8.0, name="duct_brace")
    carbon_plate(m, -20.0, -30.0, 20.0, 34.0, -3.0, 0.0, "bottom_plate",
                 holes=[(sx * 10.0, sz * 10.0, 1.2) for sx in (-1, 1) for sz in (-1, 1)],
                 slots=[(-12.0, 22.0, 12.0, 26.0)])
    for sx in (-1, 1):
        for sz in (-18.0, 26.0):
            standoff(m, sx * 15.0, sz, 0.0, 22.0, 4.0, "alu_teal")
    carbon_plate(m, -18.0, -20.0, 18.0, 32.0, 22.0, 24.0, "top_plate",
                 slots=[(-14.0, -8.0, -10.0, 16.0), (10.0, -8.0, 14.0, 16.0)], text="3IN")
    cy, cz = CINEWHOOP_LENS
    floor = round(top + 0.4, 2)
    camera_cage(m, 10.6, 2.0, 0.0, floor, -37.0, -18.0)
    camera_cage(m, 10.6, 2.0, floor, cy + 6.0, cz - 2.0, cz + 18.0)
    m.box(-12.6, floor, cz - 2.0, 12.6, floor + 1.2, cz + 18.0, "carbon", name="cam_floor")
    return m


def build_freestyle():
    m = kit.Model("frame", "freestyle_5", density=3.6, gui=(52, 200, 0))
    fk = "freestyle_5"
    x_arms(m, fk, 16.0, 12.5, 6.0, 62.0, 25.0, guard=("tpu_red", "#d6333a"))
    carbon_plate(m, -21.0, -64.0, 21.0, 58.0, -3.0, 0.0, "bottom_plate",
                 holes=[(sx * 15.25, sz * 15.25, 1.6) for sx in (-1, 1) for sz in (-1, 1)],
                 slots=[(-10.0, 30.0, 10.0, 36.0)])
    for sx in (-1, 1):
        for sz in (-36.0, 4.0, 44.0):
            standoff(m, sx * 17.0, sz, 0.0, 26.0, 5.0, "alu_red")
    carbon_plate(m, -20.0, -40.0, 20.0, 50.0, 26.0, 28.5, "top_plate",
                 slots=[(-17.0, -20.0, -12.0, 30.0), (12.0, -20.0, 17.0, 30.0)],
                 holes=[(0.0, -32.0, 2.0), (0.0, 42.0, 2.0)], text="5IN")
    m.box(-11.0, 28.5, -18.0, 11.0, 29.3, 30.0, "rubber", name="battery_pad", skip=("down",))
    camera_cage(m, 10.8, 2.5, 0.0, 26.0, -65.0, -36.0)
    m.box(-13.0, 0.0, 50.0, 13.0, 10.0, 58.0, "tpu_red", name="rear_mount")
    m.box(-9.0, 10.0, 52.0, 9.0, 14.0, 58.0, "tpu_red", name="rear_mount_top")
    return m


def build_race():
    m = kit.Model("frame", "race_5", density=3.6, gui=(52, 200, 0))
    fk = "race_5"
    x_arms(m, fk, 10.0, 7.5, 5.0, 52.0, 22.0, guard=("tpu_blue", "#2f7de0"))
    carbon_plate(m, -16.0, -56.0, 16.0, 46.0, -2.0, 0.0, "bottom_plate",
                 holes=[(sx * 15.25, sz * 15.25, 1.6) for sx in (-1, 1) for sz in (-1, 1)],
                 slots=[(-8.0, 30.0, 8.0, 35.0), (-4.0, -40.0, 4.0, -30.0), (-13.0, -14.0, -9.0, 26.0), (9.0, -14.0, 13.0, 26.0)],
                 both_sides=True)
    m.box(-8.0, RACE_PAD_MM, -18.0, 8.0, -2.0, 26.0, "rubber", name="battery_pad", skip=("up",))
    for sx in (-1, 1):
        for sz in (-30.0, 38.0):
            standoff(m, sx * 13.5, sz, 0.0, 18.0, 4.5, "alu_blue")
    carbon_plate(m, -16.0, -34.0, 16.0, 44.0, 18.0, 20.0, "top_plate",
                 holes=[(0.0, -26.0, 2.0), (0.0, 36.0, 2.0)], text="RACE")
    camera_cage(m, 10.6, 2.0, 0.0, 18.0, -57.0, -32.0)
    m.box(-9.0, 0.0, 44.0, 9.0, 7.0, 50.0, "tpu_black", name="rear_mount")
    return m


def build_long_range():
    m = kit.Model("frame", "long_range_7", density=3.0, gui=(52, 200, 0))
    fk = "long_range_7"
    x_arms(m, fk, 16.0, 12.0, 5.0, 70.0, 30.0, guard=("tpu_orange", "#f2702a"))
    carbon_plate(m, -22.0, -68.0, 22.0, 66.0, -2.5, 0.0, "bottom_plate",
                 holes=[(sx * 15.25, sz * 15.25, 1.6) for sx in (-1, 1) for sz in (-1, 1)],
                 slots=[(-10.0, 34.0, 10.0, 40.0)])
    for sx in (-1, 1):
        for sz in (-40.0, 6.0, 52.0):
            standoff(m, sx * 18.0, sz, 0.0, 30.0, 5.0, "alu_orange")
    carbon_plate(m, -22.0, -44.0, 22.0, 62.0, 30.0, 32.5, "top_plate",
                 slots=[(-19.0, -24.0, -14.0, 36.0), (14.0, -24.0, 19.0, 36.0)], text="7IN")
    m.box(-12.0, 32.5, -22.0, 12.0, 33.3, 36.0, "rubber", name="battery_pad", skip=("down",))
    camera_cage(m, 10.8, 2.5, 0.0, 30.0, -70.0, -40.0)
    m.box(-2.0, 32.5, 50.0, 2.0, 58.0, 54.0, "dark_steel", name="gps_mast")
    m.box(-11.0, 58.0, 41.0, 11.0, 64.0, 63.0, "dark_grey", name="gps_puck",
          decals={"up": gps_decal})
    m.box(-13.0, 0.0, 58.0, 13.0, 10.0, 66.0, "tpu_orange", name="rear_mount")
    return m


def gps_decal(region, ctx):
    h, w = region.shape[:2]
    paint.rect(region, 1, 1, w - 1, h - 1, hexc("#e9e6dc"))
    paint.text_centered(region, "GPS", hexc("#c62828"))
    paint.px(region, w // 2, 2, hexc("#c62828"))


def register_materials():
    paint.register("alu_red", paint.alu("#d1352b"))
    paint.register("alu_blue", paint.alu("#2e6fd8"))
    paint.register("alu_teal", paint.alu("#19a99c"))
    paint.register("alu_orange", paint.alu("#e98a1f"))


def build_all():
    register_materials()
    return [build_whoop(), build_cinewhoop(), build_freestyle(), build_race(), build_long_range()]
