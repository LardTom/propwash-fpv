package de.lardtom.propwash.drone;

public final class PilotBodyRules {
    public static final int LOADED_RADIUS = 1;
    public static final int DEATH_GRACE_TICKS = 20;
    public static final long NOT_DEAD = Long.MIN_VALUE;
    public static final int DOWN_FRAMES = 3;
    public static final long DOWN_MAX_NANOS = 400_000_000L;

    private PilotBodyRules() {
    }

    public enum AfterDeath {
        KEEP,
        WAIT,
        RELEASE
    }

    public static AfterDeath afterDeath(boolean alive, long diedAt, long now) {
        if (diedAt == NOT_DEAD) {
            return alive ? AfterDeath.KEEP : AfterDeath.WAIT;
        }
        if (alive) {
            return AfterDeath.RELEASE;
        }
        return now - diedAt >= DEATH_GRACE_TICKS || now < diedAt ? AfterDeath.RELEASE : AfterDeath.WAIT;
    }

    public static boolean leaveAfterDeath(int framesDown, long nanosDown) {
        return framesDown >= DOWN_FRAMES || nanosDown >= DOWN_MAX_NANOS || nanosDown < 0L;
    }

    public static boolean shields(boolean ruleOn, boolean piloting, boolean bypassesInvulnerability) {
        return ruleOn && piloting && !bypassesInvulnerability;
    }

    public static boolean mayStart(boolean fallFlying) {
        return !fallFlying;
    }
}
