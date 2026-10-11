package de.lardtom.propwash.input;

public enum SetupStatus {
    NEW,
    INCOMPLETE,
    COMPLETED,
    NEVER;

    public boolean wantsSetup() {
        return this == NEW || this == INCOMPLETE;
    }
}
