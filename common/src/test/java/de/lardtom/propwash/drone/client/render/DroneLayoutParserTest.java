package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonParser;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.FrameType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneLayoutParserTest {
    private static final String CONTRACT_EXAMPLE = """
            {
              "frames": {
                "freestyle_5": {
                  "motors": [[-79.5, 3.0, -79.5], [79.5, 3.0, -79.5], [-79.5, 3.0, 79.5], [79.5, 3.0, 79.5]],
                  "motor_spin": ["cw", "ccw", "ccw", "cw"],
                  "prop_height": 16.0,
                  "stack": [0.0, 3.0, 0.0],
                  "battery": [0.0, 32.0, 6.0],
                  "video": [0.0, 12.0, -48.0],
                  "camera_tilt_default": 25.0
                }
              }
            }
            """;

    private static DroneLayoutParser.Result parse(String json) {
        return DroneLayoutParser.parse(JsonParser.parseString(json));
    }

    @Test
    void parsesTheAssetContractExample() {
        DroneLayoutParser.Result result = parse(CONTRACT_EXAMPLE);
        DroneLayout layout = result.layouts().get(FrameType.FREESTYLE_5);
        assertTrue(layout.fromFile());
        assertEquals(-79.5F, layout.motor(DroneLayout.FRONT_LEFT).x(), 1.0E-6F);
        assertEquals(79.5F, layout.motor(DroneLayout.REAR_RIGHT).z(), 1.0E-6F);
        assertFalse(layout.counterClockwise(DroneLayout.FRONT_LEFT));
        assertTrue(layout.counterClockwise(DroneLayout.FRONT_RIGHT));
        assertEquals(16.0F, layout.propHeight(), 1.0E-6F);
        assertEquals(6.0F, layout.battery().z(), 1.0E-6F);
        assertEquals(-48.0F, layout.video().z(), 1.0E-6F);
        assertEquals(25.0F, layout.cameraTiltDegrees(), 1.0E-6F);
        assertEquals(FrameType.values().length - 1, result.problems().size(), result.problems().toString());
        assertEquals(1, result.layouts().size());
    }

    @Test
    void missingOptionalFieldsFallBack() {
        DroneLayoutParser.Result result = parse("""
                {"frames": {"race_5": {"motors": [[-70, 3, -80], [70, 3, -80], [-70, 3, 80], [70, 3, 80]]}}}
                """);
        DroneLayout layout = result.layouts().get(FrameType.RACE_5);
        DroneLayout fallback = DroneLayout.fallback(FrameType.RACE_5);
        assertFalse(layout.hasPropHeight());
        assertEquals(fallback.stack(), layout.stack());
        assertEquals(fallback.video(), layout.video());
        assertEquals(fallback.cameraTiltDegrees(), layout.cameraTiltDegrees());
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            assertEquals(DroneLayout.propsInCounterClockwise(slot), layout.counterClockwise(slot));
        }
    }

    @Test
    void brokenEntriesAreRejectedWithProblems() {
        DroneLayoutParser.Result result = parse("""
                {"frames": {
                  "whoop_65": {"motors": [[0, 0, 0], [1, 1, 1]]},
                  "cinewhoop_3": {"motors": [[0, 0, 0], [1, 1, 1], [2, 2, 2], [3, "x", 3]]},
                  "race_5": {"motors": [[-70, 3, -80], [70, 3, -80], [-70, 3, 80], [70, 3, 80]], "motor_spin": ["cw", "up", "cw", "cw"],
                             "prop_height": -4, "video": [1, 2], "camera_tilt_default": 400},
                  "banana_9": {}
                }}
                """);
        assertFalse(result.layouts().containsKey(FrameType.WHOOP_65));
        assertFalse(result.layouts().containsKey(FrameType.CINEWHOOP_3));
        DroneLayout race = result.layouts().get(FrameType.RACE_5);
        assertTrue(race.fromFile());
        assertFalse(race.hasPropHeight());
        assertEquals(DroneLayout.fallback(FrameType.RACE_5).video(), race.video());
        assertEquals(DroneLayout.fallback(FrameType.RACE_5).cameraTiltDegrees(), race.cameraTiltDegrees());
        assertTrue(race.counterClockwise(DroneLayout.FRONT_RIGHT));
        String problems = result.problems().toString();
        assertTrue(problems.contains("banana_9"), problems);
        assertTrue(problems.contains("whoop_65"), problems);
        assertTrue(problems.contains("motor_spin"), problems);
        assertTrue(problems.contains("prop_height"), problems);
    }

    @Test
    void nonObjectRootIsReported() {
        assertTrue(parse("[]").layouts().isEmpty());
        assertEquals(1, parse("[]").problems().size());
        assertEquals(1, parse("{}").problems().size());
        assertTrue(DroneLayoutParser.parse(null).layouts().isEmpty());
    }

    @Test
    void absurdCoordinatesAreRejected() {
        DroneLayoutParser.Result result = parse("""
                {"frames": {"long_range_7": {"motors": [[-1e9, 3, -100], [100, 3, -100], [-100, 3, 100], [100, 3, 100]]}}}
                """);
        assertFalse(result.layouts().containsKey(FrameType.LONG_RANGE_7));
    }

    @Test
    void shippedLayoutMatchesTheAccessoryTableOfTheFlightModel() throws IOException {
        Path file = Files.isDirectory(Path.of("common/src/main/resources")) ? Path.of("common/src/main/resources") : Path.of("src/main/resources");
        DroneLayoutParser.Result result = DroneLayoutParser.parse(JsonParser.parseString(
                Files.readString(file.resolve("assets/propwash/drone_layout.json"))));
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        for (FrameType frame : FrameType.values()) {
            DroneLayout layout = result.layouts().get(frame);
            assertEquals(frame.accessoryMounts, layout.accessories().keySet(), frame.key());
            for (AccessoryMount mount : frame.accessoryMounts) {
                AccessoryAnchor json = layout.accessory(mount).orElseThrow();
                AccessoryAnchor table = AccessoryAnchor.table(frame, mount).orElseThrow();
                String where = frame.key() + " " + mount.key();
                assertEquals(table.x(), json.x(), 1.0E-3, where);
                assertEquals(table.y(), json.y(), 1.0E-3, where);
                assertEquals(table.z(), json.z(), 1.0E-3, where);
                assertEquals(table.tiltDegrees(), json.tiltDegrees(), 1.0E-3, where);
                assertEquals(table.lengthMm(), json.lengthMm(), 1.0E-3, where);
            }
        }
    }

    @Test
    void accessoryAnchorsParseAndFallBack() {
        DroneLayoutParser.Result result = parse("""
                {"frames": {"freestyle_5": {"motors": [[-79.5, 3, -79.5], [79.5, 3, -79.5], [-79.5, 3, 79.5], [79.5, 3, 79.5]],
                  "accessories": {"top": {"position": [1, 40, -50], "tilt": 15}, "bottom": {"position": [0, -3], "tilt": 30},
                                  "side": {"position": [0, 0, 0], "tilt": 0}}}}}
                """);
        DroneLayout layout = result.layouts().get(FrameType.FREESTYLE_5);
        AccessoryAnchor top = layout.accessory(AccessoryMount.TOP).orElseThrow();
        assertEquals(1.0, top.x(), 1.0E-6);
        assertEquals(15.0, top.tiltDegrees(), 1.0E-6);
        assertEquals(0.0, top.lengthMm(), 1.0E-6);
        assertEquals(AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.BOTTOM), layout.accessory(AccessoryMount.BOTTOM).orElseThrow());
        String problems = result.problems().toString();
        assertTrue(problems.contains("accessories.bottom.position"), problems);
        assertTrue(problems.contains("accessories.side"), problems);
        DroneLayout missing = parse("""
                {"frames": {"race_5": {"motors": [[-70, 3, -80], [70, 3, -80], [-70, 3, 80], [70, 3, 80]]}}}
                """).layouts().get(FrameType.RACE_5);
        assertEquals(DroneLayout.fallback(FrameType.RACE_5).accessories(), missing.accessories());
        assertFalse(parse("""
                {"frames": {"race_5": {"motors": [[-70, 3, -80], [70, 3, -80], [-70, 3, 80], [70, 3, 80]],
                  "accessories": {"top": {"position": [0, 30, -50], "tilt": 120}}}}}
                """).problems().isEmpty());
    }
}
