package de.lardtom.propwash.mixin.drone;

import de.lardtom.propwash.interact.DroneVibrations;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerBlockEntityMixin {
    @Inject(method = "tryGetPlayer", at = @At("HEAD"), cancellable = true)
    private static void propwash$dronePilot(@Nullable Entity sourceEntity, CallbackInfoReturnable<@Nullable ServerPlayer> cir) {
        ServerPlayer pilot = DroneVibrations.pilot(sourceEntity);
        if (pilot != null) {
            cir.setReturnValue(pilot);
        }
    }
}
