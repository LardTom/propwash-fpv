package de.lardtom.propwash.api.drone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

public interface CameraChunkSupply {
    int MAX_RADIUS = 8;
    int MAX_PER_PLAYER = 2;
    int MAX_DISTANCE_CHUNKS = 128;

    CameraChunkResult open(ServerPlayer player, Entity drone, int radiusChunks);

    boolean close(ServerPlayer player, Entity drone);

    int closeAll(ServerPlayer player);

    Optional<CameraChunkStatus> status(ServerPlayer player, Entity drone);

    List<CameraChunkStatus> active(ServerPlayer player);
}
