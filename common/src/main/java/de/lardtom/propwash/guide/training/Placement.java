package de.lardtom.propwash.guide.training;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class Placement {
    public static final double[] TURNS = {0.0, 90.0, -90.0, 180.0};
    public static final double MIN_PROBE_Y = 0.2;
    public static final double PAD_SEARCH_UP = 1.0;
    public static final double PAD_SEARCH_DOWN = 1.5;
    public static final double CELL_MARGIN = 0.5;
    public static final int MAX_COUNT = 999;

    public enum Status {
        OK,
        BLOCKED,
        NO_GROUND,
        NOT_LOADED;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public interface Terrain {
        boolean loaded(int x, int z);

        double solidBottom(int x, int y, int z);

        double solidTop(int x, int y, int z);
    }

    public record Result(Status status, @Nullable Course course, int blocked, double turn, int blockedX, int blockedY, int blockedZ) {
        public boolean ok() {
            return this.status == Status.OK && this.course != null;
        }
    }

    private Placement() {
    }

    public static Result place(Lesson lesson, double x, double y, double z, float yaw, TrainingScale scale, Terrain terrain) {
        Result best = null;
        for (double turn : TURNS) {
            TrainingFrame frame = new TrainingFrame(x, y, z, TrainingFrame.wrap(yaw + turn), scale.geometry());
            Result result = check(lesson, frame, scale, terrain, turn);
            if (result.ok()) {
                return result;
            }
            if (best == null || rank(result) < rank(best)) {
                best = result;
            }
        }
        return best;
    }

    private static int rank(Result result) {
        return switch (result.status()) {
            case OK -> -1;
            case BLOCKED -> result.blocked();
            case NO_GROUND -> MAX_COUNT + 1;
            case NOT_LOADED -> MAX_COUNT + 2;
        };
    }

    public static Result check(Lesson lesson, TrainingFrame frame, TrainingScale scale, Terrain terrain, double turn) {
        double[] padHeights = new double[lesson.pads().size()];
        padHeights[0] = frame.y();
        for (int i = 1; i < padHeights.length; i++) {
            Pad pad = lesson.pads().get(i);
            double wx = frame.worldX(pad.x(), pad.z());
            double wz = frame.worldZ(pad.x(), pad.z());
            int bx = floor(wx);
            int bz = floor(wz);
            if (!terrain.loaded(bx, bz)) {
                return new Result(Status.NOT_LOADED, null, 0, turn, bx, floor(frame.y()), bz);
            }
            double surface = surface(terrain, bx, bz, frame.y() + PAD_SEARCH_UP, frame.y() - PAD_SEARCH_DOWN);
            if (!Double.isFinite(surface)) {
                return new Result(Status.NO_GROUND, null, 0, turn, bx, floor(frame.y()), bz);
            }
            padHeights[i] = surface;
        }
        Set<Long> seen = new HashSet<>();
        int[] first = null;
        int blocked = 0;
        double floor = frame.worldY(MIN_PROBE_Y);
        for (Lesson.Probe probe : lesson.airspace()) {
            double reach = frame.length(Math.hypot(probe.halfX(), probe.halfZ())) + CELL_MARGIN;
            double cx = frame.worldX(probe.x(), probe.z());
            double cz = frame.worldZ(probe.x(), probe.z());
            double bottom = Math.max(frame.worldY(probe.y() - probe.halfY()), floor);
            double top = frame.worldY(probe.y() + probe.halfY());
            if (top <= bottom) {
                continue;
            }
            for (int bx = floor(cx - reach); bx <= floor(cx + reach); bx++) {
                for (int bz = floor(cz - reach); bz <= floor(cz + reach); bz++) {
                    if (!inside(probe, frame, bx + 0.5, bz + 0.5)) {
                        continue;
                    }
                    if (!terrain.loaded(bx, bz)) {
                        return new Result(Status.NOT_LOADED, null, 0, turn, bx, floor(bottom), bz);
                    }
                    for (int by = floor(bottom); by <= floor(top); by++) {
                        long key = key(bx, by, bz);
                        if (!seen.add(key)) {
                            continue;
                        }
                        double low = terrain.solidBottom(bx, by, bz);
                        double high = terrain.solidTop(bx, by, bz);
                        if (!Double.isFinite(low) || !Double.isFinite(high)) {
                            continue;
                        }
                        if (by + high > bottom && by + low < top) {
                            if (first == null) {
                                first = new int[]{bx, by, bz};
                            }
                            blocked++;
                            if (blocked >= MAX_COUNT) {
                                return new Result(Status.BLOCKED, null, blocked, turn, first[0], first[1], first[2]);
                            }
                        }
                    }
                }
            }
        }
        for (int i = 1; i < padHeights.length; i++) {
            Pad pad = lesson.pads().get(i);
            double wx = frame.worldX(pad.x(), pad.z());
            double wz = frame.worldZ(pad.x(), pad.z());
            double r = frame.length(pad.radius());
            double bottom = padHeights[i] + 0.05;
            double top = padHeights[i] + frame.length(Lesson.PAD_CLEARANCE);
            for (int bx = floor(wx - r); bx <= floor(wx + r); bx++) {
                for (int bz = floor(wz - r); bz <= floor(wz + r); bz++) {
                    if (Math.hypot(bx + 0.5 - wx, bz + 0.5 - wz) > r + CELL_MARGIN) {
                        continue;
                    }
                    if (!terrain.loaded(bx, bz)) {
                        return new Result(Status.NOT_LOADED, null, 0, turn, bx, floor(bottom), bz);
                    }
                    for (int by = floor(bottom); by <= floor(top); by++) {
                        double low = terrain.solidBottom(bx, by, bz);
                        double high = terrain.solidTop(bx, by, bz);
                        if (Double.isFinite(low) && Double.isFinite(high) && by + high > bottom && by + low < top) {
                            if (first == null) {
                                first = new int[]{bx, by, bz};
                            }
                            blocked++;
                        }
                    }
                }
            }
        }
        if (blocked > 0) {
            return new Result(Status.BLOCKED, null, blocked, turn, first[0], first[1], first[2]);
        }
        return new Result(Status.OK, new Course(lesson, frame, scale, padHeights), 0, turn, 0, 0, 0);
    }

    private static boolean inside(Lesson.Probe probe, TrainingFrame frame, double wx, double wz) {
        double lx = frame.localX(wx, wz);
        double lz = frame.localZ(wx, wz);
        double margin = CELL_MARGIN / frame.scale();
        double dx = lx - probe.x();
        double dz = lz - probe.z();
        double c = Math.cos(Math.toRadians(probe.heading()));
        double s = Math.sin(Math.toRadians(probe.heading()));
        double side = dx * c - dz * s;
        double along = dx * s + dz * c;
        return Math.abs(side) <= probe.halfX() + margin && Math.abs(along) <= probe.halfZ() + margin;
    }

    public static double surface(Terrain terrain, int x, int z, double top, double bottom) {
        for (int by = floor(top); by >= floor(bottom); by--) {
            double low = terrain.solidBottom(x, by, z);
            double high = terrain.solidTop(x, by, z);
            if (!Double.isFinite(low) || !Double.isFinite(high)) {
                continue;
            }
            double surface = by + high;
            if (surface <= top + 1.0E-6 && surface >= bottom - 1.0E-6) {
                return surface;
            }
            return Double.NaN;
        }
        return Double.NaN;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }
}
