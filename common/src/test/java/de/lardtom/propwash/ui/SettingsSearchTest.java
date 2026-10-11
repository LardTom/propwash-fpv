package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsSearchTest {
    private static final SettingsSearch.Synonyms SYNONYMS = loadSynonyms();

    private static SettingsSearch.Synonyms loadSynonyms() {
        try (InputStream in = Objects.requireNonNull(SettingsSearchTest.class.getResourceAsStream("/assets/propwash/search/synonyms.txt"))) {
            return SettingsSearch.Synonyms.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SettingsSearch.Entry setting(String id, String tab, String label, List<String> aliases, List<String> details) {
        return new SettingsSearch.Entry(id, SettingsSearch.Kind.SETTING, tab, tab, label, aliases, details, List.of());
    }

    private static final List<SettingsSearch.Entry> ENTRIES = List.of(
            new SettingsSearch.Entry("tab:rates", SettingsSearch.Kind.TAB, "rates", "Rates", "Rates", List.of(), List.of(), List.of()),
            setting("rates#1", "Rates", "Roll center rate", List.of("Roll Mitten-Rate"), List.of("Rotation speed around the stick center")),
            setting("rates#2", "Rates", "Roll expo", List.of("Roll Expo"), List.of("Softens the stick center")),
            setting("rates#3", "Rates", "RC deadband", List.of("RC-Totzone"), List.of("Stick travel around the center that is ignored")),
            setting("rates#4", "Rates", "Yaw deadband", List.of("Gier-Totzone"), List.of()),
            setting("pid#1", "PID tuning", "Gyro lowpass", List.of("Gyro-Tiefpass"), List.of("Filters noise from the gyro")),
            setting("pid#2", "PID tuning", "Throttle expo", List.of("Gas-Expo"), List.of()),
            setting("video#1", "Video", "Field of view", List.of("Sichtfeld"), List.of()),
            setting("audio#1", "Audio", "Motor volume", List.of("Motorlautstärke"), List.of()),
            new SettingsSearch.Entry("world#1", SettingsSearch.Kind.WORLD_RULE, "world_rules", "World rules", "Drones hit entities",
                    List.of("Drohnen treffen Lebewesen"), List.of(), List.of("World rule", "Welt-Regel")),
            new SettingsSearch.Entry("keys#1", SettingsSearch.Kind.KEY, "keys", "Keys", "Rotate gate", List.of("Gate drehen"), List.of(),
                    List.of("Key binding", "Tastenbelegung")));

    private static List<String> ids(String query) {
        return SettingsSearch.search(query, ENTRIES, SYNONYMS, 8).stream().map(h -> h.entry().id()).toList();
    }

    @Test
    void foldingIgnoresCaseUmlautsAndPunctuation() {
        assertEquals("motorlautstarke", SettingsSearch.fold("Motorlautstärke").text());
        assertEquals("motorlautstaerke", SettingsSearch.fold("Motorlautstärke", true).text());
        assertEquals("gross", SettingsSearch.fold("Groß").text());
        assertEquals("rc totzone", SettingsSearch.fold("RC-Totzone").text());
        assertEquals("center s", SettingsSearch.fold("Center °/s").text());
        assertEquals(List.of("roll", "rate"), SettingsSearch.tokens("  Roll   RATE "));
    }

    @Test
    void exactLabelWinsOverPartialMatches() {
        assertEquals("tab:rates", ids("rates").getFirst());
        assertEquals("rates#2", ids("roll expo").getFirst());
    }

    @Test
    void wordPrefixesAreEnough() {
        assertEquals("rates#3", ids("rc dead").getFirst());
        assertTrue(ids("dead").containsAll(List.of("rates#3", "rates#4")));
        assertEquals("video#1", ids("field").getFirst());
    }

    @Test
    void allWordsMustMatch() {
        assertEquals(List.of("rates#4"), ids("yaw deadband"));
        assertTrue(ids("yaw volume").isEmpty());
    }

    @Test
    void otherLanguageLabelsAndUmlautSpellingsAreFound() {
        assertEquals("rates#3", ids("Totzone").getFirst());
        assertEquals("audio#1", ids("lautstärke").getFirst());
        assertEquals("audio#1", ids("lautstaerke").getFirst());
        assertEquals("keys#1", ids("gate drehen").getFirst());
    }

    @Test
    void synonymsBridgeLanguagesAndJargon() {
        assertTrue(ids("dead zone").contains("rates#3"));
        assertTrue(ids("blickwinkel").contains("video#1"));
        assertTrue(ids("fov").contains("video#1"));
        assertTrue(ids("tiefpass").contains("pid#1"));
        assertTrue(ids("gamerule").contains("world#1"));
        assertTrue(ids("tastenbelegung").contains("keys#1"));
        assertTrue(ids("empfindlichkeit").contains("rates#1"));
    }

    @Test
    void labelsRankAboveTooltips() {
        List<String> hits = ids("center");
        assertEquals("rates#1", hits.getFirst());
        assertTrue(hits.contains("rates#2"));
        assertTrue(hits.indexOf("rates#1") < hits.indexOf("rates#2"));
    }

    @Test
    void matchRangePointsIntoTheShownLabel() {
        SettingsSearch.Hit hit = SettingsSearch.search("deadband", ENTRIES, SYNONYMS, 8).getFirst();
        String label = hit.entry().label();
        assertEquals("deadband", label.substring(hit.matchStart(), hit.matchEnd()).toLowerCase());
        SettingsSearch.Hit umlaut = SettingsSearch.search("motorlaut", List.of(setting("a#1", "Audio", "Motorlautstärke", List.of(), List.of())),
                SYNONYMS, 8).getFirst();
        assertEquals("Motorlaut", umlaut.entry().label().substring(umlaut.matchStart(), umlaut.matchEnd()));
    }

    @Test
    void limitAndDuplicatesAreHandled() {
        List<SettingsSearch.Entry> twice = List.of(setting("x#1", "Keys", "Reset", List.of(), List.of()),
                setting("x#2", "Keys", "Reset", List.of(), List.of()), setting("y#1", "Other", "Reset", List.of(), List.of()));
        assertEquals(2, SettingsSearch.search("reset", twice, SYNONYMS, 8).size());
        assertEquals(1, SettingsSearch.search("reset", twice, SYNONYMS, 1).size());
        assertTrue(SettingsSearch.search("", ENTRIES, SYNONYMS, 8).isEmpty());
        assertTrue(SettingsSearch.search("   ", ENTRIES, SYNONYMS, 8).isEmpty());
    }

    @Test
    void typosOfLongerWordsStillFindSomething() {
        assertFalse(ids("thrtl").isEmpty());
        assertEquals("pid#2", ids("thrtl").getFirst());
    }

    @Test
    void shippedSynonymsCoverTheCommunityTerms() {
        assertTrue(SYNONYMS.groups().size() >= 30);
        assertTrue(SYNONYMS.expand("rates").contains("empfindlichkeit"));
        assertTrue(SYNONYMS.expand("totzone").contains("deadband"));
        assertTrue(SYNONYMS.expand("sichtfeld").contains("fov"));
        assertTrue(SYNONYMS.expand("gamerule").contains("welt regel"));
        assertTrue(SYNONYMS.expand("taste").contains("key binding"));
        assertTrue(SYNONYMS.expand("lautstärke").contains("volume"));
        assertTrue(SYNONYMS.expand("ab").isEmpty());
    }
}
