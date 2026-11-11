package de.lardtom.propwash.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.lardtom.propwash.capture.CameraTrackingView;
import de.lardtom.propwash.capture.CaptureServer;
import de.lardtom.propwash.capture.TrackingDiff;
import de.lardtom.propwash.server.ViewAnchors;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkMap.class)
public abstract class CameraChunkMapMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    protected abstract int getPlayerViewDistance(ServerPlayer player);

    @Shadow
    protected abstract void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView next);

    @Shadow
    protected abstract void markChunkPendingToSend(ServerPlayer player, ChunkPos pos);

    @Shadow
    private static void dropChunk(ServerPlayer player, ChunkPos pos) {
        throw new AssertionError();
    }

    @WrapMethod(method = "updateChunkTracking")
    private void propwash$cameraTracking(ServerPlayer player, Operation<Void> original) {
        if (!CaptureServer.involved(player)) {
            original.call(player);
            return;
        }
        ChunkPos center = ViewAnchors.center(player, player.chunkPosition());
        int distance = ViewAnchors.viewDistance(player, this.getPlayerViewDistance(player));
        ChunkTrackingView next = CaptureServer.view(player, this.level, center, distance);
        if (!next.equals(player.getChunkTrackingView())) {
            this.applyChunkTrackingView(player, next);
        }
    }

    @WrapMethod(method = "applyChunkTrackingView")
    private void propwash$cameraApply(ServerPlayer player, ChunkTrackingView next, Operation<Void> original) {
        ChunkTrackingView previous = player.getChunkTrackingView();
        if (!(previous instanceof CameraTrackingView) && !(next instanceof CameraTrackingView)) {
            original.call(player, next);
            return;
        }
        if (player.level() != this.level) {
            return;
        }
        CaptureServer.beforeApply(player, previous, next);
        ChunkPos to = CameraTrackingView.center(next);
        if (to != null && !to.equals(CameraTrackingView.center(previous))) {
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(to.x(), to.z()));
        }
        TrackingDiff.difference(previous, next, pos -> this.markChunkPendingToSend(player, pos), pos -> dropChunk(player, pos));
        player.setChunkTrackingView(next);
        CaptureServer.afterApply(player, next);
    }
}
