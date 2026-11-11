package de.lardtom.propwash.accessory.dispense;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.Optional;

public final class DroneWindBurst extends ExplosionDamageCalculator {
    private final ExplosionDamageCalculator vanilla;

    private DroneWindBurst(ExplosionDamageCalculator vanilla) {
        this.vanilla = vanilla;
    }

    public static ExplosionDamageCalculator of(Projectile charge, ExplosionDamageCalculator vanilla) {
        if (vanilla == null || charge.level().isClientSide() || !OwnShots.fromDrone(charge)) {
            return vanilla;
        }
        return new DroneWindBurst(vanilla);
    }

    @Override
    public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState block, FluidState fluid) {
        return this.vanilla.getBlockExplosionResistance(explosion, level, pos, block, fluid);
    }

    @Override
    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
        return this.vanilla.shouldBlockExplode(explosion, level, pos, state, power);
    }

    @Override
    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
        return this.vanilla.shouldDamageEntity(explosion, entity);
    }

    @Override
    public float getKnockbackMultiplier(Entity entity) {
        return DispenseMath.windKnockback(this.vanilla.getKnockbackMultiplier(entity), true);
    }

    @Override
    public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
        return this.vanilla.getEntityDamageAmount(explosion, entity, exposure);
    }
}
