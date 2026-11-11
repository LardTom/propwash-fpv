package de.lardtom.propwash.accessory.dispense;

import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class OwnShots {
    public static final ProjectileDeflection PASS = (projectile, entity, random, power) -> {
    };

    private static final Map<Projectile, UUID> SHOTS = new WeakHashMap<>();
    private static int passed;
    private static long pruned = Long.MIN_VALUE;

    private OwnShots() {
    }

    static synchronized void protect(Projectile projectile, QuadcopterEntity drone) {
        SHOTS.put(projectile, drone.getUUID());
    }

    public static ProjectileDeflection deflection(QuadcopterEntity drone, Projectile projectile, ProjectileDeflection vanilla) {
        if (drone.level().isClientSide() || !protects(drone, projectile)) {
            return vanilla;
        }
        synchronized (OwnShots.class) {
            passed++;
        }
        return PASS;
    }

    public static synchronized boolean protects(QuadcopterEntity drone, Projectile projectile) {
        UUID firedBy = SHOTS.get(projectile);
        return firedBy != null && firedBy.equals(drone.getUUID());
    }

    public static synchronized boolean fromDrone(Projectile projectile) {
        return SHOTS.containsKey(projectile);
    }

    public static boolean protects(QuadcopterEntity drone, @Nullable Entity entity) {
        return entity instanceof Projectile projectile && protects(drone, projectile);
    }

    public static boolean from(QuadcopterEntity drone, DamageSource source) {
        return protects(drone, source.getDirectEntity()) || protects(drone, source.getEntity());
    }

    public static synchronized int tracked() {
        return SHOTS.size();
    }

    public static synchronized int passed() {
        return passed;
    }

    static synchronized void tick(QuadcopterEntity drone) {
        long now = drone.level().getGameTime();
        if (SHOTS.isEmpty() || now == pruned) {
            return;
        }
        pruned = now;
        SHOTS.keySet().removeIf(Entity::isRemoved);
    }

    static synchronized void clear() {
        SHOTS.clear();
        passed = 0;
        pruned = Long.MIN_VALUE;
    }
}
