package de.lardtom.propwash.server;

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

class CovClearTextTest {
    private static JsonObject lang(String language) throws IOException {
        for (Path candidate : List.of(Path.of("common/src/lang/clear"), Path.of("src/lang/clear"))) {
            Path file = candidate.resolve(language + ".json");
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        throw new IOException("Sprachdatei clear/" + language + " nicht gefunden");
    }

    @Test
    void messagesMatchTheTestPlanInBothLanguages() throws IOException {
        JsonObject de = lang("de_de");
        JsonObject en = lang("en_us");
        String tally = new DroneClear.Tally().add("overworld").add("overworld").add("overworld").text();
        assertEquals("3 Drohnen entfernt (overworld: 3).", String.format(de.get(DroneClear.DONE).getAsString(), 3, tally));
        assertEquals("Removed 3 drones (overworld: 3).", String.format(en.get(DroneClear.DONE).getAsString(), 3, tally));
        assertEquals("Deine Drohne wurde entfernt.", de.get(DroneClear.REMOVED).getAsString());
        assertEquals("Your drone was removed.", en.get(DroneClear.REMOVED).getAsString());
    }

    @Test
    void netherDroneIsCountedAfterTheOverworld() throws IOException {
        DroneClear.Tally tally = new DroneClear.Tally().add("the_nether").add("overworld").add("overworld");
        assertEquals("overworld: 2, the_nether: 1", tally.text());
        assertEquals(3, tally.total());
        assertEquals("3 Drohnen entfernt (overworld: 2, the_nether: 1).", String.format(lang("de_de").get(tally.key()).getAsString(), tally.total(),
                tally.text()));
    }
}
