import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))

import kit
import render

BG = (46, 49, 56, 255)
CELL = 220


def use_assets(assets_root):
    root = Path(assets_root)
    builtin = render.ASSETS.parent

    def texture_file(ns, path):
        own = root / ns / "textures" / f"{path}.png"
        return own if own.exists() else builtin / ns / "textures" / f"{path}.png"

    def texture(sprite_id):
        if isinstance(sprite_id, dict):
            sprite_id = sprite_id["sprite"]
        ns, path = sprite_id.split(":", 1)
        file = texture_file(ns, path)
        if file not in render._texture_cache:
            render._texture_cache[file] = np.asarray(Image.open(file).convert("RGBA"), dtype=np.float64)
        return render._texture_cache[file]

    def model_path(model_id):
        ns, path = model_id.split(":", 1)
        own = root / ns / "models" / f"{path}.json"
        return own if own.exists() else builtin / ns / "models" / f"{path}.json"

    render.texture = texture
    render.model_path = model_path


def extent(quads):
    pts = np.concatenate([q[0] for q in quads])
    mm = (pts - kit.ANCHOR) * kit.UNIT_MM
    return mm.min(axis=0), mm.max(axis=0)


def cell(model_id, label, rotation=(30, 225, 0)):
    quads, translucent, _ = render.load_model(model_id)
    canvas = render.Canvas(CELL, CELL, BG)
    lo, hi = extent(quads)
    size = float(np.linalg.norm(hi - lo))
    scale = (CELL * 0.8) / max(size, 1e-3)
    tf = render.ViewTransform(rotation, scale, (CELL / 2, CELL / 2 + 6), pivot=(lo + hi) / 2)
    canvas.draw(quads, tf, translucent)
    canvas.flush()
    img = canvas.image()
    ImageDraw.Draw(img).text((6, CELL - 14), label, fill=(230, 230, 230, 255))
    return img


def sheet(assets_root, namespace, out_file):
    use_assets(assets_root)
    base = Path(assets_root) / namespace / "items" / "drone_render"
    cells = []
    for category in ("frame", "stack", "motor", "prop", "battery", "video"):
        folder = base / category
        if not folder.is_dir():
            continue
        for item in sorted(folder.glob("*.json")):
            key = item.stem
            if key.endswith("_paint") or any(key.endswith(s) for s in ("_worn", "_damaged", "_broken", "_worn_ccw",
                                                                        "_damaged_ccw", "_broken_ccw", "_ccw")):
                continue
            model_id = json.loads(item.read_text())["model"]["model"]
            cells.append(cell(model_id, f"{category}/{key}"))
    if not cells:
        return None
    columns = min(4, len(cells))
    rows = (len(cells) + columns - 1) // columns
    out = Image.new("RGBA", (columns * CELL, rows * CELL), BG)
    for i, img in enumerate(cells):
        out.paste(img, ((i % columns) * CELL, (i // columns) * CELL))
    Path(out_file).parent.mkdir(parents=True, exist_ok=True)
    out.save(out_file)
    return out_file


if __name__ == "__main__":
    print(sheet(sys.argv[1], sys.argv[2], sys.argv[3]))
