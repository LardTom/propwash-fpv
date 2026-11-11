package de.lardtom.propwash.stats.advancement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.testutil.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FpvAdvancementFilesTest {
    static final List<String> EXPECTED = List.of("root", "drone_built", "takeoff", "fpv_flight", "battery_swapped", "battery_charged",
            "distance_1km", "distance_10km", "distance_100km", "build_limit", "dive", "veteran", "first_lap", "personal_best",
            "ghost_beaten", "own_course", "first_trick", "power_loop", "combo", "school_chapter", "school_perfect", "cargo", "painted",
            "sharecode", "spectator", "enderman", "ender_dragon", "wither");
    static final Set<String> TRIGGERS = Set.of("propwash:milestone", "propwash:flight", "propwash:trick", "propwash:flight_school",
            "propwash:cargo_delivered", "propwash:drone_strike", "minecraft:inventory_changed", "minecraft:player_killed_entity");
    static final String BACKGROUND = "propwash:gui/advancements/backgrounds/propwash_fpv";

    static Path folder() {
        return TestPaths.mainResources().resolve("data/propwash/advancement/fpv");
    }

    static Map<String, JsonObject> files() throws IOException {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        Path dir = folder();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString();
                out.put(name.substring(0, name.length() - ".json".length()),
                        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        return out;
    }

    static JsonObject lang(String code) throws IOException {
        return JsonParser.parseString(Files.readString(TestPaths.lang("pw-stats").resolve(code + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    void tabHasRootAndAllAdvancementsOfTheList() throws IOException {
        Map<String, JsonObject> files = files();
        assertEquals(new TreeSet<>(EXPECTED), new TreeSet<>(files.keySet()), "Fortschritte im Tab Propwash FPV");
        assertTrue(files.size() - 1 >= 25, "etwa 25 Fortschritte unter der Wurzel");
        JsonObject root = files.get("root");
        assertFalse(root.has("parent"), "Wurzel ohne Eltern");
        JsonObject display = root.getAsJsonObject("display");
        assertEquals(BACKGROUND, display.get("background").getAsString());
        assertFalse(display.get("show_toast").getAsBoolean());
        assertFalse(display.get("announce_to_chat").getAsBoolean());
        String texture = BACKGROUND.substring("propwash:".length());
        assertTrue(Files.isRegularFile(TestPaths.mainResources().resolve("assets/propwash/textures/" + texture + ".png")), "Hintergrund " + texture);
    }

    @Test
    void everyAdvancementHangsBelowTheRoot() throws IOException {
        Map<String, JsonObject> files = files();
        assertFalse(files.isEmpty(), "keine Fortschritte unter " + folder());
        for (String name : files.keySet()) {
            Set<String> seen = new HashSet<>();
            String current = name;
            while (!current.equals("root")) {
                assertTrue(seen.add(current), "Kreis bei " + name);
                JsonObject json = files.get(current);
                assertTrue(json != null && json.has("parent"), current + " ohne Eltern");
                String parent = json.get("parent").getAsString();
                assertTrue(parent.startsWith("propwash:fpv/"), current + ": Eltern " + parent);
                current = parent.substring("propwash:fpv/".length());
                assertTrue(files.containsKey(current), name + ": unbekannte Eltern " + parent);
            }
        }
    }

    @Test
    void criteriaUseOwnTriggersAndNeverImpossible() throws IOException {
        Map<String, JsonObject> files = files();
        assertFalse(files.isEmpty(), "keine Fortschritte unter " + folder());
        Set<String> own = new TreeSet<>();
        for (Map.Entry<String, JsonObject> entry : files.entrySet()) {
            JsonObject criteria = entry.getValue().getAsJsonObject("criteria");
            assertTrue(criteria != null && !criteria.isEmpty(), entry.getKey() + ": keine Kriterien");
            Set<String> listed = new HashSet<>();
            for (JsonElement group : entry.getValue().getAsJsonArray("requirements")) {
                for (JsonElement id : group.getAsJsonArray()) {
                    listed.add(id.getAsString());
                }
            }
            assertEquals(criteria.keySet(), listed, entry.getKey() + ": requirements nennen alle Kriterien");
            for (String key : criteria.keySet()) {
                String trigger = criteria.getAsJsonObject(key).get("trigger").getAsString();
                assertTrue(TRIGGERS.contains(trigger), entry.getKey() + ": Auslöser " + trigger);
                if (trigger.startsWith("propwash:")) {
                    own.add(trigger);
                }
            }
        }
        assertEquals(new TreeSet<>(TRIGGERS.stream().filter(t -> t.startsWith("propwash:")).toList()), own, "jeder eigene Auslöser wird genutzt");
    }

    @Test
    void bossesNeedTheKillingBlowOfADrone() throws IOException {
        Map<String, JsonObject> files = files();
        for (String boss : List.of("ender_dragon", "wither")) {
            JsonObject json = files.get(boss);
            assertTrue(json != null, boss + " fehlt");
            JsonObject conditions = json.getAsJsonObject("criteria").entrySet().iterator().next().getValue().getAsJsonObject()
                    .getAsJsonObject("conditions");
            assertEquals("minecraft:" + boss, entityType(conditions.getAsJsonObject("entity")));
            assertEquals("propwash:drone", conditions.getAsJsonObject("killing_blow").getAsJsonObject("direct_entity")
                    .get("minecraft:entity_type").getAsString());
            assertEquals("challenge", json.getAsJsonObject("display").get("frame").getAsString());
        }
        JsonObject enderman = files.get("enderman");
        assertTrue(enderman != null, "enderman fehlt");
        JsonObject criterion = enderman.getAsJsonObject("criteria").entrySet().iterator().next().getValue().getAsJsonObject();
        assertEquals("propwash:drone_strike", criterion.get("trigger").getAsString());
        assertEquals("minecraft:enderman", entityType(criterion.getAsJsonObject("conditions").getAsJsonObject("entity")));
    }

    private static String entityType(JsonObject condition) {
        assertEquals("minecraft:entity_properties", condition.get("type").getAsString());
        assertEquals("this", condition.get("entity").getAsString());
        return condition.getAsJsonObject("predicate").get("minecraft:entity_type").getAsString();
    }

    @Test
    void textsAndIconsExistInBothLanguages() throws IOException {
        Map<String, JsonObject> files = files();
        assertFalse(files.isEmpty(), "keine Fortschritte unter " + folder());
        JsonObject de = lang("de_de");
        JsonObject en = lang("en_us");
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : files.entrySet()) {
            JsonObject display = entry.getValue().getAsJsonObject("display");
            for (String part : List.of("title", "description")) {
                String key = display.getAsJsonObject(part).get("translate").getAsString();
                if (!key.equals("advancements.propwash.fpv." + entry.getKey() + "." + part)) {
                    problems.add(entry.getKey() + ": Schlüssel " + key);
                }
                if (!de.has(key) || !en.has(key)) {
                    problems.add("Text fehlt: " + key);
                } else if (de.get(key).getAsString().equals(en.get(key).getAsString()) && part.equals("description")) {
                    problems.add("nicht übersetzt: " + key);
                }
            }
            String icon = display.getAsJsonObject("icon").get("id").getAsString();
            if (icon.startsWith("propwash:")
                    && !Files.isRegularFile(TestPaths.mainResources().resolve("assets/propwash/items/" + icon.substring(9) + ".json"))) {
                problems.add(entry.getKey() + ": Symbol " + icon);
            }
        }
        assertTrue(problems.isEmpty(), String.join("; ", problems));
    }
}
