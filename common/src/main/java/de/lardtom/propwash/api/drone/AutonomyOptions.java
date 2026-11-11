package de.lardtom.propwash.api.drone;

import de.lardtom.propwash.api.flight.AssistOutput;
import net.minecraft.resources.Identifier;

import java.time.Duration;
import java.util.Objects;
import java.util.OptionalDouble;

public record AutonomyOptions(Identifier id, String label, Duration maxDuration, OptionalDouble takeoff) {
    public static final Duration MIN_DURATION = Duration.ofSeconds(1);
    public static final Duration MAX_DURATION = Duration.ofMinutes(60);
    public static final double MIN_TAKEOFF_METRES = 1.0;
    public static final double MAX_TAKEOFF_METRES = 30.0;

    public AutonomyOptions {
        Objects.requireNonNull(id, "id");
        label = Objects.requireNonNullElse(label, "").strip();
        if (label.length() > AssistOutput.MAX_LABEL) {
            throw new IllegalArgumentException(id + ": Label höchstens " + AssistOutput.MAX_LABEL + " Zeichen: " + label);
        }
        Objects.requireNonNull(maxDuration, "maxDuration");
        if (maxDuration.compareTo(MIN_DURATION) < 0 || maxDuration.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException(id + ": Höchstdauer muss zwischen 1 s und 60 min liegen: " + maxDuration);
        }
        takeoff = Objects.requireNonNullElse(takeoff, OptionalDouble.empty());
        if (takeoff.isPresent() && !(takeoff.getAsDouble() >= MIN_TAKEOFF_METRES && takeoff.getAsDouble() <= MAX_TAKEOFF_METRES)) {
            throw new IllegalArgumentException(id + ": Starthöhe muss zwischen " + MIN_TAKEOFF_METRES + " und " + MAX_TAKEOFF_METRES
                    + " m liegen: " + takeoff.getAsDouble());
        }
    }

    public static AutonomyOptions of(Identifier id) {
        return new AutonomyOptions(id, "", MAX_DURATION, OptionalDouble.empty());
    }

    public AutonomyOptions label(String text) {
        return new AutonomyOptions(this.id, text, this.maxDuration, this.takeoff);
    }

    public AutonomyOptions maxDuration(Duration value) {
        return new AutonomyOptions(this.id, this.label, value, this.takeoff);
    }

    public AutonomyOptions takeoff(double metres) {
        return new AutonomyOptions(this.id, this.label, this.maxDuration, OptionalDouble.of(metres));
    }
}
