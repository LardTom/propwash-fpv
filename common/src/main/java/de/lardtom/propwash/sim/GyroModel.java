package de.lardtom.propwash.sim;

final class GyroModel {
    static final double MOTOR_SHARE = Math.sqrt(2.0 / AirframeSpec.MOTORS);
    static final double YAW_COUPLING = 0.5;
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    final double[] raw = new double[3];
    private final double[] rotorAngle = new double[AirframeSpec.MOTORS];
    private double h;

    void configure(double h) {
        this.h = h;
    }

    void reset() {
        for (int a = 0; a < 3; a++) {
            this.raw[a] = 0.0;
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.rotorAngle[i] = 0.0;
        }
    }

    void measure(double wbx, double wby, double wbz) {
        this.raw[FcAxes.ROLL] = FcAxes.rollDps(wbz);
        this.raw[FcAxes.PITCH] = FcAxes.pitchDps(wbx);
        this.raw[FcAxes.YAW] = FcAxes.yawDps(wby);
    }

    void sample(SimRandom rng, double wbx, double wby, double wbz, double noiseDps, double vibrationDps, double[] omega,
                double hoverOmega, double propWash, int decimation) {
        this.measure(wbx, wby, wbz);
        if (noiseDps > 0.0) {
            for (int a = 0; a < 3; a++) {
                this.raw[a] += noiseDps * rng.gauss();
            }
        }
        double scale = vibrationDps > 0.0 && hoverOmega > 0.0 ? vibrationDps * MOTOR_SHARE * (1.0 + 3.0 * propWash) : 0.0;
        double roll = 0.0;
        double pitch = 0.0;
        double yaw = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double w = Math.abs(omega[i]);
            if (!Double.isFinite(w)) {
                continue;
            }
            double angle = this.rotorAngle[i] + w * this.h;
            if (angle >= TWO_PI) {
                angle -= TWO_PI * Math.floor(angle / TWO_PI);
            }
            this.rotorAngle[i] = angle;
            if (scale == 0.0) {
                continue;
            }
            double gain = antiAliasGain(w, this.h);
            if (gain == 0.0) {
                continue;
            }
            gain *= decimationGain(w, this.h, decimation);
            double ratio = w / hoverOmega;
            double amp = scale * ratio * ratio * gain;
            double phase = angle + i * GOLDEN_ANGLE;
            double c = SimMath.cos(phase);
            double s = SimMath.sin(phase);
            roll += amp * c;
            pitch += amp * s;
            yaw += YAW_COUPLING * amp * (i % 2 == 0 ? c : -s);
        }
        this.raw[FcAxes.ROLL] += roll;
        this.raw[FcAxes.PITCH] += pitch;
        this.raw[FcAxes.YAW] += yaw;
    }

    static double antiAliasGain(double omega, double h) {
        double x = Math.abs(omega) * h / Math.PI;
        if (!(x < 1.0)) {
            return 0.0;
        }
        double k = 1.0 - x * x;
        return k * k;
    }

    static double decimationGain(double omega, double h, int samples) {
        if (samples <= 1) {
            return 1.0;
        }
        double x = 0.5 * Math.abs(omega) * h;
        double den = samples * SimMath.sin(x);
        if (Math.abs(den) < 1.0E-12) {
            return 1.0;
        }
        return Math.min(1.0, Math.abs(SimMath.sin(samples * x) / den));
    }
}
