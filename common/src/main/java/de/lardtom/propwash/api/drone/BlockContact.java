package de.lardtom.propwash.api.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class BlockContact {
    public static final double MAX_RESTITUTION = 0.95;
    public static final double MIN_GRIP = 0.05;
    public static final double MAX_GRIP = 1.5;
    public static final double MAX_IMPACT_SCALE = 1.0;

    private final Entity drone;
    private final Level level;
    private final BlockPos pos;
    private final BlockState state;
    private final Direction side;
    private final Vec3 normal;
    private final double impactSpeed;
    private final double baseRestitution;
    private final double baseGrip;
    private final double baseImpactScale;
    private double restitution;
    private double grip;
    private double impactScale;

    public BlockContact(Entity drone, Level level, BlockPos pos, BlockState state, Direction side, Vec3 normal, double impactSpeed,
                        double restitution, double grip, double impactScale) {
        this.drone = Objects.requireNonNull(drone, "drone");
        this.level = Objects.requireNonNull(level, "level");
        this.pos = Objects.requireNonNull(pos, "pos").immutable();
        this.state = Objects.requireNonNull(state, "state");
        this.side = Objects.requireNonNull(side, "side");
        this.normal = Objects.requireNonNull(normal, "normal");
        this.impactSpeed = Double.isFinite(impactSpeed) ? Math.max(0.0, impactSpeed) : 0.0;
        this.baseRestitution = clampRestitution(restitution);
        this.baseGrip = clampGrip(grip);
        this.baseImpactScale = clampImpactScale(impactScale);
        this.restitution = this.baseRestitution;
        this.grip = this.baseGrip;
        this.impactScale = this.baseImpactScale;
    }

    public Entity drone() {
        return this.drone;
    }

    public Level level() {
        return this.level;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public BlockState state() {
        return this.state;
    }

    public Direction side() {
        return this.side;
    }

    public Vec3 normal() {
        return this.normal;
    }

    public double impactSpeed() {
        return this.impactSpeed;
    }

    public double baseRestitution() {
        return this.baseRestitution;
    }

    public double baseGrip() {
        return this.baseGrip;
    }

    public double baseImpactScale() {
        return this.baseImpactScale;
    }

    public double restitution() {
        return this.restitution;
    }

    public double grip() {
        return this.grip;
    }

    public double impactScale() {
        return this.impactScale;
    }

    public void setRestitution(double value) {
        if (Double.isFinite(value)) {
            this.restitution = clampRestitution(value);
        }
    }

    public void setGrip(double value) {
        if (Double.isFinite(value)) {
            this.grip = clampGrip(value);
        }
    }

    public void setImpactScale(double value) {
        if (Double.isFinite(value)) {
            this.impactScale = clampImpactScale(value);
        }
    }

    private static double clampRestitution(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(MAX_RESTITUTION, value)) : 0.0;
    }

    private static double clampGrip(double value) {
        return Double.isFinite(value) ? Math.max(MIN_GRIP, Math.min(MAX_GRIP, value)) : 1.0;
    }

    private static double clampImpactScale(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(MAX_IMPACT_SCALE, value)) : MAX_IMPACT_SCALE;
    }
}
