package de.lardtom.propwash.ui.client.theme;

public final class Metrics {
    public static final int BORDER = 1;
    public static final int PADDING = 6;
    public static final int GAP = 6;
    public static final int TEXT_HEIGHT = 8;
    public static final int LINE_HEIGHT = 10;
    public static final int SMALL_LINE_HEIGHT = 7;

    public static final int CONTROL_HEIGHT = 14;
    public static final int BUTTON_HEIGHT = 20;
    public static final int ROW_HEIGHT = 16;
    public static final int TABLE_ROW = 16;
    public static final int TABLE_HEADER = 11;
    public static final int SECTION_HEIGHT = 14;
    public static final int SECTION_GAP = 6;
    public static final int PANEL_TITLE_HEIGHT = 12;

    public static final int HEADER_HEIGHT = 24;
    public static final int SUBHEADER_HEIGHT = 14;
    public static final int FOOTER_HEIGHT = 24;
    public static final int SIDEBAR_ROW = 16;
    public static final int SCROLLBAR_WIDTH = 3;
    public static final int CONTENT_INSET = 6;
    public static final int SCROLL_STEP = 18;

    public static final int LABEL_MIN = 64;
    public static final int LABEL_MAX = 140;
    public static final int LABEL_GAP = 4;
    public static final int CONTROL_MIN = 40;
    public static final int COLUMN_MIN = 190;
    public static final int COLUMN_MAX = 280;

    public static final int ARROW_WIDTH = 9;
    public static final int TOGGLE_WIDTH = 20;
    public static final int TOGGLE_HEIGHT = 10;
    public static final int CHECK_SIZE = 10;
    public static final int SLIDER_HANDLE = 4;
    public static final int SLIDER_INSET = 4;
    public static final int LED_SIZE = 7;
    public static final int ICON_SPRITE = 13;

    private Metrics() {
    }

    public static int labelWidth(int rowWidth) {
        int preferred = Math.clamp(rowWidth * 11L / 20L, LABEL_MIN, LABEL_MAX);
        return Math.max(0, Math.min(preferred, rowWidth - CONTROL_MIN));
    }

    public static int labelWidth(int rowWidth, int textWidth) {
        int base = labelWidth(rowWidth);
        int needed = textWidth + LABEL_GAP + 2;
        if (needed <= base) {
            return base;
        }
        return Math.max(base, Math.min(needed, rowWidth - CONTROL_MIN));
    }

    public static int labelWidth(int rowWidth, int textWidth, int controlNeed) {
        int width = labelWidth(rowWidth, textWidth);
        int needed = textWidth + LABEL_GAP + 2;
        if (rowWidth - width < controlNeed && width > needed) {
            width = Math.max(needed, rowWidth - controlNeed);
        }
        return width;
    }

    public static boolean hasLabelColumn(int rowWidth) {
        return rowWidth >= LABEL_MIN + CONTROL_MIN + LABEL_GAP;
    }

    public static int textY(int top, int height) {
        return top + (height - TEXT_HEIGHT) / 2;
    }

    public static int columns(int width) {
        return width >= 2 * COLUMN_MIN + GAP ? 2 : 1;
    }

    public static int columnWidth(int width, int columns) {
        int count = Math.max(1, columns);
        return Math.max(1, Math.min(COLUMN_MAX, (width - (count - 1) * GAP) / count));
    }

    public static double sliderFraction(double mouseX, int trackLeft, int trackWidth) {
        double span = Math.max(1.0, trackWidth - 2.0 * SLIDER_INSET);
        return Math.clamp((mouseX - trackLeft - SLIDER_INSET) / span, 0.0, 1.0);
    }

    public static int sliderHandleCenter(int trackLeft, int trackWidth, double fraction) {
        double f = Double.isFinite(fraction) ? Math.clamp(fraction, 0.0, 1.0) : 0.0;
        return trackLeft + SLIDER_INSET + (int) Math.round(f * Math.max(0, trackWidth - 2 * SLIDER_INSET));
    }
}
