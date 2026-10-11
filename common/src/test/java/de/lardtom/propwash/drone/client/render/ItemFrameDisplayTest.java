package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ItemFrameDisplayTest {
    private static final float MM = DroneVisualScale.MM_TO_UNITS;
    private static final List<Float> WALL_ROTATION = List.of(-90.0F, 180.0F, 0.0F);
    private static final List<Float> SHELF_ROTATION = List.of(30.0F, 180.0F, 0.0F);
    private static final float WALL_SCALE = 1.5F;
    private static final float WALL_LIFT_PX = 0.5F;
    private static final float FRAME_OUTER_PX = 24.0F;
    private static final float ANCHOR = 8.0F;

    private static Path assets() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            Path dir = candidate.resolve("src/main/resources/assets/propwash");
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }
        fail("Assets nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<Float> floats(JsonArray array) {
        List<Float> out = new ArrayList<>();
        for (JsonElement element : array) {
            out.add(element.getAsFloat());
        }
        return out;
    }

    @Test
    void wallScaleFillsTheFootprintCircle() {
        for (float footprint : new float[]{48.0F, 177.0F, 640.0F}) {
            float shown = DroneItemRenderer.wallScale(footprint, DroneItemRenderer.MAX_FIT) * footprint * MM;
            assertEquals(DroneItemRenderer.WALL_DIAMETER * 0.5F, shown, 1.0E-5F, "Fußkreis " + footprint + " mm");
            float small = DroneItemRenderer.wallScale(footprint, DroneItemRenderer.MIN_FIT) * footprint * MM;
            assertEquals(DroneItemRenderer.MIN_FIT * DroneItemRenderer.WALL_DIAMETER * 0.5F, small, 1.0E-5F);
        }
        assertEquals(1.0F, DroneItemRenderer.wallScale(0.0F, 1.0F), 0.0F);
        assertEquals(DroneItemRenderer.MIN_FIT, DroneItemRenderer.fitFactor(48.0F, 177.0F), 0.0F);
        assertEquals(DroneItemRenderer.MAX_FIT, DroneItemRenderer.fitFactor(640.0F, 177.0F), 0.0F);
    }

    @Test
    void wallFootprintOfABoxIsItsHalfDiagonal() {
        float footprint = PartItemRenderer.wallFootprint(new Vector3f(-3.0F, 0.0F, -4.0F), new Vector3f(3.0F, 9.0F, 4.0F));
        assertEquals(5.0F, footprint, 1.0E-5F);
    }

    @Test
    void droneItemHangsOnTheWallInItemFrames() throws IOException {
        JsonObject select = read(assets().resolve("items/drone.json")).getAsJsonObject("model");
        assertEquals("minecraft:select", select.get("type").getAsString());
        assertEquals("minecraft:display_context", select.get("property").getAsString());
        JsonArray cases = select.getAsJsonArray("cases");
        assertEquals(2, cases.size());
        JsonObject fixed = cases.get(0).getAsJsonObject();
        assertEquals("fixed", fixed.get("when").getAsString());
        JsonObject wall = fixed.getAsJsonObject("model").getAsJsonObject("model");
        assertEquals("propwash:drone_build", wall.get("type").getAsString());
        assertTrue(wall.get("wall").getAsBoolean());
        assertFalse(wall.has("ground"));
        JsonObject ground = cases.get(1).getAsJsonObject();
        assertEquals("ground", ground.get("when").getAsString());
        assertTrue(ground.getAsJsonObject("model").getAsJsonObject("model").get("ground").getAsBoolean());
        assertFalse(ground.getAsJsonObject("model").getAsJsonObject("model").has("wall"));
        JsonObject plain = select.getAsJsonObject("fallback").getAsJsonObject("model");
        assertEquals("propwash:drone_build", plain.get("type").getAsString());
        assertFalse(plain.has("wall"));
        assertFalse(plain.has("ground"));
        for (String model : List.of("models/item/drone.json", "models/item/drone_paint.json")) {
            JsonObject display = read(assets().resolve(model)).getAsJsonObject("display");
            JsonObject frame = display.getAsJsonObject("fixed");
            assertEquals(WALL_ROTATION, floats(frame.getAsJsonArray("rotation")), model);
            assertEquals(List.of(0.0F, 0.0F, -WALL_LIFT_PX), floats(frame.getAsJsonArray("translation")), model);
            assertEquals(List.of(WALL_SCALE, WALL_SCALE, WALL_SCALE), floats(frame.getAsJsonArray("scale")), model);
            assertEquals(List.of(30.0F, 180.0F, 0.0F), floats(display.getAsJsonObject("on_shelf").getAsJsonArray("rotation")), model);
            assertEquals(FRAME_OUTER_PX, DroneItemRenderer.WALL_DIAMETER * 16.0F * WALL_SCALE, 1.0E-4F);
        }
    }

    @Test
    void framePartsHangFlatAndFitTheItemFrame() throws IOException {
        assertTrue(checkWallModels("frame") >= 10, "zu wenige Rahmenmodelle");
    }

    @Test
    void propsHangFlatAndFitTheItemFrame() throws IOException {
        assertTrue(checkWallModels("prop") >= 5 * 18, "zu wenige Prop-Modelle");
    }

    @Test
    void flatPartsTiltTowardsTheViewerOnShelves() throws IOException {
        for (String category : List.of("frame", "prop")) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(assets().resolve("models/part/" + category), "*.json")) {
                for (Path file : files) {
                    JsonObject shelf = read(file).getAsJsonObject("display").getAsJsonObject("on_shelf");
                    assertEquals(SHELF_ROTATION, floats(shelf.getAsJsonArray("rotation")), file.getFileName().toString());
                }
            }
        }
        JsonObject motor = read(assets().resolve("models/part/motor/m2207_1750.json")).getAsJsonObject("display");
        assertEquals(List.of(0.0F, 180.0F, 0.0F), floats(motor.getAsJsonObject("on_shelf").getAsJsonArray("rotation")));
        assertEquals(List.of(0.0F, 180.0F, 0.0F), floats(motor.getAsJsonObject("fixed").getAsJsonArray("rotation")));
    }

    private static boolean variant(String name) {
        for (String suffix : List.of("_worn", "_damaged", "_broken")) {
            if (name.contains(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean onlyOnTheDrone(String category, String name) {
        return category.equals("prop") && (name.contains("_ccw") || name.contains("_blur") || name.contains("_paint"));
    }

    private static int checkWallModels(String category) throws IOException {
        int models = 0;
        Map<String, Float> lowest = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(assets().resolve("models/part/" + category), "*.json")) {
            for (Path file : files) {
                models++;
                String name = file.getFileName().toString();
                JsonObject model = read(file);
                JsonObject fixed = model.getAsJsonObject("display").getAsJsonObject("fixed");
                assertEquals(WALL_ROTATION, floats(fixed.getAsJsonArray("rotation")), name);
                if (onlyOnTheDrone(category, name)) {
                    continue;
                }
                List<Float> t = floats(fixed.getAsJsonArray("translation"));
                float s = fixed.getAsJsonArray("scale").get(0).getAsFloat() * preScale(category, name);
                float radius = 0.0F;
                float deepest = Float.NEGATIVE_INFINITY;
                for (Vector3f corner : corners(model)) {
                    corner.sub(ANCHOR, ANCHOR, ANCHOR).mul(s);
                    float wallX = -corner.x + t.get(0);
                    float wallY = -corner.z + t.get(1);
                    float depth = -corner.y + t.get(2);
                    radius = Math.max(radius, (float) Math.hypot(wallX, wallY));
                    deepest = Math.max(deepest, depth);
                }
                assertTrue(radius <= FRAME_OUTER_PX * 0.5F + 0.05F, name + ": Fußkreis " + radius + " px größer als der Item-Rahmen");
                assertTrue(deepest <= -WALL_LIFT_PX + 0.02F, name + ": ragt in den Item-Rahmen (" + deepest + " px)");
                if (!variant(name)) {
                    assertTrue(radius >= FRAME_OUTER_PX * 0.5F - 0.05F, name + ": Fußkreis " + radius + " px füllt den Item-Rahmen nicht");
                }
                lowest.merge(baseKey(name), deepest, Math::max);
            }
        }
        for (Map.Entry<String, Float> entry : lowest.entrySet()) {
            assertEquals(-WALL_LIFT_PX, entry.getValue(), 0.02F, category + "/" + entry.getKey() + ": Unterseite liegt nicht vor dem Item-Rahmen");
        }
        return models;
    }

    private static String baseKey(String modelFile) {
        String key = modelFile.replace(".json", "");
        for (String suffix : List.of("_paint", "_ccw", "_blur", "_worn", "_damaged", "_broken")) {
            key = key.replace(suffix, "");
        }
        return key;
    }

    private static float preScale(String category, String modelFile) throws IOException {
        String key = baseKey(modelFile);
        Path item = assets().resolve("items/" + key + ".json");
        if (!Files.isRegularFile(item)) {
            fail(category + "/" + modelFile + ": items/" + key + ".json fehlt");
        }
        JsonObject model = read(item).getAsJsonObject("model");
        JsonObject scaled = model.has("fallback") ? model.getAsJsonObject("fallback") : model;
        if (!scaled.has("transformation") && model.has("transformation")) {
            scaled = model;
        }
        if (!scaled.has("transformation")) {
            return 1.0F;
        }
        return scaled.getAsJsonObject("transformation").getAsJsonArray("scale").get(0).getAsFloat();
    }

    private static float angleOf(JsonObject rotation, String axis) {
        return rotation.has(axis) ? (float) Math.toRadians(rotation.get(axis).getAsFloat()) : 0.0F;
    }

    private static List<Vector3f> corners(JsonObject model) {
        List<Vector3f> out = new ArrayList<>();
        for (JsonElement element : model.getAsJsonArray("elements")) {
            JsonObject el = element.getAsJsonObject();
            List<Float> from = floats(el.getAsJsonArray("from"));
            List<Float> to = floats(el.getAsJsonArray("to"));
            Matrix3f rotation = null;
            Vector3f origin = new Vector3f();
            if (el.has("rotation")) {
                JsonObject rot = el.getAsJsonObject("rotation");
                List<Float> o = floats(rot.getAsJsonArray("origin"));
                origin.set(o.get(0), o.get(1), o.get(2));
                if (rot.has("angle")) {
                    float angle = (float) Math.toRadians(rot.get("angle").getAsFloat());
                    rotation = switch (rot.get("axis").getAsString()) {
                        case "x" -> new Matrix3f().rotationX(angle);
                        case "y" -> new Matrix3f().rotationY(angle);
                        default -> new Matrix3f().rotationZ(angle);
                    };
                } else {
                    rotation = new Matrix3f().rotationZ(angleOf(rot, "z")).rotateY(angleOf(rot, "y")).rotateX(angleOf(rot, "x"));
                }
            }
            for (int c = 0; c < 8; c++) {
                Vector3f corner = new Vector3f((c & 1) == 0 ? from.get(0) : to.get(0), (c & 2) == 0 ? from.get(1) : to.get(1),
                        (c & 4) == 0 ? from.get(2) : to.get(2));
                if (rotation != null) {
                    corner.sub(origin).mul(rotation).add(origin);
                }
                out.add(corner);
            }
        }
        return out;
    }
}
