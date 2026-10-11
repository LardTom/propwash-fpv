package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.List;

public final class RaceLine {
    public static final int ENDLESS = Integer.MAX_VALUE;
    public static final int MIN_AHEAD = 1;
    public static final int MAX_AHEAD = 8;
    public static final int DEFAULT_AHEAD = 3;
    public static final int BEHIND = 1;
    public static final double SAMPLE_STEP = 0.4;
    public static final int MAX_STEPS = 320;
    public static final double TANGENT_SCALE = 0.8;
    public static final double LEAD_IN_TANGENT = 0.5;
    public static final double LEAD_IN_FADE = 3.5;
    public static final double TAIL = 0.45;
    public static final double HIGHLIGHT_AFTER = 0.2;
    public static final float BEHIND_ALPHA = 0.3F;
    public static final float FAR_DIM = 0.35F;
    public static final double OFF_COURSE_DISTANCE = 16.0;
    public static final double APPROACH_RANGE = 48.0;
    public static final double DETOUR_PLANE = 1.0;
    public static final double DETOUR_CLEARANCE = 2.0;
    public static final double DETOUR_TANGENT = 1.6;
    public static final double DETOUR_MIN = 3.0;
    public static final double INTERNAL_SHARE = 0.5;
    public static final double INTERNAL_TANGENT = 1.15;
    private static final double EPSILON = 1.0E-6;

    private RaceLine() {
    }

    public record Sample(double x, double y, double z, double distance, double gate, float alpha, float highlight) {
    }

    public record Path(List<Sample> ahead, List<Sample> behind) {
        public static final Path EMPTY = new Path(List.of(), List.of());

        public boolean isEmpty() {
            return this.ahead.isEmpty() && this.behind.isEmpty();
        }
    }

    public static int clampAhead(int ahead) {
        return Math.max(MIN_AHEAD, Math.min(MAX_AHEAD, ahead));
    }

    public static int remainingGates(int gateCount, int lap, int lapTarget, int nextGate, boolean holeshot) {
        if (gateCount <= 0 || nextGate < 0 || lap <= 0) {
            return 0;
        }
        if (lapTarget <= 0) {
            return ENDLESS;
        }
        int laterLaps = Math.max(0, lapTarget - lap);
        if (holeshot) {
            return 1 + (laterLaps + 1) * gateCount;
        }
        int thisLap = nextGate == 0 ? 1 : gateCount - nextGate + 1;
        return thisLap + laterLaps * gateCount;
    }

    public static int passedGates(int gateCount, int lap, int nextGate, boolean holeshot) {
        if (gateCount <= 0 || nextGate < 0 || lap <= 0 || holeshot) {
            return 0;
        }
        return (lap - 1) * gateCount + (nextGate == 0 ? gateCount : nextGate);
    }

    public static List<Integer> order(int gateCount, int nextGate, int count) {
        List<Integer> order = new ArrayList<>(Math.max(0, count));
        if (gateCount <= 0 || nextGate < 0) {
            return order;
        }
        for (int i = 0; i < count; i++) {
            order.add(Math.floorMod(nextGate + i, gateCount));
        }
        return order;
    }

    public static Path of(LapTracker tracker, int ahead, double px, double py, double pz) {
        if (!tracker.racing()) {
            return Path.EMPTY;
        }
        List<GateGeometry> gates = tracker.gates();
        int count = gates.size();
        int next = tracker.nextGate();
        boolean holeshot = tracker.holeshot();
        int remaining = remainingGates(count, tracker.lap(), tracker.lapTarget(), next, holeshot);
        int passed = passedGates(count, tracker.lap(), next, holeshot);
        return build(gates, next, ahead, remaining, passed, px, py, pz);
    }

    public static Path approach(LapTracker tracker, int ahead, double px, double py, double pz) {
        LapTracker.Phase phase = tracker.phase();
        if (phase != LapTracker.Phase.IDLE && phase != LapTracker.Phase.COUNTDOWN) {
            return Path.EMPTY;
        }
        List<GateGeometry> gates = tracker.gates();
        if (gates.isEmpty()) {
            return Path.EMPTY;
        }
        GateGeometry start = gates.getFirst();
        if (distance(px, py, pz, start.centerX(), start.centerY(), start.centerZ()) > APPROACH_RANGE) {
            return Path.EMPTY;
        }
        int remaining = remainingGates(gates.size(), 1, tracker.lapTarget(), 0, true);
        return build(gates, 0, ahead, remaining, 0, px, py, pz);
    }

    public static Path build(List<GateGeometry> gates, int nextGate, int ahead, int remaining, int passed, double px, double py, double pz) {
        int count = gates.size();
        if (count == 0 || nextGate < 0 || nextGate >= count || remaining <= 0) {
            return Path.EMPTY;
        }
        if (distanceToCourse(gates, px, py, pz) > OFF_COURSE_DISTANCE) {
            return Path.EMPTY;
        }
        int shown = Math.min(clampAhead(ahead), remaining);
        boolean tail = remaining > shown && count > 1;
        List<Sample> forward = new ArrayList<>();
        GateGeometry first = gates.get(nextGate);
        double[] cursor = {0.0};
        if (first.gate()) {
            double chord = distance(px, py, pz, first.centerX(), first.centerY(), first.centerZ());
            double lead = Math.max(chord, 1.0) * TANGENT_SCALE;
            segment(forward, cursor, new Piece(px, py, pz, (first.centerX() - px) * LEAD_IN_TANGENT, (first.centerY() - py) * LEAD_IN_TANGENT,
                    (first.centerZ() - pz) * LEAD_IN_TANGENT, first.centerX(), first.centerY(), first.centerZ(), first.normalX() * lead,
                    first.normalY() * lead, first.normalZ() * lead, 0.0, 1.0), -1.0, 1.0, shown, tail, true);
        } else {
            List<GateGeometry.Waypoint> points = first.waypoints();
            GateGeometry.Waypoint entry = points.getFirst();
            double internal = points.size() > 1 ? INTERNAL_SHARE : 0.0;
            double chord = distance(px, py, pz, entry.x(), entry.y(), entry.z());
            double lead = Math.max(chord, 1.0) * TANGENT_SCALE;
            segment(forward, cursor, new Piece(px, py, pz, (entry.x() - px) * LEAD_IN_TANGENT, (entry.y() - py) * LEAD_IN_TANGENT,
                    (entry.z() - pz) * LEAD_IN_TANGENT, entry.x(), entry.y(), entry.z(), entry.dx() * lead, entry.dy() * lead, entry.dz() * lead,
                    0.0, 1.0 - internal), -1.0, 1.0, shown, tail, true);
            for (Piece piece : internalPieces(points, 1.0 - internal, internal)) {
                segment(forward, cursor, piece, -1.0, 1.0, shown, tail, true);
            }
        }
        List<Integer> order = order(count, nextGate, shown + (tail ? 1 : 0));
        if (count > 1) {
            for (int k = 0; k + 1 < order.size(); k++) {
                GateGeometry a = gates.get(order.get(k));
                GateGeometry b = gates.get(order.get(k + 1));
                boolean isTail = tail && k + 1 == order.size() - 1;
                double until = isTail ? TAIL : 1.0;
                for (Piece piece : pieces(a, b)) {
                    double local = (until - piece.from) / piece.span;
                    if (local > EPSILON) {
                        segment(forward, cursor, piece, k, Math.min(1.0, local), shown, tail, false);
                    }
                }
            }
        }
        List<Sample> behind = new ArrayList<>();
        int back = count > 1 ? Math.min(BEHIND, Math.max(0, passed - 1)) : 0;
        if (back > 0) {
            double[] behindCursor = {0.0};
            for (int j = back; j >= 1; j--) {
                GateGeometry a = gates.get(Math.floorMod(nextGate - j - 1, count));
                GateGeometry b = gates.get(Math.floorMod(nextGate - j, count));
                for (Piece piece : pieces(a, b)) {
                    behindSegment(behind, behindCursor, piece, j, back);
                }
            }
        }
        return new Path(List.copyOf(forward), List.copyOf(behind));
    }

    record Piece(double x0, double y0, double z0, double m0x, double m0y, double m0z, double x1, double y1, double z1, double m1x,
                 double m1y, double m1z, double from, double span) {
        double polygon() {
            return distance(this.x0, this.y0, this.z0, this.x0 + this.m0x / 3.0, this.y0 + this.m0y / 3.0, this.z0 + this.m0z / 3.0)
                    + distance(this.x0 + this.m0x / 3.0, this.y0 + this.m0y / 3.0, this.z0 + this.m0z / 3.0,
                    this.x1 - this.m1x / 3.0, this.y1 - this.m1y / 3.0, this.z1 - this.m1z / 3.0)
                    + distance(this.x1 - this.m1x / 3.0, this.y1 - this.m1y / 3.0, this.z1 - this.m1z / 3.0, this.x1, this.y1, this.z1);
        }

        double[] at(double t) {
            double t2 = t * t;
            double t3 = t2 * t;
            double h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
            double h10 = t3 - 2.0 * t2 + t;
            double h01 = -2.0 * t3 + 3.0 * t2;
            double h11 = t3 - t2;
            return new double[]{
                    h00 * this.x0 + h10 * this.m0x + h01 * this.x1 + h11 * this.m1x,
                    h00 * this.y0 + h10 * this.m0y + h01 * this.y1 + h11 * this.m1y,
                    h00 * this.z0 + h10 * this.m0z + h01 * this.z1 + h11 * this.m1z};
        }
    }

    public static boolean needsDetour(GateGeometry a, GateGeometry b) {
        double dot = a.normalX() * b.normalX() + a.normalY() * b.normalY() + a.normalZ() * b.normalZ();
        if (dot < 0.5) {
            return false;
        }
        double dx = b.centerX() - a.centerX();
        double dy = b.centerY() - a.centerY();
        double dz = b.centerZ() - a.centerZ();
        double ahead = a.upright() ? dx * a.normalX() + dz * a.normalZ() : dx * a.normalX() + dy * a.normalY() + dz * a.normalZ();
        double side = a.upright() ? dx * a.sideX() + dz * a.sideZ() : dx * a.sideX() + dy * a.sideY() + dz * a.sideZ();
        double up = a.upright() ? dy : dx * a.upX() + dy * a.upY() + dz * a.upZ();
        return Math.abs(ahead) < DETOUR_PLANE && Math.abs(side) < (a.width() + b.width()) * 0.5 && Math.abs(up) > EPSILON;
    }

    public static double[] detourPoint(GateGeometry a, GateGeometry b) {
        double reach = Math.max(a.width(), b.width()) * 0.5 + DETOUR_CLEARANCE;
        double mx = (a.centerX() + b.centerX()) * 0.5;
        double my = (a.centerY() + b.centerY()) * 0.5;
        double mz = (a.centerZ() + b.centerZ()) * 0.5;
        if (a.upright()) {
            return new double[]{mx + a.sideX() * reach, my, mz + a.sideZ() * reach};
        }
        return new double[]{mx + a.sideX() * reach, my + a.sideY() * reach, mz + a.sideZ() * reach};
    }

    static List<Piece> pieces(GateGeometry a, GateGeometry b) {
        if (!a.gate() || !b.gate()) {
            return elementPieces(a, b);
        }
        double ax = a.centerX();
        double ay = a.centerY();
        double az = a.centerZ();
        double bx = b.centerX();
        double by = b.centerY();
        double bz = b.centerZ();
        double chord = distance(ax, ay, az, bx, by, bz);
        if (chord < EPSILON) {
            return List.of();
        }
        if (!needsDetour(a, b)) {
            double m = chord * TANGENT_SCALE;
            return List.of(new Piece(ax, ay, az, a.normalX() * m, a.normalY() * m, a.normalZ() * m,
                    bx, by, bz, b.normalX() * m, b.normalY() * m, b.normalZ() * m, 0.0, 1.0));
        }
        double[] v = detourPoint(a, b);
        double first = distance(ax, ay, az, v[0], v[1], v[2]);
        double second = distance(v[0], v[1], v[2], bx, by, bz);
        double m0 = Math.max(first, DETOUR_MIN) * DETOUR_TANGENT;
        double m1 = Math.max(second, DETOUR_MIN) * DETOUR_TANGENT;
        double nx = a.normalX();
        double ny = a.normalY();
        double nz = a.normalZ();
        return List.of(
                new Piece(ax, ay, az, nx * m0, ny * m0, nz * m0, v[0], v[1], v[2], -nx * m0, -ny * m0, -nz * m0, 0.0, 0.5),
                new Piece(v[0], v[1], v[2], -nx * m1, -ny * m1, -nz * m1, bx, by, bz, b.normalX() * m1, b.normalY() * m1, b.normalZ() * m1,
                        0.5, 0.5));
    }

    static List<Piece> elementPieces(GateGeometry a, GateGeometry b) {
        GateGeometry.Waypoint from = a.waypoints().getLast();
        List<GateGeometry.Waypoint> points = b.waypoints();
        GateGeometry.Waypoint entry = points.getFirst();
        double internal = points.size() > 1 ? INTERNAL_SHARE : 0.0;
        List<Piece> out = new ArrayList<>(points.size());
        if (distance(from.x(), from.y(), from.z(), entry.x(), entry.y(), entry.z()) >= EPSILON) {
            out.add(hermite(from, entry, 0.0, 1.0 - internal, TANGENT_SCALE));
        }
        out.addAll(internalPieces(points, 1.0 - internal, internal));
        return out;
    }

    static List<Piece> internalPieces(List<GateGeometry.Waypoint> points, double start, double share) {
        int count = points.size() - 1;
        List<Piece> out = new ArrayList<>(Math.max(0, count));
        for (int j = 0; j < count; j++) {
            out.add(hermite(points.get(j), points.get(j + 1), start + share * j / count, share / count, INTERNAL_TANGENT));
        }
        return out;
    }

    private static Piece hermite(GateGeometry.Waypoint a, GateGeometry.Waypoint b, double from, double span, double scale) {
        double m = distance(a.x(), a.y(), a.z(), b.x(), b.y(), b.z()) * scale;
        return new Piece(a.x(), a.y(), a.z(), a.dx() * m, a.dy() * m, a.dz() * m, b.x(), b.y(), b.z(), b.dx() * m, b.dy() * m, b.dz() * m, from,
                span);
    }

    private static void segment(List<Sample> out, double[] cursor, Piece piece, double gateOffset, double until, int shown, boolean tail,
                                boolean leadIn) {
        int steps = Math.max(2, Math.min(MAX_STEPS, (int) Math.ceil(piece.polygon() * until / SAMPLE_STEP)));
        int start = out.isEmpty() ? 0 : 1;
        double lastX = out.isEmpty() ? piece.x0 : out.getLast().x();
        double lastY = out.isEmpty() ? piece.y0 : out.getLast().y();
        double lastZ = out.isEmpty() ? piece.z0 : out.getLast().z();
        for (int i = start; i <= steps; i++) {
            double t = until * i / steps;
            double[] p = piece.at(t);
            cursor[0] += distance(lastX, lastY, lastZ, p[0], p[1], p[2]);
            lastX = p[0];
            lastY = p[1];
            lastZ = p[2];
            double gate = gateOffset + piece.from + piece.span * t;
            float alpha = leadIn ? (float) smooth(cursor[0] / LEAD_IN_FADE) : 1.0F;
            alpha *= aheadFade(gate, shown, tail);
            out.add(new Sample(p[0], p[1], p[2], cursor[0], gate, alpha, highlight(gate)));
        }
    }

    private static void behindSegment(List<Sample> out, double[] cursor, Piece piece, int j, int back) {
        int steps = Math.max(2, Math.min(MAX_STEPS, (int) Math.ceil(piece.polygon() / SAMPLE_STEP)));
        int start = out.isEmpty() ? 0 : 1;
        double lastX = out.isEmpty() ? piece.x0 : out.getLast().x();
        double lastY = out.isEmpty() ? piece.y0 : out.getLast().y();
        double lastZ = out.isEmpty() ? piece.z0 : out.getLast().z();
        for (int i = start; i <= steps; i++) {
            double t = (double) i / steps;
            double[] p = piece.at(t);
            cursor[0] += distance(lastX, lastY, lastZ, p[0], p[1], p[2]);
            lastX = p[0];
            lastY = p[1];
            lastZ = p[2];
            double gate = -(j + 1) + piece.from + piece.span * t;
            double fromFar = (gate + back + 1) / back;
            float alpha = BEHIND_ALPHA * (float) smooth(fromFar);
            out.add(new Sample(p[0], p[1], p[2], cursor[0], gate, alpha, 0.0F));
        }
    }

    static float aheadFade(double gate, int shown, boolean tail) {
        if (gate <= 0.0) {
            return 1.0F;
        }
        double span = Math.max(1.0, shown);
        float dim = 1.0F - FAR_DIM * (float) Math.min(1.0, gate / span);
        if (tail && gate > shown - 1) {
            dim *= (float) (1.0 - smooth((gate - (shown - 1)) / TAIL));
        }
        return Math.max(0.0F, dim);
    }

    public static float highlight(double gate) {
        if (gate <= 0.0) {
            return 1.0F;
        }
        return (float) (1.0 - smooth(gate / HIGHLIGHT_AFTER));
    }

    public static double distanceToCourse(List<GateGeometry> gates, double px, double py, double pz) {
        int count = gates.size();
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            GateGeometry a = gates.get(i);
            GateGeometry b = gates.get((i + 1) % count);
            double ax = a.centerX();
            double ay = a.centerY();
            double az = a.centerZ();
            double dx = b.centerX() - ax;
            double dy = b.centerY() - ay;
            double dz = b.centerZ() - az;
            double length = dx * dx + dy * dy + dz * dz;
            double t = length < EPSILON ? 0.0 : Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / length));
            best = Math.min(best, distance(px, py, pz, ax + dx * t, ay + dy * t, az + dz * t));
        }
        return best;
    }

    public static double smooth(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double distance(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
