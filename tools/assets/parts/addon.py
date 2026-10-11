import argparse
import json
import math
import shutil
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))

import batteries
import ducts
import frames
import kit
import layout
import motors
import paint
import paintable
import props
import stacks
import video
from paint import hexc

CATEGORIES = ("frame", "stack", "motor", "prop", "video", "battery", "accessory")
MODEL_CATEGORIES = ("frame", "stack", "motor", "prop", "video", "battery")
PROP_DEFAULT_COLORS = {
    "polycarbonate": "#e4e4ea",
    "glass_fiber_nylon": "#5c6068",
    "carbon_fiber_nylon": "#3a3c42",
    "carbon": "#2b2c31",
}
LION_DENSITY = 2.4
LIPO_DENSITY = 2.1
TPU_REFERENCE = paintable.CHANNELS["tpu"]
WALL_ICON = {"type": "minecraft:special", "base": "propwash:item/part_wall", "model": {"type": "propwash:part", "wall": True}}


def special_icon(category):
    base = f"propwash:item/part_base/{category}"
    icon = {"type": "minecraft:special", "base": base, "model": {"type": "propwash:part"}}
    box = {"type": "minecraft:special", "base": base, "model": {"type": "propwash:part", "box": True}}
    ground = {"type": "minecraft:special", "base": base, "model": {"type": "propwash:part", "ground": True}}
    hand = {"type": "minecraft:special", "base": base, "model": {"type": "propwash:part", "hand": True}}
    wall = category in kit.WALL_CATEGORIES
    cases = [{"when": "fixed", "model": WALL_ICON}] if wall else []
    cases.append({"when": "ground", "model": ground})
    cases.append({"when": [c for c in kit.BOX_CONTEXTS if c != "ground" and not (wall and c == "fixed")], "model": box})
    hands = [side for ctx in kit.HAND_CONTEXTS if (category, ctx) not in kit.CATEGORY_TARGET_PX
             for side in (ctx, ctx.replace("righthand", "lefthand"))]
    if hands:
        cases.append({"when": hands, "model": hand})
    return {"model": {"type": "minecraft:select", "property": "minecraft:display_context", "cases": cases, "fallback": icon}}


class Output:
    def __init__(self, assets_root, namespace, bbmodel_root=None):
        self.root = Path(assets_root)
        self.namespace = namespace
        self.bbmodel_root = Path(bbmodel_root) if bbmodel_root else None
        self.paint_groups = {}
        self.motor_seats = {}
        self.written = []

    def asset(self, resource_id, folder, suffix):
        ns, path = resource_id.split(":", 1)
        return self.root / ns / folder / f"{path}{suffix}"

    def texture(self, sprite, img):
        path = self.asset(sprite, "textures", ".png")
        kit.save_png(path, img)
        self.written.append(path)

    def model(self, model, display):
        path = self.asset(model.model_id, "models", ".json")
        kit.dump_json(path, model.to_json(display))
        self.written.append(path)
        if self.bbmodel_root is not None:
            kit.dump_json(self.bbmodel_root / f"{model.category}/{model.key}.bbmodel", model.to_bbmodel(display))

    def render_item(self, model, tints=None):
        path = self.root / self.namespace / "items/drone_render" / model.category / f"{model.key}.json"
        kit.dump_json(path, {"model": kit.model_node(model.model_id, tints)})
        self.written.append(path)

    def icon(self, key, data):
        path = self.root / self.namespace / "items" / f"{key}.json"
        kit.dump_json(path, data)
        self.written.append(path)


MODEL_MIN = -16.0
MODEL_MAX = 32.0


def fits_model_bounds(model):
    for el in model.elements:
        for value in list(el.frm) + list(el.to):
            if value < MODEL_MIN or value > MODEL_MAX:
                return False
    return True


def clamp(value, low, high):
    return max(low, min(high, value))


def accent_of(definition):
    visual = definition.get("visual") or {}
    accent = visual.get("accent")
    return accent if isinstance(accent, str) and accent.startswith("#") and len(accent) == 7 else None


def dim_to_reference(color, reference):
    rgb = hexc(color)
    top = max(float(np.max(rgb)), 1.0)
    if top <= reference - 2:
        return color
    scale = (reference - 2) / top
    return "#%02x%02x%02x" % tuple(int(round(c * scale)) for c in rgb)


def stator_of(definition):
    size = definition.get("stator_mm")
    if isinstance(size, list) and len(size) == 2:
        return float(size[0]), float(size[1])
    label = str(definition.get("stator", "2207"))
    return float(label[:2]), float(label[2:])


def load_parts(parts_dir):
    parts = []
    root = Path(parts_dir)
    for category in CATEGORIES:
        folder = root / category
        if not folder.is_dir():
            continue
        for file in sorted(folder.glob("*.json")):
            parts.append((category, file.stem, json.loads(file.read_text(encoding="utf-8"))))
    return parts


def arm_positions(definition):
    arms = definition.get("arms")
    order = ("front_left", "front_right", "rear_left", "rear_right")
    if isinstance(arms, dict) and all(k in arms for k in order):
        return [[float(v) for v in arms[k]["pos"]] for k in order]
    wb = float(definition["wheelbase_mm"])
    s = wb / (2.0 * math.sqrt(2.0))
    return [[-s, 0.0, -s], [s, 0.0, -s], [-s, 0.0, s], [s, 0.0, s]]


def max_prop(definition):
    if "props_mm" in definition:
        return float(definition["props_mm"][1])
    return float(definition["max_prop_mm"])


def body_span(wb):
    return wb if wb <= 300 else 300 + 0.3 * (wb - 300)


def build_frame(key, d):
    wb = float(d["wheelbase_mm"])
    span = body_span(wb)
    motors_mm = arm_positions(d)
    shape = d.get("shape", "true_x")
    prop_mm = max_prop(d)
    ducted = "duct" in d
    accent = dim_to_reference(accent_of(d) or "#d6333a", TPU_REFERENCE)
    guard = paint.register(f"tpu_addon_{key}", paint.plastic(accent, 0.05))
    paintable.MATERIALS[("frame", guard)] = ("tpu", accent)
    thick = 6.0 if wb >= 600 else 5.0 if wb >= 200 else 3.0
    root_w = clamp(0.07 * wb, 8.0, 36.0)
    tip_w = root_w * 0.78
    pad_d = clamp(0.11 * wb, 12.0, 60.0)
    motor_y = ducts.motor_height(d) if ducted else -0.3
    top = motor_y - 0.3
    y0 = top - thick
    density = 3.6 if wb < 300 else 2.4 if wb < 600 else 1.6
    m = kit.Model("frame", key, density=density, gui=(52, 200, 0))
    if shape == "h_frame":
        for left in (True, False):
            front = motors_mm[0 if left else 1]
            rear = motors_mm[2 if left else 3]
            dx, dz = rear[0] - front[0], rear[2] - front[2]
            length = math.hypot(dx, dz)
            yaw = math.degrees(math.atan2(-dz, dx))
            m.bar(front[0], front[2], round(yaw, 3), length, root_w, y0, top, "carbon", name="rail", skip=("west",))
        rail_x = abs(motors_mm[1][0] + motors_mm[3][0]) / 2
        m.box(-rail_x, y0, -0.12 * span, rail_x, top, 0.12 * span, "carbon", name="cross_plate")
    else:
        for cx, _, cz in motors_mm:
            reach = math.hypot(cx, cz)
            yaw = math.degrees(math.atan2(-cz, cx))
            split = reach * 0.55
            start = root_w / 2 + 0.2
            m.bar(0, 0, round(yaw, 3), split - start, root_w, y0, top, "carbon", start=start, name="arm", skip=("west",))
            m.bar(0, 0, round(yaw, 3), reach - split, tip_w, y0, top, "carbon", start=split, name="arm", skip=("west",))
            if not ducted:
                length = pad_d * 0.8
                m.bar(0, 0, round(yaw, 3), length, tip_w + 1.2, y0 - 0.4, top + 0.4, guard, start=reach - pad_d / 2 - length,
                      name="arm_guard")
    for i, (cx, _, cz) in enumerate(motors_mm):
        sheet = m.sheet(f"pad{i}", cx - pad_d / 2, cz - pad_d / 2, cx + pad_d / 2, cz + pad_d / 2, frames.pad_top(accent))
        m.cylinder(cx, cz, y0 - 0.3, motor_y, pad_d, "carbon", name="motor_pad", sheet_up=sheet)
    hole = 15.25
    frames.carbon_plate(m, -0.1 * span, -0.29 * span, 0.1 * span, 0.27 * span, y0 + thick - 3.0, top + 0.6, "bottom_plate",
                        holes=[(sx * hole, sz * hole, 1.6) for sx in (-1, 1) for sz in (-1, 1)])
    battery = d.get("battery") or {}
    on_top = battery.get("on_top", wb >= 100)
    camera = d.get("camera") or {}
    cam_z = float(camera["pos"][2]) if "pos" in camera else -0.21 * wb
    if on_top:
        plate = float(battery.get("plate_mm", 0.13 * wb))
        plate_bottom = plate - 2.5
        for sx in (-1, 1):
            for sz in (-0.16, 0.02, 0.2):
                frames.standoff(m, sx * 0.08 * span, sz * span, 0.0, plate_bottom, 5.0, guard)
        frames.carbon_plate(m, -0.09 * span, -0.18 * span, 0.09 * span, 0.24 * span, plate_bottom, plate, "top_plate", text=None)
        m.box(-0.06 * span, plate, -0.1 * span, 0.06 * span, plate + 0.8, 0.16 * span, "rubber", name="battery_pad", skip=("down",))
        cage_h = max(12.0, plate_bottom)
        frames.camera_cage(m, 10.8, 2.5, 0.0, cage_h, cam_z - 8.0, cam_z + 22.0)
    if ducted:
        duct_top = ducts.top(motor_y + ducts.typical_seat(prop_mm), prop_mm)
        for cx, _, cz in motors_mm:
            ducts.ring(m, cx, cz, y0, duct_top, prop_mm, "tpu_black", lip_material=guard, lip_name="duct_rim")
    return m


def build_motor(key, d):
    stator_d, stator_h = stator_of(d)
    bell_color = accent_of(d) or "#b9bec7"
    base_d = stator_d * 0.9
    base_h = clamp(0.12 * stator_d, 1.2, 4.0)
    gap = clamp(0.08 * stator_d, 0.8, 2.5)
    seat = round(base_h + gap + stator_h + 4.0, 2)
    params = dict(base_d=base_d, base_h=base_h, base_color="#1f2023",
                  lug=(base_d * 0.66, clamp(0.2 * stator_d, 3.0, 8.0)) if stator_d >= 12 else None,
                  stator_d=stator_d, gap=gap, bell_d=stator_d + 5.0, bell_color=bell_color,
                  shaft_d=5.0 if stator_d >= 20 else 1.6 if stator_d >= 12 else 1.0,
                  shaft_len=clamp(0.4 * stator_d, 1.6, 12.0), spokes=6 if stator_d >= 30 else 5,
                  lug_w=clamp(0.2 * stator_d, 3.0, 8.0), vent="#c8733a", top_color="#232427")
    motors.MOTORS[key] = params
    layout.MOTOR_SEAT_MM[key] = seat
    paintable.MATERIALS[("motor", f"motor_bell_{key}")] = ("motor", bell_color)
    paintable.MATERIALS[("motor", f"motor_bell_plain_{key}")] = ("motor", bell_color)
    return motors.build_motor(key), seat


def prop_params(d):
    diameter = float(d["diameter_mm"])
    blades = int(d["blades"])
    radius = diameter / 2
    color = accent_of(d) or PROP_DEFAULT_COLORS.get(d.get("material", "polycarbonate"), "#e4e4ea")
    template = props.PROPS["p7_2b" if blades <= 2 else "p51_3b" if blades == 3 else "p31_4b"]
    ratio = radius / template["radius"]
    segments = [(r0 * ratio, r1 * ratio, chord * ratio * (3.0 / max(3, blades)) ** 0.3, pitch)
                for r0, r1, chord, pitch in template["segments"]]
    return dict(radius=radius, blades=blades, hub_d=clamp(0.1 * diameter, 5.0, 18.0), hub_h=clamp(0.055 * diameter, 4.0, 10.0),
                thick=clamp(0.015 * diameter, 0.8, 3.0), color=color,
                nut="nylock" if diameter >= 100 else "tmount" if diameter >= 60 else None,
                segments=segments, sweep=list(template["sweep"]))


def build_props(key, d):
    params = prop_params(d)
    props.PROPS[key] = params
    paintable.MATERIALS[("prop", f"prop_{key}")] = ("prop", params["color"])
    paintable.MATERIALS[("prop", f"prop_hub_{key}")] = ("prop", params["color"])
    cw = props.build_prop(key)
    ccw = props.build_prop(key, ccw=True)
    blur = props.build_blur(key, cw)
    damaged = [(props.build_prop(key, damage=damage), props.build_prop(key, ccw=True, damage=damage)) for damage in props.DAMAGE]
    return cw, ccw, blur, damaged


def battery_dims(d):
    pack = d.get("pack_mm")
    if isinstance(pack, list) and len(pack) == 3:
        return float(pack[1]), float(pack[2]), float(pack[0])
    density = LION_DENSITY if str(d.get("chemistry", "")).endswith("lion") else LIPO_DENSITY
    height = (float(d["mass_g"]) / density / 2.86) ** (1.0 / 3.0) * 10.0
    return 1.3 * height, height, 2.2 * height


def build_battery(key, d):
    w, h, l = battery_dims(d)
    cells = int(d["cells"])
    base = accent_of(d) or "#1c1d21"
    connector = str(d.get("connector", "")).lower()
    params = dict(w=round(w, 2), h=round(h, 2), l=round(l, 2), base=base, band="#f2f2f2" if cells == 1 else "#e8b623",
                  t1=f"{cells}S", t2=str(int(d["capacity_mah"])), text="#15161a",
                  connector="bt" if cells == 1 else "xt60" if "60" in connector or cells >= 5 else "xt30",
                  density=clamp(260.0 / max(w, h, l), 2.0, 8.0), strap=cells >= 5 or l >= 70)
    batteries.BATTERIES[key] = params
    paintable.MATERIALS[("battery", f"wrap_{key}")] = ("battery", base)
    return batteries.build_lipo(key)


def build_stack(key, d):
    mount = str(d.get("mount", "propwash:s30"))
    size = float(d.get("board_size_mm") or (26.0 if mount.endswith("whoop_aio") else 27.0 if mount.endswith("s20") else
                                              36.0 if mount.endswith("s30") else 30.0))
    half = size / 2
    hole = half - 2.75
    aio = bool(d.get("all_in_one", False))
    m = kit.Model("stack", key, density=5.5 if size >= 30 else 8.0, gui=(48, 225, 0))
    holes = [(sx * hole, sz * hole, 1.5) for sx in (-1, 1) for sz in (-1, 1)]
    for sx in (-1, 1):
        for sz in (-1, 1):
            stacks.grommet(m, sx * hole, sz * hole, 0.0, 2.0, min(4.2, size * 0.12))
    if aio:
        sheet = m.sheet("fc", -half, -half, half, half, stacks.pcb_sheet(-half, -half, half, half, holes=holes, label="AIO",
                                                                          label_pos=(0.0, -half * 0.6)))
        m.box(-half, 2.0, -half, half, 3.6, half, "pcb", name="fc_pcb", sheet_up=sheet)
        m.box(-size * 0.11, 3.6, -size * 0.11, size * 0.11, 4.6, size * 0.11, "chip", name="mcu", rot=(0, 45, 0), origin=(0, 3.6, 0))
        for sx in (-1, 1):
            m.box(sx * size * 0.3 - 2.0, 3.6, size * 0.15, sx * size * 0.3 + 2.0, 4.5, size * 0.15 + 3.6, "chip", name="fet")
        if size >= 27:
            stacks.cap(m, 0.0, 6.0, half + 5.0, 6.0, 10.0)
        return m
    esc = m.sheet("esc", -half, -half, half, half, stacks.pcb_sheet(-half, -half, half, half, holes=holes, label="ESC",
                                                                     label_pos=(0.0, 0.0)))
    m.box(-half, 3.0, -half, half, 4.6, half, "pcb", name="esc_pcb", sheet_up=esc)
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box(sx * half * 0.58 - 2.4, 4.6, sz * half * 0.45 - 2.0, sx * half * 0.58 + 2.4, 5.7, sz * half * 0.45 + 2.0, "chip", name="fet")
            m.box(sx * hole - 2.2, 4.6, sz * hole - 2.2, sx * hole + 2.2, 11.0, sz * hole + 2.2, "black", name="nylon_standoff",
                  skip=("up", "down"))
    fc = m.sheet("fc", -half, -half, half, half, stacks.pcb_sheet(-half, -half, half, half, holes=holes, label="FC",
                                                                   label_pos=(0.0, -half * 0.62)))
    m.box(-half, 11.0, -half, half, 12.6, half, "pcb", name="fc_pcb", sheet_up=fc)
    m.box(-4.0, 12.6, -4.0, 4.0, 13.6, 4.0, "chip", name="mcu", rot=(0, 45, 0), origin=(0, 12.6, 0))
    cells = int(d.get("cells", [3, 6])[1])
    stacks.cap(m, -7.0, 8.0, half + 9.0, 10.0 if cells <= 6 else 12.0, 16.0)
    stacks.cap(m, 7.0, 8.0, half + 9.0, 10.0 if cells <= 6 else 12.0, 16.0)
    stacks.lead(m, -2.8, 6.2, half - 1.0, half + 20.0, 3.0, "wire_red")
    stacks.lead(m, 2.8, 6.2, half - 1.0, half + 20.0, 3.0, "wire_black")
    stacks.xt_connector(m, half + 20.0, 2.2, 16.0, 8.0, 15.5)
    return m


def build_video(key, d):
    width = float(d.get("camera_width_mm", 19.0))
    digital = d.get("link", "analog") == "digital"
    m = kit.Model("video", key, density=6.0, gui=(20, 205, 0), gui_fill=15.0)
    bezel = "cam_bezel_dark" if digital else "cam_bezel_red"
    video.camera(m, width, width, width * 0.9, -width * 0.45, width * 0.72, width * 0.4, bezel=bezel)
    if digital:
        large = float(d.get("mass_g", 10.0)) >= 15.0 or int(d.get("vtx_power_mw", 0)) > 600
        w, h, l = (30.0, 10.5, 34.0) if large else (21.0, 6.0, 22.0)
        z0 = width * 0.45 + 3.0
        m.box(-w / 2, 2.0, z0, w / 2, 2.0 + h, z0 + l, "airunit" if large else "vtx_lite", name="air_unit")
        for i in range(5 if large else 4):
            x = -w / 2 + 2.5 + i * (w - 5.0) / (4 if large else 3)
            m.box(x - 0.8, 2.0 + h, z0 + 3.0, x + 0.8, 4.5 + h, z0 + l - 3.0, "heatsink", name="fin")
        for s in (-1, 1):
            video.antenna(m, s * w * 0.35, 2.0 + h, z0 + l - 2.0, (60, s * 28, 0), 26.0 if large else 20.0, 2.6, stalk="antenna_black",
                          tip="antenna_red", tip_len=6.0, tip_d=3.4)
    else:
        z0 = width * 0.45 + 2.0
        m.box(-9.0, -width * 0.4, z0, 9.0, -width * 0.4 + 5.0, z0 + 20.0, "vtx_wrap", name="vtx")
        video.antenna(m, 0.0, -width * 0.4 + 5.0, z0 + 17.0, (42, 0, 0), 24.0, 2.4, stalk="antenna_black", tip="antenna_red",
                      tip_len=6.0, tip_d=5.0)
    return m


def write_simple(out, model, icons, paint_shared=None):
    model.pack()
    display, pre = kit.compute_display(model)
    sprite, img = model.texture_map()["0"]
    out.texture(sprite, img)
    out.model(model, display)
    out.render_item(model)
    write_paint(out, model, display, paint_shared)
    if icons == "model":
        out.icon(model.key, kit.item_definition(model.model_id, pre))
    return display, pre


def write_paint(out, model, display, shared=None):
    variant = paintable.variant(model, shared)
    if variant is None:
        return None
    for key, (sprite, img) in variant.texture_map().items():
        if shared and key in shared:
            continue
        out.texture(sprite, img)
    out.model(variant, display)
    out.render_item(variant, paintable.default_tints(variant.groups))
    out.paint_groups[f"{model.category}/{model.key}"] = variant.groups
    return variant


def write_props(out, key, built, icons):
    cw, ccw, blur, damaged = built
    cw.pack()
    ccw.pack()
    blur.pack()
    props.attach_hub(blur, cw)
    blur.extra_textures = {"0": (blur.texture_id, blur.image), "1": (cw.texture_id, cw.image)}
    display, pre = kit.compute_display(cw, wall_extra=[pair[0] for pair in damaged])
    out.texture(cw.texture_id, cw.image)
    if np.array_equal(cw.image, ccw.image):
        ccw.extra_textures = {"0": (cw.texture_id, cw.image)}
    else:
        out.texture(ccw.texture_id, ccw.image)
    out.texture(blur.texture_id, blur.image)
    for model in (cw, ccw, blur):
        out.model(model, display)
        out.render_item(model)
    cw_paint = write_paint(out, cw, display)
    cw_texture = cw_paint.texture_map()["0"] if cw_paint is not None else None
    if cw_texture is not None and ccw.texture_map()["0"][0] == cw.texture_id:
        write_paint(out, ccw, display, shared={"0": cw_texture})
    else:
        write_paint(out, ccw, display)
    write_paint(out, blur, display, shared={"1": cw_texture} if cw_texture is not None else None)
    for pair in damaged:
        for model in pair:
            model.pack()
            out.texture(model.texture_id, model.image)
            out.model(model, display)
            out.render_item(model)
            write_paint(out, model, display)
    if icons == "model":
        extra = [("ccw", ccw.model_id), ("blur", blur.model_id), ("ccw_blur", blur.model_id)]
        stages = [(float(i + 1), f"{cw.model_id}_{damage}") for i, damage in enumerate(props.DAMAGE)]
        out.icon(key, kit.item_definition(cw.model_id, pre, extra, stages=(props.DAMAGE_PROPERTY, stages)))


def register_materials():
    frames.register_materials()
    stacks.register_materials()
    batteries.register_materials()
    video.register_materials()
    props.register_materials()


def clean(out, namespace):
    base = Path(out) / namespace
    for folder in ("models/part", "textures/item/part", "items/drone_render"):
        path = base / folder
        if path.exists():
            shutil.rmtree(path)


def generate(parts_dir, assets_root, namespace, icons="model", bbmodel_root=None, clean_first=True):
    kit.NAMESPACE = namespace
    register_materials()
    if clean_first:
        clean(assets_root, namespace)
    out = Output(assets_root, namespace, bbmodel_root)
    generated = []
    builders = {"frame": build_frame, "battery": build_battery, "stack": build_stack, "video": build_video}
    for category, key, definition in load_parts(parts_dir):
        model = None
        if category in builders:
            model = builders[category](key, definition)
        elif category == "motor":
            model, seat = build_motor(key, definition)
        elif category == "prop":
            built = build_props(key, definition)
            model = built[0]
        if model is not None and not fits_model_bounds(model):
            print(f"Warnung: {namespace}:{key} ({category}) ist größer als ein Minecraft-Modell erlaubt "
                  f"({MODEL_MIN:g}…{MODEL_MAX:g}); kein Modell erzeugt, Propwash zeichnet die Form aus der Definition")
            model = None
        if model is None:
            if icons != "none":
                out.icon(key, special_icon(category))
            generated.append((category, key, False))
            continue
        if category == "motor":
            out.motor_seats[key] = seat
        if category == "prop":
            write_props(out, key, built, icons)
        else:
            write_simple(out, model, icons)
        generated.append((category, key, True))
    problems = paintable.check_references(out.paint_groups)
    if problems:
        raise RuntimeError("Lack-Referenzen zu hell: " + "; ".join(problems))
    if out.paint_groups:
        kit.dump_json(Path(assets_root) / namespace / "drone_paint.json", paintable.summary_json(out.paint_groups))
    if out.motor_seats:
        kit.dump_json(Path(assets_root) / namespace / "drone_layout.json", {"frames": {}, "motor_seat": dict(sorted(out.motor_seats.items()))})
    return generated, out


def main(argv=None):
    parser = argparse.ArgumentParser(description="Erzeugt Drohnen-Modelle für Bauteile eines Propwash-Addons aus den Bauteil-JSONs.")
    parser.add_argument("--namespace", required=True, help="Namensraum der Teile, z. B. justmoreparts")
    parser.add_argument("--parts", required=True, help="Ordner mit <kategorie>/<pfad>.json (data/<ns>/propwash_parts)")
    parser.add_argument("--out", required=True, help="Ziel-Ordner assets (darunter <ns>/models, textures, items)")
    parser.add_argument("--icons", choices=("model", "special", "none"), default="model",
                        help="Inventar-Symbole: eigenes Modell, Sonder-Renderer propwash:part oder keine")
    parser.add_argument("--bbmodel", help="optional: Ordner für Blockbench-Quellen")
    parser.add_argument("--keep", action="store_true", help="vorhandene Modelle nicht vorher löschen")
    args = parser.parse_args(argv)
    icons = args.icons
    generated, out = generate(args.parts, args.out, args.namespace, "model" if icons == "model" else "none", args.bbmodel,
                              not args.keep)
    if icons == "special":
        for category, key, _ in generated:
            out.icon(key, special_icon(category))
    models = sum(1 for _, _, has_model in generated if has_model)
    print(f"{args.namespace}: {models} Bauteil-Modelle, {len(generated) - models} ohne Modell (prozedural), "
          f"{len(out.paint_groups)} Lack-Varianten, {len(out.written)} Dateien")


if __name__ == "__main__":
    main()
