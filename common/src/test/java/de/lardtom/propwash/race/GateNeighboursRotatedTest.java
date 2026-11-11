package de.lardtom.propwash.race;

import de.lardtom.propwash.core.OrientedBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateNeighboursRotatedTest {
    private static final double EPS = 1.0E-6;
    private static final float[] HEADINGS = {0.0F, 5.0F, 15.0F, 22.5F, 30.0F, 33.3F, 45.0F, 60.0F, 75.0F, 89.9F, 90.0F, 105.0F, 135.0F, 180.0F,
            200.0F, 270.0F, 315.0F, 344.5F};

    private static GateGeometry gate(double x, double z, float heading) {
        return GatePlacement.turnedTo(GateGeometry.of(x, 64.0, z, 0.0F, GateSize.MEDIUM), heading);
    }

    private static double postX(GateGeometry gate, int side) {
        return gate.x() + gate.sideX() * side * gate.postOffset();
    }

    private static double postZ(GateGeometry gate, int side) {
        return gate.z() + gate.sideZ() * side * gate.postOffset();
    }

    private static List<OrientedBox> parts(GateGeometry gate) {
        List<OrientedBox> out = new ArrayList<>();
        gate.parts(part -> out.add(part.deflated(0.01)), GateStacking.Base.BAR);
        return out;
    }

    private static boolean overlap(GateGeometry a, GateGeometry b) {
        for (OrientedBox p : parts(a)) {
            for (OrientedBox q : parts(b)) {
                if (p.intersects(q)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<GateGeometry> joined(List<GateGeometry> gates) {
        List<GateGeometry> out = new ArrayList<>();
        for (GateGeometry gate : gates) {
            List<GateGeometry> others = new ArrayList<>(gates);
            others.remove(gate);
            out.add(gate.withJoints(GateStacking.joints(gate, others).mask()));
        }
        return out;
    }

    @Test
    void rotatedGatesAreJointable() {
        for (float heading : HEADINGS) {
            GateGeometry gate = gate(0.5, 0.5, heading);
            assertEquals(heading, gate.heading(), 1.0E-3, "Richtung " + heading);
            assertTrue(GateStacking.jointable(gate), "gedreht um " + heading + "° rastet ein");
        }
        GateGeometry tilted = gate(0.5, 0.5, 30.0F).withTilt(new GateTilt(30.0F, 20.0F, 0.0F));
        assertFalse(GateStacking.jointable(tilted), "geneigte Gates rasten nicht ein");
        GateGeometry rolled = gate(0.5, 0.5, 30.0F).withTilt(new GateTilt(30.0F, 0.0F, 45.0F));
        assertFalse(GateStacking.jointable(rolled), "gerollte Gates rasten nicht ein");
    }

    @Test
    void rotatedGateSnapsPostOnPostInEveryHeading() {
        for (float heading : HEADINGS) {
            GateGeometry left = gate(0.5, 0.5, heading);
            double step = 2.0 * left.postOffset();
            double wantX = 0.5 + left.sideX() * step;
            double wantZ = 0.5 + left.sideZ() * step;
            for (double[] off : new double[][]{{0.4, 0.3}, {-0.45, 0.2}, {0.1, -0.5}, {-0.3, -0.35}}) {
                GateGeometry click = gate(wantX + off[0], wantZ + off[1], heading);
                GateGeometry snapped = GateStacking.snap(click, List.of(left));
                String where = heading + "° ab " + off[0] + "/" + off[1];
                assertNotSame(click, snapped, "rastet ein bei " + where);
                assertEquals(wantX, snapped.x(), EPS, "Pfostenmitte auf Pfostenmitte bei " + where);
                assertEquals(wantZ, snapped.z(), EPS, "Pfostenmitte auf Pfostenmitte bei " + where);
                assertEquals(heading, snapped.heading(), 1.0E-3, "Richtung bleibt bei " + where);
                assertEquals(postX(left, 1), postX(snapped, -1), EPS);
                assertEquals(postZ(left, 1), postZ(snapped, -1), EPS);
                assertEquals(1, GateStacking.sharedSide(left, snapped), where);
                assertEquals(-1, GateStacking.sharedSide(snapped, left), where);
                List<GateGeometry> row = joined(List.of(left, snapped));
                assertEquals(1, Integer.bitCount(row.get(0).joints()) + Integer.bitCount(row.get(1).joints()), "ein Pfosten fällt weg bei " + where);
                assertFalse(overlap(row.get(0), row.get(1)), "geteilter Pfosten kollidiert einmal bei " + where);
                assertTrue(overlap(left, snapped), "ohne Teilen lägen zwei Pfosten ineinander bei " + where);
                int placed = GateStacking.placementJoints(snapped, List.of(left));
                assertEquals(GateGeometry.JOINT_LEFT, placed, "Vorschau ohne linken Pfosten bei " + where);
                assertTrue(GateStacking.joints(left, List.of(snapped)).linked() && GateStacking.joints(snapped, List.of(left)).linked());
            }
        }
    }

    @Test
    void rotatedGateTurnedHalfWayRoundStillJoins() {
        GateGeometry left = gate(0.5, 0.5, 30.0F);
        double step = 2.0 * left.postOffset();
        GateGeometry turned = gate(0.5 + left.sideX() * step + 0.2, 0.5 + left.sideZ() * step - 0.3, 210.0F);
        GateGeometry snapped = GateStacking.snap(turned, List.of(left));
        assertEquals(0.5 + left.sideX() * step, snapped.x(), EPS, "Gegenrichtung rastet genauso ein");
        assertEquals(0.5 + left.sideZ() * step, snapped.z(), EPS);
        assertTrue(GateStacking.sharedSide(left, snapped) != 0);
    }

    @Test
    void differentRotationsDoNotJoin() {
        GateGeometry left = gate(0.5, 0.5, 30.0F);
        double step = 2.0 * left.postOffset();
        double x = 0.5 + left.sideX() * step + 0.2;
        double z = 0.5 + left.sideZ() * step - 0.2;
        for (float heading : new float[]{0.0F, 15.0F, 29.0F, 31.0F, 30.1F, 75.0F, 45.0F}) {
            GateGeometry other = gate(x, z, heading);
            assertSame(other, GateStacking.snap(other, List.of(left)), "30° und " + heading + "° rasten nicht ein");
            assertEquals(0, GateStacking.sharedSide(left, GateStacking.snap(other, List.of(left))));
        }
        GateGeometry higher = GatePlacement.turnedTo(GateGeometry.of(x, 65.0, z, 0.0F, GateSize.MEDIUM), 30.0F);
        assertSame(higher, GateStacking.snap(higher, List.of(left)), "nur gleich hoch stehende Gates");
    }

    private static List<GateGeometry> ring(float heading) {
        GateGeometry south = gate(0.5, 0.5, heading);
        double p = south.postOffset();
        double sx = south.sideX();
        double sz = south.sideZ();
        double nx = south.normalX();
        double nz = south.normalZ();
        GateGeometry east = GateStacking.snap(gate(0.5 + sx * p + nx * p + 0.3, 0.5 + sz * p + nz * p - 0.2, heading + 90.0F), List.of(south));
        GateGeometry west = GateStacking.snap(gate(0.5 - sx * p + nx * p - 0.2, 0.5 - sz * p + nz * p + 0.3, heading + 90.0F), List.of(south));
        GateGeometry north = GateStacking.snap(gate(0.5 + nx * 2.0 * p + 0.25, 0.5 + nz * 2.0 * p + 0.25, heading), List.of(east, west));
        return List.of(south, east, west, north);
    }

    @Test
    void rotatedRingSharesFourCorners() {
        for (float heading : new float[]{0.0F, 15.0F, 30.0F, 45.0F, 60.0F, 135.0F, 250.0F}) {
            List<GateGeometry> ring = ring(heading);
            GateGeometry south = ring.get(0);
            GateGeometry east = ring.get(1);
            GateGeometry west = ring.get(2);
            GateGeometry north = ring.get(3);
            double p = south.postOffset();
            assertEquals(0.5 + south.sideX() * p + south.normalX() * p, east.x(), EPS, "Ostseite bei " + heading + "°");
            assertEquals(0.5 + south.sideZ() * p + south.normalZ() * p, east.z(), EPS, "Ostseite bei " + heading + "°");
            assertEquals(0.5 - south.sideX() * p + south.normalX() * p, west.x(), EPS, "Westseite bei " + heading + "°");
            assertEquals(0.5 + south.normalX() * 2.0 * p, north.x(), EPS, "Nordseite bei " + heading + "°");
            assertEquals(0.5 + south.normalZ() * 2.0 * p, north.z(), EPS, "Nordseite bei " + heading + "°");
            int dropped = 0;
            for (GateGeometry gate : joined(ring)) {
                dropped += Integer.bitCount(gate.joints());
            }
            assertEquals(4, dropped, "vier Ecken, je ein Pfosten bei " + heading + "°");
            List<GateGeometry> trimmed = joined(ring);
            for (int i = 0; i < trimmed.size(); i++) {
                for (int j = i + 1; j < trimmed.size(); j++) {
                    assertFalse(overlap(trimmed.get(i), trimmed.get(j)), "Ecken kollidieren einmal bei " + heading + "°: " + i + "/" + j);
                }
            }
            assertEquals(0, GateStacking.sharedSide(south, north));
        }
    }

    @Test
    void lidOnARotatedRingMakesADiveBox() {
        for (float heading : new float[]{0.0F, 30.0F, 45.0F, 135.0F, 250.0F}) {
            List<GateGeometry> ring = ring(heading);
            GateGeometry south = ring.getFirst();
            for (GateGeometry clicked : ring) {
                List<GateGeometry> others = new ArrayList<>(ring);
                others.remove(clicked);
                GateStacking.Lid lid = GateStacking.lid(clicked, others, GateSize.MEDIUM);
                assertEquals(GateStacking.LidRefusal.NONE, lid.refusal(), "Deckel auf " + clicked + " bei " + heading + "°");
                GateGeometry top = lid.geometry();
                assertEquals(GateMount.FLOOR_FLAT, top.mount());
                assertFalse(top.tilted());
                assertEquals(64.0 + 4.0 - 0.25, top.y(), EPS);
                double cx = 0.5 + south.normalX() * south.postOffset();
                double cz = 0.5 + south.normalZ() * south.postOffset();
                assertEquals(cx, top.centerX(), EPS, "Deckel mittig bei " + heading + "°");
                assertEquals(cz, top.centerZ(), EPS, "Deckel mittig bei " + heading + "°");
                assertTrue(top.normalY() < 0.0, "Deckel wird von oben nach unten durchflogen");
                assertTrue(top.crossing(cx, 69.0, cz, cx, 66.0, cz, true) >= 0.0);
                float turn = Math.abs(top.heading() - heading) % 90.0F;
                assertTrue(turn < 1.0E-3F || turn > 90.0F - 1.0E-3F, "Deckel dreht mit dem Ring bei " + heading + "°: " + top.heading());
                for (GateGeometry side : ring) {
                    assertTrue(GateStacking.covers(top, side), "Deckel liegt auf " + side + " bei " + heading + "°");
                }
                List<GateGeometry> all = new ArrayList<>(ring);
                all.add(top);
                List<GateGeometry> cube = joined(all);
                for (int i = 0; i < 4; i++) {
                    assertTrue((cube.get(i).joints() & GateGeometry.JOINT_TOP) != 0, "Seite ohne oberen Balken bei " + heading + "°");
                    assertFalse(overlap(cube.get(i), top), "Balken und Deckel kollidieren einmal bei " + heading + "°");
                }
                assertTrue(GateStacking.joints(top, ring).linked());
            }
        }
    }
}
