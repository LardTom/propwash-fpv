package de.lardtom.propwash.interact;

public final class StrikeRules {
    public static final int TARGET_COOLDOWN_TICKS = 10;
    public static final int STRIKES_PER_SECOND = 8;
    public static final double BASE_REACH = 1.5;
    public static final double REACH_PER_SPEED = 0.15;
    public static final double PEAK_FACTOR = 1.25;
    public static final double PEAK_MARGIN = 4.0;

    private StrikeRules() {
    }

    public static Decision decide(boolean ruleEnabled, boolean targetIsPilot, boolean targetIsPlayer, boolean pvpAllowed, boolean cooledDown,
                                  boolean withinBudget) {
        if (!ruleEnabled) {
            return Decision.RULE_OFF;
        }
        if (targetIsPilot) {
            return Decision.OWN_PILOT;
        }
        if (targetIsPlayer && !pvpAllowed) {
            return Decision.PVP_OFF;
        }
        if (!cooledDown) {
            return Decision.COOLDOWN;
        }
        if (!withinBudget) {
            return Decision.BUDGET;
        }
        return Decision.HIT;
    }

    public static double acceptedSpeed(double claimed, double recentPeak, double serverMax) {
        if (!Double.isFinite(claimed) || claimed <= 0.0) {
            return 0.0;
        }
        double peak = Double.isFinite(recentPeak) ? Math.max(0.0, recentPeak) : 0.0;
        double limit = peak * PEAK_FACTOR + PEAK_MARGIN;
        if (Double.isFinite(serverMax) && serverMax > 0.0) {
            limit = Math.min(limit, serverMax);
        }
        return Math.min(claimed, limit);
    }

    public static boolean withinReach(double gap, double speed) {
        if (!Double.isFinite(gap) || gap < 0.0) {
            return false;
        }
        double s = Double.isFinite(speed) ? Math.max(0.0, speed) : 0.0;
        return gap <= BASE_REACH + REACH_PER_SPEED * s;
    }

    public static double gap(double aMinX, double aMinY, double aMinZ, double aMaxX, double aMaxY, double aMaxZ,
                             double bMinX, double bMinY, double bMinZ, double bMaxX, double bMaxY, double bMaxZ) {
        double dx = Math.max(0.0, Math.max(aMinX - bMaxX, bMinX - aMaxX));
        double dy = Math.max(0.0, Math.max(aMinY - bMaxY, bMinY - aMaxY));
        double dz = Math.max(0.0, Math.max(aMinZ - bMaxZ, bMinZ - aMaxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public enum Decision {
        HIT,
        RULE_OFF,
        OWN_PILOT,
        PVP_OFF,
        COOLDOWN,
        BUDGET
    }
}
