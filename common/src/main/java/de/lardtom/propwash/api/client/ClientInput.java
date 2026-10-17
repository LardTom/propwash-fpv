package de.lardtom.propwash.api.client;

import net.minecraft.world.entity.Entity;

import java.util.Optional;

public interface ClientInput {
    boolean flying();

    Optional<Entity> drone();

    double roll();

    double pitch();

    double yaw();

    double throttle();

    boolean action(int number);
}
