package de.lardtom.propwash.trick;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TrickCatalogTest {
    static final int OSD_NAME_LIMIT = 18;

    private static Path repository() {
        for (Path candidate : List.of(Path.of(".."), Path.of("."))) {
            if (Files.isRegularFile(candidate.resolve("common/src/lang/trick/de_de.json"))) {
                return candidate;
            }
        }
        fail("Repository nicht gefunden");
        return Path.of(".");
    }

    private static JsonObject lang(String module, String language) throws IOException {
        try (Reader reader = Files.newBufferedReader(repository().resolve("common/src/lang/" + module + "/" + language + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Function<String, String> text(String language) throws IOException {
        JsonObject json = lang("trick", language);
        return key -> json.has(key) ? json.get(key).getAsString() : "";
    }

    @Test
    void catalogueHasMoreThanFiftyTricksInNineFamilies() {
        assertTrue(TrickType.REPERTOIRE.size() > 50, "nur " + TrickType.REPERTOIRE.size() + " Tricks");
        assertEquals(9, TrickFamily.values().length);
        int total = 0;
        for (TrickFamily family : TrickFamily.values()) {
            assertFalse(family.tricks().isEmpty(), family + " ist leer");
            total += family.tricks().size();
        }
        assertEquals(TrickType.REPERTOIRE.size(), total);
    }

    @Test
    void repertoireIsOrderedByFamily() {
        List<TrickType> sorted = new ArrayList<>();
        for (TrickFamily family : TrickFamily.values()) {
            sorted.addAll(family.tricks());
        }
        assertEquals(sorted, TrickType.REPERTOIRE);
    }

    @Test
    void searchFindsNamesKeysAndSceneAliases() throws IOException {
        Function<String, String> de = text("de_de");
        Function<String, String> en = text("en_us");
        assertEquals(TrickType.REPERTOIRE, TrickCatalog.filter("", null, de));
        assertEquals(TrickType.REPERTOIRE, TrickCatalog.filter("   ", null, en));
        assertTrue(TrickCatalog.filter("barrel", null, de).contains(TrickType.CORKSCREW));
        assertTrue(TrickCatalog.filter("Fassrolle", null, de).contains(TrickType.CORKSCREW));
        assertTrue(TrickCatalog.filter("gigatine", null, en).contains(TrickType.GUILLOTINE));
        assertTrue(TrickCatalog.filter("stall turn", null, en).contains(TrickType.HAMMERHEAD));
        assertTrue(TrickCatalog.filter("reverse split", null, de).contains(TrickType.HALF_MATTY));
        assertTrue(TrickCatalog.filter("rückwärts", null, de).contains(TrickType.BACKWARDS_DIVE));
        assertTrue(TrickCatalog.filter("ruckwarts", null, de).contains(TrickType.BACKWARDS_DIVE));
        assertTrue(TrickCatalog.filter("inverted yaw spin", null, de).contains(TrickType.INVERTED_YAW_SPIN));
        assertTrue(TrickCatalog.filter("splits", null, en).contains(TrickType.SPLIT_S));
        assertEquals(EnumSet.of(TrickType.MATTY_FLIP, TrickType.ANTI_MATTY, TrickType.HALF_MATTY, TrickType.POWER_MATTY),
                EnumSet.copyOf(TrickCatalog.filter("matty", null, en)));
        assertTrue(TrickCatalog.filter("xyzzy", null, de).isEmpty());
    }

    @Test
    void familyFilterAndSearchCombine() throws IOException {
        Function<String, String> de = text("de_de");
        assertEquals(TrickFamily.THREE_D.tricks(), TrickCatalog.filter("", TrickFamily.THREE_D, de));
        assertEquals(TrickFamily.THREE_D.tricks(), TrickCatalog.filter("3d", null, de));
        assertEquals(List.of(TrickType.POWER_LOOP_3D), TrickCatalog.filter("loop", TrickFamily.THREE_D, de));
        List<TrickCatalog.Section> sections = TrickCatalog.sections(TrickCatalog.filter("loop", null, de));
        assertEquals(List.of(TrickFamily.LOOPS, TrickFamily.MATTY_SPLIT, TrickFamily.THREE_D), sections.stream().map(TrickCatalog.Section::family).toList());
        assertTrue(TrickCatalog.sections(List.of()).isEmpty());
    }

    @Test
    void normalizeFoldsCaseUmlautsAndPunctuation() {
        assertEquals("ruckwarts sturzflug", TrickCatalog.normalize("Rückwärts-Sturzflug"));
        assertEquals("rubik s cube", TrickCatalog.normalize("Rubik's Cube"));
        assertEquals("gross", TrickCatalog.normalize("groß"));
        assertEquals("", TrickCatalog.normalize(null));
    }

    @Test
    void osdNamesFitTheTrickLine() throws IOException {
        for (String language : List.of("de_de", "en_us")) {
            JsonObject osd = lang("osd", language);
            for (TrickType type : TrickType.values()) {
                String key = "propwash.osd.trick.type." + type.key();
                assertTrue(osd.has(key), language + ": " + key);
                String name = osd.get(key).getAsString();
                assertTrue(name.length() <= OSD_NAME_LIMIT, language + ": " + name);
                assertEquals(name.toUpperCase(java.util.Locale.ROOT), name, language + ": " + name);
            }
        }
    }

    @Test
    void familiesAndAliasesAreTranslated() throws IOException {
        JsonObject de = lang("trick", "de_de");
        JsonObject en = lang("trick", "en_us");
        for (TrickFamily family : TrickFamily.values()) {
            assertTrue(de.has(family.translationKey()) && en.has(family.translationKey()), family.translationKey());
        }
        for (TrickType type : TrickType.values()) {
            assertEquals(de.has(type.aliasKey()), en.has(type.aliasKey()), type.aliasKey());
        }
    }

    @Test
    void variantsPointToTheirBaseTrick() {
        Set<TrickType> loops = TrickType.withVariants(TrickType.POWER_LOOP);
        assertTrue(loops.containsAll(EnumSet.of(TrickType.POWER_LOOP, TrickType.POWER_FLIP, TrickType.POWER_ROLL, TrickType.INVERTED_360_POWER_LOOP)));
        assertFalse(loops.contains(TrickType.MAVERICK_LOOP));
        assertEquals(EnumSet.of(TrickType.FLIP, TrickType.DOUBLE_FLIP, TrickType.TRIPLE_FLIP, TrickType.FLIP_3D), TrickType.withVariants(TrickType.FLIP));
        for (TrickType type : TrickType.values()) {
            assertEquals(type.variantOf(), type.variantOf().variantOf(), type + " zeigt auf eine Variante");
        }
    }
}
