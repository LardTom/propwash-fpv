"""Preview sections for the gameplay-loop-v2 assets (remote, battery charger, charger and drone battery GUIs).

Used by preview.py; the code-drawn parts of the GUIs (bars, text, items) are mocked here so the textures can be judged
in context (docs/design/gameplay-loop-v2.md §8).
"""

from PIL import Image, ImageDraw, ImageFont

import charger
import menus
import views
from assetlib import ASSETS
from modelrender import load_model, m_rot, m_translate, mat_chain, model_quads, render, transform_quads

INVENTORY_GREY = (139, 139, 139, 255)
LABEL = (230, 232, 236, 255)
MUTED = (140, 148, 158, 255)
ACCENT = (255, 187, 0, 255)
GOOD = (85, 255, 85, 255)
WARN = (255, 255, 85, 255)
BAD = (255, 85, 85, 255)


def bar_colour(charge):
    return GOOD if charge > 0.5 else WARN if charge > 0.2 else BAD


def remote_views(size):
    full = model_quads(load_model("propwash:item/remote"))
    body = model_quads(load_model("propwash:item/remote_body"))
    shots = [(full, 0.0, 10.0, "Front"), (full, -35.0, 25.0, "schräg"), (full, 150.0, 20.0, "Rückseite"),
             (full, -90.0, 0.0, "Seite (Knüppel +Z)"), (body, -35.0, 25.0, "remote_body (ohne Knüppel)")]
    return [(render(q, size, yaw=yaw, pitch=pitch), label) for q, yaw, pitch, label in shots]


def gui_slot_icons():
    out = Image.new("RGBA", (16 * 8 + 16 * 2 + 16 + 40, 16 * 8 + 8), (0, 0, 0, 0))
    x = 0
    for factor in (1, 2, 8):
        icon = views.gui_render("propwash:item/remote", 16 * factor, INVENTORY_GREY)
        out.alpha_composite(icon, (x, 0))
        x += 16 * factor + 12
    return out


def charger_render(state, size, facing_rotation=0, yaw=205.0, pitch=28.0):
    quads = model_quads(load_model(f"propwash:block/battery_charger_{state}"))
    if facing_rotation:
        pivot = mat_chain(m_translate(8, 8, 8), m_rot("y", -facing_rotation), m_translate(-8, -8, -8))
        quads = transform_quads(quads, pivot)
    return render(quads, size, yaw=yaw, pitch=pitch, scale=size / 24.0)


def compass(img, text):
    d = ImageDraw.Draw(img)
    d.text((6, 4), text, fill=MUTED)
    return img


SYSTEM_FONT = "/System/Library/Fonts/Supplemental/Arial.ttf"


def _mock_font():
    try:
        return ImageFont.truetype(SYSTEM_FONT, 11)
    except OSError:
        return ImageFont.load_default(size=11)


MOCK_FONT = _mock_font()


def small_text(draw, xy, text, fill, anchor="la"):
    draw.text(xy, text, fill=fill, anchor=anchor, font=MOCK_FONT)


def charger_gui_mock(scale=2):
    tex = Image.open(ASSETS / "textures" / "gui" / "battery_charger.png").convert("RGBA").crop((0, 0, menus.CHARGER_W, menus.CHARGER_H))
    img = tex.resize((tex.width * scale, tex.height * scale), Image.NEAREST)
    d = ImageDraw.Draw(img)
    batteries = [("b1s_300_hv", 0.18), ("b4s_650", 0.74), ("b6s_1300", 1.0), (None, None)]
    for x, (key, charge) in zip(menus.CHARGER_SLOTS, batteries):
        if key:
            icon = views.gui_render(f"propwash:part/battery/{key}", 16 * scale)
            img.alpha_composite(icon, (x * scale, menus.CHARGER_SLOT_Y * scale))
            fx0, fy0, fx1, fy1 = menus.charger_fill(x)
            top = fy1 - round((fy1 - fy0) * charge)
            d.rectangle((fx0 * scale, top * scale, fx1 * scale - 1, fy1 * scale - 1), fill=bar_colour(charge))
            small_text(d, ((x + 8) * scale, menus.CHARGER_PERCENT_Y * scale), f"{round(charge * 100)}%", LABEL, "ma")
            lamp = (91, 200, 91, 255) if charge >= 1.0 else (255, 187, 0, 255)
            d.rectangle(((x + 4) * scale, menus.CHARGER_LAMP_Y * scale, (x + 12) * scale - 1, (menus.CHARGER_LAMP_Y + 2) * scale - 1), fill=lamp)
        else:
            empty = Image.open(ASSETS / "textures" / "gui" / "sprites" / "gui" / "slot" / "battery.png").convert("RGBA")
            img.alpha_composite(empty.resize((16 * scale, 16 * scale), Image.NEAREST), (x * scale, menus.CHARGER_SLOT_Y * scale))
    small_text(d, (8 * scale, 6 * scale), "Ladegerät", LABEL)
    tx, line1, line2 = menus.CHARGER_REDSTONE_TEXT
    small_text(d, (tx * scale, line1 * scale), "Redstone-Boost: aus", MUTED)
    small_text(d, (tx * scale, line2 * scale), "Redstone-Signal: schneller", MUTED)
    small_text(d, (menus.CHARGER_LABEL[0] * scale, menus.CHARGER_LABEL[1] * scale), "Inventar", LABEL)
    return img


def service_gui_mock(scale=2):
    tex = Image.open(ASSETS / "textures" / "gui" / "drone_service.png").convert("RGBA").crop((0, 0, menus.SERVICE_W, menus.SERVICE_H))
    img = tex.resize((tex.width * scale, tex.height * scale), Image.NEAREST)
    d = ImageDraw.Draw(img)
    px0, py0, px1, py1 = menus.SERVICE_PREVIEW
    drone = views.gui_render("propwash:item/drone", 40 * scale)
    img.alpha_composite(drone, (((px0 + px1) // 2 - 20) * scale, (py0 + 3) * scale))
    conditions = {"front_left": (0.0, "kaputt", (170, 0, 0, 255)), "front_right": (0.56, "56 %", (255, 255, 85, 255)),
                  "rear_left": (1.0, "100 %", GOOD), "rear_right": (0.83, "83 %", (255, 255, 85, 255))}
    for key, (sx, sy) in menus.SERVICE_PROP_SLOTS.items():
        img.alpha_composite(views.gui_render("propwash:part/prop/p51_3b", 16 * scale), (sx * scale, sy * scale))
        value, label, colour = conditions[key]
        d.rectangle(((sx - 3) * scale, (sy + 19) * scale, (sx + 19) * scale - 1, (sy + 21) * scale - 1), fill=(18, 18, 18, 255))
        if value > 0:
            d.rectangle(((sx - 3) * scale, (sy + 19) * scale, (sx - 3 + round(22 * value)) * scale - 1, (sy + 21) * scale - 1), fill=colour)
        small_text(d, ((sx + 1) * scale, (sy + 23) * scale), label, colour)
    small_text(d, (54 * scale, 21 * scale), "▲ Vorne", MUTED)
    sx, sy = menus.SERVICE_SLOT
    img.alpha_composite(views.gui_render("propwash:part/battery/b6s_1300", 16 * scale), (sx * scale, sy * scale))
    x0, y0, x1, y1 = menus.SERVICE_INFO
    small_text(d, ((x0 + 5) * scale, (y0 + 5) * scale), "6S 1300 mAh", LABEL)
    small_text(d, ((x0 + 5) * scale, (y0 + 16) * scale), "74 % · 23,4 V", MUTED)
    bar = x1 - x0 - 10
    d.rectangle(((x0 + 5) * scale, (y0 + 27) * scale, (x0 + 5 + bar) * scale - 1, (y0 + 30) * scale - 1), fill=(40, 44, 50, 255))
    d.rectangle(((x0 + 5) * scale, (y0 + 27) * scale, (x0 + 5 + round(bar * 0.74)) * scale - 1, (y0 + 30) * scale - 1), fill=GOOD)
    small_text(d, (8 * scale, 6 * scale), "Wartung", LABEL)
    small_text(d, (8 * scale, menus.SERVICE_HINT_Y * scale), "Prop auf einen Platz legen zum Tauschen", MUTED)
    cx0, cy0, cx1, cy1 = menus.SERVICE_CAMERA
    gx0, gy0, gx1, gy1 = menus.SERVICE_CAMERA_GAUGE
    pivot = ((gx0 + 4) * scale, (gy1 - 4) * scale)
    reach = (gx1 - gx0 - 8) * scale
    d.line((pivot, (pivot[0] + round(reach * 0.88), pivot[1] - round(reach * 0.47))), fill=(240, 180, 30, 255), width=scale)
    small_text(d, ((gx1 + 4) * scale, (cy0 + 4) * scale), "Kamerawinkel 28°", LABEL)
    small_text(d, ((gx1 + 4) * scale, (cy0 + 14) * scale), "Halter 0–50° · Props im Bild 8,4 %", MUTED)
    small_text(d, (menus.SERVICE_LABEL[0] * scale, menus.SERVICE_LABEL[1] * scale), "Inventar", LABEL)
    return img


def draw_sections(sheet, y, section, tile, scaled, F_SMALL):
    d = ImageDraw.Draw(sheet)

    y = section(sheet, y, "Funke propwash:remote — Modell (Knüppel mittig), GUI-Icon 1×/2×/8× auf Inventar-Grau")
    x = 20
    for img, label in remote_views(230):
        tile(sheet, img, x, y, label, bg=False)
        x += 240
    tile(sheet, gui_slot_icons(), x + 10, y + 40, "GUI 16 px · 32 px · 128 px", bg=False)
    y += 260

    y = section(sheet, y, "Funke in der Hand — erste Person (HUD-FOV 70°, Balken = 35 % Bildbreite) · dritte Person · Blick des Spielers")
    fp = views.first_person("propwash:item/remote", 640, 360)
    tile(sheet, fp, 20, y, "firstperson_righthand", bg=False)
    fpl = views.first_person("propwash:item/remote", 320, 180, left_hand=True, guides=False)
    tile(sheet, fpl, 20, y + 385, "firstperson_lefthand (gespiegelt)", bg=False)
    tile(sheet, views.third_person("propwash:item/remote", 180, one_handed=True), 380, y + 385,
         "einhändig: thirdperson_righthand", bg=False)
    tile(sheet, views.third_person("propwash:item/remote", 250), 680, y, "beidhändig (RemoteHoldPose)", bg=False)
    tile(sheet, views.third_person("propwash:item/remote", 250, yaw=250.0, pitch=10.0), 945, y, "beidhändig, Seite", bg=False)
    tile(sheet, views.third_person("propwash:item/remote", 250, yaw=0.0, pitch=60.0, zoom=2.6, with_body=False,
                                             focus_item=True), 1210, y,
         "Spielerblick (ohne Körper)", bg=False)
    tex = Image.open(ASSETS / "textures" / "item" / "remote.png").convert("RGBA")
    tile(sheet, scaled(tex, 4), 680, y + 290, "item/remote.png (64×64)")
    gimbal = Image.open(ASSETS / "textures" / "entity" / "remote" / "gimbal.png").convert("RGBA")
    tile(sheet, scaled(gimbal, 8), 960, y + 290, "entity/remote/gimbal.png")
    d.text((1100, y + 300), "Schaft: Zeilen 0–7\nKappe: Zeilen 8–15\n(Zeile 8 = Amber-Ring)\n\nDrehpunkte (Modellpixel):\nlinks (4, 6.5, 10.25)\nrechts (12, 6.5, 10.25)",
           font=F_SMALL, fill=MUTED)
    y += 590

    y = section(sheet, y, "Ladegerät propwash:battery_charger — Zustände idle · charging · done, Ausrichtungen, Texturen (32×32)")
    for i, state in enumerate(charger.STATES):
        tile(sheet, charger_render(state, 250), 20 + i * 260, y, state, bg=False)
    for i, (facing, rot) in enumerate(charger.FACINGS.items()):
        img = compass(charger_render("charging", 130, rot, yaw=200.0, pitch=45.0), f"facing={facing}")
        tile(sheet, img, 20 + i * 140, y + 280, None, bg=False)
    d.text((20, y + 420), "Kamera steht im Norden (−Z) über dem Block: bei facing=north zeigt die Front zur Kamera, Osten liegt links.", font=F_SMALL, fill=MUTED)
    for i, face in enumerate(charger.FACES):
        img = Image.open(ASSETS / "textures" / "block" / f"battery_charger_{face}.png").convert("RGBA")
        tile(sheet, scaled(img, 4), 820 + (i % 3) * 145, y + (i // 3) * 160, face)
    tile(sheet, views.gui_render("propwash:item/battery_charger", 64, INVENTORY_GREY, "propwash:block/battery_charger_charging"), 620, y + 290, "Item-Icon", bg=False)
    y += 450

    y = section(sheet, y, f"GUI Ladegerät ({menus.CHARGER_W}×{menus.CHARGER_H}) und Wartung der Drohne ({menus.SERVICE_W}×{menus.SERVICE_H}), 2× — Balken, Text, Items vom Code (hier nachgestellt)")
    cg = charger_gui_mock()
    tile(sheet, cg, 20, y, "textures/gui/battery_charger.png", bg=False)
    bg = service_gui_mock()
    tile(sheet, bg, 20 + cg.width + 30, y, "textures/gui/drone_service.png", bg=False)
    raw_x = 20 + cg.width + 30 + bg.width + 30
    for i, name in enumerate(("battery_charger", "drone_service")):
        raw = Image.open(ASSETS / "textures" / "gui" / f"{name}.png").convert("RGBA")
        tile(sheet, raw, raw_x, y + i * 280, f"{name}.png (256×256, roh)")
    y += cg.height + 40
    return y
