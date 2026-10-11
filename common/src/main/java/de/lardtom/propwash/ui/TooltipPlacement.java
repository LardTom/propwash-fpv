package de.lardtom.propwash.ui;

public final class TooltipPlacement {
    public static final int WRAP_WIDTH = 170;
    public static final int FRAME = 4;
    public static final int EDGE = FRAME + 1;
    public static final int MIN_WIDTH = 40;
    public static final int ROW = 10;

    private TooltipPlacement() {
    }

    public static int room(int screen) {
        return Math.max(0, screen - 2 * EDGE);
    }

    public static int wrapWidth(int wanted, int screenWidth) {
        return Math.max(MIN_WIDTH, Math.min(Math.max(1, wanted), room(screenWidth)));
    }

    public static int clamp(int position, int size, int screen) {
        int last = screen - EDGE - size;
        if (last <= EDGE) {
            return EDGE;
        }
        return Math.max(EDGE, Math.min(last, position));
    }

    public static boolean tooTall(int rowsHeight, int screenHeight) {
        return rowsHeight > room(screenHeight);
    }

    public static int lastStart(int[] heights, int room) {
        int start = heights.length;
        int sum = 0;
        while (start > 0 && sum + heights[start - 1] <= room) {
            sum += heights[start - 1];
            start--;
        }
        return Math.min(start, Math.max(0, heights.length - 1));
    }

    public static int windowEnd(int[] heights, int start, int room) {
        int end = start;
        int sum = 0;
        while (end < heights.length && sum + heights[end] <= room) {
            sum += heights[end];
            end++;
        }
        return Math.min(heights.length, Math.max(end, start + 1));
    }

    public static boolean fits(int position, int size, int screen) {
        return position - FRAME >= 0 && position + size + FRAME <= screen;
    }
}
