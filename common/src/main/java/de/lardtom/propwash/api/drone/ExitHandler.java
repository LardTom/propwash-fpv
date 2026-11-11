package de.lardtom.propwash.api.drone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

@FunctionalInterface
public interface ExitHandler {
    Optional<AutonomyRequest> onPilotExit(Entity drone, ServerPlayer pilot);
}
