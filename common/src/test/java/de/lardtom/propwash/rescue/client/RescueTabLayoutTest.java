package de.lardtom.propwash.rescue.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescueTabLayoutTest {
    private static final int SETTINGS = 11;

    private static int contentWidth(int screenWidth) {
        int w = Math.min(screenWidth, 720);
        int sidebar = Math.max(78, Math.min(116, w / 5));
        return Math.max(40, w - 6 - (sidebar + 8));
    }

    private static int contentHeight(int screenHeight) {
        int h = Math.min(screenHeight, 440);
        return Math.max(40, h - 24 - 4 - (24 + 6));
    }

    @Test
    void minimumConfiguratorSizeShowsEverySettingWithoutScrolling() {
        int width = contentWidth(427);
        int height = contentHeight(240);
        assertEquals(328, width);
        assertEquals(182, height);
        RescueTabLayout layout = new RescueTabLayout(width, height, SETTINGS);
        assertEquals(2, layout.columns());
        assertEquals(0, layout.maxScroll());
        assertFalse(layout.scrollable());
        for (int i = 0; i < SETTINGS; i++) {
            assertTrue(layout.visible(i, 0), "Einstellung " + i + " unsichtbar");
            assertInside(layout, i, 0, width, height);
        }
        assertTrue(layout.statusVisible(0));
        assertTrue(layout.statusTop(0) + 20 <= height);
    }

    @Test
    void narrowGuiScrollsRowByRowAndReachesEverySetting() {
        int width = contentWidth(320);
        int height = contentHeight(240);
        RescueTabLayout layout = new RescueTabLayout(width, height, SETTINGS);
        assertEquals(1, layout.columns());
        assertTrue(layout.scrollable());
        assertEquals(2, layout.maxScroll());
        assertFalse(layout.statusVisible(0));
        assertTrue(layout.statusVisible(layout.maxScroll()));
        assertEquals(layout.maxScroll(), layout.clampScroll(99));
        assertEquals(0, layout.clampScroll(-3));
    }

    @Test
    void everySizeKeepsVisibleWidgetsInsideAndEverySettingReachable() {
        for (int count = 1; count <= 14; count++) {
            for (int width = 40; width <= 620; width += 7) {
                for (int height = 40; height <= 400; height += 5) {
                    RescueTabLayout layout = new RescueTabLayout(width, height, count);
                    boolean[] seen = new boolean[count];
                    for (int scroll = 0; scroll <= layout.maxScroll(); scroll++) {
                        for (int i = 0; i < count; i++) {
                            if (!layout.visible(i, scroll)) {
                                continue;
                            }
                            seen[i] = true;
                            assertInside(layout, i, scroll, width, height);
                        }
                        if (layout.statusVisible(scroll)) {
                            assertTrue(layout.statusTop(scroll) >= 0 && layout.statusTop(scroll) + 20 <= height,
                                    "Status außerhalb bei " + width + "x" + height);
                        }
                    }
                    for (int i = 0; i < count; i++) {
                        assertTrue(seen[i], "Einstellung " + i + " von " + count + " nie sichtbar bei " + width + "x" + height);
                    }
                    assertTrue(layout.statusVisible(layout.maxScroll()), "Status nie sichtbar bei " + width + "x" + height);
                }
            }
        }
    }

    @Test
    void scrollbarThumbMapsBothWays() {
        RescueTabLayout layout = new RescueTabLayout(228, 182, SETTINGS);
        int thumb = layout.thumbHeight(182);
        assertTrue(thumb >= 12 && thumb < 182);
        assertEquals(0, layout.thumbTop(182, 0));
        assertEquals(182 - thumb, layout.thumbTop(182, layout.maxScroll()));
        assertEquals(0, layout.scrollAt(0.0, 182));
        assertEquals(layout.maxScroll(), layout.scrollAt(182.0, 182));
        for (int scroll = 0; scroll <= layout.maxScroll(); scroll++) {
            double centre = layout.thumbTop(182, scroll) + thumb / 2.0;
            assertEquals(scroll, layout.scrollAt(centre, 182));
        }
        assertTrue(layout.overScrollbar(226.0, 10.0, 182));
        assertFalse(layout.overScrollbar(200.0, 10.0, 182));
        RescueTabLayout fits = new RescueTabLayout(328, 182, SETTINGS);
        assertFalse(fits.overScrollbar(326.0, 10.0, 182));
        assertEquals(0, fits.scrollAt(150.0, 182));
    }

    @Test
    void wheelMovesOneRowPageKeysAPageAndEdgeKeysToTheEnd() {
        int width = contentWidth(320);
        int height = contentHeight(240);
        RescueTabLayout layout = new RescueTabLayout(width, height, SETTINGS + 6);
        assertTrue(layout.maxScroll() >= 4, "Tab scrollt zu wenig für die Probe: " + layout.maxScroll());
        assertEquals(0, layout.scrollRows(0.0));
        assertEquals(0, layout.scrollRows(Double.NaN));
        assertEquals(1, layout.scrollRows(1.0));
        assertEquals(1, layout.scrollRows(-1.0));
        assertEquals(1, layout.scrollRows(0.1));
        assertEquals(2, layout.scrollRows(-2.0));
        double page = Math.max(1.0, (height - 20) / 18.0);
        int paged = layout.scrollRows(page);
        assertTrue(paged > 1, "Bild ab bewegt nur " + paged + " Zeile");
        assertTrue(paged <= Math.max(1, layout.visibleRows() - 1), "Bild ab überspringt Zeilen: " + paged + " bei " + layout.visibleRows() + " sichtbaren");
        assertEquals(layout.maxScroll(), layout.clampScroll(layout.scrollRows(4096.0)));
        assertEquals(0, layout.clampScroll(layout.maxScroll() - layout.scrollRows(-4096.0)));
        assertEquals(layout.rows(), layout.scrollRows(Double.POSITIVE_INFINITY));
    }

    private static void assertInside(RescueTabLayout layout, int index, int scroll, int width, int height) {
        int right = width - (layout.scrollable() ? RescueTabLayout.SCROLLBAR_WIDTH : 0);
        int left = layout.left(index);
        int top = layout.top(index, scroll);
        String where = "Einstellung " + index + " bei " + width + "x" + height + " Scroll " + scroll;
        assertTrue(left >= 0 && left + layout.columnWidth() <= right, where + " horizontal außerhalb");
        assertTrue(top >= 0 && top + RescueTabLayout.WIDGET_HEIGHT <= height, where + " vertikal außerhalb");
    }
}
