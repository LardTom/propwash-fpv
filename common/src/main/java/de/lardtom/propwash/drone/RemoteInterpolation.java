package de.lardtom.propwash.drone;

import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class RemoteInterpolation implements InterpolationHandler {
    public static final long RESYNC_NANOS = 250_000_000L;
    private static final double MOVED_ELSEWHERE = 1.0E-6;
    private static final double INSIDE_MARGIN = 1.0E-3;
    private static final double TICKS_PER_SECOND = 20.0;

    private final QuadcopterEntity drone;
    private final RemoteMotion motion = new RemoteMotion();
    private final double[] position = new double[3];
    private final Quaternionf rotation = new Quaternionf();
    private long tickClock = Long.MIN_VALUE;
    private @Nullable Vec3 applied;
    private boolean hasRotation;
    private float yRot;
    private float xRot;

    RemoteInterpolation(QuadcopterEntity drone) {
        this.drone = drone;
        this.motion.ground(this::floor);
    }

    private double floor(double x, double z, double fromY, double toY) {
        AABB box = this.drone.getDimensions(this.drone.getPose()).makeBoundingBox(x, fromY, z);
        if (!this.drone.level().noCollision(this.drone, box.deflate(INSIDE_MARGIN))) {
            return toY;
        }
        float scale = this.drone.scale();
        double reach = AirframeSpec.of(this.drone.build()).accessoryDepth(this.drone.orientation()) * (DroneScale.scaled(scale) ? scale : 1.0);
        if (reach > 0.0) {
            box = box.setMinY(box.minY - reach);
        }
        return fromY + Entity.collideBoundingBox(this.drone, new Vec3(0.0, toY - fromY, 0.0), box, this.drone.level(), List.of()).y;
    }

    public RemoteMotion motion() {
        return this.motion;
    }

    @Override
    public @Nullable PositionAndRotation target() {
        if (this.motion.empty()) {
            return null;
        }
        double[] latest = new double[3];
        this.motion.latest(latest);
        return new PositionAndRotation.Immutable(new Vec3(latest[0], latest[1], latest[2]), this.yRot, this.xRot);
    }

    @Override
    public boolean interpolateTo(@Nullable PositionPath path, float yRot, float xRot, boolean hasRotation) {
        if (hasRotation) {
            this.yRot = yRot;
            this.xRot = xRot;
            this.drone.remoteRotation(yRot, xRot);
        }
        if (path == null) {
            return true;
        }
        if (!this.drone.remoteMotionAllowed()) {
            return false;
        }
        Vec3 end = path.endPosition();
        long now = Util.getNanos();
        Vec3 velocity = this.drone.getDeltaMovement().scale(TICKS_PER_SECOND);
        if (this.motion.empty()) {
            this.motion.seed(now - RemoteMotion.TICK_NANOS, this.drone.getX(), this.drone.getY(), this.drone.getZ(), velocity.x, velocity.y, velocity.z);
        }
        if (this.motion.position(now, end.x, end.y, end.z)) {
            this.motion.motion(now, velocity.x, velocity.y, velocity.z);
        }
        return true;
    }

    public void orientation(Quaternionf value) {
        if (this.drone.remoteMotionAllowed()) {
            this.motion.orientation(Util.getNanos(), value);
        }
    }

    public void velocity(Vec3 perTick) {
        if (this.drone.remoteMotionAllowed()) {
            this.motion.motion(Util.getNanos(), perTick.x * TICKS_PER_SECOND, perTick.y * TICKS_PER_SECOND, perTick.z * TICKS_PER_SECOND);
        }
    }

    @Override
    public void interpolate() {
        if (!this.drone.remoteMotionAllowed()) {
            this.clearState();
            return;
        }
        if (this.motion.empty() && !this.motion.hasRotation()) {
            return;
        }
        if (this.applied != null && this.applied.distanceToSqr(this.drone.position()) > MOVED_ELSEWHERE) {
            this.clearState();
            return;
        }
        long now = Util.getNanos();
        long expected = this.tickClock == Long.MIN_VALUE ? now : this.tickClock + RemoteMotion.TICK_NANOS;
        this.tickClock = Math.abs(now - expected) > RESYNC_NANOS ? now : expected + (now - expected) / 8L;
        if (!this.motion.sample(this.tickClock, this.position, this.rotation)) {
            return;
        }
        if (!this.motion.empty()) {
            Vec3 at = new Vec3(this.position[0], this.position[1], this.position[2]);
            this.drone.setPos(at);
            this.applied = this.drone.position();
        }
        this.hasRotation = this.motion.hasRotation();
    }

    public boolean rotation(Quaternionf dest) {
        if (!this.hasRotation || !this.drone.remoteMotionAllowed()) {
            return false;
        }
        dest.set(this.rotation);
        return true;
    }

    @Override
    public void applyPredictedMovement(Vec3 delta) {
    }

    @Override
    public boolean hasActiveInterpolation() {
        return this.drone.remoteMotionAllowed() && this.motion.active(this.tickClock == Long.MIN_VALUE ? Util.getNanos() : this.tickClock);
    }

    @Override
    public void cancel() {
        this.clearState();
    }

    private void clearState() {
        this.motion.clear();
        this.applied = null;
        this.hasRotation = false;
        this.tickClock = Long.MIN_VALUE;
    }
}
