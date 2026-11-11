package de.lardtom.propwash.api.race;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

public record PersonalBest(UUID course, int number, int layout, BoardEntry entry, OptionalLong previousMillis) {
    public PersonalBest {
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(previousMillis, "previousMillis");
    }
}
