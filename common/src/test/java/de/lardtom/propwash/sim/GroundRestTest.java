package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundRestTest {
    private static final double[][] AXES = {{1.0, 0.0}, {0.0, 1.0}, {0.7071067811865476, 0.7071067811865476}, {-0.6, 0.8}};

    private static FlatWorld lying(DroneBuild build, double tiltDeg, double[] axis, boolean restFlat) {
        SimTuning tuning = Tests.quietTuning();
        tuning.restFlat = restFlat;
        FlatWorld w = FlatWorld.of(build, tuning);
        w.placeOnGround(0.0, 0.0, 0.0);
        SimSnapshot snapshot = new SimSnapshot();
        w.sim.copyStateTo(snapshot);
        double half = Math.toRadians(tiltDeg) / 2.0;
        snapshot.qw = Math.cos(half);
        snapshot.qx = Math.sin(half) * axis[0];
        snapshot.qy = 0.0;
        snapshot.qz = Math.sin(half) * axis[1];
        w.sim.copyStateFrom(snapshot);
        w.sim.groundedFrames = 2;
        return w;
    }

    private static double sinkMm(FlatWorld w) {
        AirframeSpec p = w.sim.params();
        RigidBody b = w.sim.body;
        double low = Double.POSITIVE_INFINITY;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            low = Math.min(low, b.r10 * p.hullX(k) + b.r11 * p.hullY(k) + b.r12 * p.hullZ(k));
        }
        return (w.groundY - (w.y + low)) * 1000.0;
    }

    @Test
    void gainLeavesLevelAloneAndGivesFullLeverBeyondTheEdge() {
        assertEquals(1.0, GroundRest.gain(0.0, 0.0), 0.0);
        assertEquals(1.0, GroundRest.gain(0.01, -0.01), 0.0);
        assertEquals(1.0, GroundRest.gain(GroundRest.LEVEL, 0.0), 0.0);
        assertEquals(1.0, GroundRest.gain(Double.NaN, 0.0), 0.0);
        double previous = 0.0;
        for (int i = 0; i <= 1000; i++) {
            double lean = i / 1000.0;
            double torque = lean * GroundRest.gain(lean, 0.0);
            assertTrue(torque >= previous - 1.0E-12, "fällt bei " + lean);
            assertTrue(torque - previous < 0.06, "Sprung bei " + lean);
            assertTrue(torque <= 1.0 + 1.0E-12, "zu stark bei " + lean);
            if (lean >= GroundRest.EDGE) {
                assertEquals(1.0, torque, 1.0E-12, "Hebel bei " + lean);
            }
            previous = torque;
        }
        assertEquals(GroundRest.gain(0.3, 0.0), GroundRest.gain(0.0, -0.3), 1.0E-15);
        assertEquals(GroundRest.gain(0.5, 0.0), GroundRest.gain(0.3, 0.4), 1.0E-12);
    }

    @Test
    void disarmedDroneFallsFlatAndSleepsOnItsFeet() {
        for (DroneBuild build : DerivationTest.PRESETS) {
            for (double[] axis : AXES) {
                for (double tilt : new double[]{4.0, 11.5, 25.0, 45.0, 70.0, 88.0}) {
                    FlatWorld w = lying(build, tilt, axis, true);
                    String at = build.frame().name() + " " + tilt + "° um " + axis[0] + "/" + axis[1];
                    w.run(1.2, 144.0);
                    assertTrue(w.sim.telemetry().tiltDeg < 1.5, at + ": nach 1,2 s noch " + w.sim.telemetry().tiltDeg + "°");
                    w.run(1.8, 144.0);
                    assertTrue(w.sim.telemetry().sleeping, at + ": schläft nicht");
                    assertTrue(w.sim.telemetry().tiltDeg < 1.2, at + ": liegt mit " + w.sim.telemetry().tiltDeg + "°");
                    assertTrue(sinkMm(w) < 5.0, at + ": " + sinkMm(w) + " mm im Boden");
                    assertEquals(w.groundY, w.bottom(), 0.002, at);
                }
            }
        }
    }

    @Test
    void droneOnItsBackSettlesFlatOnItsBack() {
        for (DroneBuild build : DerivationTest.PRESETS) {
            for (double[] axis : AXES) {
                for (double tilt : new double[]{100.0, 135.0, 168.5, 176.0}) {
                    FlatWorld w = lying(build, tilt, axis, true);
                    String at = build.frame().name() + " " + tilt + "° um " + axis[0] + "/" + axis[1];
                    w.run(3.0, 144.0);
                    assertTrue(w.sim.telemetry().sleeping, at + ": schläft nicht");
                    assertTrue(w.sim.telemetry().tiltDeg > 178.8, at + ": liegt mit " + w.sim.telemetry().tiltDeg + "°");
                }
            }
        }
    }

    @Test
    void withoutTheRuleTheOldRestingTiltRemains() {
        FlatWorld legacy = lying(DroneBuild.FREESTYLE, 25.0, AXES[1], false);
        legacy.run(6.0, 144.0);
        assertTrue(legacy.sim.telemetry().sleeping);
        assertTrue(legacy.sim.telemetry().tiltDeg > 8.0, "alt: " + legacy.sim.telemetry().tiltDeg + "°");
        assertTrue(sinkMm(legacy) > 10.0, "alt: " + sinkMm(legacy) + " mm");
    }

    @Test
    void armedDroneKeepsItsAttitudeOnTheGround() {
        for (boolean restFlat : new boolean[]{false, true}) {
            FlatWorld w = lying(DroneBuild.FREESTYLE, 0.0, AXES[0], restFlat);
            w.input.mode = StabMode.ANGLE;
            w.input.throttle = 0.05;
            w.arm();
            w.input.roll = 0.6;
            w.run(2.0, 144.0);
            assertTrue(w.sim.telemetry().armed);
            assertFalse(w.sim.telemetry().sleeping);
            assertTrue(w.sim.telemetry().tiltDeg > 20.0, "restFlat " + restFlat + ": " + w.sim.telemetry().tiltDeg + "°");
        }
    }

    @Test
    void levelDroneIsNotDisturbed() {
        FlatWorld plain = lying(DroneBuild.RACER, 0.8, AXES[2], false);
        FlatWorld rested = lying(DroneBuild.RACER, 0.8, AXES[2], true);
        for (int i = 0; i < 432; i++) {
            plain.frame(1.0 / 144.0);
            rested.frame(1.0 / 144.0);
            assertEquals(Double.doubleToRawLongBits(plain.sim.body.qx), Double.doubleToRawLongBits(rested.sim.body.qx), "Frame " + i);
            assertEquals(Double.doubleToRawLongBits(plain.sim.body.qz), Double.doubleToRawLongBits(rested.sim.body.qz), "Frame " + i);
            assertEquals(Double.doubleToRawLongBits(plain.y), Double.doubleToRawLongBits(rested.y), "Frame " + i);
        }
    }
}
