package de.lardtom.propwash.race;

import de.lardtom.propwash.core.OrientedBox;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateFreeTransformTest {
    private static final double EPS = 1.0E-9;
    private static final float[] YAWS = {0.0F, 90.0F, 180.0F, 270.0F};

    private static GateTilt randomTilt(Random random) {
        return new GateTilt(random.nextFloat() * 360.0F - 180.0F, random.nextFloat() * 180.0F - 90.0F, random.nextFloat() * 360.0F - 180.0F);
    }

    private static GateGeometry tilted(GateMount mount, float yaw, boolean flipped, GateSize size, GateTilt tilt) {
        return GateGeometry.of(3.5, 70.0, -4.5, yaw, size, mount, flipped, size.width(), size.height(), tilt);
    }

    private static List<double[]> corners(GateGeometry gate) {
        List<double[]> out = new ArrayList<>();
        double half = gate.width() * 0.5;
        double n = gate.depth() * 0.5;
        for (int c = 0; c < 8; c++) {
            double s = (c & 1) == 0 ? -half : half;
            double u = (c & 2) == 0 ? 0.0 : gate.height();
            double d = (c & 4) == 0 ? -n : n;
            out.add(new double[]{gate.worldX(s, u, d), gate.worldY(s, u, d), gate.worldZ(s, u, d)});
        }
        return out;
    }

    private static double[] surface(GateGeometry gate) {
        return switch (gate.mount().surface()) {
            case FLOOR -> new double[]{0.0, 1.0, 0.0};
            case CEILING -> new double[]{0.0, -1.0, 0.0};
            case WALL -> new double[]{gate.facingX(), 0.0, gate.facingZ()};
        };
    }

    private static double lowest(GateGeometry gate, double[] m) {
        double best = Double.POSITIVE_INFINITY;
        for (double[] c : corners(gate)) {
            best = Math.min(best, c[0] * m[0] + c[1] * m[1] + c[2] * m[2]);
        }
        return best;
    }

    @Test
    void noTiltIsBitIdenticalToTheMountFrame() {
        for (GateMount mount : GateMount.values()) {
            for (float yaw : YAWS) {
                for (boolean flipped : new boolean[]{false, true}) {
                    GateGeometry plain = GateGeometry.of(3.5, 70.0, -4.5, yaw, GateSize.LARGE, mount, flipped);
                    GateGeometry none = tilted(mount, yaw, flipped, GateSize.LARGE, GateTilt.NONE);
                    assertEquals(plain, none);
                    assertEquals(plain.sideX(), none.sideX());
                    assertEquals(plain.normalY(), none.normalY());
                    assertEquals(plain.centerX(), none.centerX());
                    assertEquals(plain.originY(), plain.y());
                    List<double[]> a = new ArrayList<>();
                    List<double[]> b = new ArrayList<>();
                    plain.boxes((x0, y0, z0, x1, y1, z1) -> a.add(new double[]{x0, y0, z0, x1, y1, z1}));
                    none.boxes((x0, y0, z0, x1, y1, z1) -> b.add(new double[]{x0, y0, z0, x1, y1, z1}));
                    assertEquals(a.size(), b.size());
                    for (int i = 0; i < a.size(); i++) {
                        for (int k = 0; k < 6; k++) {
                            assertEquals(a.get(i)[k], b.get(i)[k]);
                        }
                    }
                    assertTrue(none.alignedFrame());
                }
            }
        }
    }

    @Test
    void anyTiltKeepsAnOrthonormalRightHandedFrame() {
        Random random = new Random(11L);
        for (int i = 0; i < 2000; i++) {
            GateMount mount = GateMount.values()[random.nextInt(GateMount.values().length)];
            GateGeometry gate = tilted(mount, YAWS[random.nextInt(4)], random.nextBoolean(), GateSize.MEDIUM, randomTilt(random));
            double[] s = {gate.sideX(), gate.sideY(), gate.sideZ()};
            double[] u = {gate.upX(), gate.upY(), gate.upZ()};
            double[] n = {gate.normalX(), gate.normalY(), gate.normalZ()};
            for (double[] v : List.of(s, u, n)) {
                assertEquals(1.0, v[0] * v[0] + v[1] * v[1] + v[2] * v[2], 1.0E-6, gate.toString());
            }
            assertEquals(0.0, s[0] * u[0] + s[1] * u[1] + s[2] * u[2], 1.0E-6);
            assertEquals(0.0, s[0] * n[0] + s[1] * n[1] + s[2] * n[2], 1.0E-6);
            assertEquals(0.0, u[0] * n[0] + u[1] * n[1] + u[2] * n[2], 1.0E-6);
            assertEquals(s[0], u[1] * n[2] - u[2] * n[1], 1.0E-6, "side = up × normal: " + gate);
            assertEquals(s[1], u[2] * n[0] - u[0] * n[2], 1.0E-6);
            assertEquals(s[2], u[0] * n[1] - u[1] * n[0], 1.0E-6);
        }
    }

    @Test
    void tiltedGatesStillRestOnTheirSurface() {
        Random random = new Random(5L);
        for (int i = 0; i < 1000; i++) {
            GateMount mount = GateMount.values()[random.nextInt(GateMount.values().length)];
            float yaw = YAWS[random.nextInt(4)];
            boolean flipped = random.nextBoolean();
            GateGeometry plain = GateGeometry.of(3.5, 70.0, -4.5, yaw, GateSize.SMALL, mount, flipped);
            GateGeometry gate = tilted(mount, yaw, flipped, GateSize.SMALL, randomTilt(random));
            double[] m = surface(plain);
            assertEquals(lowest(plain, m), lowest(gate, m), 1.0E-6, "Gate liegt nicht mehr auf seiner Fläche: " + gate);
        }
    }

    @Test
    void pitchAndRollMeanWhatTheScreenSays() {
        GateGeometry standing = tilted(GateMount.STANDING, 0.0F, false, GateSize.MEDIUM, new GateTilt(0.0F, 30.0F, 0.0F));
        assertTrue(standing.normalY() > 0.49, "positives Nicken zeigt die Flugrichtung nach oben");
        assertTrue(standing.upZ() < 0.0, "Oberkante kippt zur Einflugseite");
        GateGeometry diamond = tilted(GateMount.STANDING, 0.0F, false, GateSize.MEDIUM, new GateTilt(0.0F, 0.0F, 45.0F));
        assertEquals(0.0, diamond.normalY(), EPS);
        assertEquals(1.0, diamond.normalZ(), EPS);
        assertTrue(diamond.sideY() > 0.7, "positives Rollen hebt die linke Seite des Piloten");
        GateGeometry diagonal = tilted(GateMount.STANDING, 0.0F, false, GateSize.MEDIUM, new GateTilt(45.0F, 0.0F, 0.0F));
        GateGeometry quarter = GateGeometry.of(3.5, 70.0, -4.5, 45.0F, GateSize.MEDIUM);
        assertEquals(quarter.normalX(), diagonal.normalX(), 1.0E-9);
        assertEquals(quarter.normalZ(), diagonal.normalZ(), 1.0E-9);
        assertTrue(diagonal.upright());
        assertEquals(45.0F, diagonal.heading(), 1.0E-4F);
        GateGeometry layed = tilted(GateMount.STANDING, 0.0F, false, GateSize.MEDIUM, new GateTilt(0.0F, 90.0F, 0.0F));
        assertTrue(layed.alignedFrame(), "90° Nicken bleibt achsparallel");
        assertEquals(1.0, layed.normalY(), EPS);
    }

    @Test
    void crossingWorksAtArbitraryAngles() {
        Random random = new Random(3L);
        for (int i = 0; i < 2000; i++) {
            GateMount mount = GateMount.values()[random.nextInt(GateMount.values().length)];
            GateSize size = GateSize.values()[random.nextInt(GateSize.values().length)];
            GateGeometry gate = tilted(mount, YAWS[random.nextInt(4)], random.nextBoolean(), size, randomTilt(random));
            double cx = gate.centerX();
            double cy = gate.centerY();
            double cz = gate.centerZ();
            double side = (random.nextDouble() - 0.5) * gate.innerHalfWidth();
            double up = (random.nextDouble() - 0.5) * (gate.height() - 2.0 * gate.frame()) * 0.9;
            double px = cx + gate.sideX() * side + gate.upX() * up;
            double py = cy + gate.sideY() * side + gate.upY() * up;
            double pz = cz + gate.sideZ() * side + gate.upZ() * up;
            double ax = px - gate.normalX();
            double ay = py - gate.normalY();
            double az = pz - gate.normalZ();
            double bx = px + gate.normalX();
            double by = py + gate.normalY();
            double bz = pz + gate.normalZ();
            double t = gate.crossing(ax, ay, az, bx, by, bz, true);
            assertEquals(0.5, t, 1.0E-6, "Durchflug durch die Öffnung zählt nicht: " + gate);
            assertEquals(GateGeometry.NO_CROSSING, gate.crossing(bx, by, bz, ax, ay, az, true), "falsche Richtung zählt: " + gate);
            double post = gate.width() * 0.5 - gate.frame() * 0.5;
            double qx = cx + gate.sideX() * post;
            double qy = cy + gate.sideY() * post;
            double qz = cz + gate.sideZ() * post;
            assertEquals(GateGeometry.NO_CROSSING, gate.crossing(qx - gate.normalX(), qy - gate.normalY(), qz - gate.normalZ(), qx + gate.normalX(),
                    qy + gate.normalY(), qz + gate.normalZ(), true), "Durchflug durch den Pfosten zählt: " + gate);
        }
    }

    @Test
    void orientedPartsLeaveTheOpeningFreeAndStayInTheBounds() {
        Random random = new Random(9L);
        for (int i = 0; i < 500; i++) {
            GateMount mount = GateMount.values()[random.nextInt(GateMount.values().length)];
            GateGeometry gate = tilted(mount, YAWS[random.nextInt(4)], random.nextBoolean(), GateSize.LARGE, randomTilt(random));
            double[] hull = new double[6];
            gate.bounds((x0, y0, z0, x1, y1, z1) -> {
                hull[0] = x0;
                hull[1] = y0;
                hull[2] = z0;
                hull[3] = x1;
                hull[4] = y1;
                hull[5] = z1;
            });
            List<OrientedBox> parts = new ArrayList<>();
            gate.parts(parts::add, GateStacking.placedBase(gate));
            assertEquals(gate.boxCount(GateStacking.placedBase(gate)), parts.size());
            for (OrientedBox part : parts) {
                assertFalse(part.contains(gate.centerX(), gate.centerY(), gate.centerZ()), "Öffnung zu: " + gate);
                assertTrue(part.minX() >= hull[0] - 1.0E-6 && part.maxX() <= hull[3] + 1.0E-6 && part.minY() >= hull[1] - 1.0E-6
                        && part.maxY() <= hull[4] + 1.0E-6 && part.minZ() >= hull[2] - 1.0E-6 && part.maxZ() <= hull[5] + 1.0E-6,
                        "Teil außerhalb der Hülle: " + gate);
            }
            List<double[]> segments = new ArrayList<>();
            gate.boxes((x0, y0, z0, x1, y1, z1) -> segments.add(new double[]{x0, y0, z0, x1, y1, z1}), GateStacking.placedBase(gate));
            for (OrientedBox part : parts) {
                double[] probe = {part.cx(), part.cy(), part.cz()};
                boolean covered = false;
                for (double[] b : segments) {
                    covered |= probe[0] >= b[0] - 1.0E-6 && probe[0] <= b[3] + 1.0E-6 && probe[1] >= b[1] - 1.0E-6 && probe[1] <= b[4] + 1.0E-6
                            && probe[2] >= b[2] - 1.0E-6 && probe[2] <= b[5] + 1.0E-6;
                }
                assertTrue(covered, "Näherung für Spieler deckt den Teil nicht ab: " + gate);
            }
        }
    }

    @Test
    void hangingTiltedGatesHangOnVerticalStrapsToTheCeiling() {
        GateGeometry plain = GateGeometry.of(0.5, 60.0, 0.5, 0.0F, GateSize.MEDIUM, GateMount.HANGING, false);
        GateGeometry gate = tilted(GateMount.HANGING, 0.0F, false, GateSize.MEDIUM, new GateTilt(0.0F, 0.0F, 30.0F)).at(0.5, 60.0, 0.5);
        double ceiling = plain.y() + plain.height() + GateMount.HANG_DROP;
        assertEquals(ceiling, gate.ceilingY(), EPS);
        List<double[]> straps = new ArrayList<>();
        gate.straps((x0, y0, z0, x1, y1, z1) -> straps.add(new double[]{x0, y0, z0, x1, y1, z1}));
        assertEquals(2, straps.size());
        for (double[] strap : straps) {
            assertEquals(ceiling, strap[4], EPS);
            assertEquals(GateGeometry.HANGER_SIZE, strap[3] - strap[0], EPS);
        }
        assertNotEquals(straps.get(0)[1], straps.get(1)[1], "gerollt hängen die Gurte verschieden lang");
    }

    @Test
    void reversingATiltedGateKeepsItsPlane() {
        Random random = new Random(21L);
        for (GateMount mount : GateMount.values()) {
            if (!mount.reversible()) {
                continue;
            }
            for (int i = 0; i < 100; i++) {
                GateGeometry raw = tilted(mount, YAWS[random.nextInt(4)], false, GateSize.MEDIUM, randomTilt(random));
                GateGeometry gate = GatePlacement.rotated(GatePlacement.rotated(raw, false, false), false, false);
                assertEquals(raw.tilt(), gate.tilt());
                GateGeometry reversed = GatePlacement.rotated(gate, false, false);
                assertEquals(-gate.normalX(), reversed.normalX(), 1.0E-5, gate.toString());
                assertEquals(-gate.normalY(), reversed.normalY(), 1.0E-5, gate.toString());
                assertEquals(-gate.normalZ(), reversed.normalZ(), 1.0E-5, gate.toString());
                assertEquals(gate.centerX(), reversed.centerX(), 1.0E-5, gate.toString());
                assertEquals(gate.centerY(), reversed.centerY(), 1.0E-5, gate.toString());
                assertEquals(gate.centerZ(), reversed.centerZ(), 1.0E-5, gate.toString());
            }
        }
    }

    @Test
    void smallStepsTurnAroundTheMountPointAndFoldIntoQuarterTurns() {
        GateGeometry gate = GateGeometry.of(10.5, 64.0, -3.5, 90.0F, GateSize.MEDIUM);
        GateGeometry once = GatePlacement.rotated(gate, false, false, 45.0F);
        assertEquals(90.0F, once.yawDegrees());
        assertEquals(45.0F, once.tilt().yaw());
        assertEquals(135.0F, once.heading(), 1.0E-4F);
        GateGeometry twice = GatePlacement.rotated(once, false, false, 45.0F);
        assertEquals(180.0F, twice.yawDegrees());
        assertEquals(GateTilt.NONE, twice.tilt());
        assertEquals(GatePlacement.rotated(gate, false, false), twice);
        GateGeometry fine = gate;
        for (int i = 0; i < 72; i++) {
            fine = GatePlacement.rotated(fine, false, false, 5.0F);
            assertEquals(gate.x(), fine.x(), EPS);
            assertEquals(gate.z(), fine.z(), EPS);
        }
        assertEquals(gate.heading(), fine.heading(), 1.0E-3F);
        assertNull(GatePlacement.rotated(gate, false, true, 15.0F), "gestapelte Gates drehen nicht in kleinen Schritten");
        GateGeometry dive = GateGeometry.of(0.0, 64.5, 0.5, 270.0F, GateSize.MEDIUM, GateMount.WALL_DIVE, false);
        GateGeometry flipped = GatePlacement.rotated(dive, false, false, 15.0F);
        assertTrue(flipped.flipped(), "Wandgates kehrt die Taste weiter um");
        assertEquals(90.0F, GatePlacement.validStep(91.0F));
        assertEquals(15.0F, GatePlacement.validStep(15.0F));
    }

    @Test
    void preferenceTurnsInSmallStepsBeforePlacing() {
        GatePlacement.Preference preference = GatePlacement.Preference.DEFAULT;
        preference = preference.turnedBy(GateMount.Surface.FLOOR, 45.0F);
        assertEquals(0, preference.turn(GateMount.Surface.FLOOR));
        assertEquals(45.0F, preference.fine(GateMount.Surface.FLOOR));
        preference = preference.turnedBy(GateMount.Surface.FLOOR, 45.0F);
        assertEquals(1, preference.turn(GateMount.Surface.FLOOR));
        assertEquals(0.0F, preference.fine(GateMount.Surface.FLOOR));
        assertEquals(GatePlacement.Preference.DEFAULT.turned(GateMount.Surface.FLOOR), GatePlacement.Preference.DEFAULT
                .turnedBy(GateMount.Surface.FLOOR, 90.0F));
        GatePlacement.Preference wall = GatePlacement.Preference.DEFAULT.turnedBy(GateMount.Surface.WALL, 15.0F);
        assertEquals(1, wall.turn(GateMount.Surface.WALL));
        assertEquals(0.0F, wall.fine(GateMount.Surface.WALL));
        GatePlacement.Click click = new GatePlacement.Click(GateMount.Surface.FLOOR, 4, 63, 9, 0, 0, 4.5, 64.0, 9.5, 4.5, 64.0, 9.5, 0.0F);
        GateGeometry placed = GatePlacement.resolve(click, GatePlacement.Preference.DEFAULT.turnedBy(GateMount.Surface.FLOOR, 15.0F),
                GateSize.SMALL);
        assertEquals(15.0F, placed.tilt().yaw());
        assertFalse(placed.tilted());
        assertEquals(4.5, placed.x(), EPS);
        assertEquals(64.0, placed.originY(), 1.0E-9);
    }

    @Test
    void editingKeepsTheMountPointAndChangesTheOpening() {
        GateGeometry hanging = GateGeometry.of(2.5, 59.0, 2.5, 0.0F, GateSize.MEDIUM, GateMount.HANGING, false);
        double[] point = GatePlacement.mountPoint(hanging);
        GateGeometry bigger = GatePlacement.edited(hanging, hanging.heading(), 0.0F, 0.0F, GateSize.MEDIUM.outer(6.0), GateSize.MEDIUM.outer(4.5));
        assertEquals(7.0, bigger.width(), EPS);
        assertEquals(5.5, bigger.height(), EPS);
        double[] after = GatePlacement.mountPoint(bigger);
        for (int k = 0; k < 3; k++) {
            assertEquals(point[k], after[k], EPS);
        }
        assertEquals(hanging.ceilingY(), bigger.ceilingY(), EPS, "die Decke bleibt, wo sie ist");
        GateGeometry standing = GateGeometry.of(0.5, 64.0, 0.5, 270.0F, GateSize.SMALL);
        GateGeometry diagonal = GatePlacement.edited(standing, 315.0F, 0.0F, 0.0F, standing.width(), standing.height());
        assertEquals(270.0F, diagonal.yawDegrees());
        assertEquals(45.0F, diagonal.tilt().yaw());
        GateGeometry unchanged = GatePlacement.edited(standing, standing.heading(), 0.0F, 0.0F, standing.width(), standing.height());
        assertEquals(standing, unchanged);
    }

    @Test
    void openingSizesSnapToHalfBlocks() {
        assertEquals(1.0, GateSize.snapOpening(0.2));
        assertEquals(16.0, GateSize.snapOpening(40.0));
        assertEquals(2.5, GateSize.snapOpening(2.6));
        int packed = GateSize.packOpening(7.5, 12.0);
        assertEquals(7.5, GateSize.unpackOpeningWidth(packed));
        assertEquals(12.0, GateSize.unpackOpeningHeight(packed));
        assertEquals(GateSize.SMALL.width(), GateSize.SMALL.outer(GateSize.SMALL.openingWidth()), EPS);
        assertTrue(GateSize.LARGE.standard(6.0, 4.0));
        assertFalse(GateSize.LARGE.standard(6.5, 4.0));
    }

    @Test
    void stackingOnlyForUntiltedStandingGates() {
        GateGeometry lower = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM);
        GateGeometry upper = GateStacking.stackOn(lower, GateSize.SMALL, 180.0F);
        assertTrue(GateStacking.restsOn(upper, lower));
        GateGeometry leaning = lower.withTilt(new GateTilt(0.0F, 10.0F, 0.0F));
        assertFalse(GateStacking.stackable(leaning));
        assertFalse(GateStacking.restsOn(upper, leaning));
        assertEquals(GateStacking.Base.MOUNTED, GateStacking.placedBase(leaning));
        GateGeometry diagonal = lower.withTilt(new GateTilt(45.0F, 0.0F, 0.0F));
        GateGeometry onDiagonal = GateStacking.stackOn(diagonal, GateSize.SMALL, 0.0F);
        assertEquals(45.0F, onDiagonal.tilt().yaw());
        assertTrue(GateStacking.restsOn(onDiagonal, diagonal), "diagonale Stapel bleiben möglich");
        assertFalse(GateStacking.restsOn(upper, diagonal), "nur gleich ausgerichtete Gates bilden einen Stapel");
    }

    @Test
    void courseDataCarriesTiltAndSizeAndKeepsOldHashes() {
        CourseData.Gate plain = new CourseData.Gate(0, 1.5, 64.0, 2.5, 90.0F, GateSize.MEDIUM, GateMount.STANDING, false);
        CourseData.Gate same = new CourseData.Gate(0, 1.5, 64.0, 2.5, 90.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE,
                GateSize.MEDIUM.width(), GateSize.MEDIUM.height());
        CourseData.Gate turned = new CourseData.Gate(1, 9.5, 64.0, 2.5, 0.0F, GateSize.SMALL, GateMount.WALL_DIVE, true,
                new GateTilt(10.0F, -25.5F, 45.0F), 5.0, 3.5);
        int oldHash = new CourseData(1, "minecraft:overworld", List.of(plain)).layoutHash();
        assertEquals(oldHash, new CourseData(1, "minecraft:overworld", List.of(same)).layoutHash());
        CourseData data = new CourseData(7, "minecraft:overworld", List.of(plain, turned));
        CourseData tiltedOnly = new CourseData(7, "minecraft:overworld", List.of(plain, new CourseData.Gate(1, 9.5, 64.0, 2.5, 0.0F, GateSize.SMALL,
                GateMount.WALL_DIVE, true)));
        assertNotEquals(data.layoutHash(), tiltedOnly.layoutHash());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, data);
        CourseData read = CourseData.STREAM_CODEC.decode(buf);
        assertEquals(data, read);
        assertEquals(new GateTilt(10.0F, -25.5F, 45.0F), read.gates().get(1).tilt());
        assertEquals(5.0, read.gates().get(1).width(), EPS);
        assertEquals(data.layoutHash(), read.layoutHash());
        GateGeometry geometry = read.gates().get(1).geometry();
        assertTrue(geometry.tilted());
        assertEquals(3.5, geometry.height(), EPS);
    }
}
