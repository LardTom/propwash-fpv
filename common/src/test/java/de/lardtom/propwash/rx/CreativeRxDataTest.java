package de.lardtom.propwash.rx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CreativeRxDataTest {
    private static final String ID = "creative_rx_block";

    private static Path common() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            if (Files.isDirectory(candidate.resolve("src/main/resources/assets/propwash"))) {
                return candidate;
            }
        }
        fail("common-Modul nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static Path assets() {
        return common().resolve("src/main/resources/assets/propwash");
    }

    private static JsonObject read(Path file) {
        assertTrue(Files.isRegularFile(file), "fehlt: " + file);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            fail("nicht lesbar: " + file + " (" + e.getMessage() + ")");
            return new JsonObject();
        }
    }

    private static Path file(String ref, String folder, String extension) {
        String[] parts = ref.split(":", 2);
        assertEquals("propwash", parts[0], ref);
        return assets().resolve(folder).resolve(parts[1] + extension);
    }

    private static Map<String, String> textures(String ref) {
        JsonObject model = read(file(ref, "models", ".json"));
        Map<String, String> textures = new HashMap<>();
        if (model.has("parent") && model.get("parent").getAsString().startsWith("propwash:")) {
            textures.putAll(textures(model.get("parent").getAsString()));
        }
        if (model.has("textures")) {
            model.getAsJsonObject("textures").entrySet().forEach(e -> textures.put(e.getKey(), e.getValue().getAsString()));
        }
        return textures;
    }

    private static List<String> models(String block) {
        List<String> out = new ArrayList<>();
        for (JsonElement part : read(assets().resolve("blockstates/" + block + ".json")).getAsJsonArray("multipart")) {
            JsonObject p = part.getAsJsonObject();
            out.add(String.valueOf(p.get("when")) + " -> " + p.getAsJsonObject("apply").get("model").getAsString() + " y "
                    + (p.getAsJsonObject("apply").has("y") ? p.getAsJsonObject("apply").get("y").getAsInt() : 0));
        }
        return out;
    }

    @Test
    void namesHelpAndRangeTextInBothLanguages() throws IOException {
        JsonObject en = CovRxTextTest.lang("rx", "en_us");
        JsonObject de = CovRxTextTest.lang("rx", "de_de");
        assertEquals("Creative RX Block", en.has("block.propwash." + ID) ? en.get("block.propwash." + ID).getAsString() : null);
        assertEquals("Kreativ-RX-Block", de.has("block.propwash." + ID) ? de.get("block.propwash." + ID).getAsString() : null);
        for (JsonObject lang : List.of(en, de)) {
            for (String key : List.of("propwash.help." + ID + ".what.1", "propwash.help." + ID + ".use.1", "propwash.help." + ID + ".fits",
                    "propwash.rx.creative.tooltip", "propwash.rx.range.unlimited", "propwash.guide.scene.rx_block.callout.creative")) {
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), key);
            }
        }
    }

    @Test
    void sameModelsAsTheRxBlockWithOwnHousingTextures() {
        List<String> rx = models("rx_block");
        List<String> creative = models(ID);
        assertEquals(rx.size(), creative.size());
        for (int i = 0; i < rx.size(); i++) {
            String expected = rx.get(i);
            for (String suffix : List.of("", "_linked", "_lost")) {
                expected = expected.replace("propwash:block/rx_block" + suffix + " y", "propwash:block/" + ID + suffix + " y");
            }
            assertEquals(expected, creative.get(i));
        }
        for (String suffix : List.of("", "_linked", "_lost", "_inventory")) {
            Map<String, String> own = textures("propwash:block/" + ID + suffix);
            Map<String, String> base = textures("propwash:block/rx_block" + suffix);
            assertEquals(base.keySet(), own.keySet(), suffix);
            for (String key : List.of("body_side", "body_top", "particle")) {
                assertNotEquals(base.get(key), own.get(key), suffix + " " + key);
            }
            for (String texture : own.values()) {
                assertTrue(Files.isRegularFile(file(texture, "textures", ".png")), "Textur fehlt: " + texture);
            }
        }
        JsonObject item = read(assets().resolve("items/" + ID + ".json"));
        String model = item.getAsJsonObject("model").get("model").getAsString();
        assertEquals(textures("propwash:block/" + ID + "_inventory"), textures(model));
    }

    @Test
    void creativeOnlyWithoutRecipeOrDrops() throws IOException {
        Path data = common().resolve("src/main/resources/data/propwash");
        assertFalse(Files.exists(data.resolve("recipe/" + ID + ".json")), "Rezept");
        assertFalse(Files.exists(data.resolve("loot_table/blocks/" + ID + ".json")), "Beute");
        Path survival = Path.of("tools/data/survival.py");
        if (!Files.isRegularFile(survival)) {
            survival = Path.of("../tools/data/survival.py");
        }
        String generator = Files.readString(survival, StandardCharsets.UTF_8);
        String line = generator.lines().filter(l -> l.startsWith("NOT_CRAFTABLE = ")).findFirst().orElse("");
        assertTrue(line.contains("\"" + ID + "\""), "nicht in NOT_CRAFTABLE: " + line);
    }
}
