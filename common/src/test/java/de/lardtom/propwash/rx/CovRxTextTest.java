package de.lardtom.propwash.rx;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovRxTextTest {
    static JsonObject lang(String module, String language) throws IOException {
        for (Path candidate : List.of(Path.of("common/src/lang/" + module), Path.of("src/lang/" + module))) {
            Path file = candidate.resolve(language + ".json");
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        throw new IOException("Sprachdatei " + module + "/" + language + " nicht gefunden");
    }

    @Test
    void tooltipNamesTheRedstoneRangeInBothLanguages() throws IOException {
        assertEquals("Turns one channel of your radio into a redstone signal (0–15).", lang("rx", "en_us").get(RxText.TOOLTIP).getAsString());
        assertEquals("Macht einen Kanal deiner Funke zum Redstone-Signal (0–15).", lang("rx", "de_de").get(RxText.TOOLTIP).getAsString());
    }

    @Test
    void statusTextsForBoundAndUnboundBlocks() throws IOException {
        JsonObject de = lang("rx", "de_de");
        JsonObject en = lang("rx", "en_us");
        assertEquals("RX-Block: nicht gebunden – Rechtsklick mit deiner Funke", de.get(RxText.STATUS_UNBOUND).getAsString());
        assertEquals("RX block: not bound – right-click it with your radio", en.get(RxText.STATUS_UNBOUND).getAsString());
        assertEquals("%s · %s · %s · %s", de.get(RxText.STATUS).getAsString());
        assertEquals(de.get(RxText.STATUS).getAsString(), en.get(RxText.STATUS).getAsString());
    }

    @Test
    void noRadioTextStartsWithTheMessageFromTheTestPlan() throws IOException {
        assertTrue(lang("rx", "de_de").get(RxText.NO_RADIO).getAsString().startsWith("Keine Funke verbunden"));
        assertTrue(lang("rx", "en_us").get(RxText.NO_RADIO).getAsString().startsWith("No radio connected"));
    }
}
