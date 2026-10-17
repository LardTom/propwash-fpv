package de.lardtom.propwash.api.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

public final class DroneDamage {
    private final Entity drone;
    private final ServerLevel level;
    private final DamageSource source;
    private final float originalAmount;
    private float amount;
    private boolean cancelled;

    public DroneDamage(Entity drone, ServerLevel level, DamageSource source, float amount) {
        this.drone = Objects.requireNonNull(drone, "drone");
        this.level = Objects.requireNonNull(level, "level");
        this.source = Objects.requireNonNull(source, "source");
        this.originalAmount = amount;
        this.amount = amount;
    }

    public Entity drone() {
        return this.drone;
    }

    public ServerLevel level() {
        return this.level;
    }

    public DamageSource source() {
        return this.source;
    }

    public float originalAmount() {
        return this.originalAmount;
    }

    public float amount() {
        return this.amount;
    }

    public void setAmount(float value) {
        if (Float.isFinite(value)) {
            this.amount = Math.max(0.0F, value);
        }
    }

    public boolean cancelled() {
        return this.cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
