package de.lardtom.propwash.api.drone;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public interface CollisionQuery {
    enum Side {
        CLIENT,
        SERVER
    }

    Entity drone();

    DroneView view();

    Optional<Player> pilot();

    Side side();

    boolean autonomous();

    boolean switchActive(Identifier id);

    double axis(Identifier id);
}
