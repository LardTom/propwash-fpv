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
