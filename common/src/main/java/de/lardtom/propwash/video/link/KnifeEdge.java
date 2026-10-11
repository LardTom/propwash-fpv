package de.lardtom.propwash.video.link;

public final class KnifeEdge {
    public static final double LIT_LIMIT = -0.78;
    private static final double[] A = {1.595769140, -0.000001702, -6.808568854, -0.000576361, 6.920691902, -0.016898657,
            -3.050485660, -0.075752419, 0.850663781, -0.025639041, -0.150230960, 0.034404779};
    private static final double[] B = {-0.000000033, 4.255387524, -0.000092810, -7.780020400, -0.009520895, 5.075161298,
            -0.138341947, -1.363729124, -0.403349276, 0.702222016, -0.216195929, 0.019547031};
    private static final double[] C = {0.000000000, -0.024933975, 0.000003936, 0.005770956, 0.000689892, -0.009497136,
            0.011948809, -0.006748873, 0.000246420, 0.002102967, -0.001217930, 0.000233939};
    private static final double[] D = {0.199471140, 0.000000023, -0.009351341, 0.000023006, 0.004851466, 0.001903218,
            -0.017122914, 0.029064067, -0.027928955, 0.016497308, -0.005598515, 0.000838386};

    private KnifeEdge() {
    }

    public static double lossDb(double nu) {
        if (!(nu > LIT_LIMIT)) {
            return 0.0;
        }
        if (nu == Double.POSITIVE_INFINITY) {
            return Double.POSITIVE_INFINITY;
        }
        double v = nu - 0.1;
        return 6.9 + 20.0 * Math.log10(Math.sqrt(v * v + 1.0) + v);
    }

    public static double exactLossDb(double nu) {
        double[] cs = new double[2];
        fresnel(nu, cs);
        double a = 1.0 - cs[0] - cs[1];
        double b = cs[0] - cs[1];
        return -20.0 * Math.log10(Math.sqrt(a * a + b * b) / 2.0);
    }

    public static void fresnel(double nu, double[] out) {
        if (Double.isNaN(nu)) {
            out[0] = 0.0;
            out[1] = 0.0;
            return;
        }
        if (Double.isInfinite(nu)) {
            out[0] = nu > 0.0 ? 0.5 : -0.5;
            out[1] = out[0];
            return;
        }
        double sign = nu < 0.0 ? -1.0 : 1.0;
        double v = Math.abs(nu);
        double x = 0.5 * Math.PI * v * v;
        double re;
        double im;
        if (x < 4.0) {
            double q = x / 4.0;
            double sumRe = 0.0;
            double sumIm = 0.0;
            double power = 1.0;
            for (int n = 0; n < A.length; n++) {
                sumRe += A[n] * power;
                sumIm -= B[n] * power;
                power *= q;
            }
            double root = Math.sqrt(q);
            double cos = Math.cos(x);
            double sin = Math.sin(x);
            re = root * (cos * sumRe - sin * sumIm);
            im = root * (cos * sumIm + sin * sumRe);
        } else {
            double q = 4.0 / x;
            double sumRe = 0.0;
            double sumIm = 0.0;
            double power = 1.0;
            for (int n = 0; n < C.length; n++) {
                sumRe += C[n] * power;
                sumIm -= D[n] * power;
                power *= q;
            }
            double root = Math.sqrt(q);
            double cos = Math.cos(x);
            double sin = Math.sin(x);
            re = 0.5 + root * (cos * sumRe - sin * sumIm);
            im = 0.5 + root * (cos * sumIm + sin * sumRe);
        }
        out[0] = sign * re;
        out[1] = sign * im;
    }
}
