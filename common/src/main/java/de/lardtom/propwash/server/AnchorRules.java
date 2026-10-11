package de.lardtom.propwash.server;

public final class AnchorRules {
    public static final int DRONE_TICKET_RADIUS = 2;
    public static final int PILOT_TICKET_RADIUS = 2;
    public static final double MIN_LIMITED_RANGE = 24.0;
    public static final double RELEASE_TOLERANCE = 2.0;

    private AnchorRules() {
    }

    public static Decision decide(Situation situation) {
        if (!situation.sameLevel()) {
            return Decision.DETACHED;
        }
        if (situation.protectedArea()) {
            return Decision.RELEASE_PROTECTED;
        }
        if (situation.unlimitedRange()) {
            if (situation.onlyGeneratedChunks() && situation.area().missingWithin(0)) {
                return Decision.RELEASE_UNEXPLORED;
            }
            return Decision.ANCHOR;
        }
        if (!(situation.distance() <= situation.limitedRange() + RELEASE_TOLERANCE)) {
            return Decision.RELEASE_TOO_FAR;
        }
        return Decision.LIMITED;
    }

    public static double limitedRange(double configuredRange, int playerViewDistance) {
        double tracked = Math.max(MIN_LIMITED_RANGE, (playerViewDistance - 1) * 16.0);
        return Math.min(configuredRange, tracked);
    }

    public static int droneTicketRadius(Decision decision, GeneratedArea.Scan area, boolean onlyGeneratedChunks) {
        if (decision != Decision.ANCHOR && decision != Decision.LIMITED) {
            return -1;
        }
        if (onlyGeneratedChunks && !area.generatedWithin(DRONE_TICKET_RADIUS)) {
            return -1;
        }
        return DRONE_TICKET_RADIUS;
    }

    public static int viewTicketRadius(Decision decision, int viewDistance, GeneratedArea.Scan area, boolean onlyGeneratedChunks) {
        if (decision != Decision.ANCHOR) {
            return -1;
        }
        int wanted = StreamBudget.viewTicketRadius(viewDistance);
        if (!onlyGeneratedChunks) {
            return wanted;
        }
        int allowed = Math.min(wanted, area.generatedRadius());
        return allowed > DRONE_TICKET_RADIUS ? allowed : -1;
    }

    public enum Decision {
        DETACHED,
        LIMITED,
        ANCHOR,
        RELEASE_TOO_FAR,
        RELEASE_UNEXPLORED,
        RELEASE_PROTECTED;

        public boolean releases() {
            return this == RELEASE_TOO_FAR || this == RELEASE_UNEXPLORED || this == RELEASE_PROTECTED;
        }
    }

    public record Situation(boolean sameLevel, boolean unlimitedRange, double distance, double limitedRange,
                            boolean onlyGeneratedChunks, GeneratedArea.Scan area, boolean protectedArea) {
    }
}
