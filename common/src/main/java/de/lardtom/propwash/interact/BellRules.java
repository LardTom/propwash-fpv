package de.lardtom.propwash.interact;

public final class BellRules {
    public static final double MIN_SPEED = 1.0;
    public static final int COOLDOWN_TICKS = 5;
    public static final int RINGS_PER_SECOND = 8;
    public static final double CONTACT_SLACK = 0.05;
    public static final double HIT_SLACK = 0.25;

    private BellRules() {
    }

    public static Decision decide(double closingSpeed, boolean cooledDown, boolean withinBudget, double gap, double acceptedSpeed) {
        if (!Double.isFinite(closingSpeed) || closingSpeed < MIN_SPEED) {
            return Decision.TOO_SLOW;
        }
        if (!cooledDown) {
            return Decision.COOLDOWN;
        }
        if (!withinBudget) {
            return Decision.BUDGET;
        }
        if (!StrikeRules.withinReach(gap, acceptedSpeed)) {
            return Decision.OUT_OF_REACH;
        }
        return Decision.RING;
    }

    public static boolean cooledDown(long last, long now) {
        return last == Long.MIN_VALUE || now - last >= COOLDOWN_TICKS || now < last;
    }

    public static double clampInto(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return (min + max) * 0.5;
        }
        return Math.max(min, Math.min(max, value));
    }

    public enum Decision {
        RING,
        TOO_SLOW,
        COOLDOWN,
        BUDGET,
        OUT_OF_REACH,
        PROTECTED
    }
}
