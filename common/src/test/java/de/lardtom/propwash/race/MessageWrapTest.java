package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageWrapTest {
    private static double width(String text) {
        return text.length();
    }

    @Test
    void shortTextStaysOneLine() {
        assertEquals(List.of("Runde 1"), MessageWrap.wrap(" Runde 1 ", 40.0, MessageWrapTest::width));
        assertTrue(MessageWrap.wrap("   ", 40.0, MessageWrapTest::width).isEmpty());
    }

    @Test
    void longTextBreaksBetweenWordsAndEveryLineFits() {
        String text = "Diese Runde zählt mit Flughilfe: Welt-Regeln verändern die Physik (Schwerkraft)";
        List<String> lines = MessageWrap.wrap(text, 40.0, MessageWrapTest::width);
        assertEquals(List.of("Diese Runde zählt mit Flughilfe:", "Welt-Regeln verändern die Physik", "(Schwerkraft)"), lines);
        for (String line : lines) {
            assertTrue(line.length() <= 40, line);
        }
        assertEquals(text, String.join(" ", lines));
    }

    @Test
    void wordLongerThanTheLineGetsItsOwnLine() {
        assertEquals(List.of("a", "Kreativ-Flugcontroller", "b"), MessageWrap.wrap("a Kreativ-Flugcontroller b", 10.0, MessageWrapTest::width));
    }

    @Test
    void noWidthMeansNoWrapping() {
        assertEquals(List.of("a b c"), MessageWrap.wrap("a b c", 0.0, MessageWrapTest::width));
        assertEquals(List.of("a b c"), MessageWrap.wrap("a b c", Double.NaN, MessageWrapTest::width));
    }
}
