package de.lardtom.propwash.relay;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.video.LinkBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayRangeTest {
    private static final double EPS = 1.0E-9;
    private static final double BREAK_QUALITY = 0.1;

    @Test
    void videoHopReachesAsFarAsTheDirectLink() {
        for (VideoType type : VideoType.values()) {
            double range = type.rangeBlocks;
            for (double distance : new double[]{20.0, 128.0, 200.0, range, 1.5 * range}) {
                assertEquals(LinkBudget.marginDb(distance, range) - 3.0, RelayLinks.videoHopMarginDb(distance, range, 3.0), EPS,
                        type.name() + " bei " + distance + " Blöcken");
            }
        }
    }

    @Test
    void controlHopHasTheRemoteRange() {
        for (double distance : new double[]{50.0, 300.0, 600.0, RadioLink.FULL_RANGE}) {
            assertEquals(1.0, RelayLinks.controlHopQuality(distance, false, 0.0), EPS, "Funk-Teilstrecke " + distance);
        }
        double fading = RadioLink.FULL_RANGE + RadioLink.FADE_RANGE * 0.5;
        assertEquals(RadioLink.quality(fading, Double.POSITIVE_INFINITY, false), RelayLinks.controlHopQuality(fading, false, 0.0), 1.0E-6);
    }

    @Test
    void repeatersAtSixtyAndHundredPercentOfTheBreakExtendTheVideo() {
        for (VideoType type : VideoType.values()) {
            VideoSystem system = type.system;
            double range = type.rangeBlocks;
            double cut = breakDistance(system, range);
            double near = 0.6 * cut;
            double drone = 1.5 * cut;
            assertTrue(LinkBudget.quality(system, drone, range, 0.0) < 0.01, type.name() + ": direkt bei 150 %");
            RelayPaths paths = new RelayPaths(8);
            paths.reset(4);
            paths.hop(RelayPaths.RECEIVER, 2, RelayLinks.videoHopMarginDb(near, range, 0.0));
            paths.hop(RelayPaths.RECEIVER, 3, RelayLinks.videoHopMarginDb(cut, range, 0.0));
            paths.hop(2, 3, RelayLinks.videoHopMarginDb(cut - near, range, 0.0));
            paths.hop(2, RelayPaths.TRANSMITTER, RelayLinks.videoHopMarginDb(drone - near, range, 0.0));
            paths.hop(3, RelayPaths.TRANSMITTER, RelayLinks.videoHopMarginDb(drone - cut, range, 0.0));
            assertTrue(paths.solve(RelayRules.MAX_RELAYS, RelayLinks.videoCombine(system)), type.name() + ": keine Kette");
            assertEquals(2, paths.relays(), type.name());
            double quality = RelayLinks.videoQuality(system, paths.value());
            assertTrue(quality > BREAK_QUALITY, type.name() + ": Bild über zwei Repeater bei 150 % der Abbruchstrecke nur " + quality);
        }
    }

    @Test
    void controlChainCarriesTheRemoteBeyondItsDirectRange() {
        double drone = 2.2 * RadioLink.FULL_RANGE;
        float direct = RadioLink.quality(drone, Double.POSITIVE_INFINITY, false);
        assertEquals(0.0F, direct, 1.0E-6F);
        RelayPaths paths = new RelayPaths(8);
        paths.reset(4);
        double a = 0.7 * RadioLink.FULL_RANGE;
        double b = 1.5 * RadioLink.FULL_RANGE;
        paths.hop(RelayPaths.RECEIVER, 2, RelayLinks.controlHopQuality(a, false, 0.0));
        paths.hop(2, 3, RelayLinks.controlHopQuality(b - a, false, 0.0));
        paths.hop(3, RelayPaths.TRANSMITTER, RelayLinks.controlHopQuality(drone - b, false, 0.0));
        assertTrue(paths.solve(RelayRules.MAX_RELAYS, RelayPaths.Combine.WEAKEST));
        assertEquals(1.0F, RelayLinks.controlQuality(direct, paths.value(), drone, Double.POSITIVE_INFINITY), 1.0E-6F);
    }

    static double breakDistance(VideoSystem system, double range) {
        double lo = 1.0;
        double hi = LinkBudget.maxReachBlocks(system, range);
        for (int i = 0; i < 80; i++) {
            double mid = 0.5 * (lo + hi);
            if (LinkBudget.quality(system, mid, range, 0.0) > BREAK_QUALITY) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
