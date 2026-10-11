package de.lardtom.propwash.relay;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.video.LinkBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayPathsTest {
    private static final double EPS = 1.0E-9;

    @Test
    void withoutRepeatersThereIsNoRelayPath() {
        RelayPaths paths = new RelayPaths(8);
        paths.reset(2);
        paths.hop(RelayPaths.RECEIVER, RelayPaths.TRANSMITTER, 30.0);
        assertFalse(paths.solve(4, RelayPaths.Combine.WEAKEST));
        assertEquals(0, paths.relays());
    }

    @Test
    void directHopIsNeverPartOfTheRelaySolution() {
        RelayPaths paths = new RelayPaths(8);
        paths.reset(3);
        paths.hop(0, 1, 40.0);
        paths.hop(0, 2, 10.0);
        paths.hop(2, 1, 12.0);
        assertTrue(paths.solve(4, RelayPaths.Combine.WEAKEST));
        assertEquals(1, paths.relays());
        assertEquals(10.0, paths.value(), EPS);
        assertEquals(0, paths.node(0));
        assertEquals(2, paths.node(1));
        assertEquals(1, paths.node(2));
    }

    @Test
    void weakestHopDecidesForDigitalAndTheBestChainWins() {
        RelayPaths paths = new RelayPaths(8);
        paths.reset(5);
        paths.hop(0, 2, 20.0);
        paths.hop(2, 1, 3.0);
        paths.hop(0, 3, 15.0);
        paths.hop(3, 4, 14.0);
        paths.hop(4, 1, 16.0);
        assertTrue(paths.solve(4, RelayPaths.Combine.WEAKEST));
        assertEquals(2, paths.relays());
        assertEquals(14.0, paths.value(), EPS);
        assertEquals(3, paths.node(1));
        assertEquals(4, paths.node(2));
        assertEquals(15.0, paths.hopValue(0), EPS);
        assertEquals(14.0, paths.hopValue(1), EPS);
        assertEquals(16.0, paths.hopValue(2), EPS);
    }

    @Test
    void hopLimitCutsLongerChains() {
        RelayPaths paths = chain(3, 20.0);
        assertTrue(paths.solve(3, RelayPaths.Combine.WEAKEST));
        assertEquals(3, paths.relays());
        assertFalse(paths.solve(2, RelayPaths.Combine.WEAKEST));
        assertFalse(paths.solve(0, RelayPaths.Combine.WEAKEST));
    }

    @Test
    void ruleNeverAllowsMoreThanFourRepeaters() {
        RelayPaths paths = chain(5, 20.0);
        assertFalse(paths.solve(99, RelayPaths.Combine.WEAKEST));
        RelayPaths four = chain(4, 20.0);
        assertTrue(four.solve(99, RelayPaths.Combine.WEAKEST));
        assertEquals(RelayRules.MAX_RELAYS, four.relays());
    }

    @Test
    void analogNoiseAddsUpOverHops() {
        RelayPaths paths = chain(3, 12.0);
        assertTrue(paths.solve(4, RelayPaths.Combine.NOISE));
        assertEquals(3, paths.relays());
        assertEquals(12.0 - 10.0 * Math.log10(4.0), paths.value(), 1.0E-6);
        RelayPaths two = new RelayPaths(8);
        two.reset(3);
        two.hop(0, 2, 12.0);
        two.hop(2, 1, Double.POSITIVE_INFINITY);
        assertTrue(two.solve(4, RelayPaths.Combine.NOISE));
        assertEquals(12.0, two.value(), 1.0E-9);
    }

    @Test
    void fewerRepeatersWinOnATie() {
        RelayPaths paths = new RelayPaths(8);
        paths.reset(4);
        paths.hop(0, 2, 20.0);
        paths.hop(2, 1, 20.0);
        paths.hop(2, 3, 30.0);
        paths.hop(3, 1, 30.0);
        paths.hop(0, 3, 10.0);
        assertTrue(paths.solve(4, RelayPaths.Combine.WEAKEST));
        assertEquals(1, paths.relays());
        assertEquals(2, paths.node(1));
    }

    @Test
    void solutionNeverVisitsARepeaterTwice() {
        RelayPaths paths = new RelayPaths(10);
        paths.reset(6);
        paths.hop(0, 2, 25.0);
        paths.hop(2, 3, 25.0);
        paths.hop(3, 2, 25.0);
        paths.hop(3, 4, 25.0);
        paths.hop(4, 5, 25.0);
        paths.hop(5, 1, 25.0);
        paths.hop(2, 5, 25.0);
        for (RelayPaths.Combine combine : RelayPaths.Combine.values()) {
            assertTrue(paths.solve(4, combine));
            boolean[] seen = new boolean[6];
            for (int i = 0; i < paths.relays() + 2; i++) {
                assertFalse(seen[paths.node(i)], combine + ": Knoten doppelt");
                seen[paths.node(i)] = true;
            }
            assertEquals(2, paths.relays());
        }
    }

    @Test
    void missingHopsBreakThePath() {
        RelayPaths paths = new RelayPaths(8);
        paths.reset(4);
        paths.hop(0, 2, 20.0);
        paths.hop(3, 1, 20.0);
        assertFalse(paths.solve(4, RelayPaths.Combine.WEAKEST));
        paths.hop(2, 3, Double.NEGATIVE_INFINITY);
        assertFalse(paths.solve(4, RelayPaths.Combine.NOISE));
        paths.hop(2, 3, -3.0);
        assertTrue(paths.solve(4, RelayPaths.Combine.WEAKEST));
        assertEquals(-3.0, paths.value(), EPS);
    }

    @Test
    void reachFadesOverTheLastSixteenBlocks() {
        assertEquals(1.0, RelayRules.reachFactor(0.0), EPS);
        assertEquals(1.0, RelayRules.reachFactor(RelayRules.FULL_RANGE_BLOCKS), EPS);
        assertEquals(0.5, RelayRules.reachFactor(120.0), EPS);
        assertEquals(0.0, RelayRules.reachFactor(RelayRules.RANGE_BLOCKS), EPS);
        assertEquals(0.0, RelayRules.reachFactor(Double.NaN), EPS);
        assertEquals(0.0, RelayRules.reachPenaltyDb(50.0), EPS);
        assertEquals(20.0 * Math.log10(2.0), RelayRules.reachPenaltyDb(120.0), 1.0E-9);
        assertTrue(Double.isInfinite(RelayRules.reachPenaltyDb(130.0)));
        assertFalse(RelayRules.inReach(RelayRules.RANGE_BLOCKS));
    }

    @Test
    void longestChainStaysInsideTheRemoteRangeSoServerChecksStayValid() {
        assertTrue(RelayRules.span(RelayRules.MAX_RELAYS) <= RadioLink.FULL_RANGE,
                "Kette " + RelayRules.span(RelayRules.MAX_RELAYS) + " länger als die volle Funkreichweite");
        assertTrue(RelayRules.span(RelayRules.MAX_RELAYS) <= RadioLink.START_RANGE);
        for (double d = 0.0; d <= RadioLink.FULL_RANGE; d += 1.0) {
            assertEquals(1.0F, RadioLink.quality(d, Double.POSITIVE_INFINITY, false), "direkter Funk bei " + d);
        }
    }

    @Test
    void controlLinkThroughRepeatersNeverBeatsTheDirectLinkWithoutOwnRange() {
        double hop = RelayLinks.controlHopQuality(100.0, false, 0.0);
        assertEquals(1.0, hop, EPS);
        assertEquals(1.0F, RelayLinks.controlQuality(1.0F, hop, 300.0, Double.POSITIVE_INFINITY), 1.0E-6F);
        assertTrue(Double.isNaN(RelayLinks.controlHopQuality(RelayRules.RANGE_BLOCKS + 1.0, false, 0.0)));
        assertEquals(0.5, RelayLinks.controlHopQuality(120.0, false, 0.0), EPS);
    }

    @Test
    void controlLinkRelayHelpsAgainstTheCreativeGogglesOwnRangeButNotTheServerLimit() {
        double creativeRange = 40.0;
        float direct = RadioLink.quality(90.0, Double.POSITIVE_INFINITY, true, creativeRange);
        assertEquals(0.0F, direct, 1.0E-6F);
        double hop = RelayLinks.controlHopQuality(30.0, true, creativeRange);
        assertEquals(1.0, hop, EPS);
        assertEquals(1.0F, RelayLinks.controlQuality(direct, hop, 90.0, Double.POSITIVE_INFINITY), 1.0E-6F);
        assertEquals(0.0F, RelayLinks.controlQuality(direct, hop, 90.0, 80.0), 1.0E-6F);
        assertEquals(direct, RelayLinks.controlQuality(direct, Double.NaN, 90.0, Double.POSITIVE_INFINITY), 1.0E-6F);
    }

    @Test
    void videoHopUsesTheTransmitterRangeTheBlocksAndTheRepeaterReach() {
        double range = VideoType.ANALOG_RACE.rangeBlocks;
        assertEquals(LinkBudget.marginDb(20.0, range) - 5.0, RelayLinks.videoHopMarginDb(20.0, range, 5.0), EPS);
        assertEquals(LinkBudget.marginDb(120.0, range) - 20.0 * Math.log10(2.0), RelayLinks.videoHopMarginDb(120.0, range, 0.0), 1.0E-9);
        assertTrue(Double.isNaN(RelayLinks.videoHopMarginDb(200.0, range, 0.0)));
        assertTrue(Double.isNaN(RelayLinks.videoHopMarginDb(20.0, range, Double.NaN)));
        assertEquals(RelayPaths.Combine.NOISE, RelayLinks.videoCombine(VideoSystem.ANALOG));
        assertEquals(RelayPaths.Combine.WEAKEST, RelayLinks.videoCombine(VideoSystem.DIGITAL));
    }

    @Test
    void concreteFloorsNeedTheStairwellRepeaters() {
        double range = VideoType.ANALOG_RACE.rangeBlocks;
        double floor = 36.0;
        double direct = LinkBudget.quality(VideoSystem.ANALOG, 10.0, range, 2 * floor);
        assertEquals(0.0, direct, EPS);
        RelayPaths paths = new RelayPaths(8);
        paths.reset(4);
        paths.hop(0, 2, RelayLinks.videoHopMarginDb(14.0, range, 0.0));
        paths.hop(2, 1, RelayLinks.videoHopMarginDb(16.0, range, 2 * floor));
        paths.hop(2, 3, RelayLinks.videoHopMarginDb(13.4, range, 0.0));
        paths.hop(3, 1, RelayLinks.videoHopMarginDb(14.3, range, 0.0));
        paths.hop(0, 3, RelayLinks.videoHopMarginDb(16.0, range, 2 * floor));
        assertTrue(paths.solve(4, RelayLinks.videoCombine(VideoSystem.ANALOG)));
        assertEquals(2, paths.relays());
        double quality = RelayLinks.videoQuality(VideoSystem.ANALOG, paths.value());
        assertTrue(quality > 0.99, "Bild über zwei Repeater: " + quality);
        assertTrue(paths.solve(1, RelayLinks.videoCombine(VideoSystem.ANALOG)));
        assertTrue(RelayLinks.videoQuality(VideoSystem.ANALOG, paths.value()) < 0.05);
    }

    @Test
    void candidatesFollowTheDetourEllipseAndAreSortedByDetour() {
        RelayCandidates candidates = new RelayCandidates();
        double[] positions = {
                50.0, 0.0, 0.0,
                50.0, 0.0, 400.0,
                10.0, 0.0, 5.0,
                900.0, 0.0, 0.0};
        int[] out = new int[8];
        int n = candidates.select(0.0, 0.0, 0.0, 100.0, 0.0, 0.0, positions, 4, 4, 16, out);
        assertEquals(2, n);
        assertEquals(0, out[0]);
        assertEquals(2, out[1]);
        assertEquals(0, candidates.select(0.0, 0.0, 0.0, 100.0, 0.0, 0.0, positions, 4, 0, 16, out));
        assertEquals(1, candidates.select(0.0, 0.0, 0.0, 100.0, 0.0, 0.0, positions, 4, 4, 1, out));
    }

    private static RelayPaths chain(int relays, double margin) {
        RelayPaths paths = new RelayPaths(relays + 2);
        paths.reset(relays + 2);
        paths.hop(RelayPaths.RECEIVER, 2, margin);
        for (int i = 2; i < relays + 1; i++) {
            paths.hop(i, i + 1, margin);
        }
        paths.hop(relays + 1, RelayPaths.TRANSMITTER, margin);
        return paths;
    }
}
