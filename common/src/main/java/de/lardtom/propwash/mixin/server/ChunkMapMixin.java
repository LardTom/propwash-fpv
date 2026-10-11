package de.lardtom.propwash.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ViewAnchors;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @ModifyExpressionValue(method = "updateChunkTracking",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos propwash$anchorCenter(ChunkPos original, @Local(argsOnly = true) ServerPlayer player) {
        return ViewAnchors.center(player, original);
    }

    @ModifyExpressionValue(method = "updateChunkTracking",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;getPlayerViewDistance(Lnet/minecraft/server/level/ServerPlayer;)I"))
    private int propwash$anchorViewDistance(int original, @Local(argsOnly = true) ServerPlayer player) {
        return ViewAnchors.viewDistance(player, original);
    }

    @Inject(method = "onFullChunkStatusChange", at = @At("HEAD"))
    private void propwash$markGenerated(ChunkPos pos, FullChunkStatus status, CallbackInfo ci) {
        if (status.isOrAfter(FullChunkStatus.FULL)) {
            RangeManager.onChunkFull(this.level, pos);
        }
    }
}
