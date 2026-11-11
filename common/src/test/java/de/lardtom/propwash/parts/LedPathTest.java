package de.lardtom.propwash.parts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedPathTest {
    private static final Identifier RING = Identifier.fromNamespaceAndPath("justmoreparts", "led_ring");

    static List<Vec3Mm> ring(int segments, double radius, double y) {
        List<Vec3Mm> out = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            out.add(new Vec3Mm(radius * Math.cos(angle), y, radius * Math.sin(angle)));
        }
        return out;
    }

    private static AccessoryDefinition.Builder ringBuilder() {
        return AccessoryDefinition.builder(RING).basics(PartBasics.of(8.0)).slot(AccessorySlot.TOP).size(50.0, 4.0, 50.0)
                .flightBatteryWatts(2.0).frontalArea(0.0002).leds(16);
    }

    private static String json(String ledPath, int leds) {
        return """
                {
                  "format": 1,
                  "mass_g": 8.0,
                  "slot": "top",
                  "size_mm": [50.0, 4.0, 50.0],
                  "leds": %d,
                  "led_path": %s
                }
                """.formatted(leds, ledPath);
    }

    private static PartDataProblems problems(String text) {
        PartDataProblems problems = new PartDataProblems("led_ring.json");
        JsonObject json = PartJson.parse(text, problems);
        assertNotNull(json, problems.errors().toString());
        PartJson.decode(PartKind.ACCESSORY, RING, json, problems);
        return problems;
    }

    private static AccessoryDefinition decode(String text) {
        PartDataProblems problems = new PartDataProblems("led_ring.json");
        JsonObject json = PartJson.parse(text, problems);
        assertNotNull(json, problems.errors().toString());
        PartDefinition definition = PartJson.decode(PartKind.ACCESSORY, RING, json, problems);
        assertEquals(List.of(), problems.errors(), text);
        assertEquals(List.of(), problems.warnings(), text);
        return (AccessoryDefinition) definition;
    }

    private static void assertRejected(String text, String... fragments) {
        PartDataProblems problems = new PartDataProblems("led_ring.json");
        JsonObject json = PartJson.parse(text, problems);
        if (json != null) {
            assertNull(PartJson.decode(PartKind.ACCESSORY, RING, json, problems), text);
        }
        assertTrue(problems.hasErrors(), text);
        String all = String.join("\n", problems.errors());
        assertTrue(all.contains("led_path"), all);
        for (String fragment : fragments) {
            assertTrue(all.contains(fragment), "erwartet \"" + fragment + "\" in:\n" + all);
        }
    }

    private static String points(int count) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            out.append(i == 0 ? "" : ", ").append("[").append(i * 0.5).append(", 0.0, 0.0]");
        }
        return out.append("]").toString();
    }

    private static double distance(double[] a, double[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }

    @Test
    void pathNeedsTwoTo256Points() {
        assertRejected(json(points(1), 4), "2 bis 256", "gefunden: 1 Punkt");
        assertRejected(json(points(257), 4), "2 bis 256", "257 Punkte");
        assertRejected(json("[]", 4), "2 bis 256");
        assertEquals(2, decode(json(points(2), 4)).ledPathMm().size());
        assertEquals(256, decode(json(points(256), 4)).ledPathMm().size());
        IllegalArgumentException one = assertThrows(IllegalArgumentException.class,
                () -> ringBuilder().ledPath(new Vec3Mm(0.0, 0.0, 0.0)).build());
        assertTrue(one.getMessage().contains("2 bis 256") && one.getMessage().contains("hat 1"), one.getMessage());
        List<Vec3Mm> tooMany = new ArrayList<>();
        for (int i = 0; i < 257; i++) {
            tooMany.add(new Vec3Mm(i * 0.5, 0.0, 0.0));
        }
        assertThrows(IllegalArgumentException.class, () -> ringBuilder().ledPath(tooMany).build());
    }

    @Test
    void pointsMustBeFiniteTriples() {
        assertRejected(json("[[0.0, 0.0, 0.0], [1e999, 0.0, 0.0]]", 4), "Punkt 2", "endlichen");
        assertRejected(json("[[0.0, 0.0, 0.0], [\"NaN\", 0.0, 0.0]]", 4), "Punkt 2");
        assertRejected(json("[[0.0, 0.0, 0.0], [1.0, 0.0]]", 4), "Punkt 2", "[x, y, z]");
        assertRejected(json("[[0.0, 0.0, 0.0], null]", 4), "Punkt 2");
        assertRejected(json("\"ring\"", 4), "2 bis 256");
        assertThrows(IllegalArgumentException.class, () -> new Vec3Mm(Double.NaN, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Vec3Mm(0.0, Double.POSITIVE_INFINITY, 0.0));
        List<Vec3Mm> withNull = new ArrayList<>(List.of(new Vec3Mm(0.0, 0.0, 0.0)));
        withNull.add(null);
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> ringBuilder().ledPath(withNull).build());
        assertTrue(missing.getMessage().contains("Punkt 2 fehlt"), missing.getMessage());
    }

    @Test
    void pointsStayWithin500Millimetres() {
        assertEquals(500.0, decode(json("[[-500.0, 0.0, 0.0], [500.0, 500.0, -500.0]]", 4)).ledPathMm().get(1).y());
        assertRejected(json("[[0.0, 0.0, 0.0], [500.5, 0.0, 0.0]]", 4), "Punkt 2", "±500 mm");
        assertRejected(json("[[0.0, -501.0, 0.0], [1.0, 0.0, 0.0]]", 4), "Punkt 1", "±500 mm");
        assertRejected(json("[[0.0, 0.0, 0.0], [0.0, 0.0, 9999.0]]", 4), "±500 mm");
        assertThrows(IllegalArgumentException.class, () -> ringBuilder().ledPath(new Vec3Mm(0.0, 0.0, 0.0), new Vec3Mm(0.0, 0.0, -600.0)).build());
    }

    @Test
    void pathNeedsLedsAndALength() {
        assertRejected(json(points(3), 0), "ohne LEDs");
        String noLeds = """
                {"format": 1, "mass_g": 8.0, "slot": "top", "size_mm": [50.0, 4.0, 50.0], "led_path": [[0, 0, 0], [10, 0, 0]]}
                """;
        assertRejected(noLeds, "ohne LEDs");
        assertRejected(json("[[1.0, 2.0, 3.0], [1.0, 2.0, 3.0], [1.0, 2.0, 3.0]]", 4), "Länge 0");
        IllegalArgumentException dark = assertThrows(IllegalArgumentException.class,
                () -> ringBuilder().leds(0).ledPath(ring(8, 20.0, 6.0)).build());
        assertTrue(dark.getMessage().contains("ohne LEDs"), dark.getMessage());
    }

    @Test
    void parsesARingAndSurvivesEncodeAndDecode() {
        AccessoryDefinition parsed = decode(json("[[20, 6, 0], [0, 6, 20], [-20, 6, 0], [0, 6, -20], [20, 6, 0]]", 16));
        assertEquals(List.of(new Vec3Mm(20.0, 6.0, 0.0), new Vec3Mm(0.0, 6.0, 20.0), new Vec3Mm(-20.0, 6.0, 0.0), new Vec3Mm(0.0, 6.0, -20.0),
                new Vec3Mm(20.0, 6.0, 0.0)), parsed.ledPathMm());
        AccessoryDefinition built = ringBuilder().ledPath(ring(24, 22.0, 6.0)).build();
        String text = JsonText.pretty(PartJson.encode(built));
        assertEquals(built, decode(text));
        assertThrows(UnsupportedOperationException.class, () -> built.ledPathMm().add(Vec3Mm.ZERO));
        List<Vec3Mm> source = new ArrayList<>(ring(4, 10.0, 0.0));
        AccessoryDefinition copied = ringBuilder().ledPath(source).build();
        source.clear();
        assertEquals(5, copied.ledPathMm().size());
    }

    @Test
    void codecForToolsCarriesThePathForTheWebExport() {
        AccessoryDefinition built = ringBuilder().ledPath(new Vec3Mm(-10.0, 6.0, 0.0), new Vec3Mm(0.0, 9.5, -4.0), new Vec3Mm(10.0, 6.0, 0.0))
                .build();
        JsonObject json = PartRegistryImpl.INSTANCE.toJson(built);
        JsonArray path = json.getAsJsonArray("led_path");
        assertNotNull(path, json.toString());
        assertEquals(3, path.size());
        assertEquals(List.of(0.0, 9.5, -4.0), List.of(path.get(1).getAsJsonArray().get(0).getAsDouble(),
                path.get(1).getAsJsonArray().get(1).getAsDouble(), path.get(1).getAsJsonArray().get(2).getAsDouble()));
        assertEquals(16, json.get("leds").getAsInt());
        assertEquals(built, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, RING, json));
        JsonObject straight = PartRegistryImpl.INSTANCE.toJson(AccessoryType.LED_STRIP.definition());
        assertFalse(straight.has("led_path"), straight.toString());
    }

    @Test
    void partsWithoutPathBehaveAsBefore() {
        AccessoryDefinition old = new AccessoryDefinition(RING, PartBasics.of(6.0), AccessorySlot.BOTTOM, 32.0, 3.0, 8.0, 1.5, 0.0001, 8,
                Optional.empty(), 0.0, 0.0);
        assertEquals(List.of(), old.ledPathMm());
        AccessoryDefinition built = AccessoryDefinition.builder(RING).basics(PartBasics.of(6.0)).slot(AccessorySlot.BOTTOM).size(32.0, 3.0, 8.0)
                .flightBatteryWatts(1.5).frontalArea(0.0001).leds(8).build();
        assertEquals(built, old);
        assertEquals(old, new AccessoryDefinition(RING, PartBasics.of(6.0), AccessorySlot.BOTTOM, 32.0, 3.0, 8.0, 1.5, 0.0001, 8,
                Optional.empty(), 0.0, 0.0, null));
        assertFalse(PartJson.encode(old).has("led_path"));
        AccessoryType strip = AccessoryType.LED_STRIP;
        assertFalse(strip.hasLedPath());
        assertEquals(List.of(), strip.definition().ledPathMm());
        double length = 40.0;
        for (int i = 0; i < strip.ledCount; i++) {
            double[] led = strip.ledMm(i, length);
            assertEquals(-length / 2.0 + length / strip.ledCount * (i + 0.5), led[0], 1.0E-12);
            assertEquals(-strip.heightMm, led[1], 1.0E-12);
            assertEquals(0.0, led[2], 1.0E-12);
        }
        AccessoryAnchor bottom = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.BOTTOM);
        assertEquals(bottom.lengthMm() / strip.widthMm, bottom.stretch(strip), 1.0E-12);
    }

    @Test
    void ledsSpreadEvenlyAlongThePath() {
        AccessoryType ring = (AccessoryType) PartIndex.detached(ringBuilder().ledPath(ring(32, 20.0, 6.0)).build());
        assertTrue(ring.hasLedPath());
        assertEquals(33, ring.ledPath().size());
        double[] first = ring.ledMm(0, 999.0);
        double spacing = distance(first, ring.ledMm(1, 999.0));
        for (int i = 0; i < ring.ledCount; i++) {
            double[] led = ring.ledMm(i, 999.0);
            assertEquals(6.0, led[1], 1.0E-9);
            assertEquals(20.0, Math.hypot(led[0], led[2]), 0.1, "LED " + i + " liegt auf dem Ring");
            double[] next = ring.ledMm((i + 1) % ring.ledCount, 999.0);
            assertEquals(spacing, distance(led, next), 0.05, "Abstand LED " + i);
        }
        AccessoryType bent = (AccessoryType) PartIndex.detached(ringBuilder().leds(4)
                .ledPath(new Vec3Mm(0.0, 0.0, 0.0), new Vec3Mm(30.0, 0.0, 0.0), new Vec3Mm(30.0, 0.0, -10.0)).build());
        double[][] expected = {{5.0, 0.0, 0.0}, {15.0, 0.0, 0.0}, {25.0, 0.0, 0.0}, {30.0, 0.0, -5.0}};
        for (int i = 0; i < expected.length; i++) {
            double[] led = bent.ledMm(i, 1.0);
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(expected[i][axis], led[axis], 1.0E-9, "LED " + i);
            }
        }
        AccessoryType withPause = (AccessoryType) PartIndex.detached(ringBuilder().leds(2)
                .ledPath(new Vec3Mm(0.0, 0.0, 0.0), new Vec3Mm(0.0, 0.0, 0.0), new Vec3Mm(8.0, 0.0, 0.0)).build());
        assertEquals(2.0, withPause.ledMm(0, 1.0)[0], 1.0E-9);
        assertEquals(6.0, withPause.ledMm(1, 1.0)[0], 1.0E-9);
    }

    @Test
    void pathPartsKeepTheirShapeUnderTheFrame() {
        AccessoryDefinition definition = AccessoryDefinition.builder(RING).basics(PartBasics.of(6.0)).slot(AccessorySlot.BOTTOM)
                .size(30.0, 3.0, 30.0).leds(12).ledPath(ring(12, 12.0, -3.0)).build();
        AccessoryType type = (AccessoryType) PartIndex.detached(definition);
        AccessoryAnchor bottom = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.BOTTOM);
        assertTrue(bottom.lengthMm() > 0.0);
        assertEquals(1.0, bottom.stretch(type));
        double[] local = type.ledMm(3, bottom.lengthMm());
        double[] framed = bottom.ledMm(type, 3);
        assertEquals(bottom.toFrame(local)[0], framed[0], 1.0E-9);
        assertEquals(bottom.toFrame(local)[1], framed[1], 1.0E-9);
        assertEquals(bottom.toFrame(local)[2], framed[2], 1.0E-9);
    }

    @Test
    void handshakeHashSeesThePath() {
        AccessoryDefinition a = ringBuilder().ledPath(ring(8, 20.0, 6.0)).build();
        AccessoryDefinition b = ringBuilder().ledPath(ring(8, 21.0, 6.0)).build();
        AccessoryDefinition none = ringBuilder().build();
        assertNotEquals(a, b);
        assertNotEquals(PartIndex.hashOf(a), PartIndex.hashOf(b));
        assertNotEquals(PartIndex.hashOf(a), PartIndex.hashOf(none));
        assertEquals(PartIndex.hashOf(a), PartIndex.hashOf(ringBuilder().ledPath(ring(8, 20.0, 6.0)).build()));
    }
}
