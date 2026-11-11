package de.lardtom.propwash.zone;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneSourcesTest {
    private static final String DIM = Zone.OVERWORLD;

    private static Map<String, String> lang(String language) throws IOException {
        Map<String, String> out = new HashMap<>();
        for (Path root : List.of(Path.of("common/src/lang"), Path.of("src/lang"))) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
                for (Path dir : dirs) {
                    Path file = dir.resolve(language + ".json");
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                            out.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                }
            }
            return out;
        }
        throw new IOException("Sprachdateien nicht gefunden");
    }

    private static String text(Object part, Map<String, String> lang) {
        if (!(part instanceof Component component)) {
            return String.valueOf(part);
        }
        StringBuilder out = new StringBuilder();
        if (component.getContents() instanceof TranslatableContents translatable) {
            String pattern = lang.getOrDefault(translatable.getKey(), translatable.getKey());
            Object[] args = translatable.getArgs();
            int next = 0;
            int i = 0;
            while (i < pattern.length()) {
                if (pattern.startsWith("%s", i)) {
                    out.append(next < args.length ? text(args[next++], lang) : "");
                    i += 2;
                } else if (pattern.startsWith("%%", i)) {
                    out.append('%');
                    i += 2;
                } else {
                    out.append(pattern.charAt(i++));
                }
            }
        } else if (component.getContents() instanceof PlainTextContents plain) {
            out.append(plain.text());
        }
        for (Component sibling : component.getSiblings()) {
            out.append(text(sibling, lang));
        }
        return out.toString();
    }

    private static Zone course() {
        return new Zone(1, DIM, "Course", ZoneShape.BOX, new ZoneBox(0, 0, 0, 100, 40, 100), 0, false, 0,
                Map.of("gravity_scale", 0, "battery_scale", 0));
    }

    private static Zone plain() {
        return new Zone(6, DIM, "Plain", ZoneShape.BOX, new ZoneBox(40, 0, 40, 60, 20, 60), 1, false, 0, Map.of());
    }

    private static Zone passage() {
        return new Zone(2, DIM, "Passage", ZoneShape.BOX, new ZoneBox(40, 0, 40, 60, 20, 60), 1, true, 0, Map.of("wind", 0))
                .withWorld(WorldRuleSpecs.GRAVITY_SCALE);
    }

    @Test
    void resolutionNamesTheZoneThatDecidesEachRule() {
        ZoneResolution here = ZoneResolver.resolve(List.of(course(), passage()), DIM, PhysicsRules.DEFAULT, 50, 5, 50);
        assertEquals(passage(), here.forcedBy(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(passage(), here.decider(WorldRuleSpecs.GRAVITY_SCALE));
        assertNull(here.source(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(passage(), here.decider(WorldRuleSpecs.WIND));
        assertNull(here.forcedBy(WorldRuleSpecs.WIND));
        assertEquals(course(), here.decider(WorldRuleSpecs.BATTERY_SCALE));
        assertNull(here.decider(WorldRuleSpecs.CRASH_TOLERANCE));
        assertNull(ZoneResolution.world(PhysicsRules.DEFAULT).forcedBy(WorldRuleSpecs.GRAVITY_SCALE));
    }

    @Test
    void noticeSaysWhichZoneSuppliesWhichValue() throws IOException {
        ZoneResolution here = ZoneResolver.resolve(List.of(course(), passage()), DIM, PhysicsRules.DEFAULT, 50, 5, 50);
        assertEquals("Gravity 100% (world), Wind Off · from “Course”: Battery life Infinite · counts as assisted",
                text(ZoneText.notice(here), lang("en_us")));
        assertEquals("Schwerkraft 100 % (Welt), Wind Aus · aus „Course“: Akkulaufzeit Unendlich · zählt als Flughilfe",
                text(ZoneText.notice(here), lang("de_de")));
        ZoneResolution outer = ZoneResolver.resolve(List.of(course(), passage()), DIM, PhysicsRules.DEFAULT, 10, 5, 10);
        assertEquals("Gravity 0%, Battery life Infinite · counts as assisted", text(ZoneText.notice(outer), lang("en_us")));
        Zone neutral = new Zone(3, DIM, "Empty", ZoneShape.BOX, new ZoneBox(200, 0, 200, 210, 10, 210), 0, false, 0, Map.of());
        assertEquals("physics as world", text(ZoneText.notice(ZoneResolver.resolve(List.of(neutral), DIM, PhysicsRules.DEFAULT, 205, 5, 205)),
                lang("en_us")));
    }

    @Test
    void listsShowForcedWorldValues() throws IOException {
        assertEquals("Gravity: world value, Wind Off", text(ZoneText.overrides(passage()), lang("en_us")));
        assertEquals("Schwerkraft: Weltwert, Wind Aus", text(ZoneText.overrides(passage()), lang("de_de")));
        ZoneRelease locked = ZoneRelease.DEFAULT.with(WorldRuleSpecs.GRAVITY_SCALE.id(), false);
        assertEquals("Gravity: world value (locked), Wind Off", text(ZoneText.overrides(passage(), locked), lang("en_us")));
    }

    @Test
    void belowIgnoresTheZoneItselfAndHigherZones() {
        Zone top = new Zone(4, DIM, "Top", ZoneShape.BOX, new ZoneBox(0, 0, 0, 100, 40, 100), 5, false, 0, Map.of("gravity_scale", 300));
        List<Zone> zones = List.of(course(), passage(), top);
        PhysicsRules below = ZoneResolver.below(zones, passage(), PhysicsRules.DEFAULT, 50, 5, 50).rules();
        assertEquals(0, below.gravityPercent(), "unter der Passage liegt nur der Kurs");
        PhysicsRules underCourse = ZoneResolver.below(zones, course(), PhysicsRules.DEFAULT, 50, 5, 50).rules();
        assertEquals(100, underCourse.gravityPercent(), "unter dem Kurs liegt die Welt");
        Zone fresh = Zone.create(DIM, "Neu", ZoneShape.BOX, new ZoneBox(40, 0, 40, 60, 20, 60));
        assertEquals(0, ZoneResolver.below(zones, fresh, PhysicsRules.DEFAULT, 50, 5, 50).rules().gravityPercent(),
                "eine neue Zone liegt bei gleicher Priorität über den bestehenden");
        PhysicsRules heavy = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 130));
        assertEquals(130, ZoneResolver.below(List.of(), fresh, heavy, 50, 5, 50).rules().gravityPercent());
    }

    @Test
    void editorStartsOwnValuesFromTheValueThatAppliesSoFar() {
        ZoneDraft.Row gravity = new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.GRAVITY_SCALE);
        PhysicsRules world = PhysicsRules.DEFAULT;
        PhysicsRules below = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 0));
        ZoneDraft fromBelow = new ZoneDraft(plain());
        fromBelow.change(gravity, 1, false, world, below);
        assertEquals(10, fromBelow.zone().value(WorldRuleSpecs.GRAVITY_SCALE, -1), "von „darunter: 0 %“ einen Schritt hoch");
        ZoneDraft fromWorld = new ZoneDraft(plain().withWorld(WorldRuleSpecs.GRAVITY_SCALE));
        fromWorld.change(gravity, 1, false, world, below);
        assertEquals(110, fromWorld.zone().value(WorldRuleSpecs.GRAVITY_SCALE, -1), "vom Weltwert 100 % einen Schritt hoch");
    }

    @Test
    void outlinesOnlyWithToolUnlessAlwaysIsChosen() {
        assertFalse(ZoneView.outlines(ZoneView.Show.TOOL, false, false, false), "nur mit Werkzeug: ohne Werkzeug keine Umrisse");
        assertTrue(ZoneView.outlines(ZoneView.Show.TOOL, true, false, false));
        assertTrue(ZoneView.outlines(ZoneView.Show.TOOL, false, true, false), "beim Bearbeiten immer");
        assertTrue(ZoneView.outlines(ZoneView.Show.ALWAYS, false, false, false));
        assertFalse(ZoneView.outlines(null, false, false, false));
    }

    @Test
    void outlinesInFlightOnlyWithTheModeForIt() {
        assertTrue(ZoneView.outlines(ZoneView.Show.TOOL_OR_FLIGHT, false, false, true), "Standard: im Flug Umrisse, LOS und FPV");
        assertFalse(ZoneView.outlines(ZoneView.Show.TOOL_OR_FLIGHT, false, false, false), "Standard: zu Fuß ohne Werkzeug keine Umrisse");
        assertTrue(ZoneView.outlines(ZoneView.Show.TOOL_OR_FLIGHT, true, false, false), "Standard: zu Fuß mit Werkzeug");
        assertTrue(ZoneView.outlines(ZoneView.Show.TOOL_OR_FLIGHT, false, true, false), "Standard: beim Bearbeiten");
        assertFalse(ZoneView.outlines(ZoneView.Show.TOOL, false, false, true), "nur mit Werkzeug: im Flug keine Umrisse");
        assertTrue(ZoneView.outlines(ZoneView.Show.ALWAYS, false, false, true));
        assertFalse(ZoneView.outlines(null, false, false, true));
    }

    @Test
    void overlappingZonesShowOnlyEdgesExceptTheActiveOne() {
        Zone far = new Zone(5, DIM, "Far", ZoneShape.BOX, new ZoneBox(500, 0, 500, 510, 10, 510), 0, false, 0, Map.of());
        List<Zone> zones = List.of(course(), passage(), far);
        int active = ZoneView.highlight(0, zones, 50, 5, 50);
        assertEquals(2, active, "die Zone, in der man ist (höchste Priorität)");
        Map<Integer, ZoneView.Style> styles = ZoneView.styles(zones, null, active);
        assertEquals(Map.of(1, ZoneView.Style.EDGES, 2, ZoneView.Style.HIGHLIGHT, 5, ZoneView.Style.FULL), styles);
        assertEquals(Map.of(1, ZoneView.Style.EDGES, 2, ZoneView.Style.EDGES, 5, ZoneView.Style.FULL), ZoneView.styles(zones, null, 0),
                "außerhalb beider Zonen nur Kanten");
        assertEquals(5, ZoneView.highlight(5, zones, 50, 5, 50), "„Zeigen“ geht vor");
        assertEquals(ZoneView.Style.EDGES, ZoneView.styles(List.of(far), new ZoneBox(505, 0, 505, 520, 10, 520), 0).get(5),
                "neben dem Entwurf nur Kanten");
        assertTrue(ZoneView.overlap(course().box(), passage().box()));
        assertFalse(ZoneView.overlap(course().box(), far.box()));
        assertFalse(ZoneView.overlap(new ZoneBox(0, 0, 0, 10, 10, 10), new ZoneBox(10, 0, 0, 20, 10, 10)), "nur berührende Zonen überlappen nicht");
    }
}
