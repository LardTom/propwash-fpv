package de.lardtom.propwash.osd;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdStatsLayoutTest {
    private static final int LINES = 9;
    private static final float ROWS_PER_SCALE = 11.0F * OsdGrid.TEXT_ROW_FILL / OsdGrid.FONT_LINE_HEIGHT;
    private static final float[] SCALES = {OsdConfig.MIN_SCALE, 1.0F, 1.5F, OsdConfig.MAX_SCALE, OsdConfig.MAX_SCALE / OsdConfig.MIN_SCALE};

    @Test
    void blockAlwaysFitsOnScreen() {
        for (int row = -3; row < OsdGrid.ROWS + 3; row++) {
            int top = OsdStatsLayout.blockTop(row, LINES);
            assertTrue(top >= 0);
            assertTrue(top + LINES <= OsdGrid.ROWS);
        }
        assertEquals(3, OsdStatsLayout.blockTop(3, LINES));
    }

    @Test
    void defaultLayoutMovesArmedAndWarningsOutOfTheStats() {
        boolean[] shrunk = new boolean[2];
        int[] rows = new OsdStatsLayout().arrange(3, LINES, new int[]{9, 10}, new float[]{0.76F, 0.76F}, shrunk);
        int armed = rows[0];
        int warnings = rows[1];
        assertTrue(armed >= 12, "armed " + armed);
        assertTrue(warnings >= 12, "warnings " + warnings);
        assertNotEquals(armed, warnings);
    }

    @Test
    void companionsNeverOverlapTheStatsOrEachOther() {
        for (float companionScale : SCALES) {
            for (int statsRow = 0; statsRow < OsdGrid.ROWS; statsRow++) {
                for (int armedRow = 0; armedRow < OsdGrid.ROWS; armedRow++) {
                    for (int warningRow = 0; warningRow < OsdGrid.ROWS; warningRow++) {
                        check(companionScale, statsRow, new int[]{armedRow, warningRow, (armedRow + warningRow) % OsdGrid.ROWS});
                    }
                }
            }
        }
    }

    private static void check(float companionScale, int statsRow, int[] desired) {
        int top = OsdStatsLayout.blockTop(statsRow, LINES);
        float natural = ROWS_PER_SCALE * companionScale;
        float[] heights = new float[desired.length];
        Arrays.fill(heights, natural);
        boolean[] shrunk = new boolean[desired.length];
        int[] rows = new OsdStatsLayout().arrange(top, LINES, desired, heights, shrunk);
        List<float[]> bands = new ArrayList<>();
        bands.add(new float[]{top, top + LINES});
        for (int i = 0; i < desired.length; i++) {
            assertNotEquals(OsdStatsLayout.NONE, rows[i], "Element " + i + " ohne Platz bei Stats-Zeile " + statsRow);
            float height = shrunk[i] ? 1.0F : natural;
            float[] band = {OsdStatsLayout.top(rows[i], height), OsdStatsLayout.bottom(rows[i], height)};
            assertTrue(band[0] >= -1.0E-3F && band[1] <= OsdGrid.ROWS + 1.0E-3F);
            for (float[] other : bands) {
                assertTrue(band[1] <= other[0] + 1.0E-3F || other[1] <= band[0] + 1.0E-3F,
                        "Überlappung bei Stats-Zeile " + statsRow + ", Element " + i + " in Zeile " + rows[i]);
            }
            bands.add(band);
        }
    }

    @Test
    void untouchedWhenNothingCollides() {
        boolean[] shrunk = new boolean[2];
        int[] rows = new OsdStatsLayout().arrange(1, LINES, new int[]{12, 13}, new float[]{0.76F, 0.76F}, shrunk);
        assertEquals(12, rows[0]);
        assertEquals(13, rows[1]);
    }
}
