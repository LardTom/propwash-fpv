package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromeLayoutTest {
    @Test
    void headerGetsASecondRowOnlyWhenTheTextsDoNotFit() {
        assertTrue(ChromeLayout.headerFitsOneRow(720, 80, 150, 60, 50, 168));
        assertEquals(ChromeLayout.HEADER_ROW, ChromeLayout.headerHeight(720, 80, 150, 60, 50, 168));
        assertFalse(ChromeLayout.headerFitsOneRow(427, 80, 150, 60, 50, 106));
        assertEquals(ChromeLayout.HEADER_ROW + ChromeLayout.HEADER_SECOND_ROW, ChromeLayout.headerHeight(427, 80, 150, 60, 50, 106));
        assertTrue(ChromeLayout.headerFitsOneRow(427, 80, 40, 60, 50, 106));
    }

    @Test
    void radioNameGetsItsOwnRowBeforeItIsCut() {
        assertEquals(1, ChromeLayout.headerRows(720, 80, 150, 60, 50, 168));
        assertEquals(2, ChromeLayout.headerRows(427, 80, 150, 60, 50, 106));
        assertEquals(2, ChromeLayout.headerRows(304, 80, 60, 68, 150, 76));
        assertEquals(2, ChromeLayout.headerRows(304, 80, 60, 68, 190, 76));
        assertEquals(3, ChromeLayout.headerRows(304, 80, 60, 68, 200, 76));
        assertEquals(3, ChromeLayout.headerRows(304, 80, 400, 68, 172, 76));
        assertEquals(ChromeLayout.HEADER_ROW, ChromeLayout.headerHeight(1));
        assertEquals(ChromeLayout.HEADER_ROW + ChromeLayout.HEADER_SECOND_ROW, ChromeLayout.headerHeight(2));
        assertEquals(ChromeLayout.HEADER_ROW + 2 * ChromeLayout.HEADER_SECOND_ROW, ChromeLayout.headerHeight(3));
        assertEquals(282, ChromeLayout.radioRowRoom(304));
        assertEquals(ChromeLayout.RADIO_MIN, ChromeLayout.radioRowRoom(40));
    }

    @Test
    void lockBadgeGetsItsOwnRoomBesideTheStateBadge() {
        assertEquals(30, ChromeLayout.badgesWidth(30, 0));
        assertEquals(30 + ChromeLayout.BADGE_GAP + 92, ChromeLayout.badgesWidth(30, 92));
        int brand = 80;
        int search = 106;
        int[][] texts = {{37, 30, 92, 45}, {31, 30, 98, 49}, {37, 30, 92, 190}, {31, 30, 98, 236}, {37, 62, 92, 45}};
        for (int[] text : texts) {
            int name = text[0];
            int badges = ChromeLayout.badgesWidth(text[1], text[2]);
            int radio = text[3];
            for (int width = 304; width <= 960; width++) {
                int rows = ChromeLayout.headerRows(width, brand, name, badges, radio, search);
                int room;
                if (rows == 1) {
                    int shown = Math.min(radio, ChromeLayout.radioRoom(width, brand, name, badges, search, false));
                    room = width - brand - 10 - 6 - badges - 14 - shown - 18 - search - 8;
                } else if (rows == 2) {
                    int beside = ChromeLayout.radioRoom(width, brand, name, badges, search, true);
                    assertTrue(TextFit.mode(radio, beside) != TextFit.Mode.CUT, "Breite " + width + ": Funke gekürzt");
                    room = width - 8 - 6 - badges - 14 - Math.min(radio, beside) - 8;
                } else {
                    room = width - 16 - 6 - badges;
                }
                assertTrue(room >= name, "Breite " + width + " mit " + rows + " Zeilen: Name hat " + room + " von " + name + " Punkten");
            }
        }
    }

    @Test
    void shortRadioNamesDoNotHideThatTheRowIsFull() {
        assertEquals(3, ChromeLayout.headerRows(320, 80, 400, 134, 45, 80));
        assertEquals(16, ChromeLayout.radioSpace(320, 80, 400, 134, 80, true));
        assertEquals(ChromeLayout.RADIO_MIN, ChromeLayout.radioRoom(320, 80, 400, 134, 80, true));
        assertEquals(2, ChromeLayout.headerRows(320, 80, 37, 134, 45, 80));
    }

    @Test
    void veryLongNamesDoNotForceTheSecondRowOnWideWindows() {
        assertTrue(ChromeLayout.headerFitsOneRow(720, 80, 5000, 60, 50, 168));
    }

    @Test
    void narrowWindowsStackTheStatusAboveTheFooterButtons() {
        assertFalse(ChromeLayout.footerStacked(427, 141, 92, 6, 6));
        assertFalse(ChromeLayout.footerStacked(320, 82, 64, 6, 6));
        assertTrue(ChromeLayout.footerStacked(320, 141, 92, 6, 6));
        assertEquals(304, ChromeLayout.stackedStatusLimit(320));
        int height = ChromeLayout.stackedFooterHeight(2, 16);
        assertEquals(ChromeLayout.statusBlockHeight(2) + 16 + 3 * ChromeLayout.FOOTER_MARGIN, height);
        assertTrue(height < ChromeLayout.footerHeight(6));
        int footerTop = 200;
        int buttonTop = ChromeLayout.stackedButtonTop(footerTop, height, 16);
        assertEquals(footerTop + height - ChromeLayout.FOOTER_MARGIN - 16, buttonTop);
        int statusTop = ChromeLayout.stackedStatusTop(footerTop, height, 16, 2);
        assertEquals(footerTop + ChromeLayout.FOOTER_MARGIN, statusTop);
        assertTrue(statusTop + ChromeLayout.statusBlockHeight(2) <= buttonTop - ChromeLayout.FOOTER_MARGIN);
        int grown = ChromeLayout.stackedStatusTop(footerTop, height, 16, 5);
        assertTrue(grown < footerTop);
        assertEquals(buttonTop - ChromeLayout.FOOTER_MARGIN, grown + ChromeLayout.statusBlockHeight(5));
    }

    @Test
    void footerGrowsWithTheStatusLines() {
        assertEquals(24, ChromeLayout.footerHeight(1));
        assertEquals(24, ChromeLayout.footerHeight(2));
        assertEquals(34, ChromeLayout.footerHeight(3));
        assertEquals(44, ChromeLayout.footerHeight(4));
        assertEquals(8, ChromeLayout.statusBlockHeight(1));
        assertEquals(18, ChromeLayout.statusBlockHeight(2));
        assertEquals(0, ChromeLayout.statusBlockHeight(0));
    }

    @Test
    void statusIsCenteredAndGrowsUpwardsWhenItNeedsMoreLines() {
        assertEquals(208, ChromeLayout.statusTop(200, 24, 1));
        assertEquals(203, ChromeLayout.statusTop(200, 24, 2));
        assertEquals(193, ChromeLayout.statusTop(200, 24, 3));
        assertTrue(ChromeLayout.statusTop(200, 24, 3) < 200 + ChromeLayout.FOOTER_MARGIN);
        assertEquals(203, ChromeLayout.statusTop(200, 34, 3));
    }

    @Test
    void statusLimitEndsBeforeTheButtons() {
        assertEquals(255, ChromeLayout.statusLimit(427, 80, 64, 6, 6));
        assertEquals(148, ChromeLayout.statusLimit(320, 80, 64, 6, 6));
        assertEquals(40, ChromeLayout.statusLimit(120, 80, 64, 6, 6));
    }

    @Test
    void sidebarKeepsItsWidthUntilATitleNeedsMore() {
        assertEquals(116, ChromeLayout.sidebarWidth(720, 70));
        assertEquals(85, ChromeLayout.sidebarWidth(427, 60));
        assertEquals(104, ChromeLayout.sidebarWidth(427, 90));
        assertEquals(128, ChromeLayout.sidebarWidth(427, 200));
        assertEquals(78, ChromeLayout.sidebarWidth(320, 40));
        assertEquals(96, ChromeLayout.sidebarWidth(320, 200));
    }

    @Test
    void sidebarRowsShrinkBeforeTheyScroll() {
        assertEquals(16, ChromeLayout.sidebarRow(400, 21, 16, 9));
        assertEquals(10, ChromeLayout.sidebarRow(210, 21, 16, 9));
        assertEquals(9, ChromeLayout.sidebarRow(189, 21, 16, 9));
        assertEquals(ChromeLayout.SIDEBAR_SCROLL_ROW, ChromeLayout.sidebarRow(150, 21, 16, 9));
        assertEquals(13, ChromeLayout.sidebarVisibleRows(150, 11));
        assertEquals(16, ChromeLayout.sidebarRow(100, 0, 16, 9));
    }

    @Test
    void sidebarKeepsTheActiveTabInView() {
        assertEquals(0, ChromeLayout.sidebarFirstRow(0, 3, 21, 13));
        assertEquals(8, ChromeLayout.sidebarFirstRow(0, 20, 21, 13));
        assertEquals(2, ChromeLayout.sidebarFirstRow(8, 2, 21, 13));
        assertEquals(8, ChromeLayout.sidebarFirstRow(30, -1, 21, 13));
        assertEquals(0, ChromeLayout.sidebarFirstRow(-4, -1, 21, 13));
        assertEquals(0, ChromeLayout.sidebarFirstRow(5, 4, 10, 13));
    }

    @Test
    void radioNameGetsTheRoomThatIsLeft() {
        assertEquals(185, ChromeLayout.radioWanted(710, 185));
        assertEquals(284, ChromeLayout.radioWanted(710, 500));
        assertEquals(40, ChromeLayout.radioWanted(60, 500));
        assertEquals(275, ChromeLayout.radioRoom(710, 80, 75, 50, 168, false));
        assertTrue(ChromeLayout.headerFitsOneRow(710, 80, 75, 50, ChromeLayout.radioWanted(710, 185), 168));
        assertFalse(ChromeLayout.headerFitsOneRow(427, 80, 75, 50, ChromeLayout.radioWanted(427, 95), 106));
        assertEquals(260, ChromeLayout.radioRoom(427, 80, 75, 50, 106, true));
        assertEquals(153, ChromeLayout.radioRoom(320, 80, 75, 50, 80, true));
        assertEquals(100, ChromeLayout.radioRoom(320, 80, 400, 50, 80, true));
        assertEquals(ChromeLayout.RADIO_MIN, ChromeLayout.radioRoom(200, 80, 400, 50, 80, false));
    }
}
