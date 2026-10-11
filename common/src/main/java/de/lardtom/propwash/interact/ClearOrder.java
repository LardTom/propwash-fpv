package de.lardtom.propwash.interact;

public final class ClearOrder {
    public static final double CORRIDOR = 1.5;
    public static final double MIN_SPEED = 4.0;
    public static final double DIRECTED_SPEED = 2.0;
    public static final double SKIP_SPEED = 6.0;
    public static final double SKIP_BEHIND = 4.0;
    public static final double BEHIND_FACTOR = 3.0;
    public static final double LATERAL_SECONDS_PER_BLOCK = 0.1;
    public static final double OUTSIDE = 1000.0;
    public static final double SKIP = Double.POSITIVE_INFINITY;

    private ClearOrder() {
    }

    public static double score(double rx, double ry, double rz, double vx, double vy, double vz, double halfSize) {
        double distance = Math.sqrt(rx * rx + ry * ry + rz * rz);
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (!Double.isFinite(distance)) {
            return SKIP;
        }
        if (!Double.isFinite(speed) || speed < DIRECTED_SPEED) {
            return distance / MIN_SPEED;
        }
        double pace = Math.max(speed, MIN_SPEED);
        double along = (rx * vx + ry * vy + rz * vz) / speed;
        double lateral = Math.sqrt(Math.max(0.0, distance * distance - along * along));
        double corridor = CORRIDOR + Math.max(0.0, halfSize);
        if (along < 0.0) {
            if (speed >= SKIP_SPEED && along < -(SKIP_BEHIND + Math.max(0.0, halfSize))) {
                return SKIP;
            }
            return OUTSIDE + (BEHIND_FACTOR * -along + lateral) / pace;
        }
        if (lateral <= corridor) {
            return along / pace;
        }
        return OUTSIDE + along / pace + (lateral - corridor) * LATERAL_SECONDS_PER_BLOCK;
    }
}
