package de.lardtom.propwash.api.event;

import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

public final class EntityHit {
    public static final float MAX_DAMAGE = 40.0F;
    public static final float MAX_KNOCKBACK = 3.0F;

    private final Entity drone;
    private final ServerPlayer pilot;
    private final LivingEntity target;
    private final Vec3 relativeVelocity;
    private final double closingSpeed;
    private final Vec3 normal;
    private final Vec3 framePoint;
    private final Optional<AccessorySlot> slot;
    private final Optional<Identifier> accessory;
    private final float baseDamage;
    private float damage;
    private float knockback;

    public EntityHit(Entity drone, ServerPlayer pilot, LivingEntity target, Vec3 relativeVelocity, double closingSpeed, Vec3 normal,
                     Vec3 framePoint, Optional<AccessorySlot> slot, Optional<Identifier> accessory, float baseDamage, float knockback) {
        this.drone = Objects.requireNonNull(drone, "drone");
        this.pilot = Objects.requireNonNull(pilot, "pilot");
        this.target = Objects.requireNonNull(target, "target");
        this.relativeVelocity = Objects.requireNonNull(relativeVelocity, "relativeVelocity");
        this.closingSpeed = closingSpeed;
        this.normal = Objects.requireNonNull(normal, "normal");
        this.framePoint = Objects.requireNonNull(framePoint, "framePoint");
        this.slot = Objects.requireNonNull(slot, "slot");
        this.accessory = Objects.requireNonNull(accessory, "accessory");
        this.baseDamage = baseDamage;
        this.damage = baseDamage;
        this.knockback = knockback;
    }

    public Entity drone() {
        return this.drone;
    }

    public ServerPlayer pilot() {
        return this.pilot;
    }

    public LivingEntity target() {
        return this.target;
    }

    public Vec3 relativeVelocity() {
        return this.relativeVelocity;
    }

    public double closingSpeed() {
        return this.closingSpeed;
    }

    public Vec3 normal() {
        return this.normal;
    }

    public Vec3 framePoint() {
        return this.framePoint;
    }

    public Optional<AccessorySlot> slot() {
        return this.slot;
    }

    public Optional<Identifier> accessory() {
        return this.accessory;
    }

    public float baseDamage() {
        return this.baseDamage;
    }

    public float damage() {
        return this.damage;
    }

    public float knockback() {
        return this.knockback;
    }

    public void setDamage(float value) {
        if (Float.isFinite(value)) {
            this.damage = Math.max(0.0F, Math.min(MAX_DAMAGE, value));
        }
    }

    public void addDamage(float value) {
        this.setDamage(this.damage + value);
    }

    public void setKnockback(float value) {
        if (Float.isFinite(value)) {
            this.knockback = Math.max(0.0F, Math.min(MAX_KNOCKBACK, value));
        }
    }
}
