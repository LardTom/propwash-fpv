package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;
import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonFlightHooksTest {
    private static final double DT = 1.0 / 144.0;

    private static FlatWorld hover(long seed) {
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), seed));
        w.env.skyView = 1.0;
        w.place(0.0, 20.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        w.sim.nav.headingOffsetRad = 0.0;
        AltitudeHold hold = new AltitudeHold(20.0);
        for (int i = 0; i < 144 * 8; i++) {
            hold.apply(w, DT);
            w.frame(DT);
        }
        return w;
    }

    private static double[] state(DroneSim sim) {
        RigidBody b = sim.body;
        return new double[]{b.px, b.py, b.pz, b.vx, b.vy, b.vz, b.qw, b.qx, b.qy, b.qz, b.lx, b.ly, b.lz};
    }

    @Test
    void unusedHooksKeepTheFlightBitIdentical() {
        FlatWorld plain = hover(7L);
        FlatWorld hooked = hover(7L);
        hooked.sim.setAssist(null);
        hooked.sim.setForceProviders(List.of());
        for (int i = 0; i < 144 * 3; i++) {
            plain.input.roll = 0.3 * Math.sin(i * 0.05);
            hooked.input.roll = plain.input.roll;
            plain.input.actions = 0;
            hooked.input.actions = 5;
            plain.frame(DT);
            hooked.frame(DT);
        }
        double[] a = state(plain.sim);
        double[] b = state(hooked.sim);
        for (int i = 0; i < a.length; i++) {
            assertEquals(Double.doubleToLongBits(a[i]), Double.doubleToLongBits(b[i]), "component " + i);
        }
    }

    @Test
    void impulseChangesMomentumOnce() {
        FlatWorld w = hover(3L);
        double mass = w.sim.body.mass;
        double vx = w.sim.body.vx;
        assertTrue(w.sim.applyImpulse(0.6, 0.0, 0.0, 0.0, 0.0, 0.0));
        assertEquals(vx + 0.6 / mass, w.sim.body.vx, 1.0E-12);
        assertFalse(w.sim.applyImpulse(Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0));
        double before = Math.abs(w.sim.body.wbx) + Math.abs(w.sim.body.wby) + Math.abs(w.sim.body.wbz);
        assertTrue(w.sim.applyImpulse(0.0, 0.0, 0.3, 0.0, 0.1, 0.0));
        double after = Math.abs(w.sim.body.wbx) + Math.abs(w.sim.body.wby) + Math.abs(w.sim.body.wbz);
        assertTrue(after > before + 0.5, "Impuls über dem Schwerpunkt dreht nicht: " + before + " -> " + after);
    }

    @Test
    void frameRelativePushAcceleratesForwardAndEnds() {
        FlatWorld w = hover(4L);
        double heading = w.sim.body.headingRad();
        double fx = Math.sin(heading);
        double fz = Math.cos(heading);
        double v0 = w.sim.body.vx * fx + w.sim.body.vz * fz;
        assertTrue(w.sim.push(0.0, 0.0, -6.0, true, 0.0, 0.0, 0.0, 0.5));
        assertEquals(1, w.sim.activePushes());
        w.run(0.5, 144.0);
        double v1 = w.sim.body.vx * fx + w.sim.body.vz * fz;
        assertTrue(v1 - v0 > 1.5, "Schub nach vorn wirkt nicht: " + v0 + " -> " + v1);
        w.frame(DT);
        assertEquals(0, w.sim.activePushes());
        assertEquals(0, w.sim.telemetry().externalPushes);
    }

    @Test
    void forceProviderLiftsAndSeesTheState() {
        FlatWorld w = hover(5L);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Double> mass = new AtomicReference<>();
        w.sim.setForceProviders(List.of((state, sink) -> {
            calls.incrementAndGet();
            mass.set(state.massKg());
            sink.addForce(0.0, state.massKg() * state.gravity() * 0.6, 0.0);
        }));
        double y0 = w.sim.body.py;
        w.run(1.0, 144.0);
        assertTrue(calls.get() >= 90 && calls.get() <= 110, "Kräfte nicht im Nav-Takt: " + calls.get());
        assertEquals(w.sim.params().massKg, mass.get(), 1.0E-12);
        assertTrue(w.sim.body.py - y0 > 1.0, "Auftrieb wirkt nicht: " + (w.sim.body.py - y0));
        w.sim.setForceProviders(List.of());
        assertEquals(0, w.sim.forceProviderCount());
    }

    @Test
    void assistHoldsAnExternalPositionTarget() {
        FlatWorld w = hover(6L);
        double tx = w.sim.body.px + 6.0;
        double tz = w.sim.body.pz - 3.0;
        double ty = w.sim.body.py + 2.0;
        w.sim.setAssist((state, out) -> {
            out.holdPosition(tx, tz);
            out.holdAltitude(ty);
            out.maxSpeed(6.0);
            out.label("GOTO");
        });
        w.input.throttle = 0.5;
        w.run(20.0, 144.0);
        double error = Math.hypot(w.sim.body.px - tx, w.sim.body.pz - tz);
        assertTrue(error < 2.0, "Ziel nicht erreicht: " + error);
        assertTrue(Math.abs(w.sim.body.py - ty) < 1.5, "Höhe nicht erreicht: " + (w.sim.body.py - ty));
        assertEquals(HoldMode.POSHOLD, w.sim.telemetry().holdMode);
        assertEquals("GOTO", w.sim.telemetry().assistLabel);
        assertTrue(w.sim.telemetry().armed);
    }

    @Test
    void assistOrbitsWithVelocityAndHeading() {
        FlatWorld w = hover(8L);
        double cx = w.sim.body.px;
        double cz = w.sim.body.pz + 8.0;
        double radius = 8.0;
        double speed = 3.0;
        double altitude = w.sim.body.py;
        FlightAssist orbit = (state, out) -> {
            double dx = state.estimatedX() - cx;
            double dz = state.estimatedZ() - cz;
            if (!Double.isFinite(dx)) {
                return;
            }
            double r = Math.max(0.1, Math.hypot(dx, dz));
            double radial = 0.8 * (radius - r);
            double vx = (-dz / r) * speed + (dx / r) * radial;
            double vz = (dx / r) * speed + (dz / r) * radial;
            out.velocity(vx, vz);
            out.maxSpeed(5.0);
            out.holdAltitude(altitude);
            out.heading(Math.toDegrees(Math.atan2(-dx, dz)));
            out.label("ORBIT");
        };
        w.sim.setAssist(orbit);
        w.input.throttle = 0.5;
        w.run(15.0, 144.0);
        double radiusSum = 0.0;
        double headingError = 0.0;
        double travelled = 0.0;
        double lastX = w.sim.body.px;
        double lastZ = w.sim.body.pz;
        int n = 0;
        for (int i = 0; i < 144 * 10; i++) {
            w.frame(DT);
            radiusSum += Math.hypot(w.sim.body.px - cx, w.sim.body.pz - cz);
            double compass = AssistBridge.compass(w.sim.body.headingRad() * SimMath.DEG);
            double want = Math.toDegrees(Math.atan2(-(w.sim.body.px - cx), w.sim.body.pz - cz));
            headingError += Math.abs(AssistBridge.wrap180(want - compass));
            travelled += Math.hypot(w.sim.body.px - lastX, w.sim.body.pz - lastZ);
            lastX = w.sim.body.px;
            lastZ = w.sim.body.pz;
            n++;
        }
        assertTrue(Math.abs(radiusSum / n - radius) < 2.0, "Radius: " + radiusSum / n);
        assertTrue(headingError / n < 25.0, "Nase zeigt nicht zur Mitte: " + headingError / n);
        assertTrue(travelled > 15.0, "Kreist nicht: " + travelled);
    }

    @Test
    void assistYawRatePositiveTurnsClockwise() {
        FlatWorld w = hover(9L);
        double before = AssistBridge.compass(w.sim.body.headingRad() * SimMath.DEG);
        AtomicReference<Double> rate = new AtomicReference<>(0.0);
        w.sim.setAssist((state, out) -> {
            out.yawRate(45.0);
            rate.set(state.yawRateDps());
        });
        AltitudeHold hold = new AltitudeHold(w.sim.body.py);
        for (int i = 0; i < 144; i++) {
            hold.apply(w, DT);
            w.frame(DT);
        }
        double after = AssistBridge.compass(w.sim.body.headingRad() * SimMath.DEG);
        double turned = AssistBridge.wrap180(after - before);
        assertTrue(turned > 20.0 && turned < 70.0, "45 °/s im Uhrzeigersinn erwartet, gedreht: " + turned);
        assertTrue(rate.get() > 20.0, "Gierrate im FlightState falsch herum: " + rate.get());
    }

    @Test
    void assistSticksSmoothPilotInputAndSeeActions() {
        FlatWorld w = hover(10L);
        AtomicReference<Boolean> action2 = new AtomicReference<>(false);
        AtomicReference<StabilizationMode> pilotMode = new AtomicReference<>();
        double[] smooth = new double[1];
        w.sim.setAssist((state, out) -> {
            smooth[0] += 0.05 * (state.stickRoll() - smooth[0]);
            out.sticks(smooth[0], state.stickPitch(), state.stickYaw(), state.stickThrottle());
            out.mode(StabilizationMode.ANGLE);
            action2.set(state.action(2));
            pilotMode.set(state.pilotMode());
        });
        w.input.actions = 0b10;
        w.input.mode = StabMode.ACRO;
        w.input.roll = 1.0;
        AltitudeHold hold = new AltitudeHold(w.sim.body.py);
        hold.apply(w, DT);
        w.frame(DT);
        w.frame(DT);
        assertTrue(action2.get());
        assertEquals(StabilizationMode.ACRO, pilotMode.get());
        assertTrue(smooth[0] > 0.0 && smooth[0] < 0.3, "Glättung: " + smooth[0]);
        assertTrue(Math.abs(w.sim.telemetry().rollDeg) < 5.0, "Knüppel nicht geglättet: " + w.sim.telemetry().rollDeg);
    }

    @Test
    void failingAssistIsDroppedAndReported() {
        FlatWorld w = hover(11L);
        w.sim.setAssist((state, out) -> {
            throw new IllegalStateException("kaputt");
        });
        w.frame(DT);
        assertNull(w.sim.assist());
        RuntimeException failure = w.sim.consumeExtensionFailure();
        assertNotNull(failure);
        assertEquals("kaputt", failure.getMessage());
        assertTrue(w.sim.telemetry().armed);
    }

    @Test
    void obstacleDistancesAndReturnHomeReachTheAssist() {
        FlatWorld w = hover(12L);
        AtomicReference<Double> forward = new AtomicReference<>();
        w.sim.setAssist(new FlightAssist() {
            @Override
            public void update(FlightState state, AssistOutput out) {
                forward.set(state.obstacleDistance(SensorDirection.FORWARD));
                if (state.obstacleDistance(SensorDirection.FORWARD) < 3.0) {
                    out.requestReturnHome();
                }
            }

            @Override
            public double sensorRange() {
                return 20.0;
            }
        });
        assertEquals(20.0, w.sim.assistSensorRange());
        w.sim.setObstacleDistance(SensorDirection.FORWARD, 2.5);
        w.frame(DT);
        assertEquals(2.5, forward.get());
        assertTrue(w.sim.consumeReturnHomeRequest());
        assertFalse(w.sim.consumeReturnHomeRequest());
    }
}
