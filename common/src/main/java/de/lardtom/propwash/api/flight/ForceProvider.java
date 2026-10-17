package de.lardtom.propwash.api.flight;

@FunctionalInterface
public interface ForceProvider {
    void apply(FlightState state, ForceSink sink);
}
