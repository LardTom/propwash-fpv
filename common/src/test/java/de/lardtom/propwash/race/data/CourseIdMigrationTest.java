package de.lardtom.propwash.race.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.race.CourseRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseIdMigrationTest {
    @TempDir
    Path temp;

    private static Map<Integer, UUID> keys(CourseRegistry registry) {
        Map<Integer, UUID> keys = new HashMap<>();
        for (CourseRegistry.Course course : registry.courses()) {
            keys.put(course.id, course.key());
        }
        return keys;
    }

    private static Map<Integer, Integer> hashes(CourseRegistry registry) {
        Map<Integer, Integer> hashes = new HashMap<>();
        for (CourseRegistry.Course course : registry.courses()) {
            hashes.put(course.id, course.toData().layoutHash());
        }
        return hashes;
    }

    @Test
    void everyOldFormGetsStableIdsOnFirstLoad() throws IOException {
        Map<String, Integer> expected = Map.of("0.4.0", 1, "0.5.0", 2, "0.5.2", 3);
        Map<String, String> forms = Map.of("0.4.0", TestWorlds.V040, "0.5.0", TestWorlds.V050, "0.5.2", TestWorlds.V052);
        Set<UUID> all = new HashSet<>();
        for (Map.Entry<String, String> form : forms.entrySet()) {
            Path dir = this.temp.resolve(form.getKey());
            Path file = TestWorlds.write(dir, form.getValue());
            CourseRegistry first = TestWorlds.reopen(file);
            int count = expected.get(form.getKey());
            assertEquals(count, first.courses().size(), form.getKey());
            assertEquals(count, first.assignedKeys(), form.getKey() + ": jeder alte Kurs bekommt beim ersten Laden eine ID");
            Map<Integer, UUID> keys = keys(first);
            Map<Integer, Integer> hashes = hashes(first);
            for (UUID key : keys.values()) {
                assertEquals(4, key.version(), form.getKey());
                assertTrue(all.add(key), form.getKey() + ": IDs sind eindeutig");
            }
            first.save();
            String saved = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(saved).getAsJsonObject();
            assertEquals(CourseRegistry.FORMAT, root.get("format").getAsInt());
            for (var course : root.getAsJsonArray("courses")) {
                JsonObject object = course.getAsJsonObject();
                assertTrue(object.has("uuid"), saved);
                assertEquals(keys.get(object.get("id").getAsInt()).toString(), object.get("uuid").getAsString());
                assertTrue(object.has("gates") && object.has("layout") && object.has("dimension"), "alte Felder bleiben");
            }
            CourseRegistry second = TestWorlds.reopen(file);
            assertEquals(0, second.assignedKeys(), form.getKey() + ": danach stabil");
            assertEquals(keys, keys(second), form.getKey());
            assertEquals(hashes, hashes(second), form.getKey() + ": Layout-Prüfsumme unverändert");
            for (CourseRegistry.Course course : second.courses()) {
                assertEquals(course, second.course(course.key()));
            }
        }
    }

    @Test
    void worldCopyKeepsIdsAndUnsavedFirstLoadIsNotPersisted() throws IOException {
        Path original = TestWorlds.write(this.temp.resolve("welt"), TestWorlds.V052);
        CourseRegistry first = TestWorlds.reopen(original);
        Map<Integer, UUID> keys = keys(first);
        first.save();
        Path copy = this.temp.resolve("welt-kopie").resolve(TestWorlds.FILE);
        Files.createDirectories(copy.getParent());
        Files.copy(original, copy);
        assertEquals(keys, keys(TestWorlds.reopen(copy)), "Welt-Kopie behält ihre IDs");
        Path fresh = TestWorlds.write(this.temp.resolve("ohne-speichern"), TestWorlds.V052);
        CourseRegistry unsaved = TestWorlds.reopen(fresh);
        assertTrue(unsaved.assignedKeys() > 0);
        assertFalse(Files.readString(fresh, StandardCharsets.UTF_8).contains("\"uuid\": \"" + keys(unsaved).get(1)),
                "ohne Speichern steht nichts in der Datei (CourseRegistry.start speichert sofort)");
    }

    @Test
    void brokenAndDuplicateIdsAreRepairedOnLoad() throws IOException {
        String json = TestWorlds.V052.replace("{\"id\": 1, ", "{\"id\": 1, \"uuid\": \"6b0a3c1e-5d2f-4e7a-9b8c-1d2e3f4a5b6c\", ")
                .replace("{\"id\": 2, ", "{\"id\": 2, \"uuid\": \"6B0A3C1E-5D2F-4E7A-9B8C-1D2E3F4A5B6C\", ")
                .replace("{\"id\": 3, ", "{\"id\": 3, \"uuid\": \"kaputt\", ");
        CourseRegistry registry = TestWorlds.open(this.temp.resolve("doppelt"), json);
        assertEquals(2, registry.assignedKeys());
        assertEquals(UUID.fromString("6b0a3c1e-5d2f-4e7a-9b8c-1d2e3f4a5b6c"), registry.course(1).key(), "der erste behält seine ID");
        assertEquals(3, new HashSet<>(List.copyOf(keys(registry).values())).size());
    }
}
