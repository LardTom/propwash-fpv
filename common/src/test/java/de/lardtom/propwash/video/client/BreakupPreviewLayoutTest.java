package de.lardtom.propwash.video.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakupPreviewLayoutTest {
    @Test
    void tilesMoveToFurtherRowsUntilTheirTextsFit() {
        assertEquals(5, BreakupPreview.columns(310, tile -> tile >= 57));
        assertEquals(3, BreakupPreview.columns(220, tile -> tile >= 57));
        assertEquals(2, BreakupPreview.columns(130, tile -> tile >= 57));
        assertEquals(1, BreakupPreview.columns(100, tile -> tile >= 57));
        assertEquals(1, BreakupPreview.columns(100, tile -> false));
    }

    @Test
    void tilesShareTheWidthAndStayBelowTheirLimit() {
        assertEquals(39, BreakupPreview.tileWidth(220, 5));
        assertEquals(69, BreakupPreview.tileWidth(220, 3));
        assertEquals(150, BreakupPreview.tileWidth(2000, 5));
        assertEquals(1, BreakupPreview.tileWidth(0, 5));
        for (int width = 60; width <= 900; width++) {
            for (int columns : new int[]{5, 3, 2, 1}) {
                int tile = BreakupPreview.tileWidth(width, columns);
                assertTrue(columns * tile + (columns - 1) * 6 <= Math.max(width, columns + (columns - 1) * 6), "Breite " + width + " mit " + columns + " Spalten");
                assertTrue(BreakupPreview.tileHeight(tile) >= 20);
            }
        }
    }

    @Test
    void lostVideoLabelKeepsAMarginInsideItsTile() {
        assertEquals(53, BreakupPreview.noVideoRoom(57));
        assertEquals(4, BreakupPreview.noVideoRoom(3));
    }
}
