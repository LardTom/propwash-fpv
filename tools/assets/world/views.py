"""Preview helpers that apply the vanilla display-context transforms (GUI, first person, third person).

First person: FirstPersonHandsAndItemsRenderer#applyItemArmTransform (0.56, -0.52, -0.72) + ItemTransform,
HUD FOV 70 (Camera.BASE_HUD_FOV). Third person: HumanoidModel arm pivot (-5, 2, 0), ITEM arm pose xRot = -PI/10,
ItemInHandLayer X -90, Y 180, translate (1, 2, -10) / 16, player scale 0.9375, render scale (-1, -1, 1).
The remote in the main hand (other hand item hidden) or in the off hand with a free main hand uses the two-handed hold of drone.client.RemoteHoldPose instead: both arms xRot -50 deg,
yRot -+20 deg, the remote drawn in context NONE between both hands (midpoint of the hand points (-+1, 8, 0) px in the arm
frame), Z 180, X -55 (face towards the pilot, far edge with the antenna up), scale 0.6, grip anchor (8, 3.5, 8.5) px.
"""

import json

from PIL import Image, ImageDraw

from assetlib import REPO

from modelrender import (camera_orbit, colour_quads, load_model, m_item_transform, m_rot, m_scale, m_translate,
                         mat_chain, model_quads, render_camera, transform_quads, xform)


def vanilla_model(ref):
    path = ref.split(":", 1)[-1]
    for base in (REPO, *REPO.parents):
        candidate = base / ".ref" / "vanilla" / "assets" / "minecraft" / "models" / f"{path}.json"
        if candidate.exists():
            return json.loads(candidate.read_text(encoding="utf-8"))
    return {}


def display(model_ref, context):
    ref = model_ref
    for _ in range(8):
        model = load_model(ref) if ref.startswith("propwash:") else vanilla_model(ref)
        if context in model.get("display", {}):
            return model["display"][context]
        ref = model.get("parent")
        if not ref:
            break
    return {}


def gui_render(model_ref, size, background=None, geometry_ref=None):
    quads = model_quads(load_model(geometry_ref or model_ref))
    view = m_item_transform(display(model_ref, "gui"))
    img = render_camera(transform_quads(quads, view), size, size, ortho_scale=size, supersample=4)
    if background is not None:
        out = Image.new("RGBA", (size, size), background)
        out.alpha_composite(img)
        return out
    return img


def first_person_scene(width, height):
    img = Image.new("RGBA", (width, height), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    horizon = int(height * 0.46)
    for y in range(height):
        if y < horizon:
            t = y / horizon
            c = (int(120 + 40 * t), int(165 + 30 * t), int(230 - 10 * t), 255)
        else:
            t = (y - horizon) / (height - horizon)
            c = (int(88 - 30 * t), int(140 - 50 * t), int(60 - 20 * t), 255)
        d.line((0, y, width, y), fill=c)
    cx, cy = width // 2, height // 2
    d.line((cx - 6, cy, cx + 6, cy), fill=(240, 240, 240, 255), width=1)
    d.line((cx, cy - 6, cx, cy + 6), fill=(240, 240, 240, 255), width=1)
    return img


def first_person(model_ref, width, height, left_hand=False, extra_quads=None, guides=True):
    context = "firstperson_lefthand" if left_hand else "firstperson_righthand"
    quads = model_quads(load_model(model_ref)) + (extra_quads or [])
    side = -1 if left_hand else 1
    view = mat_chain(m_translate(side * 0.56, -0.52, -0.72), m_item_transform(display(model_ref, context), left_hand))
    img = render_camera(transform_quads(quads, view), width, height, perspective_fov=70.0, base=first_person_scene(width, height))
    if guides:
        d = ImageDraw.Draw(img)
        for frac in (0.35,):
            d.line((width - 4 - frac * width, height - 6, width - 4, height - 6), fill=(255, 187, 0, 255), width=2)
    return img


SKIN = (196, 150, 118, 255)
SHIRT = (60, 160, 170, 255)
PANTS = (60, 60, 150, 255)
HAIR = (92, 62, 40, 255)


HOLD_POSE_ITEMS = {"propwash:item/remote"}
HOLD_PITCH = -40.0
HOLD_YAW = 23.0
HOLD_TILT = 55.0
HOLD_SCALE = 0.6
HOLD_HAND_Y = 9.5
HOLD_GRIP = (8.0, 3.5, 8.5)


def _arm_quads(right):
    lo, hi = ((-3, 1) if right else (-1, 3))
    return colour_quads(SHIRT, (lo, -2, -2), (hi, 6, 2)) + colour_quads(SKIN, (lo, 6, -2), (hi, 10, 2))


def _body_quads():
    body = colour_quads(SKIN, (-4, -8, -4), (4, 0, 4))
    body += colour_quads(HAIR, (-4.05, -8.05, -4.05), (4.05, -6.5, 4.05))
    body += colour_quads(SHIRT, (-4, 0, -2), (4, 12, 2))
    body += colour_quads(PANTS, (-3.9, 12, -2), (0.1, 24, 2))
    body += colour_quads(PANTS, (-0.1, 12, -2), (3.9, 24, 2))
    return body


def _two_handed(model_ref, px):
    arms = []
    hands = []
    for right in (True, False):
        pose = mat_chain(m_translate((-5 if right else 5) * px, 2 * px, 0), m_rot("y", -HOLD_YAW if right else HOLD_YAW),
                         m_rot("x", HOLD_PITCH))
        arms += transform_quads(_arm_quads(right), mat_chain(pose, m_scale(px)))
        hands.append(xform(pose, ((-1 if right else 1) * px, HOLD_HAND_Y * px, 0)))
    mid = tuple((hands[0][i] + hands[1][i]) / 2 for i in range(3))
    remote = mat_chain(m_translate(*mid), m_rot("z", 180), m_rot("x", -HOLD_TILT), m_scale(HOLD_SCALE),
                       m_translate(0, (8 - HOLD_GRIP[1]) * px, (8 - HOLD_GRIP[2]) * px), m_translate(-0.5, -0.5, -0.5), m_scale(px))
    return arms, transform_quads(model_quads(load_model(model_ref)), remote)


def player_quads(model_ref, arm_pitch=-18.0, with_body=True, one_handed=False):
    px = 1 / 16.0
    quads = transform_quads(_body_quads(), m_scale(px)) if with_body else []
    if model_ref in HOLD_POSE_ITEMS and not one_handed:
        arms, item = _two_handed(model_ref, px)
        if with_body:
            quads += arms
    else:
        if with_body:
            quads += transform_quads(_arm_quads(False), mat_chain(m_translate(5 * px, 2 * px, 0), m_scale(px)))
        arm_pose = mat_chain(m_translate(-5 * px, 2 * px, 0), m_rot("x", arm_pitch))
        if with_body:
            quads += transform_quads(_arm_quads(True), mat_chain(arm_pose, m_scale(px)))
        hand = mat_chain(arm_pose, m_rot("x", -90), m_rot("y", 180), m_translate(1 * px, 2 * px, -10 * px),
                         m_item_transform(display(model_ref, "thirdperson_righthand")))
        item = transform_quads(model_quads(load_model(model_ref)), hand)
    to_world = mat_chain(m_translate(0, 1.501 * 0.9375, 0), m_scale(-0.9375, -0.9375, 0.9375))
    return transform_quads(quads, to_world), transform_quads(item, to_world)


def third_person(model_ref, size, yaw=200.0, pitch=15.0, zoom=1.0, with_body=True, focus_item=False, one_handed=False):
    body, item = player_quads(model_ref, with_body=with_body, one_handed=one_handed)
    view = camera_orbit(yaw, pitch, distance=4.0, target=(0.0, 1.05, 0.0))
    body, item = transform_quads(body, view), transform_quads(item, view)
    centre = (0.0, 0.0)
    if focus_item:
        xs = [p[0] for q in item for p in (q.p00, q.p10, q.p01)]
        ys = [p[1] for q in item for p in (q.p00, q.p10, q.p01)]
        centre = ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
    bg = Image.new("RGBA", (size, size), (44, 47, 53, 255))
    return render_camera(body + item, size, size, ortho_scale=size / 2.3 * zoom, ortho_center=centre, base=bg)
