package de.lardtom.propwash.zone;

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

class CovZoneTextTest {
    private static JsonObject lang(String language) throws IOException {
        for (Path candidate : List.of(Path.of("common/src/lang/zone"), Path.of("src/lang/zone"))) {
            Path file = candidate.resolve(language + ".json");
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        throw new IOException("Sprachdatei zone/" + language + " nicht gefunden");
    }

    @Test
    void emptyDimensionTextMatchesTheTestPlan() throws IOException {
        assertEquals("In dieser Dimension gibt es noch keine Zonen.", lang("de_de").get("propwash.zone.section.empty").getAsString());
        assertEquals("There are no zones in this dimension yet.", lang("en_us").get("propwash.zone.section.empty").getAsString());
        assertEquals("Neue Zone hier", lang("de_de").get("propwash.zone.section.new").getAsString());
    }
}
