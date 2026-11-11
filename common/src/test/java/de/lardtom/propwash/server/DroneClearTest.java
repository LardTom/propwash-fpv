package de.lardtom.propwash.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.lardtom.propwash.testutil.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneClearTest {
    @Test
    void emptyTallyHasNoText() {
        DroneClear.Tally tally = new DroneClear.Tally();
        assertEquals(0, tally.total());
        assertEquals("", tally.text());
        assertEquals(DroneClear.DONE, tally.key());
    }

    @Test
    void countsPerDimensionSorted() {
        DroneClear.Tally tally = new DroneClear.Tally()
                .add("the_nether")
                .add("overworld")
                .add("overworld")
                .add("example:sky");
        assertEquals(4, tally.total());
        assertEquals(2, tally.count("overworld"));
        assertEquals(1, tally.count("the_nether"));
        assertEquals(0, tally.count("the_end"));
        assertEquals("example:sky: 1, overworld: 2, the_nether: 1", tally.text());
        assertEquals(DroneClear.DONE, tally.key());
    }

    @Test
    void singleDroneUsesSingularText() {
        DroneClear.Tally tally = new DroneClear.Tally().add("overworld");
        assertEquals(1, tally.total());
        assertEquals("overworld: 1", tally.text());
        assertEquals(DroneClear.DONE_ONE, tally.key());
    }

    @Test
    void textsExistInBothLanguages() throws IOException {
        for (String language : new String[]{"en_us", "de_de"}) {
            Map<String, String> texts = load(language);
            for (String key : new String[]{DroneClear.DONE, DroneClear.DONE_ONE, DroneClear.NONE, DroneClear.REMOVED}) {
                assertTrue(texts.containsKey(key), language + " ohne " + key);
            }
            assertEquals(2, placeholders(texts.get(DroneClear.DONE)), language + " " + DroneClear.DONE);
            assertEquals(1, placeholders(texts.get(DroneClear.DONE_ONE)), language + " " + DroneClear.DONE_ONE);
            assertEquals(0, placeholders(texts.get(DroneClear.NONE)), language + " " + DroneClear.NONE);
            assertEquals(0, placeholders(texts.get(DroneClear.REMOVED)), language + " " + DroneClear.REMOVED);
        }
        assertEquals("Deine Drohne wurde entfernt.", load("de_de").get(DroneClear.REMOVED));
        assertEquals("Your drone was removed.", load("en_us").get(DroneClear.REMOVED));
    }

    private static Map<String, String> load(String language) throws IOException {
        Path file = TestPaths.lang("clear").resolve(language + ".json");
        try (InputStream in = Files.newInputStream(file)) {
            return new Gson().fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() {
            }.getType());
        }
    }

    private static int placeholders(String text) {
        int count = 0;
        int at = text.indexOf("%s");
        while (at >= 0) {
            count++;
            at = text.indexOf("%s", at + 2);
        }
        return count;
    }
}
