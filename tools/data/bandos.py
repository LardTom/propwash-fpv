"""Bandos (abandoned FPV spots): structures, biome tags, structure tag, structure set and chest loot.

Run: python3 tools/data/bandos.py            (writes all files, then validates them)
     python3 tools/data/bandos.py --check    (validates only; exit code 1 on errors)

Outputs (never edit by hand, change this script instead):
  common/src/main/resources/data/propwash/worldgen/structure/bando/<family>.json
  common/src/main/resources/data/propwash/tags/worldgen/biome/has_structure/bando/<family>.json
  common/src/main/resources/data/propwash/tags/worldgen/biome/bando/keep_away.json
  common/src/main/resources/data/propwash/tags/worldgen/structure/bandos.json
  common/src/main/resources/data/propwash/worldgen/structure_set/bandos.json
  common/src/main/resources/data/propwash/loot_table/chests/bando.json

The buildings themselves are not templates: bando.plan.* generates every bando procedurally from the seed of its
structure start. The families must match bando.plan.BandoFamily. Balance and layout rules: docs/design/bandos.md.
"""

import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import survival  # noqa: E402

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
DATA = REPO / "common" / "src" / "main" / "resources" / "data" / "propwash"
FAMILY_JAVA = REPO / "common" / "src" / "main" / "java" / "de" / "lardtom" / "propwash" / "bando" / "plan" / "BandoFamily.java"
NS = "propwash"
VANILLA = survival.VANILLA

errors = []


def error(message):
    errors.append(message)


def p(name):
    return f"{NS}:{name}"


def mc(name):
    return f"minecraft:{name}"


FAMILIES = {
    "field": {"terrain": "beard_box", "biomes": ["plains", "sunflower_plains", "meadow", "forest", "flower_forest", "birch_forest",
                                                 "old_growth_birch_forest", "dappled_forest", "savanna", "savanna_plateau", "taiga",
                                                 "old_growth_pine_taiga", "old_growth_spruce_taiga", "cherry_grove"]},
    "desert": {"terrain": "beard_box", "biomes": ["desert", "badlands", "eroded_badlands", "wooded_badlands"]},
    "snow": {"terrain": "beard_box", "biomes": ["snowy_plains", "snowy_taiga", "grove"]},
    "jungle": {"terrain": "beard_box", "biomes": ["jungle", "sparse_jungle", "bamboo_jungle"]},
    "coast": {"terrain": "none", "biomes": ["beach", "snowy_beach", "stony_shore", "ocean", "deep_ocean", "lukewarm_ocean",
                                            "deep_lukewarm_ocean", "warm_ocean", "cold_ocean", "deep_cold_ocean"]},
    "mountain": {"terrain": "beard_box", "biomes": ["windswept_hills", "windswept_gravelly_hills", "windswept_forest",
                                                    "windswept_savanna", "stony_peaks"]},
    "swamp": {"terrain": "beard_box", "biomes": ["swamp"]},
}

KEEP_AWAY = ["ice_spikes", "frozen_ocean", "deep_frozen_ocean"]

PLACEMENT = {"spacing": 44, "separation": 16, "salt": 1964823117}
AVOID = {"minecraft:woodland_mansions": 6, "minecraft:ocean_monuments": 4, "minecraft:desert_pyramids": 2, "minecraft:jungle_temples": 2,
         "minecraft:igloos": 2, "minecraft:swamp_huts": 2, "propwash:world_courses": 4}
OVERLAP = {"minecraft:ruined_portals": 16, "minecraft:shipwrecks": 32, "minecraft:ocean_ruins": 32, "minecraft:trail_ruins": 48,
           "minecraft:abandoned_camp": 32, "minecraft:villages": 96, "minecraft:pillager_outposts": 96}
UNDERGROUND = {"minecraft:mineshafts", "minecraft:buried_treasures", "minecraft:ancient_cities", "minecraft:trial_chambers",
               "minecraft:strongholds", "minecraft:nether_complexes", "minecraft:nether_fossils", "minecraft:end_cities"}

part = survival.part
WORN = 0.6


def worn(name, weight, count=None, condition=WORN):
    return survival.item(p(name), weight, count, components={p("part_condition"): condition})


def vanilla_item(name, weight, count=None):
    return survival.item(mc(name), weight, count)


LOOT = {
    "common_rolls": (2, 4),
    "common": [worn("p31_4b", 8, (2, 4)), worn("p3_3b", 7, (2, 4)), worn("p51_3b", 7, (2, 4)), part("p31_3b", 4, (2, 4)),
               part("lipo_cell", 7, (1, 2)), part("copper_coil", 7, (1, 3)), part("carbon_plate", 6, (1, 2)), part("circuit_board", 5),
               worn("m0802_22000", 4, (2, 4), 0.7), worn("m1404_4600", 3, 2, 0.7), part("b1s_300_hv", 4), part("b4s_650", 3),
               part("led_strip", 2), vanilla_item("string", 4, (2, 5)), vanilla_item("iron_nugget", 4, (3, 7)),
               vanilla_item("copper_ingot", 4, (1, 3)), vanilla_item("redstone", 3, (2, 5)), vanilla_item("torch", 3, (3, 6))],
    "bonus": [survival.empty(84), worn("m2207_1750", 4, 2), part("analog_race", 3), part("aio_20", 2), worn("freestyle_5", 2),
              part("b6s_1100", 2), part("action_cam_light", 1)],
}
ALLOWED_VANILLA = {mc(n) for n in ("string", "iron_nugget", "copper_ingot", "redstone", "torch")}
BETTER = {"m2207_1750", "analog_race", "aio_20", "freestyle_5", "b6s_1100", "action_cam_light"}
TOP_PARTS = {"digital_hd", "m2306_1860", "b6s_1300", "b6s_4000_lion", "goggles_digital", "race_5", "stack_30", "hd_chip",
             "action_cam_heavy", "goggles_creative", "gate_small", "gate_medium", "gate_large", "drone"}


def loot_table():
    lo, hi = LOOT["common_rolls"]
    return {"type": mc("chest"), "pools": [
        {"rolls": {"type": mc("uniform"), "min": lo, "max": hi}, "entries": LOOT["common"]},
        {"rolls": 1, "entries": LOOT["bonus"]},
    ]}


def outputs():
    files = {}
    for family, spec in FAMILIES.items():
        files[DATA / "worldgen" / "structure" / "bando" / f"{family}.json"] = {
            "type": p("bando"),
            "biomes": f"#{NS}:has_structure/bando/{family}",
            "spawn_overrides": {},
            "step": "surface_structures",
            "terrain_adaptation": spec["terrain"],
            "family": family,
            "avoid_overlap": [{"other_set": name, "reach": reach} for name, reach in OVERLAP.items()],
        }
        files[DATA / "tags" / "worldgen" / "biome" / "has_structure" / "bando" / f"{family}.json"] = {
            "values": [mc(b) for b in spec["biomes"]]}
    files[DATA / "tags" / "worldgen" / "biome" / "bando" / "keep_away.json"] = {"values": [mc(b) for b in KEEP_AWAY]}
    files[DATA / "tags" / "worldgen" / "structure" / "bandos.json"] = {"values": [p(f"bando/{f}") for f in FAMILIES]}
    files[DATA / "worldgen" / "structure_set" / "bandos.json"] = {
        "placement": {
            "type": p("world_course"),
            "avoid": [{"other_set": name, "chunk_count": chunks} for name, chunks in AVOID.items()],
            "salt": PLACEMENT["salt"],
            "separation": PLACEMENT["separation"],
            "spacing": PLACEMENT["spacing"],
        },
        "structures": [{"structure": p(f"bando/{f}"), "weight": 1} for f in FAMILIES],
    }
    files[DATA / "loot_table" / "chests" / "bando.json"] = loot_table()
    return files


def check_families():
    if not FAMILY_JAVA.exists():
        error(f"{FAMILY_JAVA.relative_to(REPO)} fehlt")
        return
    text = FAMILY_JAVA.read_text(encoding="utf-8")
    body = text[text.index("enum BandoFamily"):text.index(";", text.index("enum BandoFamily"))]
    java = [line.strip().split("(")[0].lower() for line in body.splitlines() if "(Terrain." in line]
    if java != list(FAMILIES):
        error(f"Familien in BandoFamily {java} passen nicht zu {list(FAMILIES)}")
    for family, spec in FAMILIES.items():
        shore = family == "coast"
        if spec["terrain"] != ("none" if shore else "beard_box"):
            error(f"{family}: Geländeanpassung {spec['terrain']} passt nicht zur Familie")


def check_references():
    seen = {}
    for family, spec in FAMILIES.items():
        for biome in spec["biomes"]:
            if biome in seen:
                error(f"Biom {biome} in {seen[biome]} und {family}")
            seen[biome] = family
            if VANILLA is not None and not (VANILLA / "data" / "minecraft" / "worldgen" / "biome" / f"{biome}.json").exists():
                error(f"{family}: Biom {biome} fehlt")
    for biome in KEEP_AWAY:
        if biome in seen:
            error(f"Biom {biome} in {seen[biome]} und in keep_away")
        if VANILLA is not None and not (VANILLA / "data" / "minecraft" / "worldgen" / "biome" / f"{biome}.json").exists():
            error(f"keep_away: Biom {biome} fehlt")
    for name in AVOID:
        ns, path = name.split(":", 1)
        if ns == "minecraft" and VANILLA is not None and not (VANILLA / "data" / "minecraft" / "worldgen" / "structure_set" / f"{path}.json").exists():
            error(f"Strukturset {name} fehlt")
        if ns == NS and not (DATA / "worldgen" / "structure_set" / f"{path}.json").exists():
            error(f"Strukturset {name} fehlt")
        if not 1 <= AVOID[name] <= 16:
            error(f"Abstand zu {name} außerhalb 1–16 Chunks")
    for name, reach in OVERLAP.items():
        path = name.split(":", 1)[1]
        if VANILLA is not None and not (VANILLA / "data" / "minecraft" / "worldgen" / "structure_set" / f"{path}.json").exists():
            error(f"Strukturset {name} fehlt")
        if name in AVOID:
            error(f"Strukturset {name} steht in avoid und avoid_overlap")
        if not 0 <= reach <= 128:
            error(f"Reichweite von {name} außerhalb 0–128 Blöcke")
    if VANILLA is not None:
        for path in sorted((VANILLA / "data" / "minecraft" / "worldgen" / "structure_set").glob("*.json")):
            name = f"minecraft:{path.stem}"
            if name not in AVOID and name not in OVERLAP and name not in UNDERGROUND:
                error(f"Vanilla-Strukturset {name} weder gemieden noch als unterirdisch eingetragen")
    if PLACEMENT["spacing"] <= PLACEMENT["separation"]:
        error("spacing muss größer als separation sein")


def check_loot():
    for pool in ("common", "bonus"):
        for entry in LOOT[pool]:
            if entry["type"] != mc("item"):
                continue
            name = entry["name"]
            if name.startswith(NS + ":"):
                short = name.split(":", 1)[1]
                if short not in survival.PROPWASH_ITEMS:
                    error(f"unbekanntes Item {name}")
                if short in TOP_PARTS:
                    error(f"Spitzenteil oder Gate {name} gehört nicht in eine Bando-Truhe")
                if pool == "common" and short in BETTER:
                    error(f"besseres Teil {name} im gewöhnlichen Pool")
            elif name not in ALLOWED_VANILLA:
                error(f"Vanilla-Item {name} nicht erlaubt")
    bonus = LOOT["bonus"]
    empty = sum(e["weight"] for e in bonus if e["type"] == mc("empty"))
    share = 1 - empty / sum(e["weight"] for e in bonus)
    if not 0.05 <= share <= 0.25:
        error(f"Chance auf besseres Teil {share:.0%} außerhalb 5–25 %")
    parts = sum(e["weight"] for e in LOOT["common"] if e["type"] == mc("item") and e["name"].startswith(NS + ":"))
    total = sum(e["weight"] for e in LOOT["common"])
    if parts / total < 0.7:
        error(f"Propwash-Teile nur {parts / total:.0%} des gewöhnlichen Pools (mindestens 70 %)")


def check_files(files, write):
    for path, obj in files.items():
        data = (json.dumps(obj, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
        if write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        elif not path.exists() or path.read_bytes() != data:
            error(f"veraltet oder fehlt: {path.relative_to(REPO)} (python3 tools/data/bandos.py ausführen)")
    for folder in (DATA / "worldgen" / "structure" / "bando", DATA / "tags" / "worldgen" / "biome" / "has_structure" / "bando",
                   DATA / "tags" / "worldgen" / "biome" / "bando"):
        for path in folder.glob("*.json"):
            if path in files:
                continue
            if write:
                path.unlink()
            else:
                error(f"nicht vom Skript erzeugt: {path.relative_to(REPO)}")


def main():
    write = "--check" not in sys.argv
    check_families()
    check_references()
    check_loot()
    check_files(outputs(), write)
    if errors:
        for e in errors:
            print("FEHLER:", e)
        print(f"BANDOS INVALID ({len(errors)} Fehler)")
        sys.exit(1)
    biomes = sum(len(spec["biomes"]) for spec in FAMILIES.values())
    print(f"BANDOS OK ({len(FAMILIES)} Strukturen, {biomes} Biome, Abstand {PLACEMENT['spacing']}/{PLACEMENT['separation']} Chunks)")


if __name__ == "__main__":
    main()
