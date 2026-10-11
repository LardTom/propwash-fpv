package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateStackingTest {
    private static final double EPS = 1.0E-9;
    private static final long MS = 1_000_000L;

    private static List<double[]> boxes(GateGeometry gate, GateStacking.Base base) {
        List<double[]> out = new ArrayList<>();
        gate.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> out.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ}), base);
        return out;
    }

    private static boolean overlaps(GateGeometry a, GateStacking.Base baseA, GateGeometry b, GateStacking.Base baseB) {
        for (double[] p : boxes(a, baseA)) {
            for (double[] q : boxes(b, baseB)) {
                if (p[0] < q[3] - EPS && p[3] > q[0] + EPS && p[1] < q[4] - EPS && p[4] > q[1] + EPS && p[2] < q[5] - EPS && p[5] > q[2] + EPS) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<GateGeometry> ladder(float[] yaws, GateSize... sizes) {
        List<GateGeometry> gates = new ArrayList<>();
        gates.add(GateGeometry.of(10.5, 64.0, -3.5, yaws[0], sizes[0]));
        for (int i = 1; i < sizes.length; i++) {
            gates.add(GateStacking.stackOn(gates.get(i - 1), sizes[i], yaws[i]));
        }
        return gates;
    }

    @Test
    void stackedGateSitsExactlyOnTopForEverySizePair() {
        for (float yaw : new float[]{0.0F, 90.0F, 180.0F, 270.0F}) {
            for (GateSize lower : GateSize.values()) {
                for (GateSize upper : GateSize.values()) {
                    GateGeometry base = GateGeometry.of(3.5, 70.0, -8.5, yaw, lower);
                    GateGeometry top = GateStacking.stackOn(base, upper, yaw);
                    assertEquals(base.x(), top.x(), EPS);
                    assertEquals(base.z(), top.z(), EPS);
                    assertEquals(base.y() + lower.height() - lower.frame(), top.y(), EPS);
                    assertEquals(yaw, top.yawDegrees(), 1.0E-4F);
                    assertTrue(GateStacking.restsOn(top, base));
                    assertFalse(GateStacking.restsOn(base, top));
                    assertFalse(overlaps(base, GateStacking.Base.LEGS, top, GateStacking.Base.STACKED), lower + " unter " + upper);
                    boolean fits = upper.width() <= lower.width();
                    assertEquals(fits, GateStacking.check(base, 1, upper) == GateStacking.Refusal.NONE, lower + " unter " + upper);
                    if (fits) {
                        double baseLeft = base.x() - base.halfExtentX();
                        double topLeft = top.x() - top.halfExtentX();
                        assertTrue(topLeft >= baseLeft - EPS);
                        assertEquals(base.x(), (top.x() - top.halfExtentX() + top.x() + top.halfExtentX()) * 0.5, EPS);
                    }
                }
            }
        }
    }

    @Test
    void stackedGatesShareOneCrossbarLikeALadder() {
        List<GateGeometry> gates = ladder(new float[]{0.0F, 180.0F, 0.0F}, GateSize.MEDIUM, GateSize.MEDIUM, GateSize.MEDIUM);
        assertEquals(GateStacking.Base.LEGS, GateStacking.base(gates, 0));
        assertEquals(GateStacking.Base.STACKED, GateStacking.base(gates, 1));
        assertEquals(GateStacking.Base.STACKED, GateStacking.base(gates, 2));
        assertEquals(GateStacking.Base.BAR, GateStacking.base(List.of(gates.getFirst()), 0));
        assertEquals(4, boxes(gates.getFirst(), GateStacking.Base.BAR).size());
        List<double[]> legs = boxes(gates.getFirst(), GateStacking.Base.LEGS);
        assertEquals(3, legs.size());
        assertEquals(64.0, legs.get(0)[1], EPS);
        assertEquals(68.0, legs.get(2)[4], EPS);
        for (int i = 1; i < gates.size(); i++) {
            GateGeometry lower = gates.get(i - 1);
            GateGeometry upper = gates.get(i);
            List<double[]> below = boxes(lower, GateStacking.base(gates, i - 1));
            List<double[]> above = boxes(upper, GateStacking.Base.STACKED);
            assertEquals(3, above.size(), "kein eigener unterer Balken");
            for (int post = 0; post < 2; post++) {
                double[] a = below.get(post);
                assertEquals(1, above.subList(0, 2).stream().filter(p -> Math.abs(p[0] - a[0]) < EPS && Math.abs(p[3] - a[3]) < EPS
                        && Math.abs(p[2] - a[2]) < EPS && Math.abs(p[5] - a[5]) < EPS && Math.abs(p[1] - a[4]) < EPS).count(),
                        "Pfosten laufen ohne Lücke gerade durch");
            }
            double[] shared = below.getLast();
            assertEquals(upper.y(), shared[1], EPS, "oberer Balken unten ist der Mittelbalken oben");
            assertEquals(upper.y() + upper.frame(), shared[4], EPS);
            assertFalse(overlaps(lower, GateStacking.base(gates, i - 1), upper, GateStacking.Base.STACKED));
            assertTrue(upper.insideOpening(0.0, upper.frame() + 0.01));
            assertFalse(lower.insideOpening(0.0, upper.y() - lower.y() + 0.01));
        }
    }

    @Test
    void smallerGateSitsCentredOnTheCrossbarOfABiggerOne() {
        GateGeometry large = GateGeometry.of(2.5, 64.0, 7.5, 90.0F, GateSize.LARGE);
        GateGeometry small = GateStacking.stackOn(large, GateSize.SMALL, 90.0F);
        List<double[]> bar = boxes(large, GateStacking.Base.LEGS);
        double[] top = bar.getLast();
        for (double[] post : boxes(small, GateStacking.Base.STACKED).subList(0, 2)) {
            assertEquals(top[4], post[1], EPS, "Pfosten steht auf dem Balken");
            assertTrue(post[2] >= top[2] - EPS && post[5] <= top[5] + EPS, "Pfosten innerhalb des Balkens");
        }
        assertEquals(large.z(), small.z(), EPS);
        assertEquals(large.x(), small.x(), EPS);
    }

    @Test
    void stacksReachFourGatesAndNoMore() {
        GateSize[] sizes = {GateSize.LARGE, GateSize.MEDIUM, GateSize.MEDIUM, GateSize.SMALL};
        List<GateGeometry> gates = ladder(new float[]{0.0F, 0.0F, 0.0F, 0.0F}, sizes);
        for (int i = 1; i < gates.size(); i++) {
            assertEquals(GateStacking.Refusal.NONE, GateStacking.check(gates.get(i - 1), i, sizes[i]));
        }
        assertEquals(GateStacking.Refusal.TOO_HIGH, GateStacking.check(gates.getLast(), GateStacking.MAX_STACK, GateSize.SMALL));
        assertEquals(GateStacking.Refusal.TOO_WIDE, GateStacking.check(gates.getLast(), 2, GateSize.MEDIUM));
        assertEquals(List.of(0, 1, 2, 3), GateStacking.column(gates, 2));
        assertEquals(3, GateStacking.top(gates, 0));
        assertEquals(64.0 + 3.5 + 3.5 + 3.5, gates.getLast().y(), EPS);
    }

    @Test
    void facingFollowsTheLowerGateOrIsFlippedWhenLookingBack() {
        assertEquals(90.0F, GateStacking.stackYaw(90.0F, 95.0F), 1.0E-4F);
        assertEquals(270.0F, GateStacking.stackYaw(90.0F, -88.0F), 1.0E-4F);
        assertEquals(90.0F, GateStacking.stackYaw(90.0F, 0.0F), 1.0E-4F);
        assertEquals(0.0F, GateStacking.stackYaw(180.0F, 359.0F), 1.0E-4F);
        GateGeometry lower = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM);
        GateGeometry flipped = GateStacking.stackOn(lower, GateSize.MEDIUM, 180.0F);
        assertTrue(GateStacking.restsOn(flipped, lower));
        GateGeometry turned = GateGeometry.of(0.5, 67.5, 0.5, 90.0F, GateSize.MEDIUM);
        assertFalse(GateStacking.restsOn(turned, lower));
        GateGeometry shifted = GateGeometry.of(1.5, 67.5, 0.5, 0.0F, GateSize.MEDIUM);
        assertFalse(GateStacking.restsOn(GateGeometry.of(0.5, 68.0, 0.5, 0.0F, GateSize.MEDIUM), lower));
        assertFalse(GateStacking.restsOn(shifted, lower));
    }

    @Test
    void columnIgnoresNeighboursAndFindsBothEnds() {
        List<GateGeometry> gates = new ArrayList<>(ladder(new float[]{90.0F, 270.0F, 90.0F}, GateSize.MEDIUM, GateSize.MEDIUM, GateSize.SMALL));
        gates.add(0, GateGeometry.of(20.5, 64.0, -3.5, 90.0F, GateSize.MEDIUM));
        gates.add(GateGeometry.of(10.5, 72.5, -3.5, 90.0F, GateSize.SMALL));
        assertEquals(List.of(1, 2, 3), GateStacking.column(gates, 3));
        assertEquals(List.of(0), GateStacking.column(gates, 0));
        assertEquals(2, GateStacking.below(gates, 3));
        assertEquals(-1, GateStacking.above(gates, 3));
        assertEquals(List.of(4), GateStacking.column(gates, 4));
    }

    @Test
    void breakingALowerGateLetsTheUpperOnesDropDown() {
        List<GateGeometry> gates = ladder(new float[]{0.0F, 180.0F, 0.0F}, GateSize.LARGE, GateSize.MEDIUM, GateSize.SMALL);
        List<GateStacking.Drop> bottom = GateStacking.afterRemoval(gates, 0);
        assertEquals(List.of(new GateStacking.Drop(1, 64.0), new GateStacking.Drop(2, 67.5)), bottom);
        List<GateStacking.Drop> middle = GateStacking.afterRemoval(gates, 1);
        assertEquals(List.of(new GateStacking.Drop(2, 67.5)), middle);
        assertTrue(GateStacking.afterRemoval(gates, 2).isEmpty());
        List<GateGeometry> settled = new ArrayList<>(gates);
        settled.remove(0);
        for (GateStacking.Drop drop : bottom) {
            GateGeometry g = gates.get(drop.index());
            settled.set(drop.index() - 1, new GateGeometry(g.x(), drop.y(), g.z(), g.yawDegrees(), g.width(), g.height(), g.frame(), g.depth()));
        }
        assertTrue(GateStacking.restsOn(settled.get(1), settled.get(0)));
        assertEquals(64.0, settled.get(0).y(), EPS);
    }

    @Test
    void lapLogicCountsTheOpeningOfEachStackedGate() {
        List<GateGeometry> gates = ladder(new float[]{0.0F, 180.0F, 0.0F}, GateSize.MEDIUM, GateSize.MEDIUM, GateSize.MEDIUM);
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(5, gates, 0L);
        List<LapTracker.Event> events = new ArrayList<>();
        long[] now = {1_000L * MS};
        double[] at = {10.5, 66.0, -6.5};
        tracker.update(now[0], at[0], at[1], at[2]);
        fly(tracker, events, now, at, 10.5, 66.0, -4.5);
        fly(tracker, events, now, at, 10.5, 66.0, -1.5);
        assertTrue(tracker.racing());
        assertEquals(1, tracker.nextGate());
        fly(tracker, events, now, at, 10.5, 74.0, -1.5);
        fly(tracker, events, now, at, 10.5, 74.0, -5.5);
        assertEquals(1, tracker.nextGate(), "Durchflug durch Gate 3 zählt nicht für Gate 2");
        fly(tracker, events, now, at, 10.5, 70.0, -5.5);
        fly(tracker, events, now, at, 10.5, 70.0, -1.5);
        assertEquals(1, tracker.nextGate(), "Gate 2 falsch herum zählt nicht");
        fly(tracker, events, now, at, 10.5, 70.0, -5.5);
        assertEquals(2, tracker.nextGate());
        fly(tracker, events, now, at, 10.5, 74.0, -5.5);
        fly(tracker, events, now, at, 10.5, 74.0, -1.5);
        assertEquals(0, tracker.nextGate());
        List<Integer> passed = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList();
        assertEquals(List.of(1, 2), passed);
        assertFalse(events.stream().anyMatch(e -> e.type() == LapTracker.EventType.WRONG_WAY && e.gate() != 1));
    }

    private static void fly(LapTracker tracker, List<LapTracker.Event> events, long[] now, double[] at, double x, double y, double z) {
        double dx = x - at[0];
        double dy = y - at[1];
        double dz = z - at[2];
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.3));
        double sx = at[0];
        double sy = at[1];
        double sz = at[2];
        for (int i = 1; i <= steps; i++) {
            now[0] += 30 * MS;
            at[0] = sx + dx * i / steps;
            at[1] = sy + dy * i / steps;
            at[2] = sz + dz * i / steps;
            tracker.update(now[0], at[0], at[1], at[2]);
            events.addAll(tracker.drainEvents());
        }
    }

    @Test
    void raceLineThroughALadderNeverCutsAFrame() {
        for (float[] yaws : List.of(new float[]{0.0F, 0.0F, 0.0F}, new float[]{0.0F, 180.0F, 0.0F}, new float[]{90.0F, 90.0F, 270.0F})) {
            List<GateGeometry> stack = ladder(yaws, GateSize.MEDIUM, GateSize.SMALL, GateSize.SMALL);
            List<GateGeometry> gates = new ArrayList<>(stack);
            GateGeometry base = stack.getFirst();
            GateGeometry exit = stack.getLast();
            gates.add(GateGeometry.of(base.x() + exit.normalX() * 20.0 + base.sideX() * 8.0, 64.0,
                    base.z() + exit.normalZ() * 20.0 + base.sideZ() * 8.0, yaws[2] + 180.0F, GateSize.MEDIUM));
            RaceLine.Path path = RaceLine.build(gates, 0, 4, RaceLine.ENDLESS, 0, base.x() - base.normalX() * 6.0, 66.0,
                    base.z() - base.normalZ() * 6.0);
            List<RaceLine.Sample> samples = path.ahead();
            assertFalse(samples.isEmpty());
            for (int g = 0; g < 3; g++) {
                GateGeometry gate = stack.get(g);
                int hits = 0;
                for (int i = 1; i < samples.size(); i++) {
                    RaceLine.Sample a = samples.get(i - 1);
                    RaceLine.Sample b = samples.get(i);
                    for (boolean forward : new boolean[]{true, false}) {
                        double da = gate.signedDistance(a.x(), a.z());
                        double db = gate.signedDistance(b.x(), b.z());
                        boolean crosses = forward ? da < 0.0 && db >= 0.0 : da > 0.0 && db <= 0.0;
                        if (!crosses) {
                            continue;
                        }
                        double t = da / (da - db);
                        double hx = a.x() + (b.x() - a.x()) * t;
                        double hy = a.y() + (b.y() - a.y()) * t;
                        double hz = a.z() + (b.z() - a.z()) * t;
                        double side = (hx - gate.x()) * gate.sideX() + (hz - gate.z()) * gate.sideZ();
                        double up = hy - gate.y();
                        boolean inFrame = Math.abs(side) <= gate.width() * 0.5 + 0.1 && up >= -0.1 && up <= gate.height() + 0.1;
                        if (inFrame) {
                            assertTrue(gate.insideOpening(side, up), "Linie schneidet den Rahmen von Gate " + (g + 1) + " bei " + side + "/" + up);
                            assertTrue(forward, "Linie durch Gate " + (g + 1) + " gegen die Pfeilrichtung " + yaws[0] + "/" + yaws[1] + "/" + yaws[2] + " bei " + i + " " + side + "/" + up);
                            hits++;
                        }
                    }
                }
                assertEquals(1, hits, "Linie geht genau einmal durch Gate " + (g + 1) + " bei Ausrichtungen " + yaws[0] + "/" + yaws[1] + "/" + yaws[2]);
            }
        }
    }

    @Test
    void detourOnlyForSameDirectionStacks() {
        GateGeometry lower = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM);
        assertTrue(RaceLine.needsDetour(lower, GateStacking.stackOn(lower, GateSize.MEDIUM, 0.0F)));
        assertFalse(RaceLine.needsDetour(lower, GateStacking.stackOn(lower, GateSize.MEDIUM, 180.0F)));
        assertFalse(RaceLine.needsDetour(lower, GateGeometry.of(0.5, 64.0, 12.5, 0.0F, GateSize.MEDIUM)));
        assertFalse(RaceLine.needsDetour(lower, GateGeometry.of(8.5, 68.0, 0.5, 0.0F, GateSize.MEDIUM)));
    }
}
