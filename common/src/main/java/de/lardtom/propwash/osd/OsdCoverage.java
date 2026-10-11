package de.lardtom.propwash.osd;

import java.util.Arrays;

public final class OsdCoverage {
    private static final float EPSILON = 1.0E-4F;
    private static final byte EMPTY = 0;
    private static final byte FILL = 1;
    private static final byte OUTLINE = 2;

    private OsdCoverage() {
    }

    public interface Area {
        boolean covered(float x, float y);
    }

    public record Shapes(float[] fill, int fillCount, float[] outline, int outlineCount) {
        public static final Shapes NONE = new Shapes(new float[0], 0, new float[0], 0);

        public float minX() {
            return bound(0, true);
        }

        public float minY() {
            return bound(1, true);
        }

        public float maxX() {
            return bound(2, false);
        }

        public float maxY() {
            return bound(3, false);
        }

        private float bound(int index, boolean min) {
            float value = min ? Float.MAX_VALUE : -Float.MAX_VALUE;
            for (int i = 0; i < this.fillCount; i++) {
                float v = this.fill[i * 4 + index];
                value = min ? Math.min(value, v) : Math.max(value, v);
            }
            for (int i = 0; i < this.outlineCount; i++) {
                float v = this.outline[i * 4 + index];
                value = min ? Math.min(value, v) : Math.max(value, v);
            }
            return value;
        }

        public boolean isEmpty() {
            return this.fillCount == 0 && this.outlineCount == 0;
        }
    }

    public static Shapes mask(boolean[] mask, int width, int height, OsdConfig.Outline outline, float e) {
        if (width <= 0 || height <= 0 || mask.length < width * height) {
            return Shapes.NONE;
        }
        float[] xs = new float[width + 1];
        float[] ys = new float[height + 1];
        for (int i = 0; i <= width; i++) {
            xs[i] = i;
        }
        for (int i = 0; i <= height; i++) {
            ys[i] = i;
        }
        Area area = (x, y) -> {
            int ix = (int) Math.floor(x);
            int iy = (int) Math.floor(y);
            return ix >= 0 && iy >= 0 && ix < width && iy < height && mask[iy * width + ix];
        };
        return compute(xs, ys, area, outline, e);
    }

    public static Shapes rects(int[] rects, int count, OsdConfig.Outline outline, float e) {
        int n = Math.min(count, rects.length / 4);
        if (n <= 0) {
            return Shapes.NONE;
        }
        float[] xs = new float[n * 2];
        float[] ys = new float[n * 2];
        int valid = 0;
        for (int i = 0; i < n; i++) {
            int x0 = rects[i * 4];
            int y0 = rects[i * 4 + 1];
            int x1 = rects[i * 4 + 2];
            int y1 = rects[i * 4 + 3];
            if (x1 <= x0 || y1 <= y0) {
                continue;
            }
            xs[valid * 2] = x0;
            xs[valid * 2 + 1] = x1;
            ys[valid * 2] = y0;
            ys[valid * 2 + 1] = y1;
            valid++;
        }
        if (valid == 0) {
            return Shapes.NONE;
        }
        int[] copy = Arrays.copyOf(rects, n * 4);
        Area area = (x, y) -> {
            for (int i = 0; i < n; i++) {
                int b = i * 4;
                if (x > copy[b] && x < copy[b + 2] && y > copy[b + 1] && y < copy[b + 3]) {
                    return true;
                }
            }
            return false;
        };
        return compute(Arrays.copyOf(xs, valid * 2), Arrays.copyOf(ys, valid * 2), area, outline, e);
    }

    static Shapes compute(float[] baseX, float[] baseY, Area area, OsdConfig.Outline outline, float e) {
        OsdConfig.Outline mode = outline == null || !(e > 0.0F) ? OsdConfig.Outline.NONE : outline;
        float[] xs = axis(baseX, mode, e);
        float[] ys = axis(baseY, mode, e);
        int columns = xs.length - 1;
        int rows = ys.length - 1;
        if (columns <= 0 || rows <= 0) {
            return Shapes.NONE;
        }
        byte[] cells = new byte[columns * rows];
        for (int j = 0; j < rows; j++) {
            float cy = (ys[j] + ys[j + 1]) * 0.5F;
            for (int i = 0; i < columns; i++) {
                float cx = (xs[i] + xs[i + 1]) * 0.5F;
                byte state;
                if (area.covered(cx, cy)) {
                    state = FILL;
                } else {
                    state = outlined(area, mode, cx, cy, e) ? OUTLINE : EMPTY;
                }
                cells[j * columns + i] = state;
            }
        }
        Merger fill = new Merger();
        Merger shade = new Merger();
        for (int j = 0; j < rows; j++) {
            fill.beginRow(ys[j], ys[j + 1]);
            shade.beginRow(ys[j], ys[j + 1]);
            int i = 0;
            while (i < columns) {
                byte state = cells[j * columns + i];
                int end = i + 1;
                while (end < columns && cells[j * columns + end] == state) {
                    end++;
                }
                if (state == FILL) {
                    fill.run(xs[i], xs[end]);
                } else if (state == OUTLINE) {
                    shade.run(xs[i], xs[end]);
                }
                i = end;
            }
            fill.endRow();
            shade.endRow();
        }
        fill.finish();
        shade.finish();
        return new Shapes(fill.quads(), fill.count(), shade.quads(), shade.count());
    }

    private static boolean outlined(Area area, OsdConfig.Outline mode, float cx, float cy, float e) {
        return switch (mode) {
            case NONE -> false;
            case SHADOW -> area.covered(cx - e, cy - e);
            case THIN -> area.covered(cx - e, cy) || area.covered(cx + e, cy) || area.covered(cx, cy - e) || area.covered(cx, cy + e);
            case THICK -> {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx != 0 || dy != 0) && area.covered(cx + dx * e, cy + dy * e)) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
        };
    }

    private static float[] axis(float[] base, OsdConfig.Outline mode, float e) {
        int copies = switch (mode) {
            case NONE -> 1;
            case SHADOW -> 2;
            case THIN, THICK -> 3;
        };
        float[] all = new float[base.length * copies];
        int n = 0;
        for (float v : base) {
            all[n++] = v;
            if (mode == OsdConfig.Outline.SHADOW) {
                all[n++] = v + e;
            } else if (copies == 3) {
                all[n++] = v - e;
                all[n++] = v + e;
            }
        }
        Arrays.sort(all, 0, n);
        float[] unique = new float[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (count == 0 || all[i] - unique[count - 1] > EPSILON) {
                unique[count++] = all[i];
            }
        }
        return Arrays.copyOf(unique, count);
    }

    private static final class Merger {
        private float[] quads = new float[64];
        private int count;
        private float[] open = new float[16];
        private int[] openIndex = new int[4];
        private int openCount;
        private float[] next = new float[16];
        private int[] nextIndex = new int[4];
        private int nextCount;
        private float rowTop;
        private float rowBottom;

        void beginRow(float top, float bottom) {
            this.rowTop = top;
            this.rowBottom = bottom;
            this.nextCount = 0;
        }

        void run(float x0, float x1) {
            for (int k = 0; k < this.openCount; k++) {
                int index = this.openIndex[k];
                if (index >= 0 && Math.abs(this.open[k * 2] - x0) < EPSILON && Math.abs(this.open[k * 2 + 1] - x1) < EPSILON
                        && Math.abs(this.quads[index * 4 + 3] - this.rowTop) < EPSILON) {
                    this.quads[index * 4 + 3] = this.rowBottom;
                    this.openIndex[k] = -1;
                    this.keep(x0, x1, index);
                    return;
                }
            }
            int index = this.add(x0, this.rowTop, x1, this.rowBottom);
            this.keep(x0, x1, index);
        }

        void endRow() {
            float[] swap = this.open;
            this.open = this.next;
            this.next = swap;
            int[] swapIndex = this.openIndex;
            this.openIndex = this.nextIndex;
            this.nextIndex = swapIndex;
            this.openCount = this.nextCount;
        }

        void finish() {
            this.openCount = 0;
        }

        float[] quads() {
            return Arrays.copyOf(this.quads, this.count * 4);
        }

        int count() {
            return this.count;
        }

        private void keep(float x0, float x1, int index) {
            if (this.nextCount * 2 + 2 > this.next.length) {
                this.next = Arrays.copyOf(this.next, this.next.length * 2);
            }
            if (this.nextCount + 1 > this.nextIndex.length) {
                this.nextIndex = Arrays.copyOf(this.nextIndex, this.nextIndex.length * 2);
            }
            this.next[this.nextCount * 2] = x0;
            this.next[this.nextCount * 2 + 1] = x1;
            this.nextIndex[this.nextCount] = index;
            this.nextCount++;
        }

        private int add(float x0, float y0, float x1, float y1) {
            if (this.count * 4 + 4 > this.quads.length) {
                this.quads = Arrays.copyOf(this.quads, this.quads.length * 2);
            }
            int b = this.count * 4;
            this.quads[b] = x0;
            this.quads[b + 1] = y0;
            this.quads[b + 2] = x1;
            this.quads[b + 3] = y1;
            return this.count++;
        }
    }
}
