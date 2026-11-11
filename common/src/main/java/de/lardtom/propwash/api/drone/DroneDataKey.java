package de.lardtom.propwash.api.drone;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

public interface DroneDataKey<T> {
    enum Sync {
        NONE,
        TRACKING
    }

    Identifier id();

    Codec<T> codec();

    T defaultValue();

    Sync sync();
}
