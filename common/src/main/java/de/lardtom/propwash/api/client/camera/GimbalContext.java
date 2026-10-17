package de.lardtom.propwash.api.client.camera;

import net.minecraft.world.entity.Entity;
import org.joml.Quaternionfc;

public interface GimbalContext {
    Entity drone();

    Quaternionfc frame();

    float uptiltDegrees();

    double deltaSeconds();

    boolean spectating();
}
