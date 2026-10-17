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
    void macOptionKeysShowTheSymbolPrintedOnTheKey() throws Exception {
        assertEquals("propwash.input.keyname.left.option", KeyNames.withSymbol("key.keyboard.left.option"));
        assertEquals("propwash.input.keyname.right.option", KeyNames.withSymbol("key.keyboard.right.option"));
        assertNull(KeyNames.withSymbol("key.keyboard.left.alt"));
        assertNull(KeyNames.withSymbol("key.keyboard.left.command"));
        assertNull(KeyNames.withSymbol("key.mouse.option"));
        assertNull(KeyNames.withSymbol(null));
        for (String lang : List.of("en_us", "de_de")) {
            Path dir = Files.isDirectory(Path.of("common/src/lang/input")) ? Path.of("common/src/lang/input") : Path.of("src/lang/input");
            String json = Files.readString(dir.resolve(lang + ".json"));
            for (String key : List.of("left.option", "right.option")) {
                int at = json.indexOf("\"propwash.input.keyname." + key + "\"");
                assertTrue(at >= 0, lang + " " + key);
                assertTrue(json.substring(at, json.indexOf('\n', at)).contains("\u2325"), lang + " " + key + " ohne Wahltasten-Zeichen");
            }
        }
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
