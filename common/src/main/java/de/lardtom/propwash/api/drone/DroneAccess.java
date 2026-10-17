package de.lardtom.propwash.api.drone;

import de.lardtom.propwash.api.flight.Intercept;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public interface DroneAccess {
    boolean isDrone(Entity entity);

    Optional<DroneView> view(Entity entity);

    List<DroneView> dronesNear(Level level, Vec3 center, double radius, boolean flyingOnly);

    Optional<DroneView> nearestDrone(Mob mob, double range, boolean requireSight, boolean flyingOnly);

    Optional<DronePhysics> physics(Entity drone);

    double obstacleDistance(Level level, Vec3 origin, Vec3 direction, double range, boolean entities, @Nullable Entity ignore);

    ObstacleScan scan(Entity drone, double range);

    Optional<Intercept.Aim> aim(Vec3 shooter, Entity drone, double projectileSpeedPerTick, double gravityPerTick);
}
