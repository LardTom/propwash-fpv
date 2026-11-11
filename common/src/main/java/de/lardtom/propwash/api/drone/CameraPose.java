package de.lardtom.propwash.api.drone;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record CameraPose(Vec3 eye, Vec3 look, Vec3 up, double verticalFovDegrees) {
    public CameraPose {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(look, "look");
        Objects.requireNonNull(up, "up");
        if (!(verticalFovDegrees > 0.0 && verticalFovDegrees < 180.0)) {
            throw new IllegalArgumentException("Sichtfeld ungültig: " + verticalFovDegrees);
        }
    }
}
