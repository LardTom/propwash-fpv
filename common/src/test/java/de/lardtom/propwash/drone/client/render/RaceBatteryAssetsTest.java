package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceBatteryAssetsTest {
    private static final Path ASSETS = LayoutFromDefinitionTest.resources().resolve("assets/propwash");

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static JsonObject model(String key) throws IOException {
        return json(ASSETS.resolve("models/part/battery/" + key + ".json"));
    }

    private static float[] yRange(JsonObject model, String name) {
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            if (name != null && !name.equals(el.has("name") ? el.get("name").getAsString() : "")) {
                continue;
            }
            for (String corner : new String[]{"from", "to"}) {
                JsonArray v = el.getAsJsonArray(corner);
                float mm = (v.get(1).getAsFloat() - 8.0F) * 8.0F;
                lo = Math.min(lo, mm);
                hi = Math.max(hi, mm);
            }
        }
        return new float[]{lo, hi};
    }

    private static boolean strapped(String key) throws IOException {
        return Float.isFinite(yRange(model(key), "strap")[0]);
    }

    @Test
    void framesWithTheBatteryUnderHangItFromThePlateOfTheFlightModel() throws IOException {
        DroneLayoutParser.Result result = LayoutFromDefinitionTest.shippedLayouts();
        for (FrameType frame : FrameType.values()) {
            if (!frame.builtin()) {
                continue;
            }
            DroneLayout layout = result.layouts().get(frame);
            boolean under = !frame.definition().geometry().batteryOnTop();
            assertEquals(under, layout.batteryBelow(), frame.key());
            if (under) {
                assertEquals(DroneLayout.batteryPlateHeight(frame), layout.battery().y(), 1.0F, frame.key());
            }
        }
        assertTrue(result.layouts().get(FrameType.RACE_5).batteryBelow());
    }

    @Test
    void strappedBatteriesShipAHangingVariantWithTheStrapAroundTheBottom() throws IOException {
        int strapped = 0;
        JsonObject paint = json(ASSETS.resolve("drone_paint.json")).getAsJsonObject("models");
        for (BatteryType battery : BatteryType.values()) {
            if (!battery.builtin()) {
                continue;
            }
            String key = battery.key();
            String under = key + PartModel.UNDER_SUFFIX;
            if (!strapped(key)) {
                assertFalse(Files.exists(ASSETS.resolve("models/part/battery/" + under + ".json")), under);
                continue;
            }
            strapped++;
            PartModel model = PartModel.batteryUnder(battery);
            assertEquals(under, model.key());
            Path item = ASSETS.resolve(model.itemModelFile().getPath());
            assertTrue(Files.readString(item).contains("\"propwash:part/battery/" + under + "\""), item.toString());
            JsonObject hanging = model(under);
            float[] pack = yRange(hanging, "pack");
            float[] basePack = yRange(model(key), "pack");
            assertEquals(0.0F, pack[1], 0.01F, under + " hängt mit der Oberseite am Anbaupunkt");
            assertEquals(basePack[1] - basePack[0], pack[1] - pack[0], 0.01F, under + " gleiche Packhöhe");
            float[] strap = yRange(hanging, "strap");
            assertTrue(strap[1] > 0.0F && strap[1] <= 4.0F, under + ": Riemen durch die Platte bis " + strap[1]);
            assertTrue(strap[0] < pack[0], under + ": Riemen läuft unter dem Pack durch");
            float[] buckle = yRange(hanging, "buckle");
            assertTrue(buckle[1] <= pack[0], under + ": Schnalle unten");
            assertTrue(paint.has("battery/" + under), under + " fehlt in drone_paint.json");
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/part/battery/" + under + PartModel.PAINT_SUFFIX + ".json")));
            assertFalse(Files.exists(ASSETS.resolve("items/" + under + ".json")), "kein Inventar-Item für " + under);
        }
        assertEquals(2, strapped);
    }

    @Test
    void packsWithoutAModelVariantHangByTheirTop() {
        Vector3f hung = DroneAssembly.hangBelow(new Vector3f(0.0F, -2.8F, 4.0F), 27.0F);
        assertEquals(-29.8F, hung.y(), 1e-5F);
        assertEquals(4.0F, hung.z(), 0.0F);
        assertEquals(-3.8F, DroneAssembly.hangBelow(new Vector3f(0.0F, -2.8F, 0.0F), 0.0F).y(), 1e-5F);
    }

    @Test
    void definitionLayoutHangsTheBatteryFromThePlate() {
        DroneLayout race = DroneLayout.fromDefinition(FrameType.RACE_5);
        assertTrue(race.batteryBelow());
        assertEquals(-2.8F, race.battery().y(), 1e-6F);
        assertFalse(DroneLayout.fromDefinition(FrameType.FREESTYLE_5).batteryBelow());
        assertTrue(FallbackShapes.topPlateHeight(FrameType.RACE_5, race) > 8.0F, "Race-Rahmen behält seine Deckplatte");
        assertFalse(FallbackShapes.topPlateHeight(FrameType.WHOOP_65, DroneLayout.fromDefinition(FrameType.WHOOP_65)) > 8.0F);
    }
}
