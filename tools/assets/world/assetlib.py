"""Shared helpers for the Propwash world-asset scripts (palette, pixel art, file output)."""

import json
import os
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
SRC = REPO / "assets-src" / "world"
RES = REPO / "common" / "src" / "main" / "resources"
ASSETS = RES / "assets" / "propwash"
DOCS = REPO / "docs"


def hex_rgba(text):
    text = text.strip().lstrip("#")
    if len(text) == 6:
        text += "ff"
    if len(text) != 8:
        raise ValueError("bad colour " + text)
    return tuple(int(text[i:i + 2], 16) for i in range(0, 8, 2))


def load_palette(path=SRC / "palette.txt"):
    palette = {}
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        symbol, colour = line.split()
        palette[symbol] = hex_rgba(colour)
    return palette


def load_pixel_sheets(path, palette=None):
    """Parse a pixel-art source file into {name: RGBA image}.

    Sections start with "== name"; optional "@X RRGGBB[AA]" lines add or
    override palette symbols for that section; all other non-empty lines are
    grid rows. Every row of a section must have the same width.
    """
    base = dict(palette or load_palette())
    sheets = {}
    name, rows, local = None, [], {}

    def flush():
        if name is None:
            return
        if not rows:
            raise ValueError(f"{path}: section {name} has no rows")
        width = len(rows[0])
        for index, row in enumerate(rows):
            if len(row) != width:
                raise ValueError(f"{path}: {name} row {index} has width {len(row)}, expected {width}")
        pal = dict(base)
        pal.update(local)
        img = Image.new("RGBA", (width, len(rows)))
        for y, row in enumerate(rows):
            for x, symbol in enumerate(row):
                if symbol not in pal:
                    raise ValueError(f"{path}: {name} row {y} col {x}: unknown symbol {symbol!r}")
                img.putpixel((x, y), pal[symbol])
        sheets[name] = img

    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        if line.startswith("#") or not line.strip():
            continue
        if line.startswith("== "):
            flush()
            name, rows, local = line[3:].strip(), [], {}
            continue
        if line.startswith("@"):
            symbol, colour = line[1:].split()
            local[symbol] = hex_rgba(colour)
            continue
        rows.append(line.strip())
    flush()
    return sheets


def ensure_parent(path):
    Path(path).parent.mkdir(parents=True, exist_ok=True)


def save_png(img, path):
    ensure_parent(path)
    img.save(path, optimize=True)


def write_json(obj, path):
    ensure_parent(path)
    text = json.dumps(obj, indent=2, ensure_ascii=False) + "\n"
    Path(path).write_text(text, encoding="utf-8")


def rel(path):
    return os.path.relpath(path, REPO)
