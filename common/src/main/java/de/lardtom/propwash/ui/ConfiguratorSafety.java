package de.lardtom.propwash.ui;

public final class ConfiguratorSafety {
    private ConfiguratorSafety() {
    }

    public enum Decision {
        OPEN,
        DISARM_THEN_OPEN,
        REFUSE_AIRBORNE,
        REFUSE_ARMED;

        public boolean opens() {
            return this == OPEN || this == DISARM_THEN_OPEN;
        }
    }

    public static Decision decide(boolean piloting, boolean armed, boolean grounded, boolean canForceDisarm) {
        if (!piloting || !armed) {
            return Decision.OPEN;
        }
        if (!grounded) {
            return Decision.REFUSE_AIRBORNE;
        }
        return canForceDisarm ? Decision.DISARM_THEN_OPEN : Decision.REFUSE_ARMED;
    }

    public static boolean lockoutDisarm(boolean configuratorOpen, boolean piloting, boolean armed) {
        return configuratorOpen && piloting && armed;
    }
}
