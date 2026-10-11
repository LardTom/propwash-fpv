package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollisionTest {
    static final DroneBuild[] PRESETS = DerivationTest.PRESETS;

    private static boolean has(EventQueue q, SimEventType type) {
        return q.contains(type);
    }

    @Test
    void flatDropBouncesSettlesAndSleeps() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        AirframeSpec p = w.sim.params();
        w.place(0.0, w.groundY - p.hitboxFootOffset + 1.0, 0.0, 0.0);
        double dt = 1.0 / 144.0;
        double t = 0.0;
        boolean firstHit = false;
        boolean crashed = false;
        double restAt = Double.NaN;
        double sleepAt = Double.NaN;
        while (t < 3.5) {
            w.frame(dt);
            t += dt;
            crashed |= has(w.sim.events(), SimEventType.CRASH);
            if (!firstHit && w.report.hitY) {
                firstHit = true;
                double impact = w.sim.collision.impactSpeed;
                assertEquals(Math.sqrt(2.0 * 9.81 * 1.0), impact, 0.15);
                double e = 0.25 * (1.0 - 0.6 * SimMath.smoothstep(0.3 * 8.0, 8.0, impact));
                assertEquals(0.205, 0.25 * (1.0 - 0.6 * SimMath.smoothstep(2.4, 8.0, Math.sqrt(2.0 * 9.81))), 0.001);
                assertEquals(e, w.sim.body.vy / impact, 0.1 * e);
            }
            double speed = Math.sqrt(w.sim.body.vx * w.sim.body.vx + w.sim.body.vy * w.sim.body.vy + w.sim.body.vz * w.sim.body.vz);
            if (firstHit && Double.isNaN(restAt) && speed < 0.05 && w.sim.groundedFrames > 0) {
                restAt = t;
            }
            if (Double.isNaN(sleepAt) && w.sim.telemetry().sleeping) {
                sleepAt = t;
            }
        }
        assertTrue(firstHit);
        assertFalse(crashed);
        assertTrue(restAt <= 2.0, "rest at " + restAt);
        assertTrue(sleepAt <= 3.0, "sleep at " + sleepAt);
        assertTrue(w.sim.telemetry().tiltDeg < 2.0);
        double x0 = w.x;
        double y0 = w.y;
        double z0 = w.z;
        w.run(5.0, 144.0);
        double drift = Math.sqrt((w.x - x0) * (w.x - x0) + (w.y - y0) * (w.y - y0) + (w.z - z0) * (w.z - z0));
        assertTrue(drift < 0.001, "drift " + drift);
        assertTrue(w.sim.telemetry().sleeping);
    }

    private static FlatWorld hoverNearWall(DroneBuild b, double heading, double gap) {
        FlatWorld w = new FlatWorld(new DroneSim(b, new FlightControllerConfig(), Tests.quietTuning(), 42L));
        w.place(0.0, 3.0, 0.0, heading);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(3.0);
        double dt = 1.0 / 144.0;
        for (int f = 0; f < 288; f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        w.wallX = w.x + w.sim.params().hitboxWidth / 2.0 + gap;
        return w;
    }

    private static SimEventType impactAgainstWall(DroneBuild b, double speed) {
        FlatWorld w = hoverNearWall(b, 0.0, 0.001);
        w.sim.body.vx = speed;
        w.sim.body.vz = 0.0;
        for (int f = 0; f < 50; f++) {
            w.frame(1_000_000L);
            if (w.report.hitX) {
                EventQueue q = w.sim.events();
                if (q.contains(SimEventType.CRASH)) {
                    assertFalse(w.sim.telemetry().armed);
                    assertEquals(DisarmReason.CRASH, w.sim.telemetry().lastDisarmReason);
                    return SimEventType.CRASH;
                }
                assertTrue(w.sim.telemetry().armed);
                if (q.contains(SimEventType.HARD_IMPACT)) {
                    return SimEventType.HARD_IMPACT;
                }
                if (q.contains(SimEventType.BUMP)) {
                    return SimEventType.BUMP;
                }
                return null;
            }
        }
        return null;
    }

    @Test
    void frontalWallImpactClassification() {
        assertEquals(SimEventType.BUMP, impactAgainstWall(DroneBuild.FREESTYLE, 3.0));
        assertEquals(SimEventType.HARD_IMPACT, impactAgainstWall(DroneBuild.FREESTYLE, 5.0));
        assertEquals(SimEventType.CRASH, impactAgainstWall(DroneBuild.FREESTYLE, 10.0));
    }

    @Test
    void crashThresholdPerFrame() {
        for (DroneBuild b : PRESETS) {
            double vcr = AirframeSpec.of(b).impactTolerance;
            assertTrue(impactAgainstWall(b, 0.99 * vcr) != SimEventType.CRASH, b.frame().name());
            assertEquals(SimEventType.CRASH, impactAgainstWall(b, 1.01 * vcr), b.frame().name());
        }
    }

    @Test
    void grazingContactDoesNotCrashAndLosesTangentialSpeed() {
        FlatWorld w = hoverNearWall(DroneBuild.FREESTYLE, 0.0, 0.001);
        w.sim.body.vx = 1.0;
        w.sim.body.vz = 6.0;
        double before = 6.0;
        for (int f = 0; f < 50; f++) {
            double vz = w.sim.body.vz;
            w.frame(1_000_000L);
            if (w.report.hitX) {
                before = vz;
                break;
            }
        }
        assertTrue(w.report.hitX);
        assertFalse(w.sim.events().contains(SimEventType.CRASH));
        assertTrue(w.sim.telemetry().armed);
        double loss = before - w.sim.body.vz;
        double bound = 0.5 * (1.0 + 0.25) * 1.0;
        assertTrue(loss > 0.3 * bound && loss <= 1.05 * bound, "loss " + loss);
    }

    @Test
    void offCenterPropTipHitTumblesAndYawsRight() {
        double heading = Math.toRadians(100.0);
        FlatWorld w = hoverNearWall(DroneBuild.FREESTYLE, heading, 0.001);
        w.sim.body.vx = 4.0 * Math.sin(heading);
        w.sim.body.vz = 4.0 * Math.cos(heading);
        for (int f = 0; f < 50 && !w.report.hitX; f++) {
            w.frame(1_000_000L);
        }
        assertTrue(w.report.hitX);
        RigidBody b = w.sim.body;
        assertTrue(SimMath.hypot3(b.wbx, b.wby, b.wbz) > 3.0, "rate " + SimMath.hypot3(b.wbx, b.wby, b.wbz));
        assertTrue(b.wby < 0.0);
        assertTrue(w.sim.collision.hull.inContact[1]);
    }

    @Test
    void propStrikeOnlyForOpenProps() {
        FlatWorld open = hoverNearWall(DroneBuild.FREESTYLE, Math.toRadians(45.0), 0.001);
        open.sim.body.vx = 3.0;
        open.sim.body.vz = 0.0;
        double before = open.sim.power.omega[3];
        boolean struck = false;
        for (int f = 0; f < 50 && !struck; f++) {
            before = open.sim.power.omega[3];
            open.frame(1_000_000L);
            EventQueue q = open.sim.events();
            for (int e = 0; e < q.size(); e++) {
                if (q.type(e) == SimEventType.PROP_STRIKE) {
                    assertEquals(3, q.motor(e));
                    struck = true;
                }
            }
        }
        assertTrue(struck);
        assertTrue(open.sim.power.omega[3] <= 0.2 * before + 1e-9 || open.sim.power.omega[3] < before * 0.35);
        assertEquals(1, open.sim.telemetry().propStrikeCount);

        FlatWorld ducted = hoverNearWall(DroneBuild.CINEWHOOP, Math.toRadians(45.0), 0.001);
        ducted.sim.body.vx = 3.0;
        ducted.sim.body.vz = 0.0;
        boolean hit = false;
        for (int f = 0; f < 50; f++) {
            ducted.frame(1_000_000L);
            hit |= ducted.report.hitX;
            assertFalse(ducted.sim.events().contains(SimEventType.PROP_STRIKE));
        }
        assertTrue(hit);
    }

    private static double energy(FlatWorld w) {
        RigidBody b = w.sim.body;
        return b.kineticEnergy() + b.mass * ModelConstants.G * (b.py - w.groundY);
    }

    @Test
    void tiltedLandingRightsItselfWithoutGainingEnergy() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        w.place(0.0, w.groundY - w.sim.params().hitboxFootOffset + 0.15, 0.0, 0.0);
        RigidBody b = w.sim.body;
        double half = Math.toRadians(22.5);
        b.qw = Math.cos(half);
        b.qx = 0.0;
        b.qy = 0.0;
        b.qz = Math.sin(half);
        b.updateDerived();
        b.vy = -2.0;
        double e0 = energy(w);
        double lowest = e0;
        double dt = 1.0 / 144.0;
        for (int f = 0; f < 432; f++) {
            w.frame(dt);
            double e = energy(w);
            assertTrue(e <= lowest + 0.01 * e0, "energy rose to " + e + " from " + lowest);
            lowest = Math.min(lowest, e);
        }
        assertTrue(Math.abs(w.sim.body.r11) > 0.98, "up.y " + w.sim.body.r11);
        assertFalse(w.sim.events().contains(SimEventType.CRASH));
    }

    @Test
    void armedLandingStaysCalm() {
        for (DroneBuild b : PRESETS) {
            FlatWorld w = new FlatWorld(new DroneSim(b, new FlightControllerConfig(), Tests.quietTuning(), 42L));
            w.place(0.0, w.groundY - w.sim.params().hitboxFootOffset + 0.4, 0.0, 0.0);
            w.input.mode = StabMode.ANGLE;
            w.arm();
            AltitudeHold hold = new AltitudeHold(w.sim.body.py);
            double dt = 1.0 / 144.0;
            for (int f = 0; f < 288; f++) {
                hold.apply(w, dt);
                w.frame(dt);
            }
            w.sim.body.vy = -1.0;
            boolean landed = false;
            boolean crashed = false;
            for (int f = 0; f < 288 && !landed; f++) {
                w.input.throttle = w.sim.telemetry().hoverThrottleStick * 0.97;
                w.frame(dt);
                landed = w.sim.events().contains(SimEventType.LANDED);
                crashed |= w.sim.events().contains(SimEventType.CRASH);
            }
            String n = b.frame().name();
            assertTrue(landed, n);
            w.input.throttle = 0.0;
            for (int f = 0; f < 144; f++) {
                w.frame(dt);
                crashed |= w.sim.events().contains(SimEventType.CRASH);
            }
            assertFalse(crashed, n);
            assertTrue(w.sim.telemetry().armed, n);
            RigidBody body = w.sim.body;
            double x0 = w.x;
            double y0 = w.y;
            double z0 = w.z;
            double maxSpeed = 0.0;
            for (int f = 0; f < 72; f++) {
                w.frame(dt);
                maxSpeed = Math.max(maxSpeed, SimMath.hypot3(body.vx, body.vy, body.vz));
            }
            assertTrue(maxSpeed < 0.01, n + " speed " + maxSpeed);
            double moved = Math.sqrt((w.x - x0) * (w.x - x0) + (w.y - y0) * (w.y - y0) + (w.z - z0) * (w.z - z0));
            assertTrue(moved < 0.001, n + " moved " + moved);
            assertTrue(w.sim.telemetry().tiltDeg < 2.0, n);
        }
    }

    @Test
    void nanMoveReportIsIgnored() {
        FlatWorld w = FlatWorld.of(DroneBuild.RACER);
        w.place(0.0, 5.0, 0.0, 0.0);
        w.run(0.2, 144.0);
        SimSnapshot before = new SimSnapshot();
        w.sim.copyStateTo(before);
        int faults = w.sim.telemetry().faults;
        MoveReport bad = new MoveReport();
        bad.requestedY = -0.01;
        bad.actualY = Double.NaN;
        bad.hitY = true;
        w.sim.afterMove(bad);
        SimSnapshot after = new SimSnapshot();
        w.sim.copyStateTo(after);
        assertEquals(faults + 1, w.sim.telemetry().faults);
        assertEquals(Double.doubleToRawLongBits(before.py), Double.doubleToRawLongBits(after.py));
        assertEquals(Double.doubleToRawLongBits(before.vy), Double.doubleToRawLongBits(after.vy));
        assertEquals(Double.doubleToRawLongBits(before.ly), Double.doubleToRawLongBits(after.ly));
        assertEquals(before.groundedFrames, after.groundedFrames);
    }
}
