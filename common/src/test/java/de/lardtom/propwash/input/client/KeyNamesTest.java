package de.lardtom.propwash.input.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyNamesTest {
    @Test
    void punctuationKeysAreSpelledOut() {
        assertEquals("propwash.input.keyname.comma", KeyNames.spelledOut(",", "key.keyboard.comma"));
        assertEquals("propwash.input.keyname.period", KeyNames.spelledOut(".", "key.keyboard.period"));
        assertEquals("propwash.input.keyname.left.bracket", KeyNames.spelledOut("[", "key.keyboard.left.bracket"));
    }

    @Test
    void readableKeysStayAsTheyAre() {
        assertNull(KeyNames.spelledOut("R", "key.keyboard.r"));
        assertNull(KeyNames.spelledOut("7", "key.keyboard.7"));
        assertNull(KeyNames.spelledOut("Backspace", "key.keyboard.backspace"));
        assertNull(KeyNames.spelledOut(",", "key.mouse.left"));
    }

    @Test
    void everyPunctuationKeyHasANameInBothLanguages() throws Exception {
        List<String> keys = List.of("comma", "period", "slash", "semicolon", "apostrophe", "left.bracket", "right.bracket", "backslash",
                "minus", "equal", "grave.accent");
        for (String lang : List.of("en_us", "de_de")) {
            Path dir = Files.isDirectory(Path.of("common/src/lang/input")) ? Path.of("common/src/lang/input") : Path.of("src/lang/input");
            String json = Files.readString(dir.resolve(lang + ".json"));
            for (String key : keys) {
                assertTrue(json.contains("\"propwash.input.keyname." + key + "\""), lang + " " + key);
            }
        }
    }
}
