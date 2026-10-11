package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.ui.ScrollMath;
import de.lardtom.propwash.ui.client.theme.Metrics;

final class RescueTabLayout {
    static final int GAP = Metrics.GAP;
    static final int HEADER = Metrics.SECTION_HEIGHT;
    static final int ROW_HEIGHT = Metrics.ROW_HEIGHT;
    static final int WIDGET_HEIGHT = Metrics.CONTROL_HEIGHT;
    static final int STATUS_OFFSET = 4;
    static final int STATUS_HEIGHT = 24;
    static final int SCROLLBAR_WIDTH = 6;
    static final int SCROLLBAR_TRACK = 3;
    static final int MIN_COLUMN_WIDTH = 140;
    static final int MAX_COLUMN_WIDTH = Metrics.COLUMN_MAX;
    private static final int MIN_THUMB = 12;

    private final int width;
    private final int count;
    private final int columns;
    private final int rows;
    private final int columnWidth;
    private final int viewport;
    private final int visibleRows;
    private final int maxScroll;

    RescueTabLayout(int width, int height, int count) {
        this(width, height, count, MIN_COLUMN_WIDTH);
    }

    RescueTabLayout(int width, int height, int count, int neededColumnWidth) {
        this.width = Math.max(0, width);
        this.count = Math.max(0, count);
        this.columns = (this.width - GAP) / 2 >= Math.max(MIN_COLUMN_WIDTH, neededColumnWidth) ? 2 : 1;
        this.rows = (this.count + this.columns - 1) / this.columns;
        this.viewport = Math.max(0, height - HEADER);
        this.visibleRows = this.viewport < WIDGET_HEIGHT ? 0 : (this.viewport - WIDGET_HEIGHT) / ROW_HEIGHT + 1;
        this.maxScroll = Math.max(0, Math.min(this.rows, this.rows - Math.floorDiv(this.viewport - STATUS_HEIGHT, ROW_HEIGHT)));
        int usable = this.width - (this.maxScroll > 0 ? SCROLLBAR_WIDTH : 0);
        this.columnWidth = Math.max(1, Math.min(MAX_COLUMN_WIDTH, (usable - GAP * (this.columns - 1)) / this.columns));
    }

    int count() {
        return this.count;
    }

    int columns() {
        return this.columns;
    }

    int rows() {
        return this.rows;
    }

    int columnWidth() {
        return this.columnWidth;
    }

    int visibleRows() {
        return this.visibleRows;
    }

    int maxScroll() {
        return this.maxScroll;
    }

    boolean scrollable() {
        return this.maxScroll > 0;
    }

    int scrollRows(double notches) {
        return ScrollMath.rowSteps(notches, this.rows, this.visibleRows, ROW_HEIGHT);
    }

    int clampScroll(int scroll) {
        return Math.max(0, Math.min(this.maxScroll, scroll));
    }

    int left(int index) {
        return (index % this.columns) * (this.columnWidth + GAP);
    }

    int top(int index, int scroll) {
        return HEADER + (index / this.columns - scroll) * ROW_HEIGHT;
    }

    boolean visible(int index, int scroll) {
        int row = index / this.columns - scroll;
        return index >= 0 && index < this.count && row >= 0 && row < this.visibleRows;
    }

    int statusTop(int scroll) {
        return HEADER + (this.rows - scroll) * ROW_HEIGHT + STATUS_OFFSET;
    }

    boolean statusVisible(int scroll) {
        return scroll <= this.rows && (this.rows - scroll) * ROW_HEIGHT + STATUS_HEIGHT <= this.viewport;
    }

    int scrollbarLeft() {
        return this.width - SCROLLBAR_TRACK;
    }

    boolean overScrollbar(double relativeX, double relativeY, int track) {
        return this.scrollable() && relativeX >= this.width - SCROLLBAR_WIDTH && relativeX < this.width
                && relativeY >= 0 && relativeY < track;
    }

    int thumbHeight(int track) {
        int content = this.rows * ROW_HEIGHT + STATUS_HEIGHT + HEADER;
        if (content <= 0 || track <= 0) {
            return Math.max(0, track);
        }
        return Math.min(track, Math.max(MIN_THUMB, (int) ((long) track * (this.viewport + HEADER) / content)));
    }

    int thumbTop(int track, int scroll) {
        if (this.maxScroll <= 0) {
            return 0;
        }
        int free = Math.max(0, track - this.thumbHeight(track));
        return (int) Math.round((double) free * this.clampScroll(scroll) / this.maxScroll);
    }

    int scrollAt(double relativeY, int track) {
        if (this.maxScroll <= 0) {
            return 0;
        }
        int thumb = this.thumbHeight(track);
        double t = (relativeY - thumb / 2.0) / Math.max(1.0, track - thumb);
        return this.clampScroll((int) Math.round(Math.max(0.0, Math.min(1.0, t)) * this.maxScroll));
    }
}
