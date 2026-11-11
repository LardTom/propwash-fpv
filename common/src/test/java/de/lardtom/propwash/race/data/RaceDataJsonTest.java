package de.lardtom.propwash.race.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.export.RaceDataJson;
import de.lardtom.propwash.race.export.RaceDataMirror;
import de.lardtom.propwash.race.export.RaceSnapshots;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RaceDataJsonTest {
    static final String KEY_1 = "5f0c2a8e-3b1d-4c6e-9a7f-1b2c3d4e5f60";
    static final String KEY_2 = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";
    static final String KEY_3 = "0f1e2d3c-4b5a-4968-8776-655443322110";
    private static final long S = TestWorlds.S;
    private static final long DAY = TestWorlds.DAY;
    private static final String GOLDEN = "PROPWASH_RACE_DATA_GOLDEN";
    private static final Set<String> COURSE_FIELDS = Set.of("id", "number", "name", "owner", "ownerName", "dimension", "layout", "layouts", "laps",
            "mode", "creativeFc", "elements", "checkpoints", "worldCourse", "start", "zones", "record", "leaderboard");
    private static final Set<String> ENTRY_FIELDS = Set.of("rank", "player", "name", "timeMs", "time", "date", "frame", "assist", "assistDetails",
            "rules", "serverRules", "creativeFc");

    @TempDir
    Path temp;

    static CourseRegistry fixture(Path dir) throws IOException {
        String json = TestWorlds.V052.replace("{\"id\": 1, ", "{\"id\": 1, \"uuid\": \"" + KEY_1 + "\", ")
                .replace("{\"id\": 2, ", "{\"id\": 2, \"uuid\": \"" + KEY_2 + "\", ")
                .replace("{\"id\": 3, ", "{\"id\": 3, \"uuid\": \"" + KEY_3 + "\", ");
        CourseRegistry registry = TestWorlds.open(dir, json);
        CourseRegistry.Course course = registry.course(1);
        registry.submitLap(course, TestWorlds.CARL, "Carl", "race_5", 33 * S, DAY - 500);
        registry.submitRace(course, TestWorlds.CARL, "Carl", 3, 101 * S, "race_5", DAY - 400);
        registry.records().archiveCurrent(course.id, 1, 2, DAY - 100);
        course.layout = 2;
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "race_5", 30_250_000_000L, DAY);
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "whoop_65", 41 * S, DAY + 10);
        registry.submitLap(course, TestWorlds.BEN, "Ben <B&B>", "race_5", 31_999_600_000L, DAY + 20);
        registry.submitAssistedLap(course, TestWorlds.BEN, "Ben <B&B>", "race_5", 28 * S, DAY + 40,
                AssistReasons.of(List.of(AssistDetail.hold(HoldMode.ALTHOLD))));
        registry.submitRace(course, TestWorlds.ANNA, "Anna", 3, 95 * S, "race_5", DAY + 50);
        registry.submitRace(course, TestWorlds.BEN, "Ben <B&B>", 3, 93 * S, "race_5", DAY + 60);
        registry.submitAssistedRace(course, TestWorlds.BEN, "Ben <B&B>", 2, 60 * S, AssistReasons.of(List.of(AssistDetail.hold(HoldMode.POSHOLD))),
                "race_5", DAY + 70);
        return registry;
    }

    static List<Zone> zones() {
        RuleSpec spec = PhysicsRules.SPECS.getFirst();
        return List.of(new Zone(4, TestWorlds.OVERWORLD, "Schwerelos", ZoneShape.BOX, ZoneBox.blocks(-55, -62, -45, -42, -50, -35), 0, true, 5,
                Map.of(spec.name(), spec.defaultValue())));
    }

    static List<CourseInfo> infos(CourseRegistry registry) {
        List<CourseInfo> out = new ArrayList<>();
        for (CourseRegistry.Course course : registry.courses()) {
            out.add(RaceSnapshots.info(course, registry.records(), zones()));
        }
        return out;
    }

    private static Path golden() {
        for (Path candidate : List.of(Path.of("common/src/test/resources/race-data"), Path.of("src/test/resources/race-data"))) {
            if (Files.isDirectory(candidate.getParent())) {
                return candidate;
            }
        }
        fail("Testressourcen nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static void compare(String name, String actual) throws IOException {
        Path file = golden().resolve(name);
        if (System.getenv(GOLDEN) != null) {
            RaceDataMirror.writeAtomic(file, actual);
        }
        assertTrue(Files.isRegularFile(file), "Vergleichsdatei fehlt: " + file + " (" + GOLDEN + "=1 schreibt sie)");
        assertEquals(Files.readString(file, StandardCharsets.UTF_8), actual, "JSON weicht von " + file + " ab");
    }

    @Test
    void mirrorMatchesTheReferenceFiles() throws IOException {
        CourseRegistry registry = fixture(this.temp);
        List<CourseInfo> infos = infos(registry);
        compare(RaceDataJson.COURSES, RaceDataJson.courses(registry, infos));
        for (CourseInfo info : infos) {
            compare(RaceDataJson.leaderboardPath(info), RaceDataJson.leaderboard(info, registry.course(info.number()), registry.records()));
        }
    }

    @Test
    void schemaHasAllDocumentedFields() throws IOException {
        CourseRegistry registry = fixture(this.temp);
        List<CourseInfo> infos = infos(registry);
        JsonObject courses = JsonParser.parseString(RaceDataJson.courses(registry, infos)).getAsJsonObject();
        assertEquals(RaceDataJson.SCHEMA, courses.get("schema").getAsInt());
        JsonArray list = courses.getAsJsonArray("courses");
        assertEquals(3, list.size());
        for (JsonElement element : list) {
            JsonObject course = element.getAsJsonObject();
            assertEquals(COURSE_FIELDS, course.keySet());
            assertEquals("leaderboards/" + course.get("id").getAsString() + ".json", course.get("leaderboard").getAsString());
        }
        JsonObject first = list.get(0).getAsJsonObject();
        assertEquals(KEY_1, first.get("id").getAsString());
        assertEquals("race_legal", first.getAsJsonObject("zones").get("status").getAsString());
        assertEquals(ENTRY_FIELDS, first.getAsJsonObject("record").keySet());
        assertTrue(list.get(1).getAsJsonObject().get("record").isJsonNull());
        assertTrue(list.get(2).getAsJsonObject().get("owner").isJsonNull());
        JsonObject board = JsonParser.parseString(RaceDataJson.leaderboard(infos.getFirst(), registry.course(1), registry.records())).getAsJsonObject();
        assertEquals(RaceDataJson.SCHEMA, board.get("schema").getAsInt());
        assertEquals(KEY_1, board.get("course").getAsString());
        JsonArray layouts = board.getAsJsonArray("layouts");
        assertEquals(2, layouts.size());
        JsonObject current = layouts.get(0).getAsJsonObject();
        assertTrue(current.get("current").getAsBoolean());
        assertEquals(2, current.get("layout").getAsInt());
        assertTrue(current.get("archivedAt").isJsonNull());
        JsonObject archived = layouts.get(1).getAsJsonObject();
        assertFalse(archived.get("current").getAsBoolean());
        assertEquals(DAY - 100, archived.get("archivedAt").getAsLong());
        assertEquals(33_000L, archived.getAsJsonObject("laps").getAsJsonArray("clean").get(0).getAsJsonObject().get("timeMs").getAsLong());
        JsonArray clean = current.getAsJsonObject("laps").getAsJsonArray("clean");
        assertEquals(3, clean.size());
        assertEquals("whoop_65", clean.get(2).getAsJsonObject().get("frame").getAsString());
        assertEquals(ENTRY_FIELDS, clean.get(0).getAsJsonObject().keySet());
        assertEquals("30.250", clean.get(0).getAsJsonObject().get("time").getAsString());
        assertEquals("Ben <B&B>", clean.get(1).getAsJsonObject().get("name").getAsString());
        JsonObject race = current.getAsJsonObject("races").getAsJsonObject("clean").getAsJsonArray("3").get(0).getAsJsonObject();
        assertEquals(3, race.get("laps").getAsInt());
        assertEquals(93_000L, race.get("timeMs").getAsLong());
        JsonObject assistedRace = current.getAsJsonObject("races").getAsJsonObject("assisted").getAsJsonArray("2").get(0).getAsJsonObject();
        assertEquals("hold_mode", assistedRace.getAsJsonArray("assist").get(0).getAsString());
        assertTrue(current.getAsJsonObject("classes").getAsJsonObject("clean").has("whoop_65"));
    }

    @Test
    void atomicWriteReplacesWithoutLeftovers() throws IOException {
        Path target = this.temp.resolve("propwash/race-data/leaderboards/" + KEY_1 + ".json");
        RaceDataMirror.writeAtomic(target, "{\"schema\": 1}\n");
        RaceDataMirror.writeAtomic(target, "{\"schema\": 1, \"course\": \"x\"}\n");
        assertEquals("{\"schema\": 1, \"course\": \"x\"}\n", Files.readString(target, StandardCharsets.UTF_8));
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertEquals(List.of(target.getFileName().toString()), files.map(p -> p.getFileName().toString()).toList());
        }
    }
}
