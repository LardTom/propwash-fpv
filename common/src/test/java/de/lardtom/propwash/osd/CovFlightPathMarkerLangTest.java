package de.lardtom.propwash.osd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CovFlightPathMarkerLangTest {
    private static final String NAME = OsdElement.FLIGHT_PATH_MARKER.translationKey();
    private static final String DESCRIPTION = NAME + ".description";

    private static JsonObject lang(String language) throws IOException {
        for (Path candidate : List.of(Path.of("common/src/lang/fpm"), Path.of("src/lang/fpm"))) {
            Path file = candidate.resolve(language + ".json");
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        fail("Sprachquellen des Flugbahn-Markers nicht gefunden");
        return new JsonObject();
    }

    @Test
    void namesAndDescriptionsInEnglishAndGerman() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        assertEquals("Flight path marker", en.get(NAME).getAsString());
        assertEquals("Flugbahn-Marker", de.get(NAME).getAsString());
        assertEquals(en.keySet(), de.keySet(), "gleiche Schlüssel in beiden Sprachen");
        for (JsonObject texts : List.of(en, de)) {
            assertTrue(texts.has(DESCRIPTION), "Beschreibung fehlt");
            String text = texts.get(DESCRIPTION).getAsString();
            assertFalse(text.isBlank());
            assertTrue(text.endsWith("."), "Beschreibung endet mit einem Punkt: " + text);
            assertTrue(text.contains("1.5 m/s") || text.contains("1,5 m/s"), "Beschreibung nennt grob 1,5 m/s: " + text);
            assertFalse(text.contains("%"), "keine Platzhalter: " + text);
        }
        String german = de.get(DESCRIPTION).getAsString();
        assertTrue(german.contains("1,5 m/s"), german);
        assertTrue(en.get(DESCRIPTION).getAsString().contains("1.5 m/s"));
    }
}
