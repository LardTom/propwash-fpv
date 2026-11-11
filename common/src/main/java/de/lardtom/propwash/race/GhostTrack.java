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
    private final @Nullable RaceStandard rules;
    private final long leadNanos;
    private final int leadSamples;
    private final Quaternionf scratch = new Quaternionf();

    public GhostTrack(long intervalNanos, double[] positions, float[] rotations, long lapNanos, String frame, int layout) {
        this(intervalNanos, positions, rotations, lapNanos, frame, layout, null);
    }

    public GhostTrack(long intervalNanos, double[] positions, float[] rotations, long lapNanos, String frame, int layout,
                      @Nullable GhostLook look) {
        this(intervalNanos, positions, rotations, lapNanos, frame, layout, look, null);
    }

    public GhostTrack(long intervalNanos, double[] positions, float[] rotations, long lapNanos, String frame, int layout,
                      @Nullable GhostLook look, @Nullable RaceStandard rules) {
        this(intervalNanos, positions, rotations, lapNanos, frame, layout, look, rules, 0L);
    }

    public GhostTrack(long intervalNanos, double[] positions, float[] rotations, long lapNanos, String frame, int layout,
                      @Nullable GhostLook look, @Nullable RaceStandard rules, long leadNanos) {
        if (intervalNanos <= 0L || positions.length % 3 != 0 || rotations.length != positions.length / 3 * 4) {
            throw new IllegalArgumentException("Ungültige Geisterspur");
        }
        int samples = positions.length / 3;
        if (samples < 2 || samples > MAX_SAMPLES) {
            throw new IllegalArgumentException("Geisterspur mit " + samples + " Punkten");
        }
        if (leadNanos < 0L || leadNanos % intervalNanos != 0L || leadNanos / intervalNanos > samples - 2) {
            throw new IllegalArgumentException("Ungültiger Vorlauf " + leadNanos);
        }
        this.intervalNanos = intervalNanos;
        this.count = samples;
        this.positions = positions.clone();
        this.rotations = rotations.clone();
        this.lapNanos = lapNanos;
        this.frame = frame == null ? "" : frame;
        this.layout = layout;
        this.look = look;
        this.rules = rules;
        this.leadNanos = leadNanos;
        this.leadSamples = (int) (leadNanos / intervalNanos);
    }

    public static @Nullable GhostTrack resample(long[] times, double[] positions, float[] rotations, int rawCount, long startNanos, long endNanos,
                                      long lapNanos, String frame, int layout) {
        return resample(times, positions, rotations, rawCount, startNanos, endNanos, lapNanos, frame, layout, INTERVAL_NANOS);
    }

    public static long intervalFor(long durationNanos, long wantedNanos) {
        long duration = Math.min(Math.max(0L, durationNanos), MAX_DURATION_NANOS);
        return Math.max(Math.max(1L, wantedNanos), Math.ceilDiv(duration, MAX_SAMPLES - 1));
    }

    public static @Nullable GhostTrack resample(long[] times, double[] positions, float[] rotations, int rawCount, long startNanos, long endNanos,
                                      long lapNanos, String frame, int layout, long intervalNanos) {
        return resample(times, positions, rotations, rawCount, startNanos, endNanos, lapNanos, frame, layout, intervalNanos, 0L);
    }

    public static @Nullable GhostTrack resample(long[] times, double[] positions, float[] rotations, int rawCount, long startNanos, long endNanos,
                                      long lapNanos, String frame, int layout, long intervalNanos, long leadNanos) {
        if (rawCount < 1 || endNanos <= startNanos || intervalNanos <= 0L) {
            return null;
        }
        long duration = Math.min(endNanos - startNanos, MAX_DURATION_NANOS);
        long interval = intervalFor(duration, intervalNanos);
        int samples = (int) (duration / interval) + 1;
        if (duration % interval != 0L && samples < MAX_SAMPLES) {
            samples++;
        }
        if (samples < 2) {
            return null;
        }
        int lead = (int) Math.min(Math.max(0L, leadNanos) / interval, MAX_SAMPLES - samples);
        samples += lead;
        double[] outPos = new double[samples * 3];
        float[] outRot = new float[samples * 4];
        Quaternionf a = new Quaternionf();
        Quaternionf b = new Quaternionf();
        int cursor = 0;
        for (int i = 0; i < samples; i++) {
            long t = Math.min(startNanos + (i - lead) * interval, startNanos + duration);
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
        return new GhostTrack(interval, outPos, outRot, lapNanos, frame, layout, null, null, lead * interval);
    }

    public long intervalNanos() {
        return this.intervalNanos;
    }

    public int samples() {
        return this.count;
    }

    public long durationNanos() {
        return (this.count - 1) * this.intervalNanos - this.leadNanos;
    }

    public long leadNanos() {
        return this.leadNanos;
    }

    public int leadSamples() {
        return this.leadSamples;
    }

    public long beginNanos() {
        return -this.leadNanos;
    }

    public long clamp(long time) {
        return Math.max(this.beginNanos(), Math.min(this.durationNanos(), time));
    }

    public long lapClock(long time) {
        return Math.max(0L, Math.min(time, this.lapNanos));
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

    public @Nullable RaceStandard rules() {
        return this.rules;
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
        return new GhostTrack(this.intervalNanos, this.positions, this.rotations, lapNanos, frame, layout, this.look, this.rules, this.leadNanos);
    }

    public GhostTrack withLook(@Nullable GhostLook look) {
        return new GhostTrack(this.intervalNanos, this.positions, this.rotations, this.lapNanos, this.frame, this.layout, look, this.rules,
                this.leadNanos);
    }

    public GhostTrack withRules(@Nullable RaceStandard value) {
        return new GhostTrack(this.intervalNanos, this.positions, this.rotations, this.lapNanos, this.frame, this.layout, this.look, value,
                this.leadNanos);
    }

    public GhostTrack withoutLead() {
        if (this.leadSamples == 0) {
            return this;
        }
        return new GhostTrack(this.intervalNanos, Arrays.copyOfRange(this.positions, this.leadSamples * 3, this.count * 3),
                Arrays.copyOfRange(this.rotations, this.leadSamples * 4, this.count * 4), this.lapNanos, this.frame, this.layout, this.look,
                this.rules, 0L);
    }

    public boolean pose(long lapTime, double[] position, Quaternionf rotation) {
        if (lapTime < this.beginNanos() || lapTime > this.durationNanos()) {
            return false;
        }
        long at = lapTime + this.leadNanos;
        int i = (int) Math.min(this.count - 1, at / this.intervalNanos);
        int j = Math.min(this.count - 1, i + 1);
        double alpha = j == i ? 0.0 : (at - i * this.intervalNanos) / (double) this.intervalNanos;
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

    public boolean smoothPose(long time, double[] position, Quaternionf rotation) {
        if (time < this.beginNanos() || time > this.durationNanos()) {
            return false;
        }
        long at = time + this.leadNanos;
        int i = (int) Math.min(this.count - 1, at / this.intervalNanos);
        int j = Math.min(this.count - 1, i + 1);
        double alpha = j == i ? 0.0 : Math.min(1.0, (at - i * this.intervalNanos) / (double) this.intervalNanos);
        int p = Math.max(0, i - 1);
        int n = Math.min(this.count - 1, j + 1);
        for (int k = 0; k < 3; k++) {
            position[k] = catmullRom(this.positions[p * 3 + k], this.positions[i * 3 + k], this.positions[j * 3 + k],
                    this.positions[n * 3 + k], alpha);
        }
        float sp = this.sign(i, p);
        float sj = this.sign(i, j);
        float sn = sj * this.sign(j, n);
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        double w = 0.0;
        for (int c = 0; c < 4; c++) {
            double value = catmullRom(sp * this.rotations[p * 4 + c], this.rotations[i * 4 + c], sj * this.rotations[j * 4 + c],
                    sn * this.rotations[n * 4 + c], alpha);
            switch (c) {
                case 0 -> x = value;
                case 1 -> y = value;
                case 2 -> z = value;
                default -> w = value;
            }
        }
        double length = Math.sqrt(x * x + y * y + z * z + w * w);
        if (!(length > 1.0E-6)) {
            rotation.set(this.rotations[i * 4], this.rotations[i * 4 + 1], this.rotations[i * 4 + 2], this.rotations[i * 4 + 3]);
            return true;
        }
        rotation.set((float) (x / length), (float) (y / length), (float) (z / length), (float) (w / length));
        return true;
    }

    private float sign(int from, int to) {
        float dot = 0.0F;
        for (int c = 0; c < 4; c++) {
            dot += this.rotations[from * 4 + c] * this.rotations[to * 4 + c];
        }
        return dot < 0.0F ? -1.0F : 1.0F;
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * (2.0 * p1 + (p2 - p0) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (3.0 * p1 - p0 - 3.0 * p2 + p3) * t3);
    }

    public long[] splits(List<GateGeometry> gates) {
        return this.splits(gates, GateOpenings.NONE);
    }

    public long[] splits(List<GateGeometry> gates, GateOpenings openings) {
        GateOpenings low = openings == null ? GateOpenings.NONE : openings;
        long[] splits = new long[gates.size()];
        Arrays.fill(splits, LapTracker.NONE);
        if (gates.isEmpty()) {
            return splits;
        }
        int gate = gates.size() > 1 ? 1 : 0;
        int stage = 0;
        for (int i = this.leadSamples + 1; i < this.count && gate > 0; i++) {
            double from = 0.0;
            while (gate > 0) {
                GateGeometry geometry = gates.get(gate);
                double t = this.crossingAfter(geometry, stage, i, from, gate, low);
                if (t < 0.0) {
                    break;
                }
                from = t;
                stage++;
                if (stage >= geometry.stageCount()) {
                    splits[gate] = Math.round((i - 1 - this.leadSamples + t) * this.intervalNanos);
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

    private double crossingAfter(GateGeometry geometry, int stage, int i, double from, int gate, GateOpenings openings) {
        double x0 = this.x(i - 1);
        double y0 = this.y(i - 1);
        double z0 = this.z(i - 1);
        double x1 = this.x(i);
        double y1 = this.y(i);
        double z1 = this.z(i);
        if (from <= 0.0) {
            return lowCrossing(geometry, stage, x0, y0, z0, x1, y1, z1, gate, openings);
        }
        double sx = x0 + (x1 - x0) * from;
        double sy = y0 + (y1 - y0) * from;
        double sz = z0 + (z1 - z0) * from;
        double t = lowCrossing(geometry, stage, sx, sy, sz, x1, y1, z1, gate, openings);
        return t < 0.0 ? t : from + (1.0 - from) * t;
    }

    private static double lowCrossing(GateGeometry geometry, int stage, double x0, double y0, double z0, double x1, double y1, double z1, int gate,
                                      GateOpenings openings) {
        double t = geometry.stageCrossing(stage, x0, y0, z0, x1, y1, z1);
        if (t >= 0.0 || openings == GateOpenings.NONE || !geometry.gate()) {
            return t;
        }
        double low = geometry.crossing(x0, y0, z0, x1, y1, z1, true, true);
        return low >= 0.0 && openings.legs(gate) ? low : t;
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
