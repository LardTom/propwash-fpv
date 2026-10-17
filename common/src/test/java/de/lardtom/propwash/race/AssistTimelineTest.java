package de.lardtom.propwash.race;

import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistTimelineTest {
    private static final long MS = 1_000_000L;
    private static final long TICK = 50 * MS;
    private static final AssistReasons GRAVITY = AssistCheck.physics(PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 130)), false);
    private static final AssistReasons CREATIVE = AssistCheck.drone(true, List.of());
    private static final AssistReasons PARTS = AssistCheck.drone(false, List.of("prop_too_large"));

    private static void fly(AssistTimeline timeline, long from, long to, AssistReasons reasons) {
        for (long t = from; t <= to; t += TICK) {
            timeline.observe(t, reasons);
        }
    }

    @Test
    void reasonsBeforeTheLapDoNotLeakIntoIt() {
        AssistTimeline timeline = new AssistTimeline();
        fly(timeline, 0, 5_000 * MS, CREATIVE);
        fly(timeline, 5_050 * MS, 20_000 * MS, AssistReasons.NONE);
        AssistReasons lap = AssistCheck.lapVerdict(timeline, 6_000 * MS, 20_000 * MS, CREATIVE, AssistReasons.NONE);
        assertEquals(AssistReasons.NONE, lap);
    }

    @Test
    void physicsResetMidFlightOnlyMarksTheLapItHappenedIn() {
        AssistTimeline timeline = new AssistTimeline();
        fly(timeline, 0, 3_000 * MS, GRAVITY);
        fly(timeline, 3_050 * MS, 30_000 * MS, AssistReasons.NONE);
        AssistReasons first = AssistCheck.lapVerdict(timeline, 1_000 * MS, 10_000 * MS, AssistReasons.NONE, AssistReasons.NONE);
        assertEquals(GRAVITY, first);
        assertEquals("gravity_scale", first.details().getFirst().subject());
        AssistReasons second = AssistCheck.lapVerdict(timeline, 10_000 * MS, 20_000 * MS, AssistReasons.NONE, AssistReasons.NONE);
        assertEquals(AssistReasons.NONE, second);
        AssistReasons third = AssistCheck.lapVerdict(timeline, 20_000 * MS, 30_000 * MS, AssistReasons.NONE, AssistReasons.NONE);
        assertEquals(AssistReasons.NONE, third);
    }

    @Test
    void aReasonAfterTheLapEndBelongsToTheNextLapEvenWhileTheReportIsPending() {
        AssistTimeline timeline = new AssistTimeline();
        fly(timeline, 0, 10_000 * MS, AssistReasons.NONE);
        fly(timeline, 10_050 * MS, 12_000 * MS, CREATIVE);
        AssistReasons pending = AssistCheck.lapVerdict(timeline, 0, 10_000 * MS, AssistReasons.NONE, AssistReasons.NONE);
        assertEquals(AssistReasons.NONE, pending);
        AssistReasons next = AssistCheck.lapVerdict(timeline, 10_000 * MS, 20_000 * MS, AssistReasons.NONE, AssistReasons.NONE);
        assertEquals(CREATIVE, next);
    }

    @Test
    void serverIsAuthoritativeForWhatItCanSee() {
        AssistTimeline timeline = new AssistTimeline();
        fly(timeline, 0, 10_000 * MS, AssistReasons.NONE);
        AssistReasons client = GRAVITY.plus(CREATIVE).plus(AssistCheck.evaluate(AssistCheck.Setup.clean().withHold(HoldMode.ALTHOLD)));
        AssistReasons verdict = AssistCheck.lapVerdict(timeline, 0, 10_000 * MS, AssistReasons.NONE, client);
        assertEquals(LapAssist.HOLD_MODE.bit(), verdict.mask());
        AssistReasons other = AssistCheck.lapVerdict(timeline, 0, 10_000 * MS, AssistReasons.NONE, AssistReasons.of(LapAssist.OTHER.bit()));
        assertEquals(LapAssist.OTHER.bit(), other.mask());
    }

    @Test
    void withoutObservationsTheCurrentStateIsUsed() {
        AssistTimeline timeline = new AssistTimeline();
        assertFalse(timeline.covers(0, 10_000 * MS));
        assertEquals(PARTS, AssistCheck.lapVerdict(timeline, 0, 10_000 * MS, PARTS, AssistReasons.NONE));
        fly(timeline, 0, 10_000 * MS, AssistReasons.NONE);
        assertTrue(timeline.covers(0, 10_000 * MS));
        assertEquals(AssistReasons.NONE, AssistCheck.lapVerdict(timeline, 0, 10_000 * MS, PARTS, AssistReasons.NONE));
    }

    @Test
    void spansMergeAndStayBounded() {
        AssistTimeline timeline = new AssistTimeline();
        fly(timeline, 0, 60_000 * MS, CREATIVE);
        assertEquals(1, timeline.spans());
        for (int i = 0; i < AssistTimeline.MAX_SPANS * 2; i++) {
            long t = 70_000 * MS + i * 2 * AssistTimeline.JOIN_GAP_NANOS;
            timeline.observe(t, i % 2 == 0 ? CREATIVE : PARTS);
        }
        assertEquals(AssistTimeline.MAX_SPANS, timeline.spans());
        timeline.forgetBefore(Long.MAX_VALUE);
        assertEquals(0, timeline.spans());
        timeline.clear();
        assertFalse(timeline.covers(0, Long.MAX_VALUE));
    }

    @Test
    void changedValuesWithinOneLapKeepTheLatestValue() {
        AssistTimeline timeline = new AssistTimeline();
        fly(timeline, 0, 2_000 * MS, GRAVITY);
        AssistReasons stronger = AssistCheck.physics(PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 150)), false);
        fly(timeline, 2_050 * MS, 4_000 * MS, stronger);
        AssistReasons lap = AssistCheck.lapVerdict(timeline, 0, 5_000 * MS, AssistReasons.NONE, AssistReasons.NONE);
        assertEquals(1, lap.details().size());
        assertEquals(150, lap.details().getFirst().value());
    }

    @Test
    void threePilotsWithDifferentSetupsGetExactlyTheirOwnReasons() {
        Map<String, AssistTimeline> server = new LinkedHashMap<>();
        Map<String, AssistReasons> serverSeen = new LinkedHashMap<>();
        Map<String, AssistReasons> clientSeen = new LinkedHashMap<>();
        server.put("clean", new AssistTimeline());
        server.put("creative", new AssistTimeline());
        server.put("hold", new AssistTimeline());
        serverSeen.put("clean", AssistReasons.NONE);
        serverSeen.put("creative", CREATIVE);
        serverSeen.put("hold", AssistReasons.NONE);
        clientSeen.put("clean", AssistReasons.NONE);
        clientSeen.put("creative", CREATIVE);
        clientSeen.put("hold", AssistCheck.evaluate(AssistCheck.Setup.clean().withHold(HoldMode.POSHOLD).withGoggles(true, true, 10, 25)));
        for (long t = 0; t <= 20_000 * MS; t += TICK) {
            for (Map.Entry<String, AssistTimeline> pilot : server.entrySet()) {
                pilot.getValue().observe(t + pilot.getKey().length() * MS, serverSeen.get(pilot.getKey()));
            }
        }
        Map<String, AssistReasons> verdicts = new LinkedHashMap<>();
        for (String pilot : server.keySet()) {
            verdicts.put(pilot, AssistCheck.lapVerdict(server.get(pilot), 1_000 * MS, 19_000 * MS, AssistReasons.NONE, clientSeen.get(pilot)));
        }
        assertEquals(AssistReasons.NONE, verdicts.get("clean"));
        assertEquals(CREATIVE, verdicts.get("creative"));
        assertEquals(LapAssist.HOLD_MODE.bit() | LapAssist.GOGGLES_LATENCY.bit(), verdicts.get("hold").mask());
        assertEquals(List.of(AssistDetail.hold(HoldMode.POSHOLD), AssistDetail.goggles(10, 25)), verdicts.get("hold").details());
    }
}
