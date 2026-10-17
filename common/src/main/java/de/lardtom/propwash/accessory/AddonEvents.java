package de.lardtom.propwash.accessory;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.event.DroneDamage;
import de.lardtom.propwash.api.event.EntityHit;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class AddonEvents {
    public static final double MOUNT_COSINE = 0.5;

    private AddonEvents() {
    }

    public static boolean wantsHits() {
        return PropwashEvents.DRONE_HIT_ENTITY.hasListeners();
    }

    public static EntityHit entityHit(QuadcopterEntity drone, ServerPlayer pilot, LivingEntity target, Vec3 relative, double closingSpeed,
                                      Vec3 normal, float baseDamage, float knockback) {
        AirframeSpec p = AirframeSpec.of(drone.build());
        Vec3 com = MountPoses.center(drone);
        AABB box = target.getBoundingBox();
        Vec3 contact = new Vec3(clamp(com.x, box.minX, box.maxX), clamp(com.y, box.minY, box.maxY), clamp(com.z, box.minZ, box.maxZ));
        Vec3 frame = MountPoses.toFrameMetres(com, drone.orientation(), p, contact);
        Optional<AccessoryMount> mount = MountPoses.mountToward(drone.build(), p, frame, MOUNT_COSINE);
        Optional<AccessoryType> type = mount.flatMap(m -> drone.build().accessories().at(m));
        EntityHit hit = new EntityHit(drone, pilot, target, relative, closingSpeed, normal, frame, mount.map(AccessoryMount::slot),
                type.map(AccessoryType::id), baseDamage, knockback);
        try {
            PropwashEvents.DRONE_HIT_ENTITY.fire(listener -> listener.onDroneHitEntity(hit));
        } catch (RuntimeException e) {
            Constants.LOG.warn("Event drone_hit_entity: Listener fehlgeschlagen", e);
        }
        return hit;
    }

    static float damage(QuadcopterEntity drone, ServerLevel level, DamageSource source, float amount) {
        if (!PropwashEvents.DRONE_DAMAGED.hasListeners()) {
            return amount;
        }
        DroneDamage damage = new DroneDamage(drone, level, source, amount);
        try {
            PropwashEvents.DRONE_DAMAGED.fire(listener -> listener.onDroneDamaged(damage));
        } catch (RuntimeException e) {
            Constants.LOG.warn("Event drone_damaged: Listener fehlgeschlagen", e);
        }
        return damage.cancelled() ? -1.0F : damage.amount();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
