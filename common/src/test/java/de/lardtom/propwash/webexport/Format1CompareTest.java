package de.lardtom.propwash.webexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Format1CompareTest {
    static final String OLD = "PROPWASH_WEBEXPORT_FORMAT1";
    static final String NEW = "PROPWASH_WEBEXPORT_FORMAT2";
    static final String SECOND = "PROPWASH_WEBEXPORT_SECOND";
    static final String REPORT = "PROPWASH_WEBEXPORT_REPORT";

    private static final String PART = """
            {"id": "%s", "category": "accessory", "builtin": true, "name": {"de_de": "Leiste", "en_us": "Strip"},
             "data": {"slot": "bottom", "leds": 8%s}, "paint": {"slots": ["accessories"], "paintable_model": false%s}}""";

    static final String PW2_WHOOP = "PW21RSaEPg8Rt";
    static final String PW3_WHOOP = "PW3-1hDVja67mWpdN3";
    private static final String WHOOP_DECODED = """
            {"build": {"frame": "propwash:whoop_65", "stack": "propwash:aio_whoop", "motor": "propwash:m0802_22000", "prop": "propwash:p31_4b",
             "video": "propwash:analog_nano", "battery": "propwash:b1s_300_hv", "accessories": {}}, "paint": null, "tune": {}, "osd": null, "name": null}""";

    private static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, ExportJson.text(JsonParser.parseString(json)), StandardCharsets.UTF_8);
    }

    private static String part(String id, boolean format2, String color) {
        return PART.formatted(id, format2 ? ", \"led_path\": []" : "", format2 ? ", \"default_colors\": {\"accessories\": \"" + color + "\"}" : "");
    }

    static void fixture(Path old, Path current, String color, boolean extraPart) throws IOException {
        write(old.resolve("manifest.json"), "{\"format\": 1}");
        write(old.resolve("parts.json"), "[" + part("propwash:led_strip", false, color) + "]");
        write(old.resolve("mounts.json"), "{\"motor\": [], \"stack\": [], \"prop\": []}");
        write(old.resolve("chemistries.json"), "[{\"id\": \"propwash:lipo\", \"name\": {\"de_de\": \"LiPo\", \"en_us\": \"LiPo\"}, \"full_v\": 4.2, \"used_by\": []}]");
        write(old.resolve("rules.json"), "{\"verified\": {\"builds\": 5000, \"mismatches\": 0}}");
        write(old.resolve("analysis_labels.json"), "{\"mass_grams\": {\"key\": \"k\"}}");
        write(old.resolve("paint.json"), "{\"slots\": [{\"key\": \"accessories\", \"bit\": 11, \"name\": {\"en_us\": \"Acc\"}}], "
                + "\"default_colors\": {\"propwash:led_strip\": {\"accessories\": \"#112233\"}}}");
        write(old.resolve("tune.json"), "{\"groups\": {\"flight\": [\"rates\"], \"shared\": [\"rates\"], \"never\": [\"led\"]}, "
                + "\"group_names\": {\"rates\": {\"en_us\": \"Rates\"}}, \"pw1_keys\": [{\"index\": 1, \"key\": \"rates.type\"}], "
                + "\"defaults\": {\"propwash:whoop\": {\"rates.type\": 0.0}}}");
        write(old.resolve("presets.json"), "[{\"id\": \"propwash:whoop\", \"sharecode\": \"" + PW2_WHOOP + "\", \"check\": {\"problems\": []}}]");
        write(old.resolve("analysis/propwash/whoop.json"), "{\"preset\": \"propwash:whoop\", \"camera_view\": null, "
                + "\"variants\": [{\"category\": \"accessory\", \"id\": \"propwash:led_strip\", \"analysis\": {\"mass_grams\": 30.5}}, "
                + "{\"category\": \"motor\", \"id\": \"justmoreparts:m1\", \"analysis\": {\"mass_grams\": 31.0}}]}");
        write(old.resolve("lang/en_us.json"), "{\"propwash.paint.slot.accessories\": \"Acc\", \"justmoreparts.share.title\": \"Share\"}");
        write(old.resolve("lang/de_de.json"), "{\"propwash.paint.slot.accessories\": \"Zubehör\"}");
        Files.createDirectories(old.resolve("assets/propwash"));
        Files.writeString(old.resolve("assets/propwash/drone_layout.json"), "{}");
        write(old.resolve("sharecode/spec.json"), "{}");

        write(current.resolve("manifest.json"), "{\"format\": 2, \"namespaces\": [\"propwash\"]}");
        String parts = part("propwash:led_strip", true, color) + (extraPart ? "," + part("propwash:led_ring", true, "#ffffff") : "");
        write(current.resolve("propwash/parts.json"), "[" + parts + "]");
        write(current.resolve("propwash/mounts.json"), "{\"motor\": [], \"stack\": [], \"prop\": []}");
        write(current.resolve("propwash/chemistries.json"),
                "[{\"id\": \"propwash:lipo\", \"name\": {\"de_de\": \"LiPo\", \"en_us\": \"LiPo\"}, \"full_v\": 4.2, \"used_by\": []}]");
        write(current.resolve("base/rules.json"), "{\"verified\": {\"builds\": 5000, \"mismatches\": 0}}");
        write(current.resolve("base/analysis_labels.json"), "{\"mass_grams\": {\"key\": \"k\"}}");
        write(current.resolve("base/paint.json"), "{\"slots\": [{\"key\": \"accessories\", \"bit\": 11, \"name\": {\"en_us\": \"Acc\"}}]}");
        write(current.resolve("base/tune.json"), "{\"groups\": {\"flight\": [\"rates\"], \"shared\": [\"rates\"], \"never\": [\"led\"]}, "
                + "\"group_names\": {\"rates\": {\"en_us\": \"Rates\"}}, \"pw1_keys\": [{\"index\": 1, \"key\": \"rates.type\"}]}");
        write(current.resolve("propwash/presets.json"), "[{\"id\": \"propwash:whoop\", \"sharecode\": \"" + PW3_WHOOP
                + "\", \"check\": {\"problems\": []}, \"default_tune\": {\"rates.type\": 0.0}}]");
        write(current.resolve("base/sharecode/spec.json"), "{}");
        write(current.resolve("base/sharecode/vectors.json"), "[]");
        write(current.resolve("propwash/analysis/whoop.json"), "{\"preset\": \"propwash:whoop\", \"camera_view\": null, "
                + "\"variants\": [{\"category\": \"accessory\", \"id\": \"propwash:led_strip\", \"analysis\": {\"mass_grams\": 30.5}}]}");
        write(current.resolve("propwash/lang/en_us.json"), "{\"propwash.paint.slot.accessories\": \"Acc\"}");
        write(current.resolve("propwash/lang/de_de.json"), "{\"propwash.paint.slot.accessories\": \"Zubehör\"}");
        Files.createDirectories(current.resolve("propwash/assets/propwash"));
        Files.writeString(current.resolve("propwash/assets/propwash/drone_layout.json"), "{}");
    }

    @Test
    void structureLedPathAndNewPartsAreAllowedAndShareFieldsCompared(@TempDir Path dir) throws IOException {
        fixture(dir.resolve("old"), dir.resolve("new"), "#112233", true);
        Format1Compare.Report report = Format1Compare.compare(dir.resolve("old"), dir.resolve("new"));
        assertTrue(report.pass(), report.text());
        assertTrue(report.allowed().contains("neues Teil: propwash:led_ring"), report.text());
        assertTrue(report.allowed().stream().anyMatch(line -> line.startsWith("led_path")), report.text());
        assertTrue(report.allowed().stream().anyMatch(line -> line.contains("justmoreparts.share.title")), report.text());
        assertEquals(1, report.compared().get("analysis_variants"));
        assertEquals(1, report.compared().get("preset_sharecodes"));
        assertEquals(1, report.compared().get("pw1_keys"));
        assertEquals(1, report.compared().get("spec"));
    }

    private static void edit(Path file, String from, String to) throws IOException {
        String text = Files.readString(file);
        assertTrue(text.contains(from), from);
        Files.writeString(file, text.replace(from, to));
    }

    @Test
    void shareFieldsAreComparedStrictly(@TempDir Path dir) throws IOException {
        Path old = dir.resolve("old");
        Path current = dir.resolve("new");
        fixture(old, current, "#112233", false);
        edit(current.resolve("base/paint.json"), "\"bit\": 11", "\"bit\": 10");
        edit(current.resolve("base/tune.json"), "\"never\": [\n      \"led\"\n    ]", "\"never\": []");
        edit(current.resolve("base/tune.json"), "\"key\": \"rates.type\"", "\"key\": \"rates.mode\"");
        edit(current.resolve("propwash/presets.json"), PW3_WHOOP, "PW3-02nGxWz4AvQoX");
        Format1Compare.Report report = Format1Compare.compare(old, current);
        assertFalse(report.pass());
        assertEquals(4, report.differenceCount(), report.text());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("slots[0].bit")), report.text());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("groups.never")), report.text());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("pw1_keys[0].key")), report.text());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("ergibt umgerechnet " + PW3_WHOOP)), report.text());
        Files.delete(current.resolve("base/sharecode/vectors.json"));
        edit(current.resolve("propwash/presets.json"), "\"sharecode\": \"PW3-02nGxWz4AvQoX\",", "");
        report = Format1Compare.compare(old, current);
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("sharecode fehlt im neuen Export")), report.text());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("vectors.json fehlt")), report.text());
    }

    private static String vector(String name, String format, String code, String status, String decoded, String extra) {
        return "{\"name\": \"" + name + "\", \"format\": \"" + format + "\", \"code\": \"" + code + "\", \"status\": \"" + status + "\", "
                + "\"layers\": " + (decoded.equals("null") ? "[]" : "[\"parts\", \"tune\"]") + ", \"decoded\": " + decoded
                + ", \"skipped\": [], \"unknown_parts\": [], \"check\": {\"problems\": [], \"warnings\": []}, "
                + "\"analysis\": {\"mass_grams\": 28.1, \"motor_temp_minute_c\": 30.0, \"warnings\": []}" + extra + "}";
    }

    @Test
    void vectorsAreConvertedAndOldVectorsReadAgain(@TempDir Path dir) throws IOException {
        Path old = dir.resolve("old");
        Path current = dir.resolve("new");
        fixture(old, current, "#112233", false);
        write(old.resolve("sharecode/vectors.json"), "[" + vector("pw2_preset:propwash:whoop", "PW2", PW2_WHOOP, "OK", WHOOP_DECODED,
                ", \"compression\": \"auto\", \"wrong_kind\": [], \"renamed\": []") + ", "
                + "{\"name\": \"PW2_F_checksum_error\", \"format\": \"PW2\", \"code\": \"PW205zwQCrAS4\", \"status\": \"CHECKSUM\", "
                + "\"layers\": [], \"decoded\": null, \"skipped\": [], \"unknown_parts\": []}, "
                + "{\"name\": \"PW2_D_foreign_namespace\", \"format\": \"PW2\", \"code\": \"PW20Nc8d2lmeWWcSEiZHqH5Es7RykzSmJYg3uVnhNkQuJGLL8ALqO9gBj2r\", "
                + "\"status\": \"OK\", \"layers\": [\"parts\", \"tune\"], \"decoded\": {\"build\": {\"frame\": \"otheraddon:frame_x\", "
                + "\"stack\": \"propwash:stack_30\", \"motor\": \"propwash:m2207_1750\", \"prop\": \"propwash:p51_3b\", \"video\": \"propwash:analog_race\", "
                + "\"battery\": \"propwash:b6s_1300\", \"accessories\": {}}, \"paint\": null, \"tune\": {\"pid.newFlightKey\": 3.0, "
                + "\"rates.yaw.rcRate\": 1.0}, \"osd\": null, \"name\": null}, \"skipped\": [], \"unknown_parts\": [\"otheraddon:frame_x\"]}]");
        String now = vector("preset:propwash:whoop", "PW3", PW3_WHOOP, "OK", WHOOP_DECODED, ", \"namespaces\": [{\"key\": \"propwash\", \"result\": \"MATCH\"}]");
        write(current.resolve("base/sharecode/vectors.json"), "[" + now + "]");
        Format1Compare.Report report = Format1Compare.compare(old, current);
        assertTrue(report.pass(), report.text());
        assertEquals(1, report.compared().get("vectors"));
        assertEquals(2, report.compared().get("legacy_vectors"));
        assertEquals(1, report.compared().get("legacy_conversions"));
        write(current.resolve("base/sharecode/vectors.json"), "[" + now.replace(PW3_WHOOP, "PW3-1gia5XF0DxkbJG") + "]");
        report = Format1Compare.compare(old, current);
        assertFalse(report.pass());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("ergibt umgerechnet " + PW3_WHOOP)), report.text());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("gleich vectors.json")), report.text());
        write(current.resolve("base/sharecode/vectors.json"), "[" + now.replace("\"motor_temp_minute_c\": 30.0", "\"motor_temp_minute_c\": 31.0") + "]");
        report = Format1Compare.compare(old, current);
        assertEquals(1, report.differenceCount(), report.text());
    }

    @Test
    void motorHeatSinceZeroSixIsAllowedOnlyAgainstAnOldPropwash(@TempDir Path dir) throws IOException {
        Path old = dir.resolve("old");
        Path current = dir.resolve("new");
        fixture(old, current, "#112233", false);
        edit(old.resolve("analysis/propwash/whoop.json"), "\"mass_grams\": 30.5", "\"mass_grams\": 30.5, \"motor_temp_minute_c\": 50.0, \"warnings\": []");
        edit(current.resolve("propwash/analysis/whoop.json"), "\"mass_grams\": 30.5",
                "\"mass_grams\": 30.5, \"motor_temp_minute_c\": 61.0, \"warnings\": [\"motor_thermal\"]");
        Format1Compare.Report report = Format1Compare.compare(old, current);
        assertEquals(2, report.differenceCount(), report.text());
        write(old.resolve("manifest.json"), "{\"format\": 1, \"mods\": {\"propwash\": \"0.5.2\"}}");
        report = Format1Compare.compare(old, current);
        assertTrue(report.pass(), report.text());
        assertEquals(2, report.allowed().stream().filter(line -> line.startsWith(Format1Compare.HEAT)).count(), report.text());
        edit(current.resolve("propwash/analysis/whoop.json"), "\"mass_grams\": 30.5", "\"mass_grams\": 30.6");
        report = Format1Compare.compare(old, current);
        assertEquals(1, report.differenceCount(), report.text());
    }

    @Test
    void shareTableStartsWithThePw2OrderAndCarriesItsChecksum(@TempDir Path dir) throws IOException {
        Path old = dir.resolve("old");
        Path current = dir.resolve("new");
        fixture(old, current, "#112233", false);
        String parts = "{\"pw2\": {\"parts\": {\"frame\": [\"propwash:a\", \"otheraddon:x\", \"propwash:b\"], \"motor\": [\"propwash:m\"]}}}";
        write(old.resolve("sharecode/spec.json"), parts);
        write(current.resolve("base/sharecode/spec.json"), parts);
        write(current.resolve("propwash/share_table.json"), "{\"format\": 1, \"namespace\": \"propwash\", \"parts\": {\"frame\": [{\"id\": \"a\"}, "
                + "{\"id\": \"b\"}, {\"id\": \"c\"}], \"motor\": [{\"id\": \"m\"}]}}");
        write(current.resolve("propwash/manifest.json"), "{\"format\": 2, \"namespace\": \"propwash\", \"table_source\": \"mod\", "
                + "\"table_entries\": 4, \"table_crc\": \"974f775d\"}");
        Format1Compare.Report report = Format1Compare.compare(old, current);
        assertTrue(report.pass(), report.text());
        assertEquals(1, report.compared().get("tables"));
        write(current.resolve("propwash/share_table.json"), "{\"format\": 1, \"namespace\": \"propwash\", \"parts\": {\"frame\": [{\"id\": \"b\"}, "
                + "{\"id\": \"a\"}, {\"id\": \"c\"}], \"motor\": [{\"id\": \"m\"}]}}");
        report = Format1Compare.compare(old, current);
        assertEquals(2, report.differenceCount(), report.text());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("PW2-Reihenfolge")), report.text());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("table_crc")), report.text());
    }

    @Test
    void changedValuesAreFound(@TempDir Path dir) throws IOException {
        fixture(dir.resolve("old"), dir.resolve("new"), "#445566", false);
        Path variant = dir.resolve("new/propwash/analysis/whoop.json");
        Files.writeString(variant, Files.readString(variant).replace("30.5", "30.6"));
        Format1Compare.Report report = Format1Compare.compare(dir.resolve("old"), dir.resolve("new"));
        assertFalse(report.pass());
        assertEquals(2, report.differenceCount(), report.text());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("default_colors.propwash:led_strip.accessories")), report.text());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("analysis.mass_grams: alt 30.5, neu 30.6")), report.text());
    }

    @Test
    void missingPartsAndDroppedLedPathAreFound(@TempDir Path dir) throws IOException {
        fixture(dir.resolve("old"), dir.resolve("new"), "#112233", false);
        Path parts = dir.resolve("new/propwash/parts.json");
        JsonArray list = JsonParser.parseString(Files.readString(parts)).getAsJsonArray();
        list.get(0).getAsJsonObject().getAsJsonObject("data").remove("led_path");
        Files.writeString(parts, ExportJson.text(list));
        Format1Compare.Report report = Format1Compare.compare(dir.resolve("old"), dir.resolve("new"));
        assertFalse(report.pass());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("led_path fehlt")), report.text());
        Files.writeString(parts, "[]");
        report = Format1Compare.compare(dir.resolve("old"), dir.resolve("new"));
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("Teil propwash:led_strip fehlt")), report.text());
    }

    @Test
    void hangsStraightRecessAndDropAreAllowedAsNewAccessoryFields(@TempDir Path dir) throws IOException {
        fixture(dir.resolve("old"), dir.resolve("new"), "#112233", false);
        Path parts = dir.resolve("new/propwash/parts.json");
        JsonArray list = JsonParser.parseString(Files.readString(parts)).getAsJsonArray();
        JsonObject data = list.get(0).getAsJsonObject().getAsJsonObject("data");
        data.addProperty("recess_mm", 20.0);
        data.addProperty("drop_mm", 1.5);
        data.addProperty("hangs_straight", true);
        Files.writeString(parts, ExportJson.text(list));
        Format1Compare.Report report = Format1Compare.compare(dir.resolve("old"), dir.resolve("new"));
        assertTrue(report.pass(), report.text());
        assertTrue(report.allowed().contains("hangs_straight (neues Feld): propwash:led_strip true"), report.text());
        assertTrue(report.allowed().contains("recess_mm (neues Feld): propwash:led_strip 20.0"), report.text());
        assertTrue(report.allowed().contains("drop_mm (neues Feld): propwash:led_strip 1.5"), report.text());
        Path old = dir.resolve("old/parts.json");
        JsonArray before = JsonParser.parseString(Files.readString(old)).getAsJsonArray();
        before.get(0).getAsJsonObject().getAsJsonObject("data").addProperty("hangs_straight", false);
        Files.writeString(old, ExportJson.text(before));
        report = Format1Compare.compare(dir.resolve("old"), dir.resolve("new"));
        assertFalse(report.pass());
        assertTrue(report.differences().stream().anyMatch(line -> line.contains("data.hangs_straight: alt false, neu true")), report.text());
    }

    private static void report(Format1Compare.Report report) throws IOException {
        String target = System.getenv(REPORT);
        if (target != null && !target.isBlank()) {
            Files.writeString(Path.of(target), report.text(), StandardCharsets.UTF_8);
        }
        System.out.println(report.text());
    }

    @Test
    void gameExportMatchesTheFormatOneExport() throws IOException {
        String old = System.getenv(OLD);
        String current = System.getenv(NEW);
        assumeTrue(old != null && current != null, OLD + " und " + NEW + " nicht gesetzt");
        Format1Compare.Report report = Format1Compare.compare(Path.of(old), Path.of(current));
        report(report);
        assertTrue(report.pass(), report.text());
    }

    @Test
    void secondGameExportIsByteEqual() throws IOException {
        String current = System.getenv(NEW);
        String second = System.getenv(SECOND);
        assumeTrue(current != null && second != null, NEW + " und " + SECOND + " nicht gesetzt");
        assertEquals(List.of(), ExportDiff.differences(Path.of(current), Path.of(second)));
    }

    @Test
    void builtinPartsMatchTheFormatOneExport(@TempDir Path dir) throws IOException {
        String old = System.getenv(OLD);
        assumeTrue(old != null, OLD + " nicht gesetzt");
        Path target = dir.resolve(WebExport.FOLDER);
        WebExport.Result result = WebExport.run(target, WebExport.ALL, DirectoryResources.propwash());
        assertTrue(result.pass(), result.summary());
        JsonObject manifest = JsonParser.parseString(Files.readString(target.resolve("manifest.json"))).getAsJsonObject();
        assertEquals("propwash", manifest.getAsJsonArray("namespaces").get(0).getAsString());
        Format1Compare.Report report = Format1Compare.compare(Path.of(old), target);
        System.out.println(report.text());
        assertTrue(report.pass(), report.text());
    }
}
