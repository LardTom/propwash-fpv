package de.lardtom.propwash.api.drone;

import de.lardtom.propwash.api.flight.FlightAssist;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public interface AutonomyAccess {
    AutonomyResult start(Entity drone, ServerPlayer owner, FlightAssist assist, AutonomyOptions options);

    boolean stop(Entity drone);

    Optional<Identifier> active(Entity drone);

    default Optional<AutonomyNotReady> notReadyReason(Entity drone, AutonomyOptions options) {
        return Optional.empty();
    }

    void registerExitHandler(Identifier id, ExitHandler handler);
}
