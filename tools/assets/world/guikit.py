"""Shared drawing helpers and palette for the dark Propwash GUI textures (workbench, charger, drone battery)."""

from PIL import Image, ImageDraw

from assetlib import hex_rgba, load_palette

_PAL = load_palette()

GUI = {
    "outline": hex_rgba("0b0b0b"),
    "panel": hex_rgba("222222"),
    "panel_hi": hex_rgba("383838"),
    "panel_lo": hex_rgba("161616"),
    "title": hex_rgba("252525"),
    "well": hex_rgba("121212"),
    "well_hi": hex_rgba("383838"),
    "well_lo": hex_rgba("0b0b0b"),
    "slot": hex_rgba("1a1a1a"),
    "slot_ring": hex_rgba("4c4c4c"),
    "grid": hex_rgba("1c1c1c"),
    "grid_major": hex_rgba("262626"),
    "blueprint": hex_rgba("121212"),
    "arm": hex_rgba("2e2e2e"),
    "arm_edge": hex_rgba("454545"),
    "body": hex_rgba("2b2b2b"),
    "body_edge": hex_rgba("5a5a5a"),
    "disc": hex_rgba("1a1a1a"),
    "disc_ring": hex_rgba("4c4c4c"),
    "amber": _PAL["A"],
    "amber_dim": hex_rgba("6b5210"),
    "arrow": hex_rgba("353535"),
    "preview_top": hex_rgba("252525"),
    "preview_bottom": hex_rgba("0f0f0f"),
    "preview_grid": hex_rgba("262626"),
    "bay": hex_rgba("1f1f1f"),
    "bay_edge": hex_rgba("353535"),
    "channel": hex_rgba("0d0d0d"),
    "channel_edge": hex_rgba("4c4c4c"),
    "contact": hex_rgba("8c8c8c"),
}


def mix(c1, c2, t):
    return tuple(round(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))


def rect(draw, box, fill):
    x0, y0, x1, y1 = box
    if x1 <= x0 or y1 <= y0:
        return
    draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=fill)


def inset(draw, box, fill):
    x0, y0, x1, y1 = box
    rect(draw, box, fill)
    rect(draw, (x0, y0, x1 - 1, y0 + 1), GUI["well_lo"])
    rect(draw, (x0, y0, x0 + 1, y1 - 1), GUI["well_lo"])
    rect(draw, (x0 + 1, y1 - 1, x1, y1), GUI["well_hi"])
    rect(draw, (x1 - 1, y0 + 1, x1, y1), GUI["well_hi"])


def slot_well(draw, sx, sy, ring=None):
    if ring is not None:
        rect(draw, (sx - 2, sy - 2, sx + 18, sy + 18), ring)
    inset(draw, (sx - 1, sy - 1, sx + 17, sy + 17), GUI["slot"])


def panel(draw, width, height):
    rect(draw, (1, 0, width - 1, height), GUI["outline"])
    rect(draw, (0, 1, width, height - 1), GUI["outline"])
    rect(draw, (1, 1, width - 1, height - 1), GUI["panel"])
    rect(draw, (1, 1, width - 2, 2), GUI["panel_hi"])
    rect(draw, (1, 1, 2, height - 2), GUI["panel_hi"])
    rect(draw, (2, height - 2, width - 1, height - 1), GUI["panel_lo"])
    rect(draw, (width - 2, 2, width - 1, height - 1), GUI["panel_lo"])
    rect(draw, (2, 2, width - 2, 15), GUI["title"])
    rect(draw, (2, 15, width - 2, 16), GUI["panel_lo"])
    rect(draw, (3, 4, 5, 13), GUI["amber"])


def preview_field(img, box, spokes=7, spacing=22, horizon=52, rows=(0, 3, 8, 15, 24, 36)):
    draw = ImageDraw.Draw(img)
    px0, py0, px1, py1 = box
    inset(draw, box, GUI["well"])
    for y in range(py0 + 1, py1 - 1):
        t = (y - py0) / (py1 - py0)
        c = mix(GUI["preview_top"], GUI["preview_bottom"], t)
        rect(draw, (px0 + 1, y, px1 - 1, y + 1), c)
    line = GUI["preview_grid"]
    top = py0 + horizon
    vx = (px0 + px1) // 2
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    layer_draw = ImageDraw.Draw(layer)
    for i in range(-spokes, spokes + 1):
        x_bottom = vx + i * spacing
        x_top = vx + (x_bottom - vx) * 0.25
        layer_draw.line((round(x_top), top, round(x_bottom), py1 - 2), fill=line, width=1)
    clip = (px0 + 1, top, px1 - 1, py1 - 1)
    img.alpha_composite(layer.crop(clip), (clip[0], clip[1]))
    for offset in rows:
        y = top + offset
        if y < py1 - 1:
            rect(draw, (px0 + 1, y, px1 - 1, y + 1), line)
