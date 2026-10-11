package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

public final class FreeformRegion {
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;
    public static final int MAX_DIMENSION = 128;
    public static final int MAX_BYTES = (MAX_DIMENSION * MAX_DIMENSION + 7) / 8;
    private static final String PREFIX = "f1";
    private static final char[] AXES = {'x', 'y', 'z'};

    private final int axis;
    private final int width;
    private final int height;
    private final int seedA;
    private final int seedB;
    private final byte[] bits;
    private final int cells;
    private final int hash;
    private final int targetA;
    private final int targetB;

    @FunctionalInterface
    public interface EdgeSink {
        void accept(int a0, int b0, int a1, int b1, int outsideA, int outsideB);
    }

    private FreeformRegion(int axis, int width, int height, int seedA, int seedB, byte[] bits) {
        this.axis = axis;
        this.width = width;
        this.height = height;
        this.seedA = seedA;
        this.seedB = seedB;
        this.bits = bits;
        int count = 0;
        for (byte b : bits) {
            count += Integer.bitCount(b & 0xFF);
        }
        this.cells = count;
        int h = axis;
        h = h * 31 + width;
        h = h * 31 + height;
        h = h * 31 + seedA;
        h = h * 31 + seedB;
        this.hash = h * 31 + Arrays.hashCode(bits);
        int[] target = this.cells == 0 ? new int[]{seedA, seedB} : this.findTarget();
        this.targetA = target[0];
        this.targetB = target[1];
    }

    public static @Nullable FreeformRegion of(int axis, int width, int height, int seedA, int seedB, byte[] packed) {
        if (axis < AXIS_X || axis > AXIS_Z || width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION || packed == null) {
            return null;
        }
        int length = (width * height + 7) / 8;
        if (packed.length != length) {
            return null;
        }
        byte[] copy = packed.clone();
        int unused = length * 8 - width * height;
        if (unused > 0) {
            copy[length - 1] &= (byte) (0xFF >>> unused);
        }
        FreeformRegion region = new FreeformRegion(axis, width, height, seedA, seedB, copy);
        if (region.cells == 0 || !region.contains(seedA, seedB)) {
            return null;
        }
        return region;
    }

    public static @Nullable FreeformRegion of(int axis, int width, int height, int seedA, int seedB, boolean[] cells) {
        if (cells == null || width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION || cells.length != width * height) {
            return null;
        }
        byte[] packed = new byte[(width * height + 7) / 8];
        for (int i = 0; i < cells.length; i++) {
            if (cells[i]) {
                packed[i >> 3] |= (byte) (1 << (i & 7));
            }
        }
        return of(axis, width, height, seedA, seedB, packed);
    }

    public static FreeformRegion single(int axis) {
        return new FreeformRegion(Math.clamp(axis, AXIS_X, AXIS_Z), 1, 1, 0, 0, new byte[]{1});
    }

    public static int aAxis(int axis) {
        return axis == AXIS_Z || axis == AXIS_Y ? AXIS_X : AXIS_Z;
    }

    public static int bAxis(int axis) {
        return axis == AXIS_Y ? AXIS_Z : AXIS_Y;
    }

    public int axis() {
        return this.axis;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int seedA() {
        return this.seedA;
    }

    public int seedB() {
        return this.seedB;
    }

    public int cells() {
        return this.cells;
    }

    public boolean horizontal() {
        return this.axis == AXIS_Y;
    }

    public boolean contains(int a, int b) {
        if (a < 0 || b < 0 || a >= this.width || b >= this.height) {
            return false;
        }
        int i = b * this.width + a;
        return (this.bits[i >> 3] & (1 << (i & 7))) != 0;
    }

    public byte[] packed() {
        return this.bits.clone();
    }

    public double halfDiagonal() {
        return Math.sqrt((double) this.width * this.width + (double) this.height * this.height) * 0.5;
    }

    public int targetA() {
        return this.targetA;
    }

    public int targetB() {
        return this.targetB;
    }

    private int[] findTarget() {
        int w = this.width;
        int h = this.height;
        int[] distance = new int[w * h];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int b = 0; b < h; b++) {
            for (int a = 0; a < w; a++) {
                if (!this.contains(a, b)) {
                    continue;
                }
                if (!this.contains(a - 1, b) || !this.contains(a + 1, b) || !this.contains(a, b - 1) || !this.contains(a, b + 1)) {
                    distance[b * w + a] = 1;
                    queue.add(b * w + a);
                }
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int a = i % w;
            int b = i / w;
            int next = distance[i] + 1;
            int[][] steps = {{a - 1, b}, {a + 1, b}, {a, b - 1}, {a, b + 1}};
            for (int[] step : steps) {
                if (this.contains(step[0], step[1])) {
                    int j = step[1] * w + step[0];
                    if (distance[j] > next) {
                        distance[j] = next;
                        queue.add(j);
                    }
                }
            }
        }
        double sumA = 0.0;
        double sumB = 0.0;
        for (int b = 0; b < h; b++) {
            for (int a = 0; a < w; a++) {
                if (this.contains(a, b)) {
                    sumA += a + 0.5;
                    sumB += b + 0.5;
                }
            }
        }
        double centroidA = sumA / this.cells;
        double centroidB = sumB / this.cells;
        int bestA = this.seedA;
        int bestB = this.seedB;
        int bestDistance = -1;
        double bestCentroid = Double.MAX_VALUE;
        for (int b = 0; b < h; b++) {
            for (int a = 0; a < w; a++) {
                if (!this.contains(a, b)) {
                    continue;
                }
                int d = distance[b * w + a];
                double da = a + 0.5 - centroidA;
                double db = b + 0.5 - centroidB;
                double c = da * da + db * db;
                if (d > bestDistance || d == bestDistance && c < bestCentroid - 1.0E-9) {
                    bestDistance = d;
                    bestCentroid = c;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        return new int[]{bestA, bestB};
    }

    public void edges(EdgeSink sink) {
        int w = this.width;
        int h = this.height;
        for (int b = 0; b <= h; b++) {
            for (int a = 0; a < w; a++) {
                boolean below = this.contains(a, b - 1);
                boolean above = this.contains(a, b);
                if (below && !above) {
                    sink.accept(a, b, a + 1, b, a, b);
                } else if (above && !below) {
                    sink.accept(a, b, a + 1, b, a, b - 1);
                }
            }
        }
        for (int a = 0; a <= w; a++) {
            for (int b = 0; b < h; b++) {
                boolean left = this.contains(a - 1, b);
                boolean right = this.contains(a, b);
                if (left && !right) {
                    sink.accept(a, b, a, b + 1, a, b);
                } else if (right && !left) {
                    sink.accept(a, b, a, b + 1, a - 1, b);
                }
            }
        }
    }

    public int edgeCount() {
        int[] count = {0};
        this.edges((a0, b0, a1, b1, oa, ob) -> count[0]++);
        return count[0];
    }

    public String encode() {
        return PREFIX + ":" + AXES[this.axis] + ":" + this.width + "x" + this.height + ":" + this.seedA + "," + this.seedB + ":"
                + Base64.getEncoder().withoutPadding().encodeToString(this.bits);
    }

    public static @Nullable FreeformRegion decode(@Nullable String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_BYTES * 2 + 64) {
            return null;
        }
        String[] parts = text.split(":", -1);
        if (parts.length != 5 || !PREFIX.equals(parts[0]) || parts[1].length() != 1) {
            return null;
        }
        int axis = switch (parts[1].toLowerCase(Locale.ROOT).charAt(0)) {
            case 'x' -> AXIS_X;
            case 'y' -> AXIS_Y;
            case 'z' -> AXIS_Z;
            default -> -1;
        };
        String[] size = parts[2].split("x", -1);
        String[] seed = parts[3].split(",", -1);
        if (axis < 0 || size.length != 2 || seed.length != 2) {
            return null;
        }
        try {
            int width = Integer.parseInt(size[0]);
            int height = Integer.parseInt(size[1]);
            int seedA = Integer.parseInt(seed[0]);
            int seedB = Integer.parseInt(seed[1]);
            byte[] packed = Base64.getDecoder().decode(parts[4]);
            return of(axis, width, height, seedA, seedB, packed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FreeformRegion that)) {
            return false;
        }
        return this.hash == that.hash && this.axis == that.axis && this.width == that.width && this.height == that.height && this.seedA == that.seedA
                && this.seedB == that.seedB && Arrays.equals(this.bits, that.bits);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public String toString() {
        return "FreeformRegion[" + AXES[this.axis] + " " + this.width + "x" + this.height + ", cells=" + this.cells + "]";
    }
}
