package de.lardtom.propwash.osd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdGridTest {
    private static final float EPS = 1.0E-4F;

    @Test
    void cellsCoverTheWholeArea() {
        OsdGrid grid = OsdGrid.screen(480, 270);
        assertEquals(16.0F, grid.cellWidth(), EPS);
        assertEquals(270.0F / 16.0F, grid.cellHeight(), EPS);
        assertEquals(0.0F, grid.cellLeft(0), EPS);
        assertEquals(480.0F, grid.cellLeft(OsdGrid.COLS), EPS);
        assertEquals(270.0F, grid.cellTop(OsdGrid.ROWS), EPS);
        assertEquals(240.0F, grid.cellLeft(15), EPS);
        assertEquals(grid.centerX(), grid.cellLeft(15), EPS);
    }

    @Test
    void gridToScreenAndBackIsStableForEveryCell() {
        OsdGrid grid = new OsdGrid(12.5F, 40.0F, 333.0F, 187.0F);
        for (int col = 0; col < OsdGrid.COLS; col++) {
            for (int row = 0; row < OsdGrid.ROWS; row++) {
                float x = grid.cellLeft(col) + grid.cellWidth() * 0.5F;
                float y = grid.cellTop(row) + grid.cellHeight() * 0.5F;
                assertEquals(col, grid.colAt(x));
                assertEquals(row, grid.rowAt(y));
                assertEquals(col, grid.nearestCol(grid.cellLeft(col) + grid.cellWidth() * 0.3F));
            }
        }
    }

    @Test
    void positionsOutsideAreClamped() {
        OsdGrid grid = OsdGrid.screen(300, 160);
        assertEquals(0, grid.colAt(-50.0));
        assertEquals(OsdGrid.COLS - 1, grid.colAt(10_000.0));
        assertEquals(0, grid.rowAt(-1.0));
        assertEquals(OsdGrid.ROWS - 1, grid.rowAt(161.0));
        assertEquals(0, OsdGrid.clampCol(-3));
        assertEquals(OsdGrid.ROWS - 1, OsdGrid.clampRow(99));
        assertFalse(grid.contains(300.0, 10.0));
        assertTrue(grid.contains(299.9, 159.9));
    }

    @Test
    void rightAlignedAnchorIsTheRightCellEdge() {
        OsdGrid grid = OsdGrid.screen(300, 160);
        assertEquals(grid.cellLeft(29), grid.anchorX(28, OsdElement.Align.RIGHT), EPS);
        assertEquals(grid.cellLeft(3), grid.anchorX(3, OsdElement.Align.LEFT), EPS);
        assertEquals(grid.cellLeft(15), grid.anchorX(15, OsdElement.Align.CENTER), EPS);
    }

    @Test
    void textScaleFollowsRowHeightAndUserScale() {
        OsdGrid grid = OsdGrid.screen(480, 270);
        float base = grid.textScale(1.0F);
        assertEquals(270.0F / 16.0F * OsdGrid.TEXT_ROW_FILL / OsdGrid.FONT_LINE_HEIGHT, base, EPS);
        assertEquals(base * 1.5F, grid.textScale(1.5F), EPS);
        assertEquals(base * 2.0F, OsdGrid.screen(960, 540).textScale(1.0F), EPS);
    }

    @Test
    void snappingProducesWholePhysicalPixels() {
        assertEquals(1.0F, OsdGrid.snapToPixels(1.1F, 4.0), EPS);
        assertEquals(5.0F, OsdGrid.snapToPixels(1.2F, 4.0) * 4.0F, EPS);
        assertEquals(0.5F, OsdGrid.snapToPixels(0.1F, 2.0), EPS);
        assertEquals(1.3F, OsdGrid.snapToPixels(1.3F, 0.0), EPS);
    }

    @Test
    void rejectsEmptyAreas() {
        assertThrows(IllegalArgumentException.class, () -> new OsdGrid(0.0F, 0.0F, 0.0F, 10.0F));
        assertThrows(IllegalArgumentException.class, () -> new OsdGrid(0.0F, 0.0F, 10.0F, Float.NaN));
    }

    @Test
    void baseLayoutFitsTheGrid() {
        for (OsdElement element : OsdElement.values()) {
            assertTrue(element.baseCol() >= 0 && element.baseCol() < OsdGrid.COLS, element.key());
            assertTrue(element.baseRow() >= 0 && element.baseRow() < OsdGrid.ROWS, element.key());
            assertEquals(element, OsdElement.byKey(element.key()));
        }
        assertTrue(OsdElement.values().length >= 35);
    }

    @Test
    void defaultStatusRowsStayAboveTheVanillaActionBar() {
        for (int guiHeight = 240; guiHeight <= 1440; guiHeight += 8) {
            OsdGrid grid = OsdGrid.screen(guiHeight * 16 / 9, guiHeight);
            float actionBarTop = guiHeight - 72.0F;
            for (OsdElement element : new OsdElement[]{OsdElement.ARMED_STATUS, OsdElement.WARNINGS}) {
                float bottom = grid.cellTop(element.baseRow() + 1);
                assertTrue(bottom <= actionBarTop,
                        element + " reicht bei Höhe " + guiHeight + " bis " + bottom + " in die Actionbar ab " + actionBarTop);
                for (OsdPreset preset : OsdPreset.values()) {
                    float presetBottom = grid.cellTop(preset.row(element) + 1);
                    assertTrue(presetBottom <= actionBarTop,
                            element + " in " + preset + " reicht bei Höhe " + guiHeight + " bis " + presetBottom + " in die Actionbar ab " + actionBarTop);
                }
            }
        }
    }
}
