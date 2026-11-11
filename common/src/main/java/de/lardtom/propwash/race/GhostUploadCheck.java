package de.lardtom.propwash.race;

import de.lardtom.propwash.drone.net.StateValidation;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class GhostUploadCheck {
    public static final long BASE_TOLERANCE_NANOS = 100_000_000L;
    public static final long MAX_TOLERANCE_NANOS = 1_500_000_000L;
    public static final long MAX_PING_NANOS = 300_000_000L;
    public static final long MIN_SPACING_NANOS = 10_000_000L;
    public static final long MIN_INTERVAL_NANOS = 5_000_000L;
    public static final long MAX_INTERVAL_NANOS = GhostTrack.INTERVAL_NANOS;
    public static final long DURATION_SLACK_NANOS = 5_000_000L;
    public static final long ALIGN_STEP_NANOS = 10_000_000L;
    public static final double POSITION_TOLERANCE = 1.5;
    public static final double MAX_SPEED = StateValidation.PHYSICS_MAX_SPEED * StateValidation.SPEED_TOLERANCE;
    public static final double STEP_SLACK = 0.1;
    public static final double OUTLIER_SHARE = 0.02;
    public static final long MAX_OUTLIER_RUN_NANOS = 400_000_000L;
    public static final double MIN_LENGTH_SHARE = 0.95;
    public static final double MAX_LENGTH_SHARE = 1.25;
    public static final double LENGTH_SLACK = 5.0;
    public static final GateOpenings LENIENT = gate -> true;

    public enum Result {
        ACCEPTED,
        MISSING,
        FORMAT,
        DURATION,
        SPEED,
        START,
        GATES,
        FINISH,
        LENGTH,
        PATH;

        public boolean accepted() {
            return this == ACCEPTED;
        }
    }

    public record Reference(long[] times, double[] positions, long lapNanos, long penaltyNanos, long serverLapNanos, long[] splits, long pingNanos) {
        public Reference {
            pingNanos = Math.clamp(pingNanos, 0L, MAX_PING_NANOS);
            times = times.clone();
            positions = positions.clone();
            splits = splits.clone();
            if (positions.length != times.length * 3) {
                throw new IllegalArgumentException("Server-Spur mit " + times.length + " Zeiten und " + positions.length / 3 + " Punkten");
            }
        }

        public static Reference of(GhostRecorder.Raw raw, long startNanos, long endNanos, @Nullable GhostTrack serverTrack, List<GateGeometry> gates,
                                   long lapNanos, long penaltyNanos, long pingNanos) {
            long[] times = new long[raw.count()];
            for (int i = 0; i < times.length; i++) {
                times[i] = raw.times()[i] - startNanos;
            }
            long[] splits = serverTrack == null ? new long[0] : serverTrack.splits(gates, LENIENT);
            return new Reference(times, raw.positions(), lapNanos, penaltyNanos, endNanos - startNanos, splits, Math.clamp(pingNanos, 0L, MAX_PING_NANOS));
        }

        public int count() {
            return this.times.length;
        }

        public long spacing() {
            if (this.times.length < 2) {
                return MIN_SPACING_NANOS;
            }
            long[] gaps = new long[this.times.length - 1];
            for (int i = 1; i < this.times.length; i++) {
                gaps[i - 1] = this.times[i] - this.times[i - 1];
            }
            Arrays.sort(gaps);
            return Math.max(MIN_SPACING_NANOS, gaps[gaps.length / 2]);
        }

        public long tolerance() {
            return Math.min(MAX_TOLERANCE_NANOS, BASE_TOLERANCE_NANOS + this.pingNanos + 2L * this.spacing());
        }

        long gap(int k) {
            return k > 0 && k < this.times.length ? Math.max(0L, this.times[k] - this.times[k - 1]) : 0L;
        }

        long staleNear(long[] stale, long time, long window) {
            long best = 0L;
            for (int k = 1; k < this.times.length; k++) {
                if (this.times[k - 1] <= time + window && this.times[k] >= time - window) {
                    best = Math.max(best, Math.max(stale[k - 1], stale[k]));
                }
            }
            return Math.min(best, MAX_TOLERANCE_NANOS);
        }

        long[] staleness() {
            long[] stale = new long[this.times.length];
            for (int j = 1; j < this.times.length; j++) {
                stale[j] = Math.max(stale[j], this.gap(j));
                long pause = Math.min(this.gap(j), MAX_TOLERANCE_NANOS);
                for (int k = j + 1; k < this.times.length && this.times[k] - this.times[j] <= pause; k++) {
                    stale[k] = Math.max(stale[k], pause);
                }
            }
            return stale;
        }

        long maxGap() {
            long best = 0L;
            for (int k = 1; k < this.times.length; k++) {
                best = Math.max(best, this.times[k] - this.times[k - 1]);
            }
            return Math.min(best, MAX_TOLERANCE_NANOS);
        }

        double x(int k) {
            return this.positions[k * 3];
        }

        double y(int k) {
            return this.positions[k * 3 + 1];
        }

        double z(int k) {
            return this.positions[k * 3 + 2];
        }
    }

    private GhostUploadCheck() {
    }

    public static Result check(Reference ref, GhostTrack track, List<GateGeometry> gates, boolean pointToPoint) {
        long interval = track.intervalNanos();
        if (track.samples() < 2 || interval < MIN_INTERVAL_NANOS || interval > MAX_INTERVAL_NANOS || ref.count() < 2 || gates.isEmpty()
                || track.leadNanos() > GhostRecorder.LEAD_NANOS + interval) {
            return Result.FORMAT;
        }
        for (int i = 0; i < track.samples(); i++) {
            if (!Double.isFinite(track.x(i)) || !Double.isFinite(track.y(i)) || !Double.isFinite(track.z(i))) {
                return Result.FORMAT;
            }
        }
        long duration = track.durationNanos();
        long flown = ref.lapNanos() - ref.penaltyNanos();
        if (Math.abs(duration - flown) > 2L * interval + DURATION_SLACK_NANOS) {
            return Result.DURATION;
        }
        double step = MAX_SPEED * interval * 1.0E-9 + STEP_SLACK;
        for (int i = 1; i < track.samples(); i++) {
            if (distance(track.x(i - 1), track.y(i - 1), track.z(i - 1), track.x(i), track.y(i), track.z(i)) > step) {
                return Result.SPEED;
            }
        }
        long tol = ref.tolerance();
        long offset = align(ref, track, ref.serverLapNanos() - duration, tol + RaceServer.crossingTolerance(ref.serverLapNanos()));
        if (!crosses(track, gates.getFirst(), 0, -offset - tol, -offset + tol, false)) {
            return Result.START;
        }
        long[] stale = ref.staleness();
        long[] splits = track.splits(gates, LENIENT);
        for (int gate = 1; gate < gates.size(); gate++) {
            long server = gate < ref.splits().length ? ref.splits()[gate] : LapTracker.NONE;
            if (server < 0L) {
                continue;
            }
            if (splits[gate] < 0L || Math.abs(splits[gate] + offset - server) > tol + ref.staleNear(stale, server, tol)) {
                return Result.GATES;
            }
        }
        GateGeometry finish = pointToPoint ? gates.getLast() : gates.getFirst();
        int finishStage = pointToPoint ? Math.max(0, finish.stageCount() - 1) : 0;
        if (!crosses(track, finish, finishStage, duration - tol, duration, true)) {
            return Result.FINISH;
        }
        if (!lengthMatches(ref, track, offset)) {
            return Result.LENGTH;
        }
        if (!followsServer(ref, track, offset, tol, stale) || !staysNearServer(ref, track, offset, tol, stale)) {
            return Result.PATH;
        }
        return Result.ACCEPTED;
    }

    static long align(Reference ref, GhostTrack track, long guess, long range) {
        long best = guess;
        double bestScore = Double.MAX_VALUE;
        double[] at = new double[3];
        double[] errors = new double[ref.count()];
        for (long offset = guess - range; offset <= guess + range; offset += ALIGN_STEP_NANOS) {
            int used = 0;
            for (int k = 0; k < ref.count(); k++) {
                long c = ref.times()[k] - offset;
                if (c < track.beginNanos() || c > track.durationNanos()) {
                    continue;
                }
                position(track, c, at);
                errors[used++] = distance(at[0], at[1], at[2], ref.x(k), ref.y(k), ref.z(k));
            }
            if (used < 2) {
                continue;
            }
            Arrays.sort(errors, 0, used);
            double score = errors[used / 2] + Math.abs(offset - guess) * 1.0E-12;
            if (score < bestScore) {
                bestScore = score;
                best = offset;
            }
        }
        return best;
    }

    static void position(GhostTrack track, long time, double[] out) {
        long at = Math.max(0L, time + track.leadNanos());
        int i = (int) Math.min(track.samples() - 1L, at / track.intervalNanos());
        int j = Math.min(track.samples() - 1, i + 1);
        double f = j == i ? 0.0 : Math.min(1.0, (at - (long) i * track.intervalNanos()) / (double) track.intervalNanos());
        out[0] = track.x(i) + (track.x(j) - track.x(i)) * f;
        out[1] = track.y(i) + (track.y(j) - track.y(i)) * f;
        out[2] = track.z(i) + (track.z(j) - track.z(i)) * f;
    }

    private static int index(GhostTrack track, long time, boolean up) {
        double at = (time + track.leadNanos()) / (double) track.intervalNanos();
        long i = up ? (long) Math.ceil(at) : (long) Math.floor(at);
        return (int) Math.max(0L, Math.min(track.samples() - 1L, i));
    }

    static boolean crosses(GhostTrack track, GateGeometry geometry, int stage, long from, long to, boolean extend) {
        int first = Math.max(1, index(track, from, false));
        int last = index(track, to, true);
        for (int i = first; i <= last; i++) {
            if (crossing(geometry, stage, track.x(i - 1), track.y(i - 1), track.z(i - 1), track.x(i), track.y(i), track.z(i))) {
                return true;
            }
        }
        if (extend && last == track.samples() - 1 && last >= 1) {
            double x = track.x(last);
            double y = track.y(last);
            double z = track.z(last);
            return crossing(geometry, stage, x, y, z, 2.0 * x - track.x(last - 1), 2.0 * y - track.y(last - 1), 2.0 * z - track.z(last - 1))
                    || crossing(geometry, stage, track.x(last - 1), track.y(last - 1), track.z(last - 1), 2.0 * x - track.x(last - 1),
                    2.0 * y - track.y(last - 1), 2.0 * z - track.z(last - 1));
        }
        return false;
    }

    private static boolean crossing(GateGeometry geometry, int stage, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (geometry.stageCrossing(stage, x0, y0, z0, x1, y1, z1) >= 0.0) {
            return true;
        }
        return geometry.gate() && geometry.crossing(x0, y0, z0, x1, y1, z1, true, true) >= 0.0;
    }

    private static boolean lengthMatches(Reference ref, GhostTrack track, long offset) {
        long from = Math.max(track.beginNanos(), ref.times()[0] - offset);
        long to = Math.min(track.durationNanos(), ref.times()[ref.count() - 1] - offset);
        if (to <= from) {
            return false;
        }
        double server = 0.0;
        for (int k = 1; k < ref.count(); k++) {
            long c = ref.times()[k] - offset;
            if (c > from && c <= to) {
                server += distance(ref.x(k - 1), ref.y(k - 1), ref.z(k - 1), ref.x(k), ref.y(k), ref.z(k));
            }
        }
        double client = 0.0;
        int first = index(track, from, true);
        int last = index(track, to, false);
        for (int i = first + 1; i <= last; i++) {
            client += distance(track.x(i - 1), track.y(i - 1), track.z(i - 1), track.x(i), track.y(i), track.z(i));
        }
        return client >= server * MIN_LENGTH_SHARE - LENGTH_SLACK && client <= server * MAX_LENGTH_SHARE + LENGTH_SLACK;
    }

    private static int allowed(int considered) {
        return Math.max(1, (int) Math.floor(considered * OUTLIER_SHARE));
    }

    private static boolean followsServer(Reference ref, GhostTrack track, long offset, long tol, long[] stale) {
        int considered = 0;
        int outliers = 0;
        long runStart = Long.MIN_VALUE;
        for (int k = 0; k < ref.count(); k++) {
            long c = ref.times()[k] - offset;
            if (c < track.beginNanos() || c > track.durationNanos()) {
                continue;
            }
            int first = index(track, c - tol - stale[k], false);
            int last = index(track, c + tol, true);
            double best = Double.MAX_VALUE;
            for (int i = first; i <= last; i++) {
                int j = Math.min(track.samples() - 1, i + 1);
                best = Math.min(best, segment(ref.x(k), ref.y(k), ref.z(k), track.x(i), track.y(i), track.z(i), track.x(j), track.y(j), track.z(j)));
            }
            considered++;
            if (best > POSITION_TOLERANCE) {
                outliers++;
                if (runStart == Long.MIN_VALUE) {
                    runStart = ref.times()[k];
                } else if (ref.times()[k] - runStart >= MAX_OUTLIER_RUN_NANOS) {
                    return false;
                }
            } else {
                runStart = Long.MIN_VALUE;
            }
        }
        return considered >= 2 && outliers <= allowed(considered);
    }

    private static boolean staysNearServer(Reference ref, GhostTrack track, long offset, long tol, long[] stale) {
        long reach = tol + ref.maxGap();
        int considered = 0;
        int outliers = 0;
        int lo = 1;
        for (int i = 0; i < track.samples(); i++) {
            long s = (long) i * track.intervalNanos() - track.leadNanos() + offset;
            if (s < ref.times()[0] || s > ref.times()[ref.count() - 1]) {
                continue;
            }
            while (lo < ref.count() - 1 && ref.times()[lo] + tol < s) {
                lo++;
            }
            double best = Double.MAX_VALUE;
            for (int k = lo; k < ref.count() && ref.times()[k - 1] - reach <= s; k++) {
                if (s < ref.times()[k - 1] - tol - Math.max(stale[k - 1], stale[k]) || s > ref.times()[k] + tol) {
                    continue;
                }
                double chord = distance(ref.x(k - 1), ref.y(k - 1), ref.z(k - 1), ref.x(k), ref.y(k), ref.z(k));
                double d = segment(track.x(i), track.y(i), track.z(i), ref.x(k - 1), ref.y(k - 1), ref.z(k - 1), ref.x(k), ref.y(k), ref.z(k));
                best = Math.min(best, d - 0.5 * chord);
            }
            considered++;
            if (best > POSITION_TOLERANCE) {
                outliers++;
            }
        }
        return considered >= 2 && outliers <= allowed(considered);
    }

    static double distance(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static double segment(double px, double py, double pz, double ax, double ay, double az, double bx, double by, double bz) {
        double vx = bx - ax;
        double vy = by - ay;
        double vz = bz - az;
        double length = vx * vx + vy * vy + vz * vz;
        double t = length <= 1.0E-12 ? 0.0 : Math.clamp(((px - ax) * vx + (py - ay) * vy + (pz - az) * vz) / length, 0.0, 1.0);
        return distance(px, py, pz, ax + vx * t, ay + vy * t, az + vz * t);
    }
}
