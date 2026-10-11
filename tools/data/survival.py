"""Survival data for Propwash: crafting recipes, recipe advancements, chest-loot tables, NeoForge loot modifiers.

Run: python3 tools/data/survival.py            (writes all files, then validates them)
     python3 tools/data/survival.py --check    (validates only; exit code 1 on errors)

Outputs (never edit by hand, change this script instead):
  common/src/main/resources/data/propwash/recipe/<id>.json
  common/src/main/resources/data/propwash/recipe/special/<id>.json (special recipes computed in code, no recipe book)
  common/src/main/resources/data/propwash/advancement/recipes/<folder>/<id>.json
  common/src/main/resources/data/propwash/loot_table/inject/<table>.json
  neoforge/src/main/resources/data/propwash/loot_modifiers/<vanilla table path>.json
  common/src/main/resources/data/propwash/tags/item/parts.json and parts/<category>.json (item tags #propwash:parts
    and #propwash:parts/<category> with every built-in part; addons append their parts with "replace": false)
  docs/design/survival-tables.md (overview of every recipe and loot table)

The Fabric side injects the same tables in code (content.ChestLoot + fabric.FabricChestLoot); the JUnit test
SurvivalDataTest checks that ChestLoot and the NeoForge modifiers list the same targets.
Validation uses .ref/vanilla (item ids, item tags, recipe conflicts) when it exists.
"""

import json
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
DATA = REPO / "common" / "src" / "main" / "resources" / "data" / "propwash"
NEO_DATA = REPO / "neoforge" / "src" / "main" / "resources" / "data" / "propwash"
DOC = REPO / "docs" / "design" / "survival-tables.md"
NS = "propwash"


def main_checkout():
    marker = REPO / ".git"
    if marker.is_file():
        line = marker.read_text(encoding="utf-8").strip()
        if line.startswith("gitdir:"):
            gitdir = (REPO / line[len("gitdir:"):].strip()).resolve()
            common = gitdir / "commondir"
            if common.is_file():
                return (gitdir / common.read_text(encoding="utf-8").strip()).resolve().parent
    return None


def find_vanilla():
    main = main_checkout()
    for base in (REPO, *REPO.parents, *((main, *main.parents) if main else ())):
        candidate = base / ".ref" / "vanilla"
        if (candidate / "data" / "minecraft").is_dir():
            return candidate
    return None


VANILLA = find_vanilla()

MATERIALS = ("carbon_plate", "copper_coil", "circuit_board", "lipo_cell", "hd_chip")
PARTS = (
    "whoop_65", "cinewhoop_3", "freestyle_5", "race_5", "long_range_7",
    "aio_whoop", "aio_20", "stack_30",
    "m0802_22000", "m1404_4600", "m2004_3000", "m2207_1750", "m2306_1860", "m2807_1300",
    "p31_4b", "p31_3b", "p3_3b", "p51_3b", "p7_2b",
    "b1s_300_hv", "b4s_650", "b6s_1100", "b6s_1300", "b6s_4000_lion",
    "analog_nano", "analog_race", "digital_lite", "digital_hd",
    "led_strip", "action_cam_light", "action_cam_heavy", "action_cam_flashback", "gripper",
)
BATTERIES = ("b1s_300_hv", "b4s_650", "b6s_1100", "b6s_1300", "b6s_4000_lion")
OTHER_ITEMS = ("drone_workbench", "battery_charger", "goggles_analog", "goggles_digital", "remote",
               "gate_small", "gate_medium", "gate_large", "gate_pole", "race_flag", "slalom_pole", "dive_cube", "freeform_gate", "course_tool",
               "signal_repeater")
NOT_CRAFTABLE = ("goggles_creative", "creative_fc", "drone")
PROPWASH_ITEMS = set(MATERIALS) | set(PARTS) | set(OTHER_ITEMS) | set(NOT_CRAFTABLE)

FOUND_CHARGE = 0.4
WHOOP_BUILD = {"frame": "whoop_65", "stack": "aio_whoop", "motor": "m0802_22000", "prop": "p31_4b",
               "video": "analog_nano", "battery": "b1s_300_hv"}


def p(name):
    return f"{NS}:{name}"


def mc(name):
    return f"minecraft:{name}"


def tag(name):
    return f"#minecraft:{name}"


CATEGORY_FOLDERS = {"misc": "misc", "equipment": "tools", "redstone": "redstone", "building": "building_blocks"}
UNLOCK_ALL = "has_propwash_item"


class Recipe:
    def __init__(self, result, category, group, pattern=None, key=None, ingredients=None, count=1):
        self.result = result
        self.category = category
        self.folder = CATEGORY_FOLDERS[category]
        self.group = group
        self.pattern = pattern
        self.key = key
        self.ingredients = ingredients
        self.count = count

    def json(self):
        out = {"type": mc("crafting_shaped") if self.pattern else mc("crafting_shapeless"), "category": self.category}
        if self.group:
            out["group"] = self.group
        if self.pattern:
            out["key"] = self.key
            out["pattern"] = self.pattern
        else:
            out["ingredients"] = self.ingredients
        result = {"id": p(self.result)}
        if self.count > 1:
            result["count"] = self.count
        out["result"] = result
        return out

    def items(self):
        return list(self.key.values()) if self.pattern else list(self.ingredients)

    def unlock(self):
        return list(dict.fromkeys(self.items()))


def shaped(result, category, group, pattern, key, count=1):
    return Recipe(result, category, group, pattern=pattern, key=key, count=count)


def shapeless(result, category, group, ingredients, count=1):
    return Recipe(result, category, group, ingredients=ingredients, count=count)


RECIPES = [
    shaped("carbon_plate", "misc", None, ["CSC", "CSC"],
           {"C": tag("coals"), "S": mc("string")}, count=2),
    shaped("copper_coil", "misc", None, [" C ", "CNC", " C "],
           {"C": mc("copper_ingot"), "N": mc("iron_nugget")}, count=2),
    shaped("circuit_board", "misc", None, ["NRN", "GGG"],
           {"N": mc("copper_nugget"), "R": mc("redstone"), "G": mc("glass_pane")}, count=2),
    shaped("lipo_cell", "misc", None, ["KNK", "RLR"],
           {"K": mc("dried_kelp"), "N": mc("copper_nugget"), "R": mc("redstone"), "L": mc("lapis_lazuli")}, count=2),
    shaped("hd_chip", "misc", None, ["QAQ", "GBG"],
           {"Q": mc("quartz"), "A": mc("amethyst_shard"), "G": mc("gold_nugget"), "B": p("circuit_board")}),

    shaped("whoop_65", "equipment", "propwash_frame", ["K K", " N ", "K K"],
           {"K": mc("dried_kelp"), "N": mc("iron_nugget")}),
    shaped("cinewhoop_3", "equipment", "propwash_frame", ["K K", "CIC", "K K"],
           {"K": mc("dried_kelp"), "C": p("carbon_plate"), "I": mc("iron_ingot")}),
    shaped("freestyle_5", "equipment", "propwash_frame", ["C C", "CIC", "C C"],
           {"C": p("carbon_plate"), "I": mc("iron_ingot")}),
    shaped("race_5", "equipment", "propwash_frame", ["C C", "GIG", "C C"],
           {"C": p("carbon_plate"), "G": mc("gold_nugget"), "I": mc("iron_ingot")}),
    shaped("long_range_7", "equipment", "propwash_frame", ["CIC", "C C", "CIC"],
           {"C": p("carbon_plate"), "I": mc("iron_ingot")}),

    shapeless("aio_whoop", "equipment", "propwash_stack",
              [p("circuit_board"), mc("redstone"), mc("copper_nugget")]),
    shaped("aio_20", "equipment", "propwash_stack", ["RBR", "CBC"],
           {"R": mc("redstone"), "B": p("circuit_board"), "C": mc("copper_ingot")}),
    shaped("stack_30", "equipment", "propwash_stack", ["BRB", "CGC", "BRB"],
           {"B": p("circuit_board"), "R": mc("redstone"), "C": mc("copper_ingot"), "G": mc("gold_ingot")}),

    shaped("m0802_22000", "equipment", "propwash_motor", ["NLN"],
           {"N": mc("iron_nugget"), "L": p("copper_coil")}, count=2),
    shaped("m1404_4600", "equipment", "propwash_motor", ["LNL", "NRN"],
           {"L": p("copper_coil"), "N": mc("iron_nugget"), "R": mc("redstone")}, count=2),
    shaped("m2004_3000", "equipment", "propwash_motor", ["NLN", "LRL"],
           {"N": mc("iron_nugget"), "L": p("copper_coil"), "R": mc("redstone")}, count=2),
    shaped("m2207_1750", "equipment", "propwash_motor", ["ILI", "LRL"],
           {"I": mc("iron_ingot"), "L": p("copper_coil"), "R": mc("redstone")}, count=2),
    shaped("m2306_1860", "equipment", "propwash_motor", ["ILI", "LQL"],
           {"I": mc("iron_ingot"), "L": p("copper_coil"), "Q": mc("quartz")}, count=2),
    shaped("m2807_1300", "equipment", "propwash_motor", ["ILI", "LRL", "ILI"],
           {"I": mc("iron_ingot"), "L": p("copper_coil"), "R": mc("redstone")}, count=2),

    shaped("p31_4b", "equipment", "propwash_prop", [" K ", "KNK", " K "],
           {"K": mc("dried_kelp"), "N": mc("iron_nugget")}, count=4),
    shaped("p31_3b", "equipment", "propwash_prop", ["K K", " N "],
           {"K": mc("dried_kelp"), "N": mc("iron_nugget")}, count=4),
    shaped("p3_3b", "equipment", "propwash_prop", [" K ", "KNK"],
           {"K": mc("dried_kelp"), "N": mc("iron_nugget")}, count=4),
    shaped("p51_3b", "equipment", "propwash_prop", ["K K", " N ", " K "],
           {"K": mc("dried_kelp"), "N": mc("iron_nugget")}, count=4),
    shaped("p7_2b", "equipment", "propwash_prop", ["K  ", " C ", "  K"],
           {"K": mc("dried_kelp"), "C": p("carbon_plate")}, count=4),

    shapeless("b1s_300_hv", "equipment", "propwash_battery",
              [p("lipo_cell"), mc("redstone"), mc("copper_nugget")]),
    shaped("b4s_650", "equipment", "propwash_battery", ["LL", "LL", "NR"],
           {"L": p("lipo_cell"), "N": mc("copper_nugget"), "R": mc("redstone")}),
    shaped("b6s_1100", "equipment", "propwash_battery", ["LLL", "LLL", "NGN"],
           {"L": p("lipo_cell"), "N": mc("copper_nugget"), "G": mc("gold_ingot")}),
    shaped("b6s_1300", "equipment", "propwash_battery", ["LLL", "LLL", "GSG"],
           {"L": p("lipo_cell"), "G": mc("gold_ingot"), "S": mc("glowstone_dust")}),
    shaped("b6s_4000_lion", "equipment", "propwash_battery", ["LAL", "LDL", "LAL"],
           {"L": p("lipo_cell"), "A": mc("amethyst_shard"), "D": mc("diamond")}),

    shapeless("analog_nano", "equipment", "propwash_video",
              [p("circuit_board"), mc("glass_pane"), mc("copper_nugget")]),
    shaped("analog_race", "equipment", "propwash_video", ["GA", "BL"],
           {"G": mc("glass_pane"), "A": mc("lightning_rod"), "B": p("circuit_board"), "L": p("copper_coil")}),
    shaped("digital_lite", "equipment", "propwash_video", ["TA", "HB"],
           {"T": mc("tinted_glass"), "A": mc("lightning_rod"), "H": p("hd_chip"), "B": p("circuit_board")}),
    shaped("digital_hd", "equipment", "propwash_video", ["TAT", "GHG", " E "],
           {"T": mc("tinted_glass"), "A": mc("lightning_rod"), "G": mc("gold_ingot"), "H": p("hd_chip"), "E": mc("echo_shard")}),

    shaped("led_strip", "equipment", "propwash_accessory", ["TTT", "NBN"],
           {"T": mc("redstone_torch"), "N": mc("copper_nugget"), "B": p("circuit_board")}),
    shaped("action_cam_light", "equipment", "propwash_accessory", ["CGC", "BHB"],
           {"C": mc("copper_ingot"), "G": mc("glass_pane"), "B": p("circuit_board"), "H": p("hd_chip")}),
    shaped("action_cam_heavy", "equipment", "propwash_accessory", ["ITI", "LHL", "IBI"],
           {"I": mc("iron_ingot"), "T": mc("tinted_glass"), "L": p("lipo_cell"), "H": p("hd_chip"), "B": p("circuit_board")}),
    shaped("action_cam_flashback", "equipment", "propwash_accessory", [" K ", "GHG", "CBC"],
           {"K": mc("clock"), "G": mc("glass_pane"), "H": p("hd_chip"), "C": mc("copper_ingot"), "B": p("circuit_board")}),
    shaped("gripper", "equipment", "propwash_accessory", [" C ", "NBN", "H H"],
           {"C": p("copper_coil"), "N": mc("iron_nugget"), "B": p("circuit_board"), "H": mc("tripwire_hook")}),

    shaped("goggles_analog", "equipment", "propwash_goggles", ["LIL", "GBG"],
           {"L": mc("leather"), "I": mc("iron_ingot"), "G": mc("glass_pane"), "B": p("circuit_board")}),
    shaped("goggles_digital", "equipment", "propwash_goggles", ["LHL", "TGT"],
           {"L": mc("leather"), "H": p("hd_chip"), "T": mc("tinted_glass"), "G": mc("gold_ingot")}),
    shaped("remote", "equipment", "propwash_radio", [" T ", "LGL", "IBI"],
           {"T": mc("redstone_torch"), "L": mc("lever"), "G": mc("glass_pane"), "I": mc("iron_ingot"), "B": p("circuit_board")}),

    shaped("drone_workbench", "misc", None, ["IRI", "PTP", "P P"],
           {"I": mc("iron_ingot"), "R": mc("redstone"), "P": tag("planks"), "T": mc("crafting_table")}),
    shaped("battery_charger", "misc", None, ["IGI", "CBC", "SSS"],
           {"I": mc("iron_ingot"), "G": mc("glass_pane"), "C": mc("copper_ingot"), "B": p("circuit_board"),
            "S": mc("smooth_stone_slab")}),

    shaped("gate_small", "building", "propwash_gate", ["WTW", "S S", "S S"],
           {"W": tag("wool"), "T": mc("redstone_torch"), "S": mc("stick")}),
    shaped("gate_medium", "building", "propwash_gate", ["WGW", "B B", "B B"],
           {"W": tag("wool"), "G": mc("glowstone_dust"), "B": mc("iron_bars")}),
    shaped("gate_large", "building", "propwash_gate", ["GWG", "I I", "I I"],
           {"W": tag("wool"), "G": mc("glowstone_dust"), "I": mc("iron_ingot")}),
    shaped("gate_pole", "building", "propwash_gate", ["G", "B", "B"],
           {"G": mc("glowstone_dust"), "B": mc("iron_bars")}, count=2),
    shaped("race_flag", "building", "propwash_course", ["SW", "SW", "N "],
           {"S": mc("stick"), "W": tag("wool"), "N": mc("iron_nugget")}),
    shaped("slalom_pole", "building", "propwash_course", ["D", "S", "N"],
           {"D": mc("red_dye"), "S": mc("stick"), "N": mc("iron_nugget")}, count=2),
    shaped("dive_cube", "building", "propwash_course", ["BGB", "B B", "BBB"],
           {"B": mc("iron_bars"), "G": mc("glowstone_dust")}),
    shaped("freeform_gate", "building", "propwash_course", ["GRG", "R R", "GRG"],
           {"G": mc("glowstone_dust"), "R": mc("redstone")}),
    shaped("course_tool", "equipment", None, ["R", "P", "S"],
           {"R": mc("redstone"), "P": mc("paper"), "S": mc("stick")}),
    shaped("signal_repeater", "building", None, [" A ", "LBL", " I "],
           {"A": mc("lightning_rod"), "L": p("copper_coil"), "B": p("circuit_board"), "I": mc("iron_ingot")}, count=2),
]


def item(name, weight, count=None, charge=None, components=None):
    entry = {"type": mc("item"), "name": name, "weight": weight}
    modifiers = []
    if count is not None:
        lo, hi = count if isinstance(count, tuple) else (count, count)
        modifiers.append({"type": mc("set_count"), "count": lo if lo == hi else {"type": mc("uniform"), "min": lo, "max": hi}})
    comps = dict(components or {})
    if charge is not None:
        comps[p("battery_charge")] = charge
    if comps:
        modifiers.append({"type": mc("set_components"), "components": comps})
    if len(modifiers) == 1:
        entry["modifier"] = modifiers[0]
    elif modifiers:
        entry["modifier"] = modifiers
    return entry


def part(name, weight, count=None):
    return item(p(name), weight, count, charge=FOUND_CHARGE if name in BATTERIES else None)


def whoop_drone(weight):
    return item(p("drone"), weight, components={
        p("drone_build"): WHOOP_BUILD,
        p("battery_charge"): FOUND_CHARGE,
        mc("item_name"): {"translate": "propwash.content.preset.whoop"},
    })


SPECIAL_RECIPES = {
    "prop_repair": {
        "type": p("prop_repair"),
        "code": "content.PropRepairRecipe",
        "text": "zwei beschädigte Props derselben Sorte im 2×2- oder 3×3-Raster → ein Prop, Zustand addiert + 5 %, höchstens "
                "100 %, Komponenten vom ersten; dasselbe am Schleifstein (`mixin.content.GrindstoneMenuMixin`)",
    },
}


def empty(weight):
    return {"type": mc("empty"), "weight": weight}


LOOT = {
    "village_house": {
        "targets": ["chests/village/village_plains_house", "chests/village/village_desert_house",
                    "chests/village/village_savanna_house", "chests/village/village_snowy_house",
                    "chests/village/village_taiga_house"],
        "entries": [empty(85), part("p31_4b", 4, (2, 4)), part("p31_3b", 2, (2, 4)), part("copper_coil", 3, (1, 2)), part("circuit_board", 3),
                    part("lipo_cell", 2, (1, 2)), part("carbon_plate", 2, (1, 2)), part("m0802_22000", 1, 2)],
    },
    "village_toolsmith": {
        "targets": ["chests/village/village_toolsmith"],
        "entries": [empty(50), part("copper_coil", 10, (1, 3)), part("circuit_board", 8, (1, 2)), part("carbon_plate", 7, (1, 3)),
                    part("lipo_cell", 6, (1, 3)), part("m0802_22000", 5, (2, 4)), part("m1404_4600", 4, 2),
                    part("aio_whoop", 3), part("whoop_65", 3), part("analog_nano", 3), part("led_strip", 3), part("battery_charger", 1)],
    },
    "village_cartographer": {
        "targets": ["chests/village/village_cartographer"],
        "entries": [empty(60), part("analog_nano", 12), part("circuit_board", 10, (1, 2)), part("carbon_plate", 8, (1, 2)),
                    part("analog_race", 6), part("goggles_analog", 4), part("action_cam_light", 3)],
    },
    "abandoned_mineshaft": {
        "targets": ["chests/abandoned_mineshaft"],
        "entries": [empty(80), part("copper_coil", 8, (1, 3)), part("lipo_cell", 6, (1, 2)), part("circuit_board", 5, (1, 2)),
                    part("m1404_4600", 3, 2), part("m2004_3000", 2, 2), part("m2207_1750", 2, 2), part("b4s_650", 2), part("p3_3b", 2, 4),
                    part("whoop_65", 2), part("led_strip", 3)],
    },
    "simple_dungeon": {
        "targets": ["chests/simple_dungeon"],
        "entries": [empty(60), part("circuit_board", 8, (1, 2)), part("lipo_cell", 8, (1, 2)), part("copper_coil", 6, (1, 2)),
                    part("b1s_300_hv", 4), part("b4s_650", 3), part("analog_race", 3), part("m2207_1750", 3, 2),
                    whoop_drone(2), part("goggles_analog", 2), part("led_strip", 2), part("remote", 1)],
    },
    "temple": {
        "targets": ["chests/desert_pyramid", "chests/jungle_temple"],
        "entries": [empty(55), part("circuit_board", 8, (2, 3)), part("carbon_plate", 8, (2, 4)), part("aio_20", 5),
                    part("m2207_1750", 5, 2), part("p51_3b", 5, 4), part("analog_race", 4), part("stack_30", 3),
                    part("b6s_1100", 3), part("hd_chip", 1), part("digital_lite", 1), part("action_cam_light", 2)],
    },
    "pillager_outpost": {
        "targets": ["chests/pillager_outpost"],
        "entries": [empty(65), part("carbon_plate", 8, (1, 3)), part("p51_3b", 5, 4), part("m2207_1750", 5, 2),
                    part("analog_race", 5), part("freestyle_5", 3), part("remote", 3), part("goggles_analog", 3),
                    whoop_drone(3), part("action_cam_light", 2)],
    },
    "stronghold": {
        "targets": ["chests/stronghold_corridor", "chests/stronghold_crossing"],
        "entries": [empty(55), part("circuit_board", 9, (2, 4)), part("lipo_cell", 8, (2, 4)), part("stack_30", 4),
                    part("b6s_1100", 4), part("hd_chip", 3), part("digital_lite", 3), part("m2807_1300", 3, 2),
                    part("p7_2b", 3, 4), part("m2306_1860", 2, 2), part("b6s_1300", 2), part("race_5", 2),
                    part("long_range_7", 2), part("action_cam_heavy", 2)],
    },
    "shipwreck_supply": {
        "targets": ["chests/shipwreck_supply"],
        "entries": [empty(70), part("lipo_cell", 8, (1, 3)), part("p3_3b", 6, 4), part("carbon_plate", 6, (1, 2)),
                    part("p51_3b", 5, 4), part("b4s_650", 3), part("cinewhoop_3", 2)],
    },
    "trial_chambers": {
        "targets": ["chests/trial_chambers/supply", "chests/trial_chambers/intersection"],
        "entries": [empty(70), part("copper_coil", 8, (2, 4)), part("circuit_board", 6, (1, 3)), part("lipo_cell", 6, (1, 3)),
                    part("m2207_1750", 4, 2), part("aio_20", 3), part("analog_race", 3), part("led_strip", 2)],
    },
    "vault": {
        "targets": ["chests/trial_chambers/reward"],
        "entries": [empty(75), part("hd_chip", 5), part("digital_lite", 5), part("m2306_1860", 5, 2), part("stack_30", 4),
                    part("b6s_1300", 4), whoop_drone(2), part("action_cam_heavy", 2)],
    },
    "vault_ominous": {
        "targets": ["chests/trial_chambers/reward_ominous"],
        "entries": [empty(50), part("digital_hd", 8), part("m2306_1860", 8, 2), part("b6s_1300", 6), part("race_5", 6),
                    part("hd_chip", 6, 2), part("b6s_4000_lion", 5), part("goggles_digital", 5), whoop_drone(6),
                    part("action_cam_heavy", 4)],
    },
    "ancient_city": {
        "targets": ["chests/ancient_city"],
        "entries": [empty(45), part("hd_chip", 8, (1, 2)), part("lipo_cell", 8, (2, 4)), part("digital_lite", 6),
                    part("stack_30", 6), part("m2306_1860", 6, 2), part("digital_hd", 5), part("b6s_1300", 5),
                    part("b6s_4000_lion", 4), part("race_5", 4), part("goggles_digital", 3), part("action_cam_heavy", 3)],
    },
    "end_city": {
        "targets": ["chests/end_city_treasure"],
        "entries": [empty(50), part("digital_hd", 8), part("m2306_1860", 8, 2), part("b6s_1300", 6), part("b6s_4000_lion", 6),
                    part("race_5", 6), part("goggles_digital", 6), part("long_range_7", 5), part("m2807_1300", 5, 2),
                    part("action_cam_heavy", 4)],
    },
}


def loot_table(spec):
    return {"type": mc("chest"), "pools": [{"entries": spec["entries"], "rolls": 1}]}


def loot_modifier(target, table):
    return {
        "type": "neoforge:add_table",
        "condition": {"type": "neoforge:loot_table_id", "loot_table_id": mc(target)},
        "table": p("inject/" + table),
    }


def criterion_name(ref):
    if ref.startswith("#"):
        return "has_" + ref.split(":", 1)[1].replace("/", "_")
    return "has_" + ref.split(":", 1)[1]


def advancement(recipe):
    criteria = {}
    for ref in recipe.unlock():
        criteria[criterion_name(ref)] = {
            "conditions": {"items": [{"items": ref}]},
            "trigger": mc("inventory_changed"),
        }
    criteria[UNLOCK_ALL] = {
        "conditions": {"items": [{"items": [p(name) for name in sorted(PROPWASH_ITEMS)]}]},
        "trigger": mc("inventory_changed"),
    }
    criteria["has_the_recipe"] = {"conditions": {"recipes": p(recipe.result)}, "trigger": mc("recipe_unlocked")}
    return {
        "parent": mc("recipes/root"),
        "criteria": criteria,
        "requirements": [["has_the_recipe", *[criterion_name(ref) for ref in recipe.unlock()], UNLOCK_ALL]],
        "rewards": {"recipes": [p(recipe.result)]},
    }


def short(ref):
    return ref.replace("minecraft:", "").replace(NS + ":", "pw:")


def count_text(entry):
    modifiers = entry.get("modifier", [])
    for modifier in modifiers if isinstance(modifiers, list) else [modifiers]:
        if modifier["type"] == mc("set_count"):
            count = modifier["count"]
            return str(count) if isinstance(count, int) else f"{count['min']}–{count['max']}"
    return "1"


def overview():
    lines = [
        "# Überlebensmodus – Tabellen",
        "",
        "Erzeugt von `tools/data/survival.py` – nicht von Hand bearbeiten. Begründung und Stufen: `docs/design/survival.md`.",
        "`pw:` = `propwash:`, Vanilla ohne Namensraum, `#` = Item-Tag.",
        "",
        "## Rezepte",
        "",
        "| Ergebnis | Menge | Muster | Zutaten | Freischaltung durch |",
        "|---|---|---|---|---|",
    ]
    for recipe in RECIPES:
        if recipe.pattern:
            pattern = " / ".join("`" + row.replace(" ", "·") + "`" for row in recipe.pattern)
            ingredients = ", ".join(f"{c} = {short(v)}" for c, v in recipe.key.items())
        else:
            pattern = "formlos"
            ingredients = ", ".join(short(v) for v in recipe.ingredients)
        unlock = ", ".join(short(v) for v in recipe.unlock()) + ", jedes Propwash-Item"
        lines.append(f"| pw:{recipe.result} | {recipe.count} | {pattern} | {ingredients} | {unlock} |")
    lines += ["", "## Sonderrezepte", "",
              "Berechnet im Code, ohne Eintrag im Rezeptbuch und ohne Freischaltung.", "",
              "| Rezept | Typ | Klasse | Wirkung |", "|---|---|---|---|"]
    for name, spec in SPECIAL_RECIPES.items():
        lines.append(f"| pw:special/{name} | {short(spec['type'])} | `{spec['code']}` | {spec['text']} |")
    lines += ["", "## Truhen-Beute", "",
              "Je Truhe wird genau einmal aus der Propwash-Tabelle gewürfelt; „Chance“ = Wahrscheinlichkeit pro Truhe.", ""]
    for table, spec in LOOT.items():
        total = sum(e["weight"] for e in spec["entries"])
        nothing = sum(e["weight"] for e in spec["entries"] if e["type"] == mc("empty"))
        lines.append(f"### `pw:inject/{table}` – Fundchance {100.0 * (total - nothing) / total:.0f} %")
        lines.append("")
        lines.append("Truhen: " + ", ".join(f"`{t}`" for t in spec["targets"]))
        lines.append("")
        lines.append("| Item | Anzahl | Chance |")
        lines.append("|---|---|---|")
        for entry in spec["entries"]:
            if entry["type"] != mc("item"):
                continue
            name = short(entry["name"])
            if name == "pw:drone":
                name = "pw:drone (fertige Whoop-Drohne)"
            lines.append(f"| {name} | {count_text(entry)} | {100.0 * entry['weight'] / total:.1f} % |")
        lines.append("")
    return "\n".join(lines)


PART_CATEGORIES = ["frame", "stack", "motor", "prop", "video", "battery", "accessory"]
CREATIVE_PARTS = {"stack": ("creative_fc",)}


def part_tags():
    files = {}
    for category in PART_CATEGORIES:
        values = [p(recipe.result) for recipe in RECIPES if recipe.group == f"{NS}_{category}"]
        values += [p(name) for name in CREATIVE_PARTS.get(category, ())]
        if not values:
            error(f"Tag parts/{category}: keine eingebauten Teile gefunden")
        files[DATA / "tags" / "item" / "parts" / f"{category}.json"] = {"values": values}
    files[DATA / "tags" / "item" / "parts.json"] = {"values": [f"#{NS}:parts/{category}" for category in PART_CATEGORIES]}
    return files


def outputs():
    files = {}
    for recipe in RECIPES:
        files[DATA / "recipe" / f"{recipe.result}.json"] = recipe.json()
        files[DATA / "advancement" / "recipes" / recipe.folder / f"{recipe.result}.json"] = advancement(recipe)
    for name, spec in SPECIAL_RECIPES.items():
        files[DATA / "recipe" / "special" / f"{name}.json"] = {"type": spec["type"]}
    files.update(part_tags())
    for table, spec in LOOT.items():
        files[DATA / "loot_table" / "inject" / f"{table}.json"] = loot_table(spec)
        for target in spec["targets"]:
            files[NEO_DATA / "loot_modifiers" / f"{target}.json"] = loot_modifier(target, table)
    files[DOC] = overview()
    return files


errors = []


def error(msg):
    errors.append(msg)


def vanilla_items():
    if VANILLA is None:
        return None
    return {f"minecraft:{path.stem}" for path in (VANILLA / "assets" / "minecraft" / "items").glob("*.json")}


def vanilla_tag(name, cache={}):
    if VANILLA is None:
        return None
    if name in cache:
        return cache[name]
    cache[name] = set()
    path = VANILLA / "data" / "minecraft" / "tags" / "item" / f"{name.split(':', 1)[1]}.json"
    if not path.exists():
        return None
    out = set()
    for value in json.loads(path.read_text())["values"]:
        ref = value if isinstance(value, str) else value["id"]
        if ref.startswith("#"):
            out |= vanilla_tag(ref[1:]) or set()
        else:
            out.add(ref)
    cache[name] = out
    return out


def resolve(ref):
    if isinstance(ref, list):
        out = set()
        for r in ref:
            out |= resolve(r)
        return frozenset(out)
    if ref.startswith("#"):
        return frozenset(vanilla_tag(ref[1:]) or {ref})
    return frozenset({ref})


def check_refs():
    known = vanilla_items()
    for recipe in RECIPES:
        refs = recipe.items()
        names = [criterion_name(ref) for ref in recipe.unlock()]
        if len(set(names)) != len(names) or UNLOCK_ALL in names or "has_the_recipe" in names:
            error(f"{recipe.result}: doppelte Freischalt-Kriterien {names}")
        for ref in refs:
            if ref.startswith("#"):
                if VANILLA is not None and vanilla_tag(ref[1:]) is None:
                    error(f"{recipe.result}: unbekanntes Item-Tag {ref}")
            elif ref.startswith(NS + ":"):
                if ref.split(":", 1)[1] not in PROPWASH_ITEMS:
                    error(f"{recipe.result}: unbekanntes Propwash-Item {ref}")
            elif known is not None and ref not in known:
                error(f"{recipe.result}: unbekanntes Vanilla-Item {ref}")
        if recipe.pattern:
            width = len(recipe.pattern[0])
            used = set()
            for row in recipe.pattern:
                if len(row) != width or width > 3 or len(recipe.pattern) > 3:
                    error(f"{recipe.result}: Muster {recipe.pattern} ungültig")
                used |= {c for c in row if c != " "}
            if used != set(recipe.key):
                error(f"{recipe.result}: Schlüssel {sorted(recipe.key)} passt nicht zum Muster {recipe.pattern}")
            if any(row.strip() == "" for row in recipe.pattern) or all(row[0] == " " for row in recipe.pattern) \
                    or all(row[-1] == " " for row in recipe.pattern):
                error(f"{recipe.result}: Muster hat eine leere Randzeile/-spalte {recipe.pattern}")
        elif not 1 <= len(recipe.ingredients) <= 9:
            error(f"{recipe.result}: {len(recipe.ingredients)} Zutaten")
        if p(recipe.result) in recipe.items():
            error(f"{recipe.result}: Ergebnis ist eigene Zutat")
    results = Counter(r.result for r in RECIPES)
    for name, count in results.items():
        if count > 1:
            error(f"{name}: {count} Rezepte")
    craftable = set(MATERIALS) | set(PARTS) | set(OTHER_ITEMS)
    missing = craftable - set(results)
    if missing:
        error(f"ohne Rezept: {sorted(missing)}")
    for name in NOT_CRAFTABLE:
        if name in results:
            error(f"{name} darf kein Rezept haben")
    for recipe in RECIPES:
        if recipe.category not in CATEGORY_FOLDERS:
            error(f"{recipe.result}: unbekannte Rezeptbuch-Kategorie {recipe.category}")
    defined = {path.stem for path in (REPO / "common" / "src" / "main" / "resources" / "assets" / NS / "items").glob("*.json")}
    if defined and defined != PROPWASH_ITEMS:
        error(f"Propwash-Items im Skript weichen von assets/{NS}/items ab: fehlt {sorted(defined - PROPWASH_ITEMS)}, "
              f"zu viel {sorted(PROPWASH_ITEMS - defined)}")


def material_order():
    produced = {r.result: r for r in RECIPES}
    for recipe in RECIPES:
        for ref in recipe.items():
            if ref.startswith(NS + ":"):
                name = ref.split(":", 1)[1]
                if name not in MATERIALS:
                    error(f"{recipe.result}: Zutat {ref} ist kein Zwischenteil")
                elif name not in produced:
                    error(f"{recipe.result}: Zwischenteil {ref} hat kein Rezept")


def grid(recipe, mirrored=False):
    rows = [row[::-1] if mirrored else row for row in recipe.pattern]
    return len(rows[0]), len(rows), tuple(None if c == " " else resolve(recipe.key[c]) for row in rows for c in row)


def vanilla_grid(data, mirrored=False):
    rows = [row[::-1] if mirrored else row for row in data["pattern"]]
    return len(rows[0]), len(rows), tuple(None if c == " " else resolve(data["key"][c]) for row in rows for c in row)


def cells_overlap(a, b):
    if a[0] != b[0] or a[1] != b[1]:
        return False
    for x, y in zip(a[2], b[2]):
        if (x is None) != (y is None):
            return False
        if x is not None and not (x & y):
            return False
    return True


def multiset_overlap(a, b):
    if len(a) != len(b):
        return False
    b = list(b)

    def match(i, used):
        if i == len(a):
            return True
        for j, candidate in enumerate(b):
            if j not in used and a[i] & candidate:
                if match(i + 1, used | {j}):
                    return True
        return False

    return match(0, frozenset())


def flat(recipe_grid):
    return [c for c in recipe_grid[2] if c is not None]


def check_conflicts():
    ours = []
    for recipe in RECIPES:
        if recipe.pattern:
            ours.append((p(recipe.result), "shaped", grid(recipe), grid(recipe, True)))
        else:
            ours.append((p(recipe.result), "shapeless", [resolve(r) for r in recipe.ingredients], None))
    others = []
    if VANILLA is not None:
        for path in sorted((VANILLA / "data" / "minecraft" / "recipe").glob("*.json")):
            data = json.loads(path.read_text())
            if data["type"] == "minecraft:crafting_shaped":
                others.append((f"minecraft:{path.stem}", "shaped", vanilla_grid(data), vanilla_grid(data, True)))
            elif data["type"] == "minecraft:crafting_shapeless":
                others.append((f"minecraft:{path.stem}", "shapeless", [resolve(r) for r in data["ingredients"]], None))
    for i, (name, kind, a, a_mirror) in enumerate(ours):
        for other_name, other_kind, b, b_mirror in ours[i + 1:] + others:
            if kind == "shaped" and other_kind == "shaped":
                clash = cells_overlap(a, b) or cells_overlap(a, b_mirror)
            elif kind == "shapeless" and other_kind == "shapeless":
                clash = multiset_overlap(a, b)
            elif kind == "shaped":
                clash = multiset_overlap(flat(a), b)
            else:
                clash = multiset_overlap(a, flat(b))
            if clash:
                error(f"Rezeptkonflikt: {name} und {other_name}")


def check_loot():
    for table, spec in LOOT.items():
        weights = [e["weight"] for e in spec["entries"]]
        nothing = sum(e["weight"] for e in spec["entries"] if e["type"] == mc("empty"))
        if nothing <= 0 or nothing >= sum(weights):
            error(f"inject/{table}: Leer-Gewicht {nothing} von {sum(weights)}")
        for entry in spec["entries"]:
            if entry["type"] != mc("item"):
                continue
            name = entry["name"]
            if not name.startswith(NS + ":") or name.split(":", 1)[1] not in PROPWASH_ITEMS - {"goggles_creative", "creative_fc"}:
                error(f"inject/{table}: unerwartetes Item {name}")
        if VANILLA is not None:
            for target in spec["targets"]:
                if not (VANILLA / "data" / "minecraft" / "loot_table" / f"{target}.json").exists():
                    error(f"inject/{table}: Vanilla-Tabelle {target} fehlt")
    targets = [t for spec in LOOT.values() for t in spec["targets"]]
    for target, count in Counter(targets).items():
        if count > 1:
            error(f"{target}: {count} Injektionen")


def check_files(files, write):
    for path, obj in files.items():
        text = obj if isinstance(obj, str) else json.dumps(obj, indent=2, ensure_ascii=False) + "\n"
        if write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        elif not path.exists() or path.read_text(encoding="utf-8") != text:
            error(f"veraltet oder fehlt: {path.relative_to(REPO)} (python3 tools/data/survival.py ausführen)")
    expected = set(files)
    for folder, pattern in ((DATA / "recipe", "*.json"), (DATA / "recipe" / "special", "*.json"),
                            (DATA / "advancement" / "recipes", "*/*.json"),
                            (DATA / "loot_table" / "inject", "*.json"), (NEO_DATA / "loot_modifiers", "**/*.json"),
                            (DATA / "tags" / "item", "parts*.json"), (DATA / "tags" / "item" / "parts", "*.json")):
        for path in folder.glob(pattern):
            if path not in expected and write:
                path.unlink()
            elif path not in expected:
                error(f"nicht vom Skript erzeugt: {path.relative_to(REPO)}")


def main():
    write = "--check" not in sys.argv
    if VANILLA is None:
        print("Hinweis: .ref/vanilla nicht gefunden, Vanilla-Items, Tags und Rezeptkonflikte werden nicht geprüft")
    check_refs()
    material_order()
    check_conflicts()
    check_loot()
    check_files(outputs(), write)
    if errors:
        for e in errors:
            print("FEHLER:", e)
        print(f"SURVIVAL DATA INVALID ({len(errors)} Fehler)")
        sys.exit(1)
    print(f"SURVIVAL DATA OK ({len(RECIPES)} Rezepte, {len(LOOT)} Beutetabellen, "
          f"{sum(len(s['targets']) for s in LOOT.values())} Truhenarten)")


if __name__ == "__main__":
    main()
