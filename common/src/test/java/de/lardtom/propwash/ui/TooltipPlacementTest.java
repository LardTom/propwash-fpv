package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipPlacementTest {
    @Test
    void wideScreenKeepsTheWantedWidth() {
        assertEquals(170, TooltipPlacement.wrapWidth(TooltipPlacement.WRAP_WIDTH, 427));
        assertEquals(220, TooltipPlacement.wrapWidth(220, 960));
    }

    @Test
    void narrowScreenLimitsTheWidth() {
        assertEquals(310, TooltipPlacement.wrapWidth(564, 320));
        assertEquals(310, TooltipPlacement.wrapWidth(667, 320));
        assertEquals(170, TooltipPlacement.wrapWidth(170, 320));
        assertEquals(TooltipPlacement.MIN_WIDTH, TooltipPlacement.wrapWidth(170, 20));
        assertEquals(TooltipPlacement.MIN_WIDTH, TooltipPlacement.wrapWidth(0, 320));
    }

    @Test
    void tooltipAboveTheScreenMovesDown() {
        int placed = TooltipPlacement.clamp(-37, 139, 240);
        assertEquals(TooltipPlacement.EDGE, placed);
        assertTrue(TooltipPlacement.fits(placed, 139, 240));
        assertFalse(TooltipPlacement.fits(-37, 139, 240));
    }

    @Test
    void tooltipBeyondTheRightEdgeMovesLeft() {
        int placed = TooltipPlacement.clamp(102, 219, 320);
        assertEquals(320 - TooltipPlacement.EDGE - 219, placed);
        assertTrue(TooltipPlacement.fits(placed, 219, 320));
        assertFalse(TooltipPlacement.fits(102, 219, 320));
    }

    @Test
    void tooltipInsideStaysWhereItIs() {
        assertEquals(60, TooltipPlacement.clamp(60, 100, 240));
        assertEquals(TooltipPlacement.EDGE, TooltipPlacement.clamp(TooltipPlacement.EDGE, 100, 240));
        assertEquals(240 - TooltipPlacement.EDGE - 100, TooltipPlacement.clamp(240 - TooltipPlacement.EDGE - 100, 100, 240));
    }

    @Test
    void tooltipLargerThanTheScreenStartsAtTheTop() {
        assertEquals(TooltipPlacement.EDGE, TooltipPlacement.clamp(-80, 300, 240));
        assertEquals(TooltipPlacement.EDGE, TooltipPlacement.clamp(120, 300, 240));
        assertFalse(TooltipPlacement.fits(TooltipPlacement.EDGE, 300, 240));
    }

    @Test
    void everyStartPositionEndsOnScreenWhenTheTooltipFits() {
        for (int size = 9; size <= TooltipPlacement.room(240); size += 7) {
            for (int position = -300; position <= 500; position += 13) {
                assertTrue(TooltipPlacement.fits(TooltipPlacement.clamp(position, size, 240), size, 240), size + " bei " + position);
            }
        }
    }

    @Test
    void tooltipTallerThanTheScreenShowsAWindowOfRows() {
        assertFalse(TooltipPlacement.tooTall(230, 240));
        assertTrue(TooltipPlacement.tooTall(231, 240));
        assertTrue(TooltipPlacement.tooTall(340, 240));
        int[] heights = new int[34];
        Arrays.fill(heights, TooltipPlacement.ROW);
        int room = TooltipPlacement.room(240) - TooltipPlacement.ROW;
        assertEquals(22, TooltipPlacement.windowEnd(heights, 0, room));
        assertEquals(12, TooltipPlacement.lastStart(heights, room));
        assertEquals(34, TooltipPlacement.windowEnd(heights, TooltipPlacement.lastStart(heights, room), room));
        assertEquals(25, TooltipPlacement.windowEnd(heights, 3, room));
    }

    @Test
    void windowCopesWithRowsOfDifferentHeight() {
        int[] heights = {10, 60, 10, 10, 120, 10, 10, 10};
        assertEquals(4, TooltipPlacement.windowEnd(heights, 0, 100));
        assertEquals(5, TooltipPlacement.lastStart(heights, 100));
        assertEquals(8, TooltipPlacement.windowEnd(heights, 5, 100));
        assertEquals(5, TooltipPlacement.windowEnd(heights, 4, 100));
        assertEquals(0, TooltipPlacement.lastStart(new int[]{300}, 100));
        assertEquals(1, TooltipPlacement.windowEnd(new int[]{300}, 0, 100));
        assertEquals(0, TooltipPlacement.lastStart(new int[0], 100));
        assertEquals(0, TooltipPlacement.windowEnd(new int[0], 0, 100));
    }
}
