package de.lardtom.propwash.mixin.accessory;

import de.lardtom.propwash.accessory.dispense.DroneWindBurst;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.ExplosionDamageCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WindCharge.class)
public abstract class WindChargeMixin {
    @ModifyArg(method = "explode",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V"),
            index = 2)
    private ExplosionDamageCalculator propwash$droneWindBurst(ExplosionDamageCalculator calculator) {
        return DroneWindBurst.of((WindCharge) (Object) this, calculator);
    }
}
