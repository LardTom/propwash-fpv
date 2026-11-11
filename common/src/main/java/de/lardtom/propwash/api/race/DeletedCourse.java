package de.lardtom.propwash.api.race;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record DeletedCourse(UUID id, int number, String name, Identifier dimension) {
    public DeletedCourse {
        Objects.requireNonNull(id, "id");
        name = name == null ? "" : name;
        Objects.requireNonNull(dimension, "dimension");
    }
}
