package de.lardtom.propwash.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import de.lardtom.propwash.rescue.HomeResets;
import de.lardtom.propwash.server.ViewAnchors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    @ModifyExpressionValue(method = "updatePlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 propwash$anchorPosition(Vec3 original, @Local(argsOnly = true) ServerPlayer player) {
        return ViewAnchors.position(player, original);
    }

    @ModifyExpressionValue(method = "updatePlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
    private boolean propwash$keepResetDrone(boolean original, @Local(argsOnly = true) ServerPlayer player) {
        return original || HomeResets.keepTracked(this.entity, player);
    }
}
