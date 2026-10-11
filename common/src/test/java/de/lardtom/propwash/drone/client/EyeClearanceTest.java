package de.lardtom.propwash.drone.client;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EyeClearanceTest {
    private static final double TAN_Y = Math.tan(Math.toRadians(100.0) / 2.0);
    private static final double TAN_X = TAN_Y * 16.0 / 9.0;

    static final class World implements EyeClearance.Solids {
        final List<double[]> boxes = new ArrayList<>();
        int queries;

        World box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.boxes.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ});
            return this;
        }

        World ground(double top) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    this.box(x, top - 1.0, z, x + 1, top, z + 1);
                }
            }
            return this;
        }

        @Override
        public void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, EyeClearance sink) {
            this.queries++;
            for (double[] b : this.boxes) {
                if (b[3] > minX && b[0] < maxX && b[4] > minY && b[1] < maxY && b[5] > minZ && b[2] < maxZ) {
                    sink.addBox(b[0], b[1], b[2], b[3], b[4], b[5]);
                }
            }
        }

        boolean inside(double x, double y, double z) {
            for (double[] b : this.boxes) {
                if (x > b[0] && x < b[3] && y > b[1] && y < b[4] && z > b[2] && z < b[5]) {
                    return true;
                }
            }
            return false;
        }

        boolean sightBlocked(Vector3d from, double x, double y, double z) {
            for (double[] b : this.boxes) {
                if (from.x >= b[0] && from.x <= b[3] && from.y >= b[1] && from.y <= b[4] && from.z >= b[2] && from.z <= b[5]) {
                    continue;
                }
                for (int i = 1; i <= 200; i++) {
                    double t = i / 200.0;
                    double px = from.x + t * (x - from.x);
                    double py = from.y + t * (y - from.y);
                    double pz = from.z + t * (z - from.z);
                    if (px > b[0] && px < b[3] && py > b[1] && py < b[4] && pz > b[2] && pz < b[5]) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    static final class View {
        final Vector3d f = new Vector3d();
        final Vector3d u = new Vector3d();
        final Vector3d l = new Vector3d();

        View(Quaterniond q) {
            q.transform(this.f.set(0.0, 0.0, -1.0));
            q.transform(this.u.set(0.0, 1.0, 0.0));
            q.transform(this.l.set(-1.0, 0.0, 0.0));
        }

        EyeClearance clearance() {
            EyeClearance c = new EyeClearance();
            c.view(this.f.x, this.f.y, this.f.z, this.u.x, this.u.y, this.u.z, this.l.x, this.l.y, this.l.z, TAN_X, TAN_Y);
            return c;
        }

        String problem(World world, Vector3d anchor, EyeClearance c) {
            double x = c.x();
            double y = c.y();
            double z = c.z();
            if (world.inside(x, y, z)) {
                return "Auge im Block bei " + x + " " + y + " " + z;
            }
            if (world.sightBlocked(anchor, x, y, z)) {
                return "Auge hinter einem Block";
            }
            int steps = 10;
            for (int a = 0; a <= steps; a++) {
                for (int b = 0; b <= steps; b++) {
                    for (int t = 1; t <= steps; t++) {
                        double depth = c.near() * t / steps;
                        double sx = (2.0 * a / steps - 1.0) * TAN_X;
                        double sy = (2.0 * b / steps - 1.0) * TAN_Y;
                        double px = x + depth * (this.f.x + sx * this.l.x + sy * this.u.x);
                        double py = y + depth * (this.f.y + sx * this.l.y + sy * this.u.y);
                        double pz = z + depth * (this.f.z + sx * this.l.z + sy * this.u.z);
                        if (world.inside(px, py, pz)) {
                            return "Nahebene im Block (near " + c.near() + ")";
                        }
                    }
                }
            }
            return null;
        }
    }

    private static Quaterniond pose(double yaw, double pitch, double roll) {
        return new Quaterniond().rotateY(Math.toRadians(yaw)).rotateX(Math.toRadians(pitch)).rotateZ(Math.toRadians(roll));
    }

    @Test
    void inFreeAirTheEyeAndTheNearPlaneStayExactlyAsTheyAre() {
        Random random = new Random(7L);
        World world = new World();
        for (int i = 0; i < 500; i++) {
            View view = new View(pose(random.nextDouble() * 360.0, random.nextDouble() * 180.0 - 90.0, random.nextDouble() * 360.0));
            EyeClearance c = view.clearance();
            double x = random.nextDouble() * 100.0 - 50.0;
            double y = random.nextDouble() * 100.0;
            double z = random.nextDouble() * 100.0 - 50.0;
            assertFalse(c.resolve(x, y, z, x + 0.01, y - 0.02, z + 0.03, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world));
            assertEquals(x, c.x());
            assertEquals(y, c.y());
            assertEquals(z, c.z());
            assertEquals(EyeClearance.VANILLA_NEAR, c.near());
            assertFalse(c.moved());
            assertFalse(c.shortened());
        }
    }

    @Test
    void farFromBlocksNothingChangesEvenWithGroundBelow() {
        World world = new World().ground(0.0);
        View view = new View(pose(0.0, -40.0, 0.0));
        EyeClearance c = view.clearance();
        assertFalse(c.resolve(0.3, 0.5, 0.2, 0.3, 0.48, 0.25, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world));
        assertEquals(0.5, c.y());
        assertEquals(EyeClearance.VANILLA_NEAR, c.near());
    }

    @Test
    void aLandedCameraKeepsItsPlaceAndOnlyTheNearPlaneComesCloser() {
        World world = new World().ground(0.0);
        View view = new View(pose(0.0, 25.0, 0.0));
        EyeClearance raw = view.clearance();
        raw.clearBoxes();
        world.collect(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0, raw);
        assertTrue(raw.clearNear(0.2, 0.025, 0.2) < EyeClearance.VANILLA_NEAR, "Ausgangslage: Nahebene ragt in den Boden");
        EyeClearance c = view.clearance();
        assertTrue(c.resolve(0.2, 0.025, 0.2, 0.2, 0.02, 0.24, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world));
        assertFalse(c.moved());
        assertTrue(c.shortened());
        assertEquals(0.025, c.y());
        assertTrue(c.near() >= EyeClearance.MIN_NEAR && c.near() < EyeClearance.VANILLA_NEAR, "near " + c.near());
        assertNull(view.problem(world, new Vector3d(0.2, 0.02, 0.24), c));
    }

    @Test
    void anEyeBelowTheGroundComesBackAboveTheSurface() {
        World world = new World().ground(0.0);
        View view = new View(pose(0.0, -90.0, 0.0));
        EyeClearance c = view.clearance();
        Vector3d anchor = new Vector3d(0.5, 0.025, 0.5);
        assertTrue(c.resolve(0.5, -0.03, 0.45, anchor.x, anchor.y, anchor.z, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world));
        assertTrue(c.moved());
        assertTrue(c.y() > 0.0);
        assertNull(view.problem(world, anchor, c));
        assertTrue(Math.abs(c.x() - 0.5) < 0.05 && Math.abs(c.z() - 0.45) < 0.05, "Auge bleibt über der Stelle: " + c.x() + " " + c.z());
    }

    @Test
    void theEyeNeverEndsUpBehindAThinFloor() {
        World world = new World().box(-2.0, 0.0, -2.0, 2.0, 0.0625, 2.0).box(-2.0, -3.0, -2.0, 2.0, -2.0, 2.0);
        View view = new View(pose(0.0, 0.0, 180.0));
        Vector3d anchor = new Vector3d(0.0, 0.0625 + 0.02, 0.0);
        EyeClearance c = view.clearance();
        c.resolve(0.0, -0.05, -0.04, anchor.x, anchor.y, anchor.z, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world);
        assertTrue(c.moved());
        assertTrue(c.y() > 0.0625, "Auge über dem Teppich, nicht darunter: " + c.y());
        assertNull(view.problem(world, anchor, c));
    }

    @Test
    void aWallThinnerThanThePushDoesNotLetTheEyeSlipThrough() {
        World world = new World().ground(0.0).box(-2.0, 0.0, -0.1, 2.0, 2.0, -0.02);
        View view = new View(pose(0.0, 0.0, 0.0));
        Vector3d anchor = new Vector3d(0.0, 0.1, 0.1);
        EyeClearance c = view.clearance();
        c.resolve(0.0, 0.1, -0.03, anchor.x, anchor.y, anchor.z, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world);
        assertTrue(c.z() > -0.02, "Auge bleibt vor der Scheibe: " + c.z());
        assertNull(view.problem(world, anchor, c));
    }

    @Test
    void aDroneSunkIntoSnowSeesFromAboveTheSnow() {
        World world = new World().ground(0.0);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                world.box(x, 0.0, z, x + 1, 0.125, z + 1);
            }
        }
        View view = new View(pose(30.0, 0.0, 0.0));
        Vector3d anchor = new Vector3d(0.4, 0.015, 0.6);
        EyeClearance c = view.clearance();
        assertTrue(c.resolve(0.4, 0.024, 0.58, anchor.x, anchor.y, anchor.z, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world));
        assertTrue(c.y() > 0.125, "über der Schneeschicht: " + c.y());
        assertNull(view.problem(world, anchor, c));
    }

    @Test
    void aCornerOfGroundAndWallIsSolvedOnBothSides() {
        World world = new World().ground(0.0);
        for (int x = -3; x <= 3; x++) {
            world.box(x, 0.0, -2.0, x + 1, 2.0, 0.0);
        }
        View view = new View(pose(0.0, -45.0, 0.0));
        Vector3d anchor = new Vector3d(0.5, 0.025, 0.14);
        EyeClearance c = view.clearance();
        c.resolve(0.5, -0.02, -0.01, anchor.x, anchor.y, anchor.z, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world);
        assertNull(view.problem(world, anchor, c));
    }

    @Test
    void withAShaderPackTheVanillaNearPlaneStaysAndTheEyeMovesInstead() {
        World world = new World().ground(0.0);
        View view = new View(pose(0.0, 25.0, 0.0));
        EyeClearance c = view.clearance();
        Vector3d anchor = new Vector3d(0.2, 0.02, 0.24);
        c.resolve(0.2, 0.025, 0.2, anchor.x, anchor.y, anchor.z, EyeClearance.VANILLA_NEAR, EyeClearance.VANILLA_NEAR, world);
        assertEquals(EyeClearance.VANILLA_NEAR, c.near());
        assertTrue(c.moved());
        assertNull(view.problem(world, anchor, c));
    }

    @Test
    void sinkingSlowlyIntoTheGroundMovesTheEyeSmoothly() {
        World world = new World().ground(0.0);
        View view = new View(pose(0.0, -60.0, 0.0));
        EyeClearance c = view.clearance();
        Vector3d anchor = new Vector3d(0.5, 0.03, 0.5);
        double lastY = Double.NaN;
        double lastZ = Double.NaN;
        double step = 0.001;
        for (double depth = 0.06; depth >= -0.06; depth -= step) {
            c.resolve(0.5, depth, 0.45, anchor.x, anchor.y, anchor.z, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world);
            assertNull(view.problem(world, anchor, c), "Tiefe " + depth);
            if (!Double.isNaN(lastY)) {
                assertTrue(Math.abs(c.y() - lastY) <= step + 1.0E-6 && Math.abs(c.z() - lastZ) <= 1.0E-6,
                        "Sprung bei Tiefe " + depth + ": " + lastY + " → " + c.y());
            }
            lastY = c.y();
            lastZ = c.z();
        }
    }

    @Test
    void randomPosesNearBlocksAreAlwaysFreeAfterwards() {
        Random random = new Random(11L);
        World world = new World().ground(0.0).box(-3.0, 0.0, -3.0, 4.0, 3.0, -1.0).box(-3.0, 1.2, -3.0, 4.0, 2.2, 4.0)
                .box(1.0, 0.0, -1.0, 2.0, 0.5, 4.0).box(1.0, 0.5, -1.0, 2.0, 1.0, -0.5);
        for (int i = 0; i < 3000; i++) {
            View view = new View(pose(random.nextDouble() * 360.0, random.nextDouble() * 180.0 - 90.0, random.nextDouble() * 360.0));
            Vector3d anchor;
            do {
                anchor = new Vector3d(random.nextDouble() * 3.0 - 1.0, random.nextDouble() * 1.2, random.nextDouble() * 4.0 - 1.0);
            } while (world.inside(anchor.x, anchor.y, anchor.z));
            double reach = 0.02 + random.nextDouble() * 0.3;
            Vector3d eye = new Vector3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize(reach).add(anchor);
            EyeClearance c = view.clearance();
            c.resolve(eye.x, eye.y, eye.z, anchor.x, anchor.y, anchor.z, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world);
            String problem = view.problem(world, anchor, c);
            assertNull(problem, "Lage " + i + ": Auge " + eye + " Anker " + anchor);
            assertTrue(c.near() >= EyeClearance.MIN_NEAR - 1.0E-12 && c.near() <= EyeClearance.VANILLA_NEAR);
        }
    }

    @Test
    void theNearPlaneShrinksOnlyAsFarAsNeeded() {
        World world = new World().ground(0.0);
        View view = new View(pose(0.0, 0.0, 0.0));
        EyeClearance c = view.clearance();
        c.resolve(0.0, 0.03, 0.0, 0.0, 0.02, 0.03, EyeClearance.VANILLA_NEAR, EyeClearance.MIN_NEAR, world);
        double expected = (0.03 - EyeClearance.MARGIN) / TAN_Y;
        assertEquals(Math.min(expected, EyeClearance.VANILLA_NEAR), c.near(), 1.0E-9);
    }
}
