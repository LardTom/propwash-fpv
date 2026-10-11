package de.lardtom.propwash.ui;

public final class ScrollMath {
    public static final int MIN_THUMB = 12;
    public static final int LINE = 18;
    public static final int PAGE_OVERLAP = 20;

    private ScrollMath() {
    }

    public static int maxOffset(int content, int view) {
        return view <= 0 ? 0 : Math.max(0, content - view);
    }

    public static double clamp(double offset, int content, int view) {
        if (!Double.isFinite(offset)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(maxOffset(content, view), offset));
    }

    public static boolean scrollable(int content, int view) {
        return maxOffset(content, view) > 0;
    }

    public static int thumbHeight(int content, int view) {
        if (view <= 0) {
            return 0;
        }
        if (content <= view) {
            return view;
        }
        return Math.min(view, Math.max(MIN_THUMB, (int) ((long) view * view / content)));
    }

    public static int thumbTop(double offset, int content, int view) {
        int max = maxOffset(content, view);
        if (max <= 0) {
            return 0;
        }
        int travel = Math.max(0, view - thumbHeight(content, view));
        return (int) Math.round(clamp(offset, content, view) / max * travel);
    }

    public static double dragOffset(double mouseInTrack, int content, int view) {
        int thumb = thumbHeight(content, view);
        double travel = Math.max(1.0, view - thumb);
        double t = Math.max(0.0, Math.min(1.0, (mouseInTrack - thumb / 2.0) / travel));
        return t * maxOffset(content, view);
    }

    public static double wheel(double offset, double notches, int content, int view) {
        return clamp(offset - notches * LINE, content, view);
    }

    public static double page(double offset, int direction, int content, int view) {
        int step = Math.max(LINE, view - PAGE_OVERLAP);
        return clamp(offset + Integer.signum(direction) * (double) step, content, view);
    }

    public static double reveal(double offset, int itemTop, int itemHeight, int content, int view, int margin) {
        double current = clamp(offset, content, view);
        int pad = Math.max(0, Math.min(margin, (view - itemHeight) / 2));
        double top = itemTop - pad;
        double bottom = itemTop + itemHeight + pad;
        if (top < current || itemHeight >= view) {
            return clamp(top, content, view);
        }
        if (bottom > current + view) {
            return clamp(bottom - view, content, view);
        }
        return current;
    }

    public static int rowMax(int rows, int visible) {
        return Math.max(0, rows - Math.max(0, visible));
    }

    public static int rowClamp(int first, int rows, int visible) {
        return Math.max(0, Math.min(rowMax(rows, visible), first));
    }

    public static int rowPage(int first, int direction, int rows, int visible) {
        return rowClamp(first + Integer.signum(direction) * Math.max(1, visible - 1), rows, visible);
    }

    public static int rowSteps(double notches, int rows, int visible, int rowHeight) {
        double amount = Math.abs(notches);
        if (Double.isNaN(amount) || amount == 0.0) {
            return 0;
        }
        if (amount <= 1.0) {
            return 1;
        }
        double wanted = amount * LINE / Math.max(1, rowHeight);
        if (wanted >= rows) {
            return Math.max(1, rows);
        }
        return (int) Math.max(1L, Math.min(Math.round(wanted), Math.max(1, visible - 1)));
    }

    public static int rowDrag(double mouseInTrack, int rows, int visible, int rowHeight) {
        int row = Math.max(1, rowHeight);
        return rowClamp((int) Math.round(dragOffset(mouseInTrack, rows * row, visible * row) / row), rows, visible);
    }
}
