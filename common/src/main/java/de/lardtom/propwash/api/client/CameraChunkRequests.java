package de.lardtom.propwash.api.client;

import de.lardtom.propwash.api.drone.CameraChunkResult;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public interface CameraChunkRequests {
    boolean supported();

    boolean request(int entityId, int radiusChunks);

    boolean release(int entityId);

    int radius(int entityId);

    Optional<CameraChunkResult.Status> answer(int entityId);

    default boolean request(Entity drone, int radiusChunks) {
        return drone != null && this.request(drone.getId(), radiusChunks);
    }

    default boolean release(Entity drone) {
        return drone != null && this.release(drone.getId());
    }

    default int radius(Entity drone) {
        return drone == null ? 0 : this.radius(drone.getId());
    }

    default Optional<CameraChunkResult.Status> answer(Entity drone) {
        return drone == null ? Optional.empty() : this.answer(drone.getId());
    }
}
