package de.lardtom.propwash.server.client;

import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.theme.Metrics;

public final class RuleRowLayout {
    public static final int SPINNER_WIDTH = 90;
    public static final int TOGGLE_TEXT_GAP = 5;
    public static final int VALUE_INSET = 3;
    public static final int SPINNER_INSET = 4;

    private RuleRowLayout() {
    }

    public static int column(int widestLabel, int width) {
        return Math.max(Metrics.LABEL_MIN, Math.min(widestLabel + Metrics.PADDING + Metrics.LABEL_GAP, width * 5 / 8));
    }

    public static int labelWidth(int column, int labelWidth, int rowWidth, int controlNeed) {
        if (TextFit.mode(labelWidth, column - Metrics.LABEL_GAP) != TextFit.Mode.CUT) {
            return column;
        }
        return Math.max(column, Math.min(labelWidth + Metrics.LABEL_GAP, rowWidth - controlNeed));
    }

    public static int spinnerWidth(int labelWidth, int rowWidth, int controlNeed) {
        int column = rowWidth - SPINNER_WIDTH - Metrics.GAP;
        return rowWidth - Metrics.GAP - labelWidth(column, labelWidth, rowWidth - Metrics.GAP, controlNeed);
    }

    public static int valueColumn(int column, int widestLabel, int widestValue, int rowWidth) {
        int wanted = Math.max(column, widestLabel + 2 * Metrics.LABEL_GAP);
        return Math.max(Metrics.LABEL_MIN, Math.min(wanted, rowWidth - widestValue - Metrics.LABEL_GAP));
    }

    public static int valueX(int valueColumn, int labelWidth, int valueWidth, int rowWidth) {
        if (TextFit.mode(labelWidth, valueColumn - 2 * Metrics.LABEL_GAP) != TextFit.Mode.CUT) {
            return valueColumn;
        }
        return Math.max(valueColumn, Math.min(labelWidth + 2 * Metrics.LABEL_GAP, rowWidth - valueWidth - Metrics.LABEL_GAP));
    }

    public static int toggleNeed(int widestValue) {
        return Metrics.TOGGLE_WIDTH + TOGGLE_TEXT_GAP + widestValue;
    }

    public static int cycleNeed(int widestValue) {
        return 2 * (Metrics.ARROW_WIDTH + VALUE_INSET) + widestValue;
    }

    public static int sliderNeed(int widestValue) {
        return Math.max(Metrics.CONTROL_MIN, widestValue + 2 * VALUE_INSET);
    }

    public static int spinnerNeed(int widestValue) {
        return Math.max(Metrics.CONTROL_MIN, widestValue + 2 * Metrics.ARROW_WIDTH + SPINNER_INSET);
    }
}
