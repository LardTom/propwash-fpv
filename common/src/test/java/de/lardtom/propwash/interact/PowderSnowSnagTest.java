package de.lardtom.propwash.interact;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.SnagField;
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

class PowderSnowSnagTest {
    private static final String TAG_FILE = "data/propwash/tags/block/drone_snag_powder_snow.json";
    private static final double TICK = 0.05;
    private static final double VANILLA_VERTICAL = 1.5;

    @Test
    void tagHoldsThePowderSnowBlock() throws IOException {
        InputStream in = PowderSnowSnagTest.class.getClassLoader().getResourceAsStream(TAG_FILE);
        assertNotNull(in, TAG_FILE + " fehlt im Klassenpfad");
        JsonObject tag;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            tag = JsonParser.parseReader(reader).getAsJsonObject();
        }
        assertFalse(tag.has("replace") && tag.get("replace").getAsBoolean(), "der eigene Tag ersetzt nichts");
        JsonArray values = tag.getAsJsonArray("values");
        List<String> entries = new ArrayList<>();
        for (JsonElement value : values) {
            entries.add(value.getAsString());
        }
        assertEquals(List.of("minecraft:powder_snow"), entries);
    }

    @Test
    void powderSnowOnlyBrakesLikeTheVanillaSpeedFactor() {
        SnagMaterial m = SnagMaterial.POWDER_SNOW;
        assertEquals("powder_snow", m.id());
        assertEquals(1.0 / (VANILLA_VERTICAL * TICK), m.linear(), 1.0E-12);
        assertEquals(0.0, m.quadratic());
        assertEquals(0.0, m.hold());
        assertEquals(0.0, m.propLoad());
        assertEquals(0.0, m.propWear());
        assertEquals(SnagMaterial.STRING.propWear(), SnagMaterial.maxPropWear());
    }

    @Test
    void fullyInsideTheWholeDroneFeelsTheFullBrake() {
        double[][] exposure = new double[SnagField.POINTS][SnagMaterial.COUNT];
        for (double[] row : exposure) {
            row[SnagMaterial.POWDER_SNOW.ordinal()] = 1.0;
        }
        SnagField field = new SnagField();
        SnagMath.fill(exposure, field);
        double linear = 0.0;
        for (int i = 0; i < SnagField.POINTS; i++) {
            linear += field.linear[i];
            assertEquals(0.0, field.quadratic[i]);
        }
        assertEquals(SnagMaterial.POWDER_SNOW.linear(), linear, 1.0E-9);
        assertEquals(0.0, field.hold);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            assertEquals(0.0, field.propLoad[i]);
        }
        double[] wear = new double[AirframeSpec.MOTORS];
        SnagMath.addWear(exposure, new double[]{1.0, 1.0, 1.0, 1.0}, 1.0, wear);
        for (double w : wear) {
            assertEquals(0.0, w);
        }
    }
}
