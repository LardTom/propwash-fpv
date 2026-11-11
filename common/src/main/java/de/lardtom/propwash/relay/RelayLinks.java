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

    public static double videoHopReach(VideoSystem system, double rangeBlocks) {
        return LinkBudget.maxReachBlocks(system, rangeBlocks);
    }

    public static double videoHopMarginDb(double distance, double rangeBlocks, double obstructionDb) {
        if (!(distance >= 0.0) || Double.isNaN(obstructionDb)) {
            return Double.NaN;
        }
        return LinkBudget.marginDb(distance, rangeBlocks) - Math.max(0.0, obstructionDb);
    }

    public static double controlHopReach(boolean freeRange, double creativeRange) {
        if (!freeRange) {
            return RadioLink.FULL_RANGE + RadioLink.FADE_RANGE;
        }
        if (creativeRange > 0.0 && Double.isFinite(creativeRange)) {
            return creativeRange * (1.0 + RadioLink.FADE_RANGE / RadioLink.FULL_RANGE);
        }
        return Double.POSITIVE_INFINITY;
    }

    public static double controlHopQuality(double distance, boolean freeRange, double creativeRange) {
        return controlHopQuality(distance, freeRange, creativeRange, 0.0);
    }

    public static double controlHopQuality(double distance, boolean freeRange, double creativeRange, double videoObstructionDb) {
        if (!(distance >= 0.0)) {
            return Double.NaN;
        }
        double shade = Double.isNaN(videoObstructionDb) ? 0.0 : videoObstructionDb;
        double quality = RadioLink.quality(distance, Double.POSITIVE_INFINITY, freeRange, creativeRange, shade);
        return quality > 0.0 ? quality : Double.NaN;
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
