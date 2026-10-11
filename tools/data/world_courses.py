"""Race courses found in the world: NBT templates, structures, structure set, tags and chest loot.

Run: python3 tools/data/world_courses.py            (writes all files, then validates them)
     python3 tools/data/world_courses.py --check    (validates only; exit code 1 on errors)

Outputs (never edit by hand, change this script instead):
  common/src/main/resources/data/propwash/structure/world_course/<template>.nbt
  common/src/main/resources/data/propwash/worldgen/structure/<variant>.json
  common/src/main/resources/data/propwash/worldgen/structure_set/world_courses.json
  common/src/main/resources/data/propwash/tags/worldgen/structure/world_courses.json
  common/src/main/resources/data/propwash/tags/worldgen/biome/has_structure/<variant>.json
  common/src/main/resources/data/propwash/loot_table/chests/world_course/<table>.json

Templates are built block by block from the layouts below, gzip-compressed with a fixed timestamp, so every run
produces byte-identical files. Gates are real propwash:gate entities with a WorldCourse marker (variant, course slot,
template-local position and yaw, template-local board position); the game turns them into a numbered course when they
first tick (race.world.WorldCourses). Balance and layouts: docs/design/world-courses.md.
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
    with gzip.GzipFile(fileobj=packed, mode="wb", mtime=0, filename="") as gz:
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


def courses():
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


VARIANTS = {
    "field_slalom": {
        "biomes": [mc("plains"), mc("sunflower_plains"), mc("meadow")],
        "template": "field_slalom", "biome_templates": [], "max_slope": 10, "chance": 1.0, "weight": 4,
    },
    "canyon_run": {
        "biomes": [mc("badlands"), mc("wooded_badlands"), mc("eroded_badlands"), mc("desert")],
        "template": "canyon_run_badlands",
        "biome_templates": [{"biomes": [mc("desert")], "template": "canyon_run_desert"}],
        "max_slope": 12, "chance": 1.0, "weight": 3,
    },
    "forest_weave": {
        "biomes": [mc("forest"), mc("flower_forest"), mc("birch_forest"), mc("old_growth_birch_forest"), mc("taiga"),
                   mc("snowy_taiga")],
        "template": "forest_weave_oak",
        "biome_templates": [
            {"biomes": [mc("birch_forest"), mc("old_growth_birch_forest")], "template": "forest_weave_birch"},
            {"biomes": [mc("taiga"), mc("snowy_taiga")], "template": "forest_weave_spruce"},
        ],
        "max_slope": 12, "chance": 1.0, "weight": 4,
    },
    "drone_arena": {
        "biomes": [mc("plains"), mc("savanna"), mc("snowy_plains")],
        "template": "drone_arena", "biome_templates": [], "max_slope": 9, "chance": 0.35, "weight": 1,
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


def template_nbt(course):
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
    for folder, pattern in ((TEMPLATES, "*.nbt"), (DATA / "worldgen" / "structure", "*.json"),
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
        lengths[course.template] = validate(course)
    check_references(course_list)
    check_loot()
    check_files(outputs(course_list), write)
    if errors:
        for e in errors:
            print("FEHLER:", e)
        print(f"WORLD COURSES INVALID ({len(errors)} Fehler)")
        sys.exit(1)
    for course in course_list:
        print(f"  {course.template}: {len(course.gates)} Gates, Runde ca. {lengths[course.template]:.0f} Blöcke, "
              f"{course.size[0]}×{course.size[2]} Blöcke")
    print(f"WORLD COURSES OK ({len(course_list)} Vorlagen, {len(VARIANTS)} Strukturen, {len(LOOT)} Beutetabellen)")


if __name__ == "__main__":
    main()
