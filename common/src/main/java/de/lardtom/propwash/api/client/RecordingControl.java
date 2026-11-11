package de.lardtom.propwash.api.client;

import net.minecraft.world.entity.Entity;

public interface RecordingControl {
    boolean start(Entity drone);

    void stop();

    boolean photo(Entity drone);

    boolean recording();
}
