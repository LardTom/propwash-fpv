package de.lardtom.propwash.accessory;

import de.lardtom.propwash.accessory.net.DronePushS2C;
import de.lardtom.propwash.api.drone.DronePhysics;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

public final class DronePushes {
    private static BiPredicate<QuadcopterEntity, Push> localApplier = (drone, push) -> false;

    private DronePushes() {
    }

    public static void setLocalApplier(BiPredicate<QuadcopterEntity, Push> applier) {
        localApplier = Objects.requireNonNull(applier, "applier");
    }

    public static Optional<Push> impulse(Vec3 impulse, Vec3 framePoint) {
        return Push.of(true, false, impulse, framePoint, 0.0);
    }

    public static Optional<Push> force(Vec3 force, boolean frameRelative, Vec3 framePoint, double seconds) {
        return Push.of(false, frameRelative, force, framePoint, seconds);
    }

    public static boolean apply(QuadcopterEntity drone, Push push) {
        if (drone.isRemoved()) {
            return false;
        }
        if (drone.level().isClientSide()) {
            return localApplier.test(drone, push);
        }
        if (drone.pilot().isPresent()) {
            LivingEntity pilot = drone.pilotEntity();
            if (pilot instanceof ServerPlayer player) {
                return PropwashNet.sendToPlayer(player, push.payload(drone.getId()));
            }
            return false;
        }
        DroneSim sim = AutonomousReturns.sim(drone);
        if (sim != null) {
            return applyToSim(sim, push);
        }
        double mass = AirframeSpec.of(drone.build()).massKg + AccessoryPayload.payloadGrams(drone) / 1000.0 + drone.cargoGrams() / 1000.0;
        Vec3 impulse = push.impulse() ? push.vector() : push.vector().scale(push.seconds());
        if (push.frameRelative()) {
            Vector3f v = new Vector3f((float) impulse.x, (float) impulse.y, (float) impulse.z);
            drone.orientation().transform(v);
            impulse = new Vec3(v.x, v.y, v.z);
        }
        drone.setDeltaMovement(drone.getDeltaMovement().add(impulse.scale(1.0 / (mass * 20.0))));
        drone.needsSync = true;
        return true;
    }

    public static boolean applyToSim(DroneSim sim, Push push) {
        Vec3 p = push.point();
        if (push.impulse()) {
            Vec3 j = push.vector();
            if (push.frameRelative()) {
                Quaterniond q = new Quaterniond();
                sim.getAttitude(q);
                Vector3d v = new Vector3d(-j.x, j.y, -j.z);
                q.transform(v);
                return sim.applyImpulse(v.x, v.y, v.z, p.x, p.y, p.z);
            }
            return sim.applyImpulse(j.x, j.y, j.z, p.x, p.y, p.z);
        }
        Vec3 f = push.vector();
        return sim.push(f.x, f.y, f.z, push.frameRelative(), p.x, p.y, p.z, push.seconds());
    }

    public static Push fromPayload(DronePushS2C payload) {
        return new Push(payload.impulse(), payload.frameRelative(), new Vec3(payload.fx(), payload.fy(), payload.fz()),
                new Vec3(payload.px(), payload.py(), payload.pz()), payload.seconds());
    }

    public record Push(boolean impulse, boolean frameRelative, Vec3 vector, Vec3 point, double seconds) {
        public static Optional<Push> of(boolean impulse, boolean frameRelative, Vec3 vector, Vec3 point, double seconds) {
            if (vector == null || point == null || !finite(vector) || !finite(point) || !Double.isFinite(seconds)) {
                return Optional.empty();
            }
            double length = vector.length();
            if (!(length > 0.0)) {
                return Optional.empty();
            }
            double limit = impulse ? DronePhysics.MAX_IMPULSE : DronePhysics.MAX_FORCE;
            Vec3 v = length > limit ? vector.scale(limit / length) : vector;
            double arm = point.length();
            Vec3 r = arm > DronePhysics.MAX_ARM ? point.scale(DronePhysics.MAX_ARM / arm) : point;
            if (!impulse && !(seconds > 0.0)) {
                return Optional.empty();
            }
            double s = impulse ? 0.0 : Math.min(DronePhysics.MAX_SECONDS, seconds);
            return Optional.of(new Push(impulse, frameRelative, v, r, s));
        }

        DronePushS2C payload(int droneId) {
            return new DronePushS2C(droneId, this.impulse, this.frameRelative, (float) this.vector.x, (float) this.vector.y, (float) this.vector.z,
                    (float) this.point.x, (float) this.point.y, (float) this.point.z, (float) this.seconds);
        }

        private static boolean finite(Vec3 v) {
            return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
        }
    }
}
