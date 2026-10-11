import shutil
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))

import accessories
import batteries
import frames
import kit
import layout
import motors
import paintable
import props
import stacks
import video

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "common/src/main/resources/assets/propwash"
SOURCES = ROOT / "assets-src/parts"


def texture_file(sprite_id):
    ns, path = sprite_id.split(":", 1)
    return ROOT / f"common/src/main/resources/assets/{ns}/textures/{path}.png"


def model_file(model_id):
    ns, path = model_id.split(":", 1)
    return ROOT / f"common/src/main/resources/assets/{ns}/models/{path}.json"


def write_model(model, display, bb_name):
    kit.dump_json(model_file(model.model_id), model.to_json(display))
    kit.dump_json(SOURCES / f"{bb_name}.bbmodel", model.to_bbmodel(display))


def write_textures(model):
    sprite, img = model.texture_map()["0"]
    kit.save_png(texture_file(sprite), img)


def render_item(model, tints=None):
    return {"model": kit.model_node(model.model_id, tints)}


def write_render_item(model, tints=None):
    kit.dump_json(ASSETS / f"items/drone_render/{model.category}/{model.key}.json", render_item(model, tints))


def write_paint(model, display, variants, groups_by_model, shared=None):
    paint = paintable.variant(model, shared)
    if paint is None:
        return None
    for key, (sprite, img) in paint.texture_map().items():
        if shared and key in shared:
            continue
        kit.save_png(texture_file(sprite), img)
    kit.dump_json(model_file(paint.model_id), paint.to_json(display))
    write_render_item(paint, paintable.default_tints(paint.groups))
    groups_by_model[f"{model.category}/{model.key}"] = paint.groups
    variants[model.key] = paint
    return paint


def clean():
    for path in (ASSETS / "models/part", ASSETS / "textures/item/part", SOURCES):
        if path.exists():
            shutil.rmtree(path)
    for path in (ASSETS / "items/drone_render").glob(f"*/*{paintable.SUFFIX}.json"):
        path.unlink()


def build():
    clean()
    results = {}
    variants = {}
    groups_by_model = {}
    simple = []
    simple += frames.build_all()
    simple += stacks.build_all()
    simple += motors.build_all()
    simple += batteries.build_all()
    simple += video.build_all()
    simple += accessories.build_all()
    for m in simple:
        m.pack()
        display, pre = kit.compute_display(m)
        write_textures(m)
        write_model(m, display, f"{m.category}/{m.key}")
        kit.dump_json(ASSETS / f"items/{m.key}.json", kit.item_definition(m.model_id, pre))
        write_render_item(m)
        write_paint(m, display, variants, groups_by_model)
        results[m.key] = m
    for m in batteries.build_under():
        m.pack()
        display, _ = kit.compute_display(m)
        write_textures(m)
        write_model(m, display, f"{m.category}/{m.key}")
        write_render_item(m)
        write_paint(m, display, variants, groups_by_model)
        results[m.key] = m
    damaged = props.build_damaged()
    wall_extra = {}
    for key, cw, _ in damaged:
        wall_extra.setdefault(key, []).append(cw)
    for cw, ccw, blur in props.build_all():
        cw.pack()
        ccw.pack()
        blur.pack()
        props.attach_hub(blur, cw)
        blur.extra_textures = {"0": (blur.texture_id, blur.image), "1": (cw.texture_id, cw.image)}
        display, pre = kit.compute_display(cw, wall_extra=wall_extra.get(cw.key, ()))
        write_textures(cw)
        if np.array_equal(cw.image, ccw.image):
            ccw.extra_textures = {"0": (cw.texture_id, cw.image)}
        else:
            write_textures(ccw)
        kit.save_png(texture_file(blur.texture_id), blur.image)
        write_model(cw, display, f"prop/{cw.key}")
        write_model(ccw, display, f"prop/{ccw.key}")
        write_model(blur, display, f"prop/{blur.key}")
        extra = [("ccw", ccw.model_id), ("blur", blur.model_id), ("ccw_blur", blur.model_id)]
        stages = [(float(i + 1), f"{cw.model_id}_{damage}") for i, damage in enumerate(props.DAMAGE)]
        kit.dump_json(ASSETS / f"items/{cw.key}.json",
                      kit.item_definition(cw.model_id, pre, extra, stages=(props.DAMAGE_PROPERTY, stages)))
        for model in (cw, ccw, blur):
            write_render_item(model)
        cw_paint = write_paint(cw, display, variants, groups_by_model)
        cw_texture = cw_paint.texture_map()["0"]
        if ccw.texture_map()["0"][0] == cw.texture_id:
            write_paint(ccw, display, variants, groups_by_model, shared={"0": cw_texture})
        else:
            write_paint(ccw, display, variants, groups_by_model)
        write_paint(blur, display, variants, groups_by_model, shared={"1": cw_texture})
        results[cw.key] = cw
        results[ccw.key] = ccw
        results[blur.key] = blur
    for key, cw, ccw in damaged:
        base = results[key]
        display, _ = kit.compute_display(base, wall_extra=wall_extra.get(key, ()))
        for model in (cw, ccw):
            model.pack()
            write_textures(model)
            write_model(model, display, f"prop/{model.key}")
            write_render_item(model)
            write_paint(model, display, variants, groups_by_model)
            results[model.key] = model
    for base in accessories.build_bases():
        base.pack()
        display, _ = kit.compute_display(base)
        write_textures(base)
        write_model(base, display, f"{base.category}/{base.key}")
        write_render_item(base)
        write_paint(base, display, variants, groups_by_model)
        results[base.key] = base
    kit.dump_json(ASSETS / "drone_layout.json", layout.layout_json())
    problems = paintable.check_references(groups_by_model)
    if problems:
        raise RuntimeError("paint references too low: " + "; ".join(problems))
    kit.dump_json(ASSETS / "drone_paint.json", paintable.summary_json(groups_by_model))
    drone = build_drone_icon(results)
    display, pre = kit.compute_display(drone, gui_rotation=(42, 205, 0), gui_fill=15.8)
    display["fixed"] = kit.wall_display()
    kit.dump_json(model_file(drone.model_id), drone.to_json(display))
    kit.dump_json(SOURCES / "item/drone.bbmodel", drone.to_bbmodel(display, name="drone"))
    painted, _ = build_painted_icon(results, variants)
    kit.dump_json(model_file(painted.model_id), painted.to_json(display))
    kit.dump_json(model_file(GROUND_BASE), ground_base(drone))
    kit.dump_json(ASSETS / "items/drone.json", icon_definition(drone.model_id, pre))
    return results, drone


GROUND_BASE = "propwash:item/drone_ground"


def ground_base(drone):
    particle = drone.texture_map()["0"][0]
    return {
        "textures": {"particle": particle},
        "display": {"ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
    }


def assembly_parts(results, frame_key, blur=False):
    f = layout.FRAMES[frame_key]
    stack, motor, prop, vid, bat = f["preset"]
    parts = [(results[frame_key], [0.0, 0.0, 0.0], 0.0, [0.0, 0.0, 0.0])]
    parts.append((results[stack], f["stack"], 0.0, [0.0, 0.0, 0.0]))
    battery, offset = battery_mount(results, frame_key, bat)
    parts.append((battery, offset, 0.0, [0.0, 0.0, 0.0]))
    parts.append((results[vid], f["video"], f["camera_tilt_default"], [0.0, 0.0, 0.0]))
    for pos_mm, spin in zip(layout.motor_positions(frame_key), layout.MOTOR_SPIN):
        parts.append((results[motor], pos_mm, 0.0, [0.0, 0.0, 0.0]))
        prop_key = prop + ("_blur" if blur else "_ccw" if spin == "ccw" else "")
        top = [pos_mm[0], pos_mm[1] + f["prop_height"], pos_mm[2]]
        parts.append((results[prop_key], top, 0.0, [0.0, 0.0, 0.0]))
    return parts


def model_top_mm(model):
    return (model.bounds()[1][1] - kit.ANCHOR) * kit.UNIT_MM


def battery_mount(results, frame_key, bat):
    anchor = layout.FRAMES[frame_key]["battery"]
    if not layout.battery_under(frame_key):
        return results[bat], anchor
    under = results.get(bat + batteries.UNDER_SUFFIX)
    if under is not None:
        return under, anchor
    base = results[bat]
    return base, [anchor[0], round(anchor[1] - model_top_mm(base), 4), anchor[2]]


def build_drone_icon(results):
    parts = assembly_parts(results, "freestyle_5")
    return kit.merge("item", "drone", parts, model_id="propwash:item/drone")


def build_painted_icon(results, variants):
    parts = assembly_parts(results, "freestyle_5")
    painted = []
    for model, offset, tilt, pivot in parts:
        painted.append((variants.get(model.key, model), offset, tilt, pivot))
    icon = paintable.IconTints()
    prop_positions = {}
    for index, (model, _, _, _) in enumerate(painted):
        if model.category == "prop":
            prop_positions[index] = len(prop_positions)

    def remap(index, tint):
        channel, base = painted[index][0].groups[tint]
        slot = paintable.slot_for(channel, prop_positions.get(index, 0))
        return icon.index(slot, channel, base)

    merged = kit.merge("item", "drone_paint", painted, model_id="propwash:item/drone_paint", tint_map=remap)
    return merged, icon.defaults


def icon_definition(model_id, pre_scale):
    scaled = kit.item_definition(model_id, pre_scale)["model"]["fallback"]
    nodes = []
    for renderer in ({"type": "propwash:drone_build", "wall": True}, {"type": "propwash:drone_build"}):
        node = {"type": "minecraft:special", "base": model_id, "model": renderer}
        if "transformation" in scaled:
            node["transformation"] = scaled["transformation"]
        nodes.append(node)
    wall, plain = nodes
    ground = {"type": "minecraft:special", "base": GROUND_BASE, "model": {"type": "propwash:drone_build", "ground": True}}
    return {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                      "cases": [{"when": "fixed", "model": wall}, {"when": "ground", "model": ground}], "fallback": plain}}


if __name__ == "__main__":
    res, _ = build()
    print(f"generated {len(res)} part models + drone icon")
