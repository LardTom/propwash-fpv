package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceLineTest {
    private static final long MS = 1_000_000L;

    private static List<GateGeometry> loop() {
        return List.of(
                GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(8.0, 64.0, 15.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 66.0, 30.0, 0.0F, GateSize.LARGE),
                GateGeometry.of(-10.0, 64.0, 15.0, 180.0F, GateSize.SMALL));
    }

    private static RaceLine.Sample atGate(List<RaceLine.Sample> samples, double gate) {
        for (RaceLine.Sample sample : samples) {
            if (Math.abs(sample.gate() - gate) < 1.0E-9) {
                return sample;
            }
        }
        throw new AssertionError("keine Probe bei Gate " + gate);
    }

    private static int indexAt(List<RaceLine.Sample> samples, double gate) {
        for (int i = 0; i < samples.size(); i++) {
            if (Math.abs(samples.get(i).gate() - gate) < 1.0E-9) {
                return i;
            }
        }
        throw new AssertionError("keine Probe bei Gate " + gate);
    }

    @Test
    void remainingGatesCountsTheFinishAndTheHoleshot() {
        assertEquals(12, RaceLine.remainingGates(4, 1, 3, 1, false));
        assertEquals(5, RaceLine.remainingGates(4, 1, 1, 0, true));
        assertEquals(1, RaceLine.remainingGates(4, 3, 3, 0, false));
        assertEquals(2, RaceLine.remainingGates(4, 3, 3, 3, false));
        assertEquals(RaceLine.ENDLESS, RaceLine.remainingGates(4, 7, 0, 2, false));
        assertEquals(0, RaceLine.remainingGates(4, 0, 3, -1, false));
        assertEquals(0, RaceLine.remainingGates(0, 1, 3, 0, false));
    }

    @Test
    void passedGatesNeverReachBeforeTheStart() {
        assertEquals(0, RaceLine.passedGates(4, 1, 0, true));
        assertEquals(1, RaceLine.passedGates(4, 1, 1, false));
        assertEquals(4, RaceLine.passedGates(4, 1, 0, false));
        assertEquals(6, RaceLine.passedGates(4, 2, 2, false));
    }

    @Test
    void orderFollowsTheCourseAndWraps() {
        assertEquals(List.of(3, 0, 1), RaceLine.order(4, 3, 3));
        assertEquals(List.of(1, 2), RaceLine.order(4, 1, 2));
        assertTrue(RaceLine.order(0, 0, 3).isEmpty());
        assertEquals(RaceLine.MIN_AHEAD, RaceLine.clampAhead(0));
        assertEquals(RaceLine.MAX_AHEAD, RaceLine.clampAhead(99));
    }

    @Test
    void lineRunsThroughEveryGateCentreInCourseOrder() {
        List<GateGeometry> gates = loop();
        RaceLine.Path path = RaceLine.build(gates, 1, 4, RaceLine.ENDLESS, 1, 4.0, 65.5, 2.0);
        List<RaceLine.Sample> ahead = path.ahead();
        int previous = -1;
        for (int k = 0; k < 4; k++) {
            GateGeometry gate = gates.get((1 + k) % 4);
            RaceLine.Sample sample = atGate(ahead, k);
            assertEquals(gate.x(), sample.x(), 1.0E-9, "x bei Gate " + k);
            assertEquals(gate.centerY(), sample.y(), 1.0E-9, "y bei Gate " + k);
            assertEquals(gate.z(), sample.z(), 1.0E-9, "z bei Gate " + k);
            int index = indexAt(ahead, k);
            assertTrue(index > previous, "Reihenfolge bei Gate " + k);
            previous = index;
        }
        for (int i = 1; i < ahead.size(); i++) {
            assertTrue(ahead.get(i).distance() >= ahead.get(i - 1).distance());
            assertTrue(ahead.get(i).gate() > ahead.get(i - 1).gate());
        }
    }

    @Test
    void lineCrossesEachGateAlongItsFlightDirection() {
        List<GateGeometry> gates = loop();
        List<RaceLine.Sample> ahead = RaceLine.build(gates, 0, 4, RaceLine.ENDLESS, 4, -2.0, 65.0, -12.0).ahead();
        for (int k = 0; k < 4; k++) {
            GateGeometry gate = gates.get(k);
            int index = indexAt(ahead, k);
            RaceLine.Sample before = ahead.get(index - 1);
            RaceLine.Sample after = ahead.get(index + 1);
            double dx = after.x() - before.x();
            double dz = after.z() - before.z();
            double length = Math.hypot(dx, dz);
            double along = (dx * gate.normalX() + dz * gate.normalZ()) / length;
            assertTrue(along > 0.97, "Richtung bei Gate " + k + ": " + along);
            double t = gate.crossing(before.x(), before.y(), before.z(), after.x(), after.y(), after.z(), true);
            assertTrue(t >= 0.0, "Rundenlogik zählt den Durchflug entlang der Linie bei Gate " + k);
        }
    }

    @Test
    void lineIsSmoothInsteadOfZigZag() {
        List<RaceLine.Sample> ahead = RaceLine.build(loop(), 0, 4, RaceLine.ENDLESS, 4, 0.0, 65.0, -10.0).ahead();
        double worst = 0.0;
        for (int i = 2; i < ahead.size(); i++) {
            RaceLine.Sample a = ahead.get(i - 2);
            RaceLine.Sample b = ahead.get(i - 1);
            RaceLine.Sample c = ahead.get(i);
            double ux = b.x() - a.x();
            double uy = b.y() - a.y();
            double uz = b.z() - a.z();
            double vx = c.x() - b.x();
            double vy = c.y() - b.y();
            double vz = c.z() - b.z();
            double lu = Math.sqrt(ux * ux + uy * uy + uz * uz);
            double lv = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (lu < 1.0E-6 || lv < 1.0E-6) {
                continue;
            }
            double cos = (ux * vx + uy * vy + uz * vz) / (lu * lv);
            worst = Math.max(worst, Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos)))));
        }
        assertTrue(worst < 15.0, "Knick in der Linie: " + worst + "°");
        for (int i = 1; i < ahead.size(); i++) {
            RaceLine.Sample a = ahead.get(i - 1);
            RaceLine.Sample b = ahead.get(i);
            double step = Math.sqrt(Math.pow(b.x() - a.x(), 2) + Math.pow(b.y() - a.y(), 2) + Math.pow(b.z() - a.z(), 2));
            assertTrue(step < 1.0, "Probenabstand " + step);
        }
    }

    @Test
    void leadInStartsAtThePilotAndFadesIn() {
        RaceLine.Path path = RaceLine.build(loop(), 1, 3, RaceLine.ENDLESS, 1, 3.0, 65.0, 4.0);
        RaceLine.Sample first = path.ahead().getFirst();
        assertEquals(3.0, first.x(), 1.0E-9);
        assertEquals(65.0, first.y(), 1.0E-9);
        assertEquals(4.0, first.z(), 1.0E-9);
        assertEquals(0.0F, first.alpha(), 1.0E-6F);
        assertEquals(1.0F, first.highlight(), 1.0E-6F);
        for (RaceLine.Sample sample : path.ahead()) {
            if (sample.gate() <= 0.0) {
                assertEquals(1.0F, sample.highlight(), 1.0E-6F);
            } else if (sample.gate() >= RaceLine.HIGHLIGHT_AFTER) {
                assertEquals(0.0F, sample.highlight(), 1.0E-6F);
            }
            if (sample.distance() >= RaceLine.LEAD_IN_FADE && sample.gate() <= 0.0) {
                assertEquals(1.0F, sample.alpha(), 1.0E-6F);
            }
        }
    }

    @Test
    void onlyTheNextGatesAreShownAndTheLineFadesOut() {
        List<RaceLine.Sample> ahead = RaceLine.build(loop(), 1, 2, RaceLine.ENDLESS, 1, 3.0, 65.0, 4.0).ahead();
        RaceLine.Sample last = ahead.getLast();
        assertEquals(1.0 + RaceLine.TAIL, last.gate(), 1.0E-9);
        assertTrue(last.alpha() < 0.02F, "Ende blendet aus: " + last.alpha());
        assertTrue(atGate(ahead, 1.0).alpha() > 0.5F);
        for (RaceLine.Sample sample : ahead) {
            assertTrue(sample.gate() <= 1.0 + RaceLine.TAIL + 1.0E-9);
            assertTrue(sample.alpha() >= 0.0F && sample.alpha() <= 1.0F);
        }
    }

    @Test
    void finalLapEndsSolidAtTheFinishGate() {
        List<GateGeometry> gates = loop();
        int remaining = RaceLine.remainingGates(4, 2, 2, 3, false);
        List<RaceLine.Sample> ahead = RaceLine.build(gates, 3, 5, remaining, 7, -8.0, 65.0, 20.0).ahead();
        RaceLine.Sample last = ahead.getLast();
        assertEquals(1.0, last.gate(), 1.0E-9);
        assertEquals(gates.getFirst().x(), last.x(), 1.0E-9);
        assertEquals(gates.getFirst().z(), last.z(), 1.0E-9);
        assertTrue(last.alpha() > 0.5F, "Ziel-Gate bleibt sichtbar: " + last.alpha());
    }

    @Test
    void passedGatesAppearFaintBehindButNeverBeforeTheStart() {
        List<GateGeometry> gates = loop();
        assertTrue(RaceLine.build(gates, 1, 3, RaceLine.ENDLESS, 1, 3.0, 65.0, 4.0).behind().isEmpty());
        assertTrue(RaceLine.build(gates, 0, 3, RaceLine.ENDLESS, 0, 0.0, 65.0, -6.0).behind().isEmpty());
        List<RaceLine.Sample> behind = RaceLine.build(gates, 2, 3, RaceLine.ENDLESS, 2, 4.0, 65.0, 20.0).behind();
        assertFalse(behind.isEmpty());
        RaceLine.Sample end = behind.getLast();
        assertEquals(gates.get(1).x(), end.x(), 1.0E-9);
        assertEquals(gates.get(1).z(), end.z(), 1.0E-9);
        assertEquals(gates.get(0).x(), behind.getFirst().x(), 1.0E-9);
        for (RaceLine.Sample sample : behind) {
            assertTrue(sample.alpha() <= RaceLine.BEHIND_ALPHA + 1.0E-6F);
            assertEquals(0.0F, sample.highlight(), 1.0E-6F);
        }
        assertEquals(0.0F, behind.getFirst().alpha(), 1.0E-6F);
    }

    @Test
    void lineHidesWhenTheDroneIsFarOffTheCourse() {
        List<GateGeometry> gates = loop();
        assertFalse(RaceLine.build(gates, 1, 3, RaceLine.ENDLESS, 1, 3.0, 65.0, 4.0).isEmpty());
        assertFalse(RaceLine.build(gates, 1, 3, RaceLine.ENDLESS, 1, -22.0, 65.0, 15.0).isEmpty(), "12 Blöcke neben dem Kurs");
        assertTrue(RaceLine.build(gates, 1, 3, RaceLine.ENDLESS, 1, -28.0, 65.0, 15.0).isEmpty(), "18 Blöcke daneben, z. B. in einer Höhle");
        assertTrue(RaceLine.build(gates, 1, 3, RaceLine.ENDLESS, 1, 0.0, 30.0, 0.0).isEmpty(), "tief unter dem Kurs");
        assertEquals(0.0, RaceLine.distanceToCourse(gates, 4.0, gates.get(0).centerY(), 7.5), 1.0E-9);
    }

    @Test
    void singleGateCourseOnlyLeadsToTheGate() {
        List<GateGeometry> one = List.of(GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM));
        RaceLine.Path path = RaceLine.build(one, 0, 3, RaceLine.ENDLESS, 3, 0.0, 65.0, -8.0);
        assertEquals(0.0, path.ahead().getLast().gate(), 1.0E-9);
        assertTrue(path.behind().isEmpty());
        assertTrue(RaceLine.build(one, -1, 3, RaceLine.ENDLESS, 3, 0.0, 65.0, -8.0).isEmpty());
        assertTrue(RaceLine.build(List.of(), 0, 3, RaceLine.ENDLESS, 3, 0.0, 65.0, -8.0).isEmpty());
    }

    @Test
    void lineOnlyExistsWhileARaceIsRunning() {
        LapTracker tracker = new LapTracker();
        List<GateGeometry> gates = List.of(
                GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 10.0, 0.0F, GateSize.MEDIUM));
        tracker.setCourse(1, gates, 0L);
        tracker.setLapTarget(1);
        long now = 1000L * MS;
        tracker.update(now, 0.0, 66.0, -3.0);
        assertTrue(RaceLine.of(tracker, 3, 0.0, 66.0, -3.0).isEmpty(), "vor dem Start keine Linie");
        tracker.update(now += 200L * MS, 0.0, 66.0, 1.0);
        assertTrue(tracker.racing());
        RaceLine.Path racing = RaceLine.of(tracker, 3, 0.0, 66.0, 1.0);
        assertFalse(racing.isEmpty(), "im Rennen sichtbar");
        assertEquals(gates.get(0).x(), racing.ahead().getLast().x(), 1.0E-9);
        assertEquals(gates.get(0).z(), racing.ahead().getLast().z(), 1.0E-9);
        tracker.abort(now += MS, LapTracker.AbortReason.RESTART);
        assertTrue(RaceLine.of(tracker, 3, 0.0, 66.0, 1.0).isEmpty(), "nach dem Abbruch weg");
        tracker.resetPosition();
        tracker.update(now += MS, 0.0, 66.0, -3.0);
        tracker.update(now += 200L * MS, 0.0, 66.0, 1.0);
        tracker.update(now += 500L * MS, 0.0, 66.0, 12.0);
        tracker.update(now += 400L * MS, 6.0, 66.0, 12.0);
        tracker.update(now += 800L * MS, 6.0, 66.0, -3.0);
        tracker.update(now += 400L * MS, 0.0, 66.0, -3.0);
        tracker.update(now += 200L * MS, 0.0, 66.0, 1.0);
        assertEquals(LapTracker.Phase.FINISHED, tracker.phase());
        assertTrue(RaceLine.of(tracker, 3, 0.0, 66.0, 1.0).isEmpty(), "nach dem Ziel weg");
    }

    @Test
    void countdownShowsNoLineButTheHoleshotLeadsThroughTheStartGate() {
        LapTracker tracker = new LapTracker();
        List<GateGeometry> gates = List.of(
                GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 10.0, 0.0F, GateSize.MEDIUM));
        tracker.setCourse(1, gates, 0L);
        tracker.setLapTarget(1);
        long now = 1000L * MS;
        tracker.update(now, 0.0, 64.2, -2.0);
        assertTrue(tracker.beginCountdown(now, 3L * LapTracker.SECOND));
        assertTrue(RaceLine.of(tracker, 3, 0.0, 64.2, -2.0).isEmpty(), "Countdown ohne Linie");
        tracker.tick(now + 3L * LapTracker.SECOND);
        assertTrue(tracker.racing() && tracker.holeshot());
        RaceLine.Path path = RaceLine.of(tracker, 8, 0.0, 64.2, -2.0);
        assertEquals(0.0, atGate(path.ahead(), 0.0).z(), 1.0E-9);
        assertEquals(2.0, path.ahead().getLast().gate(), 1.0E-9, "Holeshot, Gate 2 und Ziel");
        assertTrue(path.behind().isEmpty());
    }

    @Test
    void wrongSideDoesNotCountAndMatchesTheArrows() {
        LapTracker tracker = new LapTracker();
        List<GateGeometry> gates = List.of(
                GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 10.0, 180.0F, GateSize.MEDIUM));
        tracker.setCourse(1, gates, 0L);
        long now = 1000L * MS;
        tracker.update(now, 0.0, 66.0, -3.0);
        tracker.update(now += 200L * MS, 0.0, 66.0, 1.0);
        assertEquals(1, tracker.nextGate());
        tracker.update(now += 500L * MS, 0.0, 66.0, 12.0);
        List<LapTracker.Event> events = tracker.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e.type() == LapTracker.EventType.WRONG_WAY));
        assertEquals(1, tracker.nextGate(), "falsche Seite zählt nicht");
        List<RaceLine.Sample> ahead = RaceLine.of(tracker, 2, 0.0, 66.0, 12.0).ahead();
        int index = indexAt(ahead, 0.0);
        double dz = ahead.get(index + 1).z() - ahead.get(index - 1).z();
        assertTrue(dz < 0.0, "Linie führt in Pfeilrichtung (−Z) durch das gedrehte Gate");
        tracker.update(now += 500L * MS, 0.0, 66.0, 8.0);
        assertEquals(0, tracker.nextGate(), "richtige Seite zählt");
    }

    @Test
    void beforeTheStartTheLineLeadsThroughTheStartGate() {
        LapTracker tracker = new LapTracker();
        List<GateGeometry> gates = List.of(
                GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 10.0, 0.0F, GateSize.MEDIUM));
        tracker.setCourse(1, gates, 0L);
        tracker.setLapTarget(3);
        tracker.update(1000L * MS, 0.0, 66.0, -3.0);
        assertFalse(tracker.racing());
        RaceLine.Path path = RaceLine.approach(tracker, 3, 0.0, 66.0, -3.0);
        assertFalse(path.isEmpty(), "vor dem Start zum Start-Gate");
        RaceLine.Sample atStart = path.ahead().stream().filter(s -> Math.abs(s.gate()) < 1.0E-9).findFirst().orElseThrow();
        assertEquals(gates.get(0).x(), atStart.x(), 1.0E-9);
        assertEquals(gates.get(0).z(), atStart.z(), 1.0E-9);
        assertEquals(gates.get(0).centerY(), atStart.y(), 1.0E-9);
        assertTrue(RaceLine.approach(tracker, 3, 0.0, 66.0, -RaceLine.APPROACH_RANGE - 5.0).isEmpty(), "zu weit weg");
        tracker.update(1200L * MS, 0.0, 66.0, 1.0);
        assertTrue(tracker.racing());
        assertTrue(RaceLine.approach(tracker, 3, 0.0, 66.0, 1.0).isEmpty(), "im Rennen gilt die Rennlinie");
    }
}
