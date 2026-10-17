package de.lardtom.propwash.api.flight;

import java.util.List;
import java.util.function.Function;

public interface FlightExtensions {
    void registerAssist(String id, Function<DroneProfile, FlightAssist> factory);

    void registerForces(String id, Function<DroneProfile, ForceProvider> factory);

    List<String> assistIds();

    List<String> forceIds();
}
