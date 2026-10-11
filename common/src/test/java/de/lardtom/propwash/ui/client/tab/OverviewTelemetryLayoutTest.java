package de.lardtom.propwash.ui.client.tab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverviewTelemetryLayoutTest {
    @Test
    void motorBarsStayInsideThePanelInBothLayouts() {
        for (int smallLine = 6; smallLine <= 10; smallLine++) {
            int row = OverviewTab.motorRow(smallLine);
            assertTrue(row > smallLine, "Beschriftungen M1–M4 überlappen bei Zeilenhöhe " + smallLine);
            for (int rows = OverviewTab.TELEMETRY_ROWS; rows <= 2 * OverviewTab.TELEMETRY_ROWS; rows += 10) {
                for (boolean twoColumns : new boolean[]{true, false}) {
                    int height = OverviewTab.telemetryHeight(twoColumns, row, rows);
                    int bottom = OverviewTab.motorBarsBottom(twoColumns, row, rows);
                    String layout = (twoColumns ? "zweispaltig" : "einspaltig") + " mit " + rows + " Punkten Zeilen";
                    assertTrue(bottom <= height - 4, layout + ": Balken enden bei " + bottom + ", Panel bei " + height);
                    assertTrue(16 + rows <= height - 6, layout + ": Zeilen enden bei " + (16 + rows) + ", Panel bei " + height);
                    assertTrue(OverviewTab.motorBarsTop(twoColumns, rows) >= 16, "Balken unter dem Titel");
                }
            }
        }
    }

    @Test
    void rowsTakeTheRoomTheirWidestPairNeeds() {
        int inner = 265 - 12 - OverviewTab.TELEMETRY_GAP;
        assertEquals(inner * 3 / 5, OverviewTab.telemetryRowsWidth(265, 120));
        assertEquals(164, OverviewTab.telemetryRowsWidth(265, 164));
        assertEquals(inner - OverviewTab.MOTOR_MIN_WIDTH, OverviewTab.telemetryRowsWidth(265, inner - OverviewTab.MOTOR_MIN_WIDTH));
        assertEquals(253, OverviewTab.telemetryRowsWidth(265, inner - OverviewTab.MOTOR_MIN_WIDTH + 1));
        assertEquals(247, OverviewTab.telemetryRowsWidth(259, 100));
        for (int width = 120; width <= 600; width++) {
            for (int widest = 60; widest <= 260; widest += 4) {
                int rows = OverviewTab.telemetryRowsWidth(width, widest);
                boolean twoColumns = rows < width - 12;
                if (twoColumns) {
                    assertTrue(rows >= widest, "Breite " + width + ": Zeile mit " + widest + " Punkten passt nicht in " + rows);
                    assertTrue(width - 12 - OverviewTab.TELEMETRY_GAP - rows >= OverviewTab.MOTOR_MIN_WIDTH, "Breite " + width + ": Balken zu schmal");
                } else {
                    assertEquals(width - 12, rows);
                }
            }
        }
    }

    @Test
    void attitudeNeverShowsMinusZero() {
        assertEquals("0", String.format(java.util.Locale.ROOT, "%.0f", OverviewTab.wholeDegrees(-0.4)));
        assertEquals("-1", String.format(java.util.Locale.ROOT, "%.0f", OverviewTab.wholeDegrees(-0.6)));
        assertTrue(Double.isNaN(OverviewTab.wholeDegrees(Double.NaN)));
    }

    @Test
    void singleColumnBarsSitBelowTheFiveRows() {
        assertEquals(16 + 5 * 10 + OverviewTab.MOTOR_GAP, OverviewTab.motorBarsTop(false, OverviewTab.TELEMETRY_ROWS));
        assertEquals(72, OverviewTab.telemetryHeight(true, 8, OverviewTab.TELEMETRY_ROWS));
        assertEquals(104, OverviewTab.telemetryHeight(false, 8, OverviewTab.TELEMETRY_ROWS));
        assertEquals(110, OverviewTab.telemetryHeight(false, 10, OverviewTab.TELEMETRY_ROWS));
        assertEquals(124, OverviewTab.telemetryHeight(false, 8, OverviewTab.TELEMETRY_ROWS + 20));
        assertEquals(92, OverviewTab.telemetryHeight(true, 8, OverviewTab.TELEMETRY_ROWS + 20));
    }
}
