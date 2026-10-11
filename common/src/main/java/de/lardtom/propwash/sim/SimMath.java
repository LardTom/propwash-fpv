package de.lardtom.propwash.sim;

final class SimMath {
    static final double DEG = 180.0 / Math.PI;
    static final double RAD = Math.PI / 180.0;
    static final double TWO_PI = 2.0 * Math.PI;
    static final double HALF_PI = Math.PI / 2.0;
    static final double KERNEL_LIMIT = 0.7853981633974483;
    static final double HALF_PI_HI = 1.57079632673412561417e+00;
    static final double HALF_PI_LO = 6.07710050650619224932e-11;

    private SimMath() {
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    static double sanitize(double v, double lo, double hi, double fallback) {
        if (Double.isNaN(v)) {
            return fallback;
        }
        return clamp(v, lo, hi);
    }

    static double sanitizeFilterHz(double v, double lo, double hi, double fallback) {
        if (Double.isNaN(v)) {
            return fallback;
        }
        if (v <= 0.0) {
            return 0.0;
        }
        return clamp(v, lo, hi);
    }

    static double finiteOr(double v, double fallback) {
        return Double.isFinite(v) ? v : fallback;
    }

    static double smoothstep(double edge0, double edge1, double x) {
        if (edge1 <= edge0) {
            return x < edge0 ? 0.0 : 1.0;
        }
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    static double smoothSign(double v) {
        return v / (Math.abs(v) + 1.0);
    }

    static double wrapPi(double a) {
        if (!Double.isFinite(a)) {
            return 0.0;
        }
        double r = a % TWO_PI;
        if (r > Math.PI) {
            r -= TWO_PI;
        } else if (r <= -Math.PI) {
            r += TWO_PI;
        }
        return r;
    }

    static double pt1Gain(double cutoffHz, double dt) {
        if (cutoffHz <= 0.0 || dt <= 0.0) {
            return 1.0;
        }
        double rc = 1.0 / (TWO_PI * cutoffHz);
        return dt / (dt + rc);
    }

    static double sin(double x) {
        if (Math.abs(x) < KERNEL_LIMIT) {
            return reducedSin(x);
        }
        double k = Math.rint(x / HALF_PI);
        double r = (x - k * HALF_PI_HI) - k * HALF_PI_LO;
        int q = (int) (((long) k) & 3L);
        return switch (q) {
            case 0 -> reducedSin(r);
            case 1 -> reducedCos(r);
            case 2 -> -reducedSin(r);
            default -> -reducedCos(r);
        };
    }

    static double cos(double x) {
        if (Math.abs(x) < KERNEL_LIMIT) {
            return reducedCos(x);
        }
        double k = Math.rint(x / HALF_PI);
        double r = (x - k * HALF_PI_HI) - k * HALF_PI_LO;
        int q = (int) (((long) k) & 3L);
        return switch (q) {
            case 0 -> reducedCos(r);
            case 1 -> -reducedSin(r);
            case 2 -> -reducedCos(r);
            default -> reducedSin(r);
        };
    }

    private static double reducedSin(double r) {
        double r2 = r * r;
        double p = -1.0 / 121645100408832000.0;
        p = 1.0 / 355687428096000.0 + r2 * p;
        p = -1.0 / 1307674368000.0 + r2 * p;
        p = 1.0 / 6227020800.0 + r2 * p;
        p = -1.0 / 39916800.0 + r2 * p;
        p = 1.0 / 362880.0 + r2 * p;
        p = -1.0 / 5040.0 + r2 * p;
        p = 1.0 / 120.0 + r2 * p;
        p = -1.0 / 6.0 + r2 * p;
        return r + r * r2 * p;
    }

    private static double reducedCos(double r) {
        double r2 = r * r;
        double p = -1.0 / 6402373705728000.0;
        p = 1.0 / 20922789888000.0 + r2 * p;
        p = -1.0 / 87178291200.0 + r2 * p;
        p = 1.0 / 479001600.0 + r2 * p;
        p = -1.0 / 3628800.0 + r2 * p;
        p = 1.0 / 40320.0 + r2 * p;
        p = -1.0 / 720.0 + r2 * p;
        p = 1.0 / 24.0 + r2 * p;
        p = -0.5 + r2 * p;
        return 1.0 + r2 * p;
    }

    static double hypot3(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    static double[] solve3(double[][] a, double[] b) {
        double det = a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
                + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0]);
        double scale = 0.0;
        for (double[] row : a) {
            for (double v : row) {
                scale = Math.max(scale, Math.abs(v));
            }
        }
        if (!Double.isFinite(det) || Math.abs(det) <= 1e-12 * scale * scale * scale) {
            return null;
        }
        double[] x = new double[3];
        for (int c = 0; c < 3; c++) {
            double[][] m = {a[0].clone(), a[1].clone(), a[2].clone()};
            for (int r = 0; r < 3; r++) {
                m[r][c] = b[r];
            }
            double dc = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                    + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
            x[c] = dc / det;
        }
        return x;
    }
}
