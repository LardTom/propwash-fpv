package de.lardtom.propwash.relay;

public final class RelayRules {
    public static final int MAX_RELAYS = 4;
    public static final int DEFAULT_RELAYS = 4;
    public static final int MAX_CANDIDATES = 16;

    private RelayRules() {
    }

    public static int clampRelays(int relays) {
        return Math.clamp(relays, 0, MAX_RELAYS);
    }

    public static boolean inReach(double distance, double hopReach) {
        return distance >= 0.0 && distance <= hopReach;
    }

    public static double span(int relays, double hopReach) {
        if (!(hopReach > 0.0)) {
            return 0.0;
        }
        return (clampRelays(relays) + 1) * hopReach;
    }
}
