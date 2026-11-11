package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovGateLegsScaledTest {
    private static final double Y = 64.0;
    private static final long MS = 1_000_000L;
    private static final double[][] OPENINGS = {{4.5, 3.0}, {6.0, 5.5}, {3.0, 2.0}, {8.5, 1.5}};

    private static GateGeometry scaled(double openingWidth, double openingHeight) {
        GateGeometry base = GateGeometry.of(0.5, Y, 0.5, 0.0F, GateSize.MEDIUM);
        return GatePlacement.edited(base, base.heading(), 0.0F, 0.0F, GateSize.MEDIUM.outer(openingWidth), GateSize.MEDIUM.outer(openingHeight));
    }

    private static double cross(GateGeometry gate, double side, double y) {
        double x0 = gate.x() + gate.sideX() * side - gate.normalX();
        double z0 = gate.z() + gate.sideZ() * side - gate.normalZ();
        double x1 = gate.x() + gate.sideX() * side + gate.normalX();
        double z1 = gate.z() + gate.sideZ() * side + gate.normalZ();
        return gate.crossing(x0, y, z0, x1, y, z1, true);
    }

    @Test
    void scaledLowerGateCountsDownToTheGroundAndTheUpperFromItsBar() {
        for (double[] opening : OPENINGS) {
            String where = "Öffnung " + opening[0] + " × " + opening[1];
            GateGeometry bottom = scaled(opening[0], opening[1]);
            assertEquals(GateSize.MEDIUM.outer(opening[0]), bottom.width(), 1.0E-6, where);
            assertEquals(GateSize.MEDIUM.outer(opening[1]), bottom.height(), 1.0E-6, where);
            assertEquals(GateStacking.Refusal.NONE, GateStacking.check(bottom, 1, GateSize.SMALL), where);
            GateGeometry top = GateStacking.stackOn(bottom, GateSize.SMALL, 0.0F);
            assertTrue(GateStacking.restsOn(top, bottom), where + ": oberes Gate sitzt auf dem Balken");
            assertEquals(bottom.y() + bottom.height() - bottom.frame(), top.y(), 1.0E-9, where);
            List<GateGeometry> column = List.of(bottom, top);
            assertEquals(GateStacking.Base.LEGS, GateStacking.base(column, 0), where);
            assertEquals(GateStacking.Base.STACKED, GateStacking.base(column, 1), where);
            GateGeometry legs = bottom.withLegs(true);
            double frame = bottom.frame();
            assertEquals(0.0, legs.openingFloor(), where);
            assertEquals(frame, bottom.openingFloor(), where);
            double edge = legs.innerHalfWidth() - 0.05;
            for (double side : new double[]{-edge, 0.0, edge}) {
                for (double up : new double[]{0.02, frame * 0.5, frame - 0.01}) {
                    assertTrue(legs.insideOpening(side, up), where + ": unten auf Beinen zählt bei " + side + "/" + up);
                    assertFalse(bottom.insideOpening(side, up), where + ": mit Balken zählt es dort nicht");
                }
                assertTrue(cross(legs, side, Y + frame * 0.5) >= 0.0, where + ": Durchflug knapp über dem Boden bei " + side);
                assertEquals(GateGeometry.NO_CROSSING, cross(bottom, side, Y + frame * 0.5), where);
            }
            assertFalse(legs.insideOpening(0.0, bottom.height() - frame * 0.5), where + ": der Balken unter dem oberen Gate bleibt Rahmen");
            assertEquals(top.frame(), top.openingFloor(), where);
            assertFalse(top.legs(), where);
            assertFalse(top.insideOpening(0.0, top.frame() * 0.5), where + ": oberes Gate zählt nicht im Balken");
            assertTrue(top.insideOpening(0.0, top.frame() + 0.01), where + ": oberes Gate zählt ab dem Balken");
            assertEquals(GateGeometry.NO_CROSSING, cross(top, 0.0, top.y() + top.frame() * 0.5), where);
            assertTrue(cross(top, 0.0, top.y() + top.frame() + 0.05) >= 0.0, where);
        }
    }

    @Test
    void smallerStandardGateOnAScaledGateAndScaledOnStandard() {
        GateGeometry wide = scaled(6.0, 3.0);
        GateGeometry medium = GateStacking.stackOn(wide, GateSize.MEDIUM, 0.0F);
        assertEquals(GateStacking.Refusal.NONE, GateStacking.check(wide, 1, GateSize.MEDIUM));
        assertEquals(GateStacking.Base.LEGS, GateStacking.base(List.of(wide, medium), 0));
        GateGeometry narrow = scaled(1.5, 3.0);
        assertEquals(GateStacking.Refusal.TOO_WIDE, GateStacking.check(narrow, 1, GateSize.MEDIUM), "breiteres Gate passt nicht auf ein schmales");
        GateGeometry turned = GateStacking.stackOn(wide, GateSize.SMALL, 180.0F);
        assertTrue(GateStacking.restsOn(turned, wide), "um 180° gedreht gestapelt");
        assertEquals(GateStacking.Base.LEGS, GateStacking.base(List.of(wide, turned), 0));
    }

    @Test
    void lapLowThroughTheScaledGateNeedsTheLegs() {
        GateGeometry bottom = scaled(6.0, 4.0);
        GateGeometry top = GateStacking.stackOn(bottom, GateSize.MEDIUM, 0.0F);
        List<GateGeometry> gates = List.of(bottom, top);
        assertEquals(List.of(1), lap(gates, index -> index == 0, bottom.innerHalfWidth() - 0.3), "Runde ganz unten am Rand der Öffnung");
        assertEquals(List.of(1), lap(gates, index -> index == 0, 0.0));
        assertEquals(List.of(), lap(gates, GateOpenings.NONE, 0.0), "ohne Beine zählt unten nichts");
    }

    private static List<Integer> lap(List<GateGeometry> gates, GateOpenings openings, double side) {
        GateGeometry bottom = gates.getFirst();
        GateGeometry top = gates.get(1);
        double low = bottom.y() + bottom.frame() * 0.5;
        double mid = top.centerY();
        double x = bottom.x() + side;
        double out = bottom.x() + bottom.width() * 0.5 + 2.0;
        double[][] path = {{x, low, -3.0}, {x, low, 3.0}, {out, low, 3.0}, {out, mid, 3.0}, {out, mid, -3.0}, {0.5, mid, -3.0}, {0.5, mid, 3.0},
                {out, mid, 3.0}, {out, low, 3.0}, {out, low, -3.0}, {x, low, -3.0}, {x, low, 3.0}};
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(7, gates, 0L);
        tracker.setOpenings(openings);
        long now = 1_000L * MS;
        double[] at = path[0];
        tracker.update(now, at[0], at[1], at[2]);
        List<LapTracker.Event> events = new ArrayList<>();
        for (int leg = 1; leg < path.length; leg++) {
            double[] to = path[leg];
            for (int i = 1; i <= 20; i++) {
                now += 20 * MS;
                tracker.update(now, at[0] + (to[0] - at[0]) * i / 20.0, at[1] + (to[1] - at[1]) * i / 20.0, at[2] + (to[2] - at[2]) * i / 20.0);
                events.addAll(tracker.drainEvents());
            }
            at = to;
        }
        boolean started = events.stream().anyMatch(e -> e.type() == LapTracker.EventType.START);
        boolean lapped = events.stream().anyMatch(e -> e.type() == LapTracker.EventType.LAP);
        List<Integer> gatesPassed = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList();
        if (!started) {
            return List.of();
        }
        assertTrue(lapped, events.toString());
        assertTrue(events.stream().noneMatch(e -> e.type() == LapTracker.EventType.MISSED || e.type() == LapTracker.EventType.WRONG_WAY),
                events.toString());
        return gatesPassed;
    }
}
