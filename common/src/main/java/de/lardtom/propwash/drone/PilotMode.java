package de.lardtom.propwash.drone;

public enum PilotMode {
    NONE,
    FPV,
    LINE_OF_SIGHT;

    public static PilotMode byOrdinal(int ordinal) {
        PilotMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
