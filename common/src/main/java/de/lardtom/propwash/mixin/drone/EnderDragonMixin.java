package de.lardtom.propwash.mixin.drone;

import de.lardtom.propwash.interact.DroneBosses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {
    @Inject(method = "knockBack", at = @At("HEAD"))
    private void propwash$wingDrones(ServerLevel level, List<Entity> entities, CallbackInfo ci) {
        DroneBosses.dragonWing((EnderDragon) (Object) this, level, entities);
    }

    @Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V", at = @At("HEAD"))
    private void propwash$ramDrones(ServerLevel level, List<Entity> entities, CallbackInfo ci) {
        DroneBosses.dragonHead((EnderDragon) (Object) this, level, entities);
    }
}
