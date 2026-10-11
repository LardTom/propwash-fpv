package de.lardtom.propwash.bando.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BandoPalettes {
    public record Entry(String state, int weight) {
    }

    public static final class Palette {
        private final String name;
        private final Map<Mat, List<Entry>> roles = new EnumMap<>(Mat.class);

        private Palette(String name) {
            this.name = name;
        }

        public String name() {
            return this.name;
        }

        Palette set(Mat mat, Object... weighted) {
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < weighted.length; i += 2) {
                entries.add(new Entry((String) weighted[i], (Integer) weighted[i + 1]));
            }
            this.roles.put(mat, List.copyOf(entries));
            return this;
        }

        Palette none(Mat mat) {
            this.roles.put(mat, List.of());
            return this;
        }

        public List<Entry> entries(Mat mat) {
            List<Entry> own = this.roles.get(mat);
            if (own != null) {
                return own;
            }
            List<Entry> fallback = DEFAULTS.roles.get(mat);
            return fallback == null ? List.of() : fallback;
        }

        public Map<Mat, List<Entry>> roles() {
            return Collections.unmodifiableMap(this.roles);
        }
    }

    private static final Palette DEFAULTS = new Palette("defaults")
            .set(Mat.WATER, "minecraft:water", 1)
            .set(Mat.CHEST, "minecraft:chest", 1)
            .set(Mat.LADDER, "minecraft:ladder", 1)
            .set(Mat.CHAIN, "minecraft:iron_chain", 1)
            .set(Mat.VINE, "minecraft:vine", 1)
            .set(Mat.MOSS, "minecraft:moss_carpet", 1)
            .set(Mat.FLOAT, "minecraft:lily_pad", 1)
            .set(Mat.FOLIAGE, "minecraft:oak_leaves[persistent=true]", 1)
            .set(Mat.WINDOW, "minecraft:glass_pane", 1)
            .set(Mat.FOUNDATION, "minecraft:cobblestone", 3, "minecraft:stone_bricks", 2, "minecraft:polished_andesite", 1)
            .set(Mat.DEBRIS, "minecraft:cobblestone", 3, "minecraft:polished_andesite", 2, "minecraft:mossy_cobblestone", 1)
            .set(Mat.DEBRIS_LOW, "minecraft:cobblestone_slab", 3, "minecraft:andesite_slab", 2, "minecraft:smooth_stone_slab", 1)
            .set(Mat.RAIL, "minecraft:iron_bars", 1)
            .set(Mat.BRACE, "minecraft:iron_bars", 1)
            .set(Mat.CARGO, "minecraft:hay_block", 1)
            .set(Mat.YARD, "minecraft:gravel", 1);

    private static final List<Palette> BARN = List.of(
            new Palette("barn_spruce")
                    .set(Mat.YARD, "minecraft:dirt_path", 4, "minecraft:gravel", 2, "minecraft:packed_mud", 1)
                    .set(Mat.FLOOR, "minecraft:spruce_planks", 4, "minecraft:packed_mud", 2, "minecraft:dark_oak_planks", 1)
                    .set(Mat.WALL, "minecraft:spruce_planks", 6, "minecraft:dark_oak_planks", 2, "minecraft:stripped_spruce_wood", 1)
                    .set(Mat.WALL_ALT, "minecraft:spruce_planks", 1)
                    .set(Mat.COLUMN, "minecraft:stripped_spruce_log", 3, "minecraft:spruce_log", 1)
                    .set(Mat.BEAM, "minecraft:spruce_log", 3, "minecraft:stripped_spruce_log", 1)
                    .set(Mat.ROOF_STEP, "minecraft:dark_oak_stairs", 5, "minecraft:spruce_stairs", 2, "minecraft:mossy_cobblestone_stairs", 1)
                    .set(Mat.ROOF, "minecraft:dark_oak_slab", 1)
                    .set(Mat.TRIM, "minecraft:spruce_planks", 1)
                    .set(Mat.CATWALK, "minecraft:spruce_slab[type=top]", 1)
                    .set(Mat.RAIL, "minecraft:spruce_fence", 1)
                    .set(Mat.STACK, "minecraft:stone_bricks", 4, "minecraft:cracked_stone_bricks", 2, "minecraft:mossy_stone_bricks", 1)
                    .set(Mat.DEBRIS, "minecraft:spruce_planks", 2, "minecraft:cobblestone", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:spruce_slab", 3, "minecraft:dark_oak_slab", 2)
                    .set(Mat.CARGO, "minecraft:hay_block", 3, "minecraft:stripped_spruce_wood", 1),
            new Palette("barn_red")
                    .set(Mat.YARD, "minecraft:dirt_path", 4, "minecraft:gravel", 1)
                    .set(Mat.FLOOR, "minecraft:oak_planks", 3, "minecraft:packed_mud", 2)
                    .set(Mat.WALL, "minecraft:mangrove_planks", 6, "minecraft:stripped_mangrove_wood", 1, "minecraft:spruce_planks", 1)
                    .set(Mat.WALL_ALT, "minecraft:mangrove_planks", 1)
                    .set(Mat.COLUMN, "minecraft:stripped_dark_oak_log", 3, "minecraft:dark_oak_log", 1)
                    .set(Mat.BEAM, "minecraft:dark_oak_log", 2, "minecraft:stripped_dark_oak_log", 1)
                    .set(Mat.ROOF_STEP, "minecraft:deepslate_tile_stairs", 4, "minecraft:cobbled_deepslate_stairs", 2, "minecraft:dark_oak_stairs", 1)
                    .set(Mat.ROOF, "minecraft:deepslate_tile_slab", 1)
                    .set(Mat.TRIM, "minecraft:white_terracotta", 1)
                    .set(Mat.CATWALK, "minecraft:oak_slab[type=top]", 1)
                    .set(Mat.RAIL, "minecraft:dark_oak_fence", 1)
                    .set(Mat.STACK, "minecraft:white_terracotta", 3, "minecraft:light_gray_terracotta", 1, "minecraft:bricks", 1)
                    .set(Mat.DEBRIS, "minecraft:mangrove_planks", 2, "minecraft:cobblestone", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:mangrove_slab", 2, "minecraft:deepslate_tile_slab", 1)
                    .set(Mat.CARGO, "minecraft:hay_block", 1));

    private static final List<Palette> WAREHOUSE = List.of(
            new Palette("warehouse_copper")
                    .set(Mat.YARD, "minecraft:gravel", 3, "minecraft:dirt_path", 2, "minecraft:polished_andesite", 1)
                    .set(Mat.FLOOR, "minecraft:smooth_stone", 4, "minecraft:polished_andesite", 5, "minecraft:cobblestone", 1)
                    .set(Mat.WALL, "minecraft:light_gray_concrete", 4, "minecraft:stone_bricks", 2, "minecraft:cracked_stone_bricks", 1)
                    .set(Mat.WALL_ALT, "minecraft:waxed_weathered_cut_copper", 3, "minecraft:waxed_exposed_cut_copper", 2, "minecraft:waxed_oxidized_cut_copper", 1)
                    .set(Mat.COLUMN, "minecraft:polished_basalt", 1)
                    .set(Mat.BEAM, "minecraft:polished_basalt", 1)
                    .set(Mat.ROOF, "minecraft:waxed_weathered_cut_copper_slab", 3, "minecraft:waxed_oxidized_cut_copper_slab", 2, "minecraft:smooth_stone_slab", 1)
                    .set(Mat.ROOF_STEP, "minecraft:waxed_weathered_cut_copper_stairs", 1)
                    .set(Mat.TRIM, "minecraft:polished_basalt", 1)
                    .set(Mat.CATWALK, "minecraft:waxed_weathered_copper_grate", 1)
                    .set(Mat.CARGO, "minecraft:stripped_spruce_wood", 2, "minecraft:hay_block", 1, "minecraft:waxed_copper_grate", 1),
            new Palette("warehouse_concrete")
                    .set(Mat.YARD, "minecraft:gravel", 2, "minecraft:dirt_path", 2, "minecraft:cobblestone", 1)
                    .set(Mat.FLOOR, "minecraft:gray_concrete", 2, "minecraft:polished_andesite", 5)
                    .set(Mat.WALL, "minecraft:bricks", 4, "minecraft:light_gray_concrete", 2)
                    .set(Mat.WALL_ALT, "minecraft:white_concrete", 2, "minecraft:light_gray_concrete", 2, "minecraft:waxed_exposed_cut_copper", 1)
                    .set(Mat.COLUMN, "minecraft:light_gray_concrete", 3, "minecraft:polished_andesite", 1)
                    .set(Mat.BEAM, "minecraft:polished_basalt", 1)
                    .set(Mat.ROOF, "minecraft:smooth_stone_slab", 3, "minecraft:light_gray_concrete_slab", 2)
                    .set(Mat.ROOF_STEP, "minecraft:light_gray_concrete_stairs", 1)
                    .set(Mat.TRIM, "minecraft:light_gray_concrete", 1)
                    .set(Mat.CATWALK, "minecraft:smooth_stone_slab[type=top]", 1)
                    .set(Mat.CARGO, "minecraft:stripped_oak_wood", 2, "minecraft:hay_block", 1));

    private static final List<Palette> FACTORY = List.of(
            new Palette("factory_rust")
                    .set(Mat.YARD, "minecraft:smooth_sandstone", 2, "minecraft:cut_sandstone", 1, "minecraft:sandstone", 2)
                    .set(Mat.FLOOR, "minecraft:smooth_sandstone", 3, "minecraft:cut_sandstone", 2, "minecraft:terracotta", 2)
                    .set(Mat.FOUNDATION, "minecraft:sandstone", 2, "minecraft:smooth_sandstone", 1)
                    .set(Mat.WALL, "minecraft:bricks", 4, "minecraft:terracotta", 2, "minecraft:brown_terracotta", 1, "minecraft:mud_bricks", 1)
                    .set(Mat.WALL_ALT, "minecraft:waxed_exposed_cut_copper", 4, "minecraft:waxed_weathered_cut_copper", 1)
                    .set(Mat.COLUMN, "minecraft:polished_basalt", 1)
                    .set(Mat.BEAM, "minecraft:polished_basalt", 1)
                    .set(Mat.ROOF_STEP, "minecraft:waxed_exposed_cut_copper_stairs", 4, "minecraft:brick_stairs", 1)
                    .set(Mat.ROOF, "minecraft:waxed_exposed_cut_copper_slab", 1)
                    .set(Mat.TRIM, "minecraft:chiseled_sandstone", 1)
                    .set(Mat.STACK, "minecraft:bricks", 5, "minecraft:terracotta", 1, "minecraft:mud_bricks", 1)
                    .set(Mat.PIPE, "minecraft:waxed_exposed_cut_copper", 1)
                    .set(Mat.CATWALK, "minecraft:waxed_exposed_copper_grate", 1)
                    .set(Mat.DEBRIS, "minecraft:bricks", 2, "minecraft:sandstone", 2, "minecraft:terracotta", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:sandstone_slab", 2, "minecraft:brick_slab", 2)
                    .set(Mat.CARGO, "minecraft:cauldron", 3, "minecraft:stripped_acacia_wood", 1)
                    .none(Mat.MOSS).none(Mat.VINE).none(Mat.FOLIAGE));

    private static final List<Palette> WATER_TOWER = List.of(
            new Palette("water_tower_wood")
                    .set(Mat.YARD, "minecraft:sandstone", 2, "minecraft:smooth_sandstone", 1)
                    .set(Mat.FLOOR, "minecraft:smooth_sandstone", 3, "minecraft:cut_sandstone", 1)
                    .set(Mat.FOUNDATION, "minecraft:sandstone", 2, "minecraft:smooth_sandstone", 1)
                    .set(Mat.COLUMN, "minecraft:stripped_dark_oak_log", 1)
                    .set(Mat.BEAM, "minecraft:dark_oak_log", 1)
                    .set(Mat.BRACE, "minecraft:dark_oak_fence", 1)
                    .set(Mat.TANK, "minecraft:stripped_spruce_wood", 3, "minecraft:spruce_planks", 2)
                    .set(Mat.ROOF, "minecraft:dark_oak_slab", 2, "minecraft:spruce_slab", 1)
                    .set(Mat.CATWALK, "minecraft:spruce_slab[type=top]", 1)
                    .set(Mat.RAIL, "minecraft:dark_oak_fence", 1)
                    .set(Mat.WALL, "minecraft:terracotta", 2, "minecraft:orange_terracotta", 1, "minecraft:bricks", 1)
                    .set(Mat.PIPE, "minecraft:waxed_exposed_cut_copper", 1)
                    .set(Mat.CARGO, "minecraft:cauldron", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:sandstone_slab", 1)
                    .none(Mat.MOSS).none(Mat.VINE).none(Mat.FOLIAGE),
            new Palette("water_tower_steel")
                    .set(Mat.YARD, "minecraft:sandstone", 2, "minecraft:smooth_sandstone", 1)
                    .set(Mat.FLOOR, "minecraft:smooth_sandstone", 2, "minecraft:terracotta", 1)
                    .set(Mat.FOUNDATION, "minecraft:sandstone", 2, "minecraft:smooth_sandstone", 1)
                    .set(Mat.COLUMN, "minecraft:polished_basalt", 1)
                    .set(Mat.BEAM, "minecraft:polished_basalt", 1)
                    .set(Mat.TANK, "minecraft:waxed_exposed_cut_copper", 4, "minecraft:waxed_weathered_cut_copper", 1)
                    .set(Mat.ROOF, "minecraft:waxed_exposed_cut_copper_slab", 1)
                    .set(Mat.CATWALK, "minecraft:waxed_exposed_copper_grate", 1)
                    .set(Mat.WALL, "minecraft:bricks", 2, "minecraft:terracotta", 1)
                    .set(Mat.PIPE, "minecraft:waxed_exposed_cut_copper", 1)
                    .set(Mat.CARGO, "minecraft:cauldron", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:sandstone_slab", 1)
                    .none(Mat.MOSS).none(Mat.VINE).none(Mat.FOLIAGE));

    private static final List<Palette> REFINERY = List.of(
            new Palette("refinery")
                    .set(Mat.YARD, "minecraft:smooth_sandstone", 2, "minecraft:sandstone", 2, "minecraft:terracotta", 1)
                    .set(Mat.FLOOR, "minecraft:smooth_sandstone", 2, "minecraft:polished_andesite", 2, "minecraft:terracotta", 1)
                    .set(Mat.FOUNDATION, "minecraft:sandstone", 2, "minecraft:smooth_sandstone", 1)
                    .set(Mat.TANK, "minecraft:white_terracotta", 3, "minecraft:light_gray_concrete", 2, "minecraft:waxed_exposed_cut_copper", 1)
                    .set(Mat.ROOF, "minecraft:smooth_stone_slab", 2, "minecraft:waxed_exposed_cut_copper_slab", 1)
                    .set(Mat.STACK, "minecraft:waxed_exposed_cut_copper", 3, "minecraft:polished_andesite", 1)
                    .set(Mat.TRIM, "minecraft:red_concrete", 1)
                    .set(Mat.COLUMN, "minecraft:polished_basalt", 1)
                    .set(Mat.BEAM, "minecraft:polished_basalt", 1)
                    .set(Mat.PIPE, "minecraft:waxed_exposed_cut_copper", 3, "minecraft:waxed_weathered_cut_copper", 1)
                    .set(Mat.CATWALK, "minecraft:waxed_exposed_copper_grate", 1)
                    .set(Mat.WALL, "minecraft:bricks", 2, "minecraft:terracotta", 1)
                    .set(Mat.CARGO, "minecraft:cauldron", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:sandstone_slab", 1)
                    .none(Mat.MOSS).none(Mat.VINE).none(Mat.FOLIAGE));

    private static final List<Palette> COLLAPSED_HALL = List.of(
            new Palette("hall_rink")
                    .set(Mat.YARD, "minecraft:gravel", 2, "minecraft:polished_andesite", 1, "minecraft:cobblestone", 1)
                    .set(Mat.FLOOR, "minecraft:packed_ice", 3, "minecraft:polished_andesite", 1)
                    .set(Mat.WALL, "minecraft:stone_bricks", 3, "minecraft:cracked_stone_bricks", 2, "minecraft:polished_andesite", 1)
                    .set(Mat.WALL_ALT, "minecraft:spruce_planks", 2, "minecraft:stripped_spruce_wood", 1)
                    .set(Mat.COLUMN, "minecraft:polished_deepslate", 2, "minecraft:deepslate_bricks", 1)
                    .set(Mat.BEAM, "minecraft:stripped_spruce_log", 1)
                    .set(Mat.ROOF, "minecraft:spruce_slab", 2, "minecraft:deepslate_tile_slab", 2)
                    .set(Mat.ROOF_STEP, "minecraft:spruce_stairs", 1)
                    .set(Mat.TRIM, "minecraft:spruce_planks", 1)
                    .set(Mat.CATWALK, "minecraft:spruce_slab[type=top]", 1)
                    .set(Mat.RAIL, "minecraft:spruce_fence", 1)
                    .set(Mat.DEBRIS, "minecraft:snow[layers=8]", 2, "minecraft:packed_ice", 1, "minecraft:cobblestone", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:snow[layers=3]", 2, "minecraft:spruce_slab", 1, "minecraft:deepslate_tile_slab", 1)
                    .set(Mat.MOSS, "minecraft:snow[layers=1]", 1)
                    .set(Mat.CARGO, "minecraft:stripped_spruce_wood", 1)
                    .none(Mat.VINE).none(Mat.FOLIAGE),
            new Palette("hall_depot")
                    .set(Mat.YARD, "minecraft:gravel", 2, "minecraft:polished_andesite", 1)
                    .set(Mat.FLOOR, "minecraft:polished_andesite", 3, "minecraft:smooth_stone", 1)
                    .set(Mat.WALL, "minecraft:bricks", 3, "minecraft:stone_bricks", 1)
                    .set(Mat.WALL_ALT, "minecraft:white_concrete", 2, "minecraft:light_gray_concrete", 1)
                    .set(Mat.COLUMN, "minecraft:polished_deepslate", 1)
                    .set(Mat.BEAM, "minecraft:stripped_spruce_log", 2, "minecraft:polished_basalt", 1)
                    .set(Mat.ROOF, "minecraft:deepslate_tile_slab", 1)
                    .set(Mat.ROOF_STEP, "minecraft:deepslate_tile_stairs", 1)
                    .set(Mat.TRIM, "minecraft:polished_deepslate", 1)
                    .set(Mat.CATWALK, "minecraft:spruce_slab[type=top]", 1)
                    .set(Mat.RAIL, "minecraft:iron_bars", 1)
                    .set(Mat.DEBRIS, "minecraft:snow[layers=8]", 2, "minecraft:bricks", 1, "minecraft:packed_ice", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:snow[layers=3]", 2, "minecraft:brick_slab", 1)
                    .set(Mat.MOSS, "minecraft:snow[layers=1]", 1)
                    .set(Mat.CARGO, "minecraft:stripped_spruce_wood", 1)
                    .none(Mat.VINE).none(Mat.FOLIAGE));

    private static final List<Palette> CONCRETE_FRAME = List.of(
            new Palette("frame_concrete")
                    .set(Mat.YARD, "minecraft:cobblestone", 2, "minecraft:mossy_cobblestone", 2, "minecraft:dirt_path", 2)
                    .set(Mat.FLOOR, "minecraft:light_gray_concrete", 4, "minecraft:gray_concrete", 1, "minecraft:mossy_cobblestone", 1, "minecraft:polished_andesite", 1)
                    .set(Mat.COLUMN, "minecraft:light_gray_concrete", 4, "minecraft:gray_concrete", 1, "minecraft:mossy_stone_bricks", 1)
                    .set(Mat.BEAM, "minecraft:light_gray_concrete", 4, "minecraft:cracked_stone_bricks", 1, "minecraft:mossy_stone_bricks", 1)
                    .set(Mat.WALL, "minecraft:stone_bricks", 2, "minecraft:mossy_stone_bricks", 2, "minecraft:cracked_stone_bricks", 1)
                    .set(Mat.ROOF_STEP, "minecraft:light_gray_concrete_stairs", 2, "minecraft:stone_brick_stairs", 1)
                    .set(Mat.FOLIAGE, "minecraft:jungle_leaves[persistent=true]", 3, "minecraft:azalea_leaves[persistent=true]", 1,
                            "minecraft:flowering_azalea_leaves[persistent=true]", 1)
                    .set(Mat.DEBRIS, "minecraft:mossy_cobblestone", 2, "minecraft:cobblestone", 1, "minecraft:light_gray_concrete", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:light_gray_concrete_slab", 2, "minecraft:mossy_cobblestone_slab", 2)
                    .set(Mat.CARGO, "minecraft:mossy_cobblestone", 1));

    private static final List<Palette> PIER = List.of(
            new Palette("pier_wood")
                    .set(Mat.FOUNDATION, "minecraft:stone_bricks", 3, "minecraft:mossy_stone_bricks", 2, "minecraft:cobblestone", 1)
                    .set(Mat.FLOOR, "minecraft:spruce_planks", 4, "minecraft:dark_oak_planks", 1, "minecraft:oak_planks", 1)
                    .set(Mat.COLUMN, "minecraft:spruce_log", 3, "minecraft:dark_oak_log", 1)
                    .set(Mat.BEAM, "minecraft:dark_oak_log", 2, "minecraft:spruce_log", 1)
                    .set(Mat.RAIL, "minecraft:spruce_fence", 3, "minecraft:oak_fence", 1)
                    .set(Mat.TRIM, "minecraft:lantern", 1)
                    .set(Mat.WALL, "minecraft:spruce_planks", 2, "minecraft:stripped_spruce_wood", 1)
                    .set(Mat.WALL_ALT, "minecraft:stripped_spruce_wood", 1)
                    .set(Mat.ROOF, "minecraft:dark_oak_slab", 1)
                    .set(Mat.CARGO, "minecraft:dried_kelp_block", 2, "minecraft:stripped_spruce_wood", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:spruce_slab", 1),
            new Palette("pier_stone")
                    .set(Mat.FOUNDATION, "minecraft:stone_bricks", 2, "minecraft:mossy_stone_bricks", 2)
                    .set(Mat.FLOOR, "minecraft:oak_planks", 3, "minecraft:spruce_planks", 1)
                    .set(Mat.COLUMN, "minecraft:stone_bricks", 2, "minecraft:mossy_stone_bricks", 1)
                    .set(Mat.BEAM, "minecraft:stripped_oak_log", 1)
                    .set(Mat.RAIL, "minecraft:oak_fence", 1)
                    .set(Mat.TRIM, "minecraft:lantern", 1)
                    .set(Mat.WALL, "minecraft:oak_planks", 2, "minecraft:stripped_oak_wood", 1)
                    .set(Mat.WALL_ALT, "minecraft:white_terracotta", 1)
                    .set(Mat.ROOF, "minecraft:deepslate_tile_slab", 1)
                    .set(Mat.CARGO, "minecraft:dried_kelp_block", 2, "minecraft:stripped_oak_wood", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:oak_slab", 1));

    private static final List<Palette> PLATFORM = List.of(
            new Palette("platform_rust")
                    .set(Mat.COLUMN, "minecraft:waxed_exposed_cut_copper", 3, "minecraft:waxed_weathered_cut_copper", 1)
                    .set(Mat.BEAM, "minecraft:polished_basalt", 1)
                    .set(Mat.FLOOR, "minecraft:smooth_stone", 2, "minecraft:polished_andesite", 2, "minecraft:waxed_exposed_cut_copper", 1)
                    .set(Mat.CATWALK, "minecraft:waxed_exposed_copper_grate", 1)
                    .set(Mat.TRIM, "minecraft:red_concrete", 1)
                    .set(Mat.WALL_ALT, "minecraft:orange_concrete", 1)
                    .set(Mat.ROOF_STEP, "minecraft:andesite_stairs", 1)
                    .set(Mat.CARGO, "minecraft:cauldron", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:smooth_stone_slab", 1),
            new Palette("platform_yellow")
                    .set(Mat.COLUMN, "minecraft:yellow_concrete", 3, "minecraft:waxed_exposed_cut_copper", 1)
                    .set(Mat.BEAM, "minecraft:polished_basalt", 1)
                    .set(Mat.FLOOR, "minecraft:smooth_stone", 3, "minecraft:polished_andesite", 1)
                    .set(Mat.CATWALK, "minecraft:waxed_weathered_copper_grate", 1)
                    .set(Mat.TRIM, "minecraft:white_concrete", 1)
                    .set(Mat.WALL_ALT, "minecraft:blue_concrete", 1)
                    .set(Mat.ROOF_STEP, "minecraft:polished_andesite_stairs", 1)
                    .set(Mat.CARGO, "minecraft:cauldron", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:smooth_stone_slab", 1));

    private static final List<Palette> MINE_HEAD = List.of(
            new Palette("mine_wood")
                    .set(Mat.YARD, "minecraft:gravel", 3, "minecraft:cobblestone", 1, "minecraft:polished_andesite", 1)
                    .set(Mat.FLOOR, "minecraft:cobblestone", 3, "minecraft:stone_bricks", 2, "minecraft:polished_andesite", 1)
                    .set(Mat.FOUNDATION, "minecraft:cobblestone", 2, "minecraft:stone_bricks", 2)
                    .set(Mat.COLUMN, "minecraft:stripped_dark_oak_log", 3, "minecraft:dark_oak_log", 1)
                    .set(Mat.BEAM, "minecraft:dark_oak_log", 1)
                    .set(Mat.BRACE, "minecraft:dark_oak_fence", 1)
                    .set(Mat.TRIM, "minecraft:polished_deepslate", 1)
                    .set(Mat.CATWALK, "minecraft:dark_oak_planks", 1)
                    .set(Mat.RAIL, "minecraft:dark_oak_fence", 1)
                    .set(Mat.WALL, "minecraft:cobblestone", 3, "minecraft:mossy_cobblestone", 1, "minecraft:stone_bricks", 1)
                    .set(Mat.WALL_ALT, "minecraft:dark_oak_planks", 1)
                    .set(Mat.ROOF_STEP, "minecraft:dark_oak_stairs", 3, "minecraft:cobbled_deepslate_stairs", 1)
                    .set(Mat.ROOF, "minecraft:dark_oak_planks", 1)
                    .set(Mat.STACK, "minecraft:bricks", 1)
                    .set(Mat.DEBRIS, "minecraft:cobblestone", 2, "minecraft:polished_andesite", 1, "minecraft:polished_tuff", 1)
                    .set(Mat.CARGO, "minecraft:polished_tuff", 1, "minecraft:stripped_spruce_wood", 1),
            new Palette("mine_steel")
                    .set(Mat.YARD, "minecraft:gravel", 3, "minecraft:polished_andesite", 1)
                    .set(Mat.FLOOR, "minecraft:stone_bricks", 3, "minecraft:cracked_stone_bricks", 1)
                    .set(Mat.FOUNDATION, "minecraft:stone_bricks", 2, "minecraft:cobblestone", 1)
                    .set(Mat.COLUMN, "minecraft:polished_basalt", 1)
                    .set(Mat.BEAM, "minecraft:polished_basalt", 1)
                    .set(Mat.TRIM, "minecraft:red_terracotta", 1)
                    .set(Mat.CATWALK, "minecraft:waxed_weathered_copper_grate", 1)
                    .set(Mat.WALL, "minecraft:bricks", 3, "minecraft:stone_bricks", 1)
                    .set(Mat.WALL_ALT, "minecraft:waxed_weathered_cut_copper", 1)
                    .set(Mat.ROOF_STEP, "minecraft:deepslate_tile_stairs", 1)
                    .set(Mat.ROOF, "minecraft:deepslate_tiles", 1)
                    .set(Mat.STACK, "minecraft:bricks", 1)
                    .set(Mat.DEBRIS, "minecraft:cobblestone", 2, "minecraft:polished_tuff", 1)
                    .set(Mat.CARGO, "minecraft:polished_tuff", 1));

    private static final List<Palette> SUNKEN_WAREHOUSE = List.of(
            new Palette("swamp_mud")
                    .set(Mat.YARD, "minecraft:packed_mud", 3, "minecraft:mud_bricks", 1)
                    .set(Mat.FLOOR, "minecraft:mud_bricks", 2, "minecraft:packed_mud", 2)
                    .set(Mat.FOUNDATION, "minecraft:packed_mud", 1)
                    .set(Mat.WALL, "minecraft:mud_bricks", 3, "minecraft:packed_mud", 2, "minecraft:mossy_stone_bricks", 1)
                    .set(Mat.WALL_ALT, "minecraft:mangrove_planks", 1)
                    .set(Mat.COLUMN, "minecraft:stripped_mangrove_log", 2, "minecraft:mangrove_log", 1)
                    .set(Mat.BEAM, "minecraft:mangrove_log", 1)
                    .set(Mat.ROOF, "minecraft:waxed_weathered_cut_copper_slab", 2, "minecraft:mangrove_slab", 1, "minecraft:mud_brick_slab", 1)
                    .set(Mat.CATWALK, "minecraft:mangrove_slab[type=top]", 1)
                    .set(Mat.RAIL, "minecraft:mangrove_fence", 1)
                    .set(Mat.FOLIAGE, "minecraft:mangrove_leaves[persistent=true]", 1)
                    .set(Mat.DEBRIS, "minecraft:packed_mud", 1)
                    .set(Mat.DEBRIS_LOW, "minecraft:mud_brick_slab", 1)
                    .set(Mat.CARGO, "minecraft:mangrove_roots", 1));

    private BandoPalettes() {
    }

    public static List<Palette> of(BandoVariant variant) {
        return switch (variant) {
            case BARN -> BARN;
            case WAREHOUSE -> WAREHOUSE;
            case FACTORY -> FACTORY;
            case WATER_TOWER -> WATER_TOWER;
            case REFINERY -> REFINERY;
            case COLLAPSED_HALL -> COLLAPSED_HALL;
            case CONCRETE_FRAME -> CONCRETE_FRAME;
            case PIER -> PIER;
            case PLATFORM -> PLATFORM;
            case MINE_HEAD -> MINE_HEAD;
            case SUNKEN_WAREHOUSE -> SUNKEN_WAREHOUSE;
        };
    }

    public static Palette pick(BandoVariant variant, long seed) {
        List<Palette> options = of(variant);
        return options.get((int) Math.floorMod(seed >>> 17, (long) options.size()));
    }

    public static Palette defaults() {
        return DEFAULTS;
    }

    public static Set<String> allStates() {
        Set<String> states = new LinkedHashSet<>();
        for (Mat mat : Mat.values()) {
            for (Entry entry : DEFAULTS.entries(mat)) {
                states.add(entry.state());
            }
        }
        for (BandoVariant variant : BandoVariant.values()) {
            for (Palette palette : of(variant)) {
                for (List<Entry> entries : palette.roles.values()) {
                    for (Entry entry : entries) {
                        states.add(entry.state());
                    }
                }
            }
        }
        return states;
    }

    public static int pickIndex(List<Entry> entries, long hash) {
        int total = 0;
        for (Entry entry : entries) {
            total += entry.weight();
        }
        if (total <= 0) {
            return -1;
        }
        int r = (int) Math.floorMod(hash, (long) total);
        for (int i = 0; i < entries.size(); i++) {
            r -= entries.get(i).weight();
            if (r < 0) {
                return i;
            }
        }
        return entries.size() - 1;
    }

    public static long hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h & Long.MAX_VALUE;
    }
}
