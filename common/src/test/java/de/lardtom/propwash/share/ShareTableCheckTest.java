package de.lardtom.propwash.share;

import de.lardtom.propwash.api.share.ShareTableCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareTableCheckTest {
    private static final String FRAME = "{\"format\": 1, \"role\": \"freestyle\", \"wheelbase_mm\": 226}";
    private static final String WHOOP = "{\"format\": 1, \"role\": \"whoop\", \"wheelbase_mm\": 75}";
    private static final String MOTOR = "{\"format\": 1, \"stator\": \"2207\", \"kv\": 1950, \"mount\": \"propwash:m22\", \"cells\": [6, 6]}";
    private static final String GOOD = """
            {
              "format": 1,
              "namespace": "myaddon",
              "parts": {
                "frame": [
                  {"id": "alpha_5", "since": "1.0.0", "fallback": "propwash:freestyle_5"},
                  {"id": "beta_3", "since": "1.1.0", "fallback": "propwash:whoop_65"}
                ],
                "motor": [
                  {"id": "m2207_1950", "since": "1.0.0", "fallback": "propwash:m2207_1750"}
                ]
              }
            }
            """;

    private static Path addon(Path dir, String table) throws IOException {
        write(dir.resolve("data/myaddon/propwash_parts/frame/alpha_5.json"), FRAME);
        write(dir.resolve("data/myaddon/propwash_parts/frame/beta_3.json"), WHOOP);
        write(dir.resolve("data/myaddon/propwash_parts/motor/m2207_1950.json"), MOTOR);
        if (table != null) {
            write(dir.resolve("data/myaddon/propwash_share/table.json"), table);
        }
        return dir;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static TreeSet<String> codes(List<ShareTableCheck.Finding> findings) {
        TreeSet<String> out = new TreeSet<>();
        findings.forEach(f -> out.add(f.code()));
        return out;
    }

    private static TreeSet<String> check(Path dir, String table, String version, Path baseline) throws IOException {
        Path root = addon(dir, table);
        return codes(ShareTableCheck.check(root, version, baseline));
    }

    @Test
    void goodTableHasNoFindings(@TempDir Path dir) throws IOException {
        assertEquals(new TreeSet<>(), check(dir, GOOD, "1.1.0", null));
    }

    @Test
    void everyFindingIsDetected(@TempDir Path dir) throws IOException {
        assertEquals(new TreeSet<>(List.of("MISSING_ENTRY")), check(dir.resolve("a"), GOOD.replace("""
                  {"id": "m2207_1950", "since": "1.0.0", "fallback": "propwash:m2207_1750"}
                """, ""), "1.1.0", null).headSet("SINCE"));
        assertTrue(check(dir.resolve("b"), GOOD.replace("\"1.1.0\", \"fallback\": \"propwash:whoop_65\"", "\"0.9.0\", \"fallback\": \"propwash:whoop_65\""), "1.1.0", null)
                .contains("SINCE_DECREASING"));
        assertTrue(check(dir.resolve("c"), GOOD, "1.0.0", null).contains("SINCE_FUTURE"));
        assertTrue(check(dir.resolve("d"), GOOD.replace("\"since\": \"1.0.0\", \"fallback\": \"propwash:m2207_1750\"",
                "\"since\": \"1.0.0\", \"deprecated\": \"1.1.0\", \"fallback\": \"propwash:m2207_1750\""), "1.1.0", null).contains("DEPRECATED_WITHOUT_REPLACEMENT"));
        assertTrue(check(dir.resolve("e"), GOOD.replace("\"since\": \"1.0.0\", \"fallback\": \"propwash:m2207_1750\"",
                "\"since\": \"1.0.0\", \"deprecated\": \"1.1.0\", \"replaced_by\": \"myaddon:nope\""), "1.1.0", null).contains("REPLACEMENT_INVALID"));
        assertTrue(check(dir.resolve("f"), GOOD.replace("\"since\": \"1.0.0\", \"fallback\": \"propwash:m2207_1750\"",
                "\"since\": \"1.0.0\", \"deprecated\": \"1.1.0\", \"replaced_by\": \"myaddon:alpha_5\""), "1.1.0", null).contains("REPLACEMENT_KIND"));
        assertTrue(check(dir.resolve("g"), GOOD.replace("{\"id\": \"alpha_5\", \"since\": \"1.0.0\", \"fallback\": \"propwash:freestyle_5\"}",
                "{\"id\": \"alpha_5\", \"since\": \"1.0.0\", \"deprecated\": \"1.1.0\", \"replaced_by\": \"myaddon:beta_3\"}").replace(
                "{\"id\": \"beta_3\", \"since\": \"1.1.0\", \"fallback\": \"propwash:whoop_65\"}",
                "{\"id\": \"beta_3\", \"since\": \"1.1.0\", \"deprecated\": \"1.1.0\", \"replaced_by\": \"myaddon:alpha_5\"}"), "1.1.0", null).contains("REPLACEMENT_CYCLE"));
        assertTrue(check(dir.resolve("h"), GOOD.replace("propwash:freestyle_5", "propwash:m2207_1750"), "1.1.0", null).contains("FALLBACK_INVALID"));
        assertTrue(check(dir.resolve("i"), GOOD.replace("propwash:freestyle_5", "myaddon:beta_3"), "1.1.0", null).contains("FALLBACK_INVALID"));
        Path reserved = dir.resolve("j");
        write(reserved.resolve("data/propwash/propwash_share/table.json"), GOOD.replace("myaddon", "propwash"));
        assertTrue(codes(ShareTableCheck.check(reserved, "1.1.0", null)).contains("RESERVED_NAMESPACE"));
        assertTrue(check(dir.resolve("k"), GOOD.replace("\"id\": \"beta_3\"", "\"id\": \"alpha_5\""), "1.1.0", null).contains("DUPLICATE_ID"));
        assertTrue(check(dir.resolve("l"), GOOD.replace("\"id\": \"beta_3\"", "\"id\": \"Beta 3\""), "1.1.0", null).contains("INVALID_ID"));
        assertTrue(check(dir.resolve("m"), GOOD.replace("\"namespace\": \"myaddon\",", "\"namespace\": \"myaddon\", \"short_id\": 77,"), "1.1.0", null)
                .contains("SHORT_ID_UNKNOWN"));
        assertTrue(check(dir.resolve("n"), GOOD.replace("\"namespace\": \"myaddon\",", "\"namespace\": \"myaddon\", \"short_id\": 2,"), "1.1.0", null)
                .contains("SHORT_ID_CONFLICT"));
        assertTrue(check(dir.resolve("o"), GOOD.replace("\"namespace\": \"myaddon\"", "\"namespace\": \"otheraddon\""), "1.1.0", null).contains("NAMESPACE_MISMATCH"));
        assertTrue(check(dir.resolve("p"), GOOD.replace("\"format\": 1", "\"format\": 2"), "1.1.0", null).contains("FORMAT"));
        assertTrue(check(dir.resolve("q"), null, "1.1.0", null).contains("NO_TABLE"));
        assertTrue(check(dir.resolve("r"), GOOD.replace("\"motor\"", "\"rotor\""), "1.1.0", null).contains("UNKNOWN_KIND"));
        assertTrue(check(dir.resolve("s"), "{ kaputt", "1.1.0", null).contains("INVALID_JSON"));
        assertTrue(check(dir.resolve("t"), GOOD, "1.1", null).contains("VERSION_INVALID"));
        assertTrue(check(dir.resolve("u"), GOOD.replace("\"since\": \"1.1.0\", \"fallback\": \"propwash:whoop_65\"", "\"fallback\": \"propwash:whoop_65\""),
                "1.1.0", null).contains("SINCE_INVALID"));
        assertTrue(check(dir.resolve("v"), GOOD.replace("\"since\": \"1.0.0\", \"fallback\": \"propwash:m2207_1750\"",
                "\"since\": \"1.0.0\", \"replaced_by\": \"myaddon:m2207_1950\""), "1.1.0", null).contains("REPLACEMENT_WITHOUT_DEPRECATED"));
    }

    @Test
    void baselineCatchesRemovedMovedAndInsertedEntries(@TempDir Path dir) throws IOException {
        Path release = addon(dir.resolve("release"), GOOD);
        Path baseline = dir.resolve("baseline.json");
        ShareTableCheck.freeze(release, baseline);
        assertTrue(Files.readString(baseline).contains("\"alpha_5\""));
        assertEquals(new TreeSet<>(), check(dir.resolve("same"), GOOD, "1.2.0", baseline));
        String moved = GOOD.replace("{\"id\": \"alpha_5\", \"since\": \"1.0.0\", \"fallback\": \"propwash:freestyle_5\"},\n      {\"id\": \"beta_3\", \"since\": \"1.1.0\", \"fallback\": \"propwash:whoop_65\"}",
                "{\"id\": \"beta_3\", \"since\": \"1.0.0\", \"fallback\": \"propwash:whoop_65\"},\n      {\"id\": \"alpha_5\", \"since\": \"1.0.0\", \"fallback\": \"propwash:freestyle_5\"}");
        assertTrue(check(dir.resolve("moved"), moved, "1.2.0", baseline).contains("ENTRY_MOVED"));
        String removed = GOOD.replace(",\n      {\"id\": \"beta_3\", \"since\": \"1.1.0\", \"fallback\": \"propwash:whoop_65\"}", "");
        assertTrue(check(dir.resolve("removed"), removed, "1.2.0", baseline).contains("ENTRY_REMOVED"));
        String inserted = GOOD.replace("{\"id\": \"alpha_5\"", "{\"id\": \"gamma_1\", \"since\": \"1.0.0\"},\n      {\"id\": \"alpha_5\"");
        assertTrue(check(dir.resolve("inserted"), inserted, "1.2.0", baseline).contains("ENTRY_INSERTED"));
        Path jar = dir.resolve("myaddon-1.1.0.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("data/myaddon/propwash_share/table.json"));
            zip.write(GOOD.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertTrue(check(dir.resolve("jar"), removed, "1.2.0", jar).contains("ENTRY_REMOVED"));
        assertEquals(new TreeSet<>(), check(dir.resolve("jarok"), GOOD, "1.2.0", jar));
    }

    @Test
    void addAppendsAlphabeticallyAndSuggestsFallbacks(@TempDir Path dir) throws IOException {
        Path root = addon(dir, GOOD);
        write(root.resolve("data/myaddon/propwash_parts/frame/zeta_7.json"), "{\"format\": 1, \"role\": \"long_range\", \"wheelbase_mm\": 300}");
        write(root.resolve("data/myaddon/propwash_parts/frame/delta_2.json"), WHOOP);
        List<String> added = ShareTableCheck.add(root, "1.2.0");
        assertEquals(List.of("myaddon:delta_2 (frame)", "myaddon:zeta_7 (frame)"), added);
        String expected = """
                {
                  "format": 1,
                  "namespace": "myaddon",
                  "parts": {
                    "frame": [
                      {"id": "alpha_5", "since": "1.0.0", "fallback": "propwash:freestyle_5"},
                      {"id": "beta_3", "since": "1.1.0", "fallback": "propwash:whoop_65"},
                      {"id": "delta_2", "since": "1.2.0", "fallback": "propwash:whoop_65"},
                      {"id": "zeta_7", "since": "1.2.0", "fallback": "propwash:long_range_7"}
                    ],
                    "motor": [
                      {"id": "m2207_1950", "since": "1.0.0", "fallback": "propwash:m2207_1750"}
                    ]
                  }
                }
                """;
        assertEquals(expected, Files.readString(root.resolve("data/myaddon/propwash_share/table.json")));
        assertEquals(List.of(), ShareTableCheck.add(root, "1.2.0"));
        assertEquals(new TreeSet<>(), codes(ShareTableCheck.check(root, "1.2.0", null)));
    }

    @Test
    void initCreatesTheTableAndFreezeWritesTheBaseline(@TempDir Path dir) throws IOException {
        Path root = addon(dir, null);
        List<String> added = ShareTableCheck.init(root, "myaddon", "1.0.0", null);
        assertEquals(3, added.size());
        String table = Files.readString(root.resolve("data/myaddon/propwash_share/table.json"));
        assertTrue(table.startsWith("{\n  \"format\": 1,\n  \"namespace\": \"myaddon\",\n  \"parts\": {\n    \"frame\": [\n      {\"id\": \"alpha_5\", \"since\": \"1.0.0\", \"fallback\": \"propwash:freestyle_5\"},"), table);
        Path baseline = dir.resolve("out/baseline.json");
        ShareTableCheck.freeze(root, baseline);
        assertEquals("""
                {
                  "format": 1,
                  "kind": "propwash_share_baseline",
                  "namespaces": {
                    "myaddon": {
                      "frame": [
                        "alpha_5",
                        "beta_3"
                      ],
                      "motor": [
                        "m2207_1950"
                      ]
                    }
                  }
                }
                """, Files.readString(baseline));
    }

    @Test
    void mainReportsWithExitCodes(@TempDir Path dir) throws IOException {
        Path root = addon(dir, GOOD);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        assertEquals(0, ShareTableCheck.run(new String[]{"check", "--resources", root.toString(), "--version", "1.1.0"}, out));
        assertEquals(1, ShareTableCheck.run(new String[]{"check", "--resources", root.toString(), "--version", "1.0.0"}, out));
        assertEquals(2, ShareTableCheck.run(new String[]{"frobnicate"}, out));
        assertEquals(2, ShareTableCheck.run(new String[]{"check", "--resources"}, out));
        assertEquals(2, ShareTableCheck.run(new String[0], out));
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("SINCE_FUTURE"), text);
        assertTrue(text.contains("keine Befunde"), text);
    }
}
