package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollMathTest {
    @Test
    void contentThatFitsDoesNotScroll() {
        assertEquals(0, ScrollMath.maxOffset(180, 182));
        assertFalse(ScrollMath.scrollable(182, 182));
        assertEquals(0.0, ScrollMath.clamp(40.0, 100, 182), 1e-9);
        assertEquals(182, ScrollMath.thumbHeight(100, 182));
        assertEquals(0, ScrollMath.thumbTop(0.0, 100, 182));
        assertEquals(0.0, ScrollMath.wheel(0.0, -3.0, 100, 182), 1e-9);
    }

    @Test
    void offsetStaysBetweenStartAndEnd() {
        assertEquals(118, ScrollMath.maxOffset(300, 182));
        assertTrue(ScrollMath.scrollable(300, 182));
        assertEquals(0.0, ScrollMath.clamp(-5.0, 300, 182), 1e-9);
        assertEquals(118.0, ScrollMath.clamp(500.0, 300, 182), 1e-9);
        assertEquals(0.0, ScrollMath.clamp(Double.NaN, 300, 182), 1e-9);
        assertEquals(0, ScrollMath.maxOffset(300, -4));
    }

    @Test
    void wheelMovesOneLinePerNotchAndStopsAtTheEnds() {
        assertEquals(18.0, ScrollMath.wheel(0.0, -1.0, 300, 182), 1e-9);
        assertEquals(0.0, ScrollMath.wheel(10.0, 1.0, 300, 182), 1e-9);
        assertEquals(118.0, ScrollMath.wheel(110.0, -1.0, 300, 182), 1e-9);
    }

    @Test
    void pageKeepsSomeOverlapAndEndsAtTheEdge() {
        assertEquals(162.0, ScrollMath.page(0.0, 1, 600, 182), 1e-9);
        assertEquals(418.0, ScrollMath.page(324.0, 1, 600, 182), 1e-9);
        assertEquals(0.0, ScrollMath.page(100.0, -1, 600, 182), 1e-9);
        assertEquals(18.0, ScrollMath.page(0.0, 1, 600, 20), 1e-9);
    }

    @Test
    void thumbShrinksWithContentButStaysGrabbable() {
        assertEquals(91, ScrollMath.thumbHeight(364, 182));
        assertEquals(ScrollMath.MIN_THUMB, ScrollMath.thumbHeight(100000, 182));
        assertEquals(0, ScrollMath.thumbHeight(100, 0));
        assertEquals(0, ScrollMath.thumbTop(0.0, 364, 182));
        assertEquals(91, ScrollMath.thumbTop(182.0, 364, 182));
        assertEquals(46, ScrollMath.thumbTop(91.0, 364, 182));
    }

    @Test
    void draggingTheThumbCoversTheWholeRange() {
        assertEquals(0.0, ScrollMath.dragOffset(0.0, 364, 182), 1e-9);
        assertEquals(182.0, ScrollMath.dragOffset(182.0, 364, 182), 1e-9);
        assertEquals(91.0, ScrollMath.dragOffset(91.0, 364, 182), 1e-9);
        assertEquals(0.0, ScrollMath.dragOffset(-40.0, 364, 182), 1e-9);
    }

    @Test
    void revealScrollsOnlyAsFarAsNeeded() {
        assertEquals(50.0, ScrollMath.reveal(50.0, 100, 14, 600, 182, 6), 1e-9);
        assertEquals(94.0, ScrollMath.reveal(200.0, 100, 14, 600, 182, 6), 1e-9);
        assertEquals(138.0, ScrollMath.reveal(0.0, 300, 14, 600, 182, 6), 1e-9);
        assertEquals(418.0, ScrollMath.reveal(0.0, 590, 14, 600, 182, 6), 1e-9);
        assertEquals(0.0, ScrollMath.reveal(30.0, 2, 14, 600, 182, 6), 1e-9);
    }

    @Test
    void revealOfAnItemTallerThanTheViewShowsItsTop() {
        assertEquals(300.0, ScrollMath.reveal(0.0, 300, 400, 900, 182, 6), 1e-9);
    }

    @Test
    void rowListsPageAndDragInWholeRows() {
        assertEquals(11, ScrollMath.rowMax(14, 3));
        assertEquals(0, ScrollMath.rowMax(3, 14));
        assertEquals(2, ScrollMath.rowPage(0, 1, 14, 3));
        assertEquals(11, ScrollMath.rowPage(10, 1, 14, 3));
        assertEquals(0, ScrollMath.rowPage(1, -1, 14, 3));
        assertEquals(1, ScrollMath.rowPage(0, 1, 14, 1));
        assertEquals(0, ScrollMath.rowDrag(0.0, 14, 3, 16));
        assertEquals(11, ScrollMath.rowDrag(48.0, 14, 3, 16));
        assertEquals(9, ScrollMath.rowDrag(240.0, 22, 13, 16));
        int middle = ScrollMath.rowDrag(24.0, 14, 3, 16);
        assertTrue(middle > 3 && middle < 8, "Mitte der Bahn: " + middle);
        assertEquals(0, ScrollMath.rowDrag(20.0, 3, 3, 16));
    }

    @Test
    void rowListsTurnScrollAmountsIntoRows() {
        assertEquals(0, ScrollMath.rowSteps(0.0, 12, 4, 16));
        assertEquals(0, ScrollMath.rowSteps(Double.NaN, 12, 4, 16));
        assertEquals(1, ScrollMath.rowSteps(1.0, 12, 4, 16));
        assertEquals(1, ScrollMath.rowSteps(-0.25, 12, 4, 16));
        assertEquals(2, ScrollMath.rowSteps(2.0, 12, 4, 16));
        assertEquals(1, ScrollMath.rowSteps(2.0, 12, 2, 54));
        assertEquals(3, ScrollMath.rowSteps(7.3, 12, 4, 16));
        assertEquals(1, ScrollMath.rowSteps(7.3, 12, 2, 54));
        assertEquals(12, ScrollMath.rowSteps(4096.0, 12, 4, 16));
        assertEquals(12, ScrollMath.rowSteps(-4096.0, 12, 2, 54));
        assertEquals(12, ScrollMath.rowSteps(Double.POSITIVE_INFINITY, 12, 4, 16));
        assertEquals(1, ScrollMath.rowSteps(3.0, 0, 0, 0));
    }
}
