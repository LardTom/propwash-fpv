package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class FreeformFill {
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 64;
    public static final int MAX_PROBE = 128;

    public enum Cell {
        OPEN,
        SOLID,
        UNLOADED,
        OUTSIDE
    }

    public enum Status {
        OK,
        START_BLOCKED,
        OPEN,
        TOO_BIG,
        UNLOADED
    }

    @FunctionalInterface
    public interface Probe {
        Cell at(int x, int y, int z);
    }

    public record Result(Status status, @Nullable FreeformRegion region, int axis, int plane, int minA, int minB, int width, int height, int limit) {
        public boolean ok() {
            return this.status == Status.OK && this.region != null;
        }

        public double anchorX() {
            return anchor(this, FreeformRegion.AXIS_X);
        }

        public double anchorY() {
            return anchor(this, FreeformRegion.AXIS_Y);
        }

        public double anchorZ() {
            return anchor(this, FreeformRegion.AXIS_Z);
        }
    }

    public record Aim(int axis, int variant, int seedX, int seedY, int seedZ) {
        public int sign() {
            return this.variant == 0 ? 1 : -1;
        }
    }

    public record Integrity(int blockedCells, int openEdges, int unknown) {
        public static final Integrity INTACT = new Integrity(0, 0, 0);

        public boolean intact() {
            return this.blockedCells == 0 && this.openEdges == 0;
        }

        public Integrity ignoringGaps() {
            return new Integrity(this.blockedCells, 0, this.unknown);
        }
    }

    private FreeformFill() {
    }

    public static int probeLimit(int limit) {
        int valid = Math.clamp(limit, MIN_LIMIT, MAX_LIMIT);
        return Math.min(MAX_PROBE, Math.max(valid + 2, valid * 2 + 8));
    }

    public static int coordinate(int axis, int x, int y, int z) {
        return switch (axis) {
            case FreeformRegion.AXIS_X -> x;
            case FreeformRegion.AXIS_Y -> y;
            default -> z;
        };
    }

    private static double anchor(Result result, int component) {
        if (component == result.axis) {
            return result.plane + 0.5;
        }
        if (component == FreeformRegion.aAxis(result.axis)) {
            return result.minA + result.width * 0.5;
        }
        return result.minB + result.height * 0.5;
    }

    public static float facing(double lookX, double lookZ) {
        if (Math.abs(lookX) < 1.0E-6 && Math.abs(lookZ) < 1.0E-6) {
            return 0.0F;
        }
        return GateGeometry.snapYaw((float) Math.toDegrees(Math.atan2(-lookX, lookZ)));
    }

    public static Aim aim(int clickedX, int clickedY, int clickedZ, int faceX, int faceY, int faceZ, boolean clickedOpen, double lookX, double lookY,
                          double lookZ, boolean reverse) {
        int faceAxis = clickedOpen || faceY != 0 ? FreeformRegion.AXIS_Y : faceX != 0 ? FreeformRegion.AXIS_X : FreeformRegion.AXIS_Z;
        int seedX = clickedOpen ? clickedX : clickedX + faceX;
        int seedY = clickedOpen ? clickedY : clickedY + faceY;
        int seedZ = clickedOpen ? clickedZ : clickedZ + faceZ;
        double[] look = {lookX, lookY, lookZ};
        int best = -1;
        for (int axis : new int[]{FreeformRegion.AXIS_X, FreeformRegion.AXIS_Z, FreeformRegion.AXIS_Y}) {
            if (axis == faceAxis) {
                continue;
            }
            if (best < 0 || Math.abs(look[axis]) > Math.abs(look[best]) + 1.0E-9) {
                best = axis;
            }
        }
        boolean positive = look[best] >= 0.0;
        if (reverse) {
            positive = !positive;
        }
        return new Aim(best, positive ? 0 : 1, seedX, seedY, seedZ);
    }

    public static Cell cell(Probe probe, int axis, int plane, int a, int b) {
        return switch (axis) {
            case FreeformRegion.AXIS_X -> probe.at(plane, b, a);
            case FreeformRegion.AXIS_Y -> probe.at(a, plane, b);
            default -> probe.at(a, b, plane);
        };
    }

    public static Result fill(Probe probe, int axis, int seedX, int seedY, int seedZ, int limit) {
        int valid = Math.clamp(limit, MIN_LIMIT, MAX_LIMIT);
        int plane = coordinate(axis, seedX, seedY, seedZ);
        int seedA = coordinate(FreeformRegion.aAxis(axis), seedX, seedY, seedZ);
        int seedB = coordinate(FreeformRegion.bAxis(axis), seedX, seedY, seedZ);
        Cell first = cell(probe, axis, plane, seedA, seedB);
        if (first == Cell.UNLOADED) {
            return new Result(Status.UNLOADED, null, axis, plane, seedA, seedB, 1, 1, valid);
        }
        if (first != Cell.OPEN) {
            return new Result(Status.START_BLOCKED, null, axis, plane, seedA, seedB, 1, 1, valid);
        }
        int probeLimit = probeLimit(valid);
        Set<Long> visited = new HashSet<>();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        visited.add(key(seedA, seedB));
        queue.add(new long[]{seedA, seedB});
        int minA = seedA;
        int maxA = seedA;
        int minB = seedB;
        int maxB = seedB;
        boolean unloaded = false;
        while (!queue.isEmpty()) {
            long[] at = queue.poll();
            int a = (int) at[0];
            int b = (int) at[1];
            for (int dir = 0; dir < 4; dir++) {
                int na = a + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int nb = b + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                long k = key(na, nb);
                if (visited.contains(k)) {
                    continue;
                }
                Cell next = cell(probe, axis, plane, na, nb);
                if (next == Cell.SOLID) {
                    continue;
                }
                if (next == Cell.OUTSIDE) {
                    return new Result(Status.OPEN, null, axis, plane, minA, minB, maxA - minA + 1, maxB - minB + 1, valid);
                }
                if (next == Cell.UNLOADED) {
                    unloaded = true;
                    continue;
                }
                visited.add(k);
                minA = Math.min(minA, na);
                maxA = Math.max(maxA, na);
                minB = Math.min(minB, nb);
                maxB = Math.max(maxB, nb);
                if (maxA - minA + 1 > probeLimit || maxB - minB + 1 > probeLimit) {
                    return new Result(Status.OPEN, null, axis, plane, minA, minB, maxA - minA + 1, maxB - minB + 1, valid);
                }
                queue.add(new long[]{na, nb});
            }
        }
        int width = maxA - minA + 1;
        int height = maxB - minB + 1;
        if (unloaded) {
            return new Result(Status.UNLOADED, null, axis, plane, minA, minB, width, height, valid);
        }
        if (width > valid || height > valid) {
            return new Result(Status.TOO_BIG, null, axis, plane, minA, minB, width, height, valid);
        }
        boolean[] cells = new boolean[width * height];
        for (long k : visited) {
            int a = (int) (k >> 32);
            int b = (int) k;
            cells[(b - minB) * width + (a - minA)] = true;
        }
        FreeformRegion region = FreeformRegion.of(axis, width, height, seedA - minA, seedB - minB, cells);
        return new Result(region == null ? Status.START_BLOCKED : Status.OK, region, axis, plane, minA, minB, width, height, valid);
    }

    public static Result lenient(Probe probe, int axis, int seedX, int seedY, int seedZ, int limit) {
        Result strict = fill(probe, axis, seedX, seedY, seedZ, limit);
        if (strict.status() != Status.OPEN) {
            return strict;
        }
        int valid = Math.clamp(limit, MIN_LIMIT, MAX_LIMIT);
        int plane = coordinate(axis, seedX, seedY, seedZ);
        int seedA = coordinate(FreeformRegion.aAxis(axis), seedX, seedY, seedZ);
        int seedB = coordinate(FreeformRegion.bAxis(axis), seedX, seedY, seedZ);
        boolean[] unloaded = {false};
        Integer left = solidAlong(probe, axis, plane, seedA, seedB, -1, 0, valid, unloaded);
        Integer right = solidAlong(probe, axis, plane, seedA, seedB, 1, 0, valid, unloaded);
        Integer down = solidAlong(probe, axis, plane, seedA, seedB, 0, -1, valid, unloaded);
        Integer up = solidAlong(probe, axis, plane, seedA, seedB, 0, 1, valid, unloaded);
        Integer minA = left != null ? left : runEnd(probe, axis, plane, seedA, -1, true, down, up, valid, unloaded);
        Integer maxA = right != null ? right : runEnd(probe, axis, plane, seedA, 1, true, down, up, valid, unloaded);
        Integer minB = down != null ? down : runEnd(probe, axis, plane, seedB, -1, false, left, right, valid, unloaded);
        Integer maxB = up != null ? up : runEnd(probe, axis, plane, seedB, 1, false, left, right, valid, unloaded);
        if (unloaded[0]) {
            return new Result(Status.UNLOADED, null, axis, plane, seedA, seedB, 1, 1, valid);
        }
        if (minA == null || maxA == null || minB == null || maxB == null) {
            return strict;
        }
        int boxWidth = maxA - minA - 1;
        int boxHeight = maxB - minB - 1;
        if (boxWidth > valid || boxHeight > valid) {
            return new Result(Status.TOO_BIG, null, axis, plane, minA + 1, minB + 1, boxWidth, boxHeight, valid);
        }
        Set<Long> visited = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        visited.add(key(seedA, seedB));
        queue.add(new int[]{seedA, seedB});
        int lowA = seedA;
        int highA = seedA;
        int lowB = seedB;
        int highB = seedB;
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int dir = 0; dir < 4; dir++) {
                int na = at[0] + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int nb = at[1] + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if (na <= minA || na >= maxA || nb <= minB || nb >= maxB || visited.contains(key(na, nb))) {
                    continue;
                }
                Cell next = cell(probe, axis, plane, na, nb);
                if (next == Cell.UNLOADED) {
                    return new Result(Status.UNLOADED, null, axis, plane, seedA, seedB, 1, 1, valid);
                }
                if (next != Cell.OPEN) {
                    continue;
                }
                visited.add(key(na, nb));
                lowA = Math.min(lowA, na);
                highA = Math.max(highA, na);
                lowB = Math.min(lowB, nb);
                highB = Math.max(highB, nb);
                queue.add(new int[]{na, nb});
            }
        }
        int width = highA - lowA + 1;
        int height = highB - lowB + 1;
        boolean[] cells = new boolean[width * height];
        for (long k : visited) {
            int a = (int) (k >> 32);
            int b = (int) k;
            cells[(b - lowB) * width + (a - lowA)] = true;
        }
        FreeformRegion region = FreeformRegion.of(axis, width, height, seedA - lowA, seedB - lowB, cells);
        return new Result(region == null ? Status.START_BLOCKED : Status.OK, region, axis, plane, lowA, lowB, width, height, valid);
    }

    private static @Nullable Integer solidAlong(Probe probe, int axis, int plane, int a, int b, int da, int db, int limit, boolean[] unloaded) {
        for (int step = 1; step <= limit + 1; step++) {
            Cell cell = cell(probe, axis, plane, a + da * step, b + db * step);
            if (cell == Cell.SOLID) {
                return da != 0 ? a + da * step : b + db * step;
            }
            if (cell == Cell.UNLOADED) {
                unloaded[0] = true;
                return null;
            }
            if (cell == Cell.OUTSIDE) {
                return null;
            }
        }
        return null;
    }

    private static @Nullable Integer runEnd(Probe probe, int axis, int plane, int from, int direction, boolean alongA, @Nullable Integer line0,
                                            @Nullable Integer line1, int limit, boolean[] unloaded) {
        Integer best = null;
        for (Integer line : new Integer[]{line0, line1}) {
            if (line == null) {
                continue;
            }
            int end = from;
            for (int step = 1; step <= limit + 1; step++) {
                int at = from + direction * step;
                Cell cell = alongA ? cell(probe, axis, plane, at, line) : cell(probe, axis, plane, line, at);
                if (cell == Cell.UNLOADED) {
                    unloaded[0] = true;
                }
                if (cell != Cell.SOLID) {
                    break;
                }
                end = at;
            }
            int bound = end + direction;
            best = best == null ? bound : direction < 0 ? Math.min(best, bound) : Math.max(best, bound);
        }
        return best;
    }

    public static Integrity check(Probe probe, FreeformRegion region, int plane, int minA, int minB) {
        int axis = region.axis();
        int blocked = 0;
        int unknown = 0;
        for (int b = 0; b < region.height(); b++) {
            for (int a = 0; a < region.width(); a++) {
                if (!region.contains(a, b)) {
                    continue;
                }
                Cell cell = cell(probe, axis, plane, minA + a, minB + b);
                if (cell == Cell.SOLID) {
                    blocked++;
                } else if (cell == Cell.UNLOADED) {
                    unknown++;
                }
            }
        }
        int[] open = {0};
        int[] missing = {0};
        Set<Long> seen = new HashSet<>();
        region.edges((a0, b0, a1, b1, oa, ob) -> {
            if (!seen.add(key(oa, ob))) {
                return;
            }
            Cell cell = cell(probe, axis, plane, minA + oa, minB + ob);
            if (cell == Cell.OPEN || cell == Cell.OUTSIDE) {
                open[0]++;
            } else if (cell == Cell.UNLOADED) {
                missing[0]++;
            }
        });
        return new Integrity(blocked, open[0], unknown + missing[0]);
    }

    public static boolean outsideOpen(Probe probe, FreeformRegion region, int plane, int minA, int minB, int outsideA, int outsideB) {
        Cell cell = cell(probe, region.axis(), plane, minA + outsideA, minB + outsideB);
        return cell == Cell.OPEN || cell == Cell.OUTSIDE;
    }

    private static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }
}
