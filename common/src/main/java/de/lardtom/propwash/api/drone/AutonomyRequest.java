package de.lardtom.propwash.api.drone;

import de.lardtom.propwash.api.flight.FlightAssist;

import java.util.Objects;

public record AutonomyRequest(FlightAssist assist, AutonomyOptions options) {
    public AutonomyRequest {
        Objects.requireNonNull(assist, "assist");
        Objects.requireNonNull(options, "options");
    }

    public static AutonomyRequest of(FlightAssist assist, AutonomyOptions options) {
        return new AutonomyRequest(assist, options);
    }
}
