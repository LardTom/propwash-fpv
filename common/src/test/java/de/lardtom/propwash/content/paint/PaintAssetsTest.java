package de.lardtom.propwash.content.paint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintChannel;
import de.lardtom.propwash.build.PaintSlot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PaintAssetsTest {
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

    @Test
    void channelReferencesAndSlotsMatchTheGenerator() throws IOException {
        JsonObject paint = read(assets().resolve("drone_paint.json"));
        JsonObject channels = paint.getAsJsonObject("channels");
        assertEquals(PaintChannel.values().length, channels.size());
        for (PaintChannel channel : PaintChannel.values()) {
            assertEquals(channel.reference(), channels.get(channel.key()).getAsInt(), channel.key());
        }
        JsonArray slots = paint.getAsJsonArray("slots");
        assertEquals(PaintSlot.COUNT, slots.size());
        for (PaintSlot slot : PaintSlot.values()) {
            assertEquals(slot.key(), slots.get(slot.ordinal()).getAsString());
        }
    }

    @Test
    void everyPaintableModelHasAVariantWhoseDefaultsMatchTheJavaTint() throws IOException {
        JsonObject models = read(assets().resolve("drone_paint.json")).getAsJsonObject("models");
        assertTrue(models.size() > 50, "zu wenige lackierbare Modelle: " + models.size());
        for (Map.Entry<String, JsonElement> entry : models.entrySet()) {
            String name = entry.getKey();
            assertTrue(Files.exists(assets().resolve("models/part/" + name + "_paint.json")), name + ": Lack-Modell fehlt");
            JsonObject item = read(assets().resolve("items/drone_render/" + name + "_paint.json"));
            JsonArray tints = item.getAsJsonObject("model").getAsJsonArray("tints");
            JsonArray groups = entry.getValue().getAsJsonArray();
            assertEquals(groups.size(), tints.size(), name);
            for (int i = 0; i < groups.size(); i++) {
                JsonObject group = groups.get(i).getAsJsonObject();
                PaintChannel channel = PaintChannel.byKey(group.get("channel").getAsString()).orElseThrow();
                int base = DronePaint.parseHex(group.get("base").getAsString()).orElseThrow();
                JsonObject tint = tints.get(i).getAsJsonObject();
                assertEquals("minecraft:custom_model_data", tint.get("type").getAsString());
                assertEquals(i, tint.get("index").getAsInt());
                assertEquals(channel.tint(base), tint.get("default").getAsInt(), name + " Gruppe " + i);
            }
        }
    }

    @Test
    void droneItemDrawsItsOwnBuild() throws IOException {
        JsonObject select = read(assets().resolve("items/drone.json")).getAsJsonObject("model");
        assertEquals("minecraft:display_context", select.get("property").getAsString());
        JsonObject wall = select.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("model");
        for (JsonObject drone : List.of(select.getAsJsonObject("fallback"), wall)) {
            assertEquals("minecraft:special", drone.get("type").getAsString());
            assertEquals("propwash:item/drone", drone.get("base").getAsString());
            assertEquals("propwash:drone_build", drone.getAsJsonObject("model").get("type").getAsString());
        }
    }

    @Test
    void untouchedPartModelsStayUntinted() throws IOException {
        JsonObject frame = read(assets().resolve("models/part/frame/freestyle_5.json"));
        for (JsonElement element : frame.getAsJsonArray("elements")) {
            for (Map.Entry<String, JsonElement> face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                assertTrue(!face.getValue().getAsJsonObject().has("tintindex"), "Originalmodell darf nicht eingefärbt werden");
            }
        }
    }
}
