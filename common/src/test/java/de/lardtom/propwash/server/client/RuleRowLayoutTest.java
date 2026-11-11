package de.lardtom.propwash.server.client;

import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.theme.Metrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleRowLayoutTest {
    private static final int TAB_WIDTH = 204;
    private static final int ROW_WIDTH = TAB_WIDTH - Metrics.CHECK_SIZE - Metrics.GAP;
    private static final int FLUID_BUCKETS_DE = 196;
    private static final int GENERATE_CHUNKS_DE = 171;
    private static final int DISPENSER_COOLDOWN_DE = 171;
    private static final int DISTANCE_LIMIT_DE = 126;
    private static final int UNLIMITED_DE = 58;
    private static final int ON_DE = 12;
    private static final int OFF_DE = 18;
    private static final int TICKS_VALUE = 12;
    private static final int SPINNER_VALUE = 24;

    private static boolean cut(int textWidth, int available) {
        return TextFit.mode(textWidth, available) == TextFit.Mode.CUT;
    }

    @Test
    void commonColumnAndValueColumnKeepTheirFormula() {
        assertEquals(127, RuleRowLayout.column(FLUID_BUCKETS_DE, TAB_WIDTH));
        assertEquals(110, RuleRowLayout.column(100, TAB_WIDTH));
        assertEquals(Metrics.LABEL_MIN, RuleRowLayout.column(20, TAB_WIDTH));
        assertEquals(126, RuleRowLayout.valueColumn(127, FLUID_BUCKETS_DE, UNLIMITED_DE, ROW_WIDTH));
        assertEquals(Metrics.LABEL_MIN, RuleRowLayout.valueColumn(127, FLUID_BUCKETS_DE, 200, ROW_WIDTH));
    }

    @Test
    void longAddonNameNextToASwitchIsNotCut() {
        int column = RuleRowLayout.column(FLUID_BUCKETS_DE, TAB_WIDTH);
        assertTrue(cut(FLUID_BUCKETS_DE, column - Metrics.LABEL_GAP));
        int need = RuleRowLayout.toggleNeed(Math.max(ON_DE, OFF_DE));
        int label = RuleRowLayout.labelWidth(column, FLUID_BUCKETS_DE, ROW_WIDTH, need);
        assertTrue(!cut(FLUID_BUCKETS_DE, label - Metrics.LABEL_GAP), "Platz für den Namen " + (label - Metrics.LABEL_GAP));
        assertTrue(ROW_WIDTH - label >= need, "Schalter mit „Aus“ behält " + (ROW_WIDTH - label));
    }

    @Test
    void longNameNextToASpinnerIsNotCut() {
        int need = RuleRowLayout.spinnerNeed(SPINNER_VALUE);
        assertTrue(cut(DISTANCE_LIMIT_DE, ROW_WIDTH - RuleRowLayout.SPINNER_WIDTH - Metrics.GAP - Metrics.LABEL_GAP));
        int spinner = RuleRowLayout.spinnerWidth(DISTANCE_LIMIT_DE, ROW_WIDTH, need);
        int room = ROW_WIDTH - spinner - Metrics.GAP - Metrics.LABEL_GAP;
        assertTrue(!cut(DISTANCE_LIMIT_DE, room), "Platz für den Namen " + room);
        assertTrue(spinner >= need && spinner <= RuleRowLayout.SPINNER_WIDTH, "Feld " + spinner);
    }

    @Test
    void readOnlyRowsMoveTheirValueInsteadOfCuttingTheName() {
        int column = RuleRowLayout.valueColumn(RuleRowLayout.column(FLUID_BUCKETS_DE, TAB_WIDTH), FLUID_BUCKETS_DE, UNLIMITED_DE, ROW_WIDTH);
        int[][] rows = {{GENERATE_CHUNKS_DE, ON_DE}, {FLUID_BUCKETS_DE, OFF_DE}, {DISPENSER_COOLDOWN_DE, TICKS_VALUE}};
        for (int[] row : rows) {
            assertTrue(cut(row[0], column - 2 * Metrics.LABEL_GAP));
            int x = RuleRowLayout.valueX(column, row[0], row[1], ROW_WIDTH);
            assertTrue(!cut(row[0], x - 2 * Metrics.LABEL_GAP), "Name mit " + row[0] + " px hat " + (x - 2 * Metrics.LABEL_GAP));
            assertTrue(x + row[1] + Metrics.LABEL_GAP <= ROW_WIDTH, "Wert mit " + row[1] + " px ab " + x);
        }
        assertNotEquals(column, RuleRowLayout.valueX(column, GENERATE_CHUNKS_DE, ON_DE, ROW_WIDTH));
    }

    @Test
    void rowsThatFitOrAreOnlySqueezedKeepTheCommonColumn() {
        int need = RuleRowLayout.toggleNeed(OFF_DE);
        assertEquals(127, RuleRowLayout.labelWidth(127, 120, ROW_WIDTH, need));
        assertEquals(127, RuleRowLayout.labelWidth(127, 150, ROW_WIDTH, need));
        assertEquals(RuleRowLayout.SPINNER_WIDTH, RuleRowLayout.spinnerWidth(80, ROW_WIDTH, RuleRowLayout.spinnerNeed(SPINNER_VALUE)));
        assertEquals(RuleRowLayout.SPINNER_WIDTH, RuleRowLayout.spinnerWidth(120, ROW_WIDTH, RuleRowLayout.spinnerNeed(SPINNER_VALUE)));
        assertEquals(126, RuleRowLayout.valueX(126, 110, UNLIMITED_DE, ROW_WIDTH));
        assertEquals(126, RuleRowLayout.valueX(126, 160, ON_DE, ROW_WIDTH));
    }

    @Test
    void withoutEnoughRoomTheControlAndTheValueKeepTheirWidth() {
        int toggle = RuleRowLayout.toggleNeed(OFF_DE);
        assertEquals(ROW_WIDTH - toggle, RuleRowLayout.labelWidth(127, 400, ROW_WIDTH, toggle));
        int spinner = RuleRowLayout.spinnerNeed(SPINNER_VALUE);
        assertEquals(spinner, RuleRowLayout.spinnerWidth(400, ROW_WIDTH, spinner));
        assertEquals(ROW_WIDTH - OFF_DE - Metrics.LABEL_GAP, RuleRowLayout.valueX(126, 400, OFF_DE, ROW_WIDTH));
        assertEquals(127, RuleRowLayout.labelWidth(127, 400, 150, toggle));
    }

    @Test
    void controlNeedsCoverTheirValueText() {
        assertEquals(Metrics.TOGGLE_WIDTH + RuleRowLayout.TOGGLE_TEXT_GAP + OFF_DE, RuleRowLayout.toggleNeed(OFF_DE));
        assertEquals(2 * (Metrics.ARROW_WIDTH + RuleRowLayout.VALUE_INSET) + UNLIMITED_DE, RuleRowLayout.cycleNeed(UNLIMITED_DE));
        assertEquals(Metrics.CONTROL_MIN, RuleRowLayout.sliderNeed(TICKS_VALUE));
        assertEquals(UNLIMITED_DE + 2 * RuleRowLayout.VALUE_INSET, RuleRowLayout.sliderNeed(UNLIMITED_DE));
        assertEquals(Math.max(Metrics.CONTROL_MIN, SPINNER_VALUE + 2 * Metrics.ARROW_WIDTH + RuleRowLayout.SPINNER_INSET),
                RuleRowLayout.spinnerNeed(SPINNER_VALUE));
    }
}
