package de.lardtom.propwash.ui;

import java.util.ArrayList;
import java.util.List;

public final class ChipFlow {
    private ChipFlow() {
    }

    public record Slot(int index, int line, int x, int width) {
    }

    public record Page(List<Slot> slots, int lines, int moreLine, int moreX, int hidden, int next) {
        public Page {
            slots = List.copyOf(slots);
        }

        public boolean paged() {
            return this.hidden > 0;
        }

        public boolean shows(int index) {
            for (Slot slot : this.slots) {
                if (slot.index() == index) {
                    return true;
                }
            }
            return false;
        }

        public int first() {
            return this.slots.isEmpty() ? 0 : this.slots.getFirst().index();
        }
    }

    public static Page page(int[] widths, int start, int firstLeft, int restLeft, int right, int gap, int maxLines, int moreWidth) {
        Page whole = place(widths, 0, firstLeft, restLeft, right, gap, maxLines, 0);
        if (whole.hidden() == 0) {
            return whole;
        }
        int from = Math.max(0, Math.min(start, widths.length - 1));
        return place(widths, from, firstLeft, restLeft, right, gap, maxLines, Math.max(1, moreWidth));
    }

    public static Page pageShowing(int[] widths, int wanted, int start, int firstLeft, int restLeft, int right, int gap, int maxLines, int moreWidth) {
        Page page = page(widths, start, firstLeft, restLeft, right, gap, maxLines, moreWidth);
        for (int guard = 0; guard < widths.length && wanted >= 0 && wanted < widths.length && !page.shows(wanted); guard++) {
            page = page(widths, page.next(), firstLeft, restLeft, right, gap, maxLines, moreWidth);
        }
        return page;
    }

    private static Page place(int[] widths, int from, int firstLeft, int restLeft, int right, int gap, int maxLines, int moreWidth) {
        List<Slot> slots = new ArrayList<>();
        int lines = Math.max(1, maxLines);
        int reserve = moreWidth > 0 ? moreWidth + gap : 0;
        int line = 0;
        int x = firstLeft;
        int index = from;
        while (index < widths.length) {
            int limit = line == lines - 1 ? right - reserve : right;
            int width = widths[index];
            if (x + width > limit) {
                if (line < lines - 1) {
                    line++;
                    x = restLeft;
                    continue;
                }
                if (!slots.isEmpty()) {
                    break;
                }
                width = Math.max(1, limit - x);
            }
            slots.add(new Slot(index, line, x, width));
            x += width + gap;
            index++;
        }
        int hidden = widths.length - slots.size();
        if (hidden == 0 || moreWidth <= 0) {
            return new Page(slots, slots.isEmpty() ? 1 : slots.getLast().line() + 1, -1, -1, hidden, 0);
        }
        int moreLine = line;
        int moreX = x;
        if (moreX + moreWidth > right) {
            if (line < lines - 1) {
                moreLine = line + 1;
                moreX = restLeft;
            } else {
                moreX = right - moreWidth;
            }
        }
        return new Page(slots, moreLine + 1, moreLine, moreX, hidden, index >= widths.length ? 0 : index);
    }
}
