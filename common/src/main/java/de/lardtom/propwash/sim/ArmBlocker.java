package de.lardtom.propwash.sim;

public enum ArmBlocker {
    ARM_SWITCH_NOT_RESET,
    THROTTLE_NOT_LOW,
    TILTED,
    PACK_DEPLETED,
    BUILD_INVALID,
    CRASH_COOLDOWN,
    FAILSAFE,
    REBOOTING,
    IN_FLUID,
    HARDWARE_FAULT,
    THROTTLE_NOT_CENTERED,
    TURTLE_AIRBORNE;

    public int bit() {
        return 1 << this.ordinal();
    }

    public boolean isSet(int mask) {
        return (mask & this.bit()) != 0;
    }
}
