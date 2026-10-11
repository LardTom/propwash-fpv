package de.lardtom.propwash.video;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.sim.WearEffects;

public final class WearShake {
    private WearShake() {
    }

    public static double level(PartWear wear, double brittleness) {
        if (wear == null) {
            return 0.0;
        }
        double threshold = PartWear.DAMAGED_BELOW;
        double healthyProp = WearEffects.propVibration(PartWear.DAMAGED_BELOW, brittleness);
        double sum = 0.0;
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            sum += Math.max(0.0, WearEffects.propVibration(wear.prop(position), brittleness) - healthyProp);
            sum += WearEffects.MOTOR_VIBRATION * Math.max(0.0, threshold - unit(wear.motor(position)));
            sum += WearEffects.ARM_VIBRATION * Math.max(0.0, threshold - unit(wear.arm(position)));
        }
        return Math.min(WearEffects.MAX_VIBRATION, sum);
    }

    public static double atSpeed(double level, double rpmRatio) {
        if (!Double.isFinite(level) || !Double.isFinite(rpmRatio) || level <= 0.0) {
            return 0.0;
        }
        double ratio = Math.min(Math.max(rpmRatio, 0.0), CameraShake.RATIO_CAP);
        return Math.min(WearEffects.MAX_VIBRATION, level * ratio * ratio);
    }

    private static double unit(float condition) {
        if (!Float.isFinite(condition)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, condition));
    }
}
