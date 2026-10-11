package de.lardtom.propwash.audio;

public final class MotorControl {
    public static final int MOTORS = 4;
    public static final float MAX_RPM = 200_000.0F;

    private final float[] rpm = new float[MOTORS];
    private final float[] load = new float[MOTORS];
    private float wash;
    private float airflow;
    private float doppler = 1.0F;
    private float gain;
    private float brightnessHz = 14_000.0F;
    private float smoothingSeconds = 0.03F;
    private boolean active = true;
    private boolean onboard = true;
    private int impulseCount;
    private float impulseStrength;
    private volatile int version;

    public void setMotor(int motor, double rpm, double load) {
        if (motor < 0 || motor >= MOTORS) {
            return;
        }
        this.rpm[motor] = (float) sanitize(rpm, 0.0, MAX_RPM);
        this.load[motor] = (float) sanitize(load, 0.0, 1.0);
    }

    public void setWash(double wash) {
        this.wash = (float) sanitize(wash, 0.0, 1.0);
    }

    public void setAirflow(double airflow) {
        this.airflow = (float) sanitize(airflow, 0.0, 1.0);
    }

    public void setDoppler(double factor) {
        this.doppler = Double.isFinite(factor) ? (float) sanitize(factor, 0.5, 2.0) : 1.0F;
    }

    public void setGain(double gain) {
        this.gain = (float) sanitize(gain, 0.0, 2.0);
    }

    public void setBrightnessHz(double hz) {
        this.brightnessHz = (float) sanitize(hz, 200.0, 20_000.0);
    }

    public void setSmoothingSeconds(double seconds) {
        this.smoothingSeconds = (float) sanitize(seconds, 0.001, 0.5);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setOnboard(boolean onboard) {
        this.onboard = onboard;
    }

    public void impulse(double strength) {
        this.impulseStrength = (float) sanitize(strength, 0.0, 1.0);
        this.impulseCount++;
    }

    public void publish() {
        this.version = this.version + 1;
    }

    public int version() {
        return this.version;
    }

    public float rpm(int motor) {
        return this.rpm[motor];
    }

    public float load(int motor) {
        return this.load[motor];
    }

    public float wash() {
        return this.wash;
    }

    public float airflow() {
        return this.airflow;
    }

    public float doppler() {
        return this.doppler;
    }

    public float gain() {
        return this.gain;
    }

    public float brightnessHz() {
        return this.brightnessHz;
    }

    public float smoothingSeconds() {
        return this.smoothingSeconds;
    }

    public boolean active() {
        return this.active;
    }

    public boolean onboard() {
        return this.onboard;
    }

    public int impulseCount() {
        return this.impulseCount;
    }

    public float impulseStrength() {
        return this.impulseStrength;
    }

    public float maxRpm() {
        float max = 0.0F;
        for (int i = 0; i < MOTORS; i++) {
            max = Math.max(max, this.rpm[i]);
        }
        return max;
    }

    private static double sanitize(double value, double lo, double hi) {
        if (!Double.isFinite(value)) {
            return lo;
        }
        return value < lo ? lo : (value > hi ? hi : value);
    }
}
