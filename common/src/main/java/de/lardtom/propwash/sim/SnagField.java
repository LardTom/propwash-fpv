package de.lardtom.propwash.sim;

import java.util.Arrays;

public final class SnagField {
    public static final int POINTS = AirframeSpec.MOTORS + 1;
    public static final int CENTER = AirframeSpec.MOTORS;
    public static final double MAX_LINEAR = 200.0;
    public static final double MAX_QUADRATIC = 50.0;
    public static final double MAX_HOLD = 50.0;
    public static final double MAX_PROP_LOAD = 10.0;

    public final double[] linear = new double[POINTS];
    public final double[] quadratic = new double[POINTS];
    public final double[] propLoad = new double[AirframeSpec.MOTORS];
    public double hold;

    public boolean active() {
        if (this.hold > 0.0) {
            return true;
        }
        for (int i = 0; i < POINTS; i++) {
            if (this.linear[i] > 0.0 || this.quadratic[i] > 0.0) {
                return true;
            }
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            if (this.propLoad[i] > 0.0) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        Arrays.fill(this.linear, 0.0);
        Arrays.fill(this.quadratic, 0.0);
        Arrays.fill(this.propLoad, 0.0);
        this.hold = 0.0;
    }

    public void copyTo(SnagField dst) {
        System.arraycopy(this.linear, 0, dst.linear, 0, POINTS);
        System.arraycopy(this.quadratic, 0, dst.quadratic, 0, POINTS);
        System.arraycopy(this.propLoad, 0, dst.propLoad, 0, AirframeSpec.MOTORS);
        dst.hold = this.hold;
    }

    void sanitizeFrom(SnagField src) {
        for (int i = 0; i < POINTS; i++) {
            this.linear[i] = SimMath.sanitize(src.linear[i], 0.0, MAX_LINEAR, 0.0);
            this.quadratic[i] = SimMath.sanitize(src.quadratic[i], 0.0, MAX_QUADRATIC, 0.0);
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.propLoad[i] = SimMath.sanitize(src.propLoad[i], 0.0, MAX_PROP_LOAD, 0.0);
        }
        this.hold = SimMath.sanitize(src.hold, 0.0, MAX_HOLD, 0.0);
    }
}
