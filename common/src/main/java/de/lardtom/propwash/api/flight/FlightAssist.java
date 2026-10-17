package de.lardtom.propwash.api.flight;

@FunctionalInterface
public interface FlightAssist {
    void update(FlightState state, AssistOutput output);

    default double sensorRange() {
        return 0.0;
    }

    default void reset() {
    }
}
