import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

import kit

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "common/src/main/resources/assets/propwash"

_texture_cache = {}


def texture(sprite_id):
    if isinstance(sprite_id, dict):
        sprite_id = sprite_id["sprite"]
    ns, path = sprite_id.split(":", 1)
    file = ROOT / f"common/src/main/resources/assets/{ns}/textures/{path}.png"
    if file not in _texture_cache:
        _texture_cache[file] = np.asarray(Image.open(file).convert("RGBA"), dtype=np.float64)
    return _texture_cache[file]


def model_path(model_id):
    ns, path = model_id.split(":", 1)
    return ROOT / f"common/src/main/resources/assets/{ns}/models/{path}.json"


_tinted_cache = {}


def tinted(sprite_id, tint):
    key = (json.dumps(sprite_id, sort_keys=True), tint)
    if key not in _tinted_cache:
        img = texture(sprite_id).copy()
        rgb = np.array([(tint >> 16) & 255, (tint >> 8) & 255, tint & 255], dtype=np.float64)
        img[..., :3] = img[..., :3] * rgb / 255.0
        _tinted_cache[key] = img
    return _tinted_cache[key]


def load_model(model_id, tints=None):
    data = json.loads(model_path(model_id).read_text())
    textures = data["textures"]
    translucent = any(isinstance(v, dict) and v.get("force_translucent") for v in textures.values())
    quads = []
    for el in data["elements"]:
        f = np.array(el["from"], dtype=float)
        t = np.array(el["to"], dtype=float)
        rot = el.get("rotation")
        m = np.eye(3)
        origin = np.zeros(3)
        if rot:
            origin = np.array(rot["origin"], dtype=float)
            if "axis" in rot:
                ang = {"x": (rot["angle"], 0, 0), "y": (0, rot["angle"], 0), "z": (0, 0, rot["angle"])}[rot["axis"]]
            else:
                ang = (rot.get("x", 0), rot.get("y", 0), rot.get("z", 0))
            m = kit.element_matrix(*ang)
        for d, face in el["faces"].items():
            p0, u, v = face_frame(f, t, d)
            tex_ref = face["texture"].lstrip("#")
            index = face.get("tintindex")
            if tints is not None and index is not None and index < len(tints):
                tex = tinted(textures[tex_ref], tints[index])
            else:
                tex = texture(textures[tex_ref])
            corners = np.array([p0, p0 + u, p0 + v, p0 + u + v])
            corners = (corners - origin) @ m.T + origin
            quads.append((corners, face["uv"], tex, face.get("rotation", 0)))
    return quads, translucent, data.get("display", {})


def face_frame(f, t, d):
    x0, y0, z0 = f
    x1, y1, z1 = t
    dx, dy, dz = t - f
    if d == "north":
        return np.array([x1, y1, z0]), np.array([-dx, 0, 0]), np.array([0, -dy, 0])
    if d == "south":
        return np.array([x0, y1, z1]), np.array([dx, 0, 0]), np.array([0, -dy, 0])
    if d == "west":
        return np.array([x0, y1, z0]), np.array([0, 0, dz]), np.array([0, -dy, 0])
    if d == "east":
        return np.array([x1, y1, z1]), np.array([0, 0, -dz]), np.array([0, -dy, 0])
    if d == "up":
        return np.array([x0, y1, z0]), np.array([dx, 0, 0]), np.array([0, 0, dz])
    return np.array([x0, y0, z1]), np.array([dx, 0, 0]), np.array([0, 0, -dz])


LIGHT0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
LIGHT1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])


class Canvas:
    def __init__(self, w, h, background=(0, 0, 0, 0)):
        self.w = w
        self.h = h
        self.color = np.zeros((h, w, 4), dtype=np.float64)
        self.color[..., :] = background
        self.depth = np.full((h, w), -1e9)
        self.translucent = []

    def draw(self, quads, transform, translucent=False, light_matrix=None):
        for corners, uv, tex, uv_rot in quads:
            pts = transform(corners)
            e1 = pts[1] - pts[0]
            e2 = pts[2] - pts[0]
            normal = np.cross(e1, e2)
            if normal[2] <= 1e-9:
                continue
            world = transform.world(corners) if hasattr(transform, "world") else pts
            wn = np.cross(world[2] - world[0], world[1] - world[0])
            n_len = np.linalg.norm(wn)
            wn = wn / n_len if n_len > 0 else wn
            if light_matrix is not None:
                wn = light_matrix @ wn
            light = min(1.0, 0.45 + 0.55 * (max(0.0, wn @ LIGHT0) + max(0.0, wn @ LIGHT1)))
            if translucent:
                self.translucent.append((pts, uv, tex, light))
            else:
                self._raster(pts, uv, tex, light, False)

    def flush(self):
        self.translucent.sort(key=lambda q: q[0][:, 2].mean())
        for pts, uv, tex, light in self.translucent:
            self._raster(pts, uv, tex, light, True)
        self.translucent = []

    def _raster(self, pts, uv, tex, light, blend):
        xs = pts[:, 0]
        ys = pts[:, 1]
        x0 = max(0, int(math.floor(xs.min())))
        x1 = min(self.w - 1, int(math.ceil(xs.max())))
        y0 = max(0, int(math.floor(ys.min())))
        y1 = min(self.h - 1, int(math.ceil(ys.max())))
        if x1 < x0 or y1 < y0:
            return
        a = pts[1, :2] - pts[0, :2]
        b = pts[2, :2] - pts[0, :2]
        det = a[0] * b[1] - a[1] * b[0]
        if abs(det) < 1e-9:
            return
        yy, xx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        px_ = xx + 0.5 - pts[0, 0]
        py_ = yy + 0.5 - pts[0, 1]
        s = (px_ * b[1] - py_ * b[0]) / det
        t = (a[0] * py_ - a[1] * px_) / det
        inside = (s >= -1e-6) & (s <= 1 + 1e-6) & (t >= -1e-6) & (t <= 1 + 1e-6)
        if not inside.any():
            return
        z = pts[0, 2] + s * (pts[1, 2] - pts[0, 2]) + t * (pts[2, 2] - pts[0, 2])
        th, tw = tex.shape[:2]
        u0, v0, u1, v1 = uv
        uu = (u0 + np.clip(s, 0, 1) * (u1 - u0)) / 16.0 * tw
        vv = (v0 + np.clip(t, 0, 1) * (v1 - v0)) / 16.0 * th
        ui = np.clip(np.floor(np.minimum(uu, np.maximum(u0, u1) / 16.0 * tw - 1e-4)).astype(int), 0, tw - 1)
        vi = np.clip(np.floor(np.minimum(vv, np.maximum(v0, v1) / 16.0 * th - 1e-4)).astype(int), 0, th - 1)
        texel = tex[vi, ui]
        ys_idx = yy
        xs_idx = xx
        region_depth = self.depth[ys_idx, xs_idx]
        mask = inside & (z > region_depth)
        if blend:
            mask &= texel[..., 3] > 1
        else:
            mask &= texel[..., 3] > 25
        if not mask.any():
            return
        col = texel[..., :3] * light
        if blend:
            alpha = texel[..., 3:4] / 255.0
            cur = self.color[ys_idx, xs_idx]
            out = cur.copy()
            out[..., :3] = cur[..., :3] * (1 - alpha) + col * alpha
            out[..., 3] = np.maximum(cur[..., 3], texel[..., 3])
            self.color[ys_idx[mask], xs_idx[mask]] = out[mask]
        else:
            self.color[ys_idx[mask], xs_idx[mask], :3] = col[mask]
            self.color[ys_idx[mask], xs_idx[mask], 3] = 255
            self.depth[ys_idx[mask], xs_idx[mask]] = z[mask]

    def image(self):
        return Image.fromarray(np.clip(self.color, 0, 255).astype(np.uint8), "RGBA")


class ViewTransform:
    def __init__(self, rotation, scale, center_px, placement=None, pivot=None):
        self.m = kit.display_matrix(*rotation)
        self.scale = scale
        self.center_px = np.array(center_px, dtype=float)
        self.placement = placement
        self.pivot = np.zeros(3) if pivot is None else np.asarray(pivot, dtype=float)

    def world(self, corners_units):
        mm = (corners_units - kit.ANCHOR) * kit.UNIT_MM
        if self.placement is not None:
            rot, offset = self.placement
            mm = mm @ rot.T + offset
        return mm

    def __call__(self, corners_units):
        mm = self.world(corners_units) - self.pivot
        v = mm @ self.m.T * self.scale
        out = np.empty_like(v)
        out[:, 0] = self.center_px[0] + v[:, 0]
        out[:, 1] = self.center_px[1] - v[:, 1]
        out[:, 2] = v[:, 2]
        return out


class GuiTransform:
    def __init__(self, display, pre_scale, px_size, center_px):
        d = display.get("gui", {})
        self.rot = kit.display_matrix(*d.get("rotation", [0, 0, 0]))
        self.scale = d.get("scale", [1, 1, 1])[0]
        self.translation = np.array(d.get("translation", [0, 0, 0]), dtype=float) / 16.0
        self.pre = pre_scale
        self.px = px_size
        self.center_px = np.array(center_px, dtype=float)

    def world(self, corners_units):
        return self._block(corners_units)

    def _block(self, corners_units):
        p = corners_units / 16.0
        p = 0.5 + (p - 0.5) * self.pre
        p = p - 0.5
        p = p * self.scale
        p = p @ self.rot.T
        return p + self.translation

    def __call__(self, corners_units):
        b = self._block(corners_units) * 16.0 * self.px
        out = np.empty_like(b)
        out[:, 0] = self.center_px[0] + b[:, 0]
        out[:, 1] = self.center_px[1] - b[:, 1]
        out[:, 2] = b[:, 2]
        return out
