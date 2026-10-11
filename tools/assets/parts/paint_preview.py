import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kit
import layout
import paintable
import preview
import render

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "docs/asset-sheets/paint_preview.png"
BG = (46, 49, 56, 255)
SIZE = 300

DYES = {
    "white": "#f9fffe", "orange": "#f9801d", "magenta": "#c74ebd", "light_blue": "#3ab3da", "yellow": "#fed83d",
    "lime": "#80c71f", "pink": "#f38baa", "gray": "#474f52", "light_gray": "#9d9d97", "cyan": "#169c9c",
    "purple": "#8932b8", "blue": "#3c44aa", "brown": "#835432", "green": "#5e7c16", "red": "#b02e26", "black": "#1d1d21",
}
FINISHES = {"carbon": "#2a2c30", "gunmetal": "#4e535b", "aluminium": "#b8bec6", "gold": "#d8a93b", "copper": "#b8693a"}

SCHEMES = [
    ("standard", {}),
    ("carbon_classic", {"frame": FINISHES["carbon"], "tpu": DYES["black"], "motors": FINISHES["gunmetal"],
                        "prop_fl": DYES["gray"], "prop_fr": DYES["gray"], "prop_rl": DYES["gray"], "prop_rr": DYES["gray"],
                        "stack": FINISHES["carbon"], "battery": DYES["black"], "camera": DYES["black"], "antenna": DYES["gray"],
                        "accessories": FINISHES["gunmetal"]}),
    ("neon", {"frame": FINISHES["carbon"], "tpu": DYES["lime"], "motors": DYES["magenta"], "prop_fl": DYES["yellow"],
              "prop_fr": DYES["yellow"], "prop_rl": DYES["light_blue"], "prop_rr": DYES["light_blue"], "stack": FINISHES["carbon"],
              "battery": DYES["magenta"], "camera": DYES["lime"], "antenna": DYES["yellow"], "accessories": DYES["light_blue"]}),
    ("race_team", {"frame": DYES["white"], "tpu": DYES["red"], "motors": DYES["red"], "prop_fl": DYES["red"], "prop_fr": DYES["red"],
                   "prop_rl": DYES["white"], "prop_rr": DYES["white"], "stack": FINISHES["carbon"], "battery": DYES["white"],
                   "camera": DYES["red"], "antenna": DYES["white"], "accessories": DYES["red"]}),
    ("arctic", {"frame": FINISHES["aluminium"], "tpu": DYES["white"], "motors": FINISHES["aluminium"], "prop_fl": DYES["light_blue"],
                "prop_fr": DYES["light_blue"], "prop_rl": DYES["white"], "prop_rr": DYES["white"], "stack": FINISHES["carbon"],
                "battery": DYES["white"], "camera": DYES["white"], "antenna": DYES["light_blue"], "accessories": FINISHES["aluminium"]}),
]

PAINT = json.loads((render.ASSETS / "drone_paint.json").read_text())


def tints_for(name, scheme, prop_position):
    groups = PAINT["models"].get(name)
    if groups is None:
        return None, False
    out = []
    painted = False
    for g in groups:
        slot = paintable.slot_for(g["channel"], prop_position)
        color = scheme.get(slot)
        if color is None:
            out.append(paintable.tint(g["base"], g["channel"]))
        else:
            painted = True
            out.append(paintable.tint(color, g["channel"]))
    return out, painted


def assembly(frame_key, scheme, label, force_variant=False, extras=None):
    f = layout.FRAMES[frame_key]
    stack, motor, prop, vid, bat = f["preset"]
    items = [(f"frame/{frame_key}", [0, 0, 0], 0.0, 0), (f"stack/{stack}", f["stack"], 0.0, 0),
             (*preview.battery_part(frame_key, bat), 0.0, 0), (f"video/{vid}", f["video"], f["camera_tilt_default"], 0)]
    for mount, key in (extras or {}).items():
        anchor = f["accessories"][mount]
        base = layout.ACCESSORY_BASES.get(mount)
        if base:
            items.append((f"accessory/{base}", anchor["position"], 0.0, 0))
        items.append((f"accessory/{key}", anchor["position"], anchor["tilt"], 0))
    for i, (pos_mm, spin) in enumerate(zip(layout.motor_positions(frame_key), layout.MOTOR_SPIN)):
        items.append((f"motor/{motor}", pos_mm, 0.0, 0))
        pk = prop + ("_ccw" if spin == "ccw" else "")
        items.append((f"prop/{pk}", [pos_mm[0], pos_mm[1] + f["prop_height"], pos_mm[2]], 0.0, i))
    loaded = []
    all_pts = []
    for path, offset, tilt, position in items:
        tints, painted = tints_for(path, scheme, position)
        if tints is not None and (painted or force_variant):
            quads, translucent, _ = render.load_model(f"propwash:part/{path}{paintable.SUFFIX}", tints)
        else:
            quads, translucent, _ = render.load_model(f"propwash:part/{path}")
        rot = kit.element_matrix(tilt, 0, 0)
        off = np.array(offset, dtype=float)
        loaded.append((quads, translucent, rot, off))
        pts = (np.concatenate([q[0] for q in quads]) - kit.ANCHOR) * kit.UNIT_MM
        all_pts.append(pts @ rot.T + off)
    pts = np.concatenate(all_pts)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    pivot = (lo + hi) / 2
    scale = SIZE * 0.78 / np.linalg.norm(hi - lo)
    canvas = render.Canvas(SIZE, SIZE, BG)
    for quads, translucent, rot, off in loaded:
        tf = render.ViewTransform((32, 215, 0), scale, (SIZE / 2, SIZE / 2), placement=(rot, off), pivot=pivot)
        canvas.draw(quads, tf, translucent)
    canvas.flush()
    img = canvas.image()
    ImageDraw.Draw(img).text((6, SIZE - 14), label, fill=(230, 230, 230, 255))
    return img


def main():
    cells = [assembly("freestyle_5", {}, "freestyle_5 original"),
             assembly("freestyle_5", {}, "freestyle_5 paint defaults", force_variant=True)]
    for name, scheme in SCHEMES[1:]:
        cells.append(assembly("freestyle_5", scheme, "freestyle_5 " + name,
                              extras={"top": "action_cam_light", "bottom": "led_strip"}))
    for frame in ("whoop_65", "cinewhoop_3", "race_5", "long_range_7"):
        cells.append(assembly(frame, {}, frame + " paint defaults", force_variant=True))
        cells.append(assembly(frame, SCHEMES[2][1], frame + " neon"))
    cols = 4
    rows = (len(cells) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * SIZE, rows * SIZE), BG)
    for i, cell in enumerate(cells):
        sheet.paste(cell, ((i % cols) * SIZE, (i // cols) * SIZE))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
