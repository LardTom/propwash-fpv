package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundSolverTest {
    private static final double[][] AXES = {{1.0, 0.0}, {0.0, 1.0}, {0.7071067811865476, 0.7071067811865476}, {-0.6, 0.8}};
    private static final double FPS = 144.0;
    private static final double DT = 1.0 / FPS;

    static FlatWorld resting(DroneBuild build, double tiltDeg, double[] axis) {
        FlatWorld w = FlatWorld.of(build, Tests.quietTuning());
        w.placeOnGround(0.0, 0.0, 0.0);
        SimSnapshot snapshot = new SimSnapshot();
        w.sim.copyStateTo(snapshot);
        double half = Math.toRadians(tiltDeg) / 2.0;
        snapshot.qw = Math.cos(half);
        snapshot.qx = Math.sin(half) * axis[0];
        snapshot.qy = 0.0;
        snapshot.qz = Math.sin(half) * axis[1];
        w.sim.copyStateFrom(snapshot);
        w.y = w.groundY - w.sim.ground.lowest(w.sim.params(), w.sim.body);
        w.sim.teleport(0.0, w.y, 0.0, false);
        return w;
    }

    private static double run(FlatWorld w, double seconds) {
        double deepest = 0.0;
        for (int f = 0; f < (int) Math.round(seconds * FPS); f++) {
            w.frame(DT);
            deepest = Math.max(deepest, w.groundY - w.lowest());
        }
        return deepest;
    }

    @Test
    void tiltedDroneFallsBackOnItsFeetWithoutSinking() {
        for (DroneBuild build : DerivationTest.PRESETS) {
            for (double[] axis : AXES) {
                for (double tilt : new double[]{4.0, 11.5, 25.0, 45.0, 60.0}) {
                    FlatWorld w = resting(build, tilt, axis);
                    String at = String.format(Locale.ROOT, "%s %.1f° um %.2f/%.2f", build.frame().key(), tilt, axis[0], axis[1]);
                    double deepest = run(w, 3.0);
                    assertTrue(deepest < 0.003, at + ": " + deepest * 1000.0 + " mm im Boden");
                    assertTrue(w.sim.telemetry().tiltDeg < 2.0, at + ": liegt mit " + w.sim.telemetry().tiltDeg + "°");
                    assertTrue(w.sim.telemetry().sleeping, at + ": schläft nicht");
                    assertEquals(w.groundY, w.bottom(), 0.002, at);
                }
            }
        }
    }

    @Test
    void droneOnItsBackSettlesOnItsBack() {
        for (DroneBuild build : DerivationTest.PRESETS) {
            for (double[] axis : AXES) {
                for (double tilt : new double[]{120.0, 150.0, 176.0}) {
                    FlatWorld w = resting(build, tilt, axis);
                    String at = String.format(Locale.ROOT, "%s %.1f° um %.2f/%.2f", build.frame().key(), tilt, axis[0], axis[1]);
                    double deepest = run(w, 3.0);
                    assertTrue(deepest < 0.003, at + ": " + deepest * 1000.0 + " mm im Boden");
                    assertTrue(w.sim.telemetry().tiltDeg > 120.0, at + ": liegt mit " + w.sim.telemetry().tiltDeg + "°");
                    assertTrue(w.sim.telemetry().sleeping, at + ": schläft nicht");
                }
            }
        }
    }

    @Test
    void spinOnTheGroundCannotTurnThroughTheFloor() {
        for (DroneBuild build : DerivationTest.PRESETS) {
            for (double[] axis : AXES) {
                FlatWorld w = resting(build, 0.0, axis);
                w.run(0.2, FPS);
                w.sim.body.setBodyRates(15.0 * axis[0], 0.0, 15.0 * axis[1]);
                String at = String.format(Locale.ROOT, "%s um %.2f/%.2f", build.frame().key(), axis[0], axis[1]);
                double deepest = run(w, 3.0);
                assertTrue(deepest < 0.003, at + ": " + deepest * 1000.0 + " mm im Boden");
                assertTrue(w.sim.telemetry().tiltDeg < 2.0, at + ": liegt mit " + w.sim.telemetry().tiltDeg + "°");
            }
        }
    }

    @Test
    void levelTouchdownWithDriftSettlesFlat() {
        StringBuilder report = new StringBuilder();
        for (DroneBuild build : DerivationTest.PRESETS) {
            for (double speed : new double[]{0.5, 1.5, 3.0}) {
                FlatWorld w = FlatWorld.of(build, Tests.quietTuning());
                w.place(0.0, w.groundY - w.sim.params().hitboxFootOffset + 0.02, 0.0, 0.0);
                w.sim.body.vx = speed;
                w.sim.body.vy = -1.0;
                double rate = 0.0;
                double tilt = 0.0;
                double deepest = 0.0;
                for (int f = 0; f < (int) Math.round(1.5 * FPS); f++) {
                    w.frame(DT);
                    RigidBody b = w.sim.body;
                    rate = Math.max(rate, Math.toDegrees(Math.sqrt(b.wbx * b.wbx + b.wby * b.wby + b.wbz * b.wbz)));
                    tilt = Math.max(tilt, w.sim.telemetry().tiltDeg);
                    deepest = Math.max(deepest, w.groundY - w.lowest());
                }
                String at = String.format(Locale.ROOT, "%s %.1f m/s: %.0f °/s, bis %.1f°", build.frame().key(), speed, rate, tilt);
                report.append(at).append('\n');
                assertTrue(rate < 30.0, at);
                assertTrue(tilt < 5.0, at);
                assertTrue(deepest < 0.003, at + ", " + deepest * 1000.0 + " mm im Boden");
                assertTrue(w.sim.telemetry().tiltDeg < 2.0, at + ", liegt mit " + w.sim.telemetry().tiltDeg + "°");
            }
        }
        CalibrationHarness.write("touchdown.txt", report.toString());
    }

    @Test
    void armedDroneTipsOverItsEdgeInsteadOfThroughTheFloor() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.CINEWHOOP}) {
            FlatWorld w = resting(build, 0.0, AXES[0]);
            w.input.mode = StabMode.ANGLE;
            w.input.throttle = 0.05;
            w.arm();
            w.input.roll = 0.6;
            double deepest = run(w, 2.0);
            String at = build.frame().key();
            assertTrue(w.sim.telemetry().armed, at);
            assertTrue(w.sim.telemetry().tiltDeg > 10.0, at + ": " + w.sim.telemetry().tiltDeg + "°");
            assertTrue(deepest < 0.003, at + ": " + deepest * 1000.0 + " mm im Boden");
            assertTrue(w.bottom() - w.groundY > 0.005, at + ": Schwerpunkt hebt sich nicht");
        }
    }

    @Test
    void groundOnlyActsWithinReach() {
        for (DroneBuild build : DerivationTest.PRESETS) {
            for (double clearance : new double[]{0.08, 0.4}) {
                FlatWorld known = Tests.hovering(build, Tests.quietTuning(), 0.0, StabMode.ANGLE, 0.8);
                FlatWorld blind = Tests.hovering(build, Tests.quietTuning(), 0.0, StabMode.ANGLE, 0.8);
                blind.solidKnown = false;
                double target = known.groundY - known.sim.params().hitboxFootOffset + clearance;
                AltitudeHold a = new AltitudeHold(target);
                AltitudeHold b = new AltitudeHold(target);
                for (int f = 0; f < (int) Math.round(3.0 * FPS); f++) {
                    a.apply(known, DT);
                    b.apply(blind, DT);
                    known.input.roll = 0.2 * Math.sin(f * 0.05);
                    blind.input.roll = known.input.roll;
                    known.frame(DT);
                    blind.frame(DT);
                    String at = build.frame().key() + " " + clearance + " m, Frame " + f;
                    assertEquals(Double.doubleToRawLongBits(known.sim.body.py), Double.doubleToRawLongBits(blind.sim.body.py), at);
                    assertEquals(Double.doubleToRawLongBits(known.sim.body.qx), Double.doubleToRawLongBits(blind.sim.body.qx), at);
                    assertEquals(Double.doubleToRawLongBits(known.sim.body.qz), Double.doubleToRawLongBits(blind.sim.body.qz), at);
                }
            }
        }
    }

    @Test
    void waterSurfaceCarriesNothing() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
        w.fluidY = 0.5;
        w.place(0.0, w.groundY - w.sim.params().hitboxFootOffset + 1.0, 0.0, 0.0);
        w.run(2.0, FPS);
        assertEquals(w.groundY, w.bottom(), 0.002);
        assertTrue(w.sim.telemetry().grounded);
    }
}
