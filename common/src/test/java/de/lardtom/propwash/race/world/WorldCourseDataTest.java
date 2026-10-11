package de.lardtom.propwash.race.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorldCourseDataTest {
    private static final Set<String> NONSOLID = Set.of("minecraft:air", "minecraft:short_grass", "minecraft:fern", "minecraft:poppy",
            "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:azure_bluet", "minecraft:dead_bush",
            "minecraft:moss_carpet", "minecraft:white_carpet", "minecraft:red_carpet", "minecraft:lantern");
    private static final Set<String> NOT_STURDY = Set.of("minecraft:dirt_path", "minecraft:farmland");
    private static final Set<String> FORBIDDEN = Set.of("minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault",
            "minecraft:command_block", "minecraft:structure_block", "minecraft:jigsaw", "minecraft:barrier");
    private static final Set<String> ALLOWED_VANILLA_LOOT = Set.of("minecraft:bread", "minecraft:string", "minecraft:torch",
            "minecraft:copper_ingot");
    private static final Set<String> BETTER_PARTS = Set.of("propwash:m2207_1750", "propwash:analog_race", "propwash:aio_20",
            "propwash:b6s_1100", "propwash:action_cam_light", "propwash:drone");
    private static final Set<String> TOP_TIER = Set.of("propwash:digital_hd", "propwash:m2306_1860", "propwash:b6s_1300",
            "propwash:b6s_4000_lion", "propwash:goggles_digital", "propwash:race_5", "propwash:stack_30", "propwash:hd_chip",
            "propwash:action_cam_heavy", "propwash:goggles_creative");

    private static Path common() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            if (Files.isDirectory(candidate.resolve("src/main/resources/data/propwash"))) {
                return candidate;
            }
        }
        fail("common-Modul nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static Path data() {
        return common().resolve("src/main/resources/data/propwash");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<Path> templates() throws IOException {
        try (Stream<Path> files = Files.list(data().resolve("structure/world_course"))) {
            return files.filter(p -> p.toString().endsWith(".nbt")).sorted().toList();
        }
    }

    private record Template(String name, int sx, int sy, int sz, Map<Long, String> blocks, Map<Long, CompoundTag> blockData,
                            List<CompoundTag> entities, int dataVersion) {
        static long key(int x, int y, int z) {
            return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF);
        }

        String at(int x, int y, int z) {
            return this.blocks.getOrDefault(key(x, y, z), "minecraft:air");
        }

        boolean inside(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < this.sx && y < this.sy && z < this.sz;
        }
    }

    private static Template load(Path file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        ListTag size = root.getListOrEmpty("size");
        ListTag palette = root.getListOrEmpty("palette");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            names.add(palette.getCompoundOrEmpty(i).getStringOr("id", ""));
        }
        Map<Long, String> blocks = new HashMap<>();
        Map<Long, CompoundTag> blockData = new HashMap<>();
        ListTag list = root.getListOrEmpty("blocks");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag block = list.getCompoundOrEmpty(i);
            ListTag pos = block.getListOrEmpty("pos");
            long key = Template.key(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
            blocks.put(key, names.get(block.getIntOr("state", 0)));
            block.getCompound("nbt").ifPresent(nbt -> blockData.put(key, nbt));
        }
        List<CompoundTag> entities = new ArrayList<>();
        ListTag entityList = root.getListOrEmpty("entities");
        for (int i = 0; i < entityList.size(); i++) {
            entities.add(entityList.getCompoundOrEmpty(i));
        }
        String name = file.getFileName().toString().replace(".nbt", "");
        return new Template(name, size.getIntOr(0, 0), size.getIntOr(1, 0), size.getIntOr(2, 0), blocks, blockData, entities,
                root.getIntOr("DataVersion", 0));
    }

    private static boolean solid(String name) {
        return !NONSOLID.contains(name) && !name.endsWith("_sign");
    }

    @Test
    void everyTemplateHasNumberedGatesStandingFreeOnSolidGround() throws IOException {
        List<Path> files = templates();
        assertFalse(files.isEmpty(), "keine Weltkurs-Vorlagen");
        for (Path file : files) {
            Template t = load(file);
            assertTrue(t.dataVersion() >= 4000, t.name() + ": DataVersion fehlt");
            for (int x = 0; x < t.sx(); x++) {
                for (int z = 0; z < t.sz(); z++) {
                    assertTrue(solid(t.at(x, 0, z)), t.name() + ": Loch im Boden bei " + x + "," + z);
                }
            }
            for (String name : new HashSet<>(t.blocks().values())) {
                assertFalse(FORBIDDEN.contains(name), t.name() + ": verbotener Block " + name);
            }
            int gates = t.entities().size();
            assertTrue(gates >= 5 && gates <= 10, t.name() + ": " + gates + " Gates");
            Set<Integer> slots = new TreeSet<>();
            Set<String> variants = new HashSet<>();
            Set<List<Integer>> boards = new HashSet<>();
            List<GateGeometry> all = new ArrayList<>();
            for (CompoundTag entity : t.entities()) {
                CompoundTag nbt = entity.getCompoundOrEmpty("nbt");
                ListTag pos = entity.getListOrEmpty("pos");
                all.add(GateGeometry.of(pos.getDoubleOr(0, 0), pos.getDoubleOr(1, 0), pos.getDoubleOr(2, 0),
                        nbt.getListOrEmpty("Rotation").getFloatOr(0, -1.0F), GateSize.byKey(nbt.getStringOr("Size", ""))));
            }
            int index = -1;
            for (CompoundTag entity : t.entities()) {
                index++;
                GateStacking.Base base = GateStacking.base(all, index);
                CompoundTag nbt = entity.getCompoundOrEmpty("nbt");
                assertEquals("propwash:gate", nbt.getStringOr("id", ""), t.name() + ": nur Gates erlaubt");
                assertEquals(0, nbt.getIntOr("Course", -5), t.name() + ": Gate ohne Kurs erwartet");
                CompoundTag marker = nbt.getCompoundOrEmpty("WorldCourse");
                slots.add(marker.getIntOr("Slot", -1));
                variants.add(marker.getStringOr("Variant", ""));
                int[] board = marker.getIntArray("Board").orElseThrow();
                boards.add(Arrays.stream(board).boxed().toList());
                ListTag pos = entity.getListOrEmpty("pos");
                ListTag local = marker.getListOrEmpty("Local");
                for (int i = 0; i < 3; i++) {
                    assertEquals(pos.getDoubleOr(i, -1), local.getDoubleOr(i, -2), 1.0E-9, t.name() + ": Local passt nicht zur Position");
                }
                float yaw = nbt.getListOrEmpty("Rotation").getFloatOr(0, -1.0F);
                assertEquals(yaw, marker.getFloatOr("LocalYaw", -2.0F), 1.0E-6F, t.name());
                assertEquals(GateGeometry.snapYaw(yaw), yaw, 1.0E-6F, t.name() + ": Gate nicht achsparallel");
                GateSize size = GateSize.byKey(nbt.getStringOr("Size", ""));
                assertEquals(size.key(), nbt.getStringOr("Size", ""), t.name() + ": unbekannte Gate-Größe");
                GateGeometry geometry = GateGeometry.of(pos.getDoubleOr(0, 0), pos.getDoubleOr(1, 0), pos.getDoubleOr(2, 0), yaw, size);
                if (base == GateStacking.Base.STACKED) {
                    assertTrue(GateStacking.check(all.get(GateStacking.below(all, index)), 1, size) == GateStacking.Refusal.NONE,
                            t.name() + ": gestapeltes Gate breiter als das darunter");
                } else {
                    assertEquals(Math.rint(geometry.y()), geometry.y(), 1.0E-9, t.name() + ": Gate nicht auf Blockoberkante");
                }
                List<double[]> boxes = new ArrayList<>();
                geometry.boxes((x0, y0, z0, x1, y1, z1) -> boxes.add(new double[]{x0, y0, z0, x1, y1, z1}), base);
                for (int b = 0; b < boxes.size(); b++) {
                    double[] box = boxes.get(b);
                    for (int x = (int) Math.floor(box[0] + 1.0E-6); x < Math.ceil(box[3] - 1.0E-6); x++) {
                        for (int z = (int) Math.floor(box[2] + 1.0E-6); z < Math.ceil(box[5] - 1.0E-6); z++) {
                            for (int y = (int) Math.floor(box[1] + 1.0E-6); y < Math.ceil(box[4] - 1.0E-6); y++) {
                                assertTrue(t.inside(x, y, z), t.name() + ": Gate ragt aus der Vorlage");
                                assertFalse(solid(t.at(x, y, z)), t.name() + ": Gate steckt in " + t.at(x, y, z) + " bei " + x + "," + y + "," + z);
                            }
                            if (b < 2 && base != GateStacking.Base.STACKED) {
                                String below = t.at(x, (int) Math.rint(geometry.y()) - 1, z);
                                assertTrue(solid(below) && !below.endsWith("_leaves") && !NOT_STURDY.contains(below),
                                        t.name() + ": Gate-Pfosten schwebt über " + below + " bei " + x + "," + z);
                            }
                        }
                    }
                }
            }
            if (t.name().startsWith("drone_arena")) {
                long stacked = IntStream.range(0, all.size()).filter(i -> GateStacking.base(all, i) == GateStacking.Base.STACKED).count();
                assertEquals(2, stacked, t.name() + ": Leiter aus drei Gates erwartet");
            }
            Set<Integer> expected = new TreeSet<>();
            for (int i = 0; i < gates; i++) {
                expected.add(i);
            }
            assertEquals(expected, slots, t.name() + ": Kursreihenfolge lückenhaft");
            assertEquals(1, variants.size(), t.name() + ": mehrere Varianten");
            WorldCourseVariant variant = WorldCourseVariant.byKey(variants.iterator().next()).orElseThrow();
            assertTrue(t.name().startsWith(variant.key()), t.name() + " gehört nicht zu " + variant.key());
            assertEquals(1, boards.size(), t.name() + ": Gates zeigen auf verschiedene Tafeln");
            List<Integer> board = boards.iterator().next();
            String sign = t.at(board.get(0), board.get(1), board.get(2));
            assertTrue(sign.endsWith("_sign"), t.name() + ": keine Tafel an " + board + " sondern " + sign);
            assertTrue(solid(t.at(board.get(0), board.get(1) - 1, board.get(2))), t.name() + ": Tafel schwebt");
            CompoundTag signData = t.blockData().get(Template.key(board.get(0), board.get(1), board.get(2)));
            assertNotNull(signData, t.name() + ": Tafel ohne Text");
            assertEquals(1, signData.getByteOr("is_waxed", (byte) 0), t.name() + ": Tafel nicht gewachst");
            CompoundTag title = signData.getCompoundOrEmpty("front_text").getListOrEmpty("messages").getCompoundOrEmpty(0);
            assertEquals(variant.nameKey(), title.getStringOr("translate", ""), t.name() + ": Tafel ohne Kursnamen");
            int chests = 0;
            for (Map.Entry<Long, String> entry : t.blocks().entrySet()) {
                if (!entry.getValue().equals("minecraft:chest")) {
                    continue;
                }
                chests++;
                String table = t.blockData().get(entry.getKey()).getStringOr("LootTable", "");
                assertTrue(table.startsWith("propwash:chests/world_course/"), t.name() + ": Truhe ohne Weltkurs-Beute: " + table);
                assertTrue(Files.exists(data().resolve("loot_table/" + table.substring("propwash:".length()) + ".json")),
                        t.name() + ": Beutetabelle fehlt " + table);
            }
            assertEquals(1, chests, t.name() + ": genau eine Truhe erwartet");
        }
    }

    @Test
    void structuresSetAndTagsCoverEveryVariant() throws IOException {
        Set<String> templateNames = new HashSet<>();
        for (Path file : templates()) {
            templateNames.add("propwash:world_course/" + file.getFileName().toString().replace(".nbt", ""));
        }
        Set<String> expected = new TreeSet<>();
        for (WorldCourseVariant variant : WorldCourseVariant.values()) {
            String id = "propwash:" + variant.key();
            expected.add(id);
            JsonObject structure = read(data().resolve("worldgen/structure/" + variant.key() + ".json"));
            assertEquals("propwash:world_course", structure.get("type").getAsString(), id);
            assertEquals("surface_structures", structure.get("step").getAsString(), id);
            assertEquals("beard_thin", structure.get("terrain_adaptation").getAsString(), id);
            assertTrue(structure.getAsJsonObject("spawn_overrides").isEmpty(), id + ": keine Mob-Spawns");
            assertTrue(templateNames.contains(structure.get("template").getAsString()), id + ": Vorlage fehlt");
            for (JsonElement entry : structure.getAsJsonArray("biome_templates")) {
                assertTrue(templateNames.contains(entry.getAsJsonObject().get("template").getAsString()), id + ": Biom-Vorlage fehlt");
            }
            String biomes = structure.get("biomes").getAsString();
            assertEquals("#propwash:has_structure/" + variant.key(), biomes, id);
            JsonArray biomeList = read(data().resolve("tags/worldgen/biome/has_structure/" + variant.key() + ".json")).getAsJsonArray("values");
            assertFalse(biomeList.isEmpty(), id + ": keine Biome");
            for (JsonElement biome : biomeList) {
                assertFalse(biome.getAsString().contains("ocean") || biome.getAsString().contains("river"), id + ": Wasserbiom " + biome);
            }
            int slope = structure.get("max_slope").getAsInt();
            assertTrue(slope >= 2 && slope <= 12, id + ": max_slope " + slope);
        }
        JsonObject set = read(data().resolve("worldgen/structure_set/world_courses.json"));
        JsonObject placement = set.getAsJsonObject("placement");
        assertEquals("propwash:world_course", placement.get("type").getAsString());
        int spacing = placement.get("spacing").getAsInt();
        int separation = placement.get("separation").getAsInt();
        assertTrue(spacing >= 32 && spacing <= 128, "spacing " + spacing + " (selten, aber auffindbar)");
        assertTrue(separation >= spacing / 4 && separation < spacing, "separation " + separation);
        Map<String, Integer> avoid = new HashMap<>();
        for (JsonElement entry : placement.getAsJsonArray("avoid")) {
            avoid.put(entry.getAsJsonObject().get("other_set").getAsString(), entry.getAsJsonObject().get("chunk_count").getAsInt());
        }
        for (String important : List.of("minecraft:villages", "minecraft:pillager_outposts", "minecraft:woodland_mansions",
                "minecraft:desert_pyramids", "minecraft:jungle_temples", "minecraft:igloos", "minecraft:swamp_huts")) {
            assertTrue(avoid.getOrDefault(important, 0) >= 4, "Abstand zu " + important + " fehlt");
        }
        assertTrue(avoid.get("minecraft:villages") >= 8, "Abstand zu Dörfern zu klein");
        Set<String> inSet = new TreeSet<>();
        for (JsonElement entry : set.getAsJsonArray("structures")) {
            inSet.add(entry.getAsJsonObject().get("structure").getAsString());
        }
        assertEquals(expected, inSet);
        Set<String> inTag = new TreeSet<>();
        for (JsonElement entry : read(data().resolve("tags/worldgen/structure/world_courses.json")).getAsJsonArray("values")) {
            inTag.add(entry.getAsString());
        }
        assertEquals(expected, inTag);
    }

    @Test
    void chestLootStaysModest() throws IOException {
        try (Stream<Path> files = Files.list(data().resolve("loot_table/chests/world_course"))) {
            List<Path> tables = files.sorted().toList();
            assertFalse(tables.isEmpty());
            for (Path file : tables) {
                JsonArray pools = read(file).getAsJsonArray("pools");
                assertEquals(2, pools.size(), file + ": gewöhnlicher Pool und Bonus-Pool erwartet");
                for (int p = 0; p < pools.size(); p++) {
                    JsonObject pool = pools.get(p).getAsJsonObject();
                    int total = 0;
                    int empty = 0;
                    for (JsonElement element : pool.getAsJsonArray("entries")) {
                        JsonObject entry = element.getAsJsonObject();
                        int weight = entry.get("weight").getAsInt();
                        total += weight;
                        if (entry.get("type").getAsString().equals("minecraft:empty")) {
                            empty += weight;
                            continue;
                        }
                        String name = entry.get("name").getAsString();
                        assertFalse(TOP_TIER.contains(name), file + ": Spitzenteil " + name);
                        if (name.startsWith("minecraft:")) {
                            assertTrue(ALLOWED_VANILLA_LOOT.contains(name), file + ": Vanilla-Beute " + name);
                        }
                        if (p == 0) {
                            assertFalse(BETTER_PARTS.contains(name), file + ": besseres Teil im gewöhnlichen Pool " + name);
                        }
                    }
                    if (p == 1) {
                        double better = 1.0 - (double) empty / total;
                        assertTrue(better >= 0.05 && better <= 0.25, file + ": Chance auf besseres Teil " + better);
                    }
                }
            }
        }
    }

    @Test
    void namesAndBoardTextsAreTranslated() throws IOException {
        for (String lang : List.of("de_de", "en_us")) {
            JsonObject race = read(common().resolve("src/lang/race/" + lang + ".json"));
            for (WorldCourseVariant variant : WorldCourseVariant.values()) {
                assertTrue(race.has(variant.nameKey()), lang + ": " + variant.nameKey());
            }
            for (String key : List.of(WorldCourseBoard.COURSE_KEY, WorldCourseBoard.RECORD_KEY, WorldCourseBoard.NO_RECORD_KEY,
                    WorldCourseBoard.UNKNOWN_KEY)) {
                assertTrue(race.has(key), lang + ": " + key);
            }
            JsonObject server = read(common().resolve("src/lang/server/" + lang + ".json"));
            assertTrue(server.has("propwash.server.rule.world_courses"), lang + ": Serverregel");
        }
    }
}
