package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.OrientedBox;
import de.lardtom.propwash.core.OrientedSweep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObliqueContactTest {
    private static final double HIT_EPSILON = 1.0E-7;

    private static final class World {
        final DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 42L);
        final PilotInput input = new PilotInput();
        final SimEnvironment env = new SimEnvironment();
        final StepResult result = new StepResult();
        final MoveReport report = new MoveReport();
        final OrientedSweep.Result clip = new OrientedSweep.Result();
        final List<OrientedBox> walls;
        final boolean legacy;
        final double half = this.sim.params().hitboxWidth / 2.0;
        final double height = this.sim.params().hitboxHeight;
        final double foot = this.sim.params().hitboxFootOffset;
        double x;
        double y = 10.0;
        double z;
        boolean touched;

        World(List<OrientedBox> walls, boolean legacy) {
            this.walls = walls;
            this.legacy = legacy;
            this.sim.reset(this.x, this.y, this.z, 0.0);
            this.input.mode = StabMode.ANGLE;
        }

        void frame(long nanos) {
            this.env.groundDistance = Double.POSITIVE_INFINITY;
            this.sim.step(nanos, this.input, this.env, this.result);
            double dx = this.result.dx;
            double dy = this.result.dy;
            double dz = this.result.dz;
            double bottom = this.y + this.foot;
            OrientedSweep.clip(this.x - this.half, bottom, this.z - this.half, this.x + this.half, bottom + this.height, this.z + this.half, dx, dy,
                    dz, this.walls, this.clip);
            MoveReport r = this.report;
            r.clear();
            r.requestedX = dx;
            r.requestedY = dy;
            r.requestedZ = dz;
            r.actualX = this.clip.dx;
            r.actualY = this.clip.dy;
            r.actualZ = this.clip.dz;
            if (this.clip.hit && !this.legacy) {
                r.contactNormalX = this.clip.nx;
                r.contactNormalY = this.clip.ny;
                r.contactNormalZ = this.clip.nz;
            } else {
                r.hitX = Math.abs(r.actualX - dx) > HIT_EPSILON;
                r.hitY = Math.abs(r.actualY - dy) > HIT_EPSILON;
                r.hitZ = Math.abs(r.actualZ - dz) > HIT_EPSILON;
            }
            this.touched |= this.clip.hit;
            this.x += r.actualX;
            this.y += r.actualY;
            this.z += r.actualZ;
            this.sim.afterMove(r);
        }

        double speed() {
            RigidBody b = this.sim.body;
            return Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz);
        }
    }

    private static OrientedBox wall(double angleDegrees, double distance) {
        double a = Math.toRadians(angleDegrees);
        double nx = -Math.cos(a);
        double nz = Math.sin(a);
        double tx = Math.sin(a);
        double tz = Math.cos(a);
        double thickness = 0.25;
        double cx = -nx * (distance + thickness);
        double cz = -nz * (distance + thickness);
        return new OrientedBox(cx, 10.0, cz, tx, 0.0, tz, 0.0, 1.0, 0.0, nx, 0.0, nz, 20.0, 20.0, thickness);
    }

    private static double crashSpeed(World world) {
        return world.sim.params().impactTolerance * Tests.quietTuning().crashSpeedScale;
    }

    private static SimEventType hit(World world, double speed) {
        world.sim.body.vx = speed;
        for (int f = 0; f < 200; f++) {
            world.frame(1_000_000L);
            if (world.touched) {
                EventQueue q = world.sim.events();
                if (q.contains(SimEventType.CRASH)) {
                    return SimEventType.CRASH;
                }
                if (q.contains(SimEventType.HARD_IMPACT)) {
                    return SimEventType.HARD_IMPACT;
                }
                return q.contains(SimEventType.BUMP) ? SimEventType.BUMP : null;
            }
        }
        throw new AssertionError("keine Berührung");
    }

    @Test
    void headOnIntoATurnedWallCrashesLikeIntoAStraightOne() {
        OrientedBox turned = wall(30.0, 0.3);
        World world = new World(List.of(turned), false);
        double normal = -(turned.wx());
        world.sim.body.vz = 10.0 * -turned.wz();
        assertEquals(SimEventType.CRASH, hit(world, 10.0 * normal));
        assertEquals(SimEventType.BUMP, hit(new World(List.of(wall(30.0, 0.3)), false), 3.0 / Math.cos(Math.toRadians(30.0)) * 0.98));
    }

    @Test
    void grazingADiagonalGateGlancesOffInsteadOfCrashing() {
        OrientedBox glancing = wall(80.0, 0.3);
        World world = new World(List.of(glancing), false);
        double speed = 1.1 * crashSpeed(world);
        SimEventType event = hit(world, speed);
        assertTrue(event != SimEventType.CRASH && event != SimEventType.HARD_IMPACT, "streifender Treffer wurde zu " + event);
        assertTrue(world.speed() > 0.85 * speed, "Tempo entlang der Fläche bleibt: " + world.speed());
        RigidBody b = world.sim.body;
        double before = speed * glancing.wx();
        double into = b.vx * glancing.wx() + b.vy * glancing.wy() + b.vz * glancing.wz();
        assertTrue(into > 0.8 * before, "Anflug in die Fläche nicht gebremst: vorher " + before + ", nachher " + into);
        double tx = glancing.ux();
        double tz = glancing.uz();
        double start = world.x * tx + world.z * tz;
        for (int f = 0; f < 50; f++) {
            world.frame(1_000_000L);
        }
        double travelled = world.x * tx + world.z * tz - start;
        assertTrue(travelled > 0.25 * speed * 0.05, "Drohne klebt an der Fläche statt weiterzugleiten: " + travelled);
        assertFalse(glancing.intersects(world.x - world.half, world.y + world.foot, world.z - world.half, world.x + world.half,
                world.y + world.foot + world.height, world.z + world.half));
    }

    @Test
    void axisOnlyReportingWouldHaveCrashedTheSameGraze() {
        World legacy = new World(List.of(wall(80.0, 0.3)), true);
        assertEquals(SimEventType.CRASH, hit(legacy, 1.1 * crashSpeed(legacy)));
    }

    @Test
    void reportsWithoutContactNormalKeepTheOldPath() {
        MoveReport report = new MoveReport();
        assertFalse(report.obliqueContact());
        report.contactNormalX = 1.0;
        assertFalse(report.obliqueContact());
        report.contactNormalY = 0.0;
        report.contactNormalZ = 0.0;
        assertTrue(report.obliqueContact());
        report.clear();
        assertFalse(report.obliqueContact());
    }
}
