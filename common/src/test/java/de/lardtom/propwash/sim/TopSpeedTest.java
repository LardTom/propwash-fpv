package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopSpeedTest {
    static final DroneBuild[] PRESETS = DerivationTest.PRESETS;
    static final double FPS = 144.0;
    static final double ALTITUDE = 3000.0;
    static final double[][] LEVEL_KMH = {{30, 56}, {80, 100}, {135, 155}, {160, 185}, {100, 125}};
    static final double[] DIVE_CAP_KMH = {68, 115, 170, 195, 135};
    static final double[] DIVE_RATIO = {1.25, 1.2, 1.15, 1.15, 1.15};

    static SimTuning freshPack() {
        SimTuning t = Tests.quietTuning();
        t.infiniteBattery = true;
        t.batterySag = false;
        return t;
    }

    static double levelTopKmh(DroneBuild b) {
        FlightControllerConfig cfg = new FlightControllerConfig();
        cfg.pid.angleLimitDeg = 85.0;
        FlatWorld w = FlightCharacterization.hover(b, cfg, freshPack(), ALTITUDE, 1.0, FPS);
        double dt = 1.0 / FPS;
        double start = w.input.throttle;
        double base = 0.0;
        double t = 0.0;
        double sum = 0.0;
        int n = 0;
        while (t < 25.0) {
            RigidBody body = w.sim.body;
            double err = body.py - ALTITUDE;
            double e = body.vy - SimMath.clamp(-0.5 * err, -3.0, 3.0);
            base = SimMath.clamp(base + 0.8 * e * dt, 0.0, 84.0);
            double tilt = SimMath.clamp(base + 2.0 * e, 0.0, 84.0);
            w.input.throttle = start + (1.0 - start) * Math.min(1.0, t / 3.0);
            w.input.pitch = tilt / 85.0;
            w.frame(dt);
            t += dt;
            if (t > 22.0) {
                sum += Math.hypot(body.vx, body.vz);
                n++;
            }
        }
        return 3.6 * sum / n;
    }

    static double diveTopKmh(DroneBuild b, double throttle) {
        FlatWorld w = FlightCharacterization.hover(b, new FlightControllerConfig(), freshPack(), ALTITUDE, 1.0, FPS);
        RigidBody body = w.sim.body;
        body.qw = 0.0;
        body.qx = 1.0;
        body.qy = 0.0;
        body.qz = 0.0;
        body.vx = 0.0;
        body.vy = 0.0;
        body.vz = 0.0;
        body.lx = 0.0;
        body.ly = 0.0;
        body.lz = 0.0;
        body.updateDerived();
        w.input.mode = StabMode.ACRO;
        w.input.pitch = 0.0;
        w.input.roll = 0.0;
        w.input.yaw = 0.0;
        w.input.throttle = throttle;
        double max = 0.0;
        for (int f = 0; f < 25 * (int) FPS; f++) {
            w.frame(1.0 / FPS);
            max = Math.max(max, Math.sqrt(body.vx * body.vx + body.vy * body.vy + body.vz * body.vz));
        }
        return 3.6 * max;
    }

    @Test
    void levelTopSpeedWithFreshPackIsRealistic() {
        for (int k = 0; k < PRESETS.length; k++) {
            double v = levelTopKmh(PRESETS[k]);
            String n = PRESETS[k].frame().name();
            assertTrue(v >= LEVEL_KMH[k][0] && v <= LEVEL_KMH[k][1], n + " level " + v);
        }
    }

    @Test
    void fullThrottleDiveIsOnlySlightlyFasterThanLevelFlight() {
        for (int k = 0; k < PRESETS.length; k++) {
            DroneBuild b = PRESETS[k];
            String n = b.frame().name();
            double level = levelTopKmh(b);
            double dive = diveTopKmh(b, 1.0);
            assertTrue(dive >= level, n + " dive " + dive + " level " + level);
            assertTrue(dive <= DIVE_RATIO[k] * level, n + " dive " + dive + " level " + level);
            assertTrue(dive <= DIVE_CAP_KMH[k], n + " dive " + dive);
            double glide = diveTopKmh(b, 0.0);
            assertTrue(glide < 0.7 * dive, n + " idle dive " + glide);
        }
    }

    @Test
    void sagOffKeepsFullRestingVoltageButLoadSag() {
        for (DroneBuild b : PRESETS) {
            String n = b.frame().name();
            SimTuning off = Tests.quietTuning();
            off.batterySag = false;
            off.infiniteBattery = true;
            SimTuning on = Tests.quietTuning();
            on.infiniteBattery = true;
            TestStand low = new TestStand(b, off);
            low.sim.setBatteryCharge(0.3);
            TestStand full = new TestStand(b, on);
            full.sim.setBatteryCharge(1.0);
            AirframeSpec p = low.sim.params();
            double restingFull = p.chemistry.fullCellVoltage;
            for (TestStand s : new TestStand[]{low, full}) {
                s.spinUp();
                s.duty(1.0);
                s.substeps(2000);
            }
            Telemetry tl = low.sim.telemetry();
            Telemetry tf = full.sim.telemetry();
            assertEquals(restingFull, tl.cellVoltageResting, 1e-9, n);
            assertTrue(tl.packVoltage < p.cells * restingFull - 0.02 * p.cells, n + " load sag " + tl.packVoltage);
            assertEquals(tf.packVoltage, tl.packVoltage, 0.01 * tf.packVoltage, n);
        }
    }
}
