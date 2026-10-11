package de.lardtom.propwash.video;

public final class CameraShake {
    public static final float DEFAULT_INTENSITY = 1.0F;
    public static final float MAX_INTENSITY = 2.0F;
    public static final double BUZZ_HOVER_DEG = 0.035;
    public static final double WEAR_BUZZ_MAX_DEG = 0.2;
    public static final double WEAR_GAIN = 0.75;
    public static final double BUFFET_DEG = 0.35;
    public static final double RECIRCULATION_DEG = 0.18;
    public static final double PUNCH_DEG = 0.25;
    public static final double PUNCH_RATE_START = 4.0;
    public static final double PUNCH_RISE_FULL = 0.8;
    public static final double PUNCH_RISE_SECONDS = 0.1;
    public static final double PUNCH_DECAY_SECONDS = 0.18;
    public static final double ATTACK_SECONDS = 0.03;
    public static final double RELEASE_SECONDS = 0.2;
    public static final double BUZZ_HZ = 38.0;
    public static final double BUFFET_HZ = 9.0;
    public static final double PUNCH_HZ = 17.0;
    public static final double YAW_SHARE = 0.4;
    public static final double MAX_TOTAL_DEG = 1.2;
    public static final double FLOW_CAP = 1.5;
    public static final double RATIO_CAP = 4.0;
    private static final int HISTORY = 64;
    private static final long WRAP_NANOS = 3_600_000_000_000L;
    private static final long NONE = Long.MIN_VALUE;

    private final long[] times = new long[HISTORY];
    private final double[] buzz = new double[HISTORY];
    private final double[] buffet = new double[HISTORY];
    private final double[] punch = new double[HISTORY];
    private int start;
    private int size;
    private long lastNanos = NONE;
    private double lastRatio = Double.NaN;
    private double buzzLevel;
    private double buffetLevel;
    private double punchEnvelope;
    private double punchRise;

    public void reset() {
        this.start = 0;
        this.size = 0;
        this.lastNanos = NONE;
        this.lastRatio = Double.NaN;
        this.buzzLevel = 0.0;
        this.buffetLevel = 0.0;
        this.punchEnvelope = 0.0;
        this.punchRise = 0.0;
    }

    public void update(long nanos, double rpmRatio, double wear, double wash, double recirculation, boolean spinning) {
        if (this.lastNanos != NONE && nanos <= this.lastNanos) {
            return;
        }
        double dt = this.lastNanos == NONE ? 0.0 : Math.min((nanos - this.lastNanos) * 1.0E-9, 0.1);
        this.lastNanos = nanos;
        double ratio = spinning ? clamp(finite(rpmRatio), 0.0, RATIO_CAP) : 0.0;
        double flow = Math.min(ratio, FLOW_CAP);
        double targetBuzz = spinning ? buzzDegrees(ratio, finite(wear)) : 0.0;
        double targetBuffet = spinning ? buffetDegrees(flow, clamp(finite(wash), 0.0, 1.0), clamp(finite(recirculation), 0.0, 1.0)) : 0.0;
        this.buzzLevel = approach(this.buzzLevel, targetBuzz, dt);
        this.buffetLevel = approach(this.buffetLevel, targetBuffet, dt);
        if (dt > 0.0 && !Double.isNaN(this.lastRatio)) {
            this.punchRise = this.punchRise * Math.exp(-dt / PUNCH_RISE_SECONDS) + punchExcess(ratio - this.lastRatio, dt);
        }
        double impulse = clamp(this.punchRise / PUNCH_RISE_FULL, 0.0, 1.0);
        this.lastRatio = ratio;
        this.punchEnvelope = Math.max(this.punchEnvelope * Math.exp(-dt / PUNCH_DECAY_SECONDS), impulse);
        if (this.punchEnvelope < 1.0E-4) {
            this.punchEnvelope = 0.0;
        }
        this.push(nanos, this.buzzLevel, this.buffetLevel, this.punchEnvelope * PUNCH_DEG);
    }

    public static double buzzDegrees(double rpmRatio, double wear) {
        double ratio = clamp(finite(rpmRatio), 0.0, RATIO_CAP);
        double square = ratio * ratio;
        double healthy = BUZZ_HOVER_DEG * Math.min(1.0, square);
        double worn = Math.min(WEAR_BUZZ_MAX_DEG, BUZZ_HOVER_DEG * square) * WEAR_GAIN * Math.max(0.0, finite(wear));
        return healthy + worn;
    }

    public static double punchExcess(double ratioRise, double seconds) {
        return Math.max(0.0, finite(ratioRise) - PUNCH_RATE_START * Math.max(0.0, finite(seconds)));
    }

    public static double recirculationFade(double airspeed, double hoverInducedVelocity, double rpmRatio) {
        double speed = Math.max(0.0, finite(airspeed));
        double induced = finite(hoverInducedVelocity) * clamp(finite(rpmRatio), 0.0, RATIO_CAP);
        if (!(induced > 1.0E-6)) {
            return speed > 0.0 ? 0.0 : 1.0;
        }
        double mu = speed / induced;
        double mu2 = mu * mu;
        double inducedSquare = 2.0 / (Math.sqrt(mu2 * mu2 + 4.0) + mu2);
        return inducedSquare / (inducedSquare + mu2);
    }

    public static double buffetDegrees(double flow, double wash, double recirculation) {
        double f = clamp(finite(flow), 0.0, FLOW_CAP);
        return f * (BUFFET_DEG * clamp(finite(wash), 0.0, 1.0) + RECIRCULATION_DEG * clamp(finite(recirculation), 0.0, 1.0));
    }

    public double buzzLevel() {
        return this.buzzLevel;
    }

    public double buffetLevel() {
        return this.buffetLevel;
    }

    public double punchLevel() {
        return this.punchEnvelope * PUNCH_DEG;
    }

    public boolean idle() {
        return this.size == 0 || (this.buzzLevel < 1.0E-5 && this.buffetLevel < 1.0E-5 && this.punchEnvelope == 0.0);
    }

    public boolean sample(long nanos, float intensity, double[] outDegrees) {
        outDegrees[0] = 0.0;
        outDegrees[1] = 0.0;
        outDegrees[2] = 0.0;
        float gain = Float.isFinite(intensity) ? Math.min(Math.max(intensity, 0.0F), MAX_INTENSITY) : 0.0F;
        if (gain <= 0.0F || this.size == 0) {
            return false;
        }
        int index = this.indexAt(nanos);
        double buzzAmp;
        double buffetAmp;
        double punchAmp;
        if (index < 0) {
            int slot = this.slot(index == -1 ? 0 : this.size - 1);
            buzzAmp = this.buzz[slot];
            buffetAmp = this.buffet[slot];
            punchAmp = this.punch[slot];
        } else {
            int a = this.slot(index);
            int b = this.slot(index + 1);
            double t = (double) (nanos - this.times[a]) / (double) (this.times[b] - this.times[a]);
            buzzAmp = lerp(this.buzz[a], this.buzz[b], t);
            buffetAmp = lerp(this.buffet[a], this.buffet[b], t);
            punchAmp = lerp(this.punch[a], this.punch[b], t);
        }
        if (buzzAmp + buffetAmp + punchAmp < 1.0E-6) {
            return false;
        }
        double seconds = Math.floorMod(nanos, WRAP_NANOS) * 1.0E-9;
        double limit = MAX_TOTAL_DEG * gain;
        for (int axis = 0; axis < 3; axis++) {
            long seed = 0x9E37_79B9_7F4A_7C15L * (axis + 1);
            double value = buzzAmp * (0.7 * noise(seconds * BUZZ_HZ, seed) + 0.3 * noise(seconds * BUZZ_HZ * 1.83, seed ^ 0x5DEECE66DL))
                    + buffetAmp * noise(seconds * BUFFET_HZ, seed ^ 0xB5297A4DL)
                    + punchAmp * noise(seconds * PUNCH_HZ, seed ^ 0x68E31DA4L);
            double scaled = value * gain * (axis == 1 ? YAW_SHARE : 1.0);
            outDegrees[axis] = clamp(scaled, -limit, limit);
        }
        return true;
    }

    private int indexAt(long nanos) {
        if (nanos <= this.times[this.slot(0)]) {
            return -1;
        }
        if (nanos >= this.times[this.slot(this.size - 1)]) {
            return -2;
        }
        int low = 0;
        int high = this.size - 1;
        while (high - low > 1) {
            int mid = (low + high) >>> 1;
            if (this.times[this.slot(mid)] <= nanos) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private void push(long nanos, double buzzAmp, double buffetAmp, double punchAmp) {
        int slot;
        if (this.size < HISTORY) {
            slot = this.slot(this.size);
            this.size++;
        } else {
            slot = this.start;
            this.start = (this.start + 1) % HISTORY;
        }
        this.times[slot] = nanos;
        this.buzz[slot] = buzzAmp;
        this.buffet[slot] = buffetAmp;
        this.punch[slot] = punchAmp;
    }

    private int slot(int index) {
        return (this.start + index) % HISTORY;
    }

    private static double approach(double current, double target, double dt) {
        if (dt <= 0.0) {
            return current;
        }
        double tau = target > current ? ATTACK_SECONDS : RELEASE_SECONDS;
        double next = current + (target - current) * (1.0 - Math.exp(-dt / tau));
        return Math.abs(next) < 1.0E-6 ? 0.0 : next;
    }

    static double noise(double x, long seed) {
        double floor = Math.floor(x);
        long cell = (long) floor;
        double f = x - floor;
        double s = f * f * (3.0 - 2.0 * f);
        return lerp(lattice(cell, seed), lattice(cell + 1L, seed), s);
    }

    private static double lattice(long cell, long seed) {
        long z = cell * 0x9E37_79B9_7F4A_7C15L + seed;
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        z ^= z >>> 31;
        return ((z >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }
}
