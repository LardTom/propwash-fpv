package de.lardtom.propwash.webexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareFormat;
import de.lardtom.propwash.share.ShareSpecJson;
import de.lardtom.propwash.share.ShareTables;
import de.lardtom.propwash.share.legacy.Pw1Tables;
import de.lardtom.propwash.share.legacy.Pw2Tables;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebExportTest {
    private static Path first;
    private static Path second;
    private static WebExport.Result result;

    @BeforeAll
    static void exportTwice(@TempDir Path dir) {
        first = dir.resolve("first").resolve(WebExport.FOLDER);
        second = dir.resolve("second").resolve(WebExport.FOLDER);
        result = WebExport.run(first, WebExport.ALL, DirectoryResources.propwash());
        WebExport.run(second, WebExport.ALL, DirectoryResources.propwash());
    }

    private static JsonElement read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void exportPassesWithTheBuiltinParts() throws IOException {
        assertTrue(result.pass(), result.summary());
        JsonArray problems = read(first.resolve("problems.json")).getAsJsonArray();
        for (JsonElement problem : problems) {
            assertFalse(problem.getAsJsonObject().get("level").getAsString().equals("error"), problem.toString());
        }
        int builtin = (int) PropwashApi.parts().all().stream().filter(WebExport::exported).filter(p -> p.id().getNamespace().equals("propwash")).count();
        assertEquals(builtin, result.parts());
    }

    @Test
    void secondRunIsByteEqualExceptTheTimestamp() throws IOException {
        assertEquals(List.of(), ExportDiff.differences(first, second));
    }

    @Test
    void layoutIsFormatTwoWithBaseAndOneFolderPerNamespace() throws IOException {
        JsonObject manifest = read(first.resolve("manifest.json")).getAsJsonObject();
        assertEquals(WebExport.FORMAT, manifest.get("format").getAsInt());
        assertTrue(manifest.get("base").getAsBoolean());
        assertEquals(WebExport.ALL, manifest.get("selection").getAsString());
        List<String> namespaces = new ArrayList<>();
        manifest.getAsJsonArray("namespaces").forEach(e -> namespaces.add(e.getAsString()));
        assertEquals(List.copyOf(WebExport.namespaces()), namespaces);
        for (String file : List.of("base/rules.json", "base/paint.json", "base/tune.json", "base/analysis_labels.json", "problems.json",
                "propwash/manifest.json", "propwash/parts.json", "propwash/mounts.json", "propwash/chemistries.json", "propwash/presets.json",
                "propwash/lang/en_us.json", "propwash/lang/de_de.json", "propwash/assets/propwash/drone_layout.json")) {
            assertTrue(Files.isRegularFile(first.resolve(file)), file);
        }
        assertTrue(Files.isRegularFile(first.resolve("base/sharecode/spec.json")));
        assertTrue(Files.isRegularFile(first.resolve("base/sharecode/vectors.json")));
        Set<String> listed = new TreeSet<>();
        manifest.getAsJsonArray("files").forEach(e -> listed.add(e.getAsString()));
        for (String file : listed) {
            assertTrue(Files.isRegularFile(first.resolve(file)), file);
        }
        assertTrue(listed.contains("propwash/analysis/freestyle.json"), listed.toString());
        JsonObject own = read(first.resolve("propwash/manifest.json")).getAsJsonObject();
        assertEquals("propwash", own.get("namespace").getAsString());
        assertEquals(DirectoryResources.VERSION, own.getAsJsonObject("mod").get("version").getAsString());
        for (JsonElement file : own.getAsJsonArray("files")) {
            assertTrue(Files.isRegularFile(first.resolve("propwash").resolve(file.getAsString())), file.toString());
        }
    }

    @Test
    void partsKeepTheirPathsAndTheLedPath() throws IOException {
        JsonArray parts = read(first.resolve("propwash/parts.json")).getAsJsonArray();
        Set<String> ids = new TreeSet<>();
        for (JsonElement element : parts) {
            JsonObject part = element.getAsJsonObject();
            ids.add(part.get("id").getAsString());
            if (part.get("category").getAsString().equals("accessory")) {
                assertTrue(part.getAsJsonObject("data").get("led_path").isJsonArray(), part.get("id").getAsString());
            }
            JsonObject model = part.getAsJsonObject("model");
            if (!model.get("item").isJsonNull()) {
                assertTrue(Files.isRegularFile(first.resolve("propwash").resolve(model.get("item").getAsString())), model.toString());
            }
            assertTrue(part.getAsJsonObject("paint").get("default_colors").isJsonObject());
        }
        assertFalse(ids.contains("propwash:creative_fc"));
        assertTrue(ids.contains("propwash:led_strip"));
    }

    @Test
    void presetsCarryTheirDefaultTuneAndAnalysis() throws IOException {
        JsonArray presets = read(first.resolve("propwash/presets.json")).getAsJsonArray();
        assertFalse(presets.isEmpty());
        for (JsonElement element : presets) {
            JsonObject preset = element.getAsJsonObject();
            assertTrue(preset.get("default_tune").isJsonObject(), preset.get("id").getAsString());
            assertTrue(preset.get("analysis").isJsonObject(), preset.get("id").getAsString());
            String path = Identifier.parse(preset.get("id").getAsString()).getPath();
            JsonObject analysis = read(first.resolve("propwash/analysis/" + path + ".json")).getAsJsonObject();
            assertEquals(preset.get("id"), analysis.get("preset"));
            assertFalse(analysis.getAsJsonArray("variants").isEmpty());
        }
        JsonObject rules = read(first.resolve("base/rules.json")).getAsJsonObject();
        assertEquals(0, rules.getAsJsonObject("verified").get("mismatches").getAsInt());
        assertEquals(RuleCheck.RANDOM_BUILDS, rules.getAsJsonObject("verified").get("builds").getAsInt());
    }

    @Test
    void namesComeFromTheLanguageFilesAndAreListedPerNamespace() throws IOException {
        JsonObject lang = read(first.resolve("propwash/lang/en_us.json")).getAsJsonObject();
        assertTrue(lang.has("propwash.paint.slot.frame"));
        assertTrue(lang.has("propwash.build.issue.motor_mount"));
        JsonObject mounts = read(first.resolve("propwash/mounts.json")).getAsJsonObject();
        for (String kind : List.of("motor", "stack", "prop")) {
            assertFalse(mounts.getAsJsonArray(kind).isEmpty(), kind);
        }
    }

    @Test
    void singleNamespaceAndUnknownNamespace(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(WebExport.FOLDER);
        Files.createDirectories(target.resolve("old"));
        Files.writeString(target.resolve("old/stale.json"), "{}");
        WebExport.Result only = WebExport.run(target, "propwash", DirectoryResources.propwash());
        assertTrue(only.pass(), only.summary());
        assertFalse(Files.exists(target.resolve("old")));
        assertTrue(Files.isRegularFile(target.resolve("base/rules.json")));
        assertEquals("propwash", read(target.resolve("manifest.json")).getAsJsonObject().get("selection").getAsString());
        assertThrows(IllegalArgumentException.class, () -> WebExport.run(target, "nowhere", DirectoryResources.propwash()));
        assertTrue(WebExportCommand.choices().getFirst().equals(WebExport.ALL));
        assertTrue(WebExportCommand.choices().contains("propwash"));
    }

    @Test
    void ledPathIsExportedAsListOfPoints() {
        AccessoryDefinition ring = AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("example", "ring")).basics(PartBasics.of(8.0))
                .slot(AccessorySlot.TOP).size(50.0, 4.0, 50.0).flightBatteryWatts(2.0).frontalArea(0.0002).leds(4)
                .ledPath(new Vec3Mm(20.0, 7.0, 0.0), new Vec3Mm(0.0, 7.0, 20.0), new Vec3Mm(-20.0, 7.0, 0.0)).build();
        JsonArray path = PartData.data(ring).getAsJsonArray("led_path");
        assertEquals(JsonParser.parseString("[[20.0, 7.0, 0.0], [0.0, 7.0, 20.0], [-20.0, 7.0, 0.0]]"), path);
        PartDefinition strip = PropwashApi.parts().all(PartKind.ACCESSORY).getFirst();
        assertEquals(new JsonArray(), PartData.data(strip).getAsJsonArray("led_path"));
    }

    @Test
    void requiredPropwashVersionComesFromTheLoaderMetadata(@TempDir Path dir) throws IOException {
        Path fabric = dir.resolve("fabric");
        Files.createDirectories(fabric);
        Files.writeString(fabric.resolve("fabric.mod.json"), "{\"id\": \"example\", \"depends\": {\"propwash\": \">=0.6.0-\", \"minecraft\": \"~26.3\"}}");
        Path neoforge = dir.resolve("neoforge");
        Files.createDirectories(neoforge.resolve("META-INF"));
        Files.writeString(neoforge.resolve("META-INF/neoforge.mods.toml"), """
                [[mods]]
                modId = "example"
                [[dependencies.example]]
                modId = "minecraft"
                versionRange = "[26.3,26.4)"
                [[dependencies.example]]
                modId = "propwash"
                type = "required"
                versionRange = "[0.6.0-alpha,)"
                """);
        Problems problems = new Problems();
        assertEquals(Map.of("propwash", ">=0.6.0-"), WebExport.requires(new DirectoryResources(Map.of("example", fabric)), "example", problems));
        assertEquals(Map.of("propwash", "[0.6.0-alpha,)"), WebExport.requires(new DirectoryResources(Map.of("example", neoforge)), "example", problems));
        assertEquals(Map.of(), WebExport.requires(new DirectoryResources(Map.of("example", dir.resolve("empty"))), "example", problems));
        assertEquals(0, problems.warnings());
    }

    private static JsonObject golden(String name) throws IOException {
        for (Path candidate : List.of(Path.of("common/src/test/resources/sharecode", name), Path.of("src/test/resources/sharecode", name))) {
            if (Files.isRegularFile(candidate)) {
                return read(candidate).getAsJsonObject();
            }
        }
        throw new IOException(name + " nicht gefunden");
    }

    @Test
    void specIsTheCodecSpecWithCompleteFrozenTables() throws IOException {
        JsonObject spec = read(first.resolve("base/sharecode/spec.json")).getAsJsonObject();
        JsonObject codec = ShareSpecJson.spec();
        for (String key : codec.keySet()) {
            if (key.equals("pw1") || key.equals("pw2")) {
                for (String inner : codec.getAsJsonObject(key).keySet()) {
                    assertEquals(codec.getAsJsonObject(key).get(inner), spec.getAsJsonObject(key).get(inner), key + "." + inner);
                }
            } else {
                assertEquals(codec.get(key), spec.get(key), key);
            }
        }
        assertEquals(codec.keySet(), spec.keySet());
        JsonObject pw2 = spec.getAsJsonObject("pw2");
        assertEquals(Pw2Tables.ALPHABET, pw2.get("alphabet").getAsString());
        assertEquals(Pw2Tables.COLORS.size(), pw2.getAsJsonArray("colors").size());
        assertEquals(spec.get("colors"), pw2.get("colors"));
        assertEquals(spec.get("osd_dictionary"), pw2.get("osd_dictionary"));
        assertEquals(Pw2Tables.K_PART, pw2.getAsJsonObject("params").get("part").getAsInt());
        JsonObject pw1 = spec.getAsJsonObject("pw1");
        assertEquals(Pw1Tables.GROUP_LENGTH, pw1.get("group_length").getAsInt());
        assertEquals(12, pw1.getAsJsonArray("paint_bits").size());
        assertEquals("front", pw1.getAsJsonArray("accessory_slots").get(2).getAsJsonObject().get("key").getAsString());
        JsonObject lang = read(first.resolve("propwash/lang/en_us.json")).getAsJsonObject();
        for (JsonElement status : spec.getAsJsonArray("statuses")) {
            assertTrue(lang.has(status.getAsJsonObject().get("translation_key").getAsString()), status.toString());
        }
        for (JsonElement layer : spec.getAsJsonArray("layers")) {
            assertTrue(lang.has(layer.getAsJsonObject().get("translation_key").getAsString()), layer.toString());
        }
    }

    @Test
    void vectorsAndPresetCodesAreThePw3CodesOfTheCodec() throws IOException {
        JsonArray vectors = read(first.resolve("base/sharecode/vectors.json")).getAsJsonArray();
        JsonArray presets = read(first.resolve("propwash/presets.json")).getAsJsonArray();
        assertEquals(2 * presets.size(), vectors.size());
        assertEquals(vectors.size(), read(first.resolve("manifest.json")).getAsJsonObject().getAsJsonObject("counts").get("vectors").getAsInt());
        Map<String, JsonObject> byName = new LinkedHashMap<>();
        for (JsonElement element : vectors) {
            JsonObject vector = element.getAsJsonObject();
            byName.put(vector.get("name").getAsString(), vector);
            assertEquals("OK", vector.get("status").getAsString(), vector.toString());
            ShareDecoded decoded = ShareFormat.decode(vector.get("code").getAsString(), ShareTables.current());
            assertTrue(decoded.ok(), vector.toString());
            assertEquals(ShareSpecJson.content(decoded.require()), vector.get("decoded"));
            assertTrue(vector.get("check").isJsonObject(), vector.toString());
            assertTrue(vector.get("analysis").isJsonObject(), vector.toString());
        }
        Map<String, String> goldens = new LinkedHashMap<>();
        for (JsonElement element : golden("pw3-vectors.json").getAsJsonArray("vectors")) {
            String name = element.getAsJsonObject().get("name").getAsString();
            if (name.startsWith("U")) {
                goldens.put(name.substring(name.indexOf('/') + 1), element.getAsJsonObject().get("code").getAsString());
            }
        }
        for (JsonElement element : presets) {
            JsonObject preset = element.getAsJsonObject();
            String id = preset.get("id").getAsString();
            JsonObject vector = byName.get("preset:" + id);
            assertEquals(vector.get("code"), preset.get("sharecode"), id);
            assertEquals(preset.get("analysis"), vector.get("analysis"), id);
            assertEquals(goldens.get(id + "/preset"), preset.get("sharecode").getAsString(), id);
            assertEquals(goldens.get(id + "/parts"), byName.get("parts:" + id).get("code").getAsString(), id);
        }
    }

    @Test
    void tuneAndPaintCarryTheSharecodeTables() throws IOException {
        JsonObject tune = read(first.resolve("base/tune.json")).getAsJsonObject();
        JsonArray keys = tune.getAsJsonArray("pw1_keys");
        assertEquals(Pw1Tables.TUNE_KEYS.size(), keys.size());
        for (int i = 0; i < keys.size(); i++) {
            JsonObject entry = keys.get(i).getAsJsonObject();
            assertEquals(i + 1, entry.get("index").getAsInt());
            assertEquals(Pw1Tables.TUNE_KEYS.get(i), entry.get("key").getAsString());
        }
        assertEquals("camera", keys.get(keys.size() - 1).getAsJsonObject().get("group").getAsString());
        Set<String> groups = new TreeSet<>();
        tune.getAsJsonObject("groups").getAsJsonArray("shared").forEach(e -> groups.add(e.getAsString()));
        tune.getAsJsonObject("groups").getAsJsonArray("never").forEach(e -> assertTrue(groups.add(e.getAsString()), e.toString()));
        assertEquals(tune.getAsJsonObject("group_names").keySet(), groups);
        assertTrue(tune.getAsJsonObject("groups").getAsJsonArray("shared").contains(new JsonPrimitive("camera")));
        JsonArray slots = read(first.resolve("base/paint.json")).getAsJsonObject().getAsJsonArray("slots");
        for (int i = 0; i < slots.size(); i++) {
            assertEquals(i, slots.get(i).getAsJsonObject().get("bit").getAsInt());
        }
    }

    @Test
    void namespaceManifestNamesItsShareTable() throws IOException {
        JsonObject own = read(first.resolve("propwash/manifest.json")).getAsJsonObject();
        assertEquals("mod", own.get("table_source").getAsString());
        assertEquals(ShareTables.propwashTable().totalEntries(), own.get("table_entries").getAsInt());
        JsonObject table = read(first.resolve("propwash/share_table.json")).getAsJsonObject();
        assertEquals(Format1Compare.tableCrc(table), own.get("table_crc").getAsString());
        assertEquals(8, own.get("table_crc").getAsString().length());
        JsonObject manifest = read(first.resolve("manifest.json")).getAsJsonObject();
        assertEquals("PW3", manifest.getAsJsonObject("sharecode").get("format").getAsString());
        assertEquals(1, own.get("short_id").getAsInt());
    }
}
