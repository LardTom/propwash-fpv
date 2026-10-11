import copy

import numpy as np

import kit
from batteries import BATTERIES
from motors import MOTORS
from props import PROPS

SUFFIX = "_paint"

CHANNELS = {
    "frame": 200,
    "tpu": 242,
    "motor": 208,
    "prop": 224,
    "stack": 200,
    "battery": 200,
    "camera": 232,
    "antenna": 230,
    "accessory": 200,
}

SLOTS = ["frame", "tpu", "motors", "prop_fl", "prop_fr", "prop_rl", "prop_rr", "stack", "battery", "camera", "antenna",
         "accessories"]

SLOT_CHANNEL = {
    "frame": "frame", "tpu": "tpu", "motors": "motor", "prop_fl": "prop", "prop_fr": "prop", "prop_rl": "prop",
    "prop_rr": "prop", "stack": "stack", "battery": "battery", "camera": "camera", "antenna": "antenna",
    "accessories": "accessory",
}

PROP_SLOTS = ["prop_fl", "prop_fr", "prop_rl", "prop_rr"]

MATERIALS = {
    ("frame", "carbon"): ("frame", "#1b1d20"),
    ("frame", "black_matte"): ("frame", "#232428"),
    ("frame", "tpu_red"): ("tpu", "#d6333a"),
    ("frame", "tpu_blue"): ("tpu", "#2f7de0"),
    ("frame", "tpu_orange"): ("tpu", "#f2702a"),
    ("frame", "tpu_black"): ("tpu", "#262628"),
    ("frame", "tpu_teal"): ("tpu", "#1fa39a"),
    ("frame", "alu_red"): ("tpu", "#d1352b"),
    ("frame", "alu_blue"): ("tpu", "#2e6fd8"),
    ("frame", "alu_teal"): ("tpu", "#19a99c"),
    ("frame", "alu_orange"): ("tpu", "#e98a1f"),
    ("accessory", "tpu_black"): ("tpu", "#262628"),
    ("accessory", "tpu_red"): ("tpu", "#d6333a"),
    ("accessory", "acc_led_channel"): ("accessory", "#b9c0c8"),
    ("accessory", "acc_heatsink_base"): ("accessory", "#3f6f8c"),
    ("accessory", "acc_heavy_body"): ("accessory", "#3b3e44"),
    ("accessory", "acc_heavy_top"): ("accessory", "#3b3e44"),
    ("accessory", "acc_gripper_body"): ("accessory", "#2a5db0"),
    ("accessory", "acc_fb_body"): ("accessory", "#2d2a3a"),
    ("accessory", "acc_fb_top"): ("accessory", "#2d2a3a"),
    ("battery", "cell_wrap"): ("battery", "#1a8f8b"),
    ("video", "cam_body"): ("camera", "#1d1e21"),
    ("video", "cam_bezel_orange"): ("camera", "#e8761e"),
    ("video", "cam_bezel_red"): ("camera", "#d6352c"),
    ("video", "cam_bezel_grey"): ("camera", "#8d949c"),
    ("video", "cam_bezel_dark"): ("camera", "#3a3e44"),
    ("video", "antenna_red"): ("antenna", "#c0392b"),
    ("video", "antenna_white"): ("antenna", "#e6e6e6"),
}

for _key, _p in BATTERIES.items():
    MATERIALS[("battery", f"wrap_{_key}")] = ("battery", _p["base"])
    MATERIALS[("battery", f"wrap_{_key}_under")] = ("battery", _p["base"])
for _key, _p in MOTORS.items():
    MATERIALS[("motor", f"motor_bell_{_key}")] = ("motor", _p["bell_color"])
    MATERIALS[("motor", f"motor_bell_plain_{_key}")] = ("motor", _p["bell_color"])
for _key, _p in PROPS.items():
    MATERIALS[("prop", f"prop_{_key}")] = ("prop", _p["color"])
    MATERIALS[("prop", f"prop_hub_{_key}")] = ("prop", _p["color"])

STACK_SHEETS = ("fc", "pcb", "esc")


def sheet_rule(model, sheet):
    if model.category == "stack" and sheet.name in STACK_SHEETS:
        return "stack", "#141619"
    prop_key = model.key.split("_blur")[0].split("_worn")[0].split("_damaged")[0].split("_broken")[0].split("_ccw")[0]
    if model.category == "prop" and sheet.name in ("hub_top", "disc") and prop_key in PROPS:
        return "prop", PROPS[prop_key]["color"]
    if model.category == "motor" and sheet.name == "bell_top" and model.key in MOTORS:
        p = MOTORS[model.key]
        return "motor", p.get("top_color", p["bell_color"])
    return None


def rule(model, face):
    if face.sheet is not None:
        by_sheet = sheet_rule(model, face.sheet)
        if by_sheet is not None:
            return by_sheet
    if not isinstance(face.material, str):
        return None
    return MATERIALS.get((model.category, face.material))


def hex_rgb(value):
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def tint(base_hex, channel):
    reference = CHANNELS[channel]
    out = 0
    for c in hex_rgb(base_hex):
        out = (out << 8) | min(255, (c * 255 + reference // 2) // reference)
    return out


def luminance(rgb):
    return 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]


def to_grey(region, base_hex, channel):
    reference = CHANNELS[channel]
    base = np.array(hex_rgb(base_hex), dtype=np.float64)
    lb = max(1.0, float(luminance(base)))
    value = reference * luminance(region[..., :3].astype(np.float64)) / lb
    knee = max(float(reference), 229.5)
    if knee < 255.0:
        span = 255.0 - knee
        over = value > knee
        value[over] = knee + span * (1.0 - np.exp(-(value[over] - knee) / span))
    grey = np.clip(np.round(value), 0, 255)
    region[..., 0] = grey
    region[..., 1] = grey
    region[..., 2] = grey


def pixel_rect(uv, size):
    x0 = int(round(min(uv[0], uv[2]) * size / 16.0))
    y0 = int(round(min(uv[1], uv[3]) * size / 16.0))
    x1 = int(round(max(uv[0], uv[2]) * size / 16.0))
    y1 = int(round(max(uv[1], uv[3]) * size / 16.0))
    return x0, y0, x1, y1


def variant(model, shared=None):
    shared = shared or {}
    textures = model.texture_map()
    images = {k: img.copy() for k, (_, img) in textures.items() if k not in shared}
    groups = []
    index = {}
    done = set()
    elements = []
    for el in model.elements:
        faces = {}
        for d, fs in el.faces.items():
            nf = copy.copy(fs)
            nf.tint = None
            found = rule(model, fs)
            if found is not None:
                if found not in index:
                    index[found] = len(groups)
                    groups.append(found)
                nf.tint = index[found]
                if fs.texture in images:
                    img = images[fs.texture]
                    if fs.sheet is not None and fs.sheet.rect is not None and fs.texture == fs.sheet.texture:
                        sx, sy, sw, sh = fs.sheet.rect
                        rect = (sx, sy, sx + sw, sy + sh)
                    else:
                        rect = pixel_rect(fs.uv, img.shape[0])
                    if (fs.texture, rect) not in done:
                        done.add((fs.texture, rect))
                        x0, y0, x1, y1 = rect
                        region = img[y0:y1, x0:x1].astype(np.float64)
                        to_grey(region, found[1], found[0])
                        img[y0:y1, x0:x1] = region.astype(np.uint8)
            faces[d] = nf
        elements.append(kit.Element(el.name, el.frm, el.to, faces, el.rotation))
    if not groups:
        return None
    paint = kit.Model(model.category, model.key + SUFFIX, tex=model.tex, density=model.density, gui=model.gui,
                      gui_fill=model.gui_fill, translucent=model.translucent, seed=model.seed)
    paint.elements = elements
    extra = {}
    for k, (sprite, img) in textures.items():
        if k in shared:
            extra[k] = shared[k]
        elif k == "0":
            extra[k] = (paint.texture_id, images[k])
        else:
            extra[k] = (sprite + SUFFIX, images[k])
    paint.extra_textures = extra
    paint.image = extra["0"][1]
    paint.groups = groups
    return paint


def default_tints(groups):
    return [tint(base, channel) for channel, base in groups]


def group_json(groups):
    return [{"channel": channel, "base": base} for channel, base in groups]


class IconTints:
    def __init__(self):
        self.defaults = [0xFFFFFF] * len(SLOTS)
        self.claimed = {}
        self.extra = {}

    def index(self, slot, channel, base):
        key = (slot, base)
        if key in self.claimed:
            return self.claimed[key]
        primary = SLOTS.index(slot)
        if primary not in self.claimed.values():
            self.claimed[key] = primary
            self.defaults[primary] = tint(base, channel)
            return primary
        extra = len(self.defaults)
        self.defaults.append(tint(base, channel))
        self.claimed[key] = extra
        return extra


def slot_for(channel, prop_position):
    if channel == "prop":
        return PROP_SLOTS[prop_position]
    for slot, ch in SLOT_CHANNEL.items():
        if ch == channel:
            return slot
    raise KeyError(channel)


def check_references(groups_by_model):
    problems = []
    for name, groups in groups_by_model.items():
        channels = {g[0] for g in groups}
        if len(channels) < 2:
            continue
        for channel, base in groups:
            if max(hex_rgb(base)) > CHANNELS[channel]:
                problems.append(f"{name}: base {base} of channel {channel} exceeds reference {CHANNELS[channel]}")
    return problems


def summary_json(groups_by_model):
    return {
        "channels": dict(CHANNELS),
        "slots": list(SLOTS),
        "models": {name: group_json(groups) for name, groups in sorted(groups_by_model.items())},
    }
