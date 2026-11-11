package de.lardtom.propwash.api.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class DroneKnockback {
    public static final double MAX_SCALE = 2.0;

    public enum Cause {
        EXPLOSION,
        WIND_CHARGE,
        OTHER
    }

    private final Entity drone;
    private final ServerLevel level;
    private final Cause cause;
    private final Vec3 deltaVelocity;
    private double scale;
    private boolean cancelled;

    public DroneKnockback(Entity drone, ServerLevel level, Cause cause, Vec3 deltaVelocity) {
        this.drone = Objects.requireNonNull(drone, "drone");
        this.level = Objects.requireNonNull(level, "level");
        this.cause = Objects.requireNonNull(cause, "cause");
        this.deltaVelocity = Objects.requireNonNull(deltaVelocity, "deltaVelocity");
    }

    public Entity drone() {
        return this.drone;
    }

    public ServerLevel level() {
        return this.level;
    }

    public Cause cause() {
        return this.cause;
    }

    public Vec3 deltaVelocity() {
        return this.deltaVelocity;
    }

    public double scale() {
        return this.scale;
    }

    public void setScale(double value) {
        if (Double.isFinite(value)) {
            this.scale = Math.max(0.0, Math.min(MAX_SCALE, value));
        }
    }

    public boolean cancelled() {
        return this.cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
