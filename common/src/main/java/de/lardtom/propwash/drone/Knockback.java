package de.lardtom.propwash.drone;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.accessory.DronePushes;
import de.lardtom.propwash.api.event.DroneKnockback;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.interact.DroneBosses;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class Knockback {
    public static final double MAX_IMPULSE = 50.0;
    public static final double TICKS_PER_SECOND = 20.0;
    public static final double MIN_DELTA = 1.0E-4;

    private static @Nullable QuadcopterEntity explosionDrone;
    private static long explosionTick = Long.MIN_VALUE;
    private static Vec3 explosionDelta = Vec3.ZERO;
    private static int fired;
    private static int forwarded;
    private static Vec3 lastImpulse = Vec3.ZERO;

    private Knockback() {
    }

    public static Vec3 impulse(Vec3 deltaVelocity, double massKg, double scale, boolean cancelled) {
        if (cancelled || !(scale > 0.0) || !(massKg > 0.0) || !deltaVelocity.isFinite() || !Double.isFinite(scale) || !Double.isFinite(massKg)) {
            return Vec3.ZERO;
        }
        Vec3 impulse = deltaVelocity.scale(massKg * scale);
        double length = impulse.length();
        if (!(length > 0.0)) {
            return Vec3.ZERO;
        }
        return length > MAX_IMPULSE ? impulse.scale(MAX_IMPULSE / length) : impulse;
    }

    public static double massKg(double airframeKg, double payloadGrams, double cargoGrams) {
        return airframeKg + (Math.max(0.0, payloadGrams) + Math.max(0.0, cargoGrams)) / 1000.0;
    }

    public static Vec3 metresPerSecond(Vec3 blocksPerTick) {
        return blocksPerTick.scale(TICKS_PER_SECOND);
    }

    public static DroneKnockback.Cause cause(@Nullable Entity explosionSource) {
        return explosionSource instanceof AbstractWindCharge ? DroneKnockback.Cause.WIND_CHARGE : DroneKnockback.Cause.EXPLOSION;
    }

    public static synchronized int fired() {
        return fired;
    }

    public static synchronized int forwarded() {
        return forwarded;
    }

    public static synchronized Vec3 lastImpulse() {
        return lastImpulse;
    }

    static synchronized void explosionPush(QuadcopterEntity drone, Vec3 impulse) {
        if (!(drone.level() instanceof ServerLevel level)) {
            return;
        }
        explosionDrone = drone;
        explosionTick = level.getGameTime();
        explosionDelta = impulse.isFinite() ? impulse : Vec3.ZERO;
    }

    static void pushed(QuadcopterEntity drone, double x, double y, double z) {
        if (!(drone.level() instanceof ServerLevel level)) {
            return;
        }
        synchronized (Knockback.class) {
            if (explosionDrone == drone && explosionTick == level.getGameTime()) {
                return;
            }
        }
        fire(drone, level, DroneKnockback.Cause.OTHER, new Vec3(x, y, z));
    }

    static void explosionHit(QuadcopterEntity drone, @Nullable Entity source) {
        if (!(drone.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 delta;
        synchronized (Knockback.class) {
            if (explosionDrone != drone || explosionTick != level.getGameTime()) {
                return;
            }
            delta = explosionDelta;
            explosionDrone = null;
            explosionDelta = Vec3.ZERO;
        }
        fire(drone, level, cause(source), delta, DroneBosses.pushingSource(source));
    }

    public static boolean forwards(boolean listeners, boolean bossRule, boolean bossSource) {
        return listeners || bossRule && bossSource;
    }

    private static void fire(QuadcopterEntity drone, ServerLevel level, DroneKnockback.Cause cause, Vec3 blocksPerTick) {
        fire(drone, level, cause, blocksPerTick, false);
    }

    private static void fire(QuadcopterEntity drone, ServerLevel level, DroneKnockback.Cause cause, Vec3 blocksPerTick, boolean bossSource) {
        boolean bossRule = bossSource && DroneBosses.enabled(level);
        if (!forwards(PropwashEvents.DRONE_KNOCKBACK.hasListeners(), bossRule, bossSource) || drone.isRemoved() || !blocksPerTick.isFinite()
                || blocksPerTick.lengthSqr() < MIN_DELTA * MIN_DELTA) {
            return;
        }
        if (drone.pilot().isEmpty() && !AutonomousReturns.flying(drone)) {
            return;
        }
        DroneKnockback event = new DroneKnockback(drone, level, cause, metresPerSecond(blocksPerTick));
        if (bossRule) {
            event.setScale(1.0);
        }
        try {
            PropwashEvents.DRONE_KNOCKBACK.fire(listener -> listener.onDroneKnockback(event));
        } catch (RuntimeException e) {
            Constants.LOG.warn("Event drone_knockback: Listener fehlgeschlagen", e);
        }
        double mass = massKg(AirframeSpec.of(drone.build()).massKg, AccessoryPayload.payloadGrams(drone), drone.cargoGrams());
        Vec3 impulse = impulse(event.deltaVelocity(), mass, event.scale(), event.cancelled());
        synchronized (Knockback.class) {
            fired++;
        }
        if (impulse.lengthSqr() <= 0.0) {
            return;
        }
        boolean applied = DronePushes.impulse(impulse, Vec3.ZERO).map(push -> DronePushes.apply(drone, push)).orElse(false);
        if (applied) {
            synchronized (Knockback.class) {
                forwarded++;
                lastImpulse = impulse;
            }
        }
    }
}
