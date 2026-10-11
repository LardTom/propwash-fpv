package de.lardtom.propwash.rescue;

public final class RescueSettings {
    public static final double MIN_ALTITUDE = 3.0;
    public static final double MAX_ALTITUDE = 100.0;
    public static final double MIN_SPEED = 2.0;
    public static final double MAX_SPEED = 25.0;
    public static final double MIN_DESCENT = 0.5;
    public static final double MAX_DESCENT = 5.0;
    public static final double MIN_CLEARANCE = 0.5;
    public static final double MAX_CLEARANCE = 4.0;
    public static final double MIN_DISTANCE_LIMIT = 0.0;
    public static final double MAX_DISTANCE_LIMIT = 50.0;
    public static final double MIN_OVERRIDE = 0.1;
    public static final double MAX_OVERRIDE = 0.9;

    public FailsafeAction failsafeAction = FailsafeAction.RETURN_HOME;
    public HomeMode homeMode = HomeMode.FIRST_ARM;
    public double altitude = 15.0;
    public double speed = 8.0;
    public double descentRate = 2.0;
    public boolean obstacleAvoidance = true;
    public double clearance = 1.0;
    public double minDistance = 8.0;
    public boolean autoLand = true;
    public boolean stickOverride = true;
    public double stickOverrideThreshold = 0.3;

    public RescueSettings() {
    }

    public RescueSettings copy() {
        RescueSettings c = new RescueSettings();
        c.failsafeAction = this.failsafeAction;
        c.homeMode = this.homeMode;
        c.altitude = this.altitude;
        c.speed = this.speed;
        c.descentRate = this.descentRate;
        c.obstacleAvoidance = this.obstacleAvoidance;
        c.clearance = this.clearance;
        c.minDistance = this.minDistance;
        c.autoLand = this.autoLand;
        c.stickOverride = this.stickOverride;
        c.stickOverrideThreshold = this.stickOverrideThreshold;
        return c;
    }

    public void sanitize() {
        if (this.failsafeAction == null) {
            this.failsafeAction = FailsafeAction.RETURN_HOME;
        }
        if (this.homeMode == null) {
            this.homeMode = HomeMode.FIRST_ARM;
        }
        this.altitude = clamp(this.altitude, MIN_ALTITUDE, MAX_ALTITUDE, 15.0);
        this.speed = clamp(this.speed, MIN_SPEED, MAX_SPEED, 8.0);
        this.descentRate = clamp(this.descentRate, MIN_DESCENT, MAX_DESCENT, 2.0);
        this.clearance = clamp(this.clearance, MIN_CLEARANCE, MAX_CLEARANCE, 1.0);
        this.minDistance = clamp(this.minDistance, MIN_DISTANCE_LIMIT, MAX_DISTANCE_LIMIT, 8.0);
        this.stickOverrideThreshold = clamp(this.stickOverrideThreshold, MIN_OVERRIDE, MAX_OVERRIDE, 0.3);
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
