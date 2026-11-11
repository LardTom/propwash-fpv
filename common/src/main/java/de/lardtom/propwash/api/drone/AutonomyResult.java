package de.lardtom.propwash.api.drone;

public enum AutonomyResult {
    STARTED,
    NO_BUDGET,
    PILOTED,
    NOT_READY,
    DENIED,
    NOT_ARMABLE;

    public boolean started() {
        return this == STARTED;
    }
}
