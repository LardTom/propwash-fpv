package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;

import java.util.SplittableRandom;

public final class CrashDamage {
    public static final double MIN_SPEED = 1.5;
    public static final double MIN_SPEED_FRACTION = 0.2;
    public static final double TANGENTIAL_WEIGHT = 0.3;
    public static final double PROP_RESISTANCE = 7.5;
    public static final double MOTOR_RESISTANCE = 70.0;
    public static final double VIDEO_RESISTANCE = 150.0;
    public static final double ARM_RESISTANCE = 200.0;
    public static final double STACK_RESISTANCE = 450.0;
    public static final double BATTERY_RESISTANCE = 500.0;
    public static final double DUCT_PROTECTION = 0.35;
    public static final double STRIKE_LOAD = 0.045;
    public static final double STRIKE_GRAZE = 0.2;
    public static final double MAX_STRIKE_SPIN = 2.0;
    public static final double ELASTIC_LOAD = 0.08;
    public static final double CHIP_GAIN = 0.25;
    public static final double SPLINTER_GAIN = 0.05;
    public static final double CRACK_LOAD = 0.4;
    public static final double CRACK_MIN = 0.3;
    public static final double CRACK_SPREAD = 0.3;
    public static final double SHATTER_FROM_LOAD = 0.08;
    public static final double SHATTER_FULL_LOAD = 0.8;
    public static final double STIFF_PROP_MOTOR_SHARE = 0.5;
    public static final double PUFF_DOSE = 6.0;
    public static final double PUFF_BASE_CHANCE = 0.15;
    public static final double PUFF_MAX_CHANCE = 0.6;
    public static final double MAX_STRENGTH = 3.0;
    public static final double JITTER = 0.25;
    public static final double MIN_CRASH_WEAR = 0.0015;
    public static final int FRONT_RIGHT_MOTOR = 1;
    public static final int FRONT_LEFT_MOTOR = 3;

    private CrashDamage() {
    }

    public static double thresholdSpeed(DroneBuild build) {
        return Math.max(MIN_SPEED, MIN_SPEED_FRACTION * build.frame().impactTolerance);
    }

    public static double effectiveSpeed(ImpactSample impact) {
        double v = impact.speed();
        double t = TANGENTIAL_WEIGHT * impact.tangential();
        return Math.sqrt(v * v + t * t);
    }

    public static double severity(DroneBuild build, ImpactSample impact) {
        double excess = effectiveSpeed(impact) - thresholdSpeed(build);
        return excess > 0.0 ? excess / build.frame().impactTolerance : 0.0;
    }

    public static double dose(DroneBuild build, ImpactSample impact) {
        double s = severity(build, impact);
        return s * s;
    }

    public static boolean crash(DroneBuild build, ImpactSample impact) {
        return effectiveSpeed(impact) >= build.frame().impactTolerance;
    }

    public static boolean harmful(DroneBuild build, ImpactSample impact) {
        return severity(build, impact) > 0.0 || impact.struck() != 0;
    }

    public static double clampStrength(double strength) {
        if (!Double.isFinite(strength) || strength <= 0.0) {
            return 0.0;
        }
        return Math.min(MAX_STRENGTH, strength);
    }

    public static PartWear apply(PartWear wear, DroneBuild build, ImpactSample raw, double strength, long seed) {
        double k = clampStrength(strength);
        if (k <= 0.0 || wear == null || build == null || raw == null) {
            return wear;
        }
        ImpactSample impact = raw.sanitized();
        double rawDose = dose(build, impact);
        if (rawDose <= 0.0 && impact.struck() == 0) {
            return wear;
        }
        double d = rawDose * k;
        double floor = crash(build, impact) ? MIN_CRASH_WEAR * k : 0.0;
        SplittableRandom rng = new SplittableRandom(seed);
        boolean ducted = build.frame().ducted;
        boolean top = impact.normalY() < -0.5F;
        boolean bottom = impact.normalY() > 0.5F;
        double front = Math.max(0.0, -impact.normalZ());
        float[] arms = wear.arms();
        float[] motors = wear.motors();
        float[] props = wear.props();
        double propDurability = build.prop().definition().basics().durability();
        double motorDurability = build.motor().definition().basics().durability();
        double frameDurability = build.frame().definition().basics().durability();
        PropFailure failure = PropFailure.of(build.prop().definition());
        double approach = effectiveSpeed(impact) / build.frame().impactTolerance;
        double spin = Math.min(MAX_STRIKE_SPIN, impact.strikeSpin());
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            int motor = PartWear.simMotor(position);
            boolean tip = impact.touches(ImpactSample.HULL_TIP + motor);
            boolean under = impact.touches(ImpactSample.HULL_MOTOR + motor);
            boolean struck = impact.struck(motor);
            boolean frontMotor = motor == FRONT_RIGHT_MOTOR || motor == FRONT_LEFT_MOTOR;
            double propWeight = tip ? 1.5 : under ? 0.6 : 0.35;
            if (top) {
                propWeight = Math.max(propWeight, 1.0);
            }
            if (frontMotor) {
                propWeight = Math.max(propWeight, front);
            }
            if (bottom && !tip) {
                propWeight = Math.min(propWeight, 0.25);
            }
            double load = d * propWeight / PROP_RESISTANCE * jitter(rng);
            if (struck) {
                load += strikeLoad(spin, approach) * k * jitter(rng);
            }
            if (ducted) {
                load *= DUCT_PROTECTION;
            }
            props[position] -= (float) Math.max(floor, propLoss(failure, load / propDurability, rng));
            double motorWeight = tip ? 1.2 : under ? 1.0 : top ? 0.8 : 0.35;
            if (tip || struck) {
                motorWeight *= 1.0 + STIFF_PROP_MOTOR_SHARE * failure.shatterShare();
            }
            motors[position] -= (float) Math.max(floor, d * motorWeight / MOTOR_RESISTANCE * jitter(rng) / motorDurability);
            double armWeight = tip || under ? 1.5 : 0.3;
            arms[position] -= (float) Math.max(floor, d * armWeight / ARM_RESISTANCE * jitter(rng) / frameDurability);
        }
        double videoWeight = Math.max(0.3, 1.5 * front);
        if (impact.touches(ImpactSample.HULL_ELECTRONICS)) {
            videoWeight = Math.max(videoWeight, 1.5);
        }
        float video = wear.video() - (float) Math.max(floor, d * videoWeight / VIDEO_RESISTANCE * jitter(rng)
                / build.video().definition().basics().durability());
        double stackWeight = impact.touches(ImpactSample.HULL_BOTTOM) ? 1.0 : 0.6;
        float stack = wear.stack() - (float) Math.max(floor, d * stackWeight / STACK_RESISTANCE * jitter(rng)
                / build.stack().definition().basics().durability());
        boolean onTop = FrameGeometry.of(build.frame()).batteryOnTop();
        boolean batteryHit = impact.touches(ImpactSample.HULL_BATTERY_FRONT) || impact.touches(ImpactSample.HULL_BATTERY_BACK)
                || (onTop ? top : bottom);
        double batteryWeight = batteryHit ? 1.2 : 0.5;
        double batteryDurability = build.battery().definition().basics().durability();
        float battery = wear.battery() - (float) Math.max(floor, d * batteryWeight / BATTERY_RESISTANCE * jitter(rng) / batteryDurability);
        if (batteryHit && d > PUFF_DOSE) {
            double chance = Math.min(PUFF_MAX_CHANCE, PUFF_BASE_CHANCE + 0.35 * (d - PUFF_DOSE) / PUFF_DOSE);
            if (rng.nextDouble() < chance) {
                battery -= (float) ((0.3 + 0.3 * rng.nextDouble()) / batteryDurability);
            }
        }
        return PartWear.of(clamp(arms), clamp(stack), clamp(motors), clamp(props), clamp(video), clamp(battery));
    }

    public static double strikeLoad(double spin, double approach) {
        double w = Double.isFinite(spin) ? Math.max(0.0, Math.min(MAX_STRIKE_SPIN, spin)) : 0.0;
        double a = Double.isFinite(approach) ? Math.max(0.0, approach) : 0.0;
        return STRIKE_LOAD * w * w * (STRIKE_GRAZE + a);
    }

    public static double propLoss(PropFailure failure, double load, SplittableRandom rng) {
        double crackRoll = rng.nextDouble();
        double crackSize = rng.nextDouble();
        double shatterRoll = rng.nextDouble();
        double over = load - ELASTIC_LOAD;
        if (!(over > 0.0)) {
            return 0.0;
        }
        if (shatterRoll < failure.shatterShare() * SimMath.smoothstep(SHATTER_FROM_LOAD, SHATTER_FULL_LOAD, load)) {
            return 1.0;
        }
        double loss = over * (failure.bendShare() + CHIP_GAIN * failure.crackShare() + SPLINTER_GAIN * failure.shatterShare());
        if (crackRoll < failure.crackShare() * (1.0 - Math.exp(-over / CRACK_LOAD))) {
            loss += CRACK_MIN + CRACK_SPREAD * crackSize;
        }
        return loss;
    }

    private static double jitter(SplittableRandom rng) {
        return 1.0 - JITTER + 2.0 * JITTER * rng.nextDouble();
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float[] clamp(float[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = clamp(values[i]);
        }
        return values;
    }
}
