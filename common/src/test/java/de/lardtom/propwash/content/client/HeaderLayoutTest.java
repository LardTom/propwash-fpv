package de.lardtom.propwash.content.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderLayoutTest {
    private static final int GAP = 3;
    private static final int MORE = 15;

    @Test
    void fullLabelsWhenEverythingFits() {
        HeaderLayout.Result result = HeaderLayout.fit(200, GAP, MORE, 3, List.of(new int[]{70, 40}, new int[]{50}));
        assertEquals(2, result.shown());
        assertFalse(result.more());
        assertArrayEquals(new int[]{0, 0}, result.variants());
        assertEquals(70 + GAP + 50 + GAP, result.width());
    }

    @Test
    void widestButtonSwitchesToItsShortLabelFirst() {
        HeaderLayout.Result result = HeaderLayout.fit(100, GAP, MORE, 3, List.of(new int[]{70, 40}, new int[]{50}));
        assertEquals(2, result.shown());
        assertEquals(1, result.variant(0));
        assertEquals(0, result.variant(1));
        assertTrue(result.width() <= 100);
    }

    @Test
    void overflowGoesIntoTheMoreButton() {
        HeaderLayout.Result result = HeaderLayout.fit(61, GAP, MORE, 3, List.of(new int[]{70, 40}, new int[]{50}));
        assertEquals(1, result.shown());
        assertTrue(result.more());
        assertEquals(40 + GAP + MORE + GAP, result.width());
    }

    @Test
    void maxShownLimitsTheHeaderEvenWithSpace() {
        HeaderLayout.Result result = HeaderLayout.fit(500, GAP, MORE, 1, List.of(new int[]{13}, new int[]{13}));
        assertEquals(1, result.shown());
        assertTrue(result.more());
    }

    @Test
    void noSpaceLeavesOnlyTheMoreButton() {
        HeaderLayout.Result result = HeaderLayout.fit(10, GAP, MORE, 3, List.of(new int[]{70, 40}));
        assertEquals(0, result.shown());
        assertTrue(result.more());
        assertEquals(MORE + GAP, result.width());
    }
}
