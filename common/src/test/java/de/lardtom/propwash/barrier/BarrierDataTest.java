package de.lardtom.propwash.barrier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BarrierDataTest {
    private static final List<String> IDS = List.of(BarrierKind.DRONE_PASS.path(), BarrierKind.DRONE_BLOCK.path());

    private static Path common() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            if (Files.isDirectory(candidate.resolve("src/main/resources/data/propwash"))) {
                return candidate;
            }
        }
        fail("common-Modul nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static Path assets() {
        return common().resolve("src/main/resources/assets/propwash");
    }

    private static Path data() {
        return common().resolve("src/main/resources/data");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void blockModelsOnlyNameTheMarkerParticle() throws IOException {
        for (String id : IDS) {
            JsonObject state = read(assets().resolve("blockstates/" + id + ".json"));
            assertEquals("propwash:block/" + id, state.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString(), id);
            JsonObject model = read(assets().resolve("models/block/" + id + ".json"));
            assertFalse(model.has("elements") || model.has("parent"), id + ": Blockmodell muss unsichtbar bleiben");
            assertEquals("propwash:item/" + id, model.getAsJsonObject("textures").get("particle").getAsString(), id);
        }
    }

    @Test
    void itemsUseTheFlatIcon() throws IOException {
        for (String id : IDS) {
            JsonObject item = read(assets().resolve("items/" + id + ".json"));
            assertEquals("propwash:item/" + id, item.getAsJsonObject("model").get("model").getAsString(), id);
            JsonObject model = read(assets().resolve("models/item/" + id + ".json"));
            assertEquals("minecraft:item/generated", model.get("parent").getAsString(), id);
            BufferedImage icon = ImageIO.read(assets().resolve("textures/item/" + id + ".png").toFile());
            assertEquals(16, icon.getWidth(), id);
            assertEquals(16, icon.getHeight(), id);
            assertEquals(0, icon.getRGB(0, 0) >>> 24, id + ": Ecke muss durchsichtig sein");
        }
    }

    @Test
    void iconsDifferSoBothBarriersCanBeTold() throws IOException {
        BufferedImage pass = ImageIO.read(assets().resolve("textures/item/" + IDS.get(0) + ".png").toFile());
        BufferedImage block = ImageIO.read(assets().resolve("textures/item/" + IDS.get(1) + ".png").toFile());
        int different = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (pass.getRGB(x, y) != block.getRGB(x, y)) {
                    different++;
                }
            }
        }
        assertTrue(different > 60, "zu ähnliche Symbole: " + different + " Pixel verschieden");
    }

    @Test
    void notCraftableNotMinableNoDrops() throws IOException {
        for (String id : IDS) {
            assertFalse(Files.exists(data().resolve("propwash/recipe/" + id + ".json")), id + " hat ein Rezept");
            assertFalse(Files.exists(data().resolve("propwash/loot_table/blocks/" + id + ".json")), id + " hat eine Beute-Tabelle");
        }
        for (Path folder : List.of(data().resolve("propwash/recipe"), data().resolve("propwash/loot_table"), data().resolve("minecraft/tags"),
                data().resolve("propwash/tags"))) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    for (String id : IDS) {
                        assertFalse(text.contains("propwash:" + id), file + " nennt " + id);
                    }
                }
            }
        }
    }

    @Test
    void namesAndHelpInBothLanguages() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = read(common().resolve("src/lang/barriers/" + language + ".json"));
            for (String id : IDS) {
                for (String key : List.of("block.propwash." + id, "propwash.help." + id + ".summary", "propwash.help." + id + ".fits",
                        "propwash.help." + id + ".use.1", "propwash.help." + id + ".use.2", "propwash.help." + id + ".use.3",
                        "propwash.help." + id + ".use.4")) {
                    assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), language + ": " + key);
                }
            }
        }
        JsonObject en = read(common().resolve("src/lang/barriers/en_us.json"));
        JsonObject de = read(common().resolve("src/lang/barriers/de_de.json"));
        assertEquals("Drone-Passable Barrier", en.get("block.propwash.drone_pass_barrier").getAsString());
        assertEquals("Drone Barrier", en.get("block.propwash.drone_block_barrier").getAsString());
        assertEquals("Drohnen-durchlässige Barriere", de.get("block.propwash.drone_pass_barrier").getAsString());
        assertEquals("Drohnen-Sperre", de.get("block.propwash.drone_block_barrier").getAsString());
    }
}
