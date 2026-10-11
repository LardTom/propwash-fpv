package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GatePlacementTest {
    private static final double EPS = 1.0E-9;

    private static GatePlacement.Click floor(float yaw) {
        return new GatePlacement.Click(GateMount.Surface.FLOOR, 7, 63, -4, 0, 0, 7.8, 64.0, -3.1, 7.5, 64.0, -3.5, yaw);
    }

    private static GatePlacement.Click wall(int faceX, int faceZ, double hitAlong, double hitY) {
        int bx = 10;
        int bz = 20;
        double hx = faceX != 0 ? bx + 0.5 + 0.5 * faceX : hitAlong;
        double hz = faceX != 0 ? hitAlong : bz + 0.5 + 0.5 * faceZ;
        return new GatePlacement.Click(GateMount.Surface.WALL, bx, 70, bz, faceX, faceZ, hx, hitY, hz, 0.0, 0.0, 0.0, 33.0F);
    }

    private static void assertSameGate(GateGeometry expected, GateGeometry actual) {
        assertSame(expected.mount(), actual.mount(), actual.toString());
        assertEquals(expected.flipped(), actual.flipped(), actual.toString());
        assertEquals(expected.yawDegrees(), actual.yawDegrees(), 1.0E-4F, actual.toString());
        assertEquals(expected.x(), actual.x(), 1.0E-9, actual.toString());
        assertEquals(expected.y(), actual.y(), 1.0E-9, actual.toString());
        assertEquals(expected.z(), actual.z(), 1.0E-9, actual.toString());
        assertEquals(expected.width(), actual.width(), 1.0E-9, actual.toString());
    }

    private static double[] bounds(GateGeometry gate) {
        double[] out = new double[6];
        gate.bounds((x0, y0, z0, x1, y1, z1) -> {
            out[0] = x0;
            out[1] = y0;
            out[2] = z0;
            out[3] = x1;
            out[4] = y1;
            out[5] = z1;
        });
        return out;
    }

    @Test
    void clickedFacePicksTheSurface() {
        assertSame(GateMount.Surface.FLOOR, GatePlacement.surface(1));
        assertSame(GateMount.Surface.CEILING, GatePlacement.surface(-1));
        assertSame(GateMount.Surface.WALL, GatePlacement.surface(0));
        assertEquals(270.0F, GatePlacement.wallYaw(1, 0));
        assertEquals(90.0F, GatePlacement.wallYaw(-1, 0));
        assertEquals(0.0F, GatePlacement.wallYaw(0, 1));
        assertEquals(180.0F, GatePlacement.wallYaw(0, -1));
    }

    @Test
    void floorGatesStayExactlyAsBefore() {
        for (float yaw : new float[]{-170.0F, 12.0F, 44.0F, 95.0F, 300.0F}) {
            for (GateSize size : GateSize.values()) {
                GateGeometry placed = GatePlacement.resolve(floor(yaw), GatePlacement.Preference.DEFAULT, size);
                assertEquals(GateGeometry.of(7.5, 64.0, -3.5, GateGeometry.snapYaw(yaw), size), placed);
            }
        }
        GateGeometry turned = GatePlacement.resolve(floor(0.0F), GateMount.STANDING, 1, GateSize.MEDIUM);
        assertEquals(90.0F, turned.yawDegrees());
        assertEquals(GateMount.STANDING, turned.mount());
    }

    @Test
    void defaultsPerSurface() {
        GateGeometry wall = GatePlacement.resolve(wall(1, 0, 20.3, 70.2), GatePlacement.Preference.DEFAULT, GateSize.MEDIUM);
        assertEquals(GateMount.WALL_DIVE, wall.mount());
        assertEquals(270.0F, wall.yawDegrees());
        assertEquals(11.0, wall.x(), EPS);
        assertEquals(70.5, wall.y(), EPS);
        assertEquals(20.5, wall.z(), EPS);
        assertEquals(-1.0, wall.normalY(), EPS);
        GatePlacement.Click ceiling = new GatePlacement.Click(GateMount.Surface.CEILING, 2, 80, 3, 0, 0, 2.2, 80.0, 3.9, 0.0, 0.0, 0.0, 181.0F);
        GateGeometry hanging = GatePlacement.resolve(ceiling, GatePlacement.Preference.DEFAULT, GateSize.SMALL);
        assertEquals(GateMount.HANGING, hanging.mount());
        assertEquals(180.0F, hanging.yawDegrees());
        assertEquals(2.5, hanging.x(), EPS);
        assertEquals(3.5, hanging.z(), EPS);
    }

    @Test
    void windowOpeningsSitOnTheBlockGrid() {
        GateGeometry medium = GatePlacement.resolve(wall(-1, 0, 20.2, 70.9), GateMount.WALL_WINDOW, 0, GateSize.MEDIUM);
        assertEquals(10.0 - GateSize.MEDIUM.depth() * 0.5, medium.x(), EPS);
        double openingBottom = medium.y() + medium.frame();
        assertEquals(69.0, openingBottom, EPS);
        assertEquals(72.0, medium.y() + medium.height() - medium.frame(), EPS);
        assertEquals(20.5, medium.centerZ(), EPS);
        assertEquals(1.0, medium.normalX(), EPS);
        GateGeometry small = GatePlacement.resolve(wall(0, 1, 10.8, 70.3), GateMount.WALL_WINDOW, 0, GateSize.SMALL);
        assertEquals(11.0, small.centerX(), EPS);
        assertEquals(69.0, small.y() + small.frame(), EPS);
        assertEquals(21.0 + GateSize.SMALL.depth() * 0.5, small.z(), EPS);
        double[] box = bounds(small);
        assertEquals(21.0, box[2], EPS);
        GateGeometry large = GatePlacement.resolve(wall(0, -1, 10.1, 70.6), GateMount.WALL_WINDOW, 1, GateSize.LARGE);
        assertEquals(10.5, large.centerX(), EPS);
        assertEquals(-1.0, large.normalZ(), EPS);
    }

    @Test
    void sideGateTouchesTheWallWithOnePost() {
        for (int[] face : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int turn = 0; turn < 2; turn++) {
                GateGeometry side = GatePlacement.resolve(wall(face[0], face[1], face[0] != 0 ? 20.5 : 10.5, 70.5), GateMount.WALL_SIDE, turn,
                        GateSize.LARGE);
                double[] box = bounds(side);
                double wallX = 10 + 0.5 + 0.5 * face[0];
                double wallZ = 20 + 0.5 + 0.5 * face[1];
                if (face[0] > 0) {
                    assertEquals(wallX, box[0], EPS);
                } else if (face[0] < 0) {
                    assertEquals(wallX, box[3], EPS);
                } else if (face[1] > 0) {
                    assertEquals(wallZ, box[2], EPS);
                } else {
                    assertEquals(wallZ, box[5], EPS);
                }
                assertEquals(0.0, side.normalX() * face[0] + side.normalZ() * face[1], EPS);
                assertEquals(70.5, side.centerY(), EPS);
            }
        }
    }

    @Test
    void flatGatesLieOnFloorAndCeiling() {
        GateGeometry pit = GatePlacement.resolve(floor(90.0F), GateMount.FLOOR_FLAT, 0, GateSize.LARGE);
        double[] box = bounds(pit);
        assertEquals(64.0, box[1], EPS);
        assertEquals(64.0 + GateSize.LARGE.depth(), box[4], EPS);
        assertEquals(7.5, pit.centerX(), EPS);
        assertEquals(-3.5, pit.centerZ(), EPS);
        assertEquals(-1.0, pit.normalY(), EPS);
        GateGeometry small = GatePlacement.resolve(floor(0.0F), GateMount.FLOOR_FLAT, 1, GateSize.SMALL);
        assertEquals(8.0, small.centerX(), EPS);
        assertEquals(-3.0, small.centerZ(), EPS);
        assertEquals(1.0, small.normalY(), EPS);
    }

    @Test
    void preferenceCyclesWithinTheSurface() {
        GatePlacement.Preference preference = GatePlacement.Preference.DEFAULT;
        preference = preference.nextMount(GateMount.Surface.WALL);
        assertEquals(GateMount.WALL_SIDE, preference.mount(GateMount.Surface.WALL));
        preference = preference.nextMount(GateMount.Surface.WALL).nextMount(GateMount.Surface.WALL);
        assertEquals(GateMount.WALL_DIVE, preference.mount(GateMount.Surface.WALL));
        preference = preference.turned(GateMount.Surface.WALL);
        assertEquals(1, preference.turn(GateMount.Surface.WALL));
        assertEquals(0, preference.turned(GateMount.Surface.WALL).turn(GateMount.Surface.WALL));
        GatePlacement.Preference floor = GatePlacement.Preference.DEFAULT;
        for (int i = 1; i <= 4; i++) {
            floor = floor.turned(GateMount.Surface.FLOOR);
            assertEquals(i % 4, floor.turn(GateMount.Surface.FLOOR));
        }
        assertEquals(GateMount.FLOOR_FLAT, floor.nextMount(GateMount.Surface.FLOOR).mount(GateMount.Surface.FLOOR));
        assertEquals(GateMount.CEILING_FLAT, floor.nextMount(GateMount.Surface.CEILING).mount(GateMount.Surface.CEILING));
        assertEquals(GateMount.HANGING, floor.nextMount(GateMount.Surface.CEILING).nextMount(GateMount.Surface.CEILING).mount(GateMount.Surface.CEILING));
        GatePlacement.Preference broken = new GatePlacement.Preference(GateMount.WALL_DIVE, 7, GateMount.HANGING, -1, GateMount.STANDING, 3);
        assertEquals(GatePlacement.Preference.DEFAULT, broken);
    }

    @Test
    void rotatingInPlaceKeepsTheMountPointOnTheGrid() {
        GatePlacement.Click[] clicks = {floor(10.0F), wall(1, 0, 20.4, 70.4), wall(0, -1, 10.7, 70.9),
                new GatePlacement.Click(GateMount.Surface.CEILING, 2, 80, 3, 0, 0, 2.2, 80.0, 3.9, 0.0, 0.0, 0.0, 270.0F)};
        for (GatePlacement.Click click : clicks) {
            for (GateSize size : GateSize.values()) {
                GateGeometry gate = GatePlacement.resolve(click, GatePlacement.Preference.DEFAULT, size);
                double[] point = GatePlacement.mountPoint(gate);
                for (int step = 0; step < 12; step++) {
                    gate = GatePlacement.rotated(gate, step % 3 == 2, false);
                    assertNotNull(gate);
                    assertEquals(click.surface(), gate.mount().surface());
                    double[] now = GatePlacement.mountPoint(gate);
                    double[] grid = GatePlacement.snapped(gate.mount(), now, gate.yawDegrees(), gate.flipped(), gate.width(), gate.height(),
                            gate.frame(), gate.depth());
                    for (int i = 0; i < 3; i++) {
                        assertEquals(grid[i], now[i], EPS, "auf dem Raster: " + gate);
                        assertEquals(point[i], now[i], size == GateSize.SMALL ? 0.5 + EPS : EPS, gate.toString());
                    }
                }
            }
        }
        GateGeometry standing = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM);
        GateGeometry turned = standing;
        for (int i = 0; i < 4; i++) {
            turned = GatePlacement.rotated(turned, false, false);
        }
        assertEquals(standing, turned);
        assertEquals(180.0F, GatePlacement.rotated(standing, false, true).yawDegrees());
        assertNull(GatePlacement.rotated(standing, true, true));
        GateGeometry dive = GatePlacement.resolve(wall(1, 0, 20.4, 70.4), GatePlacement.Preference.DEFAULT, GateSize.SMALL);
        assertEquals(GateMount.WALL_SIDE, GatePlacement.rotated(dive, true, false).mount());
        assertEquals(true, GatePlacement.rotated(dive, false, false).flipped());
    }

    @Test
    void rotatedSmallGatesLandWhereAFreshGateWould() {
        GatePlacement.Click click = wall(1, 0, 20.4, 70.4);
        GateGeometry window = GatePlacement.resolve(click, GateMount.WALL_WINDOW, 0, GateSize.SMALL);
        GateGeometry dive = GatePlacement.rotated(window, true, false);
        assertEquals(GateMount.WALL_DIVE, dive.mount());
        assertSameGate(GatePlacement.resolve(click, GateMount.WALL_DIVE, 0, GateSize.SMALL), dive);
        GateGeometry side = GatePlacement.rotated(dive, true, false);
        assertSameGate(GatePlacement.resolve(wall(1, 0, 20.4, 70.2), GateMount.WALL_SIDE, 0, GateSize.SMALL), side);
        GateGeometry standing = GatePlacement.resolve(floor(0.0F), GateMount.STANDING, 0, GateSize.SMALL);
        GateGeometry flat = GatePlacement.rotated(standing, true, false);
        assertEquals(GateMount.FLOOR_FLAT, flat.mount());
        GatePlacement.Click lower = new GatePlacement.Click(GateMount.Surface.FLOOR, 7, 63, -4, 0, 0, 7.2, 64.0, -3.9, 7.5, 64.0, -3.5, 0.0F);
        assertSameGate(GatePlacement.resolve(lower, GateMount.FLOOR_FLAT, 0, GateSize.SMALL), flat);
        assertSameGate(standing, GatePlacement.rotated(flat, true, false));
        for (GatePlacement.Click start : new GatePlacement.Click[]{click, wall(0, -1, 10.7, 70.9)}) {
            GateGeometry first = GatePlacement.resolve(start, GatePlacement.Preference.DEFAULT, GateSize.SMALL);
            GateGeometry cycled = first;
            for (int i = 0; i < 3; i++) {
                cycled = GatePlacement.rotated(cycled, true, false);
            }
            assertSameGate(first, cycled);
        }
    }

    @Test
    void mountingPlaneFollowsTheRealSurface() {
        GatePlacement.Click slab = new GatePlacement.Click(GateMount.Surface.CEILING, 2, 80, 3, 0, 0, 2.2, 80.5, 3.9, 0.0, 0.0, 0.0, 0.0F, 80.5);
        GateGeometry hanging = GatePlacement.resolve(slab, GateMount.HANGING, 0, GateSize.MEDIUM);
        assertEquals(80.5, hanging.y() + hanging.topExtent(), EPS, "Gurte reichen an die Unterseite der oberen Stufe");
        GateGeometry flat = GatePlacement.resolve(slab, GateMount.CEILING_FLAT, 0, GateSize.MEDIUM);
        assertEquals(80.5, bounds(flat)[4], EPS);
        GatePlacement.Click post = new GatePlacement.Click(GateMount.Surface.WALL, 10, 70, 20, 1, 0, 10.625, 70.4, 20.4, 0.0, 0.0, 0.0, 0.0F,
                10.625);
        GateGeometry dive = GatePlacement.resolve(post, GateMount.WALL_DIVE, 0, GateSize.MEDIUM);
        assertEquals(10.625, bounds(dive)[0], EPS, "Tauchtor liegt am Zaunpfosten an");
        assertEquals(GatePlacement.resolve(wall(1, 0, 20.4, 70.4), GateMount.WALL_DIVE, 0, GateSize.MEDIUM),
                GatePlacement.resolve(new GatePlacement.Click(GateMount.Surface.WALL, 10, 70, 20, 1, 0, 11.0, 70.4, 20.4, 0.0, 0.0, 0.0, 33.0F, 11.0),
                        GateMount.WALL_DIVE, 0, GateSize.MEDIUM));
        for (GateMount mount : new GateMount[]{GateMount.WALL_DIVE, GateMount.FLOOR_FLAT, GateMount.CEILING_FLAT}) {
            GateGeometry lying = GatePlacement.place(mount, 0.0, 300.0, 0.0, 0.0F, false, GateSize.LARGE);
            double[] box = bounds(lying);
            assertEquals(lying.depth(), box[4] - box[1], EPS, "liegende Gates sind nur so hoch wie tief: " + mount);
        }
    }

    @Test
    void alignmentRule() {
        assertEquals(4.5, GatePlacement.align(4.9, 4, 3.0), EPS);
        assertEquals(4.5, GatePlacement.align(4.1, 4, 5.0), EPS);
        assertEquals(5.0, GatePlacement.align(4.9, 4, 2.0), EPS);
        assertEquals(4.0, GatePlacement.align(4.2, 4, 2.0), EPS);
        assertEquals(-3.0, GatePlacement.align(-3.4, -4, 2.0), EPS);
        assertEquals(4.5, GatePlacement.align(4.2, 4, 2.5), EPS);
    }
}
