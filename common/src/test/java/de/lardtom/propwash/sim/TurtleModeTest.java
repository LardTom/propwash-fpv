package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurtleModeTest {
    private static final double FPS = 250.0;

    record Flip(boolean armed, double seconds, double maxRise, double finalTilt, double firstRollRate, boolean stopped) {
    }

    static FlatWorld invertedOnGround(DroneBuild build) {
        FlatWorld w = FlatWorld.of(build);
        w.placeOnGround(0.0, 0.0, 0.0);
        Mode3dTest.invert(w);
        w.run(1.0, FPS);
        return w;
    }

    static Flip flip(DroneBuild build, double roll, double pitch, double yaw, double throttle, double maxSeconds) {
        FlatWorld w = invertedOnGround(build);
        double startY = w.sim.body.py;
        w.input.turtle = true;
        w.arm();
        boolean armed = w.sim.telemetry().armed;
        w.input.roll = roll;
        w.input.pitch = pitch;
        w.input.yaw = yaw;
        w.input.throttle = throttle;
        double maxRise = 0.0;
        double firstRate = Double.NaN;
        double t = 0.0;
        double upright = Double.NaN;
        while (t < maxSeconds) {
            w.frame(1.0 / FPS);
            t += 1.0 / FPS;
            maxRise = Math.max(maxRise, w.sim.body.py - startY);
            if (Double.isNaN(firstRate) && t >= 0.1) {
                firstRate = w.sim.body.wbz;
            }
            if (Double.isNaN(upright) && w.sim.telemetry().tiltDeg < 30.0) {
                upright = t;
            }
        }
        boolean stopped = w.sim.turtleMix.power == 0.0;
        return new Flip(armed, upright, maxRise, w.sim.telemetry().tiltDeg, firstRate, stopped);
    }

    @Test
    void mixSpinsOneSideBackwardsForRoll() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        TurtleMix mix = new TurtleMix();
        mix.update(p, 1.0, 0.0, 0.0, 0.0, 0.05, false);
        int spinning = 0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            assertTrue(mix.duty[i] <= 0.0, "nur rückwärts");
            if (mix.duty[i] < 0.0) {
                spinning++;
                assertTrue(p.mixRoll(i) < 0.0, "Rollen nach rechts dreht die Motoren, die sonst bremsen");
                assertEquals(-1.0, mix.duty[i], 1e-12);
            }
        }
        assertEquals(2, spinning);
    }

    @Test
    void mixUsesOneMotorOnTheDiagonalAndTwoForYaw() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        TurtleMix mix = new TurtleMix();
        mix.update(p, 0.7, 0.7, 0.0, 0.0, 0.05, false);
        assertEquals(1, count(mix));
        mix.update(p, 0.0, 0.0, -0.8, 0.0, 0.05, false);
        assertEquals(2, count(mix));
        mix.update(p, 0.9, 0.15, 0.0, 0.0, 0.05, false);
        assertEquals(2, count(mix), "fast reines Rollen bleibt Rollen");
    }

    @Test
    void stickDeflectionAndThrottleSetThePower() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        TurtleMix mix = new TurtleMix();
        mix.update(p, 0.05, 0.0, 0.0, 1.0, 0.05, false);
        assertEquals(0, count(mix), "ohne Richtung dreht nichts, auch mit Gas");
        mix.update(p, 0.3, 0.0, 0.0, 0.0, 0.05, false);
        double small = mix.power;
        assertEquals(TurtleMix.expo(0.3), small, 1e-12);
        mix.update(p, 0.3, 0.0, 0.0, 0.8, 0.05, false);
        assertEquals(0.8, mix.power, 1e-12, "Gas gibt die Leistung für die gewählte Richtung");
        mix.update(p, 1.0, 0.0, 0.0, 0.0, 0.05, false);
        assertEquals(1.0, mix.power, 1e-12);
    }

    private static int count(TurtleMix mix) {
        int n = 0;
        for (double d : mix.duty) {
            if (d < 0.0) {
                n++;
            }
        }
        return n;
    }

    @Test
    void invertedDroneArmsOnlyWithTurtle() {
        FlatWorld w = invertedOnGround(DroneBuild.FREESTYLE);
        w.arm();
        assertFalse(w.sim.telemetry().armed);
        assertTrue(ArmBlocker.TILTED.isSet(w.sim.telemetry().armBlockers));
        w.input.armSwitch = false;
        w.input.turtle = true;
        w.arm();
        assertTrue(w.sim.telemetry().armed, "Blocker " + w.sim.telemetry().armBlockers);
        assertTrue(w.sim.telemetry().turtle);
        assertTrue(w.sim.turtleArmed());
    }

    @Test
    void turtleIsRefusedInTheAir() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        w.groundY = -100.0;
        w.place(0.0, 10.0, 0.0, 0.0);
        w.run(0.3, FPS);
        w.input.turtle = true;
        w.arm();
        assertFalse(w.sim.telemetry().armed);
        assertTrue(ArmBlocker.TURTLE_AIRBORNE.isSet(w.sim.telemetry().armBlockers));
    }

    @Test
    void switchingTurtleOffDisarmsAndWantsTheArmSwitchAgain() {
        FlatWorld w = invertedOnGround(DroneBuild.FREESTYLE);
        w.input.turtle = true;
        w.arm();
        assertTrue(w.sim.telemetry().armed);
        w.input.turtle = false;
        w.run(0.02, FPS);
        assertFalse(w.sim.telemetry().armed);
        assertEquals(DisarmReason.TURTLE_OFF, w.sim.telemetry().lastDisarmReason);
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(w.sim.telemetry().armBlockers));
    }

    @Test
    void armedNormallyTheTurtleSwitchChangesNothingInFlight() {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        assertTrue(w.sim.telemetry().armed);
        w.input.turtle = true;
        w.run(0.5, FPS);
        assertTrue(w.sim.telemetry().armed);
        assertFalse(w.sim.telemetry().turtle);
    }

    @Test
    void freestyleFlipsOverWithRoll() {
        Flip f = flip(DroneBuild.FREESTYLE, 1.0, 0.0, 0.0, 0.0, 4.0);
        assertTrue(f.armed());
        assertTrue(f.seconds() < 2.0, "aufrecht nach " + f.seconds() + " s");
        assertTrue(f.finalTilt() < 20.0, "liegt am Ende flach: " + f.finalTilt());
        assertTrue(f.maxRise() < 0.5, "hebt nicht ab: " + f.maxRise());
        assertTrue(f.stopped(), "Motoren stoppen nach dem Umdrehen");
    }

    @Test
    void everyPresetFlipsBothWays() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER,
                DroneBuild.LONG_RANGE}) {
            for (double[] stick : new double[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Flip f = flip(build, stick[0], stick[1], 0.0, 0.0, 5.0);
                assertTrue(f.seconds() < 4.0, build.frame() + " " + stick[0] + "/" + stick[1] + ": " + f);
                assertTrue(f.finalTilt() < 25.0, build.frame() + " " + stick[0] + "/" + stick[1] + ": " + f);
            }
        }
    }

    @Test
    void rollRightTurnsLikeARollRightInTheCameraImage() {
        Flip f = flip(DroneBuild.FREESTYLE, 1.0, 0.0, 0.0, 0.0, 0.2);
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ACRO, 1.0);
        w.input.roll = 1.0;
        w.run(0.1, FPS);
        double normal = w.sim.body.wbz;
        assertTrue(Math.signum(normal) == Math.signum(f.firstRollRate()), "Rollrate Turtle " + f.firstRollRate() + ", normal " + normal);
    }

    @Test
    void smallStickBarelyLiftsAndCannotFlip() {
        Flip f = flip(DroneBuild.FREESTYLE, 0.12, 0.0, 0.0, 0.0, 2.0);
        assertTrue(Double.isNaN(f.seconds()), "wenig Ausschlag reicht nicht: " + f);
    }
}
