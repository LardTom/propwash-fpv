"""Race courses found in the world: NBT templates, structures, structure set, tags, chest loot and flight lines.

Run: python3 tools/data/world_courses.py            (writes all files, then validates them)
     python3 tools/data/world_courses.py --check    (validates only; exit code 1 on errors)

Outputs (never edit by hand, change this script instead):
  common/src/main/resources/data/propwash/structure/world_course/v2/<template>.nbt   (new worlds, 0.6.0 mechanics)
  common/src/main/resources/data/propwash/structure/world_course/<template>.nbt      (old layouts, byte-identical)
  common/src/main/resources/data/propwash/worldgen/structure/<variant>.json
  common/src/main/resources/data/propwash/worldgen/structure_set/world_courses.json
  common/src/main/resources/data/propwash/tags/worldgen/structure/world_courses.json
  common/src/main/resources/data/propwash/tags/worldgen/biome/has_structure/<variant>.json
  common/src/main/resources/data/propwash/loot_table/chests/world_course/<table>.json
  common/src/devtest/resources/worldcourse/lines.json                                (flight line per v2 template)

Templates are built block by block from the layouts below, gzip-compressed (level 9) with a fixed timestamp, so every
run produces byte-identical files. Course pieces are real propwash:gate entities (gates, flags, slalom poles, dive cubes,
gate poles) with a WorldCourse marker (variant, course slot, optional passes and mode, template-local position and yaw,
template-local board position); the game turns them into a numbered course when they first tick (race.world.WorldCourses).
The old templates stay because structures started in old worlds keep generating their missing chunks from them.
Balance and layouts: docs/design/world-courses.md.
"""

import gzip
import io
import json
import math
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import survival  # noqa: E402

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
DATA = REPO / "common" / "src" / "main" / "resources" / "data" / "propwash"
TEMPLATES = DATA / "structure" / "world_course"
NS = "propwash"
VANILLA = survival.VANILLA
DATA_VERSION = 5023

errors = []


def error(message):
    errors.append(message)


def p(name):
    return f"{NS}:{name}"


def mc(name):
    return f"minecraft:{name}"


class Nbt:
    def __init__(self, kind, value):
        self.kind = kind
        self.value = value


def nbyte(v):
    return Nbt(1, v)


def nint(v):
    return Nbt(3, v)


def nfloat(v):
    return Nbt(5, v)


def ndouble(v):
    return Nbt(6, v)


def nints(values):
    return Nbt(11, list(values))


def nlist(kind, items):
    return Nbt(9, (kind, list(items)))


def classify(value):
    if isinstance(value, Nbt):
        return value.kind, value.value
    if isinstance(value, bool):
        return 1, int(value)
    if isinstance(value, int):
        return 3, value
    if isinstance(value, float):
        return 6, value
    if isinstance(value, str):
        return 8, value
    if isinstance(value, dict):
        return 10, value
    if isinstance(value, list):
        if not value:
            return 9, (0, [])
        return 9, (classify(value[0])[0], value)
    raise TypeError(f"kein NBT-Wert: {value!r}")


def write_payload(out, kind, value):
    if kind == 1:
        out.write(struct.pack(">b", value))
    elif kind == 3:
        out.write(struct.pack(">i", value))
    elif kind == 4:
        out.write(struct.pack(">q", value))
    elif kind == 5:
        out.write(struct.pack(">f", value))
    elif kind == 6:
        out.write(struct.pack(">d", value))
    elif kind == 8:
        data = value.encode("utf-8")
        out.write(struct.pack(">H", len(data)))
        out.write(data)
    elif kind == 9:
        element, items = value
        out.write(struct.pack(">bi", element, len(items)))
        for item in items:
            item_kind, item_value = classify(item)
            if item_kind != element:
                raise TypeError("gemischte NBT-Liste")
            write_payload(out, item_kind, item_value)
    elif kind == 10:
        for key, item in value.items():
            item_kind, item_value = classify(item)
            out.write(struct.pack(">b", item_kind))
            write_payload(out, 8, key)
            write_payload(out, item_kind, item_value)
        out.write(b"\x00")
    elif kind == 11:
        out.write(struct.pack(">i", len(value)))
        for v in value:
            out.write(struct.pack(">i", v))
    else:
        raise TypeError(f"NBT-Typ {kind}")


def nbt_bytes(root):
    raw = io.BytesIO()
    raw.write(struct.pack(">b", 10))
    write_payload(raw, 8, "")
    write_payload(raw, 10, root)
    packed = io.BytesIO()
    with gzip.GzipFile(fileobj=packed, mode="wb", mtime=0, filename="", compresslevel=9) as gz:
        gz.write(raw.getvalue())
    return packed.getvalue()


def hashed(x, z, salt=0):
    h = (x * 73856093) ^ (z * 19349663) ^ (salt * 83492791) ^ 0x5bd1e995
    h = (h ^ (h >> 13)) * 0x5bd1e995 & 0xFFFFFFFF
    return (h ^ (h >> 15)) & 0xFFFF


GATE_SIZES = {"small": (3.0, 3.0), "medium": (4.0, 4.0), "large": (6.0, 4.0)}
FRAME = 0.5
DEPTH = 0.5
NONSOLID = {mc(n) for n in ("air", "short_grass", "fern", "poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet",
                            "dead_bush", "moss_carpet", "white_carpet", "red_carpet", "lantern")}
THIN_COLLIDING = {mc(n) for n in ("moss_carpet", "white_carpet", "red_carpet", "lantern")}
NOT_STURDY = {mc("dirt_path"), mc("farmland")}
FORBIDDEN = {mc(n) for n in ("spawner", "trial_spawner", "vault", "command_block", "structure_block", "jigsaw", "barrier")}
KNOWN_PROPS = {
    "axis": {"axis"}, "leaves": {"distance", "persistent", "waterlogged"}, "sign": {"rotation", "waterlogged"},
    "chest": {"facing", "type", "waterlogged"}, "stairs": {"facing", "half", "shape", "waterlogged"},
    "lantern": {"hanging", "waterlogged"},
}


def prop_family(name):
    short = name.split(":", 1)[1]
    if short.endswith("_leaves"):
        return "leaves"
    if short.endswith("_sign"):
        return "sign"
    if short.endswith("_stairs"):
        return "stairs"
    if short in ("chest",):
        return "chest"
    if short in ("lantern",):
        return "lantern"
    if short.endswith("_log") or short == "hay_block":
        return "axis"
    return None


def gate_geometry(gate):
    width, height = GATE_SIZES[gate.size]
    rad = math.radians(gate.yaw)
    nx, nz = round(-math.sin(rad)), round(math.cos(rad))
    sx, sz = round(math.cos(rad)), round(math.sin(rad))
    return width, height, (nx, nz), (sx, sz)


def gate_boxes(gate):
    width, height, (nx, nz), (sx, sz) = gate_geometry(gate)
    half = width / 2
    inner = half - FRAME
    foot = FRAME if gate.below else 0.0
    parts = [(-half, -inner, foot, height), (inner, half, foot, height)]
    if not gate.below and not gate.above:
        parts.append((-inner, inner, 0.0, FRAME))
    parts.append((-inner, inner, height - FRAME, height))
    boxes = []
    for s0, s1, u0, u1 in parts:
        xs = [gate.x + sx * s + nx * d for s in (s0, s1) for d in (-DEPTH / 2, DEPTH / 2)]
        zs = [gate.z + sz * s + nz * d for s in (s0, s1) for d in (-DEPTH / 2, DEPTH / 2)]
        boxes.append((min(xs), gate.y + u0, min(zs), max(xs), gate.y + u1, max(zs)))
    return boxes


def cells(box):
    x0, y0, z0, x1, y1, z1 = box
    eps = 1e-6
    for x in range(math.floor(x0 + eps), math.ceil(x1 - eps)):
        for y in range(math.floor(y0 + eps), math.ceil(y1 - eps)):
            for z in range(math.floor(z0 + eps), math.ceil(z1 - eps)):
                yield x, y, z


class Gate:
    def __init__(self, x, y, z, yaw, size):
        self.x = float(x)
        self.y = float(y)
        self.z = float(z)
        self.yaw = float(yaw)
        self.size = size
        self.below = None
        self.above = None


class Course:
    def __init__(self, variant, template, size, sign_wood="dark_oak"):
        self.variant = variant
        self.template = template
        self.size = size
        self.blocks = {}
        self.gates = []
        self.board = None
        self.chest = None
        self.clear_zones = []
        self.sign_wood = sign_wood
        self.loot = None
        self.footing = mc("gravel")

    def inside(self, x, y, z):
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def set(self, x, y, z, name, props=None, nbt=None):
        if not self.inside(x, y, z):
            error(f"{self.template}: Block außerhalb der Vorlage {x},{y},{z} {name}")
            return
        self.blocks[(x, y, z)] = (name, tuple(sorted((props or {}).items())), nbt)

    def get(self, x, y, z):
        entry = self.blocks.get((x, y, z))
        return entry[0] if entry else mc("air")

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def floor(self, pick):
        sx, _, sz = self.size
        for x in range(sx):
            for z in range(sz):
                self.set(x, 0, z, pick(x, z))

    def band(self, points, half_width, name, closed=True):
        pts = list(points) + ([points[0]] if closed else [])
        sx, _, sz = self.size
        for x in range(sx):
            for z in range(sz):
                cx, cz = x + 0.5, z + 0.5
                for (ax, az), (bx, bz) in zip(pts, pts[1:]):
                    dx, dz = bx - ax, bz - az
                    length = dx * dx + dz * dz
                    t = 0.0 if length == 0 else max(0.0, min(1.0, ((cx - ax) * dx + (cz - az) * dz) / length))
                    px, pz = ax + t * dx, az + t * dz
                    if math.hypot(cx - px, cz - pz) <= half_width + 0.01:
                        self.set(x, 0, z, name)
                        break

    def gate(self, x, y, z, yaw, size, pad=None):
        gate = Gate(x, y, z, yaw, size)
        self.gates.append(gate)
        if pad:
            boxes = gate_boxes(gate)
            minx = math.floor(min(b[0] for b in boxes)) - 3
            maxx = math.ceil(max(b[3] for b in boxes)) + 2
            minz = math.floor(min(b[2] for b in boxes)) - 3
            maxz = math.ceil(max(b[5] for b in boxes)) + 2
            for gx in range(minx, maxx + 1):
                for gz in range(minz, maxz + 1):
                    if self.inside(gx, 0, gz):
                        self.set(gx, 0, gz, pad)
        return gate

    def stack(self, lower, yaw, size):
        width, height = GATE_SIZES[lower.size]
        if GATE_SIZES[size][0] > width or lower.above:
            error(f"{self.template}: Gate {size} passt nicht auf {lower.size}")
        gate = Gate(lower.x, lower.y + height - FRAME, lower.z, yaw, size)
        gate.below = lower
        lower.above = gate
        self.gates.append(gate)
        return gate

    def sign(self, x, y, z, facing, wood=None):
        rotation = {"south": 0, "west": 4, "north": 8, "east": 12}[facing]
        name = self.variant_name_key()
        text = {
            "messages": [{"translate": name, "bold": nbyte(1)}, {"text": ""},
                         {"translate": f"{NS}.race.world.board.record_none"}, {"text": ""}],
            "color": "white",
            "has_glowing_text": nbyte(1),
        }
        back = {"messages": [{"text": ""}, {"translate": name}, {"text": ""}, {"text": ""}], "color": "black",
                "has_glowing_text": nbyte(0)}
        self.set(x, y, z, mc(f"{wood or self.sign_wood}_sign"), {"rotation": str(rotation)},
                 {"id": mc("sign"), "front_text": text, "back_text": back, "is_waxed": nbyte(1)})
        self.board = (x, y, z)

    def variant_name_key(self):
        return f"{NS}.race.world.{self.variant}"

    def place_chest(self, x, y, z, facing, table):
        self.set(x, y, z, mc("chest"), {"facing": facing},
                 {"id": mc("chest"), "LootTable": p(f"chests/world_course/{table}")})
        self.chest = (x, y, z)
        self.loot = table

    def post_cells(self, gate):
        if gate.below:
            return
        base = round(gate.y)
        for box in gate_boxes(gate)[:2]:
            for cx, _, cz in cells((box[0], box[1] - 1, box[2], box[3], box[1], box[5])):
                yield cx, base - 1, cz

    def footings(self):
        for gate in self.gates:
            for pos in self.post_cells(gate):
                if self.get(*pos) in NOT_STURDY:
                    self.set(*pos, self.footing)

    def air_above(self):
        sx, sy, sz = self.size
        for x in range(sx):
            for z in range(sz):
                for y in range(1, sy):
                    if (x, y, z) not in self.blocks:
                        self.blocks[(x, y, z)] = (mc("air"), (), None)

    def tree(self, x, z, wood, height=6, base=1, shape="round"):
        leaves = mc(f"{wood}_leaves")
        props = {"persistent": "true", "distance": "1"}
        top = base + height - 1
        if shape == "cone":
            layers = {top + 1: 0, top: 1, top - 1: 1, top - 2: 2, top - 3: 1}
        else:
            layers = {top + 1: 1, top: 1, top - 1: 2, top - 2: 2}
        for y, radius in layers.items():
            for dx in range(-radius, radius + 1):
                for dz in range(-radius, radius + 1):
                    if radius >= 2 and abs(dx) == radius and abs(dz) == radius:
                        continue
                    if radius == 1 and y == top + 1 and abs(dx) == 1 and abs(dz) == 1:
                        continue
                    if self.inside(x + dx, y, z + dz) and self.get(x + dx, y, z + dz) == mc("air"):
                        self.set(x + dx, y, z + dz, leaves, props)
        for y in range(base, top + 1):
            self.set(x, y, z, mc(f"{wood}_log"))
        self.set(x, base - 1, z, mc("packed_mud") if base == 1 else self.get(x, base - 1, z))


def field_slalom():
    c = Course("field_slalom", "field_slalom", (52, 10, 52))
    flowers = [mc("poppy"), mc("dandelion"), mc("cornflower"), mc("oxeye_daisy"), mc("azure_bluet")]
    c.floor(lambda x, z: mc("grass_block"))
    loop = [(13, 15.5), (21, 26.5), (13, 37.5), (18, 45), (25.5, 45), (38, 36.5), (30, 26.5), (38, 15.5), (38, 8),
            (13, 8)]
    c.band(loop, 1.5, mc("dirt_path"))
    c.gate(13, 1, 15.5, 0, "medium", pad=mc("dirt_path"))
    c.gate(21, 1, 26.5, 0, "medium", pad=mc("dirt_path"))
    c.gate(13, 1, 37.5, 0, "medium", pad=mc("dirt_path"))
    c.gate(25.5, 1, 45, 270, "large", pad=mc("dirt_path"))
    c.gate(38, 1, 36.5, 180, "medium", pad=mc("dirt_path"))
    c.gate(30, 1, 26.5, 180, "small", pad=mc("dirt_path"))
    c.gate(38, 1, 15.5, 180, "medium", pad=mc("dirt_path"))
    for x in range(3, 10):
        for z in range(3, 10):
            edge = x in (3, 9) or z in (3, 9)
            mark = (x in (5, 7) and 5 <= z <= 7) or (x == 6 and z == 6)
            c.set(x, 0, z, mc("polished_andesite") if edge else mc("white_concrete") if mark else mc("smooth_stone"))
    c.fill(10, 0, 6, 12, 0, 7, mc("gravel"))
    c.sign(6, 1, 2, "south")
    c.set(7, 1, 2, mc("oak_fence"))
    c.set(7, 2, 2, mc("lantern"))
    c.place_chest(2, 1, 5, "east", "common")
    for pos in ((1, 1, 2), (2, 1, 2), (1, 1, 3), (1, 2, 2)):
        c.set(*pos, mc("hay_block"))
    for pos in ((24, 1, 32), (25, 1, 32), (24, 1, 33), (25, 2, 32), (26, 1, 33)):
        c.set(*pos, mc("hay_block"))
    for x, z in ((44, 44), (47, 3), (3, 47), (47, 47), (25, 20), (2, 28)):
        c.set(x, 1, z, mc("oak_fence"))
        c.set(x, 2, z, mc("oak_fence"))
        c.set(x, 3, z, mc("lantern"))
    for x in range(52):
        for z in range(52):
            if c.get(x, 0, z) == mc("grass_block") and c.get(x, 1, z) == mc("air"):
                roll = hashed(x, z, 1) % 100
                if roll < 6:
                    c.set(x, 1, z, flowers[hashed(x, z, 2) % len(flowers)])
                elif roll < 20:
                    c.set(x, 1, z, mc("short_grass"))
    c.clear_zones.append("racing")
    return [c]


BADLANDS = {
    "floor": mc("terracotta"), "patch": mc("smooth_red_sandstone"), "strip": mc("red_sand"),
    "pad_edge": mc("smooth_red_sandstone"), "pad": mc("cut_red_sandstone"), "arch": mc("orange_terracotta"),
    "strata": [mc(n) for n in ("terracotta", "orange_terracotta", "terracotta", "yellow_terracotta", "brown_terracotta",
                               "terracotta", "red_terracotta", "light_gray_terracotta", "orange_terracotta",
                               "white_terracotta", "terracotta")],
    "sign": "dark_oak",
}
DESERT = {
    "floor": mc("smooth_sandstone"), "patch": mc("sandstone"), "strip": mc("sand"),
    "pad_edge": mc("cut_sandstone"), "pad": mc("smooth_sandstone"), "arch": mc("cut_sandstone"),
    "strata": [mc(n) for n in ("sandstone", "sandstone", "smooth_sandstone", "sandstone", "cut_sandstone", "sandstone",
                               "smooth_sandstone", "sandstone", "chiseled_sandstone", "sandstone", "sandstone")],
    "sign": "birch",
}


def canyon_run(template, palette):
    c = Course("canyon_run", template, (31, 13, 66), sign_wood=palette["sign"])
    c.floor(lambda x, z: palette["patch"] if hashed(x, z, 3) % 100 < 18 else palette["floor"])

    def column(x, z, height):
        for y in range(1, height + 1):
            c.set(x, y, z, palette["strata"][min(y - 1, len(palette["strata"]) - 1)])

    def peak(x, z):
        base = 7 + hashed(x // 2, z // 2, 4) % 4
        return 10 if 28 <= z <= 38 else base

    for z in range(4, 66):
        taper = 0 if z >= 7 else 7 - z
        for i, x in enumerate((0, 1, 2, 3)):
            column(x, z, max(1, peak(0, z) - (3 - i) - taper))
        for i, x in enumerate((30, 29, 28, 27)):
            column(x, z, max(1, peak(30, z) - (3 - i) - taper))
    for z in range(62, 66):
        for x in range(4, 27):
            column(x, z, max(1, peak(x, 62) - (z - 62) * 2))
    for z in range(10, 53):
        ends = min(z - 10, 52 - z)
        for x in range(13, 18):
            drop = abs(x - 15)
            column(x, z, max(1, min(peak(15, z), 3 + ends * 2) - drop))
    c.fill(4, 7, 30, 12, 8, 31, palette["arch"])
    c.fill(18, 7, 36, 26, 8, 37, palette["arch"])
    for z in range(4, 62):
        for x in (4, 26):
            c.set(x, 0, z, palette["strip"])
    for z in range(10, 53):
        for x in (12, 18):
            c.set(x, 0, z, palette["strip"])
    for x, z in ((4, 18), (26, 50), (12, 40), (18, 22), (4, 55), (26, 9)):
        if c.get(x, 1, z) == mc("air"):
            c.set(x, 1, z, mc("dead_bush"))
    c.gate(8, 1, 12.5, 0, "medium")
    c.gate(8, 1, 24.5, 0, "small")
    c.gate(8, 1, 42.5, 0, "medium")
    c.gate(15.5, 1, 57, 270, "large")
    c.gate(22, 1, 44.5, 180, "medium")
    c.gate(22, 1, 30.5, 180, "small")
    c.gate(22, 1, 14.5, 180, "medium")
    for x in range(12, 19):
        for z in range(1, 6):
            c.set(x, 0, z, palette["pad_edge"] if x in (12, 18) or z in (1, 5) else palette["pad"])
    c.sign(15, 1, 0, "south")
    c.place_chest(11, 1, 2, "east", "common")
    c.clear_zones.append("all")
    return c


FOREST_PALETTES = {
    "oak": {"wood": "oak", "shape": "round", "sign": "dark_oak"},
    "birch": {"wood": "birch", "shape": "round", "sign": "birch"},
    "spruce": {"wood": "spruce", "shape": "cone", "sign": "spruce"},
}


def forest_weave(template, palette):
    c = Course("forest_weave", template, (50, 12, 50), sign_wood=palette["sign"])

    def pick(x, z):
        roll = hashed(x, z, 5) % 100
        if roll < 14:
            return mc("gravel")
        if roll < 28:
            return mc("packed_mud")
        if roll < 33:
            return mc("mossy_cobblestone")
        return mc("dirt_path")

    c.floor(pick)
    c.footing = mc("packed_mud")
    c.gate(10, 1, 10.5, 0, "medium")
    c.gate(10, 1, 24.5, 0, "small")
    c.gate(19.5, 1, 37, 270, "medium")
    c.gate(33.5, 1, 41, 270, "medium")
    c.gate(40, 1, 30.5, 180, "medium")
    c.gate(32, 1, 21.5, 180, "small")
    c.gate(40, 1, 11.5, 180, "medium")
    c.gate(25.5, 1, 5, 90, "large")
    wood, shape = palette["wood"], palette["shape"]
    for x, z, h in ((10, 17, 6), (26, 39, 7), (36, 26, 6), (36, 16, 7), (17, 7, 6), (4, 31, 7), (16, 29, 6),
                    (24, 24, 7), (29, 30, 6), (45, 22, 7), (46, 45, 6), (3, 46, 7), (21, 46, 6), (46, 3, 7),
                    (31, 11, 6), (45, 37, 6)):
        c.tree(x, z, wood, h + (2 if shape == "cone" else 0), shape=shape)
    c.fill(21, 1, 16, 25, 1, 16, mc(f"{wood}_log"), {"axis": "x"})
    for pos in ((42, 1, 45), (43, 1, 45), (42, 1, 46), (42, 2, 45)):
        c.set(*pos, mc("mossy_cobblestone"))
    blocked = {(x, z) for gate in c.gates for box in gate_boxes(gate) for x, _, z in cells(box)}
    for x in range(50):
        for z in range(50):
            if (c.get(x, 1, z) == mc("air") and c.get(x, 0, z) == mc("dirt_path") and hashed(x, z, 6) % 100 < 7
                    and (x, z) not in blocked):
                c.set(x, 1, z, mc("moss_carpet"))
    for x in range(1, 7):
        for z in range(1, 7):
            c.set(x, 0, z, mc("mossy_cobblestone") if x in (1, 6) or z in (1, 6) else mc("cobblestone"))
    c.sign(3, 1, 0, "south")
    c.place_chest(0, 1, 3, "east", "common")
    c.clear_zones.append("all")
    return c


def drone_arena():
    c = Course("drone_arena", "drone_arena", (44, 12, 44))
    lane = [mc("stone_bricks"), mc("stone_bricks"), mc("cracked_stone_bricks"), mc("mossy_stone_bricks"), mc("cobblestone"),
            mc("gravel")]

    def pick(x, z):
        edge = min(x, z, 43 - x, 43 - z)
        roll = hashed(x, z, 7)
        if 3 <= edge <= 11:
            return lane[roll % len(lane)]
        return [mc("packed_mud"), mc("packed_mud"), mc("gravel"), mc("tuff")][roll % 4]

    c.floor(pick)
    ruin = [mc("stone_bricks"), mc("mossy_stone_bricks"), mc("cracked_stone_bricks")]
    for x in range(44):
        for z in range(44):
            if min(x, z, 43 - x, 43 - z) != 0 or (z == 0 and 19 <= x <= 24):
                continue
            height = hashed(x, z, 8) % 4
            for y in range(1, height + 1):
                c.set(x, y, z, ruin[hashed(x, y * 7 + z, 9) % 3])
    for z in range(14, 31):
        for step in range(3):
            x = 40 + step
            if hashed(x, z, 10) % 100 < 12:
                continue
            for y in range(1, step + 1):
                c.set(x, y, z, mc("stone_bricks"))
            c.set(x, step + 1, z, mc("stone_brick_stairs"), {"facing": "east"})
    for x, z, height in ((3, 3, 4), (40, 3, 4), (3, 40, 4), (40, 40, 2)):
        for y in range(1, height + 1):
            c.set(x, y, z, mc("cobblestone_wall"))
        if height == 4:
            c.set(x, height + 1, z, mc("lantern"))
    for x in range(14, 17):
        for z in range(33, 39):
            c.fill(x, 1, z, x, 2, z, mc("stone_bricks"))
    for x in range(32, 38):
        for z in range(11, 14):
            c.fill(x, 1, z, x, 2, z, mc("stone_bricks"))
    c.gate(11, 1, 13.5, 0, "medium")
    c.gate(11, 1, 26.5, 0, "small")
    c.gate(15.5, 3, 36, 270, "medium")
    c.gate(27.5, 1, 38, 270, "large")
    ladder = c.gate(36, 1, 29.5, 180, "medium")
    ladder = c.stack(ladder, 0, "small")
    c.stack(ladder, 180, "small")
    c.gate(35, 3, 12.5, 180, "medium")
    c.gate(27.5, 1, 6, 90, "large")
    c.gate(18.5, 1, 6, 90, "small")
    for x in range(2, 7):
        for z in range(3, 8):
            c.set(x, 0, z, mc("polished_andesite") if x in (2, 6) or z in (3, 7) else mc("smooth_stone"))
    for x, z, height in ((2, 3, 3), (6, 3, 3), (2, 7, 2), (6, 7, 3)):
        for y in range(1, height + 1):
            c.set(x, y, z, mc("oak_fence"))
    for x in range(2, 7):
        for z in range(3, 6):
            if hashed(x, z, 11) % 100 < 70:
                c.set(x, 4, z, mc("white_wool") if x % 2 == 0 else mc("red_wool"))
    for x, z, name in ((4, 6, "white_carpet"), (5, 6, "red_carpet"), (3, 5, "white_carpet")):
        c.set(x, 1, z, mc(name))
    for pos in ((21, 1, 23), (22, 1, 23), (21, 1, 24), (22, 1, 24), (21, 2, 23), (22, 2, 24), (21, 3, 23)):
        c.set(*pos, mc("cracked_stone_bricks"))
    c.set(21, 4, 23, mc("lantern"))
    c.sign(4, 1, 2, "south")
    c.place_chest(7, 1, 2, "south", "arena")
    c.clear_zones.append("all")
    return c


def legacy_courses():
    result = field_slalom()
    result.append(canyon_run("canyon_run_badlands", BADLANDS))
    result.append(canyon_run("canyon_run_desert", DESERT))
    for key, palette in FOREST_PALETTES.items():
        result.append(forest_weave(f"forest_weave_{key}", palette))
    result.append(drone_arena())
    for course in result:
        course.footings()
        course.air_above()
    return result


V2 = "v2"
LINES = REPO / "common" / "src" / "devtest" / "resources" / "worldcourse" / "lines.json"
PATH_TOP = 15 / 16
TOPS = {mc("dirt_path"): PATH_TOP, mc("farmland"): PATH_TOP}
ELEMENTS = {"flag": (5.0, 4.0, 0.25), "pole": (4.0, 3.0, 0.25), "cube": (3.5, 3.5, 0.25), "gate_pole": (0.5, 4.0, 0.5)}
POLE_REACH = 4.0
LEFT = 0
RIGHT = 1
EXIT_FRONT = 0
EXIT_RIGHT = 1
EXIT_BACK = 2
EXIT_LEFT = 3
POLE_COLORS = {"white": 0, "orange": 1, "yellow": 4, "lime": 5, "red": 14}
CLEARANCE = 0.3
LINE_STEP = 0.25
APPROACH = 4


class Piece:
    def __init__(self, kind, x, y, z, yaw, size="medium", variant=0, length=None, below=None, side=LEFT):
        self.kind = kind
        self.x = float(x)
        self.y = float(y)
        self.z = float(z)
        self.yaw = float(yaw)
        self.size = size
        self.variant = variant
        self.length = length
        self.score_below = below
        self.side = side
        self.below = None
        self.above = None
        self.passes = []

    def gate(self):
        return self.kind == "gate"

    def dims(self):
        if self.gate():
            return GATE_SIZES[self.size]
        width, height, _ = ELEMENTS[self.kind]
        return width, self.length if self.length else height

    def frame(self):
        return FRAME if self.gate() else ELEMENTS[self.kind][2]

    def sign(self):
        if self.kind == "gate_pole":
            return 1 if self.side == LEFT else -1
        return 1 if self.variant == LEFT else -1

    def score_width(self):
        return POLE_REACH if self.kind == "gate_pole" else self.dims()[0]

    def label(self):
        return f"{self.kind} {self.x:g},{self.y:g},{self.z:g}"


def axes(yaw, reverse=False):
    rad = math.radians(yaw)
    nx, nz = round(-math.sin(rad)), round(math.cos(rad))
    sx, sz = round(math.cos(rad)), round(math.sin(rad))
    if reverse:
        return (-nx, -nz), (-sx, -sz)
    return (nx, nz), (sx, sz)


def oriented(piece, s0, s1, u0, u1, n0, n1):
    (nx, nz), (sx, sz) = axes(piece.yaw)
    xs = [piece.x + sx * s + nx * n for s in (s0, s1) for n in (n0, n1)]
    zs = [piece.z + sz * s + nz * n for s in (s0, s1) for n in (n0, n1)]
    return min(xs), piece.y + u0, min(zs), max(xs), piece.y + u1, max(zs)


def piece_boxes(piece):
    if piece.gate():
        return gate_boxes(piece)
    width, height = piece.dims()
    t = piece.frame() * 0.5
    if piece.kind == "cube":
        half = width * 0.5
        bar = piece.frame()
        inner = half - bar
        spans = {-1: (-half, -inner), 1: (inner, half)}
        boxes = [oriented(piece, *spans[a], 0.0, height, *spans[b]) for a in (-1, 1) for b in (-1, 1)]
        for level in (0.0, height - bar):
            for a in (-1, 1):
                boxes.append(oriented(piece, -inner, inner, level, level + bar, *spans[a]))
                boxes.append(oriented(piece, *spans[a], level, level + bar, -inner, inner))
        return boxes
    boxes = [oriented(piece, -t, t, 0.0, height, -t, t)]
    if piece.kind == "flag":
        top = height - 0.125
        bottom = top - min(2.0, height * 0.55)
        d = 1 / 32
        if piece.sign() > 0:
            boxes.append(oriented(piece, t, t + 1.0, bottom, top, -d, d))
        else:
            boxes.append(oriented(piece, -t - 1.0, -t, bottom, top, -d, d))
    return boxes


class Plane:
    def __init__(self, origin, side, up, normal, s_range, u_range):
        self.origin = origin
        self.side = side
        self.up = up
        self.normal = normal
        self.s_range = s_range
        self.u_range = u_range

    def crossed(self, a, b):
        ox, oy, oz = self.origin
        nx, ny, nz = self.normal
        da = (a[0] - ox) * nx + (a[1] - oy) * ny + (a[2] - oz) * nz
        db = (b[0] - ox) * nx + (b[1] - oy) * ny + (b[2] - oz) * nz
        if not (da < 0.0 <= db):
            return False
        t = da / (da - db)
        px, py, pz = (a[i] + (b[i] - a[i]) * t for i in range(3))
        s = (px - ox) * self.side[0] + (py - oy) * self.side[1] + (pz - oz) * self.side[2]
        u = (px - ox) * self.up[0] + (py - oy) * self.up[1] + (pz - oz) * self.up[2]
        return self.s_range[0] <= s <= self.s_range[1] and self.u_range[0] <= u <= self.u_range[1]


def stages(piece, reverse):
    (nx, nz), (sx, sz) = axes(piece.yaw, reverse)
    width, height = piece.dims()
    origin = (piece.x, piece.y, piece.z)
    if piece.gate():
        inner = width * 0.5 - FRAME
        return [Plane(origin, (sx, 0, sz), (0, 1, 0), (nx, 0, nz), (-inner, inner), (FRAME, height - FRAME))]
    if piece.kind == "cube":
        half = width * 0.5
        bar = piece.frame()
        inner = half - bar
        top = Plane((piece.x, piece.y + height - bar * 0.5, piece.z), (sx, 0, sz), (nx, 0, nz), (0, -1, 0), (-inner, inner),
                    (-inner, inner))
        exits = {EXIT_FRONT: (nx, nz), EXIT_RIGHT: (-sx, -sz), EXIT_BACK: (-nx, -nz), EXIT_LEFT: (sx, sz)}
        ex, ez = exits[piece.variant]
        reach = half - bar * 0.5
        face = Plane((piece.x + ex * reach, piece.y, piece.z + ez * reach), (ez, 0, ex), (0, 1, 0), (ex, 0, ez), (-inner, inner),
                     (bar, height - bar))
        return [top, face]
    sign = piece.sign()
    below = 1.0 if piece.score_below is None else piece.score_below
    reach = piece.score_width()

    def ray(dx, dz, ex, ez):
        return Plane(origin, (dx, 0, dz), (0, 1, 0), (ex, 0, ez), (0.0, reach), (-below, height))

    if piece.kind in ("pole", "gate_pole"):
        return [ray(sx * sign, sz * sign, nx, nz)]
    return [ray(-sx * sign, -sz * sign, nx, nz), ray(nx, nz, sx * sign, sz * sign), ray(sx * sign, sz * sign, -nx, -nz)]


def fouls(piece, reverse):
    if piece.kind not in ("pole", "gate_pole", "flag"):
        return []
    (nx, nz), (sx, sz) = axes(piece.yaw, reverse)
    _, height = piece.dims()
    sign = piece.sign() * (-1 if piece.kind in ("pole", "gate_pole") else 1)
    below = 1.0 if piece.score_below is None else piece.score_below
    return [Plane((piece.x, piece.y, piece.z), (sx * sign, 0, sz * sign), (0, 1, 0), (nx, 0, nz), (0.0, piece.score_width()),
                  (-below, height))]


def densify(points, step=LINE_STEP):
    out = [points[0]]
    for a, b in zip(points, points[1:]):
        length = math.dist(a, b)
        steps = max(1, math.ceil(length / step))
        for i in range(1, steps + 1):
            out.append(tuple(a[k] + (b[k] - a[k]) * i / steps for k in range(3)))
    return out


def box_distance(point, box):
    x0, y0, z0, x1, y1, z1 = box
    dx = max(x0 - point[0], 0.0, point[0] - x1)
    dy = max(y0 - point[1], 0.0, point[1] - y1)
    dz = max(z0 - point[2], 0.0, point[2] - z1)
    return math.sqrt(dx * dx + dy * dy + dz * dz)


class Course2(Course):
    def __init__(self, variant, name, size, sign_wood="dark_oak", mode="laps"):
        super().__init__(variant, f"{V2}/{name}", size, sign_wood)
        self.name = name
        self.mode = mode
        self.pieces = []
        self.order = []
        self.line = []

    def add(self, kind, x, y, z, yaw, **kwargs):
        piece = Piece(kind, x, y, z, yaw, **kwargs)
        self.pieces.append(piece)
        return piece

    def stack_on(self, lower, yaw, size):
        width, height = GATE_SIZES[lower.size]
        if GATE_SIZES[size][0] > width or lower.above:
            error(f"{self.template}: Gate {size} passt nicht auf {lower.size}")
        piece = self.add("gate", lower.x, lower.y + height - FRAME, lower.z, yaw, size=size)
        piece.below = lower
        lower.above = piece
        return piece

    def go(self, piece, reverse=False):
        piece.passes.append((len(self.order), reverse))
        self.order.append((piece, reverse))

    def path_under(self, piece, name=mc("dirt_path")):
        row = math.floor(piece.y - 1e-6)
        for box in piece_boxes(piece):
            for x, y, z in cells(box):
                if y == row and self.inside(x, y, z):
                    self.set(x, y, z, name)

    def pad(self, piece, name, reach=3):
        for dx in range(-reach, reach + 1):
            for dz in range(-reach, reach + 1):
                x, z = math.floor(piece.x) + dx, math.floor(piece.z) + dz
                if self.inside(x, 0, z):
                    self.set(x, 0, z, name)

    def trail(self, half_width, name, points=None):
        pts = densify(points or self.line, 0.5)
        sx, _, sz = self.size
        for x in range(sx):
            for z in range(sz):
                cx, cz = x + 0.5, z + 0.5
                if any(math.hypot(cx - p[0], cz - p[2]) <= half_width + 0.01 for p in pts):
                    self.set(x, 0, z, name)


def footprint(piece):
    if piece.gate():
        if piece.below:
            return []
        boxes = gate_boxes(piece)[:2]
    elif piece.kind == "cube":
        half = piece.dims()[0] * 0.5
        boxes = [(piece.x - half, piece.y, piece.z - half, piece.x + half, piece.y + 1, piece.z + half)]
    else:
        boxes = piece_boxes(piece)[:1]
    cells_out = []
    eps = 1e-6
    for x0, _, z0, x1, _, z1 in boxes:
        for x in range(math.floor(x0 + eps), math.ceil(x1 - eps)):
            for z in range(math.floor(z0 + eps), math.ceil(z1 - eps)):
                if (x, z) not in cells_out:
                    cells_out.append((x, z))
    return cells_out


def surface(course, x, y, z):
    name = course.get(x, y, z)
    if name in TOPS:
        return y + TOPS[name]
    if solid(name) and not name.endswith("_leaves"):
        return y + 1.0
    return None


def field_slalom_v2():
    c = Course2("field_slalom", "field_slalom", (52, 10, 52))
    flowers = [mc("poppy"), mc("dandelion"), mc("cornflower"), mc("oxeye_daisy"), mc("azure_bluet")]
    c.floor(lambda x, z: mc("grass_block"))
    y = PATH_TOP
    start = c.add("gate", 13, y, 15.5, 0)
    poles = [c.add("pole", 13, y, 21, 0, variant=LEFT), c.add("pole", 13, y, 26, 0, variant=RIGHT),
             c.add("pole", 13, y, 31, 0, variant=LEFT)]
    second = c.add("gate", 13, y, 36, 0)
    out = c.add("gate", 22.75, y, 44.5, 0, size="large")
    back = c.add("gate", 28.25, y, 44.5, 180, size="large")
    east = c.add("gate", 38, y, 36.5, 180)
    small = c.add("gate", 30, y, 26.5, 180, size="small")
    north = c.add("gate", 38, y, 15.5, 180)
    for piece in [start] + poles + [second, out, back, east, small, north]:
        c.go(piece)
    c.line = [(13, 2.5, 11), (13, 2.5, 18), (15.5, 2.5, 19.5), (15.5, 2.5, 22.5), (10.5, 2.5, 24.5), (10.5, 2.5, 27.5),
              (15.5, 2.5, 29.5), (15.5, 2.5, 32.5), (13, 2.5, 34.5), (13, 2.5, 38), (16, 2.5, 41), (21, 2.5, 42.5),
              (22.75, 2.5, 46), (24, 2.5, 48), (25.5, 2.5, 48.75), (27, 2.5, 48), (28.25, 2.5, 46), (28.5, 2.5, 42),
              (31, 2.5, 39.5), (36, 2.5, 38.5), (38, 2.5, 37.5), (38, 2.5, 34), (33.5, 2.5, 30.5), (30, 2.5, 28),
              (30, 2.5, 25), (33.5, 2.5, 21), (38, 2.5, 18), (38, 2.5, 13), (37, 2.5, 9.5), (33, 2.5, 7.5), (17, 2.5, 7.5),
              (13.5, 2.5, 9.5), (13, 2.5, 12), (13, 2.5, 18)]
    c.trail(1.5, mc("dirt_path"))
    for piece in c.pieces:
        c.pad(piece, mc("dirt_path"))
    for x in range(3, 10):
        for z in range(3, 10):
            edge = x in (3, 9) or z in (3, 9)
            mark = (x in (5, 7) and 5 <= z <= 7) or (x == 6 and z == 6)
            c.set(x, 0, z, mc("polished_andesite") if edge else mc("white_concrete") if mark else mc("smooth_stone"))
    c.fill(10, 0, 6, 12, 0, 7, mc("gravel"))
    c.sign(6, 1, 2, "south")
    c.set(7, 1, 2, mc("oak_fence"))
    c.set(7, 2, 2, mc("lantern"))
    c.place_chest(2, 1, 5, "east", "common")
    for pos in ((1, 1, 2), (2, 1, 2), (1, 1, 3), (1, 2, 2)):
        c.set(*pos, mc("hay_block"))
    for pos in ((24, 1, 32), (25, 1, 32), (24, 1, 33), (25, 2, 32), (26, 1, 33)):
        c.set(*pos, mc("hay_block"))
    for x, z in ((44, 44), (47, 3), (3, 47), (47, 47), (25, 20), (2, 28)):
        c.set(x, 1, z, mc("oak_fence"))
        c.set(x, 2, z, mc("oak_fence"))
        c.set(x, 3, z, mc("lantern"))
    for x in range(52):
        for z in range(52):
            if c.get(x, 0, z) == mc("grass_block") and c.get(x, 1, z) == mc("air"):
                roll = hashed(x, z, 1) % 100
                if roll < 6:
                    c.set(x, 1, z, flowers[hashed(x, z, 2) % len(flowers)])
                elif roll < 20:
                    c.set(x, 1, z, mc("short_grass"))
    c.clear_zones.append("racing")
    return [c]


def canyon_run_v2(template, palette):
    c = Course2("canyon_run", template, (31, 13, 66), sign_wood=palette["sign"], mode="point_to_point")
    c.floor(lambda x, z: palette["patch"] if hashed(x, z, 3) % 100 < 18 else palette["floor"])

    def column(x, z, height):
        for y in range(1, height + 1):
            c.set(x, y, z, palette["strata"][min(y - 1, len(palette["strata"]) - 1)])

    def peak(x, z):
        base = 7 + hashed(x // 2, z // 2, 4) % 4
        return 10 if 28 <= z <= 38 else base

    for z in range(4, 66):
        taper = 0 if z >= 7 else 7 - z
        for i, x in enumerate((0, 1, 2, 3)):
            column(x, z, max(1, peak(0, z) - (3 - i) - taper))
        for i, x in enumerate((30, 29, 28, 27)):
            column(x, z, max(1, peak(30, z) - (3 - i) - taper))
    for z in range(62, 66):
        for x in range(4, 27):
            column(x, z, max(1, peak(x, 62) - (z - 62) * 2))
    for z in range(10, 53):
        ends = min(z - 10, 52 - z)
        for x in range(13, 18):
            drop = abs(x - 15)
            column(x, z, max(1, min(peak(15, z), 3 + ends * 2) - drop))
    c.fill(4, 7, 30, 12, 8, 31, palette["arch"])
    c.fill(18, 7, 36, 26, 8, 37, palette["arch"])
    for z in range(4, 62):
        for x in (4, 26):
            c.set(x, 0, z, palette["strip"])
    for z in range(10, 53):
        for x in (12, 18):
            c.set(x, 0, z, palette["strip"])
    for x, z in ((4, 18), (26, 50), (12, 40), (18, 22), (4, 55), (26, 9)):
        if c.get(x, 1, z) == mc("air"):
            c.set(x, 1, z, mc("dead_bush"))
    start = c.add("gate", 8, 1, 12.5, 0)
    poles = [c.add("pole", 8, 1, 19, 0, variant=LEFT), c.add("pole", 8, 1, 25, 0, variant=RIGHT)]
    arch = c.add("gate", 8, 1, 35, 0, size="small")
    far = c.add("gate", 8, 1, 45, 0)
    flag = c.add("flag", 15.5, 1, 56, 0, variant=LEFT)
    back = c.add("gate", 22, 1, 45, 180)
    arch_back = c.add("gate", 22, 1, 31, 180, size="small")
    finish = c.add("gate", 22, 1, 14.5, 180, size="large")
    for piece in [start] + poles + [arch, far, flag, back, arch_back, finish]:
        c.go(piece)
    c.line = [(8, 2.5, 8), (8, 2.5, 15), (10, 2.5, 17.5), (10, 2.5, 20.5), (6, 2.5, 23.5), (6, 2.5, 26.5), (8, 2.5, 30),
              (8, 2.5, 37), (8, 2.5, 43), (8.5, 2.5, 47), (11, 2.5, 52), (12.5, 2.5, 57.5), (14, 2.5, 59.5), (17, 2.5, 59.5),
              (18.5, 2.5, 57.5), (19.5, 2.5, 53), (21.5, 2.5, 48.5), (22, 2.5, 43), (22, 2.5, 34), (22, 2.5, 29), (22, 2.5, 18),
              (22, 2.5, 10)]
    for x in range(12, 19):
        for z in range(1, 6):
            c.set(x, 0, z, palette["pad_edge"] if x in (12, 18) or z in (1, 5) else palette["pad"])
    c.sign(15, 1, 0, "south")
    c.place_chest(11, 1, 2, "east", "common")
    c.clear_zones.append("all")
    return c


def forest_weave_v2(template, palette):
    c = Course2("forest_weave", template, (50, 12, 50), sign_wood=palette["sign"])

    def pick(x, z):
        roll = hashed(x, z, 5) % 100
        if roll < 14:
            return mc("gravel")
        if roll < 28:
            return mc("packed_mud")
        if roll < 33:
            return mc("mossy_cobblestone")
        return mc("dirt_path")

    c.floor(pick)
    y = PATH_TOP
    start = c.add("gate", 10, y, 10.5, 0)
    poles = [c.add("gate_pole", 10, y, 16, 0, variant=POLE_COLORS["red"], length=6.0, side=LEFT),
             c.add("gate_pole", 10, y, 21, 0, variant=POLE_COLORS["white"], length=6.0, side=RIGHT),
             c.add("gate_pole", 10, y, 26, 0, variant=POLE_COLORS["red"], length=6.0, side=LEFT)]
    south = c.add("gate", 19.5, y, 37, 270)
    corner = c.add("gate", 33.5, y, 41, 270)
    east = c.add("gate", 40, y, 30.5, 180)
    small = c.add("gate", 32, y, 21.5, 180, size="small")
    north = c.add("gate", 40, y, 11.5, 180)
    large = c.add("gate", 25.5, y, 5, 90, size="large")
    for piece in [start] + poles + [south, corner, east, small, north, large]:
        c.go(piece)
    c.line = [(10, 2.5, 7), (10, 2.5, 13), (12, 2.5, 14.5), (12, 2.5, 17.5), (8, 2.5, 19.5), (8, 2.5, 22.5), (12, 2.5, 24.5),
              (12, 2.5, 27.5), (11.5, 2.5, 31), (13, 2.5, 34.5), (16, 2.5, 36.5), (18, 2.5, 37), (24, 2.5, 37), (28, 2.5, 37.5),
              (31, 2.5, 40), (32, 2.5, 41), (35, 2.5, 41), (38, 2.5, 39), (40, 2.5, 35), (40, 2.5, 29), (39.5, 2.5, 27),
              (35, 2.5, 23.5), (32, 2.5, 23), (32, 2.5, 20), (32.5, 2.5, 17), (34, 2.5, 14.5), (38, 2.5, 13), (40, 2.5, 12.5),
              (40, 2.5, 10), (39, 2.5, 7), (35, 2.5, 5), (28, 2.5, 5), (23, 2.5, 5), (17, 2.5, 4), (12, 2.5, 5.5), (10, 2.5, 8),
              (10, 2.5, 13)]
    for piece in c.pieces:
        c.path_under(piece)
    wood, shape = palette["wood"], palette["shape"]
    for x, z, h in ((5, 21, 6), (15, 18, 7), (16, 29, 6), (26, 39, 7), (36, 26, 6), (36, 16, 7), (17, 7, 6), (4, 31, 7),
                    (24, 24, 7), (29, 30, 6), (45, 22, 7), (46, 45, 6), (3, 46, 7), (21, 46, 6), (46, 3, 7), (31, 11, 6),
                    (45, 37, 6)):
        c.tree(x, z, wood, h + (2 if shape == "cone" else 0), shape=shape)
    c.fill(21, 1, 16, 25, 1, 16, mc(f"{wood}_log"), {"axis": "x"})
    for pos in ((42, 1, 45), (43, 1, 45), (42, 1, 46), (42, 2, 45)):
        c.set(*pos, mc("mossy_cobblestone"))
    blocked = {(x, z) for piece in c.pieces for box in piece_boxes(piece) for x, _, z in cells(box)}
    for x in range(50):
        for z in range(50):
            if (c.get(x, 1, z) == mc("air") and c.get(x, 0, z) == mc("dirt_path") and hashed(x, z, 6) % 100 < 7
                    and (x, z) not in blocked):
                c.set(x, 1, z, mc("moss_carpet"))
    for x in range(1, 7):
        for z in range(1, 7):
            c.set(x, 0, z, mc("mossy_cobblestone") if x in (1, 6) or z in (1, 6) else mc("cobblestone"))
    c.sign(3, 1, 0, "south")
    c.place_chest(0, 1, 3, "east", "common")
    c.clear_zones.append("all")
    return c


def drone_arena_v2():
    c = Course2("drone_arena", "drone_arena", (44, 12, 44))
    lane = [mc("stone_bricks"), mc("stone_bricks"), mc("cracked_stone_bricks"), mc("mossy_stone_bricks"), mc("cobblestone"),
            mc("gravel")]

    def pick(x, z):
        edge = min(x, z, 43 - x, 43 - z)
        roll = hashed(x, z, 7)
        if 3 <= edge <= 11:
            return lane[roll % len(lane)]
        return [mc("packed_mud"), mc("packed_mud"), mc("gravel"), mc("tuff")][roll % 4]

    c.floor(pick)
    ruin = [mc("stone_bricks"), mc("mossy_stone_bricks"), mc("cracked_stone_bricks")]
    for x in range(44):
        for z in range(44):
            if min(x, z, 43 - x, 43 - z) != 0 or (z == 0 and 19 <= x <= 24):
                continue
            height = hashed(x, z, 8) % 4
            for y in range(1, height + 1):
                c.set(x, y, z, ruin[hashed(x, y * 7 + z, 9) % 3])
    for z in range(14, 31):
        for step in range(3):
            x = 40 + step
            if hashed(x, z, 10) % 100 < 12:
                continue
            for y in range(1, step + 1):
                c.set(x, y, z, mc("stone_bricks"))
            c.set(x, step + 1, z, mc("stone_brick_stairs"), {"facing": "east"})
    for x, z, height in ((3, 3, 4), (40, 3, 4), (3, 40, 4), (40, 40, 2)):
        for y in range(1, height + 1):
            c.set(x, y, z, mc("cobblestone_wall"))
        if height == 4:
            c.set(x, height + 1, z, mc("lantern"))
    for x in range(14, 17):
        for z in range(33, 39):
            c.fill(x, 1, z, x, 2, z, mc("stone_bricks"))
    for x in range(32, 37):
        for z in range(10, 14):
            c.fill(x, 1, z, x, 2, z, mc("stone_bricks"))
    tower = [mc("cracked_stone_bricks"), mc("stone_bricks"), mc("mossy_stone_bricks")]
    for x in (21, 22):
        for z in (23, 24):
            for y in range(1, 4):
                c.set(x, y, z, tower[hashed(x, y * 5 + z, 12) % 3])
    start = c.add("gate", 11, 1, 13.5, 0)
    spur = c.add("gate", 16, 1, 23.5, 270)
    flag = c.add("flag", 22, 4, 24, 270, variant=LEFT, below=4.0)
    plinth = c.add("gate", 15.5, 3, 36, 270)
    loop = c.add("gate", 27.5, 1, 38, 270, size="large")
    ladder = c.add("gate", 36, 1, 29.5, 180)
    middle = c.stack_on(ladder, 0, "small")
    top = c.stack_on(middle, 180, "small")
    cube = c.add("cube", 34.5, 3, 12, 180, variant=EXIT_FRONT)
    home = c.add("gate", 27.5, 1, 6, 90, size="large")
    c.go(start)
    c.go(spur)
    c.go(flag)
    c.go(spur, reverse=True)
    c.go(plinth)
    c.go(loop)
    c.go(loop)
    c.go(ladder)
    c.go(middle)
    c.go(top)
    c.go(cube)
    c.go(home)
    c.line = [(11, 2.5, 8), (11, 2.5, 16), (11.5, 2.5, 19), (13, 2.5, 22.5), (17.5, 2.5, 24), (20, 2.5, 25.8), (23.5, 2.5, 26.2),
              (25, 2.5, 25), (25, 2.5, 23), (23.5, 2.5, 21.8), (20.5, 2.5, 21.8), (17.5, 2.5, 23), (14.5, 2.5, 23.5),
              (11.5, 2.5, 25.5), (10, 3.5, 29.5), (10.5, 4.5, 33.5), (13, 5, 36), (18, 5, 36), (22, 3.5, 37.5), (25, 2.5, 38),
              (30, 2.5, 38), (32.5, 4, 38), (33, 7, 38), (31, 9.5, 38), (27.5, 10, 38), (24, 9.5, 38), (22, 7, 38),
              (22.5, 4, 38), (25, 2.5, 38), (30, 2.5, 38), (33, 2.5, 37), (35.5, 2.5, 34), (36, 2.5, 31.5), (36, 2.5, 27.5),
              (36, 4, 25.5), (36, 6, 25.5), (36, 6, 27.5), (36, 6, 31.5), (36, 7.5, 33), (36, 8.5, 32), (36, 8.5, 31),
              (36, 8.5, 27), (35.5, 8.5, 20), (34.5, 8.2, 14.5), (34.5, 7.5, 12.5), (34.5, 5.5, 12), (34.5, 4.6, 11),
              (34.5, 4.2, 9.5), (33, 3.2, 7), (30, 2.5, 6), (25, 2.5, 6), (16, 2.5, 6.5), (12, 2.5, 8.5), (11, 2.5, 11),
              (11, 2.5, 16)]
    for x in range(2, 7):
        for z in range(3, 8):
            c.set(x, 0, z, mc("polished_andesite") if x in (2, 6) or z in (3, 7) else mc("smooth_stone"))
    for x, z, height in ((2, 3, 3), (6, 3, 3), (2, 7, 2), (6, 7, 3)):
        for y in range(1, height + 1):
            c.set(x, y, z, mc("oak_fence"))
    for x in range(2, 7):
        for z in range(3, 6):
            if hashed(x, z, 11) % 100 < 70:
                c.set(x, 4, z, mc("white_wool") if x % 2 == 0 else mc("red_wool"))
    for x, z, name in ((4, 6, "white_carpet"), (5, 6, "red_carpet"), (3, 5, "white_carpet")):
        c.set(x, 1, z, mc(name))
    c.sign(4, 1, 2, "south")
    c.place_chest(7, 1, 2, "south", "arena")
    c.clear_zones.append("all")
    return c


def current_courses():
    result = field_slalom_v2()
    result.append(canyon_run_v2("canyon_run_badlands", BADLANDS))
    result.append(canyon_run_v2("canyon_run_desert", DESERT))
    for key, palette in FOREST_PALETTES.items():
        result.append(forest_weave_v2(f"forest_weave_{key}", palette))
    result.append(drone_arena_v2())
    for course in result:
        course.air_above()
    return result


def courses():
    return legacy_courses() + current_courses()


VARIANTS = {
    "field_slalom": {
        "biomes": [mc("plains"), mc("sunflower_plains"), mc("meadow")],
        "template": "v2/field_slalom", "biome_templates": [], "max_slope": 10, "chance": 1.0, "weight": 4,
    },
    "canyon_run": {
        "biomes": [mc("badlands"), mc("wooded_badlands"), mc("eroded_badlands"), mc("desert")],
        "template": "v2/canyon_run_badlands",
        "biome_templates": [{"biomes": [mc("desert")], "template": "v2/canyon_run_desert"}],
        "max_slope": 12, "chance": 1.0, "weight": 3,
    },
    "forest_weave": {
        "biomes": [mc("forest"), mc("flower_forest"), mc("birch_forest"), mc("old_growth_birch_forest"), mc("taiga"),
                   mc("snowy_taiga")],
        "template": "v2/forest_weave_oak",
        "biome_templates": [
            {"biomes": [mc("birch_forest"), mc("old_growth_birch_forest")], "template": "v2/forest_weave_birch"},
            {"biomes": [mc("taiga"), mc("snowy_taiga")], "template": "v2/forest_weave_spruce"},
        ],
        "max_slope": 12, "chance": 1.0, "weight": 4,
    },
    "drone_arena": {
        "biomes": [mc("plains"), mc("savanna"), mc("snowy_plains")],
        "template": "v2/drone_arena", "biome_templates": [], "max_slope": 9, "chance": 0.35, "weight": 1,
    },
}
PLACEMENT = {"spacing": 32, "separation": 10, "salt": 1734215807}
AVOID = {"villages": 8, "pillager_outposts": 6, "woodland_mansions": 8, "desert_pyramids": 4, "jungle_temples": 4,
         "igloos": 4, "swamp_huts": 4, "abandoned_camp": 4, "ruined_portals": 3, "trail_ruins": 3}

part = survival.part


def vanilla_item(name, weight, count=None):
    return survival.item(mc(name), weight, count)


LOOT = {
    "common": {
        "common_rolls": (2, 4),
        "common": [part("p31_4b", 10, (2, 4)), part("p3_3b", 8, (2, 4)), part("p51_3b", 8, (2, 4)),
                   part("lipo_cell", 8, (1, 2)), part("copper_coil", 8, (1, 3)), part("carbon_plate", 7, (1, 2)),
                   part("circuit_board", 5), part("b1s_300_hv", 5), part("b4s_650", 4), part("m0802_22000", 4, (2, 4)),
                   part("m1404_4600", 3, 2), vanilla_item("bread", 6, (2, 4)), vanilla_item("string", 4, (2, 5)),
                   vanilla_item("torch", 4, (4, 8)), vanilla_item("copper_ingot", 4, (1, 3))],
        "bonus": [survival.empty(86), part("m2207_1750", 4, 2), part("analog_race", 3), part("aio_20", 2),
                  part("b6s_1100", 2), part("gate_medium", 3)],
    },
    "arena": {
        "common_rolls": (3, 5),
        "common": [part("p51_3b", 10, (2, 4)), part("p3_3b", 8, (2, 4)), part("p31_4b", 6, (2, 4)),
                   part("lipo_cell", 8, (1, 2)), part("copper_coil", 8, (1, 3)), part("carbon_plate", 8, (1, 3)),
                   part("circuit_board", 6, (1, 2)), part("b4s_650", 5), part("b1s_300_hv", 4),
                   part("m1404_4600", 4, 2), part("m2004_3000", 3, 2), vanilla_item("bread", 5, (2, 4)),
                   vanilla_item("torch", 4, (4, 8)), vanilla_item("copper_ingot", 4, (1, 3))],
        "bonus": [survival.empty(78), part("m2207_1750", 5, 2), part("analog_race", 4), part("aio_20", 3),
                  part("b6s_1100", 3), part("gate_large", 3), part("action_cam_light", 2), survival.whoop_drone(2)],
    },
}
ALLOWED_VANILLA_LOOT = {mc(n) for n in ("bread", "string", "torch", "copper_ingot")}
BETTER_PARTS = {"m2207_1750", "analog_race", "aio_20", "b6s_1100", "action_cam_light", "drone"}


def loot_table(spec):
    lo, hi = spec["common_rolls"]
    return {"type": mc("chest"), "pools": [
        {"rolls": {"type": mc("uniform"), "min": lo, "max": hi}, "entries": spec["common"]},
        {"rolls": 1, "entries": spec["bonus"]},
    ]}


def palette_blocks(course):
    ordered = sorted(course.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))
    palette = []
    index = {}
    blocks = []
    for (x, y, z), (name, props, nbt) in ordered:
        key = (name, props)
        if key not in index:
            index[key] = len(palette)
            entry = {"id": name}
            if props:
                entry["properties"] = dict(props)
            palette.append(entry)
        block = {"pos": nlist(3, [nint(x), nint(y), nint(z)]), "state": nint(index[key])}
        if nbt is not None:
            block["nbt"] = nbt
        blocks.append(block)
    return palette, blocks


def template_nbt(course):
    if isinstance(course, Course2):
        return template_nbt2(course)
    palette, blocks = palette_blocks(course)
    entities = []
    bx, by, bz = course.board
    for slot, gate in enumerate(course.gates):
        entities.append({
            "pos": nlist(6, [ndouble(gate.x), ndouble(gate.y), ndouble(gate.z)]),
            "blockPos": nlist(3, [nint(math.floor(gate.x)), nint(math.floor(gate.y)), nint(math.floor(gate.z))]),
            "nbt": {
                "id": p("gate"),
                "Rotation": nlist(5, [nfloat(gate.yaw), nfloat(0.0)]),
                "Size": gate.size,
                "GateIndex": nint(-1),
                "Course": nint(0),
                "WorldCourse": {
                    "Variant": course.variant,
                    "Slot": nint(slot),
                    "Local": nlist(6, [ndouble(gate.x), ndouble(gate.y), ndouble(gate.z)]),
                    "LocalYaw": nfloat(gate.yaw),
                    "Board": nints([bx, by, bz]),
                },
            },
        })
    sx, sy, sz = course.size
    return {
        "DataVersion": nint(DATA_VERSION),
        "size": nlist(3, [nint(sx), nint(sy), nint(sz)]),
        "palette": palette,
        "blocks": blocks,
        "entities": entities,
    }


def resolve_block_tag(name, seen=None):
    seen = seen or set()
    if VANILLA is None or name in seen:
        return set()
    seen.add(name)
    path = VANILLA / "data" / "minecraft" / "tags" / "block" / f"{name}.json"
    if not path.exists():
        return set()
    result = set()
    for value in json.loads(path.read_text())["values"]:
        ref = value if isinstance(value, str) else value["id"]
        if ref.startswith("#"):
            result |= resolve_block_tag(ref[1:].split(":", 1)[1], seen)
        else:
            result.add(ref)
    return result


def solid(name):
    return name not in NONSOLID and not name.endswith("_sign")


def grows_plants(course, x, z, soil, sand):
    name = course.get(x, 0, z)
    if name not in soil or course.get(x, 1, z) != mc("air"):
        return False
    if name in sand:
        return not any(course.inside(x + dx, 1, z + dz) and solid(course.get(x + dx, 1, z + dz))
                       for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    return True


def validate(course):
    t = course.template
    if course.board is None or course.chest is None:
        error(f"{t}: Schild oder Truhe fehlt")
        return
    if not 5 <= len(course.gates) <= 10:
        error(f"{t}: {len(course.gates)} Gates (erlaubt 5–10)")
    sx, sy, sz = course.size
    for x in range(sx):
        for z in range(sz):
            if course.get(x, 0, z) == mc("air"):
                error(f"{t}: Loch im Boden bei {x},{z}")
    names = {entry[0] for entry in course.blocks.values()}
    for name in sorted(names & FORBIDDEN):
        error(f"{t}: verbotener Block {name}")
    if VANILLA is not None:
        states = VANILLA / "assets" / "minecraft" / "blockstates"
        for name in sorted(names):
            if not (states / f"{name.split(':', 1)[1]}.json").exists():
                error(f"{t}: unbekannter Block {name}")
    for (x, y, z), (name, props, nbt) in course.blocks.items():
        family = prop_family(name)
        allowed = KNOWN_PROPS.get(family, set())
        for key, _ in props:
            if key not in allowed:
                error(f"{t}: Eigenschaft {key} an {name} nicht geprüft")
    sand = resolve_block_tag("supports_cactus")
    soil = resolve_block_tag("substrate_overworld") | sand
    for i, gate in enumerate(course.gates):
        width, height, (nx, nz), (sx_, sz_) = gate_geometry(gate)
        base = round(gate.y)
        if abs(gate.y - base) > 1e-9 and not gate.below:
            error(f"{t}: Gate {i} steht nicht auf einer Blockoberkante")
        for box in gate_boxes(gate):
            for cell in cells(box):
                if not course.inside(*cell):
                    error(f"{t}: Gate {i} ragt aus der Vorlage")
                elif solid(course.get(*cell)) or course.get(*cell) in THIN_COLLIDING:
                    error(f"{t}: Gate {i} steckt in {course.get(*cell)} bei {cell}")
        for cx, cy, cz in course.post_cells(gate):
            below = course.get(cx, cy, cz)
            if not solid(below) or below.endswith("_leaves") or below in NOT_STURDY:
                error(f"{t}: Gate {i} schwebt über {below} bei {cx},{cy},{cz}")
        inner = width / 2 - FRAME
        for step in range(-24, 25):
            d = step * 0.25
            for side in (-inner / 2, 0.0, inner / 2):
                for up in (1.5, height / 2, height - 1.5):
                    px = gate.x + nx * d + sx_ * side
                    pz = gate.z + nz * d + sz_ * side
                    cell = (math.floor(px), math.floor(gate.y + up), math.floor(pz))
                    if course.inside(*cell) and solid(course.get(*cell)):
                        error(f"{t}: Anflug von Gate {i} blockiert durch {course.get(*cell)} bei {cell}")
                        break
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                cx, cz = math.floor(gate.x) + dx, math.floor(gate.z) + dz
                if course.inside(cx, 0, cz) and grows_plants(course, cx, cz, soil, sand):
                    error(f"{t}: Gate {i}: Boden {course.get(cx, 0, cz)} bei {cx},{cz} lässt Bäume oder Kakteen wachsen")
    if "all" in course.clear_zones:
        for (x, y, z), (name, _, _) in course.blocks.items():
            if y == 0 and grows_plants(course, x, z, soil, sand):
                error(f"{t}: Boden {name} bei {x},{z} lässt Bäume oder Kakteen wachsen")
    length = 0.0
    for a, b in zip(course.gates, course.gates[1:] + course.gates[:1]):
        distance = math.hypot(b.x - a.x, b.z - a.z)
        length += distance
        if b.below is a or a.below is b:
            length += abs(b.y - a.y)
            continue
        if not 8.0 <= distance <= 30.0:
            error(f"{t}: Abstand zwischen Gates {distance:.1f} außerhalb 8–30")
    if not 80.0 <= length <= 260.0:
        error(f"{t}: Rundenlänge {length:.0f} außerhalb 80–260")
    bx, by, bz = course.board
    if not course.get(bx, by, bz).endswith("_sign"):
        error(f"{t}: kein Schild an der Tafel-Position")
    if not solid(course.get(bx, by - 1, bz)):
        error(f"{t}: Schild schwebt")
    return length


def validate2(course):
    t = course.template
    if course.board is None or course.chest is None:
        error(f"{t}: Schild oder Truhe fehlt")
        return 0.0
    if not 5 <= len(course.pieces) <= 12:
        error(f"{t}: {len(course.pieces)} Elemente (erlaubt 5–12)")
    if not 5 <= len(course.order) <= 14:
        error(f"{t}: {len(course.order)} Durchgänge (erlaubt 5–14)")
    if course.mode not in ("laps", "point_to_point"):
        error(f"{t}: unbekannter Modus {course.mode}")
    sx, sy, sz = course.size
    for x in range(sx):
        for z in range(sz):
            if course.get(x, 0, z) == mc("air"):
                error(f"{t}: Loch im Boden bei {x},{z}")
    names = {entry[0] for entry in course.blocks.values()}
    for name in sorted(names & FORBIDDEN):
        error(f"{t}: verbotener Block {name}")
    if VANILLA is not None:
        states = VANILLA / "assets" / "minecraft" / "blockstates"
        for name in sorted(names):
            if not (states / f"{name.split(':', 1)[1]}.json").exists():
                error(f"{t}: unbekannter Block {name}")
    for (x, y, z), (name, props, nbt) in course.blocks.items():
        allowed = KNOWN_PROPS.get(prop_family(name), set())
        for key, _ in props:
            if key not in allowed:
                error(f"{t}: Eigenschaft {key} an {name} nicht geprüft")
    sand = resolve_block_tag("supports_cactus")
    soil = resolve_block_tag("substrate_overworld") | sand
    for piece in course.pieces:
        label = f"{t}: {piece.label()}"
        if not piece.passes:
            error(f"{label} gehört zu keinem Durchgang")
        for value in (piece.x, piece.y, piece.z):
            if abs(value * 16 - round(value * 16)) > 1e-9:
                error(f"{label} liegt nicht im 1/16-Raster")
        for box in piece_boxes(piece):
            for cell in cells(box):
                name = course.get(*cell)
                if not course.inside(*cell):
                    error(f"{label} ragt aus der Vorlage")
                elif name in TOPS and box[1] >= cell[1] + TOPS[name] - 1e-9:
                    continue
                elif solid(name) or name in THIN_COLLIDING:
                    error(f"{label} steckt in {name} bei {cell}")
        row = math.floor(piece.y - 1e-6)
        for x, z in footprint(piece):
            top = surface(course, x, row, z) if course.inside(x, row, z) else None
            if top is None or abs(top - piece.y) > 1e-9:
                error(f"{label} steht nicht bündig auf {course.get(x, row, z)} bei {x},{row},{z} (Oberkante {top})")
        if piece.below is not None and abs(piece.y - (piece.below.y + GATE_SIZES[piece.below.size][1] - FRAME)) > 1e-9:
            error(f"{label} sitzt nicht auf dem Gate darunter")
        if piece.gate():
            width, height = piece.dims()
            (nx, nz), (gx, gz) = axes(piece.yaw)
            inner = width / 2 - FRAME
            low = FRAME if piece.below else 0.0
            for step in range(-APPROACH * 4, APPROACH * 4 + 1):
                d = step * 0.25
                for side in (-inner / 2, 0.0, inner / 2):
                    for up in (low + 1.0, height / 2, height - 1.0):
                        cell = (math.floor(piece.x + nx * d + gx * side), math.floor(piece.y + up), math.floor(piece.z + nz * d + gz * side))
                        if course.inside(*cell) and solid(course.get(*cell)):
                            error(f"{label}: Anflug blockiert durch {course.get(*cell)} bei {cell}")
                            break
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                cx, cz = math.floor(piece.x) + dx, math.floor(piece.z) + dz
                if course.inside(cx, 0, cz) and grows_plants(course, cx, cz, soil, sand):
                    error(f"{label}: Boden {course.get(cx, 0, cz)} bei {cx},{cz} lässt Bäume oder Kakteen wachsen")
    if "all" in course.clear_zones:
        for (x, y, z), (name, _, _) in course.blocks.items():
            if y == 0 and grows_plants(course, x, z, soil, sand):
                error(f"{t}: Boden {name} bei {x},{z} lässt Bäume oder Kakteen wachsen")
    laps = course.mode == "laps"
    pairs = list(zip(course.order, course.order[1:] + (course.order[:1] if laps else [])))
    for (a, _), (b, _) in pairs:
        if a is b or a.below is b or b.below is a:
            continue
        distance = math.hypot(b.x - a.x, b.z - a.z)
        if not 5.0 <= distance <= 30.0:
            error(f"{t}: Abstand {a.label()} → {b.label()} {distance:.1f} außerhalb 5–30")
    bx, by, bz = course.board
    if not course.get(bx, by, bz).endswith("_sign"):
        error(f"{t}: kein Schild an der Tafel-Position")
    if not solid(course.get(bx, by - 1, bz)):
        error(f"{t}: Schild schwebt")
    return check_line(course)


def check_line(course):
    t = course.template
    if len(course.line) < 2:
        error(f"{t}: keine Fluglinie")
        return 0.0
    length = sum(math.dist(a, b) for a, b in zip(course.line, course.line[1:]))
    laps = course.mode == "laps"
    if laps:
        start, reverse = course.order[0]
        crossings = sum(1 for a, b in zip(densify(course.line), densify(course.line)[1:]) if stages(start, reverse)[0].crossed(a, b))
        if crossings != 2:
            error(f"{t}: Fluglinie kreuzt das Start-Gate {crossings}× statt 2×")
    low, high = (80.0, 260.0) if laps else (60.0, 260.0)
    if not low <= length <= high:
        error(f"{t}: Fluglinie {length:.0f} Blöcke außerhalb {low:.0f}–{high:.0f}")
    boxes = [(piece, box) for piece in course.pieces for box in piece_boxes(piece)]
    points = densify(course.line)
    for point in points:
        cell = tuple(math.floor(v) for v in point)
        if not course.inside(*cell):
            error(f"{t}: Fluglinie verlässt die Vorlage bei {point}")
            break
        if solid(course.get(*cell)) or course.get(*cell) in THIN_COLLIDING:
            error(f"{t}: Fluglinie stößt bei {tuple(round(v, 2) for v in point)} an {course.get(*cell)}")
            break
        near = [piece for piece, box in boxes if box_distance(point, box) < CLEARANCE]
        if near:
            error(f"{t}: Fluglinie streift {near[0].label()} bei {tuple(round(v, 2) for v in point)}")
            break
    targets = []
    sequence = list(range(len(course.order))) + ([0] if laps else [])
    for k in sequence:
        piece, reverse = course.order[k]
        for j, plane in enumerate(stages(piece, reverse)):
            targets.append((k, j, plane, fouls(piece, reverse)))
    done = 0
    for a, b in zip(points, points[1:]):
        hit = False
        used = set()
        while (done < len(targets) and id(course.order[targets[done][0]][0]) not in used
               and targets[done][2].crossed(a, b)):
            if targets[done][1] == len(stages(*course.order[targets[done][0]])) - 1:
                used.add(id(course.order[targets[done][0]][0]))
            done += 1
            hit = True
        if done < len(targets) and any(foul.crossed(a, b) for foul in targets[done][3]):
            k = targets[done][0]
            error(f"{t}: Fluglinie fliegt Durchgang {k + 1} ({course.order[k][0].label()}) auf der falschen Seite")
        if hit:
            continue
        for k, (piece, _) in enumerate(course.order):
            if piece.gate() and any(stages(piece, r)[0].crossed(a, b) for r in (False, True)):
                error(f"{t}: Fluglinie fliegt bei {tuple(round(v, 2) for v in a)} durch {piece.label()}, erwartet Durchgang "
                      f"{targets[done][0] + 1 if done < len(targets) else '-'}")
                break
    if done < len(targets):
        k, j = targets[done][0], targets[done][1]
        error(f"{t}: Fluglinie verfehlt Durchgang {k + 1} ({course.order[k][0].label()}), Stufe {j + 1}")
    return length


def template_nbt2(course):
    palette, blocks = palette_blocks(course)
    entities = []
    bx, by, bz = course.board
    for piece in course.pieces:
        first = piece.passes[0][0]
        marker = {
            "Variant": course.variant,
            "Slot": nint(first),
            "Local": nlist(6, [ndouble(piece.x), ndouble(piece.y), ndouble(piece.z)]),
            "LocalYaw": nfloat(piece.yaw),
            "Board": nints([bx, by, bz]),
        }
        if len(piece.passes) > 1 or piece.passes[0][1]:
            marker["Passes"] = ",".join(f"{slot}{'r' if reverse else ''}" for slot, reverse in piece.passes)
        if course.mode != "laps":
            marker["Mode"] = course.mode
        nbt = {
            "id": p("gate"),
            "Rotation": nlist(5, [nfloat(piece.yaw), nfloat(0.0)]),
            "Size": piece.size if piece.gate() else "medium",
        }
        if not piece.gate():
            nbt["Kind"] = piece.kind
            nbt["Variant"] = nint(piece.variant)
        if piece.kind == "gate_pole" and piece.length:
            nbt["OpeningHeight"] = ndouble(piece.length)
        nbt["GateIndex"] = nint(-1)
        nbt["Course"] = nint(0)
        if piece.kind == "gate_pole":
            nbt["ScoreRole"] = "pass"
            nbt["ScoreSide"] = "left" if piece.side == LEFT else "right"
        if piece.score_below is not None:
            nbt["ScoreBelow"] = ndouble(piece.score_below)
        nbt["WorldCourse"] = marker
        entities.append({
            "pos": nlist(6, [ndouble(piece.x), ndouble(piece.y), ndouble(piece.z)]),
            "blockPos": nlist(3, [nint(math.floor(piece.x)), nint(math.floor(piece.y)), nint(math.floor(piece.z))]),
            "nbt": nbt,
        })
    sx, sy, sz = course.size
    return {
        "DataVersion": nint(DATA_VERSION),
        "size": nlist(3, [nint(sx), nint(sy), nint(sz)]),
        "palette": palette,
        "blocks": blocks,
        "entities": entities,
    }


def lines_text(course_list):
    out = ["{"]
    current = [c for c in course_list if isinstance(c, Course2)]
    for i, course in enumerate(current):
        points = ", ".join(f"[{x:g}, {y:g}, {z:g}]" for x, y, z in course.line)
        out.append(f'  "{course.name}": {{"mode": "{course.mode}", "passes": {len(course.order)}, "line": [{points}]}}'
                   + ("," if i < len(current) - 1 else ""))
    out.append("}")
    return "\n".join(out) + "\n"


def check_loot():
    for table, spec in LOOT.items():
        for pool in ("common", "bonus"):
            for entry in spec[pool]:
                if entry["type"] != mc("item"):
                    continue
                name = entry["name"]
                if name.startswith(NS + ":"):
                    short = name.split(":", 1)[1]
                    if short not in survival.PROPWASH_ITEMS - {"goggles_creative"}:
                        error(f"{table}: unbekanntes Item {name}")
                    if pool == "common" and short in BETTER_PARTS:
                        error(f"{table}: besseres Teil {name} im gewöhnlichen Pool")
                elif name not in ALLOWED_VANILLA_LOOT:
                    error(f"{table}: Vanilla-Item {name} nicht erlaubt")
        bonus = spec["bonus"]
        empty = sum(e["weight"] for e in bonus if e["type"] == mc("empty"))
        share = 1 - empty / sum(e["weight"] for e in bonus)
        if not 0.05 <= share <= 0.25:
            error(f"{table}: Chance auf besseres Teil {share:.0%} außerhalb 5–25 %")


def outputs(course_list):
    files = {}
    for course in course_list:
        files[TEMPLATES / f"{course.template}.nbt"] = nbt_bytes(template_nbt(course))
    for key, spec in VARIANTS.items():
        files[DATA / "worldgen" / "structure" / f"{key}.json"] = {
            "type": p("world_course"),
            "biomes": f"#{NS}:has_structure/{key}",
            "spawn_overrides": {},
            "step": "surface_structures",
            "terrain_adaptation": "beard_thin",
            "template": p(f"world_course/{spec['template']}"),
            "biome_templates": [{"biomes": e["biomes"], "template": p(f"world_course/{e['template']}")}
                                for e in spec["biome_templates"]],
            "max_slope": spec["max_slope"],
            "chance": spec["chance"],
        }
        files[DATA / "tags" / "worldgen" / "biome" / "has_structure" / f"{key}.json"] = {"values": spec["biomes"]}
    files[DATA / "worldgen" / "structure_set" / "world_courses.json"] = {
        "placement": {
            "type": p("world_course"),
            "avoid": [{"other_set": mc(name), "chunk_count": chunks} for name, chunks in AVOID.items()],
            "salt": PLACEMENT["salt"],
            "separation": PLACEMENT["separation"],
            "spacing": PLACEMENT["spacing"],
        },
        "structures": [{"structure": p(key), "weight": spec["weight"]} for key, spec in VARIANTS.items()],
    }
    files[DATA / "tags" / "worldgen" / "structure" / "world_courses.json"] = {"values": [p(k) for k in VARIANTS]}
    for table, spec in LOOT.items():
        files[DATA / "loot_table" / "chests" / "world_course" / f"{table}.json"] = loot_table(spec)
    files[LINES] = lines_text(course_list).encode("utf-8")
    return files


def check_references(course_list):
    if VANILLA is not None:
        for name in AVOID:
            if not (VANILLA / "data" / "minecraft" / "worldgen" / "structure_set" / f"{name}.json").exists():
                error(f"Strukturset {name} fehlt")
    templates = {c.template for c in course_list}
    for key, spec in VARIANTS.items():
        for name in [spec["template"]] + [e["template"] for e in spec["biome_templates"]]:
            if name not in templates:
                error(f"{key}: Vorlage {name} fehlt")
        for biome in spec["biomes"] + [b for e in spec["biome_templates"] for b in e["biomes"]]:
            if VANILLA is not None and not (VANILLA / "data" / "minecraft" / "worldgen" / "biome"
                                            / f"{biome.split(':', 1)[1]}.json").exists():
                error(f"{key}: Biom {biome} fehlt")
        for entry in spec["biome_templates"]:
            if not set(entry["biomes"]) <= set(spec["biomes"]):
                error(f"{key}: Vorlagen-Biome {entry['biomes']} nicht in den Struktur-Biomen")
    for course in course_list:
        if course.loot not in LOOT:
            error(f"{course.template}: Beutetabelle {course.loot} fehlt")
        if course.variant not in VARIANTS:
            error(f"{course.template}: Variante {course.variant} fehlt")


def check_files(files, write):
    for path, obj in files.items():
        data = obj if isinstance(obj, bytes) else (json.dumps(obj, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
        if write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        elif not path.exists() or path.read_bytes() != data:
            error(f"veraltet oder fehlt: {path.relative_to(REPO)} (python3 tools/data/world_courses.py ausführen)")
    for folder, pattern in ((TEMPLATES, "*.nbt"), (TEMPLATES / V2, "*.nbt"), (DATA / "worldgen" / "structure", "*.json"),
                            (DATA / "loot_table" / "chests" / "world_course", "*.json"),
                            (DATA / "tags" / "worldgen" / "biome" / "has_structure", "*.json")):
        for path in folder.glob(pattern):
            if path in files:
                continue
            if write:
                path.unlink()
            else:
                error(f"nicht vom Skript erzeugt: {path.relative_to(REPO)}")


def main():
    write = "--check" not in sys.argv
    course_list = courses()
    lengths = {}
    for course in course_list:
        lengths[course.template] = validate2(course) if isinstance(course, Course2) else validate(course)
    check_references(course_list)
    check_loot()
    check_files(outputs(course_list), write)
    if errors:
        for e in errors:
            print("FEHLER:", e)
        print(f"WORLD COURSES INVALID ({len(errors)} Fehler)")
        sys.exit(1)
    for course in course_list:
        if isinstance(course, Course2):
            kinds = ", ".join(f"{sum(1 for e in course.pieces if e.kind == k)} × {k}" for k in ("gate", "flag", "pole", "gate_pole", "cube")
                              if any(e.kind == k for e in course.pieces))
            print(f"  {course.template}: {len(course.order)} Durchgänge ({kinds}), {course.mode}, Fluglinie ca. "
                  f"{lengths[course.template]:.0f} Blöcke, {course.size[0]}×{course.size[2]} Blöcke")
        else:
            print(f"  {course.template} (alte Welten): {len(course.gates)} Gates, Runde ca. {lengths[course.template]:.0f} Blöcke, "
                  f"{course.size[0]}×{course.size[2]} Blöcke")
    legacy = sum(1 for c in course_list if not isinstance(c, Course2))
    print(f"WORLD COURSES OK ({len(course_list) - legacy} Vorlagen, {legacy} alte Vorlagen, {len(VARIANTS)} Strukturen, "
          f"{len(LOOT)} Beutetabellen)")


if __name__ == "__main__":
    main()
