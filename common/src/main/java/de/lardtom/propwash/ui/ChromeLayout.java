package de.lardtom.propwash.ui;

public final class ChromeLayout {
    public static final int HEADER_ROW = 24;
    public static final int HEADER_SECOND_ROW = 14;
    public static final int FOOTER_MIN = 24;
    public static final int LINE = 10;
    public static final int GLYPH = 8;
    public static final int FOOTER_MARGIN = 3;
    public static final int STATUS_MIN = 110;
    public static final int SIDEBAR_MIN = 78;
    public static final int SIDEBAR_MAX = 116;
    public static final int SIDEBAR_PADDING = 14;
    public static final int SIDEBAR_SCROLL_ROW = 11;
    public static final int RADIO_MIN = 40;
    public static final int BADGE_GAP = 6;
    private static final int NAME_SHARE_PERCENT = 40;
    private static final int RADIO_SHARE_PERCENT = 40;

    private ChromeLayout() {
    }

    public static int headerHeight(int width, int brandRight, int nameWidth, int badgeWidth, int radioWidth, int searchWidth) {
        return headerFitsOneRow(width, brandRight, nameWidth, badgeWidth, radioWidth, searchWidth) ? HEADER_ROW : HEADER_ROW + HEADER_SECOND_ROW;
    }

    public static int headerRows(int width, int brandRight, int nameWidth, int badgeWidth, int radioWidth, int searchWidth) {
        if (headerFitsOneRow(width, brandRight, nameWidth, badgeWidth, radioWanted(width, radioWidth), searchWidth)) {
            return 1;
        }
        int beside = radioSpace(width, brandRight, nameWidth, badgeWidth, searchWidth, true);
        boolean fits = radioWidth <= beside || beside >= RADIO_MIN && TextFit.mode(radioWidth, beside) != TextFit.Mode.CUT;
        return fits ? 2 : 3;
    }

    public static int headerHeight(int rows) {
        return HEADER_ROW + (Math.max(1, rows) - 1) * HEADER_SECOND_ROW;
    }

    public static int radioRowRoom(int width) {
        return Math.max(RADIO_MIN, width - 8 - 6 - 8);
    }

    public static boolean headerFitsOneRow(int width, int brandRight, int nameWidth, int badgeWidth, int radioWidth, int searchWidth) {
        int name = Math.min(nameWidth, Math.max(0, width * NAME_SHARE_PERCENT / 100));
        int needed = brandRight + 10 + name + 6 + badgeWidth + 14 + 6 + radioWidth + 18 + searchWidth + 8;
        return needed <= width;
    }

    public static int badgesWidth(int stateBadge, int lockBadge) {
        return lockBadge > 0 ? stateBadge + BADGE_GAP + lockBadge : stateBadge;
    }

    public static int radioWanted(int width, int radioWidth) {
        return Math.min(radioWidth, Math.max(RADIO_MIN, width * RADIO_SHARE_PERCENT / 100));
    }

    public static int radioRoom(int width, int brandRight, int nameWidth, int badgeWidth, int searchWidth, boolean twoRows) {
        return Math.max(RADIO_MIN, radioSpace(width, brandRight, nameWidth, badgeWidth, searchWidth, twoRows));
    }

    public static int radioSpace(int width, int brandRight, int nameWidth, int badgeWidth, int searchWidth, boolean twoRows) {
        int name = Math.min(nameWidth, Math.max(0, width * NAME_SHARE_PERCENT / 100));
        int fixed = twoRows ? 8 + name + 6 + badgeWidth + 14 + 6 + 8 : brandRight + 10 + name + 6 + badgeWidth + 14 + 6 + 18 + searchWidth + 8;
        return width - fixed;
    }

    public static int statusBlockHeight(int lines) {
        return lines <= 0 ? 0 : (lines - 1) * LINE + GLYPH;
    }

    public static int footerHeight(int statusLines) {
        return Math.max(FOOTER_MIN, statusBlockHeight(statusLines) + 2 * FOOTER_MARGIN);
    }

    public static int statusLimit(int windowWidth, int saveWidth, int sideWidth, int padding, int gap) {
        return Math.max(40, windowWidth - padding - saveWidth - gap - sideWidth - 16);
    }

    public static boolean footerStacked(int windowWidth, int saveWidth, int sideWidth, int padding, int gap) {
        return windowWidth - padding - saveWidth - gap - sideWidth - 16 < STATUS_MIN;
    }

    public static int stackedStatusLimit(int windowWidth) {
        return Math.max(40, windowWidth - 16);
    }

    public static int stackedFooterHeight(int statusLines, int controlHeight) {
        return statusBlockHeight(Math.max(1, statusLines)) + controlHeight + 3 * FOOTER_MARGIN;
    }

    public static int stackedButtonTop(int footerTop, int footerHeight, int controlHeight) {
        return footerTop + footerHeight - FOOTER_MARGIN - controlHeight;
    }

    public static int stackedStatusTop(int footerTop, int footerHeight, int controlHeight, int lines) {
        int lowest = stackedButtonTop(footerTop, footerHeight, controlHeight) - FOOTER_MARGIN - statusBlockHeight(lines);
        return Math.min(footerTop + FOOTER_MARGIN, lowest);
    }

    public static int statusTop(int footerTop, int footerHeight, int lines) {
        int block = statusBlockHeight(lines);
        int centered = footerTop + (footerHeight - block) / 2;
        int lowest = footerTop + footerHeight - FOOTER_MARGIN - block;
        return Math.min(centered, lowest);
    }

    public static int sidebarWidth(int windowWidth, int widestTitle) {
        int classic = Math.max(SIDEBAR_MIN, Math.min(SIDEBAR_MAX, windowWidth / 5));
        int needed = widestTitle + SIDEBAR_PADDING;
        int limit = Math.max(SIDEBAR_MIN, windowWidth * 3 / 10);
        return Math.max(classic, Math.min(needed, limit));
    }

    public static int sidebarRow(int available, int tabs, int preferred, int minimum) {
        if (tabs <= 0) {
            return preferred;
        }
        int even = available / tabs;
        if (even >= minimum) {
            return Math.min(preferred, even);
        }
        return Math.max(minimum, SIDEBAR_SCROLL_ROW);
    }

    public static int sidebarVisibleRows(int available, int row) {
        return Math.max(1, available / Math.max(1, row));
    }

    public static int sidebarFirstRow(int first, int active, int tabs, int visible) {
        int maxFirst = Math.max(0, tabs - visible);
        int clamped = Math.max(0, Math.min(maxFirst, first));
        if (active < 0 || active >= tabs) {
            return clamped;
        }
        if (active < clamped) {
            return active;
        }
        if (active >= clamped + visible) {
            return Math.min(maxFirst, active - visible + 1);
        }
        return clamped;
    }
}
