package de.lardtom.propwash.api.client.osd;

import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public interface OsdContext {
    Optional<Entity> drone();

    FlightTelemetry telemetry();

    boolean fpv();

    boolean lineOfSight();

    boolean preview();

    long gameTime();

    float partialTick();
}
