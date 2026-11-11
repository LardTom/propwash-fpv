package de.lardtom.propwash.api.drone;

import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record DroneView(Entity entity, Optional<UUID> pilot, boolean flying, boolean armed, boolean autopilot, Vec3 center, Vec3 velocity,
                        Vec3 forward, Vec3 up, double massKg, Map<AccessorySlot, Identifier> accessories, double angleLimitDeg) {
    public DroneView {
        accessories = Map.copyOf(accessories);
    }

    public DroneView(Entity entity, Optional<UUID> pilot, boolean flying, boolean armed, boolean autopilot, Vec3 center, Vec3 velocity,
                     Vec3 forward, Vec3 up, double massKg, Map<AccessorySlot, Identifier> accessories) {
        this(entity, pilot, flying, armed, autopilot, center, velocity, forward, up, massKg, accessories, FlightState.DEFAULT_ANGLE_LIMIT_DEG);
    }

    public Optional<Identifier> accessory(AccessorySlot slot) {
        return Optional.ofNullable(this.accessories.get(slot));
    }

    public double speed() {
        return this.velocity.length();
    }
}
