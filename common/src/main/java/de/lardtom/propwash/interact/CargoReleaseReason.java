package de.lardtom.propwash.interact;

public enum CargoReleaseReason {
    COMMAND,
    CRASH,
    DISARMED,
    REMOVED;

    public static CargoReleaseReason byOrdinal(int ordinal) {
        CargoReleaseReason[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : COMMAND;
    }
}
