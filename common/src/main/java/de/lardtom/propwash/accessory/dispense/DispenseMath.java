package de.lardtom.propwash.accessory.dispense;

import de.lardtom.propwash.interact.ItemWeights;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class DispenseMath {
    public static final double TICKS_PER_SECOND = 20.0;
    public static final double WIND_CHARGE_BLAST_NS = 0.8;
    public static final float DRONE_WIND_KNOCKBACK = 0.5F;
    public static final double ITEM_EJECT_SPEED = 0.2;
    public static final double ITEM_EJECT_SPREAD = 0.1;
    public static final double ITEM_EJECT_UP = 0.2;
    public static final double ITEM_ACCURACY = 6.0;
    public static final double TRIANGLE_SCALE = 0.0172275;

    public enum Kind {
        ARROW(0.02),
        POTION(0.35),
        EXPERIENCE_BOTTLE(0.35),
        WIND_CHARGE(0.05),
        SNOWBALL(0.1),
        EGG(0.06),
        FIRE_CHARGE(0.1),
        FIREWORK(0.1),
        TRIDENT(ItemWeights.TRIDENT_GRAMS / 1000.0),
        OTHER(0.05);

        public final double massKg;

        Kind(double massKg) {
            this.massKg = massKg;
        }
    }

    private DispenseMath() {
    }

    public static Direction nearest(double x, double y, double z) {
        Direction best = Direction.NORTH;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            double dot = direction.getStepX() * x + direction.getStepY() * y + direction.getStepZ() * z;
            if (dot > bestDot + 1.0E-12) {
                bestDot = dot;
                best = direction;
            }
        }
        return best;
    }

    public static Direction nearest(Vec3 forward) {
        return nearest(forward.x, forward.y, forward.z);
    }

    public static double muzzleSpeed(double powerBlocksPerTick, double speedScale) {
        double speed = powerBlocksPerTick * speedScale * TICKS_PER_SECOND;
        return Double.isFinite(speed) ? Math.max(0.0, speed) : 0.0;
    }

    public static double recoil(Kind kind, double powerBlocksPerTick, double speedScale) {
        double recoil = kind.massKg * muzzleSpeed(powerBlocksPerTick, speedScale);
        if (kind == Kind.WIND_CHARGE) {
            recoil += WIND_CHARGE_BLAST_NS;
        }
        return recoil;
    }

    public static float windKnockback(float vanilla, boolean fromDrone) {
        if (!fromDrone || !Float.isFinite(vanilla)) {
            return vanilla;
        }
        return vanilla * DRONE_WIND_KNOCKBACK;
    }

    public static boolean refusesFluid(boolean fluidContainer, boolean allowFluids) {
        return fluidContainer && !allowFluids;
    }

    public static boolean denied(boolean mayInteract, boolean actionRestricted, boolean mayBuild) {
        return !mayInteract || actionRestricted || !mayBuild;
    }

    public static int remaining(int count, int used, boolean consume) {
        if (!consume) {
            return count;
        }
        return Math.max(0, count - Math.max(0, used));
    }

    public static Vec3 inherit(Vec3 droneVelocityMetresPerSecond, Vec3 ownBlocksPerTick) {
        return ownBlocksPerTick.add(droneVelocityMetresPerSecond.scale(1.0 / TICKS_PER_SECOND));
    }

    public static Vec3 itemEject(Vec3 forward, double speed) {
        Vec3 f = forward.lengthSqr() > 1.0E-12 ? forward.normalize() : new Vec3(0.0, 0.0, -1.0);
        return new Vec3(f.x * speed, f.y * speed + ITEM_EJECT_UP, f.z * speed);
    }
}
