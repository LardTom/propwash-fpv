package de.lardtom.propwash.mixin.drone;

import de.lardtom.propwash.interact.DroneBosses;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Warden.class)
public abstract class WardenMixin {
    @Inject(method = "increaseAngerAt(Lnet/minecraft/world/entity/Entity;IZ)V", at = @At("HEAD"))
    private void propwash$droneAnger(@Nullable Entity entity, int amount, boolean playSound, CallbackInfo ci) {
        DroneBosses.wardenAngered((Warden) (Object) this, entity, amount);
    }
}
