package de.lardtom.propwash.bando;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoPalettes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BandoDataTest {
    private static final String VANILLA_PROBE = "data/minecraft/worldgen/structure_set/villages.json";
    private static final Set<String> JIGSAW_TOWNS = Set.of("minecraft:villages", "minecraft:pillager_outposts");
    private static final int JIGSAW_TOWN_REACH = 96;
    private static final Set<String> UNDERGROUND = Set.of("minecraft:mineshafts", "minecraft:buried_treasures", "minecraft:ancient_cities",
            "minecraft:trial_chambers", "minecraft:strongholds", "minecraft:nether_complexes", "minecraft:nether_fossils", "minecraft:end_cities");

    static Path root() {
        for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
            if (Files.isDirectory(candidate.resolve("common/src/main/resources/data/propwash"))) {
                return candidate;
            }
        }
        fail("Projektwurzel nicht gefunden");
        return Path.of(".");
    }

    static Path data() {
        return root().resolve("common/src/main/resources/data/propwash");
    }

    static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void structuresMatchTheFamilies() throws IOException {
        Set<String> tagged = new HashSet<>();
        for (JsonElement entry : read(data().resolve("tags/worldgen/structure/bandos.json")).getAsJsonArray("values")) {
            tagged.add(entry.getAsString());
        }
        assertEquals(BandoFamily.values().length, tagged.size());
        for (BandoFamily family : BandoFamily.values()) {
            String id = "propwash:bando/" + family.key();
            assertTrue(tagged.contains(id), id + " fehlt im Tag");
            JsonObject structure = read(data().resolve("worldgen/structure/bando/" + family.key() + ".json"));
            assertEquals("propwash:bando", structure.get("type").getAsString());
            assertEquals(family.key(), structure.get("family").getAsString());
            assertEquals("surface_structures", structure.get("step").getAsString());
            assertEquals(0, structure.getAsJsonObject("spawn_overrides").size(), family + ": keine Mob-Spawns");
            String terrain = structure.get("terrain_adaptation").getAsString();
            assertEquals(family == BandoFamily.COAST ? "none" : "beard_box", terrain, family + ": Geländeanpassung");
            String biomes = structure.get("biomes").getAsString();
            assertEquals("#propwash:has_structure/bando/" + family.key(), biomes);
            JsonArray list = read(data().resolve("tags/worldgen/biome/has_structure/bando/" + family.key() + ".json")).getAsJsonArray("values");
            assertTrue(list.size() >= (family == BandoFamily.SWAMP ? 1 : 2), family + ": zu wenige Biome");
            assertFalse(list.toString().contains("mangrove_swamp"), family + ": Mangrovensumpf ist zu dicht für Bandos");
        }
    }

    @Test
    void setIsRareAndKeepsDistance() throws IOException {
        JsonObject set = read(data().resolve("worldgen/structure_set/bandos.json"));
        JsonObject placement = set.getAsJsonObject("placement");
        assertEquals("propwash:world_course", placement.get("type").getAsString());
        int spacing = placement.get("spacing").getAsInt();
        int separation = placement.get("separation").getAsInt();
        assertTrue(spacing >= 32 && separation >= 8 && spacing > separation, "Abstand " + spacing + "/" + separation);
        Set<String> avoid = new HashSet<>();
        for (JsonElement entry : placement.getAsJsonArray("avoid")) {
            avoid.add(entry.getAsJsonObject().get("other_set").getAsString());
        }
        assertTrue(avoid.containsAll(Set.of("propwash:world_courses", "minecraft:woodland_mansions")), avoid.toString());
        assertEquals(BandoFamily.values().length, set.getAsJsonArray("structures").size());
    }

    @Test
    void everyVanillaStructureSetNearTheSurfaceIsAvoided() throws IOException {
        Set<String> avoid = new HashSet<>();
        for (JsonElement entry : read(data().resolve("worldgen/structure_set/bandos.json")).getAsJsonObject("placement").getAsJsonArray("avoid")) {
            avoid.add(entry.getAsJsonObject().get("other_set").getAsString());
        }
        Set<String> overlap = null;
        for (BandoFamily family : BandoFamily.values()) {
            Set<String> sets = new HashSet<>();
            for (JsonElement entry : read(data().resolve("worldgen/structure/bando/" + family.key() + ".json")).getAsJsonArray("avoid_overlap")) {
                JsonObject object = entry.getAsJsonObject();
                sets.add(object.get("other_set").getAsString());
                assertTrue(object.get("reach").getAsInt() >= 16, family + ": Reichweite zu klein für " + object);
                if (JIGSAW_TOWNS.contains(object.get("other_set").getAsString())) {
                    assertTrue(object.get("reach").getAsInt() >= JIGSAW_TOWN_REACH, family + ": Dorf oder Außenposten reicht weiter als " + object);
                }
            }
            assertTrue(overlap == null || overlap.equals(sets), family + ": andere Liste als die übrigen Familien " + sets);
            overlap = sets;
        }
        assertNotNull(overlap);
        assertTrue(overlap.containsAll(Set.of("minecraft:ruined_portals", "minecraft:shipwrecks", "minecraft:ocean_ruins", "minecraft:trail_ruins",
                "minecraft:abandoned_camp")), overlap.toString());
        assertTrue(overlap.containsAll(JIGSAW_TOWNS), overlap.toString());
        for (String set : JIGSAW_TOWNS) {
            assertTrue(!avoid.contains(set), set + " steht in avoid und avoid_overlap");
        }
        List<String> vanillaSets = new ArrayList<>();
        try (var files = Files.list(vanilla().resolve("data/minecraft/worldgen/structure_set"))) {
            for (Path file : files.toList()) {
                vanillaSets.add("minecraft:" + file.getFileName().toString().replace(".json", ""));
            }
        }
        assertTrue(vanillaSets.size() >= 20, "Vanilla-Struktursets nicht gelesen: " + vanillaSets);
        for (String set : vanillaSets) {
            assertTrue(avoid.contains(set) || overlap.contains(set) || UNDERGROUND.contains(set),
                    set + " ist weder gemieden noch als unterirdisch eingetragen (tools/data/bandos.py)");
        }
    }

    @Test
    void chestLootHasPropwashParts() throws IOException {
        JsonObject loot = read(data().resolve("loot_table/chests/bando.json"));
        assertEquals("minecraft:chest", loot.get("type").getAsString());
        JsonArray pools = loot.getAsJsonArray("pools");
        assertEquals(3, pools.size());
        int parts = 0;
        int worn = 0;
        for (JsonElement entry : pools.get(0).getAsJsonObject().getAsJsonArray("entries")) {
            JsonObject object = entry.getAsJsonObject();
            if (object.get("name").getAsString().startsWith("propwash:")) {
                parts++;
                if (object.toString().contains("propwash:part_condition")) {
                    worn++;
                }
            }
            assertTrue(!object.get("name").getAsString().contains("gate"), "keine Gates in Bando-Truhen");
        }
        assertTrue(parts >= 8 && worn >= 3, parts + " Teile, " + worn + " gebraucht");
    }

    @Test
    void paletteBlocksExistInVanilla() throws IOException {
        Path blockstates = vanilla().resolve("assets/minecraft/blockstates");
        assertTrue(Files.isDirectory(blockstates), "Blockzustände fehlen in " + blockstates);
        for (String state : BandoPalettes.allStates()) {
            String id = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
            assertTrue(id.startsWith("minecraft:"), state);
            assertTrue(Files.exists(blockstates.resolve(id.substring("minecraft:".length()) + ".json")), "Block fehlt in 26.3: " + id);
        }
    }

    static Path vanilla() throws IOException {
        URL probe = BandoDataTest.class.getClassLoader().getResource(VANILLA_PROBE);
        assertNotNull(probe, "Vanilla-Daten fehlen auf dem Klassenpfad: " + VANILLA_PROBE);
        try {
            URI uri = probe.toURI();
            if ("jar".equals(uri.getScheme())) {
                FileSystem jar;
                try {
                    jar = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException missing) {
                    jar = FileSystems.newFileSystem(uri, Map.of());
                }
                return jar.getPath("/");
            }
            Path file = Path.of(uri);
            return file.getRoot().resolve(file.subpath(0, file.getNameCount() - Path.of(VANILLA_PROBE).getNameCount()));
        } catch (URISyntaxException broken) {
            throw new IOException(broken);
        }
    }

    private static void targets(JsonElement element, Set<String> blocks, Set<String> tags) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                targets(child, blocks, tags);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        for (String key : object.keySet()) {
            JsonElement value = object.get(key);
            if (key.equals("tag") && value.isJsonPrimitive()) {
                tags.add(value.getAsString());
            } else if ((key.equals("block") || key.equals("blocks")) && value.isJsonPrimitive()) {
                blocks.add(value.getAsString());
            } else if (key.equals("blocks") && value.isJsonArray()) {
                for (JsonElement block : value.getAsJsonArray()) {
                    blocks.add(block.getAsString());
                }
            } else {
                targets(value, blocks, tags);
            }
        }
    }

    private static void expand(Path vanilla, String tag, Set<String> blocks) throws IOException {
        String id = tag.startsWith("#") ? tag.substring(1) : tag;
        Path file = vanilla.resolve("data/minecraft/tags/block/" + id.substring("minecraft:".length()) + ".json");
        if (!Files.exists(file)) {
            return;
        }
        for (JsonElement entry : read(file).getAsJsonArray("values")) {
            String value = entry.getAsString();
            if (value.startsWith("#")) {
                expand(vanilla, value, blocks);
            } else {
                blocks.add(value);
            }
        }
    }

    @Test
    void paletteBlocksAreNoTargetsOfLaterVanillaFeatures() throws IOException {
        Path vanilla = vanilla();
        Set<String> blocks = new HashSet<>();
        Set<String> tags = new HashSet<>();
        try (var files = Files.list(vanilla.resolve("data/minecraft/worldgen/feature"))) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith("ore_") || name.startsWith("disk_")) {
                    JsonObject feature = read(file);
                    targets(feature.has("targets") ? feature.get("targets") : feature.get("target"), blocks, tags);
                }
            }
        }
        for (String tag : tags) {
            expand(vanilla, tag, blocks);
        }
        assertTrue(blocks.contains("minecraft:andesite") && blocks.contains("minecraft:dirt"), "Erz-Ziele nicht gelesen: " + blocks);
        for (String state : BandoPalettes.allStates()) {
            String id = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
            assertFalse(blocks.contains(id), id + " wird von Erzadern oder Scheiben nach dem Bando ersetzt");
        }
    }

    private static void groundBelow(JsonElement element, Set<String> blocks) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                groundBelow(child, blocks);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement type = object.get("type");
        JsonElement offset = object.get("offset");
        if (type != null && type.isJsonPrimitive() && type.getAsString().equals("minecraft:matching_blocks") && offset != null
                && offset.isJsonArray() && offset.getAsJsonArray().size() == 3 && offset.getAsJsonArray().get(1).getAsInt() == -1
                && offset.getAsJsonArray().get(0).getAsInt() == 0 && offset.getAsJsonArray().get(2).getAsInt() == 0) {
            JsonElement value = object.get("blocks");
            if (value != null && value.isJsonPrimitive()) {
                blocks.add(value.getAsString());
            } else if (value != null && value.isJsonArray()) {
                for (JsonElement block : value.getAsJsonArray()) {
                    blocks.add(block.getAsString());
                }
            }
        }
        for (String key : object.keySet()) {
            groundBelow(object.get(key), blocks);
        }
    }

    @Test
    void paletteBlocksAreNoGroundForLaterVanillaTrees() throws IOException {
        Path vanilla = vanilla();
        Set<String> blocks = new HashSet<>();
        for (String folder : List.of("placed_feature", "feature")) {
            try (var files = Files.walk(vanilla.resolve("data/minecraft/worldgen/" + folder))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                    groundBelow(read(file), blocks);
                }
            }
        }
        assertTrue(blocks.contains("minecraft:snow_block") && blocks.contains("minecraft:powder_snow"),
                "Untergrund der Schnee-Bäume nicht gelesen: " + blocks);
        for (String state : BandoPalettes.allStates()) {
            String id = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
            assertFalse(blocks.contains(id), id + " ist Untergrund eines Vanilla-Features nach dem Bando (Bäume wachsen im Grundriss)");
        }
    }

    @Test
    void generatorCheckPasses() throws IOException, InterruptedException {
        Path script = root().resolve("tools/data/bandos.py");
        assumeTrue(Files.exists(script), "Generator fehlt");
        Process process;
        try {
            process = new ProcessBuilder("python3", script.toString(), "--check").redirectErrorStream(true).start();
        } catch (IOException missing) {
            assumeTrue(false, "python3 nicht verfügbar");
            return;
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Generator hängt");
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("BANDOS OK"), output);
    }
}
