package de.lardtom.propwash.osd;

import java.util.Arrays;

public final class OsdStatsLayout {
    public static final int NONE = -1;
    private static final float EPSILON = 1.0E-4F;

    private final float[] tops = new float[8];
    private final float[] bottoms = new float[8];
    private int count;

    public static int blockTop(int row, int lines) {
        return Math.max(0, Math.min(row, OsdGrid.ROWS - Math.max(1, lines)));
    }

    public static float top(int row, float heightRows) {
        return row + 0.5F - heightRows * 0.5F;
    }

    public static float bottom(int row, float heightRows) {
        return row + 0.5F + heightRows * 0.5F;
    }

    public int[] arrange(int blockTop, int lines, int[] desired, float[] naturalRows, boolean[] shrunk) {
        int[] rows = new int[desired.length];
        if (this.placeAll(blockTop, lines, desired, naturalRows, false, rows)) {
            Arrays.fill(shrunk, false);
            return rows;
        }
        this.placeAll(blockTop, lines, desired, naturalRows, true, rows);
        for (int i = 0; i < desired.length; i++) {
            shrunk[i] = naturalRows[i] > 1.0F;
        }
        return rows;
    }

    private boolean placeAll(int blockTop, int lines, int[] desired, float[] naturalRows, boolean fitRow, int[] rows) {
        this.reset();
        if (lines > 0) {
            this.block(blockTop, blockTop + lines);
        }
        boolean all = true;
        for (int i = 0; i < desired.length; i++) {
            float height = fitRow ? Math.min(1.0F, naturalRows[i]) : naturalRows[i];
            rows[i] = desired[i] == NONE ? NONE : this.place(desired[i], height);
            all &= desired[i] == NONE || rows[i] != NONE;
        }
        return all;
    }

    public void reset() {
        this.count = 0;
    }

    public void block(float top, float bottom) {
        if (this.count < this.tops.length) {
            this.tops[this.count] = top;
            this.bottoms[this.count] = bottom;
            this.count++;
        }
    }

    public int place(int desired, float heightRows) {
        int start = OsdGrid.clampRow(desired);
        for (int distance = 0; distance < OsdGrid.ROWS; distance++) {
            int below = start + distance;
            if (this.fits(below, heightRows)) {
                return this.take(below, heightRows);
            }
            int above = start - distance;
            if (distance > 0 && this.fits(above, heightRows)) {
                return this.take(above, heightRows);
            }
        }
        return NONE;
    }

    public boolean fits(int row, float heightRows) {
        if (row < 0 || row >= OsdGrid.ROWS) {
            return false;
        }
        float top = top(row, heightRows);
        float bottom = bottom(row, heightRows);
        if (top < -EPSILON || bottom > OsdGrid.ROWS + EPSILON) {
            return false;
        }
        for (int i = 0; i < this.count; i++) {
            if (top < this.bottoms[i] - EPSILON && this.tops[i] < bottom - EPSILON) {
                return false;
            }
        }
        return true;
    }

    private int take(int row, float heightRows) {
        this.block(top(row, heightRows), bottom(row, heightRows));
        return row;
    }
}
