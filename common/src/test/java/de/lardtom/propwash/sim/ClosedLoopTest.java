package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosedLoopTest {
    static final DroneBuild[] PRESETS = DerivationTest.PRESETS;
    static final double STICK_500 = 0.8564;

    private static SimTuning infinite() {
        SimTuning t = Tests.quietTuning();
        t.infiniteBattery = true;
        return t;
    }

    @Test
    void hoverThrottleStickHoldsAltitudeOpenLoop() {
        for (DroneBuild b : PRESETS) {
            FlatWorld w = Tests.hovering(b, Tests.quietTuning(), 5.0, StabMode.ANGLE, 1.0);
            double maxVy = 0.0;
            double maxTilt = 0.0;
            double dt = 1.0 / 144.0;
            for (int k = 0; k < 576; k++) {
                w.input.throttle = w.sim.telemetry().hoverThrottleStick;
                w.frame(dt);
                maxVy = Math.max(maxVy, Math.abs(w.sim.body.vy));
                maxTilt = Math.max(maxTilt, w.sim.telemetry().tiltDeg);
            }
            assertTrue(maxVy <= 0.3, b.frame().name() + " vy " + maxVy);
            assertTrue(maxTilt <= 0.5, b.frame().name() + " tilt " + maxTilt);
        }
    }

    @Test
    void hoverThrottleMatchesCalibrationTable() {
        double[][] expected = {{47.8, 55.0}, {32.2, 36.4}, {20.6, 23.4}, {16.7, 19.2}, {22.8, 27.7}};
        double[] socs = {1.0, 0.5};
        for (int k = 0; k < PRESETS.length; k++) {
            for (int s = 0; s < 2; s++) {
                FlatWorld w = Tests.hovering(PRESETS[k], infinite(), 5.0, StabMode.ANGLE, socs[s]);
                AltitudeHold hold = new AltitudeHold(5.0);
                double dt = 1.0 / 144.0;
                double sum = 0.0;
                int n = 0;
                for (int f = 0; f < 1440; f++) {
                    hold.apply(w, dt);
                    w.frame(dt);
                    sum += w.input.throttle;
                    n++;
                }
                assertEquals(expected[k][s], 100.0 * sum / n, 1.5, PRESETS[k].frame().name() + " soc " + socs[s]);
            }
        }
    }

    private static double[] rateStep(DroneBuild b, SimTuning tuning, FlightControllerConfig cfg, double fps, int axis,
                                     double stick, double target, double window) {
        FlatWorld w = new FlatWorld(new DroneSim(b, cfg, tuning, 42L));
        w.place(0.0, 30.0, 0.0, 0.0);
        w.sim.setBatteryCharge(0.5);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(30.0);
        double dt = 1.0 / fps;
        for (int f = 0; f < (int) (3.0 * fps); f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        w.input.mode = StabMode.ACRO;
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        if (axis == 0) {
            w.input.roll = stick;
        } else if (axis == 1) {
            w.input.pitch = stick;
        } else {
            w.input.yaw = stick;
        }
        double t = 0.0;
        double t90 = Double.NaN;
        double peak = 0.0;
        double settle = 0.0;
        double last = 0.0;
        double minLate = Double.POSITIVE_INFINITY;
        double maxLate = Double.NEGATIVE_INFINITY;
        while (t < window - 1e-9) {
            w.frame(dt);
            t += dt;
            double r = axis == 0 ? Tests.rollRateDps(w.sim) : (axis == 1 ? Tests.pitchRateDps(w.sim) : Tests.yawRateDps(w.sim));
            if (Double.isNaN(t90) && r >= 0.9 * target) {
                t90 = t;
            }
            peak = Math.max(peak, r);
            if (Math.abs(r - target) > 0.05 * target) {
                settle = t;
            }
            if (t > window - 0.2) {
                minLate = Math.min(minLate, r);
                maxLate = Math.max(maxLate, r);
            }
            last = r;
        }
        return new double[]{t90, peak / target - 1.0, Math.abs(last - target) / target, settle, (maxLate - minLate) / target};
    }

    @Test
    void rollAndPitchRateResponse() {
        double[] t144 = {111, 76, 49, 56, 76};
        double[] t60 = {117, 83, 50, 50, 83};
        for (int k = 0; k < PRESETS.length; k++) {
            for (int axis = 0; axis < 2; axis++) {
                for (int f = 0; f < 2; f++) {
                    double fps = f == 0 ? 144.0 : 60.0;
                    double table = f == 0 ? t144[k] : t60[k];
                    double[] r = rateStep(PRESETS[k], infinite(), new FlightControllerConfig(), fps, axis, STICK_500, 500.0, 0.3);
                    String n = PRESETS[k].frame().name() + " axis " + axis + " fps " + fps;
                    assertTrue(r[0] * 1000.0 <= 1.25 * table + 5.0, n + " t90 " + r[0]);
                    assertTrue(r[1] <= 0.08, n + " overshoot " + r[1]);
                    assertTrue(r[2] < 0.02, n + " residual " + r[2]);
                }
            }
        }
    }

    @Test
    void yawRateResponse() {
        double stick = RateCurves.stickFor(RatesType.ACTUAL, new RateCurve(), 300.0);
        assertEquals(300.0, RateCurves.rateDps(RatesType.ACTUAL, new RateCurve(), stick), 1e-6);
        assertEquals(STICK_500, RateCurves.stickFor(RatesType.ACTUAL, new RateCurve(), 500.0), 1e-4);
        for (DroneBuild b : PRESETS) {
            for (double fps : new double[]{144.0, 60.0}) {
                double[] r = rateStep(b, infinite(), new FlightControllerConfig(), fps, 2, stick, 300.0, 0.5);
                String n = b.frame().name() + " fps " + fps;
                assertTrue(r[0] <= 0.150, n + " t90 " + r[0]);
                assertTrue(r[1] <= 0.10, n + " overshoot " + r[1]);
                assertTrue(r[3] <= 0.250, n + " settle " + r[3]);
            }
        }
    }

    private static FlatWorld angleHover(DroneBuild b, SimTuning t, double fps) {
        FlatWorld w = new FlatWorld(new DroneSim(b, new FlightControllerConfig(), t, 42L));
        w.place(0.0, 30.0, 0.0, 0.0);
        w.sim.setBatteryCharge(0.5);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(30.0);
        double dt = 1.0 / fps;
        for (int f = 0; f < (int) (3.0 * fps); f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        return w;
    }

    private static double[] angleStep(DroneBuild b, double wind) {
        FlatWorld w = angleHover(b, infinite(), 144.0);
        AltitudeHold hold = new AltitudeHold(30.0);
        w.env.windX = wind;
        double dt = 1.0 / 144.0;
        w.input.roll = StickMath.angleToStick(30.0, w.sim.config().pid);
        double t = 0.0;
        double t90 = Double.NaN;
        double peak = 0.0;
        while (t < 1.5 - 1e-9) {
            hold.apply(w, dt);
            w.frame(dt);
            t += dt;
            double roll = Tests.rollDeg(w.sim);
            if (Double.isNaN(t90) && roll >= 27.0) {
                t90 = t;
            }
            peak = Math.max(peak, roll);
        }
        return new double[]{t90, peak - 30.0, Math.abs(Tests.rollDeg(w.sim) - 30.0)};
    }

    @Test
    void angleModeStepResponse() {
        double[] table = {208, 167, 188, 181, 174};
        for (int k = 0; k < PRESETS.length; k++) {
            double[] r = angleStep(PRESETS[k], 0.0);
            String n = PRESETS[k].frame().name();
            assertTrue(r[0] * 1000.0 <= 1.3 * table[k], n + " t90 " + r[0]);
            assertTrue(r[1] <= 1.0, n + " overshoot " + r[1]);
            assertTrue(r[2] <= 0.3, n + " error " + r[2]);
            double[] windy = angleStep(PRESETS[k], 3.0);
            assertTrue(windy[2] <= 1.0, n + " wind error " + windy[2]);
        }
    }

    @Test
    void angleModeTracksSine() {
        for (DroneBuild b : PRESETS) {
            for (double fps : new double[]{144.0, 60.0}) {
                FlatWorld w = angleHover(b, infinite(), fps);
                AltitudeHold hold = new AltitudeHold(30.0);
                double dt = 1.0 / fps;
                double t = 0.0;
                double sum = 0.0;
                int n = 0;
                while (t < 5.0) {
                    w.input.roll = (20.0 / 60.0) * Math.sin(2.0 * Math.PI * 0.5 * t);
                    hold.apply(w, dt);
                    w.frame(dt);
                    t += dt;
                    if (t >= 1.0) {
                        double e = Tests.rollDeg(w.sim) - 20.0 * Math.sin(2.0 * Math.PI * 0.5 * t);
                        sum += e * e;
                        n++;
                    }
                }
                double rms = Math.sqrt(sum / n);
                assertTrue(rms <= (fps > 100 ? 1.0 : 1.5), b.frame().name() + " fps " + fps + " rms " + rms);
            }
        }
    }

    @Test
    void angleModeRecoversFromInverted() {
        for (DroneBuild b : PRESETS) {
            FlatWorld w = angleHover(b, infinite(), 144.0);
            double dt = 1.0 / 144.0;
            w.input.mode = StabMode.ACRO;
            w.input.throttle = w.sim.telemetry().hoverThrottleStick;
            w.input.roll = 0.5;
            int guard = 0;
            while (w.sim.telemetry().tiltDeg < 170.0 && guard++ < 1000) {
                w.frame(dt);
            }
            w.input.roll = 0.0;
            w.input.mode = StabMode.ANGLE;
            w.run(1.0, 144.0);
            assertTrue(w.sim.telemetry().tiltDeg < 10.0, b.frame().name() + " tilt " + w.sim.telemetry().tiltDeg);
            assertTrue(Double.isFinite(w.sim.body.qw + w.sim.body.vx));
        }
    }

    private static double unwrap(double previous, double current) {
        double d = current - previous;
        while (d > 180.0) {
            d -= 360.0;
        }
        while (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    @Test
    void angleModeHeadingHoldAndCoordinatedYaw() {
        for (DroneBuild b : PRESETS) {
            FlatWorld w = angleHover(b, infinite(), 144.0);
            AltitudeHold hold = new AltitudeHold(30.0);
            hold.integralLimit = 1.0;
            double dt = 1.0 / 144.0;
            double startHeading = w.sim.telemetry().headingDeg;
            w.input.pitch = StickMath.angleToStick(30.0, w.sim.config().pid);
            double drift = 0.0;
            double prev = startHeading;
            double maxDrift = 0.0;
            for (int f = 0; f < 1440; f++) {
                hold.apply(w, dt);
                w.frame(dt);
                double h = w.sim.telemetry().headingDeg;
                drift += unwrap(prev, h);
                prev = h;
                maxDrift = Math.max(maxDrift, Math.abs(drift));
            }
            assertTrue(maxDrift <= 2.0, b.frame().name() + " drift " + maxDrift);
            w.input.pitch = 0.0;
            w.input.roll = StickMath.angleToStick(30.0, w.sim.config().pid);
            w.input.yaw = StickMath.rateToStick(90.0, w.sim.config().rates, Axis.YAW);
            for (int f = 0; f < 144; f++) {
                hold.apply(w, dt);
                w.frame(dt);
            }
            prev = w.sim.telemetry().headingDeg;
            double turned = 0.0;
            for (int f = 0; f < 144; f++) {
                hold.apply(w, dt);
                w.frame(dt);
                double h = w.sim.telemetry().headingDeg;
                turned += unwrap(prev, h);
                prev = h;
            }
            assertEquals(-90.0, turned, 4.5, b.frame().name() + " turn rate");
        }
    }

    @Test
    void horizonModeLevelsAndStillFlips() {
        for (DroneBuild b : PRESETS) {
            FlatWorld w = angleHover(b, infinite(), 144.0);
            AltitudeHold hold = new AltitudeHold(30.0);
            double dt = 1.0 / 144.0;
            w.input.roll = 0.5;
            for (int f = 0; f < 216; f++) {
                hold.apply(w, dt);
                w.frame(dt);
            }
            assertEquals(30.0, w.sim.telemetry().tiltDeg, 1.0);
            w.input.mode = StabMode.HORIZON;
            w.input.roll = 0.0;
            for (int f = 0; f < 144; f++) {
                hold.apply(w, dt);
                w.frame(dt);
            }
            assertTrue(w.sim.telemetry().tiltDeg < 3.0, b.frame().name() + " tilt " + w.sim.telemetry().tiltDeg);
            w.input.roll = 1.0;
            double angle = 0.0;
            for (int f = 0; f < 144; f++) {
                w.frame(dt);
                angle += Tests.rollRateDps(w.sim) * dt;
            }
            assertTrue(angle > 360.0, b.frame().name() + " rolled " + angle);
        }
    }

    @Test
    void fullStickFlipTime() {
        for (DroneBuild b : PRESETS) {
            FlatWorld w = angleHover(b, infinite(), 144.0);
            double dt = 1.0 / 144.0;
            w.input.mode = StabMode.ACRO;
            w.input.throttle = w.sim.telemetry().hoverThrottleStick;
            w.input.roll = 1.0;
            double angle = 0.0;
            double t = 0.0;
            while (angle < 360.0 && t < 1.0) {
                w.frame(dt);
                t += dt;
                angle += Tests.rollRateDps(w.sim) * dt;
            }
            assertTrue(t <= 0.620, b.frame().name() + " flip " + t);
        }
    }

    private static double[] airmodeRun(DroneBuild b, boolean airmode) {
        FlightControllerConfig cfg = new FlightControllerConfig();
        cfg.pid.airmode = airmode;
        FlatWorld w = new FlatWorld(new DroneSim(b, cfg, infinite(), 42L));
        w.place(0.0, 30.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(30.0);
        double dt = 1.0 / 144.0;
        for (int f = 0; f < 432; f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        w.input.mode = StabMode.ACRO;
        w.input.throttle = 0.0;
        w.input.roll = RateCurves.stickFor(RatesType.ACTUAL, w.sim.config().rates.roll, 300.0);
        double minDuty = 1.0;
        double t = 0.0;
        while (t < 0.150 - 1e-9) {
            w.frame(dt);
            t += dt;
        }
        for (int i = 0; i < 4; i++) {
            minDuty = Math.min(minDuty, w.sim.telemetry().motorDuty[i]);
        }
        return new double[]{Tests.rollRateDps(w.sim), w.sim.telemetry().throttleOut, minDuty};
    }

    @Test
    void airmodeKeepsAuthorityAtZeroThrottle() {
        for (DroneBuild b : PRESETS) {
            double[] on = airmodeRun(b, true);
            double required = b == DroneBuild.WHOOP ? 200.0 : 270.0;
            assertTrue(on[0] >= required, b.frame().name() + " rate " + on[0]);
            assertTrue(on[1] > 0.0);
            double[] off = airmodeRun(b, false);
            assertEquals(0.0, off[1], 1e-9);
            assertEquals(0.055, off[2], 1e-9);
        }
    }

    @Test
    void noItermWindupOnTheGround() {
        for (DroneBuild b : PRESETS) {
            FlatWorld w = FlatWorld.of(b);
            w.placeOnGround(0.0, 0.0, 0.0);
            w.run(0.5, 144.0);
            w.arm();
            double x0 = w.x;
            double y0 = w.y;
            double z0 = w.z;
            w.input.throttle = 0.1;
            w.run(5.0, 144.0);
            Telemetry t = w.sim.telemetry();
            assertTrue(t.armed);
            assertFalse(t.airmodeActive);
            for (int a = 0; a < 3; a++) {
                assertEquals(0.0, t.iTerm[a], 0.0);
            }
            double moved = Math.sqrt((w.x - x0) * (w.x - x0) + (w.y - y0) * (w.y - y0) + (w.z - z0) * (w.z - z0));
            assertTrue(moved < 0.01, b.frame().name() + " moved " + moved);
        }
    }

    private static double dynamicSpeed(DroneBuild b, double tiltDeg) {
        FlightControllerConfig cfg = new FlightControllerConfig();
        cfg.pid.angleLimitDeg = 85.0;
        FlatWorld w = new FlatWorld(new DroneSim(b, cfg, infinite(), 42L));
        w.place(0.0, 100.0, 0.0, 0.0);
        w.sim.setBatteryCharge(BuildAnalysis.TOP_SPEED_SOC);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(100.0);
        hold.integralLimit = 1.0;
        hold.kd = 0.12;
        hold.ki = 0.1;
        double dt = 1.0 / 144.0;
        for (int f = 0; f < 288; f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        w.input.pitch = StickMath.angleToStick(tiltDeg, w.sim.config().pid);
        hold.feedForwardScale = 1.0 / Math.sqrt(Math.cos(Math.toRadians(tiltDeg)));
        double sum = 0.0;
        int n = 0;
        for (int f = 0; f < 15 * 144; f++) {
            hold.apply(w, dt);
            w.frame(dt);
            if (f >= 12 * 144) {
                sum += Math.hypot(w.sim.body.vx, w.sim.body.vz);
                n++;
                assertTrue(Math.abs(w.sim.body.vy) < 1.0, b.frame().name() + " vy " + w.sim.body.vy);
            }
        }
        assertEquals(tiltDeg, w.sim.telemetry().tiltDeg, 0.5);
        return sum / n;
    }

    @Test
    void dynamicTopSpeedMatchesStationarySolution() {
        for (DroneBuild b : PRESETS) {
            AirframeSpec p = AirframeSpec.of(b);
            double acroTilt = Math.toDegrees(OperatingPoints.topSpeedTilt(p, BuildAnalysis.TOP_SPEED_SOC));
            double[] tilts = {Math.min(60.0, acroTilt - 2.0), acroTilt - 2.0};
            for (double tilt : tilts) {
                double expected = OperatingPoints.speedAtTilt(p, Math.toRadians(tilt));
                double actual = dynamicSpeed(b, tilt);
                assertEquals(expected, actual, 0.05 * expected, b.frame().name() + " tilt " + tilt);
            }
        }
    }

    @Test
    void antiWindupPreventsBounceBack() {
        for (DroneBuild b : PRESETS) {
            FlatWorld w = angleHover(b, infinite(), 144.0);
            double dt = 1.0 / 144.0;
            w.input.mode = StabMode.ACRO;
            w.input.throttle = w.sim.telemetry().hoverThrottleStick;
            w.input.roll = 1.0;
            w.run(1.0, 144.0);
            double before = Tests.rollRateDps(w.sim);
            w.input.roll = 0.0;
            double reverse = 0.0;
            for (int f = 0; f < 72; f++) {
                w.frame(dt);
                reverse = Math.max(reverse, -Tests.rollRateDps(w.sim));
            }
            assertTrue(reverse <= 0.10 * before, b.frame().name() + " reverse " + reverse + " of " + before);
        }
    }

    private static FlatWorld groundReady(SimTuning tuning) {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE, tuning);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.run(0.1, 144.0);
        return w;
    }

    private static int blockedMask(FlatWorld w) {
        EventQueue q = w.sim.events();
        for (int e = 0; e < q.size(); e++) {
            if (q.type(e) == SimEventType.ARM_BLOCKED) {
                return (int) q.value(e);
            }
        }
        return -1;
    }

    private static void flipSwitchOn(FlatWorld w) {
        w.input.armSwitch = false;
        w.frame(0L);
        w.input.armSwitch = true;
        w.frame(0L);
    }

    @Test
    void armingBlockersFailsafeAndCooldown() {
        FlatWorld w = groundReady(Tests.quietTuning());
        w.input.throttle = 0.2;
        flipSwitchOn(w);
        assertFalse(w.sim.telemetry().armed);
        assertEquals(ArmBlocker.THROTTLE_NOT_LOW.bit(), blockedMask(w));
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(w.sim.telemetry().armBlockers));
        w.input.throttle = 0.0;
        w.input.armSwitch = false;
        w.frame(0L);
        assertEquals(0, w.sim.telemetry().armBlockers);

        w.sim.body.setUpright(0.0);
        w.sim.body.qw = Math.cos(Math.toRadians(15.0));
        w.sim.body.qz = Math.sin(Math.toRadians(15.0));
        w.sim.body.updateDerived();
        flipSwitchOn(w);
        assertEquals(ArmBlocker.TILTED.bit(), blockedMask(w));
        w.sim.body.setUpright(0.0);

        FlatWorld empty = groundReady(Tests.quietTuning());
        empty.sim.setBatteryCharge(0.02);
        flipSwitchOn(empty);
        assertEquals(ArmBlocker.PACK_DEPLETED.bit(), blockedMask(empty));

        FlatWorld invalid = new FlatWorld(new DroneSim(new DroneBuild(de.lardtom.propwash.build.FrameType.FREESTYLE_5,
                de.lardtom.propwash.build.StackType.AIO_20, de.lardtom.propwash.build.MotorType.M2207_1750,
                de.lardtom.propwash.build.PropType.P51_3B, de.lardtom.propwash.build.VideoType.DIGITAL_HD,
                de.lardtom.propwash.build.BatteryType.B6S_1300), new FlightControllerConfig(), Tests.quietTuning(), 1L));
        invalid.placeOnGround(0.0, 0.0, 0.0);
        invalid.run(0.1, 144.0);
        flipSwitchOn(invalid);
        assertEquals(ArmBlocker.BUILD_INVALID.bit(), blockedMask(invalid));

        FlatWorld link = groundReady(Tests.quietTuning());
        link.input.linkOk = false;
        flipSwitchOn(link);
        assertEquals(ArmBlocker.FAILSAFE.bit(), blockedMask(link));

        FlatWorld fluid = groundReady(Tests.quietTuning());
        fluid.env.fluid = Fluid.WATER;
        flipSwitchOn(fluid);
        assertEquals(ArmBlocker.IN_FLUID.bit(), blockedMask(fluid));

        FlatWorld reset = groundReady(Tests.quietTuning());
        reset.input.armSwitch = true;
        reset.sim.reset(0.0, reset.groundY - reset.sim.params().hitboxFootOffset, 0.0, 0.0);
        reset.frame(0L);
        assertFalse(reset.sim.telemetry().armed);
        assertEquals(ArmBlocker.ARM_SWITCH_NOT_RESET.bit(), blockedMask(reset));
        assertEquals(ArmBlocker.ARM_SWITCH_NOT_RESET.bit(), reset.sim.telemetry().armBlockers);
        reset.frame(0L);
        assertFalse(reset.sim.telemetry().armed);
        flipSwitchOn(reset);
        assertTrue(reset.sim.telemetry().armed);

        FlatWorld crash = groundReady(Tests.quietTuning());
        crash.arm();
        crash.sim.forceDisarm(DisarmReason.FORCED);
        crash.input.armSwitch = false;
        crash.frame(0L);
        crash.arm();
        assertTrue(crash.sim.telemetry().armed);
        crash.sim.teleport(crash.x, crash.y + 0.05, crash.z, false);
        crash.y += 0.05;
        crash.sim.body.vy = -20.0;
        crash.run(1.0 / 144.0, 144.0);
        assertFalse(crash.sim.telemetry().armed);
        assertEquals(DisarmReason.CRASH, crash.sim.telemetry().lastDisarmReason);
        crash.frame(0L);
        int mask = crash.sim.telemetry().armBlockers;
        assertTrue(ArmBlocker.CRASH_COOLDOWN.isSet(mask));
        assertTrue(ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(mask));
        crash.input.armSwitch = false;
        crash.frame(0L);
        crash.input.armSwitch = true;
        crash.frame(0L);
        assertFalse(crash.sim.telemetry().armed);
        assertEquals(ArmBlocker.CRASH_COOLDOWN.bit(), blockedMask(crash));
        crash.input.armSwitch = false;
        crash.frame(0L);
        long cooldownLeft = crash.sim.arming.crashCooldownSubsteps;
        assertTrue(cooldownLeft > 0 && cooldownLeft <= 2000);
        for (long k = 0; k < cooldownLeft - 1; k++) {
            crash.frame(DroneSim.SUBSTEP_NANOS);
        }
        assertTrue(ArmBlocker.CRASH_COOLDOWN.isSet(crash.sim.telemetry().armBlockers));
        crash.frame(DroneSim.SUBSTEP_NANOS);
        crash.frame(0L);
        assertFalse(ArmBlocker.CRASH_COOLDOWN.isSet(crash.sim.telemetry().armBlockers));
        crash.input.armSwitch = true;
        crash.frame(0L);
        assertTrue(crash.sim.telemetry().armed);

        FlatWorld fs = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ACRO, 1.0);
        fs.input.linkOk = false;
        int stage1 = -1;
        int disarmAt = -1;
        for (int k = 1; k <= 4000; k++) {
            fs.frame(DroneSim.SUBSTEP_NANOS);
            Telemetry tm = fs.sim.telemetry();
            if (stage1 < 0 && tm.failsafeStage == 1) {
                stage1 = k;
                assertEquals(StabMode.ANGLE, tm.mode);
            }
            if (disarmAt < 0 && !tm.armed) {
                disarmAt = k;
            }
        }
        assertEquals(600, stage1, 1);
        assertEquals(3600, disarmAt, 1);
        assertEquals(DisarmReason.FAILSAFE, fs.sim.telemetry().lastDisarmReason);
        assertTrue(ArmBlocker.FAILSAFE.isSet(fs.sim.telemetry().armBlockers));
    }

    static double[] margins(AirframeSpec p, PidLoopRate loop, FilterSettings filters, AxisPid gains, double authority) {
        double kp = gains.p * PidAutoTune.P_SCALE * SimMath.DEG;
        double ki = gains.i * PidAutoTune.I_SCALE * SimMath.DEG;
        double kd = gains.d * PidAutoTune.D_SCALE * SimMath.DEG;
        double delay = 1.5 / loop.hz() + 0.5 / DroneSim.PHYSICS_HZ;
        double wg = 2.0 * Math.PI * filters.gyroLpfHz;
        double wd = 2.0 * Math.PI * filters.dtermLpf1Hz;
        double tau = p.motorTau;
        int points = 3000;
        double[] w = new double[points];
        double[] mag = new double[points];
        double[] phase = new double[points];
        for (int i = 0; i < points; i++) {
            double omega = 2.0 * Math.pow(10.0, 4.0 * i / (points - 1));
            double dRe = 1.0;
            double dIm = omega / wd;
            double den = dRe * dRe + dIm * dIm;
            double dTermRe = kd * omega * dIm / den;
            double dTermIm = kd * omega * dRe / den;
            double cRe = kp + dTermRe;
            double cIm = -ki / omega + dTermIm;
            double cMag = Math.hypot(cRe, cIm);
            double cPhase = Math.atan2(cIm, cRe);
            double gMag = authority / (omega * Math.hypot(1.0, omega * tau) * Math.hypot(1.0, omega / wg));
            double gPhase = -Math.PI / 2.0 - Math.atan(omega * tau) - omega * delay - Math.atan(omega / wg);
            w[i] = omega;
            mag[i] = cMag * gMag;
            phase[i] = Math.toDegrees(cPhase + gPhase);
        }
        int cross = -1;
        for (int i = 1; i < points; i++) {
            if (mag[i - 1] >= 1.0 && mag[i] < 1.0) {
                cross = i;
            }
        }
        if (cross < 0) {
            return new double[]{180.0, Double.POSITIVE_INFINITY, 0.0};
        }
        double f = (1.0 - mag[cross - 1]) / (mag[cross] - mag[cross - 1]);
        double pc = phase[cross - 1] + f * (phase[cross] - phase[cross - 1]);
        double wc = w[cross - 1] + f * (w[cross] - w[cross - 1]);
        double pm = 180.0 + pc;
        double gm = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points; i++) {
            if (w[i] > 0.5 * wc && phase[i] <= -180.0) {
                gm = -20.0 * Math.log10(mag[i]);
                break;
            }
        }
        return new double[]{pm, gm, wc};
    }

    @Test
    void derivedPidsHaveStabilityMarginsForEveryBuild() {
        for (PidLoopRate loop : PidLoopRate.values()) {
            FilterSettings filters = new FilterSettings();
            filters.pidLoop = loop;
            for (DroneBuild b : DerivationTest.buildableCombinations()) {
                if (BuildAnalysis.of(b).warnings().contains(BuildWarning.CANNOT_HOVER)) {
                    continue;
                }
                AirframeSpec p = AirframeSpec.of(b);
                PidProfile pid = PidAutoTune.derive(p, filters, new PidProfile());
                double[] roll = margins(p, loop, filters, pid.roll, p.authorityRoll);
                double[] pitch = margins(p, loop, filters, pid.pitch, p.authorityPitch);
                String n = b + " " + loop;
                assertTrue(roll[0] >= 50.0, n + " roll PM " + roll[0]);
                assertTrue(roll[1] >= 8.0, n + " roll GM " + roll[1]);
                assertTrue(pitch[0] >= 50.0, n + " pitch PM " + pitch[0]);
                assertTrue(pitch[1] >= 8.0, n + " pitch GM " + pitch[1]);
            }
        }
        AirframeSpec freestyle = AirframeSpec.of(DroneBuild.FREESTYLE);
        PidProfile pid = PidAutoTune.derive(freestyle, new FilterSettings(), new PidProfile());
        double[] m = margins(freestyle, PidLoopRate.HZ_2000, new FilterSettings(), pid.roll, freestyle.authorityRoll);
        assertEquals(63.0, m[0], 3.0);
        assertEquals(13.1, m[1], 1.5);
    }

    @Test
    void slowerPidLoopsStayStable() {
        for (PidLoopRate loop : new PidLoopRate[]{PidLoopRate.HZ_1000, PidLoopRate.HZ_500}) {
            for (DroneBuild b : PRESETS) {
                FlightControllerConfig cfg = new FlightControllerConfig();
                cfg.filters.pidLoop = loop;
                double[] r = rateStep(b, infinite(), cfg, 144.0, 0, STICK_500, 500.0, 0.6);
                String n = b.frame().name() + " " + loop;
                assertTrue(r[1] <= 0.12, n + " overshoot " + r[1]);
                assertTrue(r[4] < 0.05, n + " late ripple " + r[4]);
                assertTrue(r[2] < 0.03, n + " residual " + r[2]);
            }
        }
    }

    private static int eventIndex(EventQueue q, SimEventType type) {
        for (int e = 0; e < q.size(); e++) {
            if (q.type(e) == type) {
                return e;
            }
        }
        return -1;
    }

    @Test
    void batteryWarningsAndBrownout() {
        TestStand lr = new TestStand(DroneBuild.LONG_RANGE, Tests.quietTuning());
        lr.spinUp();
        lr.duty(1.0);
        double critical = -1.0;
        for (int k = 1; k <= 12000 && critical < 0.0; k++) {
            lr.substeps(1);
            if (eventIndex(lr.sim.events(), SimEventType.BATTERY_CRITICAL) >= 0) {
                critical = k * DroneSim.H;
            }
        }
        assertTrue(critical > 0.0 && critical <= 5.0, "critical after " + critical);
        assertEquals(BatteryState.CRITICAL, lr.sim.telemetry().batteryState);

        TestStand whoop = new TestStand(DroneBuild.WHOOP, Tests.quietTuning());
        whoop.sim.setBatteryCharge(0.06);
        whoop.duty(1.0);
        double brownoutSoc = Double.NaN;
        for (int k = 0; k < 400_000 && Double.isNaN(brownoutSoc); k++) {
            whoop.substeps(1);
            if (eventIndex(whoop.sim.events(), SimEventType.BROWNOUT) >= 0) {
                brownoutSoc = whoop.sim.power.soc;
            }
        }
        assertTrue(brownoutSoc > -0.10, "brownout soc " + brownoutSoc);
        assertEquals(DisarmReason.BROWNOUT, whoop.sim.telemetry().lastDisarmReason);
        whoop.substeps(0);
        whoop.sim.step(0L, whoop.input, whoop.env, whoop.result);
        assertTrue(ArmBlocker.REBOOTING.isSet(whoop.sim.telemetry().armBlockers));

        for (DroneBuild b : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.LONG_RANGE}) {
            TestStand stand = new TestStand(b, Tests.quietTuning());
            stand.sim.setBatteryCharge(0.06);
            stand.spinUp();
            stand.duty(1.0);
            boolean brownout = false;
            int guard = 0;
            while (stand.sim.power.soc > -0.05 && guard++ < 2_000_000) {
                stand.substeps(1);
                brownout |= eventIndex(stand.sim.events(), SimEventType.BROWNOUT) >= 0;
            }
            assertFalse(brownout, b.frame().name());
            assertTrue(stand.sim.telemetry().armed, b.frame().name());
        }
    }

    @Tag("slow")
    @Test
    void everyBuildableCombinationHoversWithDerivedPids() {
        for (DroneBuild b : DerivationTest.buildableCombinations()) {
            BuildAnalysis analysis = BuildAnalysis.of(b);
            if (analysis.warnings().contains(BuildWarning.CANNOT_HOVER)) {
                continue;
            }
            FlatWorld w = new FlatWorld(new DroneSim(b, new FlightControllerConfig(), infinite(), 42L));
            w.place(0.0, 30.0, 0.0, 0.0);
            w.input.mode = StabMode.ANGLE;
            w.arm();
            AltitudeHold hold = new AltitudeHold(30.0);
            double dt = 1.0 / 60.0;
            double worst = 0.0;
            for (int f = 0; f < 1200; f++) {
                hold.apply(w, dt);
                w.frame(dt);
                RigidBody body = w.sim.body;
                assertTrue(Double.isFinite(body.px + body.py + body.pz + body.qw + body.lx), b.toString());
                if (f >= 120) {
                    worst = Math.max(worst, SimMath.hypot3(body.wbx, body.wby, body.wbz));
                }
            }
            assertTrue(worst < 3.0, b + " rate " + worst);
            assertTrue(w.sim.telemetry().armed, b.toString());
        }
    }
}
