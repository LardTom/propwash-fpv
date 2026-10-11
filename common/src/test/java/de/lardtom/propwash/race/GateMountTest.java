package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateMountTest {
    private static final double EPS = 1.0E-9;
    private static final float[] YAWS = {0.0F, 90.0F, 180.0F, 270.0F};

    private static List<GateGeometry> all(double x, double y, double z) {
        List<GateGeometry> gates = new ArrayList<>();
        for (GateMount mount : GateMount.values()) {
            for (GateSize size : GateSize.values()) {
                for (float yaw : YAWS) {
                    for (boolean flipped : new boolean[]{false, true}) {
                        if (flipped && !mount.reversible()) {
                            continue;
                        }
                        gates.add(GateGeometry.of(x, y, z, yaw, size, mount, flipped));
                    }
                }
            }
        }
        return gates;
    }

    private static double[] cross(double ax, double ay, double az, double bx, double by, double bz) {
        return new double[]{ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    @Test
    void everyMountHasAnOrthonormalRightHandedFrame() {
        for (GateGeometry gate : all(3.0, 70.0, -5.0)) {
            double[] s = {gate.sideX(), gate.sideY(), gate.sideZ()};
            double[] u = {gate.upX(), gate.upY(), gate.upZ()};
            double[] n = {gate.normalX(), gate.normalY(), gate.normalZ()};
            for (double[] v : List.of(s, u, n)) {
                assertEquals(1.0, v[0] * v[0] + v[1] * v[1] + v[2] * v[2], EPS, gate.toString());
                for (double c : v) {
                    assertTrue(Math.abs(c) < EPS || Math.abs(Math.abs(c) - 1.0) < EPS, "nicht achsparallel: " + gate);
                }
            }
            double[] expected = cross(u[0], u[1], u[2], n[0], n[1], n[2]);
            for (int i = 0; i < 3; i++) {
                assertEquals(expected[i], s[i], EPS, "side = up × normal: " + gate);
            }
        }
    }

    @Test
    void mountsPointWhereTheDesignSays() {
        GateGeometry dive = GateGeometry.of(0.0, 64.0, 0.0, 270.0F, GateSize.MEDIUM, GateMount.WALL_DIVE, false);
        assertEquals(-1.0, dive.normalY(), EPS);
        assertEquals(1.0, dive.upX(), EPS);
        GateGeometry launch = GateGeometry.of(0.0, 64.0, 0.0, 270.0F, GateSize.MEDIUM, GateMount.WALL_DIVE, true);
        assertEquals(1.0, launch.normalY(), EPS);
        GateGeometry window = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM, GateMount.WALL_WINDOW, false);
        assertEquals(-1.0, window.normalZ(), EPS);
        assertEquals(1.0, window.upY(), EPS);
        GateGeometry side = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM, GateMount.WALL_SIDE, false);
        assertEquals(1.0, side.normalX(), EPS);
        assertEquals(-1.0, side.sideZ(), EPS);
        GateGeometry hanging = GateGeometry.of(0.0, 64.0, 0.0, 90.0F, GateSize.MEDIUM, GateMount.HANGING, false);
        assertEquals(-1.0, hanging.normalX(), EPS);
        assertEquals(1.0, hanging.upY(), EPS);
        GateGeometry flat = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM, GateMount.CEILING_FLAT, false);
        assertEquals(-1.0, flat.normalY(), EPS);
        assertEquals(1.0, flat.upZ(), EPS);
        assertFalse(GateGeometry.of(0.0, 0.0, 0.0, 0.0F, GateSize.MEDIUM, GateMount.HANGING, true).flipped());
    }

    private static double legacyCrossing(GateGeometry gate, double x0, double y0, double z0, double x1, double y1, double z1, boolean forward) {
        double r = Math.toRadians(gate.yawDegrees());
        double nx = Math.abs(-Math.sin(r)) < EPS ? 0.0 : -Math.sin(r);
        double nz = Math.abs(Math.cos(r)) < EPS ? 0.0 : Math.cos(r);
        double sx = Math.abs(Math.cos(r)) < EPS ? 0.0 : Math.cos(r);
        double sz = Math.abs(Math.sin(r)) < EPS ? 0.0 : Math.sin(r);
        double d0 = (x0 - gate.x()) * nx + (z0 - gate.z()) * nz;
        double d1 = (x1 - gate.x()) * nx + (z1 - gate.z()) * nz;
        boolean crosses = forward ? d0 < 0.0 && d1 >= 0.0 : d0 > 0.0 && d1 <= 0.0;
        if (!crosses) {
            return GateGeometry.NO_CROSSING;
        }
        double t = d0 / (d0 - d1);
        double hx = x0 + (x1 - x0) * t;
        double hy = y0 + (y1 - y0) * t;
        double hz = z0 + (z1 - z0) * t;
        double side = (hx - gate.x()) * sx + (hz - gate.z()) * sz;
        return gate.insideOpening(side, hy - gate.y()) ? t : GateGeometry.NO_CROSSING;
    }

    @Test
    void standingGatesCrossBitForBitLikeBefore() {
        Random random = new Random(42L);
        for (int i = 0; i < 20_000; i++) {
            float yaw = i % 3 == 0 ? random.nextFloat() * 720.0F - 360.0F : YAWS[random.nextInt(4)];
            GateGeometry gate = GateGeometry.of(random.nextGaussian() * 20.0, 60.0 + random.nextGaussian() * 5.0, random.nextGaussian() * 20.0,
                    yaw, GateSize.values()[random.nextInt(3)]);
            double x0 = gate.x() + random.nextGaussian() * 4.0;
            double y0 = gate.y() + random.nextDouble() * 5.0;
            double z0 = gate.z() + random.nextGaussian() * 4.0;
            double x1 = x0 + random.nextGaussian() * 3.0;
            double y1 = y0 + random.nextGaussian();
            double z1 = z0 + random.nextGaussian() * 3.0;
            for (boolean forward : new boolean[]{true, false}) {
                double expected = legacyCrossing(gate, x0, y0, z0, x1, y1, z1, forward);
                double actual = gate.crossing(x0, y0, z0, x1, y1, z1, forward);
                assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual), gate + " " + i);
            }
            assertEquals(gate.x(), gate.centerX());
            assertEquals(gate.z(), gate.centerZ());
            assertEquals(gate.y() + gate.height() * 0.5, gate.centerY());
        }
    }

    @Test
    void throughTheMiddleCountsOnlyInFlightDirection() {
        for (GateGeometry gate : all(-12.0, 65.0, 40.0)) {
            double cx = gate.centerX();
            double cy = gate.centerY();
            double cz = gate.centerZ();
            double nx = gate.normalX();
            double ny = gate.normalY();
            double nz = gate.normalZ();
            double t = gate.crossing(cx - nx * 2.0, cy - ny * 2.0, cz - nz * 2.0, cx + nx * 2.0, cy + ny * 2.0, cz + nz * 2.0, true);
            assertEquals(0.5, t, 1.0E-6, gate.toString());
            assertEquals(GateGeometry.NO_CROSSING,
                    gate.crossing(cx + nx * 2.0, cy + ny * 2.0, cz + nz * 2.0, cx - nx * 2.0, cy - ny * 2.0, cz - nz * 2.0, true), gate.toString());
            assertTrue(gate.crossing(cx + nx * 2.0, cy + ny * 2.0, cz + nz * 2.0, cx - nx * 2.0, cy - ny * 2.0, cz - nz * 2.0, false) >= 0.0);
            double sx = gate.sideX() * gate.width();
            double sy = gate.sideY() * gate.width();
            double sz = gate.sideZ() * gate.width();
            assertEquals(GateGeometry.NO_CROSSING, gate.crossing(cx + sx - nx, cy + sy - ny, cz + sz - nz, cx + sx + nx, cy + sy + ny,
                    cz + sz + nz, true), "neben dem Gate: " + gate);
        }
    }

    @Test
    void diveGateCountsDownwardsButNotUpwards() {
        GatePlacement.Click click = new GatePlacement.Click(GateMount.Surface.WALL, 10, 70, 20, 1, 0, 11.0, 70.4, 20.6, 0.0, 0.0, 0.0, 90.0F);
        GateGeometry dive = GatePlacement.resolve(click, GateMount.WALL_DIVE, 0, GateSize.MEDIUM);
        double x = dive.centerX();
        double z = dive.centerZ();
        assertEquals(13.0, x, EPS);
        assertEquals(20.5, z, EPS);
        assertEquals(70.5, dive.centerY(), EPS);
        assertTrue(dive.crossing(x, 74.0, z, x, 67.0, z, true) >= 0.0);
        assertEquals(GateGeometry.NO_CROSSING, dive.crossing(x, 67.0, z, x, 74.0, z, true));
        assertEquals(GateGeometry.NO_CROSSING, dive.crossing(11.2, 74.0, z, 11.2, 67.0, z, true), "durch den Balken an der Wand");
        GateGeometry launch = GatePlacement.resolve(click, GateMount.WALL_DIVE, 1, GateSize.MEDIUM);
        assertTrue(launch.crossing(x, 67.0, z, x, 74.0, z, true) >= 0.0);
        assertEquals(GateGeometry.NO_CROSSING, launch.crossing(x, 74.0, z, x, 67.0, z, true));
    }

    @Test
    void lapTrackerCountsADiveAndRejectsTheWrongWay() {
        GatePlacement.Click wall = new GatePlacement.Click(GateMount.Surface.WALL, 0, 80, 0, 0, 1, 0.5, 80.5, 1.0, 0.0, 0.0, 0.0, 180.0F);
        GateGeometry dive = GatePlacement.resolve(wall, GateMount.WALL_DIVE, 0, GateSize.MEDIUM);
        GateGeometry floor = GateGeometry.of(0.5, 64.0, 20.5, 0.0F, GateSize.MEDIUM);
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(1, List.of(dive, floor), 0L);
        long now = LapTracker.SECOND;
        double x = dive.centerX();
        double z = dive.centerZ();
        tracker.update(now, x, 78.0, z);
        tracker.drainEvents();
        for (int i = 1; i <= 20; i++) {
            now += LapTracker.SECOND / 20L;
            tracker.update(now, x, 78.0 + i * 0.25, z);
        }
        assertTrue(tracker.drainEvents().stream().noneMatch(e -> e.type() == LapTracker.EventType.START), "aufwärts durchs Tauchtor gestartet");
        for (int i = 1; i <= 40; i++) {
            now += LapTracker.SECOND / 20L;
            tracker.update(now, x, 83.0 - i * 0.25, z);
        }
        List<LapTracker.Event> events = tracker.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e.type() == LapTracker.EventType.START), "Sturzflug durchs Tauchtor zählt nicht: " + events);
        assertTrue(tracker.racing());
        for (int i = 1; i <= 40; i++) {
            now += LapTracker.SECOND / 20L;
            tracker.update(now, x, 73.0 + i * 0.25, z);
        }
        assertTrue(tracker.drainEvents().stream().noneMatch(e -> e.type() == LapTracker.EventType.GATE || e.type() == LapTracker.EventType.LAP));
    }

    @Test
    void boxesStayAxisAlignedInsideTheBoundsAndKeepTheOpeningFree() {
        for (GateGeometry gate : all(4.5, 66.0, -9.5)) {
            GateStacking.Base base = gate.mount().base();
            List<double[]> boxes = new ArrayList<>();
            gate.boxes((x0, y0, z0, x1, y1, z1) -> boxes.add(new double[]{x0, y0, z0, x1, y1, z1}), base);
            assertEquals(gate.boxCount(base), boxes.size(), gate.toString());
            double[] bounds = new double[6];
            gate.bounds((x0, y0, z0, x1, y1, z1) -> {
                bounds[0] = x0;
                bounds[1] = y0;
                bounds[2] = z0;
                bounds[3] = x1;
                bounds[4] = y1;
                bounds[5] = z1;
            });
            for (double[] box : boxes) {
                for (int i = 0; i < 3; i++) {
                    assertTrue(box[i] >= bounds[i] - EPS && box[i + 3] <= bounds[i + 3] + EPS, "Box außerhalb der Hülle: " + gate);
                    assertTrue(box[i + 3] > box[i], "leere Box: " + gate);
                }
            }
            double inner = gate.innerHalfWidth();
            double[][] free = {{0.0, gate.height() * 0.5}, {inner - 0.05, gate.frame() + 0.05}, {-inner + 0.05, gate.height() - gate.frame() - 0.05}};
            for (double[] p : free) {
                assertFalse(inside(boxes, gate.worldX(p[0], p[1], 0.0), gate.worldY(p[0], p[1], 0.0), gate.worldZ(p[0], p[1], 0.0)),
                        "Öffnung belegt bei " + p[0] + "/" + p[1] + ": " + gate);
            }
            double post = gate.width() * 0.5 - gate.frame() * 0.5;
            for (double side : new double[]{-post, post}) {
                assertTrue(inside(boxes, gate.worldX(side, gate.height() * 0.5, 0.0), gate.worldY(side, gate.height() * 0.5, 0.0),
                        gate.worldZ(side, gate.height() * 0.5, 0.0)), "Pfosten fehlt: " + gate);
            }
            assertTrue(inside(boxes, gate.worldX(0.0, gate.frame() * 0.5, 0.0), gate.worldY(0.0, gate.frame() * 0.5, 0.0),
                    gate.worldZ(0.0, gate.frame() * 0.5, 0.0)), "unterer Balken fehlt: " + gate);
        }
    }

    private static boolean inside(List<double[]> boxes, double x, double y, double z) {
        for (double[] b : boxes) {
            if (x > b[0] && x < b[3] && y > b[1] && y < b[4] && z > b[2] && z < b[5]) {
                return true;
            }
        }
        return false;
    }

    @Test
    void hangingGatesReachTheCeilingWithTheirStraps() {
        GatePlacement.Click ceiling = new GatePlacement.Click(GateMount.Surface.CEILING, 5, 90, 5, 0, 0, 5.3, 90.0, 5.6, 0.0, 0.0, 0.0, 0.0F);
        GateGeometry hanging = GatePlacement.resolve(ceiling, GateMount.HANGING, 0, GateSize.LARGE);
        assertEquals(90.0 - GateMount.HANG_DROP - GateSize.LARGE.height(), hanging.y(), EPS);
        double[] top = {Double.NEGATIVE_INFINITY};
        hanging.boxes((x0, y0, z0, x1, y1, z1) -> top[0] = Math.max(top[0], y1), GateStacking.Base.MOUNTED);
        assertEquals(90.0, top[0], EPS);
        assertEquals(90.0, hanging.y() + hanging.topExtent(), EPS);
        GateGeometry flat = GatePlacement.resolve(ceiling, GateMount.CEILING_FLAT, 0, GateSize.LARGE);
        double[] flatTop = {Double.NEGATIVE_INFINITY};
        flat.bounds((x0, y0, z0, x1, y1, z1) -> flatTop[0] = y1);
        assertEquals(90.0, flatTop[0], EPS);
    }

    @Test
    void pathLengthUsesOpeningCenters() {
        GateGeometry a = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM);
        GateGeometry b = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM, GateMount.FLOOR_FLAT, false);
        assertEquals(2.0 * Math.sqrt(4.0 + 4.0), GateGeometry.pathLength(List.of(a, b)), 1.0E-9);
    }
}
