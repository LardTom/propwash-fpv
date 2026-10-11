package de.lardtom.propwash.ui.client.theme;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeTextTooltipTest {
    @Test
    void missingOrEmptyTextShowsNoTooltip() {
        assertFalse(ThemeText.shows(null));
        assertFalse(ThemeText.shows(Component.empty()));
        assertTrue(ThemeText.shows(Component.literal("Boxer")));
    }

    @Test
    void widgetTooltipIsNullInsteadOfCrashingLater() {
        assertNull(ThemeText.tip(null));
        assertNull(ThemeText.tip(Component.empty()));
        assertNotNull(ThemeText.tip(Component.literal("Boxer")));
    }

    @Test
    void cutOnlyWhenTheShownTextIsShorter() {
        Component full = Component.literal("Radiomaster Boxer");
        assertFalse(ThemeText.cut(null, "Radio…"));
        assertFalse(ThemeText.cut(full, "Radiomaster Boxer"));
        assertTrue(ThemeText.cut(full, "Radiomaster…"));
    }

    @Test
    void tooltipLinesDropNullsAndEmptyBlocks() {
        assertEquals(List.of(), ThemeText.visibleLines(null));
        assertEquals(List.of(), ThemeText.visibleLines(List.of()));
        assertEquals(List.of(), ThemeText.visibleLines(Arrays.asList(null, Component.empty())));
        Component name = Component.literal("Boxer");
        List<Component> shown = ThemeText.visibleLines(Arrays.asList(null, name, Component.empty()));
        assertEquals(2, shown.size());
        assertEquals("Boxer", shown.getFirst().getString());
    }
}
