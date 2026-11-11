package de.lardtom.propwash.api.flight;

import java.util.Optional;

@FunctionalInterface
public interface FlightAssist {
    void update(FlightState state, AssistOutput output);

    default double sensorRange() {
        return 0.0;
    }

    default void reset() {
    }

    default Optional<String> modeNameKey(StabilizationMode mode) {
        return Optional.empty();
    }
}
