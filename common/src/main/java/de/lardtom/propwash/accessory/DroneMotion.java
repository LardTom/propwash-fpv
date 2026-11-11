package de.lardtom.propwash.accessory;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.server.ServerRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

public final class DroneMotion {
    private static final Map<QuadcopterEntity, Sample> SAMPLES = new WeakHashMap<>();
    private static final Map<QuadcopterEntity, Piloted> PILOTED = new WeakHashMap<>();
    private static final double[] SCRATCH = new double[3];

    private DroneMotion() {
    }

    static synchronized void sample(QuadcopterEntity drone, long tick) {
        Vec3 position = drone.position();
        Sample last = SAMPLES.get(drone);
        Vec3 velocity = Vec3.ZERO;
        if (last != null && tick > last.tick && tick - last.tick <= 5) {
            velocity = position.subtract(last.position).scale(20.0 / (tick - last.tick));
        }
        SAMPLES.put(drone, new Sample(tick, position, velocity));
        if (drone.pilot().isEmpty()) {
            PILOTED.remove(drone);
        }
    }

    static synchronized void pilotMoved(QuadcopterEntity drone, ServerPlayer pilot, Vec3 from, Vec3 to) {
        double maxSpeed = StateValidation.refillRate(StateValidation.effectiveMaxSpeed(ServerRules.maxDroneSpeed(pilot)));
        pilotMoved(drone, from, to, Util.getNanos(), maxSpeed);
    }

    static synchronized void pilotMoved(QuadcopterEntity drone, Vec3 from, Vec3 to, long nanos, double maxSpeed) {
        Piloted piloted = PILOTED.computeIfAbsent(drone, key -> new Piloted());
        piloted.maxSpeed = maxSpeed;
        MotionTrack track = piloted.track;
        if (!track.isEmpty() && !track.endsAt(from.x, from.y, from.z)) {
            track.reset();
        }
        track.add(nanos, to.x, to.y, to.z);
    }

    static synchronized void clear() {
        SAMPLES.clear();
        PILOTED.clear();
    }

    public static synchronized Vec3 velocity(QuadcopterEntity drone) {
        if (drone.level().isClientSide()) {
            return new Vec3(drone.getX() - drone.xo, drone.getY() - drone.yo, drone.getZ() - drone.zo).scale(20.0);
        }
        Piloted piloted = drone.pilot().isPresent() ? PILOTED.get(drone) : null;
        if (piloted != null && piloted.track.estimate(Util.getNanos(), SCRATCH)) {
            MotionTrack.limit(SCRATCH, piloted.maxSpeed);
            return new Vec3(SCRATCH[0], SCRATCH[1], SCRATCH[2]);
        }
        Sample sample = SAMPLES.get(drone);
        Vec3 velocity = sample == null ? drone.getDeltaMovement().scale(20.0) : sample.velocity;
        if (piloted == null) {
            return velocity;
        }
        SCRATCH[0] = velocity.x;
        SCRATCH[1] = velocity.y;
        SCRATCH[2] = velocity.z;
        MotionTrack.limit(SCRATCH, piloted.maxSpeed);
        return new Vec3(SCRATCH[0], SCRATCH[1], SCRATCH[2]);
    }

    private static final class Piloted {
        final MotionTrack track = new MotionTrack();
        double maxSpeed = Double.POSITIVE_INFINITY;
    }

    private record Sample(long tick, Vec3 position, Vec3 velocity) {
    }
}
