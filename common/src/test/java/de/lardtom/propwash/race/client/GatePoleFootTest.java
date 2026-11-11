package de.lardtom.propwash.race.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GatePoleFootTest {
    private static final String PIECES = "src/main/resources/assets/propwash/models/gate/piece/";

    private static JsonObject piece(String name) throws IOException {
        for (Path root : List.of(Path.of("common"), Path.of("."))) {
            Path file = root.resolve(PIECES + name + ".json");
            if (Files.isRegularFile(file)) {
                return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        throw new IllegalStateException(PIECES + name + ".json nicht gefunden");
    }

    @Test
    void poleFootLooksTheSameAtZeroNinetyOneEightyAndTwoSeventyDegrees() throws IOException {
        JsonObject foot = piece("pole_foot");
        assertEquals(16, ModelSymmetry.quads(foot).size(), "vier gleiche Arme mit Oberseite, Spitze und zwei Seiten");
        assertEquals(List.of(), ModelSymmetry.quarterTurnProblems(foot), "Fuß sieht nach einer Vierteldrehung anders aus");
        for (String name : List.of("pole_end", "pole_tee")) {
            JsonObject model = piece(name);
            assertFalse(ModelSymmetry.quads(model, true).isEmpty(), name + " ohne Seiten");
            assertEquals(List.of(), ModelSymmetry.quarterTurnProblems(model, true), name + ": Seiten nach einer Vierteldrehung anders");
        }
    }

    @Test
    void theCheckFindsArmsOfDifferentHeightOrTurnedStripes() {
        String arm = "{\"from\":[6.5,0,4],\"to\":[9.5,%s,8],\"faces\":{\"up\":{\"uv\":[0,4,1.5,6],\"texture\":\"#frame\"%s}}}";
        String cross = "{\"elements\":[" + String.format(arm, "1", "") + ",{\"from\":[8,0,6.5],\"to\":[12,%s,9.5],\"faces\":{\"up\":{\"uv\":[0,4,1.5,6],"
                + "\"texture\":\"#frame\",\"rotation\":%s}}},{\"from\":[6.5,0,8],\"to\":[9.5,1,12],\"faces\":{\"up\":{\"uv\":[0,4,1.5,6],"
                + "\"texture\":\"#frame\",\"rotation\":180}}},{\"from\":[4,0,6.5],\"to\":[8,1,9.5],\"faces\":{\"up\":{\"uv\":[0,4,1.5,6],"
                + "\"texture\":\"#frame\",\"rotation\":270}}}]}";
        JsonObject even = JsonParser.parseString(String.format(cross, "1", "90")).getAsJsonObject();
        assertEquals(List.of(), ModelSymmetry.quarterTurnProblems(even), "vier gleiche Arme");
        JsonObject lower = JsonParser.parseString(String.format(cross, "0.75", "90")).getAsJsonObject();
        assertFalse(ModelSymmetry.quarterTurnProblems(lower).isEmpty(), "ein Arm niedriger");
        JsonObject turned = JsonParser.parseString(String.format(cross, "1", "270")).getAsJsonObject();
        assertFalse(ModelSymmetry.quarterTurnProblems(turned).isEmpty(), "Streifen eines Arms falsch herum");
    }
}
