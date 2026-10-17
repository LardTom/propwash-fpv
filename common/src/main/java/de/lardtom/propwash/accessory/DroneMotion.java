package de.lardtom.propwash.accessory;

import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

public final class DroneMotion {
    private static final Map<QuadcopterEntity, Sample> SAMPLES = new WeakHashMap<>();

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
    }

    static synchronized void clear() {
        SAMPLES.clear();
    }

    public static synchronized Vec3 velocity(QuadcopterEntity drone) {
        if (drone.level().isClientSide()) {
            return new Vec3(drone.getX() - drone.xo, drone.getY() - drone.yo, drone.getZ() - drone.zo).scale(20.0);
        }
        Sample sample = SAMPLES.get(drone);
        return sample == null ? drone.getDeltaMovement().scale(20.0) : sample.velocity;
    }

    private record Sample(long tick, Vec3 position, Vec3 velocity) {
    }
}
