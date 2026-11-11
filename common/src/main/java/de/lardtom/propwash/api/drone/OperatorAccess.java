package de.lardtom.propwash.api.drone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public interface OperatorAccess {
    int AXES = 4;
    int ACTIONS = 4;

    boolean bind(Entity drone, ServerPlayer operator);

    boolean unbind(Entity drone);

    Optional<ServerPlayer> operator(Entity drone);
}
