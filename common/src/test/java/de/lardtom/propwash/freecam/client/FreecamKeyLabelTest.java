package de.lardtom.propwash.freecam.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.testutil.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreecamKeyLabelTest {
    private static final int LIST_HEIGHT = 474;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = LIST_HEIGHT / ROW_HEIGHT;

    @Test
    void keysSharingRStayVisibleTogetherInTheKeyListWithTheFreecamKey() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            Map<String, String> labels = keyLabels(language);
            String freecam = labels.get(KeyDefaults.FREECAM.mappingName());
            String flight = labels.get(KeyDefaults.FLIGHT_MODE.mappingName());
            String rotate = labels.get(KeyDefaults.GATE_ROTATE.mappingName());
            assertNotNull(freecam, language + ": Name der Freecam-Taste fehlt");
            assertNotNull(flight, language + ": Name der Flugmodus-Taste fehlt");
            assertNotNull(rotate, language + ": Name der Taste Gate drehen fehlt");
            String low = flight.compareTo(rotate) <= 0 ? flight : rotate;
            String high = low.equals(flight) ? rotate : flight;
            List<String> span = labels.values().stream().filter(label -> label.compareTo(low) >= 0 && label.compareTo(high) <= 0).sorted().toList();
            assertTrue(span.size() <= VISIBLE_ROWS, language + ": " + span.size() + " Zeilen von „" + low + "“ bis „" + high + "“, sichtbar sind "
                    + VISIBLE_ROWS + " (Liste " + LIST_HEIGHT + " px, Zeile " + ROW_HEIGHT + " px); Freecam-Taste „" + freecam + "“ "
                    + (span.contains(freecam) ? "liegt dazwischen" : "liegt außerhalb"));
        }
    }

    private static Map<String, String> keyLabels(String language) throws IOException {
        Map<String, String> labels = new TreeMap<>();
        Path root = TestPaths.common().resolve("src/lang");
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.sorted().toList()) {
                Path file = module.resolve(language + ".json");
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (entry.getKey().startsWith("key.propwash.")) {
                        labels.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        }
        return labels;
    }
}
