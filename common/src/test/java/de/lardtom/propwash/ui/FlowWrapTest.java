package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowWrapTest {
    private static final ToIntFunction<String> SIX = s -> s.length() * 6;

    @Test
    void firstLinesAreNarrowTheRestUsesTheFullWidth() {
        String text = "eins zwei drei vier fünf sechs sieben acht neun zehn elf zwölf dreizehn vierzehn";
        List<String> lines = FlowWrap.lines(text, SIX, 60, 2, 180);
        assertEquals("eins zwei", lines.get(0));
        assertEquals("drei vier", lines.get(1));
        assertEquals("fünf sechs sieben acht neun", lines.get(2));
        for (int i = 0; i < lines.size(); i++) {
            assertTrue(SIX.applyAsInt(lines.get(i)) <= (i < 2 ? 60 : 180), lines.get(i));
        }
        assertEquals(text, String.join(" ", lines));
    }

    @Test
    void nothingIsDroppedAtAnyWidth() {
        String text = "Gasmitte ist Nullschub, darunter drehen die Motoren rückwärts. So fliegst du kopfüber und startest nach einem Absturz.";
        for (int narrow = 20; narrow <= 200; narrow += 7) {
            for (int first = 0; first <= 5; first++) {
                List<String> lines = FlowWrap.lines(text, SIX, narrow, first, narrow + 110);
                assertEquals(text.replace(" ", ""), String.join("", lines).replace(" ", ""), "Breite " + narrow + ", Zeilen " + first);
                for (int i = 0; i < lines.size(); i++) {
                    assertTrue(SIX.applyAsInt(lines.get(i)) <= (i < first ? narrow : narrow + 110) || lines.get(i).length() == 1, lines.get(i));
                }
            }
        }
    }

    @Test
    void aWordWiderThanTheLineIsBrokenInsteadOfRunningOut() {
        List<String> lines = FlowWrap.lines("Scharfschalten jetzt", SIX, 36, 1, 60);
        assertEquals(List.of("Scharf", "schalten", "jetzt"), lines);
    }

    @Test
    void emptyTextHasNoLinesAndWhitespaceCollapses() {
        assertEquals(List.of(), FlowWrap.lines("   ", SIX, 60, 2, 120));
        assertEquals(List.of("a b"), FlowWrap.lines(" a \n b ", SIX, 60, 2, 120));
    }

    @Test
    void linesBesideCountsEveryLineTouchingTheBlock() {
        assertEquals(2, FlowWrap.linesBeside(18, 2, 10));
        assertEquals(4, FlowWrap.linesBeside(38, 2, 10));
        assertEquals(0, FlowWrap.linesBeside(0, 2, 10));
    }
}
