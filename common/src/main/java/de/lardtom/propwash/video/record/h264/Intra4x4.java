package de.lardtom.propwash.video.record.h264;

final class Intra4x4 {
    static final int VERTICAL = 0;
    static final int HORIZONTAL = 1;
    static final int DC = 2;
    static final int DIAGONAL_DOWN_LEFT = 3;
    static final int DIAGONAL_DOWN_RIGHT = 4;
    static final int VERTICAL_RIGHT = 5;
    static final int HORIZONTAL_DOWN = 6;
    static final int VERTICAL_LEFT = 7;
    static final int HORIZONTAL_UP = 8;
    static final int MODES = 9;

    final int[] top = new int[8];
    final int[] left = new int[4];
    int corner;
    boolean hasTop;
    boolean hasLeft;
    boolean hasCorner;

    private Intra4x4() {
    }

    static Intra4x4 create() {
        return new Intra4x4();
    }

    boolean available(int mode) {
        return switch (mode) {
            case VERTICAL, DIAGONAL_DOWN_LEFT, VERTICAL_LEFT -> this.hasTop;
            case HORIZONTAL, HORIZONTAL_UP -> this.hasLeft;
            case DC -> true;
            default -> this.hasTop && this.hasLeft && this.hasCorner;
        };
    }

    private int t(int x) {
        return x < 0 ? this.corner : this.top[x];
    }

    private int l(int y) {
        return y < 0 ? this.corner : this.left[y];
    }

    void predict(int mode, int[] out) {
        if (mode == DC) {
            java.util.Arrays.fill(out, 0, 16, this.dc());
            return;
        }
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                out[y * 4 + x] = this.sample(mode, x, y);
            }
        }
    }

    private int sample(int mode, int x, int y) {
        switch (mode) {
            case VERTICAL:
                return this.top[x];
            case HORIZONTAL:
                return this.left[y];
            case DC:
                return this.dc();
            case DIAGONAL_DOWN_LEFT:
                if (x == 3 && y == 3) {
                    return (this.t(6) + 3 * this.t(7) + 2) >> 2;
                }
                return (this.t(x + y) + 2 * this.t(x + y + 1) + this.t(x + y + 2) + 2) >> 2;
            case DIAGONAL_DOWN_RIGHT:
                if (x > y) {
                    return (this.t(x - y - 2) + 2 * this.t(x - y - 1) + this.t(x - y) + 2) >> 2;
                }
                if (x < y) {
                    return (this.l(y - x - 2) + 2 * this.l(y - x - 1) + this.l(y - x) + 2) >> 2;
                }
                return (this.t(0) + 2 * this.corner + this.l(0) + 2) >> 2;
            case VERTICAL_RIGHT: {
                int z = 2 * x - y;
                int k = x - (y >> 1);
                if (z >= 0 && (z & 1) == 0) {
                    return (this.t(k - 1) + this.t(k) + 1) >> 1;
                }
                if (z > 0) {
                    return (this.t(k - 2) + 2 * this.t(k - 1) + this.t(k) + 2) >> 2;
                }
                if (z == -1) {
                    return (this.l(0) + 2 * this.corner + this.t(0) + 2) >> 2;
                }
                return (this.l(y - 1) + 2 * this.l(y - 2) + this.l(y - 3) + 2) >> 2;
            }
            case HORIZONTAL_DOWN: {
                int z = 2 * y - x;
                int k = y - (x >> 1);
                if (z >= 0 && (z & 1) == 0) {
                    return (this.l(k - 1) + this.l(k) + 1) >> 1;
                }
                if (z > 0) {
                    return (this.l(k - 2) + 2 * this.l(k - 1) + this.l(k) + 2) >> 2;
                }
                if (z == -1) {
                    return (this.l(0) + 2 * this.corner + this.t(0) + 2) >> 2;
                }
                return (this.t(x - 1) + 2 * this.t(x - 2) + this.t(x - 3) + 2) >> 2;
            }
            case VERTICAL_LEFT: {
                int k = x + (y >> 1);
                if ((y & 1) == 0) {
                    return (this.t(k) + this.t(k + 1) + 1) >> 1;
                }
                return (this.t(k) + 2 * this.t(k + 1) + this.t(k + 2) + 2) >> 2;
            }
            default: {
                int z = x + 2 * y;
                int k = y + (x >> 1);
                if (z > 5) {
                    return this.left[3];
                }
                if (z == 5) {
                    return (this.left[2] + 3 * this.left[3] + 2) >> 2;
                }
                if ((z & 1) == 0) {
                    return (this.l(k) + this.l(k + 1) + 1) >> 1;
                }
                return (this.l(k) + 2 * this.l(k + 1) + this.l(k + 2) + 2) >> 2;
            }
        }
    }

    private int dc() {
        int sumTop = this.top[0] + this.top[1] + this.top[2] + this.top[3];
        int sumLeft = this.left[0] + this.left[1] + this.left[2] + this.left[3];
        if (this.hasTop && this.hasLeft) {
            return (sumTop + sumLeft + 4) >> 3;
        }
        if (this.hasLeft) {
            return (sumLeft + 2) >> 2;
        }
        if (this.hasTop) {
            return (sumTop + 2) >> 2;
        }
        return 128;
    }
}
