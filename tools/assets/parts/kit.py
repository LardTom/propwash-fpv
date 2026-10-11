import base64
import io
import json
import math
import uuid

import numpy as np
from PIL import Image

import paint

UNIT_MM = 8.0
ANCHOR = 8.0
DIRS = ("north", "south", "east", "west", "up", "down")
NAMESPACE = "propwash"
TEXTURE_ROOT = "item/part"


def pos(mm):
    return ANCHOR + mm / UNIT_MM


def yaw_dir(deg):
    r = math.radians(deg)
    return math.cos(r), -math.sin(r)


def rot_matrix_xyz(x, y, z):
    rx, ry, rz = (math.radians(a) for a in (x, y, z))
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mx, my, mz


def element_matrix(x, y, z):
    mx, my, mz = rot_matrix_xyz(x, y, z)
    return mz @ my @ mx


def display_matrix(x, y, z):
    mx, my, mz = rot_matrix_xyz(x, y, z)
    return mx @ my @ mz


class FaceSpec:
    def __init__(self, material, decal=None, sheet=None, key=None, uv_rotation=0):
        self.material = material
        self.decal = decal
        self.sheet = sheet
        self.key = key
        self.uv_rotation = uv_rotation
        self.uv = None
        self.texture = "0"
        self.density_factor = None
        self.tint = None


class Sheet:
    def __init__(self, name, x0, z0, x1, z1, painter, texture="0"):
        self.name = name
        self.x0, self.z0, self.x1, self.z1 = x0, z0, x1, z1
        self.painter = painter
        self.rect = None
        self.texture = texture


class Element:
    def __init__(self, name, frm, to, faces, rotation=None):
        self.name = name
        self.frm = frm
        self.to = to
        self.faces = faces
        self.rotation = rotation

    def corners(self):
        (x0, y0, z0), (x1, y1, z1) = self.frm, self.to
        pts = np.array([[x, y, z] for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], dtype=float)
        if self.rotation:
            origin, rx, ry, rz = self.rotation
            m = element_matrix(rx, ry, rz)
            pts = (pts - np.array(origin)) @ m.T + np.array(origin)
        return pts


def face_size(frm, to, direction):
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    if direction in ("north", "south"):
        return dx, dy
    if direction in ("east", "west"):
        return dz, dy
    return dx, dz


class Model:
    def __init__(self, category, key, tex=64, density=4.0, min_density=0.5, gui=(30, 225, 0), gui_fill=15.0,
                 translucent=False, seed=1):
        self.category = category
        self.key = key
        self.tex = tex
        self.density = density
        self.min_density = min_density
        self.gui = gui
        self.gui_fill = gui_fill
        self.translucent = translucent
        self.elements = []
        self.sheets = []
        self.seed = seed
        self.image = None
        self.extra_textures = {}

    @property
    def model_id(self):
        if getattr(self, "override_id", None):
            return self.override_id
        return f"{NAMESPACE}:part/{self.category}/{self.key}"

    @property
    def texture_id(self):
        return f"{NAMESPACE}:{TEXTURE_ROOT}/{self.category}/{self.key}"

    def sheet(self, name, x0, z0, x1, z1, painter):
        s = Sheet(name, x0, z0, x1, z1, painter)
        self.sheets.append(s)
        return s

    def box(self, x0, y0, z0, x1, y1, z1, material, name="", skip=(), only=None, faces=None, decals=None,
            rot=None, origin=None, sheet_up=None, sheet_down=None, keys=None, uv_rot=None, lowres=None):
        lo = [min(x0, x1), min(y0, y1), min(z0, z1)]
        hi = [max(x0, x1), max(y0, y1), max(z0, z1)]
        for i in range(3):
            if hi[i] - lo[i] < 0.05:
                hi[i] = lo[i] + 0.05
        frm = [round(pos(v), 4) for v in lo]
        to = [round(pos(v), 4) for v in hi]
        face_specs = {}
        dirs = only if only is not None else DIRS
        for d in dirs:
            if d in skip:
                continue
            mat = material
            if faces and d in faces:
                mat = faces[d]
            if mat is None:
                continue
            decal = decals.get(d) if decals else None
            sheet = None
            if d == "up" and sheet_up is not None:
                sheet = sheet_up
            if d == "down" and sheet_down is not None:
                sheet = sheet_down
            key = keys.get(d) if keys else None
            face_specs[d] = FaceSpec(mat, decal, sheet, key, (uv_rot or {}).get(d, 0))
            if lowres and d in lowres:
                face_specs[d].density_factor = lowres[d]
        rotation = None
        if rot is not None and any(abs(a) > 1e-6 for a in rot):
            o = origin if origin is not None else ((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2)
            rotation = ([round(pos(v), 4) for v in o], rot[0], rot[1], rot[2])
        el = Element(name, frm, to, face_specs, rotation)
        self.elements.append(el)
        return el

    def cbox(self, cx, y0, cz, w, h, d, material, **kw):
        return self.box(cx - w / 2, y0, cz - d / 2, cx + w / 2, y0 + h, cz + d / 2, material, **kw)

    def bar(self, sx, sz, yaw, length, width, y0, y1, material, start=0.0, **kw):
        kw.setdefault("rot", (0, yaw, 0))
        kw.setdefault("origin", (sx, (y0 + y1) / 2, sz))
        return self.box(sx + start, y0, sz - width / 2, sx + start + length, y1, sz + width / 2, material, **kw)

    def cylinder(self, cx, cz, y0, y1, d, material, top=None, bottom=None, bevel=None, name="cyl",
                 no_top=False, no_bottom=False, sheet_up=None, sheet_down=None, phase=0.0):
        a = d * math.tan(math.radians(22.5))
        if bevel is None:
            bevel = max(0.35, min(d * 0.07, (y1 - y0) * 0.2))
        bevel = min(bevel, (y1 - y0) * 0.45)
        top = top or material
        bottom = bottom or material
        tface = None if no_top else top
        bface = None if no_bottom else bottom
        r = d / 2
        h = a / 2
        o = (cx, (y0 + y1) / 2, cz)
        rot0 = (0, phase, 0) if phase else None
        self.box(cx - r, y0, cz - h, cx + r, y1, cz + h, material, name=name, rot=rot0, origin=o,
                 faces={"up": tface, "down": bface}, sheet_up=sheet_up, sheet_down=sheet_down)
        for sgn in (-1, 1):
            z0, z1 = (cz + h, cz + r) if sgn > 0 else (cz - r, cz - h)
            inner = "north" if sgn > 0 else "south"
            self.box(cx - h, y0, z0, cx + h, y1, z1, material, name=name, rot=rot0, origin=o, skip=(inner,),
                     faces={"up": tface, "down": bface}, sheet_up=sheet_up, sheet_down=sheet_down)
        yb0, yb1 = y0 + bevel, y1 - bevel
        self.box(cx - r, yb0, cz - h, cx + r, yb1, cz + h, material, name=name, rot=(0, 45 + phase, 0), origin=o,
                 faces={"up": tface, "down": bface})
        for sgn in (-1, 1):
            z0, z1 = (cz + h, cz + r) if sgn > 0 else (cz - r, cz - h)
            inner = "north" if sgn > 0 else "south"
            self.box(cx - h, yb0, z0, cx + h, yb1, z1, material, name=name, rot=(0, 45 + phase, 0), origin=o,
                     skip=(inner,), faces={"up": tface, "down": bface})

    def cylinder_x(self, cx, cy, cz, length, d, material, end=None, name="cylx", along="x", bevel=None,
                   no_start=False, no_end=False):
        a = d * math.tan(math.radians(22.5))
        r = d / 2
        h = a / 2
        end = end or material
        if bevel is None:
            bevel = max(0.35, min(d * 0.07, length * 0.2))
        if along == "x":
            x0, x1 = cx - length / 2, cx + length / 2
            f0 = {"west": None if no_start else end, "east": None if no_end else end}
            o = (cx, cy, cz)
            self.box(x0, cy - r, cz - h, x1, cy + r, cz + h, material, name=name, faces=f0)
            self.box(x0, cy - h, cz + h, x1, cy + r * 0 + h, cz + r, material, name=name, faces=f0, skip=("north",))
            self.box(x0, cy - h, cz - r, x1, cy + h, cz - h, material, name=name, faces=f0, skip=("south",))
            fb = {"west": None if no_start else end, "east": None if no_end else end}
            self.box(x0 + bevel, cy - r, cz - h, x1 - bevel, cy + r, cz + h, material, name=name, rot=(45, 0, 0),
                     origin=o, faces=fb)
            self.box(x0 + bevel, cy - h, cz + h, x1 - bevel, cy + h, cz + r, material, name=name, rot=(45, 0, 0),
                     origin=o, faces=fb, skip=("north",))
            self.box(x0 + bevel, cy - h, cz - r, x1 - bevel, cy + h, cz - h, material, name=name, rot=(45, 0, 0),
                     origin=o, faces=fb, skip=("south",))
        else:
            z0, z1 = cz - length / 2, cz + length / 2
            f0 = {"north": None if no_start else end, "south": None if no_end else end}
            o = (cx, cy, cz)
            self.box(cx - r, cy - h, z0, cx + r, cy + h, z1, material, name=name, faces=f0)
            self.box(cx - h, cy + h, z0, cx + h, cy + r, z1, material, name=name, faces=f0, skip=("down",))
            self.box(cx - h, cy - r, z0, cx + h, cy - h, z1, material, name=name, faces=f0, skip=("up",))
            self.box(cx - r, cy - h, z0 + bevel, cx + r, cy + h, z1 - bevel, material, name=name, rot=(0, 0, 45),
                     origin=o, faces=f0)
            self.box(cx - h, cy + h, z0 + bevel, cx + h, cy + r, z1 - bevel, material, name=name, rot=(0, 0, 45),
                     origin=o, faces=f0, skip=("down",))
            self.box(cx - h, cy - r, z0 + bevel, cx + h, cy - h, z1 - bevel, material, name=name, rot=(0, 0, 45),
                     origin=o, faces=f0, skip=("up",))

    def ring(self, cx, cz, y0, y1, radius, wall, segments, material, name="ring", inner=None, top=None,
             overlap=1.04, skip=()):
        seg_len = 2 * (radius + wall) * math.tan(math.pi / segments) * overlap
        o = (cx, (y0 + y1) / 2, cz)
        faces = {}
        if inner:
            faces["west"] = inner
        if top:
            faces["up"] = top
        for i in range(segments):
            ang = 360.0 * i / segments
            self.box(cx + radius, y0, cz - seg_len / 2, cx + radius + wall, y1, cz + seg_len / 2, material, name=name,
                     rot=(0, ang, 0), origin=o, faces=dict(faces), skip=skip)

    def pack(self):
        for density_try in range(40):
            dens = self.density * (0.9 ** density_try)
            if dens < self.min_density:
                break
            rects = self._collect(dens)
            size = self.tex
            placed = shelf_pack(rects, size, size)
            if placed is not None:
                self._apply(placed, dens, size)
                self.used_density = dens
                return
        raise RuntimeError(f"texture overflow for {self.key}")

    def _collect(self, dens):
        entries = {}
        self._face_keys = []
        for el in self.elements:
            for d, fs in el.faces.items():
                if fs.sheet is not None:
                    continue
                w, h = face_size(el.frm, el.to, d)
                factor = fs.density_factor if fs.density_factor is not None else (0.5 if d == "down" else 1.0)
                tw = max(1, int(round(w * dens * factor)))
                th = max(1, int(round(h * dens * factor)))
                if fs.uv_rotation in (90, 270):
                    tw, th = th, tw
                side = "top" if d == "up" else "bottom" if d == "down" else "side"
                key = (fs.material, side, tw, th, fs.decal.__name__ if callable(fs.decal) else fs.decal, fs.key)
                entries.setdefault(key, (tw, th))
                self._face_keys.append((el, d, key))
        for s in self.sheets:
            tw = max(1, int(round((s.x1 - s.x0) / UNIT_MM * dens)))
            th = max(1, int(round((s.z1 - s.z0) / UNIT_MM * dens)))
            entries[("__sheet__", s.name)] = (tw, th)
        return entries

    def _apply(self, placed, dens, size):
        img = np.zeros((size, size, 4), dtype=np.uint8)
        rng = np.random.default_rng(self.seed)
        for key, (x, y, w, h) in placed.items():
            if key[0] == "__sheet__":
                s = next(s for s in self.sheets if s.name == key[1])
                s.rect = (x, y, w, h)
                region = np.zeros((h, w, 4), dtype=np.float64)
                s.painter(region, paint.Ctx(w, h, "up", rng, mm_per_px=(s.x1 - s.x0) / w))
                img[y:y + h, x:x + w] = np.clip(region, 0, 255).astype(np.uint8)
                continue
            material, side, tw, th, decal_name, _ = key
            region = np.zeros((th, tw, 4), dtype=np.float64)
            ctx = paint.Ctx(tw, th, side, rng, mm_per_px=UNIT_MM / dens)
            paint.MATERIALS[material](region, ctx) if isinstance(material, str) else material(region, ctx)
            img[y:y + th, x:x + tw] = np.clip(region, 0, 255).astype(np.uint8)
        decal_done = set()
        for el, d, key in self._face_keys:
            x, y, w, h = placed[key]
            fs = el.faces[d]
            if fs.decal is not None and key not in decal_done:
                region = img[y:y + h, x:x + w].astype(np.float64)
                fs.decal(region, paint.Ctx(w, h, d, rng, mm_per_px=UNIT_MM / dens))
                img[y:y + h, x:x + w] = np.clip(region, 0, 255).astype(np.uint8)
                decal_done.add(key)
            fs.uv = [x * 16.0 / size, y * 16.0 / size, (x + w) * 16.0 / size, (y + h) * 16.0 / size]
        for el in self.elements:
            for d, fs in el.faces.items():
                if fs.sheet is None:
                    continue
                s = fs.sheet
                sx, sy, sw, sh = s.rect
                (x0, _, z0), (x1, _, z1) = el.frm, el.to
                mx0 = (x0 - ANCHOR) * UNIT_MM
                mx1 = (x1 - ANCHOR) * UNIT_MM
                mz0 = (z0 - ANCHOR) * UNIT_MM
                mz1 = (z1 - ANCHOR) * UNIT_MM
                fu0 = (mx0 - s.x0) / (s.x1 - s.x0)
                fu1 = (mx1 - s.x0) / (s.x1 - s.x0)
                fv0 = (mz0 - s.z0) / (s.z1 - s.z0)
                fv1 = (mz1 - s.z0) / (s.z1 - s.z0)
                if d == "down":
                    fv0, fv1 = 1 - fv1, 1 - fv0
                fu0, fu1 = max(0.0, min(1.0, fu0)), max(0.0, min(1.0, fu1))
                fv0, fv1 = max(0.0, min(1.0, fv0)), max(0.0, min(1.0, fv1))
                fs.uv = [(sx + fu0 * sw) * 16.0 / size, (sy + fv0 * sh) * 16.0 / size,
                         (sx + fu1 * sw) * 16.0 / size, (sy + fv1 * sh) * 16.0 / size]
        self.image = img

    def bounds(self):
        pts = np.concatenate([el.corners() for el in self.elements])
        return pts.min(axis=0), pts.max(axis=0)

    def texture_map(self):
        if self.extra_textures:
            return self.extra_textures
        return {"0": (self.texture_id, self.image)}

    def to_json(self, display):
        tex = {k: v[0] for k, v in self.texture_map().items()}
        textures = {}
        for k, v in tex.items():
            if self.translucent and k == "0":
                textures[k] = {"sprite": v, "force_translucent": True}
            else:
                textures[k] = v
        textures["particle"] = tex["0"]
        elements = []
        for el in self.elements:
            e = {"name": el.name or "part", "from": r4(el.frm), "to": r4(el.to)}
            if el.rotation:
                origin, rx, ry, rz = el.rotation
                nz = [(ax, a) for ax, a in (("x", rx), ("y", ry), ("z", rz)) if abs(a) > 1e-6]
                if len(nz) == 1:
                    e["rotation"] = {"angle": round(nz[0][1], 4), "axis": nz[0][0], "origin": r4(origin)}
                else:
                    e["rotation"] = {"x": round(rx, 4), "y": round(ry, 4), "z": round(rz, 4), "origin": r4(origin)}
            faces = {}
            for d in DIRS:
                if d in el.faces:
                    fs = el.faces[d]
                    f = {"uv": r4(fs.uv), "texture": "#" + fs.texture}
                    if fs.uv_rotation:
                        f["rotation"] = fs.uv_rotation
                    if fs.tint is not None:
                        f["tintindex"] = fs.tint
                    faces[d] = f
            e["faces"] = faces
            elements.append(e)
        return {
            "credit": "Propwash FPV - generated by tools/assets/parts",
            "texture_size": [int(self.texture_map()["0"][1].shape[1]), int(self.texture_map()["0"][1].shape[0])],
            "textures": textures,
            "elements": elements,
            "gui_light": "side",
            "display": display,
        }

    def to_bbmodel(self, display, name=None):
        tex_map = self.texture_map()
        keys = sorted(tex_map.keys(), key=lambda k: int(k))
        index = {k: i for i, k in enumerate(keys)}
        textures = []
        for k in keys:
            sprite, img = tex_map[k]
            png = io.BytesIO()
            Image.fromarray(img, "RGBA").save(png, format="PNG")
            ns, path = sprite.split(":", 1)
            folder, file = path.rsplit("/", 1)
            textures.append({
                "path": "", "name": file + ".png", "folder": folder, "namespace": ns, "id": k,
                "width": img.shape[1], "height": img.shape[0], "uv_width": img.shape[1], "uv_height": img.shape[0],
                "particle": k == "0", "render_mode": "default", "visible": True, "internal": True, "saved": True,
                "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, sprite)),
                "source": "data:image/png;base64," + base64.b64encode(png.getvalue()).decode("ascii"),
            })
        elements = []
        outliner = []
        for i, el in enumerate(self.elements):
            eid = str(uuid.uuid5(uuid.NAMESPACE_URL, f"{self.model_id}#{i}"))
            origin = el.rotation[0] if el.rotation else [(el.frm[k] + el.to[k]) / 2 for k in range(3)]
            rotation = [el.rotation[1], el.rotation[2], el.rotation[3]] if el.rotation else [0, 0, 0]
            faces = {}
            for d in DIRS:
                if d in el.faces:
                    fs = el.faces[d]
                    _, img = tex_map[fs.texture]
                    scale_u = img.shape[1] / 16.0
                    scale_v = img.shape[0] / 16.0
                    uv = [fs.uv[0] * scale_u, fs.uv[1] * scale_v, fs.uv[2] * scale_u, fs.uv[3] * scale_v]
                    faces[d] = {"uv": r4(uv), "texture": index[fs.texture], "rotation": fs.uv_rotation}
                else:
                    faces[d] = {"uv": [0, 0, 0, 0], "texture": None}
            elements.append({
                "name": el.name or "part", "box_uv": False, "rescale": False, "locked": False,
                "render_order": "default", "allow_mirror_modeling": True,
                "from": r4(el.frm), "to": r4(el.to), "autouv": 0, "color": i % 8,
                "origin": r4(origin), "rotation": r4(rotation), "faces": faces, "type": "cube", "uuid": eid,
            })
            outliner.append(eid)
        first = tex_map[keys[0]][1]
        return {
            "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
            "name": name or self.key,
            "parent": "",
            "ambientocclusion": True,
            "front_gui_light": False,
            "visible_box": [1, 1, 0],
            "variable_placeholders": "",
            "variable_placeholder_buttons": [],
            "unhandled_root_fields": {},
            "resolution": {"width": first.shape[1], "height": first.shape[0]},
            "elements": elements,
            "outliner": outliner,
            "textures": textures,
            "display": display,
        }


def r4(values):
    out = []
    for v in values:
        rv = round(float(v), 4)
        if rv == int(rv):
            rv = int(rv)
        out.append(rv)
    return out


def shelf_pack(entries, width, height):
    items = sorted(entries.items(), key=lambda kv: (-kv[1][1] * kv[1][0], -kv[1][1], -kv[1][0], str(kv[0])))
    sky = np.zeros(width, dtype=int)
    placed = {}
    for key, (w, h) in items:
        if w > width or h > height:
            return None
        best = None
        for x in range(0, width - w + 1):
            y = int(sky[x:x + w].max())
            if y + h > height:
                continue
            score = (y + h, x)
            if best is None or score < best[0]:
                best = (score, x, y)
        if best is None:
            return None
        _, x, y = best
        sky[x:x + w] = y + h
        placed[key] = (x, y, w, h)
    return placed


DISPLAY_CONTEXTS = {
    "gui": None,
    "ground": (0, 0, 0),
    "fixed": (0, 180, 0),
    "on_shelf": (0, 180, 0),
    "thirdperson_righthand": (70, 45, 0),
    "firstperson_righthand": (10, 45, 0),
    "head": (0, 0, 0),
}

TARGET_PX = {
    "gui": 15.0,
    "ground": 7.0,
    "fixed": 12.0,
    "on_shelf": 10.0,
    "thirdperson_righthand": 7.0,
    "firstperson_righthand": 9.0,
    "head": 8.0,
}

CATEGORY_TARGET_PX = {
    ("battery", "firstperson_righthand"): 1.6,
    ("battery", "ground"): 6.0,
    ("battery", "fixed"): 18.0,
    ("battery", "on_shelf"): 15.0,
    ("video", "fixed"): 16.0,
    ("video", "on_shelf"): 14.0,
    ("stack", "fixed"): 16.0,
    ("stack", "on_shelf"): 13.0,
    ("item", "on_shelf"): 16.0,
}

BOX_CONTEXTS = ("ground", "fixed", "on_shelf", "head")
HAND_CONTEXTS = ("thirdperson_righthand", "firstperson_righthand")
GRIP_CONTEXTS = ("thirdperson_righthand",)
HAND_BOX_PX = 14.4
HAND_FULL_MM = 180.0
HAND_EXPONENT = 0.6
HAND_MIN_FACTOR = 0.25
GROUND_BULK_PX = 4.8

SHELF_ROTATION = (30, 180, 0)
SIDE_ROTATION = (0, 90, 0)
FLAT_ROTATION = (-90, 180, 0)

CATEGORY_ROTATION = {
    ("battery", "firstperson_righthand"): (5, 20, 0),
    ("battery", "fixed"): SIDE_ROTATION,
    ("battery", "on_shelf"): SIDE_ROTATION,
    ("video", "fixed"): SIDE_ROTATION,
    ("video", "on_shelf"): SIDE_ROTATION,
    ("stack", "fixed"): FLAT_ROTATION,
    ("stack", "on_shelf"): SHELF_ROTATION,
    ("item", "on_shelf"): SHELF_ROTATION,
    ("frame", "on_shelf"): SHELF_ROTATION,
    ("prop", "on_shelf"): SHELF_ROTATION,
}

CATEGORY_OFFSET_PX = {
    ("battery", "firstperson_righthand"): (-0.4, 4.4, 1.13),
}

WALL_ROTATION = (-90, 180, 0)
WALL_DIAMETER_PX = 24.0
WALL_LIFT_PX = 0.5
WALL_CATEGORIES = ("frame", "prop")

OFFSET_PX = {
    "gui": (0, 0, 0),
    "ground": (0, 0, 0),
    "fixed": (0, 0, 0),
    "on_shelf": (0, 0, 0),
    "thirdperson_righthand": (0, 2.5, 0.5),
    "firstperson_righthand": (1.13, 3.2, 1.13),
    "head": (0, 13, 0),
}


def rotated_extent(corners_centered, rotation):
    m = display_matrix(*rotation)
    p = np.einsum("ij,nj->ni", m, corners_centered)
    ext = p.max(axis=0) - p.min(axis=0)
    return float(ext[0]), float(ext[1]), float(ext[2])


def hand_extent(corners_centered, rotation):
    right = rotated_extent(corners_centered, rotation)
    left = rotated_extent(corners_centered, (rotation[0], -rotation[1], -rotation[2]))
    return tuple(max(a, b) for a, b in zip(right, left))


def bulk(extent):
    w, h, d = extent
    return max(w * h * d, 1e-9) ** (1.0 / 3.0)


def hand_factor(length):
    if length <= 0.0:
        return 1.0
    return max(HAND_MIN_FACTOR, min(1.0, (length / HAND_FULL_MM) ** HAND_EXPONENT))


def length_mm(model):
    lo, hi = model.bounds()
    return float((hi - lo).max()) * UNIT_MM


def hand_target(category, ctx, length):
    target = CATEGORY_TARGET_PX.get((category, ctx))
    if target is not None:
        return target
    return TARGET_PX[ctx] * hand_factor(length)


def display_scale(ctx, target, extent, hand=1.0):
    w, h, d = extent
    if ctx in BOX_CONTEXTS:
        scale = target / max(w, h, d, 1e-3)
        if ctx == "ground":
            scale = min(scale, GROUND_BULK_PX / max(bulk(extent), 1e-3))
        return scale
    scale = target / max(w, h, 1e-3)
    if ctx in HAND_CONTEXTS:
        scale = min(scale, HAND_BOX_PX * hand / max(w, h, d, 1e-3))
    return scale


def wall_footprint(pts, center):
    d = pts[:, [0, 2]] - center[[0, 2]]
    return float(np.sqrt((d ** 2).sum(axis=1)).max())


def hangs_on_wall(model, ctx):
    return ctx == "fixed" and model.category in WALL_CATEGORIES


def wall_display():
    return {
        "rotation": r4(WALL_ROTATION),
        "translation": r4([0, 0, -WALL_LIFT_PX]),
        "scale": r4([WALL_DIAMETER_PX / 16.0] * 3),
    }


def compute_display(model, gui_rotation=None, gui_fill=None, wall_extra=()):
    lo, hi = model.bounds()
    corners = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    pts = np.concatenate([el.corners() for el in model.elements])
    wall_pts = np.concatenate([pts] + [np.concatenate([el.corners() for el in m.elements]) for m in wall_extra])
    center = (lo + hi) / 2.0
    length = length_mm(model)
    hand = hand_factor(length)
    gui_rotation = gui_rotation or model.gui
    scales = {}
    rots = {}
    for ctx, rot in DISPLAY_CONTEXTS.items():
        rot = gui_rotation if rot is None else rot
        rot = CATEGORY_ROTATION.get((model.category, ctx), rot)
        if hangs_on_wall(model, ctx):
            rots[ctx] = WALL_ROTATION
            scales[ctx] = WALL_DIAMETER_PX / max(2.0 * wall_footprint(wall_pts, center), 1e-3)
            continue
        rots[ctx] = rot
        if ctx == "gui":
            target = gui_fill or model.gui_fill
        elif ctx in HAND_CONTEXTS:
            target = hand_target(model.category, ctx, length)
        else:
            target = CATEGORY_TARGET_PX.get((model.category, ctx), TARGET_PX[ctx])
        if ctx in HAND_CONTEXTS:
            scales[ctx] = display_scale(ctx, target, hand_extent(pts - center, rot), hand)
        else:
            scales[ctx] = display_scale(ctx, target, rotated_extent(pts - center, rot))
    pre = max(1.0, max(scales.values()) / 3.9)
    pre = round(pre, 3)
    display = {}
    for ctx, rot in rots.items():
        s = scales[ctx] / pre
        m = display_matrix(*rot)
        if hangs_on_wall(model, ctx):
            pivot = np.array([center[0], wall_pts[:, 1].min(), center[2]])
            shift = -(m @ ((pivot - ANCHOR) * s * pre))
            t = [shift[0], shift[1], shift[2] - WALL_LIFT_PX]
            display[ctx] = {
                "rotation": r4(rot),
                "translation": r4([max(-80, min(80, v)) for v in t]),
                "scale": r4([round(s, 4)] * 3),
            }
            continue
        shift = -(m @ ((center - ANCHOR) * s * pre))
        off = CATEGORY_OFFSET_PX.get((model.category, ctx))
        if off is None:
            off = [v * hand for v in OFFSET_PX[ctx]] if ctx in GRIP_CONTEXTS else OFFSET_PX[ctx]
        t = [shift[i] + off[i] for i in range(3)]
        display[ctx] = {
            "rotation": r4(rot),
            "translation": r4([max(-80, min(80, v)) for v in t]),
            "scale": r4([round(s, 4)] * 3),
        }
    return display, pre


def model_node(model_id, tints=None):
    node = {"type": "minecraft:model", "model": model_id}
    if tints:
        node["tints"] = [{"type": "minecraft:custom_model_data", "index": i, "default": int(t)} for i, t in enumerate(tints)]
    return node


def item_definition(model_id, pre_scale, extra_cases=None, tints=None, stages=None):
    base = model_node(model_id, tints)
    scaled = dict(base)
    if abs(pre_scale - 1.0) > 1e-6:
        t = 0.5 - 0.5 * pre_scale
        scaled["transformation"] = {
            "left_rotation": [0.0, 0.0, 0.0, 1.0],
            "right_rotation": [0.0, 0.0, 0.0, 1.0],
            "scale": [pre_scale, pre_scale, pre_scale],
            "translation": [round(t, 5), round(t, 5), round(t, 5)],
        }
    if stages:
        prop, entries = stages
        plain = dict(scaled)
        scaled = {"type": "minecraft:range_dispatch", "property": prop, "entries": [], "fallback": plain}
        if "transformation" in plain:
            scaled["transformation"] = plain.pop("transformation")
        for threshold, mid in entries:
            scaled["entries"].append({"threshold": threshold, "model": model_node(mid, tints)})
    render_model = base
    if extra_cases:
        render_model = {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": when, "model": model_node(mid, tints)} for when, mid in extra_cases],
            "fallback": base,
        }
    return {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": [{"when": "none", "model": render_model}],
            "fallback": scaled,
        }
    }


def dump_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def save_png(path, img):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(path, format="PNG", optimize=True)


def euler_from_matrix(m):
    b = math.asin(max(-1.0, min(1.0, -m[2, 0])))
    a = math.atan2(m[2, 1], m[2, 2])
    c = math.atan2(m[1, 0], m[0, 0])
    return math.degrees(a), math.degrees(b), math.degrees(c)


def merge(name_category, name_key, parts, model_id=None, tint_map=None):
    merged = Model(name_category, name_key)
    merged.image = None
    textures = {}
    for index, (model, offset_mm, tilt, pivot_mm) in enumerate(parts):
        remap = {}
        for k, (sprite, img) in model.texture_map().items():
            existing = next((kk for kk, vv in textures.items() if vv[0] == sprite), None)
            if existing is None:
                existing = str(len(textures))
                textures[existing] = (sprite, img)
            remap[k] = existing
        rt = element_matrix(tilt, 0, 0)
        pivot = np.array([pos(v) for v in pivot_mm])
        off = np.array(offset_mm) / UNIT_MM
        for el in model.elements:
            frm = np.array(el.frm, dtype=float)
            to = np.array(el.to, dtype=float)
            if el.rotation:
                o_el = np.array(el.rotation[0], dtype=float)
                r_el = element_matrix(el.rotation[1], el.rotation[2], el.rotation[3])
            else:
                o_el = (frm + to) / 2
                r_el = np.eye(3)
            total = rt @ r_el
            delta = rt @ (o_el - pivot) + pivot + off - o_el
            new_frm = (frm + delta).tolist()
            new_to = (to + delta).tolist()
            new_origin = (o_el + delta).tolist()
            rotation = None
            if not np.allclose(total, np.eye(3), atol=1e-7):
                ax, ay, az = euler_from_matrix(total)
                rotation = ([round(v, 4) for v in new_origin], ax, ay, az)
            faces = {}
            for d, fs in el.faces.items():
                nf = FaceSpec(fs.material, fs.decal, fs.sheet, fs.key, fs.uv_rotation)
                nf.uv = list(fs.uv)
                nf.texture = remap[fs.texture]
                if fs.tint is not None and tint_map is not None:
                    nf.tint = tint_map(index, fs.tint)
                faces[d] = nf
            merged.elements.append(Element(el.name, [round(v, 4) for v in new_frm], [round(v, 4) for v in new_to],
                                           faces, rotation))
    merged.extra_textures = textures
    if model_id:
        merged.override_id = model_id
    return merged
