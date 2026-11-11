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
    void controlLinkThroughRepeatersNeverBeatsAFullDirectLink() {
        double hop = RelayLinks.controlHopQuality(100.0, false, 0.0);
        assertEquals(1.0, hop, EPS);
        assertEquals(1.0F, RelayLinks.controlQuality(1.0F, hop, 300.0, Double.POSITIVE_INFINITY), 1.0E-6F);
        double reach = RelayLinks.controlHopReach(false, 0.0);
        assertEquals(RadioLink.FULL_RANGE + RadioLink.FADE_RANGE, reach, EPS);
        assertTrue(Double.isNaN(RelayLinks.controlHopQuality(reach + 1.0, false, 0.0)));
        assertEquals(RadioLink.quality(300.0, Double.POSITIVE_INFINITY, false, 0.0, 15.0), RelayLinks.controlHopQuality(300.0, false, 0.0, 15.0), 1.0E-6);
        assertTrue(RelayLinks.controlHopQuality(300.0, false, 0.0, 15.0) < 0.95, "Wand dämpft auch die Funk-Teilstrecke");
        assertEquals(1.0, RelayLinks.controlHopQuality(100.0, true, 0.0, 45.0), EPS);
        assertTrue(Double.isInfinite(RelayLinks.controlHopReach(true, 0.0)));
        assertEquals(60.0, RelayLinks.controlHopReach(true, 40.0), EPS);
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
    void videoHopUsesTheTransmitterRangeAndTheBlocks() {
        double range = VideoType.ANALOG_RACE.rangeBlocks;
        assertEquals(LinkBudget.marginDb(20.0, range) - 5.0, RelayLinks.videoHopMarginDb(20.0, range, 5.0), EPS);
        assertEquals(LinkBudget.marginDb(120.0, range), RelayLinks.videoHopMarginDb(120.0, range, 0.0), EPS);
        assertEquals(LinkBudget.marginDb(200.0, range), RelayLinks.videoHopMarginDb(200.0, range, -2.0), EPS);
        assertTrue(Double.isNaN(RelayLinks.videoHopMarginDb(20.0, range, Double.NaN)));
        assertTrue(Double.isNaN(RelayLinks.videoHopMarginDb(Double.NaN, range, 0.0)));
        assertEquals(LinkBudget.maxReachBlocks(VideoSystem.ANALOG, range), RelayLinks.videoHopReach(VideoSystem.ANALOG, range), EPS);
        assertEquals(LinkBudget.lostDb(VideoSystem.DIGITAL), RelayLinks.videoHopMarginDb(RelayLinks.videoHopReach(VideoSystem.DIGITAL, range), range, 0.0),
                1.0E-9);
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
        int n = candidates.select(0.0, 0.0, 0.0, 100.0, 0.0, 0.0, positions, 4, 4, 128.0, 16, out);
        assertEquals(2, n);
        assertEquals(0, out[0]);
        assertEquals(2, out[1]);
        assertEquals(0, candidates.select(0.0, 0.0, 0.0, 100.0, 0.0, 0.0, positions, 4, 0, 128.0, 16, out));
        assertEquals(1, candidates.select(0.0, 0.0, 0.0, 100.0, 0.0, 0.0, positions, 4, 4, 128.0, 1, out));
        assertEquals(3, candidates.select(0.0, 0.0, 0.0, 100.0, 0.0, 0.0, positions, 4, 4, 200.0, 16, out));
        assertEquals(1, out[2]);
        assertEquals(4, candidates.select(0.0, 0.0, 0.0, 100.0, 0.0, 0.0, positions, 4, 4, Double.POSITIVE_INFINITY, 16, out));
    }

    @Test
    void candidatesFollowTheHopReachOfTheLink() {
        RelayCandidates candidates = new RelayCandidates();
        double[] positions = {
                300.0, 1.0, 0.0,
                600.0, 1.0, 40.0,
                450.0, 1.0, 1400.0};
        int[] out = new int[8];
        double reach = RelayLinks.videoHopReach(VideoSystem.DIGITAL, VideoType.DIGITAL_LITE.rangeBlocks);
        int n = candidates.select(0.0, 1.6, 0.0, 900.0, 30.0, 0.0, positions, 3, 1, reach, RelayRules.MAX_CANDIDATES, out);
        assertEquals(2, n, "Repeater auf der Strecke zu einer Drohne 900 Blöcke weg");
        assertEquals(0, out[0]);
        assertEquals(1, out[1]);
        assertEquals(0, candidates.select(0.0, 1.6, 0.0, 900.0, 30.0, 0.0, positions, 3, 1, 128.0, RelayRules.MAX_CANDIDATES, out));
    }

    @Test
    void serverChainCheckFollowsTheHopsAndTheRule() {
        double hop = RadioLink.RELEASE_RANGE;
        double[] nodes = {
                1000.0, 1.0, 0.0,
                2000.0, 1.0, 0.0,
                3000.0, 1.0, 0.0};
        assertTrue(RelayReach.chain(0.0, 1.6, 0.0, 1200.0, 30.0, 0.0, nodes, 0, 4, hop));
        assertFalse(RelayReach.chain(0.0, 1.6, 0.0, 1800.0, 30.0, 0.0, nodes, 0, 4, hop));
        assertTrue(RelayReach.chain(0.0, 1.6, 0.0, 1800.0, 30.0, 0.0, nodes, 1, 4, hop));
        assertTrue(RelayReach.chain(0.0, 1.6, 0.0, 3900.0, 30.0, 0.0, nodes, 3, 4, hop));
        assertFalse(RelayReach.chain(0.0, 1.6, 0.0, 3900.0, 30.0, 0.0, nodes, 3, 2, hop), "Welt-Regel 2 schneidet die dritte Station ab");
        assertFalse(RelayReach.chain(0.0, 1.6, 0.0, 3900.0, 30.0, 0.0, nodes, 3, 0, hop), "Repeater aus");
        double[] gap = {
                1000.0, 1.0, 0.0,
                2600.0, 1.0, 0.0};
        assertFalse(RelayReach.chain(0.0, 1.6, 0.0, 3000.0, 30.0, 0.0, gap, 2, 4, hop), "Lücke über der Freigabe-Reichweite");
        double[] reversed = {
                3000.0, 1.0, 0.0,
                2000.0, 1.0, 0.0,
                1000.0, 1.0, 0.0};
        assertTrue(RelayReach.chain(0.0, 1.6, 0.0, 3900.0, 30.0, 0.0, reversed, 3, 4, hop), "Reihenfolge der Kandidaten egal");
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
