package de.lardtom.propwash.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import de.lardtom.propwash.server.ViewAnchors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {
    @ModifyExpressionValue(method = "sendNextChunks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos propwash$anchorSortCenter(ChunkPos original, @Local(argsOnly = true) ServerPlayer player) {
        return ViewAnchors.center(player, original);
    }
}
