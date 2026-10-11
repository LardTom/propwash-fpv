package de.lardtom.propwash.relay;

public final class RelayRules {
    public static final double RANGE_BLOCKS = 128.0;
    public static final double FULL_RANGE_BLOCKS = 112.0;
    public static final int MAX_RELAYS = 4;
    public static final int DEFAULT_RELAYS = 4;
    public static final int MAX_CANDIDATES = 16;

    private RelayRules() {
    }

    public static int clampRelays(int relays) {
        return Math.clamp(relays, 0, MAX_RELAYS);
    }

    public static double reachFactor(double distance) {
        if (!(distance >= 0.0)) {
            return 0.0;
        }
        if (distance <= FULL_RANGE_BLOCKS) {
            return 1.0;
        }
        if (distance >= RANGE_BLOCKS) {
            return 0.0;
        }
        return (RANGE_BLOCKS - distance) / (RANGE_BLOCKS - FULL_RANGE_BLOCKS);
    }

    public static double reachPenaltyDb(double distance) {
        double factor = reachFactor(distance);
        return factor <= 0.0 ? Double.POSITIVE_INFINITY : -20.0 * Math.log10(factor);
    }

    public static boolean inReach(double distance) {
        return distance >= 0.0 && distance < RANGE_BLOCKS;
    }

    public static double span(int relays) {
        return (clampRelays(relays) + 1) * RANGE_BLOCKS;
    }
}
