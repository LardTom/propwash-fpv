package de.lardtom.propwash.race;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class GhostTrack {
    public static final long INTERVAL_NANOS = 50_000_000L;
    public static final int MAX_SAMPLES = 12_001;
    public static final long MAX_DURATION_NANOS = INTERVAL_NANOS * (MAX_SAMPLES - 1);

    private final long intervalNanos;
    private final int count;
    private final double[] positions;
    private final float[] rotations;
    private final long lapNanos;
    private final String frame;
    private final int layout;
    private final @Nullable GhostLook look;
    private final Quaternionf scratch = new Quaternionf();

    public GhostTrack(long intervalNanos, double[] positions, float[] rotations, long lapNanos, String frame, int layout) {
        this(intervalNanos, positions, rotations, lapNanos, frame, layout, null);
    }

    public GhostTrack(long intervalNanos, double[] positions, float[] rotations, long lapNanos, String frame, int layout,
                      @Nullable GhostLook look) {
        if (intervalNanos <= 0L || positions.length % 3 != 0 || rotations.length != positions.length / 3 * 4) {
            throw new IllegalArgumentException("Ungültige Geisterspur");
        }
        int samples = positions.length / 3;
        if (samples < 2 || samples > MAX_SAMPLES) {
            throw new IllegalArgumentException("Geisterspur mit " + samples + " Punkten");
        }
        this.intervalNanos = intervalNanos;
        this.count = samples;
        this.positions = positions.clone();
        this.rotations = rotations.clone();
        this.lapNanos = lapNanos;
        this.frame = frame == null ? "" : frame;
        this.layout = layout;
        this.look = look;
    }

    public static @Nullable GhostTrack resample(long[] times, double[] positions, float[] rotations, int rawCount, long startNanos, long endNanos,
                                      long lapNanos, String frame, int layout) {
        if (rawCount < 1 || endNanos <= startNanos) {
            return null;
        }
        long duration = Math.min(endNanos - startNanos, MAX_DURATION_NANOS);
        int samples = (int) (duration / INTERVAL_NANOS) + 1;
        if (duration % INTERVAL_NANOS != 0L && samples < MAX_SAMPLES) {
            samples++;
        }
        if (samples < 2) {
            return null;
        }
        double[] outPos = new double[samples * 3];
        float[] outRot = new float[samples * 4];
        Quaternionf a = new Quaternionf();
        Quaternionf b = new Quaternionf();
        int cursor = 0;
        for (int i = 0; i < samples; i++) {
            long t = Math.min(startNanos + i * INTERVAL_NANOS, startNanos + duration);
            while (cursor + 1 < rawCount && times[cursor + 1] <= t) {
                cursor++;
            }
            int lo = cursor;
            int hi = Math.min(cursor + 1, rawCount - 1);
            double alpha = 0.0;
            if (hi != lo && times[hi] > times[lo] && t > times[lo]) {
                alpha = Math.min(1.0, (t - times[lo]) / (double) (times[hi] - times[lo]));
            }
            for (int k = 0; k < 3; k++) {
                double pa = positions[lo * 3 + k];
                double pb = positions[hi * 3 + k];
                outPos[i * 3 + k] = pa + (pb - pa) * alpha;
            }
            a.set(rotations[lo * 4], rotations[lo * 4 + 1], rotations[lo * 4 + 2], rotations[lo * 4 + 3]);
            b.set(rotations[hi * 4], rotations[hi * 4 + 1], rotations[hi * 4 + 2], rotations[hi * 4 + 3]);
            a.slerp(b, (float) alpha);
            outRot[i * 4] = a.x;
            outRot[i * 4 + 1] = a.y;
            outRot[i * 4 + 2] = a.z;
            outRot[i * 4 + 3] = a.w;
        }
        return new GhostTrack(INTERVAL_NANOS, outPos, outRot, lapNanos, frame, layout);
    }

    public long intervalNanos() {
        return this.intervalNanos;
    }

    public int samples() {
        return this.count;
    }

    public long durationNanos() {
        return (this.count - 1) * this.intervalNanos;
    }

    public long lapNanos() {
        return this.lapNanos;
    }

    public String frame() {
        return this.frame;
    }

    public int layout() {
        return this.layout;
    }

    public @Nullable GhostLook look() {
        return this.look;
    }

    public double x(int sample) {
        return this.positions[sample * 3];
    }

    public double y(int sample) {
        return this.positions[sample * 3 + 1];
    }

    public double z(int sample) {
        return this.positions[sample * 3 + 2];
    }

    public float rotation(int sample, int component) {
        return this.rotations[sample * 4 + component];
    }

    double[] positions() {
        return this.positions;
    }

    float[] rotations() {
        return this.rotations;
    }

    public GhostTrack withLap(long lapNanos, String frame, int layout) {
        return new GhostTrack(this.intervalNanos, this.positions, this.rotations, lapNanos, frame, layout, this.look);
    }

    public GhostTrack withLook(@Nullable GhostLook look) {
        return new GhostTrack(this.intervalNanos, this.positions, this.rotations, this.lapNanos, this.frame, this.layout, look);
    }

    public boolean pose(long lapTime, double[] position, Quaternionf rotation) {
        if (lapTime < 0L || lapTime > this.durationNanos()) {
            return false;
        }
        int i = (int) Math.min(this.count - 1, lapTime / this.intervalNanos);
        int j = Math.min(this.count - 1, i + 1);
        double alpha = j == i ? 0.0 : (lapTime - i * this.intervalNanos) / (double) this.intervalNanos;
        int p = Math.max(0, i - 1);
        int n = Math.min(this.count - 1, j + 1);
        for (int k = 0; k < 3; k++) {
            position[k] = catmullRom(this.positions[p * 3 + k], this.positions[i * 3 + k], this.positions[j * 3 + k],
                    this.positions[n * 3 + k], alpha);
        }
        float ax = this.rotations[i * 4];
        float ay = this.rotations[i * 4 + 1];
        float az = this.rotations[i * 4 + 2];
        float aw = this.rotations[i * 4 + 3];
        rotation.set(ax, ay, az, aw);
        if (j != i) {
            rotation.slerp(this.scratch.set(this.rotations[j * 4], this.rotations[j * 4 + 1], this.rotations[j * 4 + 2],
                    this.rotations[j * 4 + 3]), (float) alpha);
        }
        return true;
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * (2.0 * p1 + (p2 - p0) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (3.0 * p1 - p0 - 3.0 * p2 + p3) * t3);
    }

    public long[] splits(List<GateGeometry> gates) {
        long[] splits = new long[gates.size()];
        Arrays.fill(splits, LapTracker.NONE);
        if (gates.isEmpty()) {
            return splits;
        }
        int gate = gates.size() > 1 ? 1 : 0;
        int stage = 0;
        for (int i = 1; i < this.count && gate > 0; i++) {
            double from = 0.0;
            while (gate > 0) {
                GateGeometry geometry = gates.get(gate);
                double t = this.crossingAfter(geometry, stage, i, from);
                if (t < 0.0) {
                    break;
                }
                from = t;
                stage++;
                if (stage >= geometry.kind().stages()) {
                    splits[gate] = Math.round((i - 1 + t) * this.intervalNanos);
                    gate = (gate + 1) % gates.size();
                    stage = 0;
                }
                if (geometry.gate()) {
                    break;
                }
            }
        }
        splits[0] = this.lapNanos;
        return splits;
    }

    private double crossingAfter(GateGeometry geometry, int stage, int i, double from) {
        double x0 = this.x(i - 1);
        double y0 = this.y(i - 1);
        double z0 = this.z(i - 1);
        double x1 = this.x(i);
        double y1 = this.y(i);
        double z1 = this.z(i);
        if (from <= 0.0) {
            return geometry.stageCrossing(stage, x0, y0, z0, x1, y1, z1);
        }
        double sx = x0 + (x1 - x0) * from;
        double sy = y0 + (y1 - y0) * from;
        double sz = z0 + (z1 - z0) * from;
        double t = geometry.stageCrossing(stage, sx, sy, sz, x1, y1, z1);
        return t < 0.0 ? t : from + (1.0 - from) * t;
    }

    public static boolean completeSplits(long[] splits) {
        for (long split : splits) {
            if (split < 0L) {
                return false;
            }
        }
        return splits.length > 0;
    }
}
