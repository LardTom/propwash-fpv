package de.lardtom.propwash.rx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxTooltipTest {
    @Test
    void englishTooltipIsSplitIntoTwoEvenLines() {
        assertEquals(List.of("Turns one channel of your radio", "into a redstone signal (0–15)."),
                RxText.tooltipLines("Turns one channel of your radio into a redstone signal (0–15)."));
    }

    @Test
    void germanTooltipIsSplitIntoTwoEvenLines() {
        assertEquals(List.of("Macht einen Kanal deiner Funke", "zum Redstone-Signal (0–15)."),
                RxText.tooltipLines("Macht einen Kanal deiner Funke zum Redstone-Signal (0–15)."));
    }

    @Test
    void shortTextStaysOnOneLine() {
        assertEquals(List.of("Radio channel to redstone"), RxText.tooltipLines("Radio channel to redstone"));
        assertEquals(List.of("propwash.rx.tooltip"), RxText.tooltipLines("propwash.rx.tooltip"));
        assertEquals(List.of(""), RxText.tooltipLines(""));
    }

    @Test
    void longTextUsesTheFewestLinesThatFit() {
        String text = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen";
        List<String> lines = RxText.tooltipLines(text);
        assertEquals(3, lines.size());
        assertEquals(text, String.join(" ", lines));
        for (String line : lines) {
            assertTrue(line.length() <= RxText.TOOLTIP_WIDTH, line);
        }
    }

    @Test
    void tooltipOfBothLanguagesKeepsEveryWordAndFitsATooltipLine() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            String text = CovRxTextTest.lang("rx", language).get(RxText.TOOLTIP).getAsString();
            List<String> lines = RxText.tooltipLines(text);
            assertEquals(text, String.join(" ", lines), language);
            for (String line : lines) {
                assertTrue(line.length() <= RxText.TOOLTIP_WIDTH, language + " zu lang: " + line);
            }
        }
    }

    @Test
    void firstLineIsPassedThroughUnchanged() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            assertEquals("%s", CovRxTextTest.lang("rx", language).get(RxText.TOOLTIP_LINE).getAsString(), language);
        }
    }
}
