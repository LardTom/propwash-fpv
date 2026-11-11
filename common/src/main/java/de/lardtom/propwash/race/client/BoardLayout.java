package de.lardtom.propwash.race.client;

public final class BoardLayout {
    public static final int NONE = -1;
    public static final int CLEAN = 0;
    public static final int ASSISTED = 1;

    public enum Kind {
        NONE,
        HEADER,
        ROW,
        MESSAGE
    }

    public record Hit(Kind kind, boolean assisted, int rank) {
        public static final Hit MISS = new Hit(Kind.NONE, false, 0);
    }

    private final int row;
    private final int clean;
    private final int assisted;
    private final boolean cleanOpen;
    private final boolean assistedOpen;
    private final int cleanEnd;
    private final int assistedHeader;
    private final int assistedTop;
    private final int height;

    public BoardLayout(int clean, int assisted, boolean cleanCollapsed, boolean assistedCollapsed, int row, int gap) {
        this.row = row;
        this.clean = Math.max(0, clean);
        this.assisted = Math.max(0, assisted);
        this.cleanOpen = !cleanCollapsed;
        this.assistedOpen = !assistedCollapsed;
        int y = row;
        if (this.cleanOpen) {
            y += this.clean > 0 ? this.clean * row : row;
        }
        this.cleanEnd = y;
        if (this.assisted > 0) {
            y += gap;
            this.assistedHeader = y;
            y += row;
            this.assistedTop = y;
            if (this.assistedOpen) {
                y += this.assisted * row;
            }
        } else {
            this.assistedHeader = -1;
            this.assistedTop = -1;
        }
        this.height = y;
    }

    public int height() {
        return this.height;
    }

    public int total(boolean assisted) {
        return assisted ? this.assisted : this.clean;
    }

    public boolean has(boolean assisted) {
        return !assisted || this.assisted > 0;
    }

    public boolean open(boolean assisted) {
        return assisted ? this.assistedOpen : this.cleanOpen;
    }

    public int headerY(boolean assisted) {
        return assisted ? this.assistedHeader : 0;
    }

    public boolean message() {
        return this.cleanOpen && this.clean == 0;
    }

    public int messageY() {
        return this.message() ? this.row : Integer.MIN_VALUE;
    }

    public int rowY(boolean assisted, int rank) {
        if (!this.has(assisted) || !this.open(assisted) || rank < 1 || rank > this.total(assisted)) {
            return Integer.MIN_VALUE;
        }
        return (assisted ? this.assistedTop : this.row) + (rank - 1) * this.row;
    }

    public int[] visible(boolean assisted, int top, int bottom) {
        if (!this.has(assisted) || !this.open(assisted) || this.total(assisted) == 0) {
            return new int[0];
        }
        int start = assisted ? this.assistedTop : this.row;
        int first = Math.max(1, Math.floorDiv(top - start, this.row) + 1);
        int last = Math.min(this.total(assisted), Math.floorDiv(bottom - 1 - start, this.row) + 1);
        return first > last ? new int[0] : new int[]{first, last};
    }

    public int sticky(int top) {
        if (top <= 0) {
            return NONE;
        }
        if (this.cleanOpen && this.clean > 0 && top < this.cleanEnd) {
            return CLEAN;
        }
        if (this.assisted > 0 && this.assistedOpen && top > this.assistedHeader && top < this.height) {
            return ASSISTED;
        }
        return NONE;
    }

    public Hit hit(int y) {
        if (y < 0 || y >= this.height) {
            return Hit.MISS;
        }
        if (y < this.row) {
            return new Hit(Kind.HEADER, false, 0);
        }
        if (y < this.cleanEnd) {
            if (this.message()) {
                return new Hit(Kind.MESSAGE, false, 0);
            }
            return new Hit(Kind.ROW, false, (y - this.row) / this.row + 1);
        }
        if (this.assisted == 0 || y < this.assistedHeader) {
            return Hit.MISS;
        }
        if (y < this.assistedTop) {
            return new Hit(Kind.HEADER, true, 0);
        }
        return new Hit(Kind.ROW, true, (y - this.assistedTop) / this.row + 1);
    }
}
