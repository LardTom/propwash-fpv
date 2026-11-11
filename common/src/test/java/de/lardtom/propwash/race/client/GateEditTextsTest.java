package de.lardtom.propwash.race.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.client.GateEditModel.Row;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateEditTextsTest {
    private static Map<String, String> lang(String code) throws IOException {
        Path root = Files.isDirectory(Path.of("common/src/lang")) ? Path.of("common/src/lang") : Path.of("src/lang");
        Map<String, String> all = new HashMap<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.toList()) {
                Path file = module.resolve(code + ".json");
                if (Files.isRegularFile(file)) {
                    JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                    json.entrySet().forEach(e -> all.put(e.getKey(), e.getValue().getAsString()));
                }
            }
        }
        return all;
    }

    @Test
    void everyRowOfEveryMenuHasATooltipInBothLanguages() throws IOException {
        Map<String, String> en = lang("en_us");
        Map<String, String> de = lang("de_de");
        int checked = 0;
        for (CourseElement kind : CourseElement.values()) {
            for (boolean flat : kind.freeform() ? new boolean[]{false, true} : new boolean[]{false}) {
                for (Row row : GateEditModel.rows(kind, flat)) {
                    String key = GateEditPanel.tooltipKey(kind, row);
                    assertTrue(en.containsKey(key), kind + " " + row + ": " + key + " fehlt (en)");
                    assertTrue(de.containsKey(key), kind + " " + row + ": " + key + " fehlt (de)");
                    String label = GateEditPanel.labelKey(kind, row);
                    assertTrue(en.containsKey(label) && de.containsKey(label), kind + " " + row + ": " + label + " fehlt");
                    checked++;
                }
            }
        }
        assertTrue(checked > 40, "geprüfte Zeilen: " + checked);
    }

    @Test
    void ctrlStepsAreNamedForEveryRowThatHasThem() throws IOException {
        Map<String, String> en = lang("en_us");
        Map<String, String> de = lang("de_de");
        for (Row row : Row.values()) {
            String key = GateEditPanel.fineKey(CourseElement.GATE, row);
            boolean fine = row.angle() || row == Row.PULSE || row.move();
            if (!fine) {
                assertNull(key, row + " hat keinen Feinschritt");
                continue;
            }
            assertNotNull(key, row + " ohne Beschriftung des Feinschritts");
            assertTrue(en.containsKey(key) && de.containsKey(key), key + " fehlt");
        }
        assertNull(GateEditPanel.fineKey(CourseElement.FREEFORM, Row.HEADING), "Vierteldrehung ohne Feinschritt");
        String move = GateEditPanel.fineKey(CourseElement.GATE, Row.MOVE_X);
        assertTrue(en.get(move).contains("1/16") && en.get(move).contains("1/32"), en.get(move));
        assertTrue(de.get(move).contains("1/16") && de.get(move).contains("1/32"), de.get(move));
        assertTrue(en.get("propwash.race.nudge.hint").contains("1/32"), "Fußzeile der Seite Position nennt Strg+Umschalt");
        assertTrue(de.get("propwash.race.nudge.hint").contains("1/32"));
        assertTrue(en.get("propwash.race.nudge.hint").contains("1/16") && de.get("propwash.race.nudge.hint").contains("1/16"));
        assertTrue(en.get("propwash.race.nudge.hint.move").contains("Page Up/Down"), "eigene Zeile fürs Verschieben");
        assertTrue(de.get("propwash.race.nudge.hint.move").contains("Bild↑/↓"));
        assertTrue(en.get("propwash.race.nudge.step.hint").contains("1/32"));
        assertTrue(de.get("propwash.race.nudge.step.hint").contains("1/32"));
        assertTrue(en.get("propwash.race.edit.nudge.step.tooltip").contains("1/32"));
        assertTrue(de.get("propwash.race.edit.nudge.step.tooltip").contains("1/32"));
    }

    @Test
    void valueHintsNameTheCtrlStepOfThePage() throws IOException {
        Map<String, String> en = lang("en_us");
        Map<String, String> de = lang("de_de");
        assertEquals("propwash.race.edit.hint.value.angle", GateEditPanel.valueHintKey(List.of(Row.HEADING, Row.WIDTH)));
        assertEquals("propwash.race.edit.hint.value.pulse", GateEditPanel.valueHintKey(List.of(Row.SIGNAL, Row.PULSE, Row.RACE_ONLY)));
        assertEquals("propwash.race.edit.hint.value.plain", GateEditPanel.valueHintKey(List.of(Row.SCORE_BELOW, Row.SCORE_ABOVE)));
        for (String key : List.of("propwash.race.edit.hint.value.angle", "propwash.race.edit.hint.value.pulse", "propwash.race.edit.hint.value.plain")) {
            assertTrue(en.containsKey(key) && de.containsKey(key), key + " fehlt");
        }
        assertTrue(en.get("propwash.race.edit.hint.value.angle").contains("1°"));
        assertTrue(de.get("propwash.race.edit.hint.value.angle").contains("1°"));
    }

    @Test
    void freeformFooterFollowsTheSelectedRowAndPage() throws IOException {
        Map<String, String> en = lang("en_us");
        Map<String, String> de = lang("de_de");
        List<List<Row>> pages = GateEditModel.pages(CourseElement.FREEFORM, true);
        List<Row> pose = pages.getFirst();
        List<Row> signal = pages.get(1);
        assertEquals("propwash.race.edit.hint.flip", GateEditPanel.freeformHintKey(pose, Row.VARIANT), "Zeile Flugrichtung: Mausrad kehrt um");
        String turn = GateEditPanel.freeformHintKey(pose, Row.HEADING);
        assertEquals("propwash.race.edit.hint.freeform.turn", turn, "Zeile Ausrichtung: Mausrad dreht um 90°");
        String edge = GateEditPanel.freeformHintKey(pose, Row.OPEN_EDGES);
        assertEquals("propwash.race.edit.hint.freeform.toggle", edge, "Zeile Rand: Mausrad schaltet um");
        for (Row row : signal) {
            assertEquals("propwash.race.edit.hint.value.pulse", GateEditPanel.freeformHintKey(signal, row), "Seite Redstone, Zeile " + row);
        }
        for (Row row : GateEditModel.pages(CourseElement.FREEFORM, false).getFirst()) {
            assertEquals(GateEditPanel.freeformHintKey(pose, row), GateEditPanel.freeformHintKey(GateEditModel.pages(CourseElement.FREEFORM, false)
                    .getFirst(), row), "senkrechtes Freiform-Gate, Zeile " + row);
        }
        for (String key : List.of(turn, edge)) {
            assertTrue(en.containsKey(key) && de.containsKey(key), key + " fehlt");
            assertTrue(en.get(key).contains("%s") && de.get(key).contains("%s"), key + ": nennt die Taste fürs Umkehren");
        }
        assertTrue(en.get(turn).contains("90°") && de.get(turn).contains("90°"), "Ausrichtung nennt die Vierteldrehung");
        assertTrue(en.get("propwash.race.edit.hint.value.pulse").contains("1 tick"), en.get("propwash.race.edit.hint.value.pulse"));
        assertTrue(de.get("propwash.race.edit.hint.value.pulse").contains("1 Tick"), de.get("propwash.race.edit.hint.value.pulse"));
    }
}
