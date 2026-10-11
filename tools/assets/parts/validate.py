import json
import math
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))

import batteries
import kit
import layout
import paintable

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "common/src/main/resources/assets/propwash"
SOURCES = ROOT / "assets-src/parts"

CATEGORIES = {
    "frame": layout.FRAME_KEYS,
    "stack": layout.STACK_KEYS,
    "motor": layout.MOTOR_KEYS,
    "prop": layout.PROP_KEYS,
    "battery": layout.BATTERY_KEYS,
    "video": layout.VIDEO_KEYS,
    "accessory": layout.ACCESSORY_KEYS,
}
DISPLAY_KEYS = {"gui", "ground", "fixed", "on_shelf", "thirdperson_righthand", "firstperson_righthand", "head"}
FACE_KEYS = {"north", "south", "east", "west", "up", "down"}

errors = []


def fail(msg):
    errors.append(msg)


def load(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing file {path.relative_to(ROOT)}")
    except json.JSONDecodeError as e:
        fail(f"invalid JSON {path.relative_to(ROOT)}: {e}")
    return None


def resource(identifier, kind, ext):
    ns, path = identifier.split(":", 1)
    return ROOT / f"common/src/main/resources/assets/{ns}/{kind}/{path}.{ext}"


def check_texture(sprite, where):
    tex_id = sprite["sprite"] if isinstance(sprite, dict) else sprite
    if not isinstance(tex_id, str) or ":" not in tex_id:
        fail(f"{where}: bad texture reference {sprite!r}")
        return None
    if not tex_id.split(":", 1)[1].startswith("item/"):
        fail(f"{where}: texture {tex_id} is outside the items atlas (textures/item/**)")
    path = resource(tex_id, "textures", "png")
    if not path.exists():
        fail(f"{where}: texture {tex_id} does not exist")
        return None
    img = Image.open(path)
    w, h = img.size
    if (w, h) not in ((32, 32), (64, 64)):
        fail(f"{where}: texture {tex_id} is {w}x{h}, expected 32x32 or 64x64")
    if img.mode != "RGBA":
        fail(f"{where}: texture {tex_id} is not RGBA")
    return img


def check_model(model_id, required_display=True):
    path = resource(model_id, "models", "json")
    data = load(path)
    if data is None:
        return None
    where = str(path.relative_to(ROOT))
    textures = data.get("textures", {})
    for key, sprite in textures.items():
        check_texture(sprite, where)
    elements = data.get("elements")
    if not elements:
        fail(f"{where}: no elements")
        return data
    for i, el in enumerate(elements):
        for key in ("from", "to"):
            v = el.get(key)
            if not isinstance(v, list) or len(v) != 3:
                fail(f"{where}#{i}: bad {key}")
                continue
            for c in v:
                if not (-16.0 <= c <= 32.0):
                    fail(f"{where}#{i}: {key} {v} outside -16..32")
        if any(el["to"][k] < el["from"][k] for k in range(3)):
            fail(f"{where}#{i}: to < from")
        rot = el.get("rotation")
        if rot is not None:
            if "origin" not in rot:
                fail(f"{where}#{i}: rotation without origin")
            if "axis" in rot:
                if rot["axis"] not in ("x", "y", "z") or "angle" not in rot:
                    fail(f"{where}#{i}: bad single-axis rotation")
            elif not any(k in rot for k in ("x", "y", "z")):
                fail(f"{where}#{i}: rotation without angles")
        faces = el.get("faces", {})
        if not faces:
            fail(f"{where}#{i}: element without faces")
        for d, face in faces.items():
            if d not in FACE_KEYS:
                fail(f"{where}#{i}: unknown face {d}")
            ref = face.get("texture", "")
            if not ref.startswith("#") or ref[1:] not in textures:
                fail(f"{where}#{i}.{d}: texture {ref} not defined")
            uv = face.get("uv")
            if not uv or len(uv) != 4 or any(not (0.0 <= u <= 16.0) for u in uv):
                fail(f"{where}#{i}.{d}: bad uv {uv}")
    display = data.get("display", {})
    if required_display:
        missing = DISPLAY_KEYS - set(display)
        if missing:
            fail(f"{where}: display lacks {sorted(missing)}")
    for ctx, t in display.items():
        for k in ("rotation", "translation", "scale"):
            if k not in t or len(t[k]) != 3:
                fail(f"{where}: display.{ctx}.{k} missing")
        if any(abs(v) > 80 for v in t.get("translation", [])):
            fail(f"{where}: display.{ctx}.translation exceeds 80")
        if any(abs(v) > 4 for v in t.get("scale", [])):
            fail(f"{where}: display.{ctx}.scale exceeds 4")
    return data


def collect_models(node, out):
    if isinstance(node, dict):
        if node.get("type") == "minecraft:model" and "model" in node:
            out.append(node["model"])
            t = node.get("transformation")
            if t is not None and set(t) != {"translation", "left_rotation", "scale", "right_rotation"}:
                fail(f"transformation of {node['model']} must have all four keys")
        if node.get("type") == "minecraft:special" and "base" in node:
            out.append(node["base"])
        for v in node.values():
            collect_models(v, out)
    elif isinstance(node, list):
        for v in node:
            collect_models(v, out)


def check_item(key, expect):
    path = ASSETS / f"items/{key}.json"
    data = load(path)
    if data is None:
        return
    models = []
    collect_models(data.get("model"), models)
    for mid in expect:
        if mid not in models:
            fail(f"items/{key}.json does not reference {mid}")
    for mid in set(models):
        if not resource(mid, "models", "json").exists():
            fail(f"items/{key}.json references missing model {mid}")


def check_drone_item(data):
    if data is None:
        return
    node = data.get("model", {})
    if node.get("type") != "minecraft:select" or node.get("property") != "minecraft:display_context":
        fail("items/drone.json must select on minecraft:display_context")
        return
    cases = {case.get("when"): case.get("model", {}) for case in node.get("cases", [])}
    plain = node.get("fallback", {})
    for name, special, mode in (("fixed", cases.get("fixed"), "wall"), ("ground", cases.get("ground"), "ground"),
                                ("fallback", plain, None)):
        if not special or special.get("type") != "minecraft:special":
            fail(f"items/drone.json {name} must be a minecraft:special model")
            continue
        renderer = special.get("model", {})
        if renderer.get("type") != "propwash:drone_build":
            fail(f"items/drone.json {name} must use the special renderer propwash:drone_build")
        for flag in ("wall", "ground"):
            if bool(renderer.get(flag, False)) != (mode == flag):
                fail(f"items/drone.json {name}: \"{flag}\" must be {mode == flag}")
    ground = cases.get("ground", {})
    if ground.get("base") != "propwash:item/drone_ground" or "transformation" in ground:
        fail("items/drone.json ground must use the base propwash:item/drone_ground without pre-scale")
    base = load(resource("propwash:item/drone_ground", "models", "json")) or {}
    if base.get("display", {}).get("ground") != {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}:
        fail("item/drone_ground: display.ground must be the identity (the renderer sizes the drone on the ground)")


def check_wall_display():
    expected = kit.wall_display()
    for model_id in ("propwash:item/drone", "propwash:item/drone_paint"):
        data = load(resource(model_id, "models", "json"))
        if data and data.get("display", {}).get("fixed") != expected:
            fail(f"{model_id}: display.fixed must be {expected}")
    for category in kit.WALL_CATEGORIES:
        for path in sorted((ASSETS / "models/part" / category).glob("*.json")):
            display = (load(path) or {}).get("display", {})
            fixed = display.get("fixed", {})
            if fixed.get("rotation") != list(kit.WALL_ROTATION):
                fail(f"part/{category}/{path.stem}: display.fixed must hang on the wall, rotation {fixed.get('rotation')}")
            shelf = display.get("on_shelf", {})
            if shelf.get("rotation") != list(kit.SHELF_ROTATION):
                fail(f"part/{category}/{path.stem}: display.on_shelf must tilt towards the viewer, rotation {shelf.get('rotation')}")


def check_layout():
    data = load(ASSETS / "drone_layout.json")
    if data is None:
        return
    frames = data.get("frames", {})
    for key in layout.FRAME_KEYS:
        f = frames.get(key)
        if f is None:
            fail(f"drone_layout.json lacks frame {key}")
            continue
        motors = f.get("motors", [])
        if len(motors) != 4 or any(len(m) != 3 for m in motors):
            fail(f"drone_layout.{key}.motors must be 4 points")
            continue
        fl, fr, rl, rr = motors
        if not (fl[0] < 0 and fl[2] < 0 and fr[0] > 0 and fr[2] < 0 and rl[0] < 0 and rl[2] > 0 and rr[0] > 0 and rr[2] > 0):
            fail(f"drone_layout.{key}.motors order must be FL, FR, RL, RR")
        diag = math.dist(fl, rr)
        if abs(diag - layout.WHEELBASE_MM[key]) > 0.5:
            fail(f"drone_layout.{key}: diagonal {diag:.1f} mm != wheelbase {layout.WHEELBASE_MM[key]} mm")
        if f.get("motor_spin") != ["cw", "ccw", "ccw", "cw"]:
            fail(f"drone_layout.{key}.motor_spin must be props-in")
        for field in ("stack", "battery", "video"):
            if len(f.get(field, [])) != 3:
                fail(f"drone_layout.{key}.{field} must be a 3-vector")
        for field in ("prop_height", "camera_tilt_default"):
            if not isinstance(f.get(field), (int, float)):
                fail(f"drone_layout.{key}.{field} must be a number")
        accessories = f.get("accessories", {})
        if not isinstance(accessories, dict) or not accessories:
            fail(f"drone_layout.{key}.accessories must name at least one mount")
            continue
        for mount, anchor in accessories.items():
            if mount not in layout.MOUNT_KEYS:
                fail(f"drone_layout.{key}.accessories: unknown mount {mount}")
            if len(anchor.get("position", [])) != 3:
                fail(f"drone_layout.{key}.accessories.{mount}.position must be a 3-vector")
            tilt = anchor.get("tilt")
            if not isinstance(tilt, (int, float)) or not -45.0 <= tilt <= 90.0:
                fail(f"drone_layout.{key}.accessories.{mount}.tilt must be -45..90")
            if mount == "bottom" and not isinstance(anchor.get("length"), (int, float)):
                fail(f"drone_layout.{key}.accessories.bottom.length must be a number")
    extra = set(frames) - set(layout.FRAME_KEYS)
    if extra:
        fail(f"drone_layout.json has unknown frames {sorted(extra)}")


def check_prop_clearance():
    data = json.loads((ASSETS / "drone_layout.json").read_text())
    for key in layout.FRAME_KEYS:
        motors = data["frames"][key]["motors"]
        prop = layout.FRAMES[key]["preset"][2]
        from props import PROPS
        radius = PROPS[prop]["radius"]
        gap = math.dist(motors[0], motors[1]) - 2 * radius
        if gap <= 0:
            fail(f"{key}: props {prop} overlap (gap {gap:.1f} mm)")

CAMERA_BATTERIES = {
    "cinewhoop_3": ["b4s_650"],
    "freestyle_5": ["b6s_1300", "b6s_1100", "b4s_650"],
    "race_5": ["b6s_1100", "b6s_1300", "b4s_650"],
    "long_range_7": ["b6s_4000_lion", "b6s_1300"],
}
CAMERA_CLEARANCE_MM = 1.0
BATTERY_BODY = ("pack", "strap", "buckle", "cell", "cell_cap", "shrink", "label_wrap")


def element_points(el, steps=4):
    lo, hi = el["from"], el["to"]
    pts = []
    for i in range(steps + 1):
        for j in range(steps + 1):
            for k in range(steps + 1):
                pts.append([lo[0] + (hi[0] - lo[0]) * i / steps, lo[1] + (hi[1] - lo[1]) * j / steps,
                            lo[2] + (hi[2] - lo[2]) * k / steps])
    rot = el.get("rotation")
    if not rot:
        return pts
    import kit
    if "axis" in rot:
        angles = {"x": 0.0, "y": 0.0, "z": 0.0}
        angles[rot["axis"]] = rot["angle"]
        matrix = kit.element_matrix(angles["x"], angles["y"], angles["z"])
    else:
        matrix = kit.element_matrix(rot.get("x", 0.0), rot.get("y", 0.0), rot.get("z", 0.0))
    origin = rot["origin"]
    out = []
    for p in pts:
        d = [p[a] - origin[a] for a in range(3)]
        out.append([sum(matrix[r][c] * d[c] for c in range(3)) + origin[r] for r in range(3)])
    return out


def check_accessory_clearance():
    data = json.loads((ASSETS / "drone_layout.json").read_text())
    for frame, batteries in CAMERA_BATTERIES.items():
        anchor = data["frames"][frame]["accessories"].get("top")
        if anchor is None:
            continue
        ax, ay, az = anchor["position"]
        tilt = math.radians(anchor["tilt"])
        c, s = math.cos(tilt), math.sin(tilt)
        boxes = []
        for key in batteries:
            path, (bx, by, bz) = layout.battery_placement(frame, key, model_exists, model_top)
            model = load(resource(f"propwash:part/{path}", "models", "json"))
            for el in (model or {}).get("elements", []):
                if el.get("name") in BATTERY_BODY:
                    lo = [(v - 8) * 8 for v in el["from"]]
                    hi = [(v - 8) * 8 for v in el["to"]]
                    boxes.append((key, [lo[0] + bx, lo[1] + by, lo[2] + bz], [hi[0] + bx, hi[1] + by, hi[2] + bz]))
        for cam in ("action_cam_light", "action_cam_heavy", "action_cam_flashback"):
            model = load(resource(f"propwash:part/accessory/{cam}", "models", "json"))
            worst = None
            for el in (model or {}).get("elements", []):
                for p in element_points(el):
                    x, y, z = [(v - 8) * 8 for v in p]
                    fx, fy, fz = ax + x, ay + y * c - z * s, az + y * s + z * c
                    for key, lo, hi in boxes:
                        m = CAMERA_CLEARANCE_MM
                        if lo[0] - m < fx < hi[0] + m and lo[1] - m < fy < hi[1] + m and lo[2] - m < fz < hi[2] + m:
                            worst = (key, el.get("name"), round(fz - lo[2], 1))
            if worst:
                fail(f"{frame}: {cam} overlaps battery {worst[0]} ({worst[1]}, {worst[2]} mm)")


def model_exists(path):
    return resource(f"propwash:part/{path}", "models", "json").exists()


def model_top(path):
    model = load(resource(f"propwash:part/{path}", "models", "json")) or {}
    tops = [(max(el["from"][1], el["to"][1]) - 8) * 8 for el in model.get("elements", [])]
    return max(tops) if tops else 0.0


def check_battery_mounts():
    data = json.loads((ASSETS / "drone_layout.json").read_text())
    for key in layout.BATTERY_KEYS:
        if not batteries.strapped(key):
            continue
        under = key + batteries.UNDER_SUFFIX
        check_model(f"propwash:part/battery/{under}", required_display=False)
        check_item(f"drone_render/battery/{under}", [f"propwash:part/battery/{under}"])
        check_model(f"propwash:part/battery/{under}{paintable.SUFFIX}", required_display=False)
        load(SOURCES / "battery" / f"{under}.bbmodel")
        model = load(resource(f"propwash:part/battery/{under}", "models", "json")) or {}
        pack = [el for el in model.get("elements", []) if el.get("name") == "pack"]
        if not pack or abs((max(pack[0]["from"][1], pack[0]["to"][1]) - 8) * 8) > 0.01:
            fail(f"battery/{under}: pack must hang from y = 0 (top of the pack)")
        if (ASSETS / f"items/{under}.json").exists():
            fail(f"items/{under}.json must not exist (under variants are drone_render only)")
    for frame in layout.FRAME_KEYS:
        definition = json.loads((ROOT / f"common/src/main/resources/data/propwash/propwash_parts/frame/{frame}.json").read_text())
        on_top = definition.get("battery", {}).get("on_top", True)
        if on_top == layout.battery_under(frame):
            fail(f"{frame}: battery_under in layout.py contradicts battery.on_top in the part definition")
        if layout.battery_under(frame):
            plate = definition["battery"]["plate_mm"]
            y = data["frames"][frame]["battery"][1]
            if abs(y - plate) > 1.0:
                fail(f"{frame}: battery hangs at {y} mm, the flight model uses plate_mm {plate} mm")


def check_sources():
    for category, keys in CATEGORIES.items():
        for key in keys:
            variants = [key] + ([key + "_ccw", key + "_blur"] if category == "prop" else [])
            for v in variants:
                path = SOURCES / category / f"{v}.bbmodel"
                data = load(path)
                if data is not None and not data.get("elements"):
                    fail(f"{path.relative_to(ROOT)}: no elements")
    load(SOURCES / "item/drone.bbmodel")


def check_paint():
    data = load(ASSETS / "drone_paint.json")
    if data is None:
        return
    for name, groups in data.get("models", {}).items():
        model = check_model(f"propwash:part/{name}_paint")
        item = load(ASSETS / f"items/drone_render/{name}_paint.json")
        tints = item.get("model", {}).get("tints", []) if item else []
        if len(tints) != len(groups):
            fail(f"items/drone_render/{name}_paint.json has {len(tints)} tints for {len(groups)} paint groups")
        used = set()
        for el in (model or {}).get("elements", []):
            for face in el.get("faces", {}).values():
                if "tintindex" in face:
                    used.add(face["tintindex"])
        if used and max(used) >= len(tints):
            fail(f"part/{name}_paint uses tintindex {max(used)} beyond {len(tints)} tints")
        if not used:
            fail(f"part/{name}_paint has no tinted faces")
        base = check_model(f"propwash:part/{name}")
        if base:
            for el in base.get("elements", []):
                if any("tintindex" in face for face in el.get("faces", {}).values()):
                    fail(f"part/{name} (original) must stay untinted")
                    break
    check_model("propwash:item/drone_paint")


def main():
    for category, keys in CATEGORIES.items():
        for key in keys:
            check_model(f"propwash:part/{category}/{key}")
            expect = [f"propwash:part/{category}/{key}"]
            if category == "prop":
                for v in ("_ccw", "_blur"):
                    check_model(f"propwash:part/prop/{key}{v}")
                    expect.append(f"propwash:part/prop/{key}{v}")
                blur = load(resource(f"propwash:part/prop/{key}_blur", "models", "json"))
                if blur and not (isinstance(blur["textures"].get("0"), dict)
                                 and blur["textures"]["0"].get("force_translucent")):
                    fail(f"prop/{key}_blur: disc texture must be force_translucent")
            check_item(key, expect)
    for base in layout.ACCESSORY_BASES.values():
        check_model(f"propwash:part/accessory/{base}")
        load(SOURCES / "accessory" / f"{base}.bbmodel")
    check_model("propwash:item/drone")
    check_item("drone", ["propwash:item/drone"])
    check_drone_item(load(ASSETS / "items/drone.json"))
    check_wall_display()
    check_layout()
    check_paint()
    check_prop_clearance()
    check_accessory_clearance()
    check_battery_mounts()
    check_sources()
    for path in sorted((ASSETS / "items").glob("*.json")):
        load(path)
    if errors:
        for e in errors:
            print("FAIL", e)
        print(f"{len(errors)} problem(s)")
        return 1
    print("assets-parts: all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
