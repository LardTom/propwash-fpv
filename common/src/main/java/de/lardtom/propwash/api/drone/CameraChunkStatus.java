package de.lardtom.propwash.api.drone;

import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

public record CameraChunkStatus(UUID drone, int entityId, ChunkPos center, int requestedRadius, int radius, boolean paused,
                                int chunks, int sentChunks) {
    public boolean complete() {
        return !this.paused && this.chunks > 0 && this.sentChunks >= this.chunks;
    }
}
