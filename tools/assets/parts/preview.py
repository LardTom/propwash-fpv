import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kit
import layout
import render

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "docs/asset-sheets/parts_preview.png"
BG = (46, 49, 56, 255)
CELL = 210
ICON_PX = 4

ROWS = [
    ("frame", layout.FRAME_KEYS),
    ("stack", layout.STACK_KEYS),
    ("motor", layout.MOTOR_KEYS),
    ("prop", layout.PROP_KEYS + [k + "_blur" for k in layout.PROP_KEYS[2:]]),
    ("battery", layout.BATTERY_KEYS),
    ("video", layout.VIDEO_KEYS),
    ("accessory", layout.ACCESSORY_KEYS + list(layout.ACCESSORY_BASES.values())),
]

ACCESSORY_DEMOS = [
    ("freestyle_5", {"top": "action_cam_heavy", "bottom": "led_strip"}),
    ("cinewhoop_3", {"top": "action_cam_light", "bottom": "led_strip"}),
    ("race_5", {"top": "action_cam_flashback"}),
    ("long_range_7", {"top": "action_cam_heavy", "bottom": "led_strip"}),
    ("whoop_65", {"bottom": "led_strip"}),
    ("freestyle_5", {"bottom": "gripper"}),
]
ACCESSORY_WIDTH = {"led_strip": 32.0}
HANGING = {"gripper"}


def model_extent(quads):
    pts = np.concatenate([q[0] for q in quads])
    mm = (pts - kit.ANCHOR) * kit.UNIT_MM
    return mm.min(axis=0), mm.max(axis=0)


def pre_scale_for(item_key):
    path = render.ASSETS / f"items/{item_key}.json"
    if not path.exists():
        return 1.0
    data = json.loads(path.read_text())
    fb = data["model"]
    if fb.get("type") == "minecraft:select" and fb.get("property") == "minecraft:display_context":
        fb = fb["fallback"]
    if fb.get("type") == "minecraft:condition":
        fb = fb["on_false"]
    t = fb.get("transformation")
    return t["scale"][0] if t else 1.0


def render_view(canvas, quads, translucent, rotation, scale, center_px, pivot):
    tf = render.ViewTransform(rotation, scale, center_px, pivot=pivot)
    canvas.draw(quads, tf, translucent)


def cell_for_model(model_id, item_key, row_scale, rotation=(30, 225, 0)):
    quads, translucent, display = render.load_model(model_id)
    canvas = render.Canvas(CELL, CELL, BG)
    lo, hi = model_extent(quads)
    pivot = (lo + hi) / 2
    render_view(canvas, quads, translucent, rotation, row_scale, (CELL / 2, CELL / 2 + 8), pivot)
    canvas.flush()
    img = canvas.image()
    icon = render.Canvas(16 * ICON_PX, 16 * ICON_PX, (139, 139, 139, 255))
    pre = pre_scale_for(item_key)
    gt = render.GuiTransform(display, pre, ICON_PX, (8 * ICON_PX, 8 * ICON_PX))
    icon.draw(quads, gt, translucent)
    icon.flush()
    icon_img = icon.image()
    frame = Image.new("RGBA", (16 * ICON_PX + 4, 16 * ICON_PX + 4), (55, 55, 55, 255))
    frame.paste(icon_img, (2, 2))
    img.paste(frame, (4, 4))
    d = ImageDraw.Draw(img)
    d.text((6, CELL - 14), model_id.split("/")[-1], fill=(230, 230, 230, 255))
    return img


def row_scale(category, keys):
    extents = []
    for key in keys:
        quads, _, _ = render.load_model(f"propwash:part/{category}/{key}")
        lo, hi = model_extent(quads)
        extents.append(np.linalg.norm(hi - lo))
    return (CELL * 0.82) / max(extents)


def model_exists(path):
    return render.model_path(f"propwash:part/{path}").exists()


def model_top(path):
    quads, _, _ = render.load_model(f"propwash:part/{path}")
    return float((np.concatenate([q[0] for q in quads])[:, 1].max() - kit.ANCHOR) * kit.UNIT_MM)


def battery_part(frame_key, battery_key):
    return layout.battery_placement(frame_key, battery_key, model_exists, model_top)


def assembly(frame_key, blur=False, size=330, rotation=(32, 215, 0), extras=None, label=None):
    f = layout.FRAMES[frame_key]
    stack, motor, prop, vid, bat = f["preset"]
    items = [(f"frame/{frame_key}", [0, 0, 0], 0.0), (f"stack/{stack}", f["stack"], 0.0),
             (*battery_part(frame_key, bat), 0.0), (f"video/{vid}", f["video"], f["camera_tilt_default"])]
    for mount, key in (extras or {}).items():
        anchor = f["accessories"][mount]
        base = layout.ACCESSORY_BASES.get(mount)
        if base:
            items.append((f"accessory/{base}", anchor["position"], 0.0))
        stretch = anchor.get("length", 0.0) / ACCESSORY_WIDTH[key] if key in ACCESSORY_WIDTH and anchor.get("length") else 1.0
        tilt = 0.0 if key in HANGING else anchor["tilt"]
        items.append((f"accessory/{key}", anchor["position"], tilt, stretch))
    for pos_mm, spin in zip(layout.motor_positions(frame_key), layout.MOTOR_SPIN):
        items.append((f"motor/{motor}", pos_mm, 0.0))
        pk = prop + ("_blur" if blur else "_ccw" if spin == "ccw" else "")
        items.append((f"prop/{pk}", [pos_mm[0], pos_mm[1] + f["prop_height"], pos_mm[2]], 0.0))
    loaded = []
    all_pts = []
    for entry in items:
        path, offset, tilt = entry[:3]
        stretch = entry[3] if len(entry) > 3 else 1.0
        quads, translucent, _ = render.load_model(f"propwash:part/{path}")
        rot = kit.element_matrix(tilt, 0, 0) @ np.diag([stretch, 1.0, 1.0])
        off = np.array(offset, dtype=float)
        loaded.append((quads, translucent, rot, off))
        pts = (np.concatenate([q[0] for q in quads]) - kit.ANCHOR) * kit.UNIT_MM
        all_pts.append(pts @ rot.T + off)
    pts = np.concatenate(all_pts)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    pivot = (lo + hi) / 2
    scale = size * 0.78 / np.linalg.norm(hi - lo)
    canvas = render.Canvas(size, size, BG)
    for quads, translucent, rot, off in loaded:
        tf = render.ViewTransform(rotation, scale, (size / 2, size / 2), placement=(rot, off), pivot=pivot)
        canvas.draw(quads, tf, translucent)
    canvas.flush()
    img = canvas.image()
    d = ImageDraw.Draw(img)
    d.text((6, size - 14), label or (frame_key + (" (blur)" if blur else "")), fill=(230, 230, 230, 255))
    return img


def drone_icon(size_px=8):
    quads, translucent, display = render.load_model("propwash:item/drone")
    pre = pre_scale_for("drone")
    canvas = render.Canvas(16 * size_px, 16 * size_px, (139, 139, 139, 255))
    canvas.draw(quads, render.GuiTransform(display, pre, size_px, (8 * size_px, 8 * size_px)), translucent)
    canvas.flush()
    return canvas.image()


def main():
    cols = max(len(k) for _, k in ROWS)
    width = cols * CELL
    height = len(ROWS) * CELL + 330 * 4 + 10
    sheet = Image.new("RGBA", (max(width, 330 * 4), height), BG)
    y = 0
    for category, keys in ROWS:
        base_keys = [k for k in keys if not k.endswith("_blur")]
        scale = row_scale(category, base_keys)
        for i, key in enumerate(keys):
            item_key = key[:-5] if key.endswith("_blur") else key
            img = cell_for_model(f"propwash:part/{category}/{key}", item_key, scale)
            sheet.paste(img, (i * CELL, y))
        y += CELL
    x = 0
    for i, fk in enumerate(layout.FRAME_KEYS):
        img = assembly(fk)
        sheet.paste(img, ((i % 4) * 330, y + (i // 4) * 330))
    img = assembly("freestyle_5", blur=True)
    sheet.paste(img, (1 * 330, y + 330))
    icon = drone_icon()
    sheet.paste(icon, (2 * 330 + 20, y + 330 + 20))
    ImageDraw.Draw(sheet).text((2 * 330 + 20, y + 330 + 26 + icon.height), "drone (item icon)", fill=(230, 230, 230, 255))
    for i, (fk, extras) in enumerate(ACCESSORY_DEMOS):
        img = assembly(fk, extras=extras, label=fk + " + " + ", ".join(extras.values()))
        sheet.paste(img, ((i % 4) * 330, y + 660 + (i // 4) * 330))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(OUT)


if __name__ == "__main__":
    main()
