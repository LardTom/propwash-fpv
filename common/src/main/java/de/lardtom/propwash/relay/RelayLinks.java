package de.lardtom.propwash.relay;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.video.LinkBudget;

public final class RelayLinks {
    private RelayLinks() {
    }

    public static RelayPaths.Combine videoCombine(VideoSystem system) {
        return system == VideoSystem.DIGITAL ? RelayPaths.Combine.WEAKEST : RelayPaths.Combine.NOISE;
    }

    public static double videoHopMarginDb(double distance, double rangeBlocks, double obstructionDb) {
        if (!RelayRules.inReach(distance) || Double.isNaN(obstructionDb)) {
            return Double.NaN;
        }
        return LinkBudget.marginDb(distance, rangeBlocks) - Math.max(0.0, obstructionDb) - RelayRules.reachPenaltyDb(distance);
    }

    public static double controlHopQuality(double distance, boolean freeRange, double creativeRange) {
        if (!RelayRules.inReach(distance)) {
            return Double.NaN;
        }
        double radio = RadioLink.quality(distance, Double.POSITIVE_INFINITY, freeRange, creativeRange);
        return Math.min(radio, RelayRules.reachFactor(distance));
    }

    public static float controlQuality(float direct, double relayed, double directDistance, double serverLimit) {
        double best = Double.isNaN(relayed) ? direct : Math.max(direct, relayed);
        double server = RadioLink.quality(directDistance, serverLimit, true);
        return (float) Math.clamp(Math.min(best, server), 0.0, 1.0);
    }

    public static double videoQuality(VideoSystem system, double pathMarginDb) {
        if (Double.isNaN(pathMarginDb)) {
            return 0.0;
        }
        return LinkBudget.quality(system, pathMarginDb);
    }
}
