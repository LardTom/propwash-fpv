package de.lardtom.propwash.race.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateRowTipTest {
    @Test
    void tooltipStartsAtItsRowWhenThereIsRoom() {
        assertEquals(59, GateEditPanel.rowTipTop(60, 40, 238));
    }

    @Test
    void tooltipMovesUpUntilItEndsAboveTheStartNotice() {
        int noticeTop = 147;
        int floor = noticeTop - 4;
        int top = GateEditPanel.rowTipTop(120, 76, floor);
        assertEquals(floor - 76, top);
        assertTrue(top + 76 <= floor, "Tooltip endet über dem Start-Hinweis");
    }

    @Test
    void tooltipNeverStartsAboveTheScreenMargin() {
        assertEquals(2, GateEditPanel.rowTipTop(-10, 40, 238));
        assertEquals(2, GateEditPanel.rowTipTop(0, 141, 143));
    }

    @Test
    void tooltipTallerThanTheRoomAboveTheNoticeReportsNoRoom() {
        assertEquals(GateEditPanel.NO_ROOM, GateEditPanel.rowTipTop(120, 146, 143));
        assertEquals(GateEditPanel.NO_ROOM, GateEditPanel.rowTipTop(120, 150, 133));
    }
}
