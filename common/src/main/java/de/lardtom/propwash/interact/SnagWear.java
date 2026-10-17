package de.lardtom.propwash.interact;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.Telemetry;

import java.util.Arrays;

public final class SnagWear {
    private final double[] pending = new double[AirframeSpec.MOTORS];
    private final double[] rpm = new double[AirframeSpec.MOTORS];

    public void add(SnagSampler sampler, Telemetry telemetry, double omegaMax, double seconds) {
        if (!sampler.touching() || !(seconds > 0.0) || !telemetry.armed) {
            return;
        }
        double rpmMax = omegaMax * 60.0 / (2.0 * Math.PI);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double r = telemetry.motorRpm[i];
            this.rpm[i] = rpmMax > 0.0 && Double.isFinite(r) ? Math.abs(r) / rpmMax : 0.0;
        }
        SnagMath.addWear(sampler.exposures(), this.rpm, seconds, this.pending);
    }

    public boolean any() {
        for (double v : this.pending) {
            if (v > 0.0) {
                return true;
            }
        }
        return false;
    }

    public float[] drain() {
        float[] out = new float[PartWear.POSITIONS];
        for (int motor = 0; motor < AirframeSpec.MOTORS; motor++) {
            out[PartWear.positionOfSimMotor(motor)] += (float) this.pending[motor];
        }
        Arrays.fill(this.pending, 0.0);
        return out;
    }

    public void clear() {
        Arrays.fill(this.pending, 0.0);
    }

    public static PartWear apply(PartWear wear, float[] propWear, float allowance, float strength) {
        PartWear result = wear;
        if (propWear == null || !(strength > 0.0F)) {
            return result;
        }
        for (int position = 0; position < Math.min(PartWear.POSITIONS, propWear.length); position++) {
            float amount = propWear[position];
            if (!Float.isFinite(amount) || amount <= 0.0F) {
                continue;
            }
            float applied = Math.min(amount, allowance) * strength;
            if (applied > 0.0F) {
                result = result.withProp(position, Math.max(0.0F, result.prop(position) - applied));
            }
        }
        return result;
    }
}
