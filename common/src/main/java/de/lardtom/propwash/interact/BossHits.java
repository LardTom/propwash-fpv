package de.lardtom.propwash.interact;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.DroneWear;
import de.lardtom.propwash.drone.Knockback;
import de.lardtom.propwash.sim.ImpactSample;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class BossHits {
    public static final double WING_GAIN = 80.0;
    public static final double WING_MAX = 18.0;
    public static final double TURBULENCE_RADIUS = 14.0;
    public static final double TURBULENCE_GAIN = 1.6;
    public static final double TURBULENCE_SECONDS = 0.25;
    public static final double DOWNWASH = 0.35;
    public static final float BREATH_ELECTRONICS = 0.15F;
    public static final float BREATH_BATTERY = 0.1F;
    public static final float BREATH_MOTORS = 0.08F;
    public static final float BREATH_PROPS = 0.05F;
    private static final double GRAVITY = 9.81;

    private BossHits() {
    }

    public static ImpactSample sample(BossHit hit, DroneBuild build, Vec3 direction) {
        Vec3 n = unit(direction, new Vec3(0.0, 1.0, 0.0));
        float speed = (float) Math.min(ImpactSample.MAX_SPEED, hit.impactFactor() * build.frame().impactTolerance);
        return new ImpactSample(speed, 0.0F, (float) n.x, (float) n.y, (float) n.z, ImpactSample.CONTACT_MASK, 0, 0.0F);
    }

    public static boolean crash(BossHit hit, DroneBuild build) {
        return hit.impactFactor() >= 1.0;
    }

    public static PartWear wear(PartWear before, DroneBuild build, BossHit hit, Vec3 direction, float strength, long seed) {
        if (before == null || build == null || !(strength > 0.0F)) {
            return before;
        }
        if (hit == BossHit.DRAGON_BREATH) {
            return breath(before, strength);
        }
        if (!(hit.impactFactor() > 0.0)) {
            return before;
        }
        return DroneWear.apply(before, build, List.of(sample(hit, build, direction)), strength, seed);
    }

    public static PartWear breath(PartWear before, float strength) {
        float k = Math.max(0.0F, Math.min(3.0F, strength));
        PartWear out = before.withVideo(down(before.video(), BREATH_ELECTRONICS * k))
                .withStack(down(before.stack(), BREATH_ELECTRONICS * k))
                .withBattery(down(before.battery(), BREATH_BATTERY * k));
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            out = out.withMotor(position, down(out.motor(position), BREATH_MOTORS * k)).withProp(position, down(out.prop(position), BREATH_PROPS * k));
        }
        return out;
    }

    public static Vec3 velocityChange(BossHit hit, Vec3 direction) {
        Vec3 n = unit(direction, Vec3.ZERO);
        return new Vec3(n.x * hit.push(), n.y * hit.push() + hit.lift(), n.z * hit.push());
    }

    public static Vec3 wingVelocity(Vec3 fromBody) {
        Vec3 flat = new Vec3(fromBody.x, 0.0, fromBody.z);
        double d = flat.length();
        Vec3 away = d < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.scale(1.0 / d);
        double speed = Math.min(WING_MAX, WING_GAIN / Math.max(d, 1.0E-3));
        return new Vec3(away.x * speed, BossHit.DRAGON_WING.lift(), away.z * speed);
    }

    public static Vec3 ramVelocity(Vec3 fromHead, Vec3 dragonVelocity) {
        Vec3 away = unit(fromHead, new Vec3(0.0, 1.0, 0.0));
        Vec3 base = velocityChange(BossHit.DRAGON_RAM, away);
        Vec3 carried = dragonVelocity == null || !dragonVelocity.isFinite() ? Vec3.ZERO : dragonVelocity;
        return base.add(carried);
    }

    public static Vec3 impulse(Vec3 velocityChange, double massKg) {
        return Knockback.impulse(velocityChange, massKg, 1.0, false);
    }

    public static Vec3 turbulence(Vec3 fromBody, double dragonSpeed, double massKg, double r1, double r2, double r3) {
        double d = fromBody.length();
        if (!(massKg > 0.0) || !Double.isFinite(d) || d >= TURBULENCE_RADIUS) {
            return Vec3.ZERO;
        }
        double near = 1.0 - d / TURBULENCE_RADIUS;
        double speed = Double.isFinite(dragonSpeed) ? Math.max(0.0, dragonSpeed) : 0.0;
        double magnitude = massKg * GRAVITY * TURBULENCE_GAIN * near * near * (0.5 + Math.min(1.0, speed / 20.0));
        Vec3 away = unit(fromBody, new Vec3(1.0, 0.0, 0.0));
        Vec3 gust = new Vec3(r1 * 2.0 - 1.0, r2 * 2.0 - 1.0, r3 * 2.0 - 1.0);
        Vec3 dir = unit(away.add(gust).add(0.0, -DOWNWASH, 0.0), away);
        return dir.scale(magnitude);
    }

    private static float down(float condition, float amount) {
        return Math.max(0.0F, condition - amount);
    }

    private static Vec3 unit(Vec3 v, Vec3 fallback) {
        if (v == null || !v.isFinite()) {
            return fallback;
        }
        double length = v.length();
        return length < 1.0E-6 ? fallback : v.scale(1.0 / length);
    }
}
