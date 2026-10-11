"""Render docs/asset-sheets/world_preview.png from the generated world assets."""

from PIL import Image, ImageDraw, ImageFont

import generate as gen
import preview_gameplay
from assetlib import ASSETS, DOCS, RES, save_png
from modelrender import Quad, box_uv_quads, load_model, model_quads, render

BG = (24, 26, 30, 255)
PANEL = (34, 37, 43, 255)
TEXT = (220, 224, 230, 255)
MUTED = (140, 148, 158, 255)


SYSTEM_FONT = "/System/Library/Fonts/Supplemental/Arial.ttf"


def font(size):
    try:
        return ImageFont.truetype(SYSTEM_FONT, size)
    except OSError:
        return ImageFont.load_default(size=size)


F_TITLE, F_LABEL, F_SMALL = font(26), font(15), font(12)


def scaled(img, factor):
    return img.resize((img.width * factor, img.height * factor), Image.NEAREST)


def checker(w, h, a=(52, 56, 62, 255), b=(44, 47, 53, 255), cell=8):
    img = Image.new("RGBA", (w, h), a)
    d = ImageDraw.Draw(img)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle((x, y, x + cell - 1, y + cell - 1), fill=b)
    return img


def tile(sheet, img, x, y, label=None, bg=True):
    if bg:
        sheet.alpha_composite(checker(img.width, img.height), (x, y))
    sheet.alpha_composite(img, (x, y))
    if label:
        ImageDraw.Draw(sheet).text((x, y + img.height + 4), label, font=F_SMALL, fill=MUTED)


def section(sheet, y, title):
    d = ImageDraw.Draw(sheet)
    d.rectangle((20, y, sheet.width - 20, y + 2), fill=(255, 187, 0, 255))
    d.text((20, y + 8), title, font=F_LABEL, fill=TEXT)
    return y + 34


def gate_render(size, tint, px, yaw=-32.0, pitch=18.0, scale=None, center=None):
    quads = model_quads(load_model(f"propwash:gate/{size}_frame"))
    quads += model_quads(load_model(f"propwash:gate/{size}_lights"), tint=tint)
    return render(quads, px, yaw=yaw, pitch=pitch, scale=scale, center=center)


def head_skin():
    skin = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(skin)
    d.rectangle((0, 0, 31, 15), fill=(196, 150, 118, 255))
    d.rectangle((8, 0, 15, 7), fill=(92, 62, 40, 255))
    d.rectangle((0, 8, 31, 9), fill=(92, 62, 40, 255))
    d.rectangle((10, 12, 10, 12), fill=(250, 250, 250, 255))
    d.rectangle((11, 12, 11, 12), fill=(60, 80, 160, 255))
    d.rectangle((13, 12, 13, 12), fill=(60, 80, 160, 255))
    d.rectangle((14, 12, 14, 12), fill=(250, 250, 250, 255))
    d.rectangle((10, 14, 13, 14), fill=(120, 70, 60, 255))
    return skin


def worn_quads(kind):
    def head_point(p):
        x, y, z = p
        u = gen.GOGGLE_UNIT
        return (-(x - 8) / u, (y - 8) / u + 4.0, -(z - 8) / u)

    quads = []
    for q in model_quads(load_model(f"propwash:item/goggles_{kind}_worn")):
        nx, ny, nz = q.normal
        quads.append(Quad(head_point(q.p00), head_point(q.p10), head_point(q.p01), q.texture, q.rect, q.tint, q.shade, (-nx, ny, -nz)))
    return quads


def head_render(kind, px, yaw=-30.0, pitch=15.0):
    quads = box_uv_quads(head_skin(), 0, 0, 8, 8, 8, (-4, 0, -4), (4, 8, 4))
    quads += worn_quads(kind)
    return render(quads, px, yaw=yaw, pitch=pitch, scale=px / 17.0, center=(0.0, 5.0))


def workbench_render(px):
    return render(model_quads(load_model("propwash:block/drone_workbench")), px, yaw=-35.0, pitch=28.0)


def gui_with_slots():
    gui = Image.open(ASSETS / "textures" / "gui" / "drone_workbench.png").convert("RGBA").crop((0, 0, gen.GUI_W, gen.GUI_H))
    for key, (sx, sy) in gen.PART_SLOTS.items():
        category = key.split("_")[0]
        icon = Image.open(ASSETS / "textures" / "gui" / "sprites" / "gui" / "slot" / f"{category}.png").convert("RGBA")
        gui.alpha_composite(icon, (sx, sy))
    icon = Image.open(ASSETS / "textures" / "gui" / "sprites" / "gui" / "slot" / "drone.png").convert("RGBA")
    gui.alpha_composite(icon, gen.SALVAGE_SLOT)
    big = scaled(gui, 2)
    d = ImageDraw.Draw(big)
    d.text((16, 12), "Drohnen-Werkbank", font=F_LABEL, fill=(230, 232, 236, 255))
    d.text((16, (gen.INVENTORY[1] - 11) * 2), "Inventar", font=F_LABEL, fill=(230, 232, 236, 255))
    return big


def main():
    sheet = Image.new("RGBA", (1500, 5200), BG)
    d = ImageDraw.Draw(sheet)
    d.text((20, 18), "Propwash FPV — Welt-Assets (assets-world)", font=F_TITLE, fill=TEXT)
    y = 64

    y = section(sheet, y, "Item-Icons (6×) und Werkbank-Block")
    items = [("goggles_analog", "Brille analog"), ("goggles_digital", "Brille digital"), ("goggles_creative", "Brille kreativ"),
             ("gate_small", "Gate klein"), ("gate_medium", "Gate mittel"), ("gate_large", "Gate groß"), ("course_tool", "Kurs-Werkzeug")]
    x = 20
    for name, label in items:
        img = Image.open(ASSETS / "textures" / "item" / f"{name}.png").convert("RGBA")
        tile(sheet, scaled(img, 6), x, y, label)
        x += 120
    tile(sheet, workbench_render(150), x + 10, y - 20, "Drohnen-Werkbank", bg=False)
    faces_x = x + 190
    for i, face in enumerate(gen.WORKBENCH_FACES):
        img = Image.open(ASSETS / "textures" / "block" / f"drone_workbench_{face}.png").convert("RGBA")
        tile(sheet, scaled(img, 4), faces_x + (i % 2) * 76, y - 16 + (i // 2) * 84, face)
    y += 150

    y = section(sheet, y, "Item-Icons in Originalgröße (1×, 2×) auf Inventar-Grau")
    strip = Image.new("RGBA", (1460, 44), (139, 139, 139, 255))
    x = 8
    for name, _ in items:
        img = Image.open(ASSETS / "textures" / "item" / f"{name}.png").convert("RGBA")
        strip.alpha_composite(img, (x, 14))
        strip.alpha_composite(scaled(img, 2), (x + 22, 6))
        x += 70
    sheet.alpha_composite(strip, (20, y))
    y += 64

    y = section(sheet, y, "Gates (Modell frame + lights; Lichter: grün = nächstes Gate, weiß, grau = ohne Kurs) — Maßstab gleich")
    tints = [(0x33, 0xFF, 0x66), (255, 255, 255), (0x8A, 0x8A, 0x8A)]
    common_scale = 6.5
    for i, size in enumerate(("small", "medium", "large")):
        img = gate_render(size, tints[i], 460, scale=common_scale)
        tile(sheet, img, 20 + i * 485, y, f"{size} {gen.GATES[size][0]}×{gen.GATES[size][1]} Blöcke", bg=False)
    y += 490
    for i, size in enumerate(("small", "medium", "large")):
        img = gate_render(size, tints[0], 300, yaw=0.0, pitch=0.0)
        tile(sheet, img, 20 + i * 330, y, f"{size} Frontansicht", bg=False)
    frame_tex = Image.open(ASSETS / "textures" / "item" / "gate" / "frame.png").convert("RGBA")
    light_tex = Image.open(ASSETS / "textures" / "item" / "gate" / "lights.png").convert("RGBA")
    tile(sheet, scaled(frame_tex, 4), 1020, y, "gate/frame.png (64×64)")
    tile(sheet, scaled(light_tex, 4), 1300, y, "gate/lights.png")
    y += 330

    y = section(sheet, y, "Werkbank-GUI (2×, mit Slot-Symbolen) und Slot-Sprites")
    gui = gui_with_slots()
    tile(sheet, gui, 20, y, "textures/gui/drone_workbench.png (312×224 von 512×256)", bg=False)
    sx = 20 + gui.width + 30
    for i, category in enumerate(gen.SLOT_CATEGORIES):
        icon = Image.open(ASSETS / "textures" / "gui" / "sprites" / "gui" / "slot" / f"{category}.png").convert("RGBA")
        cell_light = Image.new("RGBA", (72, 72), (139, 139, 139, 255))
        cell_light.alpha_composite(scaled(icon, 4), (4, 4))
        cell_dark = Image.new("RGBA", (72, 72), gen.GUI["slot"])
        cell_dark.alpha_composite(scaled(icon, 4), (4, 4))
        row, colm = divmod(i, 2)
        tile(sheet, cell_light, sx + colm * 170, y + row * 110, category, bg=False)
        tile(sheet, cell_dark, sx + colm * 170 + 78, y + row * 110, None, bg=False)
    y += gui.height + 40

    y = section(sheet, y, "Brillen am Kopf (3D-Modell item/goggles_*_worn im Kopf-Kontext) und Mod-Icon")
    for i, kind in enumerate(gen.GOGGLES):
        tile(sheet, head_render(kind, 200), 20 + i * 215, y, kind, bg=False)
        for j, (yaw, pitch) in enumerate(((-90.0, 5.0), (160.0, 20.0))):
            tile(sheet, head_render(kind, 96, yaw, pitch), 20 + i * 215 + j * 100, y + 230, None, bg=False)
    for i, kind in enumerate(gen.GOGGLES):
        tex = Image.open(ASSETS / "textures" / "item" / f"goggles_{kind}_worn.png").convert("RGBA")
        tile(sheet, scaled(tex, 2), 680 + (i % 2) * 150, y + (i // 2) * 160, f"goggles_{kind}_worn.png")
    icon = Image.open(ASSETS / "icon.png").convert("RGBA")
    tile(sheet, icon, 1000, y, "icon.png 256", bg=False)
    tile(sheet, icon.resize((64, 64), Image.LANCZOS), 1270, y, "64", bg=False)
    tile(sheet, icon.resize((32, 32), Image.LANCZOS), 1350, y, "32", bg=False)
    pack = Image.open(RES / "pack.png").convert("RGBA")
    tile(sheet, pack, 1270, y + 110, "pack.png", bg=False)
    y += 350

    y = preview_gameplay.draw_sections(sheet, y, section, tile, scaled, F_SMALL)
    sheet = sheet.crop((0, 0, sheet.width, y))

    out = DOCS / "asset-sheets" / "world_preview.png"
    save_png(sheet.convert("RGB"), out)
    print(out)


if __name__ == "__main__":
    main()
