package de.lardtom.propwash.api.client.camera;

import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;

public interface GimbalProvider {
    boolean appliesTo(Entity drone);

    void orient(GimbalContext context, Quaternionf rotation);

    default boolean suppressesShake() {
        return true;
    }
}
