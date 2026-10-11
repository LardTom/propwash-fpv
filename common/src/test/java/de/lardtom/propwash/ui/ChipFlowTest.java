package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChipFlowTest {
    private static final int[] CLASSES = {58, 76, 70, 50, 82};

    @Test
    void chipsThatFitStayOnOneLineWithoutAPager() {
        ChipFlow.Page page = ChipFlow.page(new int[] {58, 76, 70}, 0, 140, 8, 452, 2, 2, 22);
        assertEquals(3, page.slots().size());
        assertEquals(1, page.lines());
        assertFalse(page.paged());
        assertEquals(new ChipFlow.Slot(0, 0, 140, 58), page.slots().getFirst());
        assertEquals(new ChipFlow.Slot(2, 0, 278, 70), page.slots().getLast());
    }

    @Test
    void chipsWrapToASecondLineBeforeAnythingIsHidden() {
        ChipFlow.Page page = ChipFlow.page(CLASSES, 0, 170, 8, 452, 2, 2, 22);
        assertEquals(5, page.slots().size());
        assertEquals(2, page.lines());
        assertFalse(page.paged());
        assertEquals(1, page.slots().getLast().line());
        assertEquals(8, page.slots().getLast().x());
    }

    @Test
    void everyChipIsReachableThroughThePager() {
        int[] widths = {58, 76, 70, 50, 82, 64, 90, 44, 72, 66, 80};
        List<Integer> seen = new ArrayList<>();
        int start = 0;
        for (int guard = 0; guard < widths.length; guard++) {
            ChipFlow.Page page = ChipFlow.page(widths, start, 150, 8, 296, 2, 2, 22);
            assertTrue(page.paged());
            assertFalse(page.slots().isEmpty());
            assertEquals(widths.length - page.slots().size(), page.hidden());
            assertTrue(page.moreX() + 22 <= 296, "Weiter-Knopf ragt hinaus: " + page.moreX());
            for (ChipFlow.Slot slot : page.slots()) {
                assertTrue(slot.x() + slot.width() <= 296, "Chip ragt hinaus: " + slot);
                assertTrue(slot.line() < 2);
                seen.add(slot.index());
            }
            start = page.next();
            if (start == 0) {
                break;
            }
        }
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), seen);
    }

    @Test
    void theSelectedChipIsPagedIntoView() {
        int[] widths = {58, 76, 70, 50, 82, 64, 90, 44, 72, 66, 80};
        ChipFlow.Page first = ChipFlow.page(widths, 0, 150, 8, 296, 2, 2, 22);
        assertFalse(first.shows(10));
        ChipFlow.Page page = ChipFlow.pageShowing(widths, 10, 0, 150, 8, 296, 2, 2, 22);
        assertTrue(page.shows(10));
        assertEquals(page.first(), ChipFlow.page(widths, page.first(), 150, 8, 296, 2, 2, 22).first());
        assertEquals(0, page.next());
    }

    @Test
    void aChipWiderThanTheLineIsNarrowedInsteadOfDropped() {
        ChipFlow.Page page = ChipFlow.page(new int[] {400, 60}, 0, 100, 8, 200, 2, 1, 22);
        assertTrue(page.paged());
        assertEquals(1, page.slots().size());
        assertEquals(new ChipFlow.Slot(0, 0, 100, 76), page.slots().getFirst());
        assertEquals(1, page.next());
        assertTrue(ChipFlow.page(new int[] {400, 60}, 1, 100, 8, 200, 2, 1, 22).shows(1));
    }

    @Test
    void noChipsNeedNoRoom() {
        ChipFlow.Page page = ChipFlow.page(new int[0], 3, 100, 8, 200, 2, 2, 22);
        assertTrue(page.slots().isEmpty());
        assertEquals(1, page.lines());
        assertFalse(page.paged());
    }
}
