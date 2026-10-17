package de.lardtom.propwash.accessory;

import de.lardtom.propwash.api.drone.DroneAccess;
import de.lardtom.propwash.api.drone.DronePhysics;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.drone.ObstacleScan;
import de.lardtom.propwash.api.flight.Intercept;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DroneQueries implements DroneAccess {
    public static final DroneQueries INSTANCE = new DroneQueries();
    public static final double MAX_RANGE = 128.0;
    public static final double MAX_SCAN = 64.0;

    private DroneQueries() {
    }

    @Override
    public boolean isDrone(Entity entity) {
        return entity instanceof QuadcopterEntity;
    }

    @Override
    public Optional<DroneView> view(Entity entity) {
        if (!(entity instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return Optional.empty();
        }
        Map<AccessorySlot, Identifier> accessories = new EnumMap<>(AccessorySlot.class);
        for (Map.Entry<AccessoryMount, AccessoryType> entry : drone.build().accessories().byMount().entrySet()) {
            accessories.put(entry.getKey().slot(), entry.getValue().id());
        }
        boolean autopilot = !drone.level().isClientSide() && AutonomousReturns.flying(drone);
        boolean flying = drone.pilot().isPresent() || autopilot;
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
        drone.orientation().transform(forward);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
        drone.orientation().transform(up);
        double mass = AirframeSpec.of(drone.build()).massKg + (AccessoryPayload.payloadGrams(drone) + drone.cargoGrams()) / 1000.0;
        return Optional.of(new DroneView(drone, drone.pilot(), flying, drone.isArmed(), autopilot, MountPoses.center(drone),
                DroneMotion.velocity(drone), new Vec3(forward.x, forward.y, forward.z), new Vec3(up.x, up.y, up.z), mass, accessories));
    }

    @Override
    public List<DroneView> dronesNear(Level level, Vec3 center, double radius, boolean flyingOnly) {
        double r = Math.max(0.0, Math.min(MAX_RANGE, radius));
        AABB box = new AABB(center.subtract(r, r, r), center.add(r, r, r));
        List<DroneView> out = new ArrayList<>();
        for (QuadcopterEntity drone : level.getEntitiesOfClass(QuadcopterEntity.class, box, d -> d.isAlive()
                && d.position().distanceToSqr(center) <= r * r)) {
            this.view(drone).filter(view -> !flyingOnly || view.flying()).ifPresent(out::add);
        }
        out.sort(Comparator.comparingDouble(view -> view.center().distanceToSqr(center)));
        return out;
    }

    @Override
    public Optional<DroneView> nearestDrone(Mob mob, double range, boolean requireSight, boolean flyingOnly) {
        for (DroneView view : this.dronesNear(mob.level(), mob.getEyePosition(), range, flyingOnly)) {
            if (!requireSight || mob.getSensing().hasLineOfSight(view.entity())) {
                return Optional.of(view);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<DronePhysics> physics(Entity drone) {
        if (!(drone instanceof QuadcopterEntity quad)) {
            return Optional.empty();
        }
        return Optional.of(new DronePhysics() {
            @Override
            public boolean impulse(Vec3 impulse, Vec3 framePoint) {
                return DronePushes.impulse(impulse, framePoint).map(push -> DronePushes.apply(quad, push)).orElse(false);
            }

            @Override
            public boolean push(Vec3 force, boolean frameRelative, Vec3 framePoint, double seconds) {
                return DronePushes.force(force, frameRelative, framePoint, seconds).map(push -> DronePushes.apply(quad, push)).orElse(false);
            }
        });
    }

    @Override
    public double obstacleDistance(Level level, Vec3 origin, Vec3 direction, double range, boolean entities, @Nullable Entity ignore) {
        double r = Math.max(0.0, Math.min(MAX_RANGE, range));
        double length = direction.length();
        if (!(length > 1.0E-9) || !(r > 0.0)) {
            return r;
        }
        Vec3 end = origin.add(direction.scale(r / length));
        BlockHitResult block = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        double best = block.getType() == HitResult.Type.MISS ? r : block.getLocation().distanceTo(origin);
        if (entities) {
            Vec3 stop = origin.add(direction.scale(best / length));
            AABB box = new AABB(origin, stop).inflate(1.0);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, ignore, origin, stop, box,
                    e -> e != ignore && !e.isSpectator() && e.isPickable(), 0.0F);
            if (hit != null) {
                best = Math.min(best, hit.getLocation().distanceTo(origin));
            }
        }
        return best;
    }

    @Override
    public ObstacleScan scan(Entity drone, double range) {
        double r = Math.max(0.0, Math.min(MAX_SCAN, range));
        double[] distances = new double[SensorDirection.values().length];
        Vec3 origin = drone instanceof QuadcopterEntity quad ? MountPoses.center(quad) : drone.position();
        for (SensorDirection direction : SensorDirection.values()) {
            Vector3f d = new Vector3f((float) direction.x(), (float) direction.y(), (float) direction.z());
            if (drone instanceof QuadcopterEntity quad) {
                quad.orientation().transform(d);
            }
            distances[direction.ordinal()] = this.obstacleDistance(drone.level(), origin, new Vec3(d.x, d.y, d.z), r, false, drone);
        }
        return new ObstacleScan(r, distances);
    }

    @Override
    public Optional<Intercept.Aim> aim(Vec3 shooter, Entity drone, double projectileSpeedPerTick, double gravityPerTick) {
        Vec3 target = drone instanceof QuadcopterEntity quad ? MountPoses.center(quad) : drone.getBoundingBox().getCenter();
        Vec3 velocity = drone instanceof QuadcopterEntity quad ? DroneMotion.velocity(quad).scale(1.0 / 20.0) : drone.getDeltaMovement();
        return Intercept.aim(shooter.x, shooter.y, shooter.z, target.x, target.y, target.z, velocity.x, velocity.y, velocity.z,
                projectileSpeedPerTick, gravityPerTick, 200.0);
    }
}
