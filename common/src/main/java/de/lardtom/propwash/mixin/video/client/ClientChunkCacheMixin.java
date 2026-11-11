package de.lardtom.propwash.mixin.video.client;

import de.lardtom.propwash.video.client.TerrainMemory;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @Unique
    private int propwash$centerX;
    @Unique
    private int propwash$centerZ;
    @Unique
    private int propwash$radius;

    @Shadow
    private static int calculateStorageRange(int viewRange) {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void propwash$trackStorage(ClientLevel level, int serverChunkRadius, CallbackInfo ci) {
        this.propwash$radius = calculateStorageRange(serverChunkRadius);
    }

    @Inject(method = "drop", at = @At("HEAD"), require = 1)
    private void propwash$rememberDropped(ChunkPos pos, CallbackInfo ci) {
        this.propwash$remember(pos.x(), pos.z());
    }

    @Inject(method = "updateViewCenter", at = @At("HEAD"), require = 1)
    private void propwash$rememberLeaving(int x, int z, CallbackInfo ci) {
        this.propwash$rememberOutside(x, z, this.propwash$radius);
        this.propwash$centerX = x;
        this.propwash$centerZ = z;
    }

    @Inject(method = "updateViewRadius", at = @At("HEAD"), require = 1)
    private void propwash$rememberShrunk(int viewRange, CallbackInfo ci) {
        int radius = calculateStorageRange(viewRange);
        if (radius < this.propwash$radius) {
            this.propwash$rememberOutside(this.propwash$centerX, this.propwash$centerZ, radius);
        }
        this.propwash$radius = radius;
    }

    @Unique
    private void propwash$rememberOutside(int x, int z, int radius) {
        int r = this.propwash$radius;
        for (int cx = this.propwash$centerX - r; cx <= this.propwash$centerX + r; cx++) {
            for (int cz = this.propwash$centerZ - r; cz <= this.propwash$centerZ + r; cz++) {
                if (Math.abs(cx - x) > radius || Math.abs(cz - z) > radius) {
                    this.propwash$remember(cx, cz);
                }
            }
        }
    }

    @Unique
    private void propwash$remember(int x, int z) {
        LevelChunk chunk = ((ClientChunkCache) (Object) this).getChunkNow(x, z);
        if (chunk != null) {
            TerrainMemory.INSTANCE.remember(chunk);
        }
    }
}
