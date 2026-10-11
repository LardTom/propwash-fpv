package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropWashTest {
    private static SimTuning washTuning(boolean wash) {
        SimTuning t = Tests.quietTuning();
        t.propWash = wash;
        t.infiniteBattery = true;
        return t;
    }

    private static FlatWorld hoverAt(DroneBuild b, SimTuning t, double altitude) {
        FlatWorld w = new FlatWorld(new DroneSim(b, new FlightControllerConfig(), t, 42L));
        w.place(0.0, altitude, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(altitude);
        double dt = 1.0 / 144.0;
        for (int f = 0; f < 432; f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        return w;
    }

    private static double[] sinkRecovery(boolean wash) {
        FlatWorld w = hoverAt(DroneBuild.FREESTYLE, washTuning(wash), 60.0);
        w.input.mode = StabMode.ACRO;
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        w.sim.body.vy = -6.0;
        double dt = 1.0 / 144.0;
        double peak = 0.0;
        double sum = 0.0;
        double maxTilt = 0.0;
        boolean crash = false;
        for (int f = 0; f < 72; f++) {
            w.frame(dt);
            RigidBody b = w.sim.body;
            double rate = SimMath.hypot3(b.wbx, b.wby, b.wbz) * SimMath.DEG;
            sum += rate * rate;
            peak = Math.max(peak, w.sim.telemetry().propWash);
            maxTilt = Math.max(maxTilt, w.sim.telemetry().tiltDeg);
            crash |= w.sim.events().contains(SimEventType.CRASH);
        }
        return new double[]{peak, Math.sqrt(sum / 72.0), maxTilt, crash ? 1.0 : 0.0};
    }

    @Test
    void sinkingIntoOwnDownwashShakesTheQuad() {
        double[] wash = sinkRecovery(true);
        double[] clean = sinkRecovery(false);
        assertTrue(wash[0] >= 0.7, "peak " + wash[0]);
        assertTrue(wash[1] >= 3.0 * clean[1], "rms " + wash[1] + " vs " + clean[1]);
        assertTrue(wash[1] > 1.0, "rms " + wash[1]);
        assertTrue(wash[2] <= 30.0, "tilt " + wash[2]);
        assertEquals(0.0, wash[3]);
        assertEquals(0.0, clean[0], 1e-12);
    }

    @Test
    void forwardFlightBlowsTheWashAway() {
        SimTuning t = washTuning(true);
        t.wakeField = true;
        FlatWorld w = hoverAt(DroneBuild.FREESTYLE, t, 60.0);
        AirframeSpec p = w.sim.params();
        double tilt = Math.toDegrees(OperatingPoints.tiltForSpeed(p, 10.0, Math.toRadians(80.0)));
        AltitudeHold hold = new AltitudeHold(60.0);
        hold.kd = 0.12;
        hold.ki = 0.1;
        hold.integralLimit = 1.0;
        w.input.pitch = StickMath.angleToStick(tilt, w.sim.config().pid);
        double dt = 1.0 / 144.0;
        double maxWash = 0.0;
        double maxWake = 0.0;
        for (int f = 0; f < 10 * 144; f++) {
            hold.apply(w, dt);
            w.frame(dt);
            if (f >= 3 * 144) {
                maxWash = Math.max(maxWash, w.sim.telemetry().propWash);
                maxWake = Math.max(maxWake, w.sim.telemetry().propWashWake);
            }
        }
        assertEquals(10.0, Math.hypot(w.sim.body.vx, w.sim.body.vz), 1.0);
        assertTrue(maxWash <= 0.1, "wash " + maxWash);
        assertTrue(maxWake <= 0.1, "wake " + maxWake);
    }

    @Test
    void hoverWithWashAndGyroNoiseStaysSteady() {
        for (DroneBuild b : DerivationTest.PRESETS) {
            SimTuning t = new SimTuning();
            t.infiniteBattery = true;
            FlatWorld w = hoverAt(b, t, 20.0);
            AltitudeHold hold = new AltitudeHold(20.0);
            double dt = 1.0 / 144.0;
            double sum = 0.0;
            int saturated = 0;
            int n = 0;
            for (int f = 0; f < 1440; f++) {
                hold.apply(w, dt);
                w.frame(dt);
                double e = w.sim.telemetry().attitudeErrorDeg;
                sum += e * e;
                if (w.sim.telemetry().motorSaturated) {
                    saturated++;
                }
                n++;
            }
            double rms = Math.sqrt(sum / n);
            assertTrue(rms < 1.0, b.frame().name() + " rms " + rms);
            assertTrue(saturated < 0.05 * n, b.frame().name() + " saturated " + saturated);
        }
    }

    @Test
    void wakeColumnIsRememberedAndDecays() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        WakeField wake = new WakeField();
        wake.configure(DroneSim.SLOW_DT);
        wake.reset();
        double thrust = 2.0 * p.massKg * ModelConstants.G;
        double y = 0.0;
        double climb = 3.0;
        for (int tick = 0; tick < 375; tick++) {
            if (tick % 10 == 0) {
                wake.emit(true, p, ModelConstants.RHO0, 0.0, y, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, thrust, climb);
            }
            wake.advance(0.0, 0.0, 0.0);
            y += climb * DroneSim.SLOW_DT;
        }
        assertTrue(wake.aliveCount() > 20);
        double best = 0.0;
        for (double qy = y - 15.0; qy < y; qy += 0.05) {
            double w = wake.query(0.0, qy, 0.0, 0.0, -20.0, 0.0, 0.0, 1.0, 0.0, p.hoverInducedVelocity);
            best = Math.max(best, w);
        }
        assertTrue(best >= 0.5, "column weight " + best);
        double side = wake.query(5.0, y - 3.0, 0.0, 0.0, -20.0, 0.0, 0.0, 1.0, 0.0, p.hoverInducedVelocity);
        assertEquals(0.0, side, 1e-12);
        double againstThrust = 0.0;
        for (double qy = y - 15.0; qy < y; qy += 0.05) {
            againstThrust = Math.max(againstThrust, wake.query(0.0, qy, 0.0, 0.0, 20.0, 0.0, 0.0, 1.0, 0.0, p.hoverInducedVelocity));
        }
        assertTrue(againstThrust <= 0.25 * best + 1e-9);
        for (int tick = 0; tick < 5 * 250; tick++) {
            if (tick % 10 == 0) {
                wake.emit(true, p, ModelConstants.RHO0, 0.0, y, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.05 * thrust, 0.0);
            }
            wake.advance(0.0, 0.0, 0.0);
        }
        assertEquals(0, wake.aliveCount());
    }

    @Test
    void wakePacketsDecayAfterLandingAtIdle() {
        SimTuning t = new SimTuning();
        t.gyroNoiseDps = 0.0;
        t.gyroVibrationDps = 0.0;
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE, t);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.arm();
        w.input.throttle = 0.8 * w.sim.telemetry().hoverThrottleStick;
        w.run(1.5, 144.0);
        assertTrue(w.sim.wake.aliveCount() > 0);
        w.input.throttle = 0.0;
        w.run(5.0, 144.0);
        assertEquals(0, w.sim.wake.aliveCount());
        assertTrue(w.sim.telemetry().armed);
        assertFalse(w.sim.telemetry().propWashWake > 0.0);
    }
}
