package de.lardtom.propwash.dev;

import org.joml.Quaternionfc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CameraPathMeter {
    public static final int FPS = 60;
    public static final long FRAME_NANOS = 1_000_000_000L / FPS;
    public static final double MAX_TICK_SHARE = 0.25;
    public static final double MAX_JUMP_RATIO = 1.8;
    public static final double FLOOR_SHARE = 0.05;

    private final List<double[]> frames = new ArrayList<>();

    public record Series(String name, int steps, double mean, double tickShare, double jumpRatio, double lag1, double lag2, double lag3) {
        public boolean smooth() {
            return this.tickShare <= MAX_TICK_SHARE && this.jumpRatio <= MAX_JUMP_RATIO;
        }

        public String text() {
            return String.format(Locale.ROOT, "%s: Mittel %.4f je Bild, 20-Hz-Anteil %.2f, Sprungverhältnis %.2f, Autokorrelation Lag 1/2/3 %.2f/%.2f/%.2f",
                    this.name, this.mean, this.tickShare, this.jumpRatio, this.lag1, this.lag2, this.lag3);
        }
    }

    public record Result(Series position, Series rotation) {
        public boolean smooth() {
            return this.position.smooth() && this.rotation.smooth();
        }

        public String text() {
            return this.position.text() + "; " + this.rotation.text();
        }
    }

    public void frame(double x, double y, double z, Quaternionfc rotation) {
        this.frames.add(new double[]{x, y, z, rotation.x(), rotation.y(), rotation.z(), rotation.w()});
    }

    public int frames() {
        return this.frames.size();
    }

    public Result result() {
        int steps = Math.max(0, this.frames.size() - 1);
        double[] moved = new double[steps];
        double[] turned = new double[steps];
        for (int k = 0; k < steps; k++) {
            double[] a = this.frames.get(k);
            double[] b = this.frames.get(k + 1);
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double dz = b[2] - a[2];
            moved[k] = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double dot = Math.abs(a[3] * b[3] + a[4] * b[4] + a[5] * b[5] + a[6] * b[6]);
            double la = Math.sqrt(a[3] * a[3] + a[4] * a[4] + a[5] * a[5] + a[6] * a[6]);
            double lb = Math.sqrt(b[3] * b[3] + b[4] * b[4] + b[5] * b[5] + b[6] * b[6]);
            double cos = la > 0.0 && lb > 0.0 ? Math.min(1.0, dot / (la * lb)) : 1.0;
            turned[k] = Math.toDegrees(2.0 * Math.acos(cos));
        }
        return new Result(series("Position", moved), series("Lage", turned));
    }

    public static Series series(String name, double[] steps) {
        int n = steps.length;
        if (n < 6) {
            return new Series(name, n, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0);
        }
        double sum = 0.0;
        double re = 0.0;
        double im = 0.0;
        for (int k = 0; k < n; k++) {
            double value = Math.abs(steps[k]);
            sum += value;
            double phase = 2.0 * Math.PI * (k % 3) / 3.0;
            re += value * Math.cos(phase);
            im += value * Math.sin(phase);
        }
        double mean = sum / n;
        double tickShare = sum > 0.0 ? Math.hypot(re, im) / sum : 0.0;
        double floor = Math.max(1.0E-9, FLOOR_SHARE * mean);
        double[] ratios = new double[n - 2];
        double[] triple = new double[3];
        for (int k = 0; k + 2 < n; k++) {
            triple[0] = Math.abs(steps[k]);
            triple[1] = Math.abs(steps[k + 1]);
            triple[2] = Math.abs(steps[k + 2]);
            Arrays.sort(triple);
            ratios[k] = Math.max(triple[2], floor) / Math.max(triple[1], floor);
        }
        Arrays.sort(ratios);
        double jumpRatio = ratios[ratios.length / 2];
        return new Series(name, n, mean, tickShare, jumpRatio, lag(steps, mean, 1), lag(steps, mean, 2), lag(steps, mean, 3));
    }

    private static double lag(double[] steps, double mean, int lag) {
        double var = 0.0;
        for (double step : steps) {
            var += (step - mean) * (step - mean);
        }
        if (var <= 1.0E-12 * Math.max(1.0, mean * mean) * steps.length) {
            return 0.0;
        }
        double cov = 0.0;
        for (int k = 0; k + lag < steps.length; k++) {
            cov += (steps[k] - mean) * (steps[k + lag] - mean);
        }
        return cov / var;
    }
}
