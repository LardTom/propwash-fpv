package de.lardtom.propwash.video;

public final class ImpactJolt {
    public static final double DEG_PER_MPS = 0.45;
    public static final double MIN_SPEED = 0.3;
    public static final double MAX_DEG = 3.0;
    public static final double DECAY_SECONDS = 0.11;
    public static final double PITCH_HZ = 13.0;
    public static final double ROLL_HZ = 15.5;
    public static final double YAW_HZ = 11.0;
    public static final double ROLL_SHARE = 0.6;
    public static final double YAW_SHARE = 0.4;
    public static final double DURATION_SECONDS = 0.7;
    public static final int SLOTS = 4;
    private static final long NONE = Long.MIN_VALUE;
    private static final double TWO_PI = 2.0 * Math.PI;

    private final long[] start = new long[SLOTS];
    private final double[] amplitude = new double[SLOTS];
    private final long[] seed = new long[SLOTS];
    private int next;
    private int hits;

    public ImpactJolt() {
        this.reset();
    }

    public void reset() {
        for (int i = 0; i < SLOTS; i++) {
            this.start[i] = NONE;
            this.amplitude[i] = 0.0;
            this.seed[i] = 0L;
        }
        this.next = 0;
        this.hits = 0;
    }

    public static double amplitude(double speedLost) {
        if (!Double.isFinite(speedLost) || speedLost < MIN_SPEED) {
            return 0.0;
        }
        return Math.min(MAX_DEG, DEG_PER_MPS * speedLost);
    }

    public static double envelope(double seconds) {
        if (!(seconds >= 0.0) || seconds > DURATION_SECONDS) {
            return 0.0;
        }
        return Math.exp(-seconds / DECAY_SECONDS);
    }

    public boolean hit(long nanos, double speedLost, long seed) {
        double degrees = amplitude(speedLost);
        if (degrees <= 0.0) {
            return false;
        }
        int slot = this.next;
        this.next = (this.next + 1) % SLOTS;
        this.start[slot] = nanos;
        this.amplitude[slot] = degrees;
        this.seed[slot] = seed;
        this.hits++;
        return true;
    }

    public int hits() {
        return this.hits;
    }

    public double lastAmplitude() {
        return this.hits == 0 ? 0.0 : this.amplitude[Math.floorMod(this.next - 1, SLOTS)];
    }

    public boolean active(long nanos) {
        for (int i = 0; i < SLOTS; i++) {
            if (this.start[i] != NONE && nanos >= this.start[i] && (nanos - this.start[i]) * 1.0E-9 <= DURATION_SECONDS) {
                return true;
            }
        }
        return false;
    }

    public boolean sample(long nanos, float intensity, double[] outDegrees) {
        outDegrees[0] = 0.0;
        outDegrees[1] = 0.0;
        outDegrees[2] = 0.0;
        float gain = Float.isFinite(intensity) ? Math.min(Math.max(intensity, 0.0F), CameraShake.MAX_INTENSITY) : 0.0F;
        if (gain <= 0.0F) {
            return false;
        }
        boolean any = false;
        for (int i = 0; i < SLOTS; i++) {
            if (this.start[i] == NONE || nanos < this.start[i]) {
                continue;
            }
            double seconds = (nanos - this.start[i]) * 1.0E-9;
            double envelope = envelope(seconds) * this.amplitude[i] * gain;
            if (envelope < 1.0E-5) {
                continue;
            }
            long bits = this.seed[i] * 0x9E37_79B9_7F4A_7C15L;
            double rollSign = (bits >>> 40 & 1L) == 0L ? 1.0 : -1.0;
            double yawSign = (bits >>> 41 & 1L) == 0L ? 1.0 : -1.0;
            outDegrees[0] -= envelope * Math.sin(TWO_PI * PITCH_HZ * seconds);
            outDegrees[1] += envelope * YAW_SHARE * yawSign * Math.sin(TWO_PI * YAW_HZ * seconds);
            outDegrees[2] += envelope * ROLL_SHARE * rollSign * Math.sin(TWO_PI * ROLL_HZ * seconds);
            any = true;
        }
        if (!any) {
            return false;
        }
        double limit = MAX_DEG * gain;
        for (int axis = 0; axis < 3; axis++) {
            outDegrees[axis] = Math.max(-limit, Math.min(limit, outDegrees[axis]));
        }
        return true;
    }
}
