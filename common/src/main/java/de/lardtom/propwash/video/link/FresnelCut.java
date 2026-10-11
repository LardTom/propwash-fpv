package de.lardtom.propwash.video.link;

public final class FresnelCut {
    public static final double[] BOUNDS = {0.1, 0.3, 0.5, 0.75, 1.05, 1.45, 2.0, 2.7, 3.6, 4.8, 6.5, 9.0, 12.5, 17.5, 25.0};
    public static final int SAMPLES = BOUNDS.length;
    public static final double STOP_FROM = 0.75;
    public static final double MIN_POWER = 1.0E-6;
    public static final double[] SHIFTS = {-0.3, 0.0, 0.3};
    public static final double[] SHIFT_WEIGHTS = {1.0, 2.0, 1.0};
    private static final double[] RADII = new double[SAMPLES];
    private static final int SHIFT_COUNT = SHIFTS.length;
    private static final double[] CENTER_RE = new double[SHIFT_COUNT];
    private static final double[] CENTER_IM = new double[SHIFT_COUNT];
    private static final double[][] PLUS_RE = new double[SHIFT_COUNT][SAMPLES];
    private static final double[][] PLUS_IM = new double[SHIFT_COUNT][SAMPLES];
    private static final double[][] MINUS_RE = new double[SHIFT_COUNT][SAMPLES];
    private static final double[][] MINUS_IM = new double[SHIFT_COUNT][SAMPLES];
    private static final double[][] PLUS_TAIL_RE = new double[SHIFT_COUNT][SAMPLES + 1];
    private static final double[][] PLUS_TAIL_IM = new double[SHIFT_COUNT][SAMPLES + 1];
    private static final double[][] MINUS_TAIL_RE = new double[SHIFT_COUNT][SAMPLES + 1];
    private static final double[][] MINUS_TAIL_IM = new double[SHIFT_COUNT][SAMPLES + 1];
    private static final double SHIFT_WEIGHT_SUM;

    static {
        for (int i = 0; i < SAMPLES; i++) {
            RADII[i] = i + 1 < SAMPLES ? 0.5 * (BOUNDS[i] + BOUNDS[i + 1]) : 1.25 * BOUNDS[i];
        }
        double[] cell = new double[2];
        double weights = 0.0;
        for (int s = 0; s < SHIFT_COUNT; s++) {
            double shift = SHIFTS[s];
            weights += SHIFT_WEIGHTS[s];
            weight(-BOUNDS[0] - shift, BOUNDS[0] - shift, cell);
            CENTER_RE[s] = cell[0];
            CENTER_IM[s] = cell[1];
            for (int i = 0; i < SAMPLES; i++) {
                double inner = BOUNDS[i];
                double outer = i + 1 < SAMPLES ? BOUNDS[i + 1] : Double.POSITIVE_INFINITY;
                weight(inner - shift, outer - shift, cell);
                PLUS_RE[s][i] = cell[0];
                PLUS_IM[s][i] = cell[1];
                weight(-outer - shift, -inner - shift, cell);
                MINUS_RE[s][i] = cell[0];
                MINUS_IM[s][i] = cell[1];
            }
            for (int i = SAMPLES - 1; i >= 0; i--) {
                PLUS_TAIL_RE[s][i] = PLUS_TAIL_RE[s][i + 1] + PLUS_RE[s][i];
                PLUS_TAIL_IM[s][i] = PLUS_TAIL_IM[s][i + 1] + PLUS_IM[s][i];
                MINUS_TAIL_RE[s][i] = MINUS_TAIL_RE[s][i + 1] + MINUS_RE[s][i];
                MINUS_TAIL_IM[s][i] = MINUS_TAIL_IM[s][i + 1] + MINUS_IM[s][i];
            }
        }
        SHIFT_WEIGHT_SUM = weights;
    }

    private FresnelCut() {
    }

    public static double radius(int index) {
        return RADII[index];
    }

    public static double power(double center, double[] plus, int plusCount, double[] minus, int minusCount) {
        int np = Math.clamp(plusCount, 0, SAMPLES);
        int nm = Math.clamp(minusCount, 0, SAMPLES);
        double total = 0.0;
        for (int s = 0; s < SHIFT_COUNT; s++) {
            double re = CENTER_RE[s] * center;
            double im = CENTER_IM[s] * center;
            double[] pr = PLUS_RE[s];
            double[] pi = PLUS_IM[s];
            for (int i = 0; i < np; i++) {
                re += pr[i] * plus[i];
                im += pi[i] * plus[i];
            }
            re += PLUS_TAIL_RE[s][np];
            im += PLUS_TAIL_IM[s][np];
            double[] mr = MINUS_RE[s];
            double[] mi = MINUS_IM[s];
            for (int i = 0; i < nm; i++) {
                re += mr[i] * minus[i];
                im += mi[i] * minus[i];
            }
            re += MINUS_TAIL_RE[s][nm];
            im += MINUS_TAIL_IM[s][nm];
            total += SHIFT_WEIGHTS[s] * (re * re + im * im);
        }
        return total / SHIFT_WEIGHT_SUM;
    }

    public static double combine(double cutX, double cutY, double centerPower) {
        double x = Math.clamp(cutX, 0.0, 1.0);
        double y = Math.clamp(cutY, 0.0, 1.0);
        double c = Math.clamp(centerPower, 0.0, 1.0);
        double p = x * y + (1.0 - c) * (x + y - c);
        return Math.clamp(p, MIN_POWER, 1.0);
    }

    public static double lossDb(double power) {
        if (!(power < 1.0)) {
            return 0.0;
        }
        return -10.0 * Math.log10(Math.max(power, MIN_POWER));
    }

    public static double amplitude(double lossDb) {
        if (!(lossDb > 0.0)) {
            return 1.0;
        }
        return Math.pow(10.0, -lossDb / 20.0);
    }

    private static void weight(double from, double to, double[] out) {
        double[] a = new double[2];
        double[] b = new double[2];
        KnifeEdge.fresnel(from, a);
        KnifeEdge.fresnel(to, b);
        double dc = b[0] - a[0];
        double ds = b[1] - a[1];
        out[0] = 0.5 * (dc + ds);
        out[1] = 0.5 * (dc - ds);
    }
}
