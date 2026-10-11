"""Tiny software renderer for Java block/item models and armor cubes (preview only).

Faces follow the vanilla vertex/UV order (FaceInfo + CuboidFace.UVs), so the
texture orientation in the preview matches the game.
"""

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

from assetlib import ASSETS

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
NORMALS = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}


def face_corners(direction, lo, hi):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    table = {
        "down": [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        "north": [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)],
        "south": [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
        "west": [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        "east": [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)],
    }
    return table[direction]


def default_uv(direction, lo, hi):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    return {
        "down": [x0, 16 - z1, x1, 16 - z0],
        "up": [x0, z0, x1, z1],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0],
        "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[direction]


class Quad:
    def __init__(self, p00, p10, p01, texture, rect, tint=(255, 255, 255), shade=1.0, normal=(0, 0, 1)):
        self.p00, self.p10, self.p01 = p00, p10, p01
        self.texture = texture
        self.rect = rect
        self.tint = tint
        self.shade = shade
        self.normal = normal


def load_texture(ref):
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    base = ASSETS.parent / ns / "textures" / f"{path}.png"
    return Image.open(base).convert("RGBA")


def resolve(textures, key):
    seen = 0
    while key.startswith("#"):
        key = textures[key[1:]]
        seen += 1
        if seen > 8:
            raise ValueError("texture loop")
    return key


def load_model(ref):
    ns, path = ref.split(":", 1)
    return json.loads((ASSETS.parent / ns / "models" / f"{path}.json").read_text(encoding="utf-8"))


def model_quads(model, tint=(255, 255, 255), emissive_full=True, cube_textures=None):
    textures = dict(model.get("textures", {}))
    elements = model.get("elements")
    if elements is None and model.get("parent") == "minecraft:block/cube":
        elements = [{"from": [0, 0, 0], "to": [16, 16, 16],
                     "faces": {d: {"texture": "#" + d} for d in ("down", "up", "north", "south", "west", "east")}}]
    quads = []
    cache = {}
    for element in elements:
        lo, hi = element["from"], element["to"]
        emissive = element.get("light_emission", 0) > 0 and emissive_full
        for direction, face in element["faces"].items():
            ref = resolve(textures, face["texture"])
            if ref not in cache:
                cache[ref] = load_texture(ref)
            tex = cache[ref]
            uv = face.get("uv") or default_uv(direction, lo, hi)
            tw, th = tex.size
            ua, va, ub, vb = uv[0] * tw / 16, uv[1] * th / 16, uv[2] * tw / 16, uv[3] * th / 16
            c = face_corners(direction, lo, hi)
            face_tint = tint if face.get("tintindex", -1) >= 0 else (255, 255, 255)
            quads.append(Quad(c[0], c[3], c[1], tex, (ua, va, ub, vb), face_tint,
                              1.0 if emissive else SHADE[direction], NORMALS[direction]))
    return quads


def box_uv_quads(tex, u, v, w, h, d, lo, hi):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    quads = [
        Quad((x0, y1, z1), (x1, y1, z1), (x0, y0, z1), tex, (u + d, v + d, u + d + w, v + d + h), shade=0.8, normal=(0, 0, 1)),
        Quad((x0, y1, z0), (x0, y1, z1), (x0, y0, z0), tex, (u, v + d, u + d, v + d + h), shade=0.6, normal=(-1, 0, 0)),
        Quad((x1, y1, z1), (x1, y1, z0), (x1, y0, z1), tex, (u + d + w, v + d, u + 2 * d + w, v + d + h), shade=0.6, normal=(1, 0, 0)),
        Quad((x1, y1, z0), (x0, y1, z0), (x1, y0, z0), tex, (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), shade=0.8, normal=(0, 0, -1)),
        Quad((x0, y1, z0), (x1, y1, z0), (x0, y1, z1), tex, (u + d, v, u + d + w, v + d), shade=1.0, normal=(0, 1, 0)),
    ]
    return quads


def render(quads, size, yaw=35.0, pitch=28.0, scale=None, center=None, supersample=2, background=(0, 0, 0, 0), fit_margin=0.08):
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def xf(p):
        x, y, z = p
        rx = x * cy_ + z * sy_
        rz = -x * sy_ + z * cy_
        ry = y * cp - rz * sp
        rz2 = y * sp + rz * cp
        return rx, ry, rz2

    def xn(n):
        x, y, z = n
        rx = x * cy_ + z * sy_
        rz = -x * sy_ + z * cy_
        return rx, y * cp - rz * sp, y * sp + rz * cp

    polys = []
    for q in quads:
        n = xn(q.normal)
        if n[2] <= 1e-6:
            continue
        ua, va, ub, vb = q.rect
        tw, th = q.texture.size
        pix = q.texture.load()
        a, b, c = q.p00, q.p10, q.p01
        eu = tuple(b[i] - a[i] for i in range(3))
        ev = tuple(c[i] - a[i] for i in range(3))
        u_lo, u_hi = sorted((ua, ub))
        v_lo, v_hi = sorted((va, vb))
        for ty in range(math.floor(v_lo + 1e-6), math.ceil(v_hi - 1e-6)):
            for tx in range(math.floor(u_lo + 1e-6), math.ceil(u_hi - 1e-6)):
                px = pix[min(tw - 1, max(0, tx)), min(th - 1, max(0, ty))]
                if px[3] < 8:
                    continue
                s0 = (max(tx, u_lo) - ua) / (ub - ua) if ub != ua else 0
                s1 = (min(tx + 1, u_hi) - ua) / (ub - ua) if ub != ua else 1
                t0 = (max(ty, v_lo) - va) / (vb - va) if vb != va else 0
                t1 = (min(ty + 1, v_hi) - va) / (vb - va) if vb != va else 1
                corners = []
                for s, t in ((s0, t0), (s1, t0), (s1, t1), (s0, t1)):
                    corners.append(xf(tuple(a[i] + eu[i] * s + ev[i] * t for i in range(3))))
                depth = sum(p[2] for p in corners) / 4
                colour = tuple(min(255, round(px[i] * q.tint[i] / 255 * q.shade)) for i in range(3)) + (px[3],)
                polys.append((depth, corners, colour))
    if not polys:
        return Image.new("RGBA", (size, size), background)
    xs = [p[0] for _, cs, _ in polys for p in cs]
    ys = [p[1] for _, cs, _ in polys for p in cs]
    if scale is None:
        span = max(max(xs) - min(xs), max(ys) - min(ys))
        scale = size * (1 - 2 * fit_margin) / span
    if center is None:
        center = ((max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2)
    ss = supersample
    img = Image.new("RGBA", (size * ss, size * ss), background)
    draw = ImageDraw.Draw(img)
    polys.sort(key=lambda p: p[0])
    for _, cs, colour in polys:
        pts = [((p[0] - center[0]) * scale * ss + size * ss / 2, (center[1] - p[1]) * scale * ss + size * ss / 2) for p in cs]
        draw.polygon(pts, fill=colour)
    return img.resize((size, size), Image.LANCZOS)


def model_file_quads(ref, tint=(255, 255, 255)):
    return model_quads(load_model(ref), tint)


def mat_identity():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_chain(*ms):
    out = mat_identity()
    for m in ms:
        out = mat_mul(out, m)
    return out


def m_translate(x, y, z):
    m = mat_identity()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def m_scale(x, y=None, z=None):
    y = x if y is None else y
    z = x if z is None else z
    m = mat_identity()
    m[0][0], m[1][1], m[2][2] = x, y, z
    return m


def m_rot(axis, degrees):
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    m = mat_identity()
    if axis == "x":
        m[1][1], m[1][2], m[2][1], m[2][2] = c, -s, s, c
    elif axis == "y":
        m[0][0], m[0][2], m[2][0], m[2][2] = c, s, -s, c
    else:
        m[0][0], m[0][1], m[1][0], m[1][1] = c, -s, s, c
    return m


def m_item_transform(transform, left_hand=False):
    rx, ry, rz = transform.get("rotation", [0, 0, 0])
    tx, ty, tz = [v / 16.0 for v in transform.get("translation", [0, 0, 0])]
    sx, sy, sz = transform.get("scale", [1, 1, 1])
    if left_hand:
        tx, ry, rz = -tx, -ry, -rz
    return mat_chain(m_translate(tx, ty, tz), m_rot("x", rx), m_rot("y", ry), m_rot("z", rz),
                     m_scale(sx, sy, sz), m_translate(-0.5, -0.5, -0.5), m_scale(1 / 16.0))


def xform(m, p):
    x, y, z = p
    return tuple(m[i][0] * x + m[i][1] * y + m[i][2] * z + m[i][3] for i in range(3))


def xform_dir(m, n):
    x, y, z = n
    v = tuple(m[i][0] * x + m[i][1] * y + m[i][2] * z for i in range(3))
    length = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / length for c in v)


def transform_quads(quads, m):
    return [Quad(xform(m, q.p00), xform(m, q.p10), xform(m, q.p01), q.texture, q.rect, q.tint, q.shade, xform_dir(m, q.normal))
            for q in quads]


def colour_quads(colour, lo, hi, shade_faces=True):
    tex = Image.new("RGBA", (1, 1), colour)
    quads = []
    for direction in ("down", "up", "north", "south", "west", "east"):
        c = face_corners(direction, lo, hi)
        quads.append(Quad(c[0], c[3], c[1], tex, (0, 0, 1, 1), shade=SHADE[direction] if shade_faces else 1.0, normal=NORMALS[direction]))
    return quads


def render_camera(quads, width, height, perspective_fov=None, ortho_scale=None, ortho_center=(0.0, 0.0), supersample=2, base=None):
    """Render view-space quads (camera at origin looking along -Z, +Y up).

    perspective_fov: vertical field of view in degrees (vanilla hand rendering), otherwise orthographic with
    ortho_scale pixels per unit around ortho_center.
    """
    ss = supersample
    w, h = width * ss, height * ss
    if perspective_fov is not None:
        f = 1.0 / math.tan(math.radians(perspective_fov) / 2.0)

        def project(p):
            depth = max(1e-4, -p[2])
            return (w / 2 + p[0] / depth * f * h / 2, h / 2 - p[1] / depth * f * h / 2)

        def facing(q, p):
            return sum(q.normal[i] * p[i] for i in range(3)) < 0

        def distance(ps):
            return -sum(math.sqrt(sum(c * c for c in p)) for p in ps) / len(ps)
    else:
        def project(p):
            return (w / 2 + (p[0] - ortho_center[0]) * ortho_scale * ss, h / 2 - (p[1] - ortho_center[1]) * ortho_scale * ss)

        def facing(q, p):
            return q.normal[2] > 1e-6

        def distance(ps):
            return sum(p[2] for p in ps) / len(ps)

    polys = []
    for q in quads:
        if not facing(q, q.p00):
            continue
        ua, va, ub, vb = q.rect
        tw, th = q.texture.size
        pix = q.texture.load()
        a, b, c = q.p00, q.p10, q.p01
        eu = tuple(b[i] - a[i] for i in range(3))
        ev = tuple(c[i] - a[i] for i in range(3))
        u_lo, u_hi = sorted((ua, ub))
        v_lo, v_hi = sorted((va, vb))
        for ty in range(math.floor(v_lo + 1e-6), math.ceil(v_hi - 1e-6)):
            for tx in range(math.floor(u_lo + 1e-6), math.ceil(u_hi - 1e-6)):
                px = pix[min(tw - 1, max(0, tx)), min(th - 1, max(0, ty))]
                if px[3] < 8:
                    continue
                s0 = (max(tx, u_lo) - ua) / (ub - ua) if ub != ua else 0
                s1 = (min(tx + 1, u_hi) - ua) / (ub - ua) if ub != ua else 1
                t0 = (max(ty, v_lo) - va) / (vb - va) if vb != va else 0
                t1 = (min(ty + 1, v_hi) - va) / (vb - va) if vb != va else 1
                pts = [tuple(a[i] + eu[i] * s + ev[i] * t for i in range(3)) for s, t in ((s0, t0), (s1, t0), (s1, t1), (s0, t1))]
                colour = tuple(min(255, round(px[i] * q.tint[i] / 255 * q.shade)) for i in range(3)) + (px[3],)
                polys.append((distance(pts), [project(p) for p in pts], colour))
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    polys.sort(key=lambda p: p[0])
    for _, pts, colour in polys:
        draw.polygon(pts, fill=colour)
    img = img.resize((width, height), Image.LANCZOS)
    if base is not None:
        out = base.copy().convert("RGBA")
        out.alpha_composite(img)
        return out
    return img


def camera_orbit(yaw, pitch, distance=0.0, target=(0.0, 0.0, 0.0)):
    return mat_chain(m_translate(0, 0, -distance), m_rot("x", pitch), m_rot("y", yaw), m_translate(-target[0], -target[1], -target[2]))
