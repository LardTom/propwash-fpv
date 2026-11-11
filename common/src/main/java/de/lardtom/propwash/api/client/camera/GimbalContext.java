package de.lardtom.propwash.api.client.camera;

import net.minecraft.world.entity.Entity;
import org.joml.Quaternionfc;

public interface GimbalContext {
    Entity drone();

    Quaternionfc frame();

    float uptiltDegrees();

    double deltaSeconds();

    boolean spectating();

    default double axis(String id) {
        return 0.0;
    }

    default double operatorAxis(int index) {
        return 0.0;
    }

    default boolean operatorAction(int number) {
        return false;
    }
}
