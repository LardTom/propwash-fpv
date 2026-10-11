"""GUI textures for the battery charger and the drone service menu (docs/design/gameplay-loop-v2.md §8, §11.5; docs/ASSETS.md).

Both are 256 x 256 with the panel at (0, 0); slot positions are Slot#x/y (top left of the 16 x 16 item area).
"""

from PIL import Image, ImageDraw

from assetlib import ASSETS, hex_rgba, save_png, write_json
from guikit import GUI, inset, panel, preview_field, rect, slot_well

TEX_SIZE = 256

CHARGER_W, CHARGER_H = 176, 207
CHARGER_SLOTS = (17, 59, 101, 143)
CHARGER_SLOT_Y = 30
CHARGER_INVENTORY = (8, 125)
CHARGER_HOTBAR_Y = 183
CHARGER_LABEL = (8, 114)
CHARGER_REDSTONE = (7, 90, 169, 111)
CHARGER_REDSTONE_ICON = (10, 92, 26, 108)
CHARGER_REDSTONE_TEXT = (30, 92, 101)


def charger_bay(x):
    return x - 10, 19, x + 26, 88


def charger_channel(x):
    return x + 1, 51, x + 15, 75


def charger_fill(x):
    return x + 2, 52, x + 14, 74


CHARGER_DIVIDER_Y = 76
CHARGER_LAMP_Y = 22
CHARGER_PERCENT_Y = 78

SERVICE_W, SERVICE_H = 236, 244
SERVICE_PROPS = (7, 18, 121, 110)
SERVICE_PROP_SLOTS = {"front_left": (17, 33), "front_right": (95, 33), "rear_left": (17, 75), "rear_right": (95, 75)}
SERVICE_BODY = (52, 44, 76, 88)
SERVICE_SLOT = (131, 81)
SERVICE_PREVIEW = (125, 18, 229, 64)
SERVICE_INFO = (154, 68, 229, 110)
SERVICE_HINT_Y = 114
SERVICE_CAMERA = (7, 124, 229, 150)
SERVICE_CAMERA_GAUGE = (10, 126, 34, 148)
SERVICE_INVENTORY = (38, 164)
SERVICE_HOTBAR_Y = 222
SERVICE_LABEL = (38, 153)


def inventory(draw, origin, hotbar_y):
    ix, iy = origin
    for row in range(3):
        for column in range(9):
            slot_well(draw, ix + column * 18, iy + row * 18)
    for column in range(9):
        slot_well(draw, ix + column * 18, hotbar_y)


def inventory_slots(origin, hotbar_y):
    ix, iy = origin
    slots = {}
    for row in range(3):
        for column in range(9):
            slots[f"inventory_{row}_{column}"] = (ix + column * 18, iy + row * 18)
    for column in range(9):
        slots[f"hotbar_{column}"] = (ix + column * 18, hotbar_y)
    return slots


def charger_texture():
    img = Image.new("RGBA", (TEX_SIZE, TEX_SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    panel(draw, CHARGER_W, CHARGER_H)
    for x in CHARGER_SLOTS:
        bx0, by0, bx1, by1 = charger_bay(x)
        rect(draw, (bx0, by0, bx1, by1), GUI["bay_edge"])
        rect(draw, (bx0 + 1, by0 + 1, bx1 - 1, by1 - 1), GUI["bay"])
        rect(draw, (bx0 + 1, by0 + 1, bx1 - 1, by0 + 2), GUI["panel_hi"])
        rect(draw, (x + 3, CHARGER_LAMP_Y - 1, x + 13, CHARGER_LAMP_Y + 3), GUI["bay_edge"])
        rect(draw, (x + 4, CHARGER_LAMP_Y, x + 12, CHARGER_LAMP_Y + 2), GUI["channel"])
        slot_well(draw, x, CHARGER_SLOT_Y)
        rect(draw, (x + 5, 48, x + 11, 50), GUI["contact"])
        rect(draw, (x + 6, 47, x + 10, 48), GUI["channel_edge"])
        cx0, cy0, cx1, cy1 = charger_channel(x)
        rect(draw, (cx0, cy0, cx1, cy1), GUI["channel_edge"])
        rect(draw, (cx0 + 1, cy0 + 1, cx1 - 1, cy1 - 1), GUI["channel"])
        for frac in (0.25, 0.5, 0.75):
            ty = round(cy1 - 1 - (cy1 - cy0 - 2) * frac)
            rect(draw, (cx1 + 1, ty, cx1 + (4 if frac == 0.5 else 3), ty + 1), GUI["channel_edge"])
        rect(draw, (bx0 + 3, CHARGER_DIVIDER_Y, bx1 - 3, CHARGER_DIVIDER_Y + 1), GUI["bay_edge"])
    rx0, ry0, rx1, ry1 = CHARGER_REDSTONE
    rect(draw, (rx0, ry0, rx1, ry1), GUI["bay_edge"])
    rect(draw, (rx0 + 1, ry0 + 1, rx1 - 1, ry1 - 1), GUI["bay"])
    inventory(draw, CHARGER_INVENTORY, CHARGER_HOTBAR_Y)
    return img


def prop_centers():
    return {key: (x + 8, y + 8) for key, (x, y) in SERVICE_PROP_SLOTS.items()}


def service_texture():
    img = Image.new("RGBA", (TEX_SIZE, TEX_SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    panel(draw, SERVICE_W, SERVICE_H)
    bx0, by0, bx1, by1 = SERVICE_PROPS
    inset(draw, SERVICE_PROPS, GUI["blueprint"])
    for gx in range(bx0 + 8, bx1 - 1, 8):
        rect(draw, (gx, by0 + 1, gx + 1, by1 - 1), GUI["grid"])
    for gy in range(by0 + 8, by1 - 1, 8):
        rect(draw, (bx0 + 1, gy, bx1 - 1, gy + 1), GUI["grid"])
    body = SERVICE_BODY
    center = ((body[0] + body[2]) // 2, (body[1] + body[3]) // 2)
    centers = prop_centers()
    for cx, cy in centers.values():
        draw.ellipse((cx - 13, cy - 13, cx + 13, cy + 13), outline=GUI["disc_ring"])
    for cx, cy in centers.values():
        draw.line((center, (cx, cy)), fill=GUI["arm_edge"], width=7)
    for cx, cy in centers.values():
        draw.line((center, (cx, cy)), fill=GUI["arm"], width=5)
    rect(draw, body, GUI["body_edge"])
    rect(draw, (body[0] + 1, body[1] + 1, body[2] - 1, body[3] - 1), GUI["body"])
    rect(draw, (center[0] - 4, body[1] + 2, center[0] + 4, body[1] + 3), GUI["amber"])
    for key, (sx, sy) in SERVICE_PROP_SLOTS.items():
        slot_well(draw, sx, sy, GUI["slot_ring"])
        rect(draw, (sx - 4, sy + 18, sx + 20, sy + 22), GUI["well"])
    preview_field(img, SERVICE_PREVIEW, spokes=6, spacing=18, horizon=30, rows=(0, 3, 7, 12))
    sx, sy = SERVICE_SLOT
    rect(draw, (sx - 3, sy - 3, sx + 19, sy + 19), GUI["amber"])
    rect(draw, (sx - 2, sy - 2, sx + 18, sy + 18), GUI["outline"])
    slot_well(draw, sx, sy)
    inset(draw, SERVICE_INFO, GUI["well"])
    x0, y0, x1, _ = SERVICE_INFO
    rect(draw, (x0 + 1, y0 + 1, x1 - 1, y0 + 2), GUI["amber"])
    inset(draw, SERVICE_CAMERA, GUI["well"])
    cx0, cy0, cx1, _ = SERVICE_CAMERA
    rect(draw, (cx0 + 1, cy0 + 1, cx1 - 1, cy0 + 2), GUI["amber"])
    inset(draw, SERVICE_CAMERA_GAUGE, GUI["blueprint"])
    gx0, _, gx1, gy1 = SERVICE_CAMERA_GAUGE
    rect(draw, (gx0 + 2, gy1 - 4, gx1 - 2, gy1 - 3), GUI["grid"])
    inventory(draw, SERVICE_INVENTORY, SERVICE_HOTBAR_Y)
    return img


TOOLTIP_SIZE = 100
TOOLTIP_INSET = 8
TOOLTIP_FILL = hex_rgba("161616ff")
TOOLTIP_EDGE = hex_rgba("4c4c4c")
TOOLTIP_ACCENT = hex_rgba("6b5210")


def tooltip_background():
    img = Image.new("RGBA", (TOOLTIP_SIZE, TOOLTIP_SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    rect(draw, (TOOLTIP_INSET, TOOLTIP_INSET, TOOLTIP_SIZE - TOOLTIP_INSET, TOOLTIP_SIZE - TOOLTIP_INSET), TOOLTIP_FILL)
    return img


def tooltip_frame():
    img = Image.new("RGBA", (TOOLTIP_SIZE, TOOLTIP_SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    a, b = TOOLTIP_INSET, TOOLTIP_SIZE - TOOLTIP_INSET
    rect(draw, (a, a, b, a + 1), TOOLTIP_EDGE)
    rect(draw, (a, b - 1, b, b), TOOLTIP_EDGE)
    rect(draw, (a, a, a + 1, b), TOOLTIP_EDGE)
    rect(draw, (b - 1, a, b, b), TOOLTIP_EDGE)
    rect(draw, (a + 1, a, b - 1, a + 1), TOOLTIP_ACCENT)
    return img


def nine_slice(border, stretch):
    scaling = {"type": "nine_slice", "width": TOOLTIP_SIZE, "height": TOOLTIP_SIZE, "border": border}
    if stretch:
        scaling["stretch_inner"] = True
    return {"gui": {"scaling": scaling}}


def generate():
    sprites = ASSETS / "textures" / "gui" / "sprites" / "tooltip"
    save_png(tooltip_background(), sprites / "propwash_background.png")
    write_json(nine_slice(9, False), sprites / "propwash_background.png.mcmeta")
    save_png(tooltip_frame(), sprites / "propwash_frame.png")
    write_json(nine_slice(10, True), sprites / "propwash_frame.png.mcmeta")
    save_png(charger_texture(), ASSETS / "textures" / "gui" / "battery_charger.png")
    save_png(service_texture(), ASSETS / "textures" / "gui" / "drone_service.png")
