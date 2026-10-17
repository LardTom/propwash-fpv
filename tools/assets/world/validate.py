"""Validate the generated Propwash world assets (JSON, references, atlases, sizes, gate geometry, GUI layout).

Run: python3 tools/assets/world/validate.py   (exit code 1 on any error)
"""

import json
import sys
from pathlib import Path

from PIL import Image

import charger
import course
import generate as gen
import maps
import materials
import menus
import remote
import repeater
from assetlib import ASSETS, REPO, RES, rel

def find_vanilla():
    for base in (REPO, *REPO.parents):
        candidate = base / ".ref" / "vanilla" / "assets" / "minecraft"
        if candidate.exists():
            return candidate
    return None


VANILLA = find_vanilla()

MODEL_ATLAS_DIRS = ("item/", "block/")
GATE_SIZES = ("small", "medium", "large")
errors = []


def error(msg):
    errors.append(msg)


def read_json(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        error(f"{rel(path)}: kein gültiges JSON ({e})")
        return None


def split(ref):
    return ref.split(":", 1) if ":" in ref else ("minecraft", ref)


def model_path(ref):
    ns, path = split(ref)
    if ns == "propwash":
        return ASSETS / "models" / f"{path}.json"
    return VANILLA / "models" / f"{path}.json" if VANILLA else None


def texture_path(ref):
    ns, path = split(ref)
    if ns == "propwash":
        return ASSETS / "textures" / f"{path}.png"
    return VANILLA / "textures" / f"{path}.png" if VANILLA else None


def png(path, size=None, what=""):
    if not path.exists():
        error(f"{rel(path)}: Datei fehlt {what}")
        return None
    try:
        img = Image.open(path)
        img.load()
    except OSError as e:
        error(f"{rel(path)}: kein gültiges PNG ({e})")
        return None
    if img.format != "PNG":
        error(f"{rel(path)}: Format {img.format}, erwartet PNG")
    if size and img.size != size:
        error(f"{rel(path)}: Größe {img.size}, erwartet {size}")
    return img.convert("RGBA")


def load_model_chain(ref, seen=None):
    seen = seen or []
    if ref in seen:
        error(f"Modell-Schleife: {' -> '.join(seen + [ref])}")
        return {}
    path = model_path(ref)
    if path is None:
        return {}
    if not path.exists():
        if split(ref)[0] == "propwash" or split(ref)[1] not in ("builtin/generated", "builtin/entity"):
            if not split(ref)[1].startswith("builtin/"):
                error(f"Modell {ref} fehlt ({rel(path) if split(ref)[0] == 'propwash' else path})")
        return {}
    model = read_json(path) or {}
    textures = {}
    elements = None
    if "parent" in model:
        parent = load_model_chain(model["parent"], seen + [ref])
        textures.update(parent.get("textures", {}))
        elements = parent.get("elements")
    textures.update(model.get("textures", {}))
    if "elements" in model:
        elements = model["elements"]
    return {"textures": textures, "elements": elements, "raw": model}


def resolve_texture(textures, key, where):
    depth = 0
    while key.startswith("#"):
        name = key[1:]
        if name not in textures:
            error(f"{where}: Texturvariable #{name} nicht definiert")
            return None
        key = textures[name]
        depth += 1
        if depth > 8:
            error(f"{where}: Texturvariablen-Schleife")
            return None
    return key


def check_model(ref):
    path = model_path(ref)
    where = rel(path)
    chain = load_model_chain(ref)
    if not chain:
        return None
    textures = chain["textures"]
    for name, value in textures.items():
        if value.startswith("#"):
            continue
        ns, tpath = split(value)
        if ns == "propwash":
            if not tpath.startswith(MODEL_ATLAS_DIRS):
                error(f"{where}: Textur {value} liegt nicht im Item- oder Block-Atlas (textures/item/** oder textures/block/**)")
            if not texture_path(value).exists():
                error(f"{where}: Textur {value} fehlt")
    atlases = set()
    for value in textures.values():
        if not value.startswith("#"):
            atlases.add(split(value)[1].split("/")[0])
    if len(atlases) > 1 and "item" in atlases and "block" in atlases:
        error(f"{where}: Texturen aus Item- und Block-Atlas gemischt")
    for index, element in enumerate(chain["elements"] or []):
        for key in ("from", "to"):
            vec = element.get(key)
            if not isinstance(vec, list) or len(vec) != 3:
                error(f"{where}: Element {index} {key} ungültig")
                continue
            if any(v < -16 or v > 32 for v in vec):
                error(f"{where}: Element {index} {key}={vec} außerhalb -16..32")
        if any(element["from"][i] > element["to"][i] for i in range(3)):
            error(f"{where}: Element {index} from > to")
        emission = element.get("light_emission", 0)
        if not isinstance(emission, int) or not 0 <= emission <= 15:
            error(f"{where}: Element {index} light_emission {emission} ungültig")
        faces = element.get("faces", {})
        if not faces:
            error(f"{where}: Element {index} ohne Flächen")
        for direction, face in faces.items():
            if direction not in ("down", "up", "north", "south", "west", "east"):
                error(f"{where}: Element {index} unbekannte Fläche {direction}")
            uv = face.get("uv")
            if uv is not None and (len(uv) != 4 or any(v < 0 or v > 16 for v in uv)):
                error(f"{where}: Element {index} {direction} uv {uv} außerhalb 0..16")
            resolve_texture(textures, face.get("texture", ""), f"{where} Element {index} {direction}")
    return chain


def item_model_refs(node, where):
    kind = node.get("type")
    if kind == "minecraft:model":
        return [node.get("model", "")]
    if kind == "minecraft:select":
        refs = []
        for case in node.get("cases", []):
            refs += item_model_refs(case.get("model", {}), where)
        if "fallback" in node:
            refs += item_model_refs(node["fallback"], where)
        return refs
    if kind == "minecraft:composite":
        refs = []
        for child in node.get("models", []):
            refs += item_model_refs(child, where)
        return refs
    if kind == "minecraft:special":
        if not node.get("model", {}).get("type"):
            error(f"{where}: minecraft:special ohne Renderer-Typ")
        return [node.get("base", "")]
    error(f"{where}: unerwarteter Item-Modell-Typ {kind}")
    return []


def check_item_definition(item_id):
    path = ASSETS / "items" / f"{item_id}.json"
    data = read_json(path)
    if data is None:
        return
    refs = item_model_refs(data.get("model", {}), rel(path))
    for ref in refs:
        if not model_path(ref) or not model_path(ref).exists():
            error(f"{rel(path)}: Modell {ref} fehlt")
            continue
        check_model(ref)
    return data


def bbox(elements):
    lo = [min(e["from"][i] for e in elements) for i in range(3)]
    hi = [max(e["to"][i] for e in elements) for i in range(3)]
    return lo, hi


def check_gate_pieces():
    s = gen.GATE_PX_PER_BLOCK
    expected = set(gen.gate_piece_names())
    present = {p.stem for p in (ASSETS / "models" / "gate" / "piece").glob("*.json")}
    if present != expected:
        error(f"models/gate/piece: erwartet {sorted(expected)}, gefunden {sorted(present)}")
    for name in sorted(expected & present):
        piece = check_model(f"propwash:gate/piece/{name}")
        check_item_definition(f"race/gate_piece_{name}")
        if not piece:
            continue
        lights = name.startswith("led_")
        for index, element in enumerate(piece["elements"]):
            if lights and element.get("light_emission") != 15:
                error(f"Gate-Teil {name}: Licht-Element {index} ohne light_emission 15")
            for direction, face in element["faces"].items():
                if lights != ("tintindex" in face):
                    error(f"Gate-Teil {name}: Element {index} {direction} mit falschem tintindex")
        lo, hi = bbox(piece["elements"])
        length = (hi[1] - lo[1]) if name.startswith(("post_", "start_post_", "led_post_")) else (hi[0] - lo[0])
        if name.startswith(("post_", "start_post_", "led_post_", "bar_", "start_bar_", "led_bottom", "led_top", "banner_panel")):
            want = (0.5 if name.endswith("_half") else 1.0) * s
            if abs(length - want) > 1e-6:
                error(f"Gate-Teil {name}: Länge {length / s} statt {want / s} Block")


def check_gates():
    for size in GATE_SIZES:
        width, height = gen.GATES[size]
        frame = check_model(f"propwash:gate/{size}_frame")
        lights = check_model(f"propwash:gate/{size}_lights")
        if not frame or not lights:
            continue
        s = gen.GATE_PX_PER_BLOCK
        lo, hi = bbox(frame["elements"])
        expected_x = (8 - width * s / 2, 8 + width * s / 2)
        if (lo[0], hi[0]) != expected_x:
            error(f"Gate {size}: Rahmen-X {lo[0]}..{hi[0]}, erwartet {expected_x}")
        if (lo[1], hi[1]) != (0, height * s):
            error(f"Gate {size}: Rahmen-Y {lo[1]}..{hi[1]}, erwartet 0..{height * s}")
        if abs((lo[2] + hi[2]) / 2 - 8) > 1e-6:
            error(f"Gate {size}: Rahmen nicht mittig in Z")
        llo, lhi = bbox(lights["elements"])
        if llo[0] < lo[0] or lhi[0] > hi[0] or llo[1] < lo[1] or lhi[1] > hi[1]:
            error(f"Gate {size}: Lichter ragen über den Rahmen hinaus")
        for index, element in enumerate(lights["elements"]):
            if element.get("light_emission") != 15:
                error(f"Gate {size}: Licht-Element {index} ohne light_emission 15")
            for direction, face in element["faces"].items():
                if face.get("tintindex") != 0:
                    error(f"Gate {size}: Licht-Element {index} {direction} ohne tintindex 0")
        for index, element in enumerate(frame["elements"]):
            for direction, face in element["faces"].items():
                if "tintindex" in face:
                    error(f"Gate {size}: Rahmen-Element {index} {direction} hat tintindex")
        inner = width / 2 - gen.GATE_FRAME
        opening = (8 - inner * s, 8 + inner * s)
        for element in frame["elements"]:
            f, t = element["from"], element["to"]
            if f[0] < opening[1] and t[0] > opening[0] and f[1] < (height - gen.GATE_FRAME) * s and t[1] > gen.GATE_FRAME * s:
                error(f"Gate {size}: Element {element.get('name')} ragt in die Öffnung")
        start = check_model(f"propwash:gate/{size}_start_frame")
        if start:
            if [e["from"] + e["to"] for e in start["elements"]] != [e["from"] + e["to"] for e in frame["elements"]]:
                error(f"Gate {size}: Start-/Ziel-Rahmen weicht in der Form vom Rahmen ab")
            if start["textures"].get("frame") != "propwash:item/gate/start_frame":
                error(f"Gate {size}: Start-/Ziel-Rahmen ohne karierte Textur")
        for base in ("legs", "stacked", "mount"):
            suffix = gen.gate_variant_suffix(base)
            variant = check_model(f"propwash:gate/{size}_frame{suffix}")
            variant_lights = check_model(f"propwash:gate/{size}_lights{suffix}")
            variant_start = check_model(f"propwash:gate/{size}_start_frame{suffix}")
            if not variant or not variant_lights or not variant_start:
                continue
            if [e["from"] + e["to"] for e in variant_start["elements"]] != [e["from"] + e["to"] for e in variant["elements"]]:
                error(f"Gate {size}: Start-/Ziel-Rahmen {base} weicht in der Form ab")
            vlo, vhi = bbox(variant["elements"])
            if min(vlo) < -16 or max(vhi) > 32:
                error(f"Gate {size}: Rahmen {base} verlässt das Modellraster")
            if (vlo[0], vhi[0]) != expected_x or vhi[1] != height * s:
                error(f"Gate {size}: Rahmen {base} X {vlo[0]}..{vhi[0]}, oben {vhi[1]}")
            for element in variant["elements"]:
                f, t = element["from"], element["to"]
                if base != "mount" and f[0] < opening[1] and t[0] > opening[0] and t[1] <= gen.GATE_FRAME * s + 1e-6 and f[1] < gen.GATE_FRAME * s:
                    if element.get("name", "").startswith("bar_bottom"):
                        error(f"Gate {size}: Rahmen {base} hat noch einen unteren Balken")
            names = {e.get("name", "") for e in variant["elements"]}
            if base == "stacked" and (any(n.startswith("foot_") or n.startswith("corner_bottom") for n in names) or "tee_left" not in names):
                error(f"Gate {size}: gestapelter Rahmen mit Füßen/Ecken unten oder ohne T-Stück")
            if base == "legs" and not {"foot_left", "foot_right"} <= names:
                error(f"Gate {size}: Rahmen auf Beinen ohne Füße")
            light_names = {e.get("name", "") for e in variant_lights["elements"]}
            if base == "mount":
                if any(n.startswith("foot_") for n in names) or not any(n.startswith("bar_bottom") for n in names):
                    error(f"Gate {size}: Wand-/Deckenrahmen mit Füßen oder ohne unteren Balken")
                if not any(n.startswith("led_bottom_front") for n in light_names):
                    error(f"Gate {size}: Lichter mount ohne untere Leisten")
                continue
            if any(n.startswith("led_bottom_front") or n.startswith("led_bottom_back") for n in light_names):
                error(f"Gate {size}: Lichter {base} haben untere Leisten ohne unteren Balken")
        banner = check_model(f"propwash:gate/{size}_banner")
        if banner:
            blo, bhi = bbox(banner["elements"])
            if (blo[0], bhi[0]) != expected_x:
                error(f"Gate {size}: Banner-X {blo[0]}..{bhi[0]}, erwartet {expected_x}")
            if (blo[1], bhi[1]) != (0, gen.GATE_BANNER_HEIGHT * s):
                error(f"Gate {size}: Banner-Y {blo[1]}..{bhi[1]}, erwartet 0..{gen.GATE_BANNER_HEIGHT * s}")
            if abs((blo[2] + bhi[2]) / 2 - 8) > 1e-6:
                error(f"Gate {size}: Banner nicht mittig in Z")
            if min(blo) < -16 or max(bhi) > 32:
                error(f"Gate {size}: Banner verlässt das Modellraster")
    s = gen.GATE_PX_PER_BLOCK
    plate = check_model("propwash:gate/mount_plate")
    if plate:
        plo, phi = bbox(plate["elements"])
        if abs((plo[0] + phi[0]) / 2 - 8) > 1e-6 or abs((plo[2] + phi[2]) / 2 - 8) > 1e-6 or plo[1] != 0:
            error("Montageplatte: Ursprung nicht unten mittig")
        if (phi[1] - plo[1]) > 0.25 * s:
            error("Montageplatte zu dick")
    hanger = check_model("propwash:gate/hanger")
    if hanger:
        hlo, hhi = bbox(hanger["elements"])
        if hlo[1] != 0 or hhi[1] != gen.GATE_HANG_DROP * s:
            error(f"Aufhängung: Y {hlo[1]}..{hhi[1]}, erwartet 0..{gen.GATE_HANG_DROP * s}")
        if abs((hlo[0] + hhi[0]) / 2 - 8) > 1e-6 or abs((hlo[2] + hhi[2]) / 2 - 8) > 1e-6:
            error("Aufhängung nicht mittig")
    check_gate_pieces()
    for tex, size in (("item/gate/frame", (gen.FRAME_TEX, gen.FRAME_TEX)), ("item/gate/lights", (gen.LIGHT_TEX, gen.LIGHT_TEX)),
                      ("item/gate/start_frame", (gen.FRAME_TEX, gen.FRAME_TEX)), ("item/gate/banner", (gen.BANNER_TEX, gen.BANNER_TEX))):
        img = png(texture_path(f"propwash:{tex}"), size)
        if img is not None and tex.endswith("lights"):
            for x0, y0, x1, y1 in gen.LIGHT_REGIONS.values():
                for y in range(y0, y1):
                    for x in range(x0, x1):
                        r, g, b, a = img.getpixel((x, y))
                        if not (r == g == b and a == 255 and r >= 0xB0):
                            error(f"gate/lights.png: Pixel {(x, y)} nicht neutral hell ({r},{g},{b},{a})")
                            return


def check_workbench():
    data = read_json(ASSETS / "blockstates" / "drone_workbench.json")
    if data:
        variants = data.get("variants", {})
        if list(variants) != [""]:
            error("blockstates/drone_workbench.json: erwartet genau eine Variante \"\" (Block ohne Eigenschaften)")
        for variant in variants.values():
            ref = variant.get("model", "")
            if not model_path(ref).exists():
                error(f"blockstates/drone_workbench.json: Modell {ref} fehlt")
            check_model(ref)
    check_item_definition("drone_workbench")
    for face in gen.WORKBENCH_FACES:
        png(ASSETS / "textures" / "block" / f"drone_workbench_{face}.png", (16, 16))


def check_goggles():
    for kind in gen.GOGGLES:
        name = f"goggles_{kind}"
        data = check_item_definition(name)
        icon = png(ASSETS / "textures" / "item" / f"{name}.png", (16, 16))
        if icon is not None and icon.getextrema()[3][0] != 0:
            error(f"{name}.png: Item-Icon ohne Transparenz")
        png(ASSETS / "textures" / "item" / f"{name}_worn.png", (gen.GOGGLE_TEX, gen.GOGGLE_TEX))
        model = (data or {}).get("model", {})
        cases = model.get("cases", [])
        worn = f"propwash:item/{name}_worn"
        if model.get("type") != "minecraft:select" or model.get("property") != "minecraft:display_context":
            error(f"items/{name}.json: erwartet select auf minecraft:display_context")
        elif not any("head" in case.get("when", []) and case.get("model", {}).get("model") == worn for case in cases):
            error(f"items/{name}.json: Kopf-Kontext zeigt nicht {worn}")
        elif model.get("fallback", {}).get("model") != f"propwash:item/{name}":
            error(f"items/{name}.json: Rückfall ist nicht das flache Icon-Modell")
        chain = check_model(worn)
        if not chain or not chain["elements"]:
            error(f"{worn}: keine Elemente")
            continue
        lo, hi = bbox(chain["elements"])
        head_lo, head_hi = 8 - 4 * gen.GOGGLE_UNIT, 8 + 4 * gen.GOGGLE_UNIT
        if not (lo[2] < head_lo - 2 * gen.GOGGLE_UNIT):
            error(f"{worn}: Brillenkasten ragt nicht vor das Gesicht (min z {lo[2]})")
        if not (hi[2] > head_hi and lo[0] < head_lo and hi[0] > head_hi):
            error(f"{worn}: Kopfband umschließt den Kopf nicht ({lo} .. {hi})")
        eyes = 8 + (3.5 - 4.0) * gen.GOGGLE_UNIT
        visor = next((e for e in chain["elements"] if e.get("name") == "visor"), None)
        if visor is None or not (visor["from"][1] <= eyes <= visor["to"][1]):
            error(f"{worn}: Brillenkasten deckt die Augenhöhe nicht ab")
        if (ASSETS / "equipment" / f"{name}.json").exists():
            error(f"equipment/{name}.json: veraltet, die Brille wird als 3D-Modell getragen")


def check_items():
    for size in GATE_SIZES:
        check_item_definition(f"gate_{size}")
        png(ASSETS / "textures" / "item" / f"gate_{size}.png", (16, 16))
    check_item_definition("course_tool")
    png(ASSETS / "textures" / "item" / "course_tool.png", (16, 16))
    check_item_definition("waterproof_spray")
    png(ASSETS / "textures" / "item" / "waterproof_spray.png", (16, 16))
    for name in materials.MATERIALS:
        check_item_definition(name)
        png(ASSETS / "textures" / "item" / f"{name}.png", (16, 16))
    check_item_definition(maps.ITEM)
    png(ASSETS / "textures" / "item" / f"{maps.ITEM}.png", maps.SIZES[maps.ITEM])
    png(ASSETS / "textures" / "map" / "decorations" / f"{maps.DECORATION}.png", maps.SIZES[maps.DECORATION])
    for name, img in maps.sheets().items():
        target = ASSETS / "textures" / ("item" if name == maps.ITEM else "map/decorations") / f"{name}.png"
        if target.exists() and Image.open(target).convert("RGBA").tobytes() != img.tobytes():
            error(f"{rel(target)} weicht von assets-src/world/pixel/maps.txt ab (generate.py ausführen)")


def check_course():
    s = course.PX
    present = {p.stem for p in (ASSETS / "models" / "course").glob("*.json")}
    if present != set(course.PIECES):
        error(f"models/course: erwartet {sorted(course.PIECES)}, gefunden {sorted(present)}")
    lengths = {"slalom_tube": 1.0, "slalom_tube_half": 0.5, "flag_tube": 1.0, "flag_tube_half": 0.5, "cube_post": 1.0, "cube_post_half": 0.5,
               "cube_bar": 1.0, "cube_bar_half": 0.5, "cube_led": 1.0, "cube_led_half": 0.5}
    for name in sorted(present & set(course.PIECES)):
        piece = check_model(f"propwash:course/{name}")
        definition = check_item_definition(f"race/course_{name}")
        if not piece or not piece["elements"]:
            error(f"Kurs-Teil {name}: keine Elemente")
            continue
        tinted = name in course.TINTED
        if definition is not None and tinted != bool(definition.get("model", {}).get("tints")):
            error(f"items/race/course_{name}.json: Einfärbung passt nicht zum Teil")
        for index, element in enumerate(piece["elements"]):
            if tinted and element.get("light_emission") != 15:
                error(f"Kurs-Teil {name}: Licht-Element {index} ohne light_emission 15")
        lo, hi = bbox(piece["elements"])
        if name in lengths:
            vertical = "tube" in name or "post" in name
            length = (hi[1] - lo[1]) if vertical else (hi[0] - lo[0])
            if abs(length - lengths[name] * s) > 1e-6:
                error(f"Kurs-Teil {name}: Länge {length / s} statt {lengths[name]} Block")
        if name.startswith(("slalom_tube", "flag_tube", "cube_post")) and abs((lo[0] + hi[0]) * 0.5 - 8) > 1e-6:
            error(f"Kurs-Teil {name}: Rohr nicht mittig auf der Achse")
    png(ASSETS / "textures" / "item" / "course" / "pole.png", (course.TEX, course.TEX))
    png(ASSETS / "textures" / "item" / "course" / "flag.png", (course.FLAG_TEX, course.FLAG_TEX))
    for name in ("race_flag", "slalom_pole", "dive_cube", "freeform_gate", "gate_pole"):
        check_item_definition(name)
        png(ASSETS / "textures" / "item" / f"{name}.png", (16, 16))


def check_gui():
    gui = png(ASSETS / "textures" / "gui" / "drone_workbench.png", (gen.GUI_TEX_W, gen.GUI_TEX_H))
    for category in gen.SLOT_CATEGORIES:
        png(ASSETS / "textures" / "gui" / "sprites" / "gui" / "slot" / f"{category}.png", (16, 16))
    if gui is None:
        return
    slot = gen.GUI["slot"]
    slots = dict(gen.PART_SLOTS)
    slots["salvage"] = gen.SALVAGE_SLOT
    slots["result"] = gen.RESULT_SLOT
    ix, iy = gen.INVENTORY
    for row in range(3):
        for column in range(9):
            slots[f"inventory_{row}_{column}"] = (ix + column * 18, iy + row * 18)
    for column in range(9):
        slots[f"hotbar_{column}"] = (ix + column * 18, iy + gen.HOTBAR_OFFSET)
    for name, (sx, sy) in slots.items():
        for x, y in ((sx, sy), (sx + 15, sy + 15), (sx + 8, sy + 8)):
            if gui.getpixel((x, y)) != slot:
                error(f"GUI: Slot {name} bei ({sx},{sy}) nicht als Slot-Fläche gezeichnet (Pixel {x},{y})")
                break
    corner = gui.getpixel((0, 0))
    if corner[3] != 0:
        error("GUI: Ecke (0,0) sollte transparent sein (abgerundetes Panel)")
    if gui.getpixel((gen.GUI_W, 0))[3] != 0 or gui.getpixel((0, gen.GUI_H))[3] != 0:
        error("GUI: außerhalb 312×224 muss die Textur leer sein")


def overlaps(a, b):
    return all(a[i] < b[i + 3] and b[i] < a[i + 3] for i in range(3))


def element_box(element):
    return tuple(element["from"]) + tuple(element["to"])


REMOTE_DISPLAY = ("firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand",
                  "gui", "ground", "fixed", "head", "on_shelf")
GUI_SLOT = gen.GUI["slot"]
GUI_CHANNEL = gen.GUI["channel"]
GUI_CHANNEL_EDGE = gen.GUI["channel_edge"]
AMBER = (0xFF, 0xBB, 0x00)


def check_remote():
    data = check_item_definition("remote")
    full_ref, body_ref = "propwash:item/remote", "propwash:item/remote_body"
    if data is not None and data != remote.item_definition():
        error("items/remote.json: Aufbau weicht vom Vertrag ab (select display_context -> remote, sonst composite remote_body + special propwash:remote_gimbals)")
    png(ASSETS / "textures" / "item" / "remote.png", (remote.TEX, remote.TEX))
    full = read_json(model_path(full_ref)) or {}
    body = read_json(model_path(body_ref)) or {}
    check_model(full_ref)
    check_model(body_ref)
    for ref, model in ((full_ref, full), (body_ref, body)):
        if model.get("texture_size") != [remote.TEX, remote.TEX]:
            error(f"{ref}: texture_size muss [{remote.TEX}, {remote.TEX}] sein")
        for key in REMOTE_DISPLAY:
            if key not in model.get("display", {}):
                error(f"{ref}: display.{key} fehlt")
    if full.get("display") != body.get("display"):
        error("remote/remote_body: display-Blöcke unterscheiden sich")
    sticks = [remote.STICK_LEFT, remote.STICK_RIGHT]
    stick_boxes = [box for pair in sticks for box in pair]
    full_boxes = {e.get("name"): element_box(e) for e in full.get("elements", [])}
    for side, (shaft, cap) in (("left", remote.STICK_LEFT), ("right", remote.STICK_RIGHT)):
        for part, box in (("shaft", shaft), ("cap", cap)):
            name = f"gimbal_{side}_{part}"
            if full_boxes.get(name) != box:
                error(f"{full_ref}: Element {name} fehlt oder hat andere Maße als {box}")
    for element in body.get("elements", []):
        for box in stick_boxes:
            if overlaps(element_box(element), box):
                error(f"{body_ref}: Element {element.get('name')} ragt in die Knüppel-Box {box}")
    body_names = [e.get("name") for e in body.get("elements", [])]
    full_names = [e.get("name") for e in full.get("elements", [])]
    if [n for n in full_names if not n.startswith(("gimbal_left_", "gimbal_right_", "switch_"))] != body_names:
        error("remote_body muss genau remote ohne die Knüppel- und Schalter-Elemente sein (die zeichnet RemoteGimbalModel)")
    if sorted(n for n in full_names if n.startswith("switch_")) != [f"switch_{i}" for i in range(4)]:
        error(f"{full_ref}: braucht genau die Schalter switch_0..3 (Vorlage für RemoteGimbalModel)")
    for pivot, plate in ((remote.PIVOT_LEFT, remote.PLATE_LEFT), (remote.PIVOT_RIGHT, remote.PLATE_RIGHT)):
        px, py, pz = pivot
        if not (plate[0] < px < plate[3] and plate[1] < py < plate[4] and pz == plate[5]):
            error(f"Funke: Drehpunkt {pivot} liegt nicht auf der Tellerfläche {plate}")
        if abs(px - (plate[0] + plate[3]) / 2) > 1e-6 or abs(py - (plate[1] + plate[4]) / 2) > 1e-6:
            error(f"Funke: Drehpunkt {pivot} nicht in der Tellermitte")
    for (shaft, cap), pivot in zip(sticks, (remote.PIVOT_LEFT, remote.PIVOT_RIGHT)):
        centre = ((shaft[0] + shaft[3]) / 2, (shaft[1] + shaft[4]) / 2, shaft[2])
        if centre != pivot:
            error(f"Funke: Knüppel {shaft} sitzt nicht im Drehpunkt {pivot}")
    for ref, model in ((full_ref, full), (body_ref, body)):
        names = [e.get("name") for e in model.get("elements", [])]
        by_group = {g.get("name"): g for g in model.get("groups", [])}
        covered = sorted(i for g in model.get("groups", []) for i in g.get("children", []))
        if covered != list(range(len(names))):
            error(f"{ref}: groups müssen jedes Element genau einmal enthalten")
        if "body" not in by_group:
            error(f"{ref}: Gruppe body fehlt")
        for side, pivot in (("left", remote.PIVOT_LEFT), ("right", remote.PIVOT_RIGHT)):
            group = by_group.get(f"gimbal_{side}")
            if ref == body_ref:
                if group is not None:
                    error(f"{ref}: Gruppe gimbal_{side} darf in remote_body nicht vorkommen")
                continue
            if group is None:
                error(f"{ref}: Gruppe gimbal_{side} fehlt")
                continue
            if group.get("origin") != list(pivot):
                error(f"{ref}: Gruppe gimbal_{side} braucht origin {list(pivot)}")
            if sorted(names[i] for i in group.get("children", []) if i < len(names)) != [f"gimbal_{side}_cap", f"gimbal_{side}_shaft"]:
                error(f"{ref}: Gruppe gimbal_{side} muss genau Schaft und Kappe enthalten")
    display = next((e for e in full.get("elements", []) if e.get("name") == "display"), None)
    if display is None or display.get("light_emission") != 6 or list(display.get("faces", {})) != ["south"]:
        error(f"{full_ref}: Display-Element braucht nur die Fläche south und light_emission 6")
    gimbal = png(ASSETS / "textures" / "entity" / "remote" / "gimbal.png", (remote.GIMBAL_TEX, remote.GIMBAL_TEX))
    if gimbal is not None and gimbal.size == (16, 16):
        if any(gimbal.getpixel((x, 8))[:3] != AMBER for x in range(16)):
            error("gimbal.png: Zeile 8 muss der Amber-Ring sein")
        if gimbal.getpixel((8, 3))[:3] != (0x8C, 0x94, 0x9E):
            error("gimbal.png: Schaft (Zeilen 0-7) muss Metall #8C949E sein")


def check_charger():
    data = read_json(ASSETS / "blockstates" / "battery_charger.json")
    if data:
        parts = data.get("multipart", [])
        expected = set()
        for f in charger.FACINGS:
            for s in charger.STATES:
                expected.add((("facing", f), ("state", s)))
            for i in range(len(charger.SLOT_CENTERS)):
                for b in charger.BAY_STATES:
                    expected.add((("facing", f), (f"bay_{i}", b)))
        seen = set()
        for part in parts:
            when = part.get("when", {})
            key = tuple(sorted(when.items()))
            seen.add(key)
            apply = part.get("apply", {})
            if "state" in when:
                model = f"propwash:block/battery_charger_{when['state']}"
            else:
                bay = next((k for k in when if k.startswith("bay_")), None)
                model = f"propwash:block/battery_charger_{bay}_{when.get(bay)}" if bay else None
            if apply.get("model") != model:
                error(f"blockstates/battery_charger.json: {when} zeigt auf {apply.get('model')}")
            if apply.get("y", 0) != charger.FACINGS.get(when.get("facing")):
                error(f"blockstates/battery_charger.json: {when} hat y={apply.get('y', 0)}")
        if seen != {tuple(sorted(k)) for k in expected}:
            error(f"blockstates/battery_charger.json: {len(seen)} Teile statt {len(expected)}")
    for face in charger.FACES:
        png(ASSETS / "textures" / "block" / f"battery_charger_{face}.png", (charger.TEX, charger.TEX))
    shape = (1.0, 0.0, 2.0, 15.0, 8.0, 14.0)
    b = charger.BOUNDS
    for state in charger.STATES:
        ref = f"propwash:block/battery_charger_{state}"
        chain = check_model(ref)
        if not chain or not chain["elements"]:
            error(f"{ref}: keine Elemente")
            continue
        lo, hi = bbox(chain["elements"])
        if any(lo[i] < b[i] for i in range(3)) or any(hi[i] > b[i + 3] for i in range(3)):
            error(f"{ref}: Bounding-Box {lo}..{hi} nicht in {b}")
        for element in chain["elements"]:
            name = element.get("name", "")
            box = element_box(element)
            front = name == "front_plate" or name == "display"
            if not front and (any(box[i] < shape[i] for i in range(3)) or any(box[i + 3] > shape[i + 3] for i in range(3))):
                error(f"{ref}: {name} ragt aus der Code-Form Block.box(1, 0, 2, 15, 8, 14)")
            if name.startswith("led_"):
                error(f"{ref}: LEDs gehören in die Schacht-Modelle")
            emission = element.get("light_emission", 0)
            if name == "display" and emission != (0 if state == "idle" else charger.EMISSION):
                error(f"{ref}: display light_emission {emission} passt nicht zum Zustand {state}")
            if name != "display" and emission:
                error(f"{ref}: {name} darf nicht leuchten")
        if chain["textures"].get("front") != f"propwash:block/battery_charger_front_{state}":
            error(f"{ref}: Front-Textur passt nicht zum Zustand")
    for index in range(len(charger.SLOT_CENTERS)):
        center = charger.bay_center(index)
        for bay_state in charger.BAY_STATES:
            ref = f"propwash:block/battery_charger_bay_{index}_{bay_state}"
            chain = check_model(ref)
            if not chain or not chain["elements"]:
                error(f"{ref}: keine Elemente")
                continue
            leds = [e for e in chain["elements"] if e.get("name") == f"led_{index}"]
            packs = [e for e in chain["elements"] if e.get("name") == f"pack_{index}"]
            if len(leds) != 1 or (leds[0]["from"][0] + leds[0]["to"][0]) / 2 != center:
                error(f"{ref}: LED fehlt oder sitzt nicht bei x = {center}")
            elif leds[0].get("light_emission", 0) != (0 if bay_state == "empty" else charger.EMISSION):
                error(f"{ref}: LED-Leuchten passt nicht zu {bay_state}")
            if len(packs) != (0 if bay_state == "empty" else 1):
                error(f"{ref}: Akku im Schacht passt nicht zu {bay_state}")
            for pack in packs:
                x0, x1 = charger.slot_x(center)
                box = element_box(pack)
                if box[0] < x0 or box[3] > x1 or box[2] < charger.SLOT_Z[0] or box[5] > charger.SLOT_Z[1]:
                    error(f"{ref}: Akku ragt aus dem Schacht")
                faces = pack.get("faces", {})
                for side in ("north", "south", "east", "west"):
                    if faces.get(side, {}).get("tintindex") != index:
                        error(f"{ref}: Akku-Seite {side} ohne tintindex {index} (Farbe je Akku-Typ)")
                if "tintindex" in faces.get("up", {}):
                    error(f"{ref}: Akku-Oberseite darf nicht eingefärbt werden")
            if chain["textures"].get("led") != f"propwash:block/battery_charger_front_{charger.BAY_LED[bay_state]}":
                error(f"{ref}: LED-Textur passt nicht zu {bay_state}")
    item = read_json(ASSETS / "items" / "battery_charger.json")
    if item and item.get("model", {}).get("model") != "propwash:item/battery_charger":
        error("items/battery_charger.json: muss propwash:item/battery_charger zeigen")
    if item and len(item.get("model", {}).get("tints", [])) != len(charger.SLOT_CENTERS):
        error("items/battery_charger.json: je Schacht ein Farbton für den Akku erwartet")
    icon = read_json(ASSETS / "models" / "item" / "battery_charger.json")
    if icon and icon.get("parent") != "propwash:block/battery_charger_inventory":
        error("models/item/battery_charger.json: muss auf propwash:block/battery_charger_inventory aufbauen")
    chain = check_model("propwash:block/battery_charger_inventory")
    if chain and len([e for e in chain["elements"] if e.get("name", "").startswith("pack_")]) != 3:
        error("battery_charger_inventory: drei Akkus in den Schächten erwartet")
    check_item_definition("battery_charger")


def check_repeater():
    name = repeater.NAME
    data = read_json(ASSETS / "blockstates" / f"{name}.json")
    if data:
        variants = data.get("variants", {})
        expected = {f"face={f},facing={d}" for f in repeater.FACES for d in repeater.FACINGS}
        if set(variants) != expected:
            error(f"blockstates/{name}.json: Varianten {sorted(variants)} statt {sorted(expected)}")
        for key, apply in variants.items():
            props = dict(part.split("=") for part in key.split(","))
            x, y = repeater.rotation(props.get("face"), props.get("facing"))
            if apply.get("model") != f"propwash:block/{name}" or apply.get("x", 0) != x or apply.get("y", 0) != y:
                error(f"blockstates/{name}.json: {key} dreht falsch ({apply})")
            if apply.get("uvlock"):
                error(f"blockstates/{name}.json: {key} ohne uvlock erwartet")
    for key in repeater.TEXTURES:
        png(ASSETS / "textures" / "block" / f"{name}_{key}.png", (repeater.TEX, repeater.TEX))
    png(ASSETS / "textures" / "block" / f"{name}_led.png", (16, 32))
    meta = read_json(ASSETS / "textures" / "block" / f"{name}_led.png.mcmeta")
    if meta and len(meta.get("animation", {}).get("frames", [])) != 2:
        error(f"{name}_led.png.mcmeta: zwei Bilder (an, gedimmt) erwartet")
    chain = check_model(f"propwash:block/{name}")
    if chain and chain["elements"]:
        lo, hi = bbox(chain["elements"])
        if lo + hi != list(repeater.BOUNDS):
            error(f"block/{name}: Begrenzung {lo + hi} statt {list(repeater.BOUNDS)}")
        leds = [e for e in chain["elements"] if e.get("name") == "led"]
        if len(leds) != 1 or leds[0].get("light_emission", 0) != repeater.EMISSION:
            error(f"block/{name}: eine leuchtende LED erwartet")
        housing = [e for e in chain["elements"] if e.get("name") == "housing"]
        if leds and housing and not leds[0]["to"][1] > housing[0]["to"][1]:
            error(f"block/{name}: LED liegt nicht über dem Gehäuse (Z-Fighting)")
        body = [repeater.SHAPE_BODY, repeater.SHAPE_ANTENNA]
        for element in chain["elements"]:
            inside = any(all(b[i] - 1e-6 <= element["from"][i] and element["to"][i] <= b[i + 3] + 1e-6 for i in range(3)) for b in body)
            if element.get("name") != "led" and not inside:
                error(f"block/{name}: Element {element.get('name')} ragt aus der Code-Form")
    icon = read_json(ASSETS / "models" / "item" / f"{name}.json")
    if icon and icon.get("parent") != f"propwash:block/{name}":
        error(f"models/item/{name}.json: muss auf propwash:block/{name} aufbauen")
    check_item_definition(name)


def check_slot_pixels(img, where, slots, icon_slots=()):
    for name, (sx, sy) in slots.items():
        probes = ((sx, sy), (sx + 15, sy + 15)) if name in icon_slots else ((sx, sy), (sx + 15, sy + 15), (sx + 8, sy + 8))
        for x, y in probes:
            if img.getpixel((x, y)) != GUI_SLOT:
                error(f"{where}: Slot {name} bei ({sx},{sy}) nicht als Slot-Fläche gezeichnet (Pixel {x},{y})")
                break


def check_panel_bounds(img, where, width, height):
    if img.getpixel((0, 0))[3] != 0:
        error(f"{where}: Ecke (0,0) sollte transparent sein")
    for x, y in ((width, 0), (0, height), (width + 5, height + 5), (255, 255)):
        if img.getpixel((x, y))[3] != 0:
            error(f"{where}: außerhalb {width}×{height} muss die Textur leer sein (Pixel {x},{y})")
            return


def check_menus():
    charger_gui = png(ASSETS / "textures" / "gui" / "battery_charger.png", (menus.TEX_SIZE, menus.TEX_SIZE))
    if charger_gui is not None:
        where = "gui/battery_charger.png"
        check_panel_bounds(charger_gui, where, menus.CHARGER_W, menus.CHARGER_H)
        slots = {f"charger_{i}": (x, menus.CHARGER_SLOT_Y) for i, x in enumerate(menus.CHARGER_SLOTS)}
        slots.update(menus.inventory_slots(menus.CHARGER_INVENTORY, menus.CHARGER_HOTBAR_Y))
        check_slot_pixels(charger_gui, where, slots)
        for x in menus.CHARGER_SLOTS:
            fx0, fy0, fx1, fy1 = menus.charger_fill(x)
            cx0, cy0, cx1, cy1 = menus.charger_channel(x)
            inner = {charger_gui.getpixel((px, py)) for px in range(fx0, fx1) for py in range(fy0, fy1)}
            if inner != {GUI_CHANNEL}:
                error(f"{where}: Balkenrinne bei x={x} innen nicht leer")
            edge = {charger_gui.getpixel((px, cy0)) for px in range(cx0, cx1)} | {charger_gui.getpixel((cx0, py)) for py in range(cy0, cy1)}
            if edge != {GUI_CHANNEL_EDGE}:
                error(f"{where}: Balkenrinne bei x={x} ohne 1-px-Rahmen")
            bx0, by0, _, _ = menus.charger_bay(x)
            if charger_gui.getpixel((bx0, by0)) == charger_gui.getpixel((bx0 - 1, by0)):
                error(f"{where}: Ladeschacht-Rahmen bei x={x} fehlt")
    service_gui = png(ASSETS / "textures" / "gui" / "drone_service.png", (menus.TEX_SIZE, menus.TEX_SIZE))
    if service_gui is not None:
        where = "gui/drone_service.png"
        check_panel_bounds(service_gui, where, menus.SERVICE_W, menus.SERVICE_H)
        slots = {"battery": menus.SERVICE_SLOT}
        slots.update({f"prop_{key}": pos for key, pos in menus.SERVICE_PROP_SLOTS.items()})
        slots.update(menus.inventory_slots(menus.SERVICE_INVENTORY, menus.SERVICE_HOTBAR_Y))
        check_slot_pixels(service_gui, where, slots)
        sx, sy = menus.SERVICE_SLOT
        if service_gui.getpixel((sx - 3, sy - 3))[:3] != AMBER:
            error(f"{where}: Akku-Slot ohne Amber-Ring")
        x0, y0, _, _ = menus.SERVICE_INFO
        if service_gui.getpixel((x0 + 5, y0 + 1))[:3] != AMBER:
            error(f"{where}: Info-Feld ohne Amber-Linie oben")
        cx0, cy0, _, cy1 = menus.SERVICE_CAMERA
        if service_gui.getpixel((cx0 + 5, cy0 + 1))[:3] != AMBER:
            error(f"{where}: Kamera-Feld ohne Amber-Linie oben")
        if cy1 >= menus.SERVICE_LABEL[1] or cy0 <= menus.SERVICE_HINT_Y + 8:
            error(f"{where}: Kamera-Feld überlappt Hinweis oder Inventar-Titel")
        fl = menus.SERVICE_PROP_SLOTS["front_left"]
        fr = menus.SERVICE_PROP_SLOTS["front_right"]
        rl = menus.SERVICE_PROP_SLOTS["rear_left"]
        if fl[1] != fr[1] or fl[0] != rl[0] or fl[1] >= rl[1] or fl[0] >= fr[0]:
            error(f"{where}: Prop-Slots nicht wie die Drohne von oben angeordnet")


def check_icons():
    icon = png(ASSETS / "icon.png", (256, 256))
    pack = png(RES / "pack.png", (128, 128))
    for name, img in (("icon.png", icon), ("pack.png", pack)):
        if img is not None:
            w, h = img.size
            if img.getpixel((w // 2, h // 2))[3] != 255:
                error(f"{name}: Bildmitte nicht deckend")


def check_owned_json():
    owned = [
        *(ASSETS / "items").glob("goggles_*.json"), *(ASSETS / "items").glob("gate_*.json"),
        ASSETS / "items" / "course_tool.json", ASSETS / "items" / "drone_workbench.json",
        *(ASSETS / "models" / "gate").glob("*.json"), *(ASSETS / "models" / "gate" / "piece").glob("*.json"),
        *(ASSETS / "items" / "race").glob("gate_piece_*.json"), *(ASSETS / "models" / "item").glob("goggles_*.json"),
        *(ASSETS / "models" / "item").glob("gate_*.json"), ASSETS / "models" / "item" / "course_tool.json",
        ASSETS / "models" / "block" / "drone_workbench.json", ASSETS / "blockstates" / "drone_workbench.json",
        ASSETS / "items" / "remote.json", ASSETS / "items" / "battery_charger.json", ASSETS / "blockstates" / "battery_charger.json",
        ASSETS / "models" / "item" / "battery_charger.json",
        ASSETS / "items" / "signal_repeater.json", ASSETS / "blockstates" / "signal_repeater.json",
        ASSETS / "models" / "item" / "signal_repeater.json", ASSETS / "models" / "block" / "signal_repeater.json",
        *(ASSETS / "models" / "item").glob("remote*.json"), *(ASSETS / "models" / "block").glob("battery_charger_*.json"),
    ]
    for path in owned:
        read_json(path)
    expected_models = {f"{s}_{p}.json" for s in GATE_SIZES for p in ("frame", "lights", "start_frame", "banner")}
    expected_models |= {f"{s}_{p}{gen.gate_variant_suffix(b)}.json" for s in GATE_SIZES for p in ("frame", "lights", "start_frame")
                        for b in ("legs", "stacked", "mount")}
    expected_models |= {"mount_plate.json", "hanger.json"}
    present = {p.name for p in (ASSETS / "models" / "gate").glob("*.json")}
    if present != expected_models:
        error(f"models/gate: erwartet {sorted(expected_models)}, gefunden {sorted(present)}")


def main():
    if VANILLA is None:
        print("Hinweis: .ref/vanilla nicht gefunden, Vanilla-Eltern werden nicht geprüft")
    check_owned_json()
    check_workbench()
    check_goggles()
    check_items()
    check_gates()
    check_course()
    check_gui()
    check_remote()
    check_charger()
    check_repeater()
    check_menus()
    check_icons()
    if errors:
        for e in errors:
            print("FEHLER:", e)
        print(f"WORLD ASSETS INVALID ({len(errors)} Fehler)")
        sys.exit(1)
    print("WORLD ASSETS OK")


if __name__ == "__main__":
    main()
