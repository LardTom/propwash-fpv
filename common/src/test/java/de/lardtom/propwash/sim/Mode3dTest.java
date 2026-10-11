package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PropType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mode3dTest {
    private static final double DT = 1.0 / 250.0;

    static PropType symmetricProp() {
        PropDefinition standard = PropType.P51_3B.definition();
        return AddonParts.part(PropDefinition.builder(AddonParts.id("p51_3d"))
                .mass(standard.basics().massGrams())
                .size(standard.diameterMm(), 3.0, 3)
                .coefficients(standard.thrustCoefficient() * 0.85, standard.powerCoefficient() * 0.9)
                .reverseThrust(1.0)
                .build());
    }

    static void invert(FlatWorld w) {
        SimSnapshot s = new SimSnapshot();
        w.sim.copyStateTo(s);
        s.qw = 0.0;
        s.qx = 1.0;
        s.qy = 0.0;
        s.qz = 0.0;
        w.sim.copyStateFrom(s);
    }

    static FlatWorld armed3d(DroneBuild build, double altitude, boolean inverted) {
        FlatWorld w = FlatWorld.of(build);
        w.groundY = -100.0;
        w.place(0.0, altitude, 0.0, 0.0);
        if (inverted) {
            invert(w);
        }
        w.input.mode = StabMode.ACRO;
        w.input.mode3d = true;
        w.input.throttle = 0.5;
        w.arm();
        assertTrue(w.sim.telemetry().armed, "scharf im 3D-Modus, Blocker " + w.sim.telemetry().armBlockers);
        return w;
    }

    static double holdStick(FlatWorld w, double target, double[] state, boolean reverse) {
        RigidBody b = w.sim.body;
        double err = target - b.py;
        double up = Math.abs(b.r11);
        double raw = state[1] + 0.12 * err - 0.10 * b.vy + state[0];
        state[0] = SimMath.clamp(state[0] + 0.25 * err * DT, -0.6, 0.6);
        double magnitude = SimMath.clamp01(raw / Math.max(0.5, up));
        double band = FlightControllerConfig.DEADBAND_3D;
        return reverse ? 0.5 - band - magnitude * (0.5 - band) : 0.5 + band + magnitude * (0.5 - band);
    }

    @Test
    void throttleMapsAroundTheCentreWithDeadband() {
        FlightController fc = new FlightController();
        double band = 0.05;
        assertEquals(0.0, fc.magnitude3d(0.5, band, true), 1e-12);
        assertEquals(0.0, fc.magnitude3d(0.53, band, true), 1e-12);
        assertEquals(1, fc.direction3d);
        assertEquals(1.0, fc.magnitude3d(1.0, band, true), 1e-12);
        assertEquals(0.5, fc.magnitude3d(0.775, band, true), 1e-12);
        assertEquals(1, fc.direction3d);
        assertEquals(1.0, fc.magnitude3d(0.0, band, true), 1e-12);
        assertEquals(-1, fc.direction3d);
        assertEquals(FlightController.REVERSAL_ITERM_HOLD_S, fc.reversalHoldS, 1e-12);
        assertEquals(0.5, fc.magnitude3d(0.225, band, true), 1e-12);
        assertEquals(0.0, fc.magnitude3d(0.47, band, true), 1e-12);
        assertEquals(-1, fc.direction3d, "in der Totzone bleibt die letzte Richtung (Leerlauf rückwärts)");
        assertEquals(0.0, fc.magnitude3d(0.5, band, false), 1e-12);
        assertEquals(1, fc.direction3d, "unscharf in der Mitte gilt vorwärts");
        assertEquals(0.0, fc.magnitude3d(0.45, band, true), 1e-12);
        assertEquals(-1, fc.direction3d);
    }

    @Test
    void armingWantsCentredThrottleAndAcceptsInvertedDrones() {
        FlightControllerConfig cfg = new FlightControllerConfig();
        PilotInput in = new PilotInput();
        in.mode3d = true;
        in.throttle = 0.0;
        int low = Arming.conditions3d(in, cfg, 0.0, 1.0, true, false, false, Fluid.NONE, false);
        assertTrue(ArmBlocker.THROTTLE_NOT_CENTERED.isSet(low));
        in.throttle = 0.52;
        assertEquals(0, Arming.conditions3d(in, cfg, 0.0, 1.0, true, false, false, Fluid.NONE, false));
        assertEquals(0, Arming.conditions3d(in, cfg, 178.0, 1.0, true, false, false, Fluid.NONE, false));
        assertTrue(ArmBlocker.TILTED.isSet(Arming.conditions3d(in, cfg, 90.0, 1.0, true, false, false, Fluid.NONE, false)));
        in.throttle = 0.0;
        assertTrue(ArmBlocker.TILTED.isSet(Arming.conditions(in, cfg, 178.0, 1.0, true, false, false, Fluid.NONE, false)));
        assertFalse(ArmBlocker.THROTTLE_NOT_CENTERED.isSet(Arming.conditions(in, cfg, 0.0, 1.0, true, false, false, Fluid.NONE, false)));
    }

    @Test
    void invertedDroneArmsOnlyIn3d() {
        FlatWorld normal = FlatWorld.of(DroneBuild.FREESTYLE);
        normal.placeOnGround(0.0, 0.0, 0.0);
        invert(normal);
        normal.input.throttle = 0.0;
        normal.arm();
        assertFalse(normal.sim.telemetry().armed);
        assertTrue(ArmBlocker.TILTED.isSet(normal.sim.telemetry().armBlockers));
        FlatWorld threeD = FlatWorld.of(DroneBuild.FREESTYLE);
        threeD.placeOnGround(0.0, 0.0, 0.0);
        invert(threeD);
        threeD.input.mode3d = true;
        threeD.input.throttle = 0.0;
        threeD.arm();
        assertFalse(threeD.sim.telemetry().armed, "3D braucht Gas in der Mitte");
        assertTrue(ArmBlocker.THROTTLE_NOT_CENTERED.isSet(threeD.sim.telemetry().armBlockers));
        threeD.input.throttle = 0.5;
        threeD.arm();
        assertTrue(threeD.sim.telemetry().armed);
    }

    @Test
    void standardPropsGiveMuchLessReverseThrustThanSymmetric3dProps() {
        AirframeSpec standard = AirframeSpec.of(DroneBuild.FREESTYLE);
        AirframeSpec symmetric = AirframeSpec.of(DroneBuild.FREESTYLE.withProp(symmetricProp()));
        assertEquals(ModelConstants.REVERSE_THRUST_STANDARD, standard.reverseThrust, 1e-12);
        assertEquals(1.0, symmetric.reverseThrust, 1e-12);
        assertEquals(1.0, symmetric.reverseTorque, 1e-12);
        assertTrue(standard.reverseTorque > standard.reverseThrust && standard.reverseTorque < 1.0);
        double standardRatio = staticReverseRatio(DroneBuild.FREESTYLE);
        double symmetricRatio = staticReverseRatio(DroneBuild.FREESTYLE.withProp(symmetricProp()));
        assertTrue(standardRatio > 0.35 && standardRatio < 0.8, "Umkehrschub normaler Props " + standardRatio);
        assertTrue(symmetricRatio > 0.9 && symmetricRatio < 1.1, "Umkehrschub symmetrischer 3D-Props " + symmetricRatio);
    }

    static double staticReverseRatio(DroneBuild build) {
        FlatWorld w = armed3d(build, 20.0, false);
        w.sim.setPoseLocked(true);
        w.input.throttle = 1.0;
        w.run(0.6, 250.0);
        double forward = w.sim.telemetry().totalThrustN;
        assertTrue(forward > 0.0);
        w.input.throttle = 0.0;
        w.run(0.6, 250.0);
        Telemetry t = w.sim.telemetry();
        assertTrue(t.totalThrustN < 0.0, "Schub rückwärts " + t.totalThrustN);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            assertTrue(t.motorRpm[i] < 0.0, "Motor " + i + " dreht rückwärts: " + t.motorRpm[i]);
            assertTrue(t.motorThrustN[i] < 0.0);
        }
        return -t.totalThrustN / forward;
    }

    @Test
    void airTorqueAndYawReactionFlipWithTheSpinDirection() {
        FlatWorld w = armed3d(DroneBuild.FREESTYLE, 20.0, false);
        w.sim.setPoseLocked(true);
        w.input.throttle = 0.8;
        w.run(0.4, 250.0);
        double[] forward = w.sim.power.airTorque.clone();
        w.input.throttle = 0.2;
        w.run(0.4, 250.0);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double omega = w.sim.power.omega[i];
            assertTrue(omega < 0.0);
            assertTrue(forward[i] > 0.0);
            assertTrue(w.sim.power.airTorque[i] < 0.0, "Luftmoment kehrt mit der Drehrichtung um");
            assertEquals(Math.signum(omega), Math.signum(w.sim.power.reactionTorque[i]));
        }
    }

    @Test
    void reversingPassesThroughZeroWithinAShortTransition() {
        FlatWorld w = armed3d(DroneBuild.FREESTYLE, 20.0, false);
        w.sim.setPoseLocked(true);
        w.input.throttle = 0.75;
        w.run(0.5, 1000.0);
        double before = w.sim.power.omega[0];
        assertTrue(before > 0.0);
        w.input.throttle = 0.0;
        double maxStep = 0.0;
        double previous = before;
        double reversedAt = Double.NaN;
        for (int frame = 1; frame <= 600; frame++) {
            w.frame(1.0 / 1000.0);
            double omega = w.sim.power.omega[0];
            maxStep = Math.max(maxStep, Math.abs(omega - previous));
            previous = omega;
            if (Double.isNaN(reversedAt) && w.sim.power.omega[0] < 0.0 && w.sim.power.omega[1] < 0.0 && w.sim.power.omega[2] < 0.0
                    && w.sim.power.omega[3] < 0.0) {
                reversedAt = frame / 1000.0;
            }
        }
        assertTrue(reversedAt > 0.01 && reversedAt < 0.3, "Umkehr nach " + reversedAt + " s");
        assertTrue(maxStep < 0.25 * w.sim.params().omegaMax, "kein Sprung der Drehzahl: " + maxStep);
        assertTrue(w.sim.power.omega[0] < -0.3 * before);
    }

    @Test
    void invertedHoverWithReverseThrust() {
        FlatWorld w = armed3d(DroneBuild.FREESTYLE, 30.0, true);
        double[] state = {0.0, 0.35};
        for (int i = 0; i < 6 * 250; i++) {
            w.input.throttle = holdStick(w, 30.0, state, true);
            w.frame(DT);
        }
        Telemetry t = w.sim.telemetry();
        assertTrue(t.armed);
        assertTrue(t.mode3d);
        assertTrue(t.tiltDeg > 160.0, "bleibt kopfüber: " + t.tiltDeg);
        assertTrue(Math.abs(w.sim.body.py - 30.0) < 0.5, "hält die Höhe kopfüber: " + w.sim.body.py);
        assertTrue(t.throttleOut < 0.0, "Gas im OSD negativ: " + t.throttleOut);
        assertTrue(t.totalThrustN < 0.0);
    }

    @Test
    void stickDirectionsStayTheSameUpsideDown() {
        double[] upright = responses(false);
        double[] inverted = responses(true);
        assertTrue(inverted[0] > 0.6 * upright[0], "Gier-Rate kopfüber " + inverted[0] + " °/s, aufrecht " + upright[0]);
        assertTrue(inverted[1] > 0.6 * upright[1], "Roll-Rate kopfüber " + inverted[1] + " °/s, aufrecht " + upright[1]);
        assertTrue(upright[0] > 50.0 && upright[1] > 50.0);
    }

    static double[] responses(boolean inverted) {
        FlatWorld w = armed3d(DroneBuild.FREESTYLE, 30.0, inverted);
        double[] state = {0.0, inverted ? 0.35 : 0.25};
        for (int i = 0; i < 2 * 250; i++) {
            w.input.throttle = holdStick(w, 30.0, state, inverted);
            w.frame(DT);
        }
        w.input.yaw = 0.5;
        for (int i = 0; i < 50; i++) {
            w.input.throttle = holdStick(w, 30.0, state, inverted);
            w.frame(DT);
        }
        double yaw = Tests.yawRateDps(w.sim);
        w.input.yaw = 0.0;
        for (int i = 0; i < 100; i++) {
            w.input.throttle = holdStick(w, 30.0, state, inverted);
            w.frame(DT);
        }
        w.input.roll = 0.3;
        for (int i = 0; i < 25; i++) {
            w.input.throttle = holdStick(w, 30.0, state, inverted);
            w.frame(DT);
        }
        return new double[]{yaw, Tests.rollRateDps(w.sim)};
    }

    @Test
    void flipAndFlyUpsideDown() {
        FlatWorld w = armed3d(DroneBuild.FREESTYLE, 30.0, false);
        double[] state = {0.0, 0.25};
        for (int i = 0; i < 2 * 250; i++) {
            w.input.throttle = holdStick(w, 30.0, state, false);
            w.frame(DT);
        }
        w.input.throttle = 0.5;
        w.input.roll = 1.0;
        int guard = 0;
        while (w.sim.telemetry().tiltDeg < 165.0 && guard++ < 250) {
            w.frame(DT);
        }
        w.input.roll = 0.0;
        assertTrue(w.sim.telemetry().tiltDeg >= 165.0, "Rolle bis kopfüber");
        double afterFlip = w.sim.body.py;
        state[0] = 0.0;
        state[1] = 0.35;
        for (int i = 0; i < 3 * 250; i++) {
            w.input.throttle = holdStick(w, afterFlip, state, true);
            w.frame(DT);
        }
        Telemetry t = w.sim.telemetry();
        assertTrue(t.tiltDeg > 150.0, "fliegt kopfüber weiter: " + t.tiltDeg);
        assertTrue(w.sim.body.py > afterFlip - 2.0, "Höhe gehalten: " + w.sim.body.py + " nach " + afterFlip);
        w.input.pitch = 0.3;
        for (int i = 0; i < 20; i++) {
            w.input.throttle = holdStick(w, afterFlip, state, true);
            w.frame(DT);
        }
        w.input.pitch = 0.0;
        double vx0 = w.sim.body.vx;
        double vz0 = w.sim.body.vz;
        for (int i = 0; i < 250; i++) {
            w.input.throttle = holdStick(w, afterFlip, state, true);
            w.frame(DT);
        }
        double moved = Math.hypot(w.sim.body.vx - vx0, w.sim.body.vz - vz0);
        assertTrue(moved > 1.0, "kopfüber geneigt fliegt die Drohne waagerecht: " + moved);
    }

    @Test
    void punchDownIsFasterThanFreeFall() {
        FlatWorld w = armed3d(DroneBuild.FREESTYLE, 60.0, false);
        double[] state = {0.0, 0.25};
        for (int i = 0; i < 2 * 250; i++) {
            w.input.throttle = holdStick(w, 60.0, state, false);
            w.frame(DT);
        }
        double v0 = w.sim.body.vy;
        w.input.throttle = 0.0;
        w.run(0.4, 250.0);
        double punched = w.sim.body.vy - v0;
        FlatWorld cut = FlatWorld.of(DroneBuild.FREESTYLE);
        cut.groundY = -100.0;
        cut.place(0.0, 60.0, 0.0, 0.0);
        cut.input.mode = StabMode.ACRO;
        cut.input.throttle = 0.0;
        cut.arm();
        cut.run(0.4, 250.0);
        double free = cut.sim.body.vy;
        assertTrue(punched < 1.5 * free, "Umkehrschub nach unten " + punched + " m/s, Gas weg " + free + " m/s");
    }

    @Test
    void takeOffUpsideDownAfterACrash() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        w.placeOnGround(0.0, 0.0, 0.0);
        invert(w);
        w.run(0.5, 250.0);
        double start = w.y;
        w.input.mode = StabMode.ACRO;
        w.input.mode3d = true;
        w.input.throttle = 0.5;
        w.arm();
        assertTrue(w.sim.telemetry().armed);
        w.input.throttle = 0.15;
        w.run(1.2, 250.0);
        assertTrue(w.y > start + 1.0, "hebt kopfüber ab: " + start + " → " + w.y);
        assertTrue(w.sim.telemetry().tiltDeg > 150.0, "bleibt dabei kopfüber: " + w.sim.telemetry().tiltDeg);
    }

    @Test
    void switching3dOffInvertedRecoversInAngle() {
        FlatWorld w = armed3d(DroneBuild.FREESTYLE, 40.0, true);
        double[] state = {0.0, 0.35};
        for (int i = 0; i < 250; i++) {
            w.input.throttle = holdStick(w, 40.0, state, true);
            w.frame(DT);
        }
        w.input.mode3d = false;
        w.input.mode = StabMode.ANGLE;
        w.input.throttle = 0.5;
        w.run(2.0, 250.0);
        Telemetry t = w.sim.telemetry();
        assertFalse(t.mode3d);
        assertTrue(t.tiltDeg < 20.0, "nach dem Abschalten aufrecht: " + t.tiltDeg);
        assertTrue(t.throttleOut > 0.0);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            assertTrue(t.motorRpm[i] > 0.0, "alle Motoren wieder vorwärts");
        }
        assertTrue(t.verticalSpeed > 0.0 || w.sim.body.py > 35.0, "fängt sich: " + w.sim.body.py + " / " + t.verticalSpeed);
    }

    @Test
    void holdModesAndFailsafeFlyWithNormalThrottle() {
        FlatWorld w = armed3d(DroneBuild.FREESTYLE, 30.0, false);
        w.input.altHold = true;
        w.run(0.5, 250.0);
        assertFalse(w.sim.telemetry().mode3d, "Höhe halten fliegt ohne 3D");
        w.input.altHold = false;
        w.run(0.2, 250.0);
        assertTrue(w.sim.telemetry().mode3d);
    }

    @Test
    void disabled3dStaysOnTheNormalPath() {
        FlatWorld a = FlatWorld.of(DroneBuild.FREESTYLE);
        FlatWorld b = FlatWorld.of(DroneBuild.FREESTYLE);
        for (FlatWorld w : new FlatWorld[]{a, b}) {
            w.place(0.0, 10.0, 0.0, 0.0);
            w.input.mode = StabMode.ACRO;
            w.input.throttle = 0.0;
            w.arm();
        }
        b.sim.setConfig(withDeadband(0.15));
        for (int i = 0; i < 500; i++) {
            double stick = 0.3 + 0.3 * Math.sin(i * 0.02);
            a.input.throttle = stick;
            b.input.throttle = stick;
            a.input.roll = 0.2 * Math.sin(i * 0.05);
            b.input.roll = a.input.roll;
            a.frame(DT);
            b.frame(DT);
            assertFalse(a.sim.reversibleActive());
        }
        assertEquals(a.sim.body.py, b.sim.body.py);
        assertEquals(a.sim.body.qx, b.sim.body.qx);
        assertEquals(a.sim.power.omega[2], b.sim.power.omega[2]);
    }

    static FlightControllerConfig withDeadband(double band) {
        FlightControllerConfig c = new FlightControllerConfig();
        c.deadband3d = band;
        return c;
    }
}
