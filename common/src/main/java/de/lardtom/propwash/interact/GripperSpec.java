package de.lardtom.propwash.interact;

import de.lardtom.propwash.sim.SimTuning;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class GripperSpec {
    public static final double MAX_PAYLOAD_GRAMS = 400.0;
    public static final int MAX_STACKS = 4;
    public static final double RESERVE_SHARE = 0.5;
    public static final double MIN_LOADED_THRUST_RATIO = 1.3;
    public static final double REACH_SIDE = 0.6;
    public static final double REACH_DOWN = 0.9;
    public static final double REACH_UP = 0.25;
    public static final double MASS_STEP_GRAMS = 5.0;
    public static final int PICKUP_INTERVAL_TICKS = 4;
    public static final int RELEASE_AFTER_DISARMED_TICKS = 10;
    public static final int RELEASED_IGNORE_TICKS = 1200;
    public static final double REGRAB_DISTANCE = 1.5;
    public static final int MAX_NESTING = 4;

    private GripperSpec() {
    }

    public static double capacityGrams(double massGrams, double maxThrustGrams) {
        return capacityGrams(massGrams, maxThrustGrams, 1.0);
    }

    public static double capacityGrams(double massGrams, double maxThrustGrams, double gravityScale) {
        if (!Double.isFinite(massGrams) || !Double.isFinite(maxThrustGrams) || !(gravityScale >= 0.0) || !Double.isFinite(gravityScale)) {
            return 0.0;
        }
        double scale = Math.max(gravityScale, SimTuning.GRAVITY_REFERENCE_MIN);
        double share = RESERVE_SHARE * Math.max(0.0, maxThrustGrams - massGrams);
        double loaded = maxThrustGrams / (MIN_LOADED_THRUST_RATIO * scale) - massGrams;
        return Math.max(0.0, Math.min(MAX_PAYLOAD_GRAMS, Math.floor(Math.min(share, loaded))));
    }

    public static boolean mayTake(@Nullable UUID target, @Nullable UUID pilot) {
        return target == null || target.equals(pilot);
    }

    public static boolean awayFromDrop(double dx, double dy, double dz) {
        return dx * dx + dz * dz >= REGRAB_DISTANCE * REGRAB_DISTANCE || Math.abs(dy) >= REGRAB_DISTANCE;
    }

    public static long massStep(double payloadGrams) {
        if (!Double.isFinite(payloadGrams) || payloadGrams <= 0.0) {
            return 0L;
        }
        long steps = Math.round(payloadGrams / MASS_STEP_GRAMS);
        return Math.max(1L, steps) * (long) MASS_STEP_GRAMS;
    }
}
