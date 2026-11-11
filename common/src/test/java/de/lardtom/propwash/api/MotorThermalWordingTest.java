package de.lardtom.propwash.api;

import de.lardtom.propwash.api.part.MotorThermal;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MotorThermalWordingTest {
    private static final List<String> DOCS = List.of("docs/api/addon-guide.md", "docs/design/parts-api.md");
    private static final List<String> FIELDS = List.of("derate_start_c", "derateStartC", "MotorThermal.limits(", ".limits(");
    private static final List<String> THROTTLING = List.of("drossel", "regelt der esc", "begrenzt der esc", "throttl");

    private static Path doc(String name) {
        for (Path candidate : List.of(Path.of(".."), Path.of("."))) {
            Path file = candidate.resolve(name);
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        fail(name + " nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static boolean throttling(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return THROTTLING.stream().anyMatch(lower::contains);
    }

    @Test
    void invalidLimitsNameWhatTheyMean() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MotorThermal.limits(150.0, 140.0));
        String message = e.getMessage();
        assertTrue(!throttling(message) && message.contains("Leistungsverlust ab 150.0 °C") && message.contains("starker Verschleiß ab 140.0 °C"),
                "Meldung nennt derate_start_c nicht als Beginn des Leistungsverlusts: " + message);
    }

    @Test
    void docsDoNotCallTheMotorLimitAThrottlePoint() throws IOException {
        List<String> out = new ArrayList<>();
        for (String name : DOCS) {
            List<String> lines = Files.readAllLines(doc(name), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (FIELDS.stream().anyMatch(line::contains) && throttling(line)) {
                    out.add(name + ":" + (i + 1) + " " + line.strip());
                }
            }
        }
        assertEquals(List.of(), out, "derate_start_c heißt seit Phase B „ab hier verliert der Motor Leistung“, abgeregelt wird nur im ESC");
    }
}
