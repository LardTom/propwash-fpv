package de.lardtom.propwash.interact;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneBlockTriggersTest {
    private static final String TAG_FILE = "data/propwash/tags/block/drone_triggers.json";

    @Test
    void tagListsExactlyPressurePlatesAndTripwire() throws IOException {
        JsonObject tag = readTag();
        assertFalse(tag.has("replace") && tag.get("replace").getAsBoolean(), "der eigene Tag ersetzt nichts");
        JsonArray values = tag.getAsJsonArray("values");
        assertNotNull(values);
        List<String> entries = new ArrayList<>();
        for (JsonElement value : values) {
            entries.add(value.getAsString());
        }
        assertEquals(List.of("#minecraft:pressure_plates", "minecraft:tripwire"), entries);
        assertEquals(1, tag.size(), "nur values im Tag");
    }

    @Test
    void tagKeyPointsAtTheFile() {
        assertEquals("propwash:drone_triggers", DroneBlockTriggers.DRONE_TRIGGERS.location().toString());
    }

    @Test
    void firstCheckAlwaysRuns() {
        assertTrue(DroneBlockTriggers.moved(null, Vec3.ZERO));
        assertTrue(DroneBlockTriggers.moved(null, new Vec3(12.5, 64.0, -3.25)));
    }

    @Test
    void restingDroneIsSkipped() {
        Vec3 here = new Vec3(10.5, 64.0, 10.5);
        assertFalse(DroneBlockTriggers.moved(here, here));
        assertFalse(DroneBlockTriggers.moved(here, here.add(0.5E-4, 0.0, 0.0)));
        assertFalse(DroneBlockTriggers.moved(here, here.add(0.0, -0.9E-4, 0.0)));
        assertFalse(DroneBlockTriggers.moved(here, here.add(0.5E-4, 0.5E-4, 0.5E-4)));
    }

    @Test
    void movementAboveTheThresholdCounts() {
        Vec3 here = new Vec3(10.5, 64.0, 10.5);
        assertTrue(DroneBlockTriggers.moved(here, here.add(1.1E-4, 0.0, 0.0)));
        assertTrue(DroneBlockTriggers.moved(here, here.add(0.0, 0.0, -2.0E-4)));
        assertTrue(DroneBlockTriggers.moved(here, here.add(0.75E-4, 0.75E-4, 0.0)));
        assertTrue(DroneBlockTriggers.moved(here, here.add(0.0, -0.25, 0.0)));
    }

    @Test
    void thresholdIsADistanceInBlocks() {
        assertEquals(1.0E-4, DroneBlockTriggers.MOVE_THRESHOLD, 0.0);
        assertEquals(1.0E-3, DroneBlockTriggers.REACH, 0.0);
        Vec3 here = new Vec3(0.0, 0.0, 0.0);
        double justBelow = DroneBlockTriggers.MOVE_THRESHOLD * 0.999;
        double justAbove = DroneBlockTriggers.MOVE_THRESHOLD * 1.001;
        assertFalse(DroneBlockTriggers.moved(here, new Vec3(justBelow, 0.0, 0.0)));
        assertTrue(DroneBlockTriggers.moved(here, new Vec3(justAbove, 0.0, 0.0)));
    }

    private static JsonObject readTag() throws IOException {
        InputStream in = DroneBlockTriggersTest.class.getClassLoader().getResourceAsStream(TAG_FILE);
        assertNotNull(in, TAG_FILE + " fehlt im Klassenpfad");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
