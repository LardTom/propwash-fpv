package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class PathWobble {
    public static final double REFERENCE_SECONDS = 0.25;
    public static final double SWAY_LOW_HZ = 0.5;
    public static final double SWAY_HIGH_HZ = 8.0;
    public static final double SLOW_HIGH_HZ = 3.0;
    public static final double FAST_LOW_HZ = 12.0;

    private PathWobble() {
    }

    public record Result(double liveSway, double exportSway, double liveFast, double fakeSway, double fakeSlowSway, int samples) {
        public double ratio() {
            return this.liveSway > 0.0 ? this.exportSway / this.liveSway : Double.NaN;
        }
    }

    public static Result compare(Quaterniond[] live, Quaterniond[] export, boolean[] mask, double rate) {
        Quaterniond[] reference = smooth(live, REFERENCE_SECONDS * rate);
        double[][] liveDeviation = deviation(reference, live);
        double[][] exportDeviation = deviation(reference, export);
        double[][] difference = new double[3][live.length];
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 0; i < live.length; i++) {
                difference[axis][i] = exportDeviation[axis][i] - liveDeviation[axis][i];
            }
        }
        int samples = 0;
        for (boolean used : mask) {
            samples += used ? 1 : 0;
        }
        return new Result(rms(band(liveDeviation, rate, SWAY_LOW_HZ, SWAY_HIGH_HZ), mask),
                rms(band(exportDeviation, rate, SWAY_LOW_HZ, SWAY_HIGH_HZ), mask),
                rms(band(liveDeviation, rate, FAST_LOW_HZ, Double.POSITIVE_INFINITY), mask),
                rms(band(difference, rate, SWAY_LOW_HZ, SWAY_HIGH_HZ), mask),
                rms(band(difference, rate, SWAY_LOW_HZ, SLOW_HIGH_HZ), mask), samples);
    }

    public static Quaterniond[] smooth(Quaterniond[] rotations, double sigmaSamples) {
        int n = rotations.length;
        int reach = (int) Math.ceil(3.0 * sigmaSamples);
        Quaterniond[] out = new Quaterniond[n];
        for (int i = 0; i < n; i++) {
            Quaterniond inverse = new Quaterniond(rotations[i]).conjugate();
            Vector3d sum = new Vector3d();
            double weight = 0.0;
            for (int j = Math.max(0, i - reach); j <= Math.min(n - 1, i + reach); j++) {
                double w = Math.exp(-0.5 * (j - i) * (j - i) / (sigmaSamples * sigmaSamples));
                sum.fma(w, log(inverse.mul(rotations[j], new Quaterniond())));
                weight += w;
            }
            out[i] = new Quaterniond(rotations[i]).mul(exp(sum.mul(1.0 / weight))).normalize();
        }
        return out;
    }

    public static double[][] deviation(Quaterniond[] reference, Quaterniond[] rotations) {
        double[][] out = new double[3][rotations.length];
        for (int i = 0; i < rotations.length; i++) {
            Vector3d v = log(new Quaterniond(reference[i]).conjugate().mul(rotations[i]));
            out[0][i] = Math.toDegrees(v.x);
            out[1][i] = Math.toDegrees(v.y);
            out[2][i] = Math.toDegrees(v.z);
        }
        return out;
    }

    public static double[][] band(double[][] signal, double rate, double lowHz, double highHz) {
        int n = signal[0].length;
        int size = Integer.highestOneBit(Math.max(1, n - 1)) << 1;
        double[][] out = new double[signal.length][n];
        for (int axis = 0; axis < signal.length; axis++) {
            double[] re = new double[size];
            double[] im = new double[size];
            double mean = 0.0;
            for (int i = 0; i < n; i++) {
                mean += signal[axis][i];
            }
            mean /= n;
            for (int i = 0; i < n; i++) {
                re[i] = signal[axis][i] - mean;
            }
            fft(re, im);
            for (int k = 0; k < size; k++) {
                double hz = Math.min(k, size - k) * rate / size;
                if (hz < lowHz || hz >= highHz) {
                    re[k] = 0.0;
                    im[k] = 0.0;
                }
                im[k] = -im[k];
            }
            fft(re, im);
            for (int i = 0; i < n; i++) {
                out[axis][i] = re[i] / size;
            }
        }
        return out;
    }

    public static double rms(double[][] signal, boolean[] mask) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < signal[0].length; i++) {
            if (!mask[i]) {
                continue;
            }
            for (double[] axis : signal) {
                sum += axis[i] * axis[i];
            }
            count++;
        }
        return count == 0 ? 0.0 : Math.sqrt(sum / count);
    }

    public static double amplitude(double[] signal, int from, int to, double rate, double hz) {
        double period = rate / hz;
        int length = (int) (Math.floor((to - from) / period) * period);
        double re = 0.0;
        double im = 0.0;
        for (int i = from; i < from + length; i++) {
            double phase = 2.0 * Math.PI * hz * i / rate;
            re += signal[i] * Math.cos(phase);
            im += signal[i] * Math.sin(phase);
        }
        return 2.0 * Math.hypot(re, im) / Math.max(1, length);
    }

    public static Quaterniond[] export(CameraTrack track, double[] ticks) {
        Quaterniond[] out = new Quaterniond[ticks.length];
        for (int i = 0; i < ticks.length; i++) {
            out[i] = FlashbackSpline.at(track.camera(), (float) ticks[i]).angles().rotation();
        }
        return out;
    }

    static Vector3d log(Quaterniond q) {
        double sign = q.w < 0.0 ? -1.0 : 1.0;
        double sin = Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z);
        if (sin < 1.0E-12) {
            return new Vector3d(2.0 * sign * q.x, 2.0 * sign * q.y, 2.0 * sign * q.z);
        }
        double scale = 2.0 * Math.atan2(sin, sign * q.w) / sin * sign;
        return new Vector3d(q.x * scale, q.y * scale, q.z * scale);
    }

    static Quaterniond exp(Vector3d v) {
        double angle = v.length();
        if (angle < 1.0E-12) {
            return new Quaterniond(0.5 * v.x, 0.5 * v.y, 0.5 * v.z, 1.0).normalize();
        }
        double scale = Math.sin(0.5 * angle) / angle;
        return new Quaterniond(v.x * scale, v.y * scale, v.z * scale, Math.cos(0.5 * angle));
    }

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int length = 2; length <= n; length <<= 1) {
            double angle = -2.0 * Math.PI / length;
            double stepRe = Math.cos(angle);
            double stepIm = Math.sin(angle);
            for (int start = 0; start < n; start += length) {
                double wRe = 1.0;
                double wIm = 0.0;
                for (int k = 0; k < length / 2; k++) {
                    int a = start + k;
                    int b = a + length / 2;
                    double xRe = re[b] * wRe - im[b] * wIm;
                    double xIm = re[b] * wIm + im[b] * wRe;
                    re[b] = re[a] - xRe;
                    im[b] = im[a] - xIm;
                    re[a] += xRe;
                    im[a] += xIm;
                    double nextRe = wRe * stepRe - wIm * stepIm;
                    wIm = wRe * stepIm + wIm * stepRe;
                    wRe = nextRe;
                }
            }
        }
    }
}
