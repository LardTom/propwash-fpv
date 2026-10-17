package de.lardtom.propwash.content;

public final class WorkbenchLayout {
    public static final int WIDTH = 312;
    public static final int HEIGHT = 224;

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 142;

    public static final int BLUEPRINT_X0 = 7;
    public static final int BLUEPRINT_Y0 = 17;
    public static final int BLUEPRINT_X1 = 169;
    public static final int BLUEPRINT_Y1 = 128;
    public static final int CENTER_X = 88;
    public static final int CENTER_Y = 72;

    public static final int PREVIEW_X0 = 176;
    public static final int PREVIEW_Y0 = 17;
    public static final int PREVIEW_X1 = 305;
    public static final int PREVIEW_Y1 = 91;

    public static final int SALVAGE_X = 234;
    public static final int SALVAGE_Y = 100;
    public static final int RESULT_X = 202;
    public static final int RESULT_Y = 100;
    public static final int ARROW_X = 171;
    public static final int ARROW_Y = 100;

    public static final int STATS_X0 = 177;
    public static final int STATS_Y0 = 129;
    public static final int STATS_X1 = 304;
    public static final int STATS_PAD = 4;
    public static final int STATS_ROW = 10;
    public static final int STATS_GAP = 4;
    public static final int ISSUES_Y1 = 217;
    public static final int ISSUE_BULLET = 4;
    public static final int ISSUE_BULLET_GAP = 5;

    private static final int[] PART_X = {80, 80, 34, 126, 34, 126, 10, 150, 10, 150, 80, 80, 52, 108, 30};
    private static final int[] PART_Y = {64, 42, 30, 30, 98, 98, 30, 30, 98, 98, 20, 87, 64, 64, 64};

    private WorkbenchLayout() {
    }

    public static int statLabelWidth(int valueWidth) {
        return STATS_X1 - STATS_PAD - valueWidth - STATS_GAP - (STATS_X0 + STATS_PAD);
    }

    public static int issueTextWidth() {
        return STATS_X1 - STATS_PAD - (STATS_X0 + STATS_PAD + ISSUE_BULLET + ISSUE_BULLET_GAP);
    }

    public static int partX(int slot) {
        return PART_X[slot];
    }

    public static int partY(int slot) {
        return PART_Y[slot];
    }
}
