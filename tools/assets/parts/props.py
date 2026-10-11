import copy
import math

import numpy as np

import kit
import layout
import paint
from paint import hexc

PROPS = {
    "p31_4b": dict(radius=15.5, blades=4, hub_d=5.2, hub_h=4.2, thick=0.9, color="#ff7a1a", nut=None,
                   segments=[(2.4, 6.4, 3.8, 20), (6.0, 11.9, 5.8, 17), (11.5, 14.5, 4.6, 15), (14.1, 15.5, 2.8, 14)],
                   sweep=[0, 3, 6, 9]),
    "p31_3b": dict(radius=15.5, blades=3, hub_d=5.0, hub_h=4.0, thick=0.8, color="#ff4fa3", nut=None,
                   segments=[(2.4, 6.6, 4.2, 20), (6.2, 12.2, 6.4, 17), (11.8, 14.6, 5.0, 15), (14.2, 15.5, 3.0, 14)],
                   sweep=[0, 3, 6, 9]),
    "p3_3b": dict(radius=38.0, blades=3, hub_d=9.0, hub_h=6.0, thick=1.4, color="#1fc3d6", nut="tmount",
                  segments=[(4.0, 11.8, 6.8, 20), (11.0, 27.8, 10.5, 17), (27.0, 35.2, 8.0, 15), (34.5, 38.0, 5.0, 14)],
                  sweep=[0, 2, 5, 8]),
    "p51_3b": dict(radius=65.0, blades=3, hub_d=13.0, hub_h=7.0, thick=2.0, color="#a4e22b", nut="nylock",
                   segments=[(5.5, 17.0, 10.0, 19), (16.0, 47.0, 15.0, 16), (46.0, 60.0, 11.5, 14), (59.0, 65.0, 7.0, 13)],
                   sweep=[0, 2, 4, 6]),
    "p7_2b": dict(radius=89.0, blades=2, hub_d=14.0, hub_h=8.0, thick=2.2, color="#8f5cf2", nut="nylock",
                  segments=[(6.0, 21.0, 12.5, 17), (20.0, 63.0, 18.5, 14), (62.0, 81.0, 13.5, 12), (80.0, 89.0, 8.5, 11)],
                  sweep=[0, 2, 4, 6]),
}


def hub_top(color):
    base = hexc(color)

    def painter(region, ctx):
        h, w = region.shape[:2]
        paint.fill(region, paint.shade(base, 1.1))
        cx, cy = w / 2, h / 2
        r = min(w, h) / 2
        yy, xx = np.mgrid[0:h, 0:w]
        d = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
        region[d > r * 0.85, :3] = paint.shade(base, 1.3)
        region[d < r * 0.45, :3] = hexc("#2a2a2c")

    return painter


def nut_top(region, ctx):
    h, w = region.shape[:2]
    paint.fill(region, hexc("#c3c9d1"))
    cx, cy = w / 2, h / 2
    r = min(w, h) / 2
    paint.disc(region, cx, cy, r * 0.8, hexc("#1f4fb8"))
    paint.disc(region, cx, cy, r * 0.42, hexc("#9aa1aa"))
    paint.disc(region, cx, cy, r * 0.22, hexc("#4a4f57"))


DAMAGE = ("worn", "damaged", "broken")
DAMAGE_PROPERTY = "propwash:prop_damage"

BLADE_DAMAGE = {
    "worn": [
        dict(tip=0.93, chip=0.4, nicks=[(0.42, 0.3)]),
        dict(tip=0.96, chip=0.35, nicks=[(0.62, 0.25)]),
        dict(tip=0.9, chip=0.45, nicks=[]),
        dict(tip=0.95, chip=0.3, nicks=[(0.5, 0.25)]),
    ],
    "damaged": [
        dict(tip=0.52, chip=0.55, nicks=[(0.3, 0.3)]),
        dict(tip=0.9, chip=0.4, nicks=[(0.6, 0.35)]),
        dict(tip=0.86, chip=0.45, nicks=[(0.42, 0.3)]),
        dict(tip=0.92, chip=0.35, nicks=[(0.55, 0.3)]),
    ],
    "broken": [
        dict(tip=0.07, chip=0.0, nicks=[]),
        dict(tip=0.84, chip=0.5, nicks=[(0.3, 0.3)], bend=32.0, bend_at=0.42),
        dict(tip=0.7, chip=0.5, nicks=[(0.45, 0.35)]),
        dict(tip=0.88, chip=0.4, nicks=[(0.6, 0.3)]),
    ],
}


def blade_damage(damage, blade):
    if damage is None:
        return {}
    specs = BLADE_DAMAGE[damage]
    return specs[blade] if blade < len(specs) else specs[-1]


def blade_pieces(p, spec, lead):
    hub = p["hub_d"] * 0.5
    span = p["radius"] - hub
    reach = hub + span * spec.get("tip", 1.0)
    pieces = []
    marks = []
    for (r0, r1, chord, pitch), sweep in zip(p["segments"], p["sweep"]):
        if r0 >= reach:
            break
        pieces.append(dict(r0=r0, r1=min(r1, reach), z0=-chord / 2, z1=chord / 2, pitch=pitch, sweep=sweep))
    if not pieces:
        return pieces, marks
    for frac, depth in spec.get("nicks", []):
        rn = hub + span * frac
        width = max(0.6, span * 0.06)
        a, b = rn - width / 2, rn + width / 2
        for i, pc in enumerate(pieces):
            if pc["r0"] + 0.3 < a and b < pc["r1"] - 0.3:
                chord = pc["z1"] - pc["z0"]
                cut = chord * depth
                notch = dict(pc, r0=a, r1=b)
                if lead > 0:
                    notch["z1"] = pc["z1"] - cut
                    face = notch["z1"]
                else:
                    notch["z0"] = pc["z0"] + cut
                    face = notch["z0"]
                pieces[i:i + 1] = [dict(pc, r1=a), notch, dict(pc, r0=b)]
                marks.append(dict(notch, z0=face - 0.12, z1=face + 0.12))
                break
    if spec.get("tip", 1.0) < 1.0:
        last = pieces[-1]
        length = last["r1"] - last["r0"]
        chip = spec.get("chip", 0.0)
        if chip > 0.0 and length > 1.2:
            jag = min(length * 0.45, max(0.8, span * 0.08))
            split = last["r1"] - jag
            chord = last["z1"] - last["z0"]
            tip = dict(last, r0=split)
            if lead > 0:
                tip["z1"] = last["z1"] - chord * chip
                step = dict(last, r0=split - 0.15, r1=split + 0.02, z0=tip["z1"], z1=last["z1"])
            else:
                tip["z0"] = last["z0"] + chord * chip
                step = dict(last, r0=split - 0.15, r1=split + 0.02, z0=last["z0"], z1=tip["z0"])
            pieces[-1:] = [dict(last, r1=split), tip]
            marks.append(step)
        end = pieces[-1]
        edge = min(0.8, (end["r1"] - end["r0"]) * 0.3)
        marks.append(dict(end, r0=end["r1"] - edge, r1=end["r1"] + 0.02))
    bend_at = spec.get("bend_at")
    if bend_at is not None:
        rb = hub + span * bend_at
        out = []
        for pc in pieces:
            if pc["r0"] < rb < pc["r1"]:
                out.append(dict(pc, r1=rb))
                out.append(dict(pc, r0=rb, bent=True))
            else:
                out.append(dict(pc, bent=pc["r0"] >= rb))
        pieces = out
        marks = [dict(mk, bent=mk["r0"] >= rb) for mk in marks]
        crease = next((pc for pc in pieces if pc.get("bent")), None)
        if crease is not None:
            marks.append(dict(crease, r0=rb - 0.2, r1=rb + 0.2, bent=False))
    return pieces, marks


def piece_rotation(pc, spin, yaw, spec):
    base = kit.element_matrix(-spin * pc["pitch"], yaw, 0.0)
    if not pc.get("bent"):
        return (-spin * pc["pitch"], yaw, 0), np.zeros(3)
    angle = math.radians(-spec["bend"])
    c, s = math.cos(angle), math.sin(angle)
    droop = np.array([[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]])
    b = np.array([spec["bend_radius"], 0.0, 0.0])
    shift = droop.T @ b - b
    total = base @ droop
    ax, ay, az = kit.euler_from_matrix(total)
    return (ax, ay, az), shift


def emit_piece(m, pc, material, name, spin, yaw, spec, y_mid, t, pad=0.0):
    rot, shift = piece_rotation(pc, spin, yaw, spec)
    x0, y0, z0 = pc["r0"], y_mid - t / 2 - pad, pc["z0"] - pad
    x1, y1, z1 = pc["r1"], y_mid + t / 2 + pad, pc["z1"] + pad
    if pc.get("bent"):
        sx, sy, sz = shift
        x0, y0, z0, x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz, x1 + sx, y1 + sy, z1 + sz
    m.box(x0, y0, z0, x1, y1, z1, material, name=name, rot=rot, origin=(0, y_mid, 0))


def build_prop(key, ccw=False, damage=None):
    p = PROPS[key]
    suffix = ("_" + damage if damage else "") + ("_ccw" if ccw else "")
    density = 16.0 if p["radius"] < 20 else 6.0 if p["radius"] < 40 else 3.6
    m = kit.Model("prop", key + suffix, density=density, gui=(58, 225, 0), gui_fill=15.5)
    blade_mat = paint.register(f"prop_{key}", paint.prop_material(p["color"]))
    hub_mat = paint.register(f"prop_hub_{key}", paint.plastic(p["color"]))
    build_hub(m, p, hub_mat)
    y_mid = p["hub_h"] * 0.5
    spin = -1 if ccw else 1
    t = p["thick"]
    hub = p["hub_d"] * 0.5
    for b in range(p["blades"]):
        base_yaw = 360.0 * b / p["blades"] + 15.0
        spec = dict(blade_damage(damage, b))
        if "bend_at" in spec:
            spec["bend_radius"] = hub + (p["radius"] - hub) * spec["bend_at"]
        pieces, marks = blade_pieces(p, spec, spin)
        for pc in pieces:
            emit_piece(m, pc, blade_mat, "blade", spin, base_yaw + spin * pc["sweep"], spec, y_mid, t)
        for mk in marks:
            emit_piece(m, mk, "prop_break", "break", spin, base_yaw + spin * mk["sweep"], spec, y_mid, t, pad=0.02)
    return m


def build_hub(m, p, hub_mat):
    hd, hh = p["hub_d"], p["hub_h"]
    sheet = m.sheet("hub_top", -hd / 2, -hd / 2, hd / 2, hd / 2, hub_top(p["color"]))
    m.cylinder(0, 0, 0.0, hh, hd, hub_mat, name="hub", sheet_up=sheet, bevel=max(0.4, hd * 0.06))
    if p["nut"] == "nylock":
        nd = 8.6
        nut_sheet = m.sheet("nut_top", -nd / 2, -nd / 2, nd / 2, nd / 2, nut_top)
        m.cylinder(0, 0, hh, hh + 3.2, nd, "steel", name="nut", bevel=0.4, no_bottom=True)
        m.cylinder(0, 0, hh + 3.2, hh + 5.6, nd - 0.6, "alu_nut", name="nut_cap", sheet_up=nut_sheet, bevel=0.5,
                   no_bottom=True)
    elif p["nut"] == "tmount":
        for s in (-1, 1):
            m.box(s * 2.6 - 0.9, hh, -0.9, s * 2.6 + 0.9, hh + 0.8, 0.9, "dark_steel", name="screw")


BLUR_SATURATION = 0.45
BLUR_ALPHA = (0.2, 0.3)


def blur_disc(color, radius_mm, hub_r_mm, blades):
    base = hexc(color)
    grey = np.full(3, float(np.dot(base, [0.299, 0.587, 0.114])))
    tone = paint.mix(grey, base, BLUR_SATURATION)

    def painter(region, ctx):
        h, w = region.shape[:2]
        cx, cy = w / 2, h / 2
        r = min(w, h) / 2
        yy, xx = np.mgrid[0:h, 0:w]
        d = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy) / r
        region[..., :3] = tone
        hub = hub_r_mm / radius_mm
        body = (d >= hub * 0.9) & (d <= 1.0)
        inner = np.clip((1 - d) / (1 - hub), 0, 1)
        low, high = BLUR_ALPHA
        a = low + (high - low) * inner * min(1.0, blades / 4.0 + 0.25)
        a = a * np.clip((1.0 - d) / 0.12, 0, 1)
        band = body & (np.abs(d - 0.64) < 0.05)
        a[band] = a[band] + 0.03
        alpha = np.zeros((h, w))
        alpha[body] = a[body]
        region[..., 3] = np.clip(alpha, 0, 1) * 255

    return painter


def build_blur(key, prop_model):
    p = PROPS[key]
    r = p["radius"]
    m = kit.Model("prop", key + "_blur", tex=64, density=48.0 / (2 * r / kit.UNIT_MM), gui=(58, 225, 0),
                  gui_fill=15.5, translucent=True)
    y_mid = p["hub_h"] * 0.5
    sheet = m.sheet("disc", -r, -r, r, r, blur_disc(p["color"], r, p["hub_d"] / 2, p["blades"]))
    m.box(-r, y_mid - 0.1, -r, r, y_mid + 0.1, r, "carbon", name="blur_disc", only=("up", "down"), sheet_up=sheet,
          sheet_down=sheet)
    return m


def attach_hub(blur_model, prop_model):
    for el in prop_model.elements:
        if el.name in ("hub", "nut", "nut_cap", "screw"):
            clone = copy.copy(el)
            clone.faces = {}
            for d, fs in el.faces.items():
                nf = copy.copy(fs)
                nf.texture = "1"
                clone.faces[d] = nf
            blur_model.elements.append(clone)


def register_materials():
    paint.register("alu_nut", paint.alu("#1f4fb8"))
    paint.register("prop_break", paint.plastic("#ece9df"))


def build_all():
    register_materials()
    out = []
    for key in layout.PROP_KEYS:
        cw = build_prop(key)
        ccw = build_prop(key, ccw=True)
        blur = build_blur(key, cw)
        out.append((cw, ccw, blur))
    return out


def build_damaged():
    out = []
    for key in layout.PROP_KEYS:
        for damage in DAMAGE:
            out.append((key, build_prop(key, damage=damage), build_prop(key, ccw=True, damage=damage)))
    return out
