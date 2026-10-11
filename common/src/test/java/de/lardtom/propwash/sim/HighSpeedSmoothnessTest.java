package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.video.CameraShake;
import de.lardtom.propwash.video.WearShake;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HighSpeedSmoothnessTest {
    static final DroneBuild[] PRESETS = DerivationTest.PRESETS;
    static final double ALTITUDE = 3000.0;
    static final double FPS = 120.0;
    static final double FRAME_JITTER = 0.15;
    static final double SETTLE_SECONDS = 8.0;
    static final double END_SECONDS = 13.0;
    static final double CALM_WIND = 1.2;
    static final double CALM_GUSTS = 0.12;
    static final double[][] RUNS = {{70.0, 1.0, 0.0}, {60.0, 0.6, 0.0}, {130.0, 0.5, 0.0}, {70.0, 1.0, 0.4}};
    static final double SWING_SECONDS = 0.7;
    static final double[] MIN_SPEED = {12.0, 22.0, 34.0, 40.0, 27.0};
    static final double MAX_WOBBLE_RMS_DEG = 0.012;
    static final double MAX_WOBBLE_PEAK_DEG = 0.035;
    static final double MAX_RATE_RMS_DPS = 0.6;
    static final double MAX_SHAKE_RATIO = 1.15;
    static final double MAX_SHAKE_PEAK_DEG = 0.06;
    static final double MAX_GATE_BUFFET_SHARE = 0.3;

    record Flight(double speed, double rpmRatio, double wobbleRms, double wobbleMax, double rateRms, double shakeRms, double shakeMax,
                  double gateBuffet) {
    }

    static Flight fly(DroneBuild build, double[] run) {
        return fly(build, run[0], run[1], run[2]);
    }

    static Flight fly(DroneBuild build, double tiltDeg, double throttle, double swing) {
        return fly(build, PartWear.PRISTINE, tiltDeg, throttle, swing);
    }

    static Flight fly(DroneBuild build, PartWear wear, double tiltDeg, double throttle, double swing) {
        SimTuning tuning = new SimTuning();
        tuning.infiniteBattery = true;
        FlatWorld w = new FlatWorld(new DroneSim(build, new FlightControllerConfig(), tuning, 42L));
        w.place(0.0, ALTITUDE, 0.0, 0.0);
        w.sim.setBatteryCharge(1.0);
        w.sim.setWear(wear);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        w.env.windX = CALM_WIND;
        w.env.gustIntensity = CALM_GUSTS;
        AltitudeHold hold = new AltitudeHold(ALTITUDE);
        Random random = new Random(11L);
        RigidBody body = w.sim.body;
        AirframeSpec spec = w.sim.params();
        CameraShake shake = new CameraShake();
        double hoverRpm = spec.hoverOmega * 60.0 / (2.0 * Math.PI);
        int capacity = (int) ((END_SECONDS - SETTLE_SECONDS) * FPS * 1.3);
        double[][] forward = new double[3][capacity];
        double[][] up = new double[3][capacity];
        double[][] rate = new double[3][capacity];
        double[] degrees = new double[3];
        boolean hovering = tiltDeg <= 0.0;
        boolean reached = false;
        double start = 0.0;
        double t = 0.0;
        long now = 1_000_000_000L;
        int n = 0;
        double speedSum = 0.0;
        double ratioSum = 0.0;
        double shakeSum = 0.0;
        double shakeMax = 0.0;
        double gateBuffet = 0.0;
        while (t < END_SECONDS && n < capacity) {
            long frame = Math.round(1.0E9 / FPS * (1.0 + FRAME_JITTER * (2.0 * random.nextDouble() - 1.0)));
            double dt = frame * 1.0E-9;
            t += dt;
            now += frame;
            if (t < 2.0 || hovering) {
                hold.apply(w, dt);
                start = w.input.throttle;
            } else {
                w.input.mode = StabMode.ACRO;
                double tilt = Math.toDegrees(Math.acos(SimMath.clamp(body.r11, -1.0, 1.0)));
                reached |= tilt >= tiltDeg - 3.0;
                w.input.pitch = reached ? 0.0 : 0.5;
                double wave = t > SETTLE_SECONDS - 2.0 ? swing * (0.5 - 0.5 * Math.cos(2.0 * Math.PI * t / SWING_SECONDS)) : 0.0;
                w.input.throttle = start + (throttle - start) * Math.min(1.0, 2.0 * (t - 2.0)) - wave;
            }
            w.frame(frame);
            Telemetry tm = w.sim.telemetry();
            double rpm = 0.25 * (Math.abs(tm.motorRpm[0]) + Math.abs(tm.motorRpm[1]) + Math.abs(tm.motorRpm[2]) + Math.abs(tm.motorRpm[3]));
            double ratio = rpm / hoverRpm;
            shake.update(now, ratio, WearShake.atSpeed(WearShake.level(w.sim.wear(), spec.propBrittleness), ratio),
                    Math.max(tm.propWash, tm.propWashVrs), 0.0, tm.armed && !tm.grounded);
            if (t < SETTLE_SECONDS) {
                continue;
            }
            forward[0][n] = body.r02;
            forward[1][n] = body.r12;
            forward[2][n] = body.r22;
            up[0][n] = body.r01;
            up[1][n] = body.r11;
            up[2][n] = body.r21;
            rate[0][n] = body.wbz * SimMath.DEG;
            rate[1][n] = body.wbx * SimMath.DEG;
            rate[2][n] = -body.wby * SimMath.DEG;
            shake.sample(now, CameraShake.DEFAULT_INTENSITY, degrees);
            double deviation = Math.sqrt(degrees[0] * degrees[0] + degrees[1] * degrees[1] + degrees[2] * degrees[2]);
            shakeSum += deviation * deviation;
            shakeMax = Math.max(shakeMax, deviation);
            gateBuffet = Math.max(gateBuffet, CameraShake.buffetDegrees(Math.min(ratio, CameraShake.FLOW_CAP), 0.0,
                    CameraShake.recirculationFade(tm.airspeed, spec.hoverInducedVelocity, ratio)));
            speedSum += tm.airspeed;
            ratioSum += ratio;
            n++;
        }
        assertTrue(w.sim.telemetry().armed, build.frame().name() + " disarmed");
        int window = (int) (0.5 * FPS);
        double[] f = wobble(forward, n, window);
        double[] u = wobble(up, n, window);
        double rateRms = 0.0;
        for (int a = 0; a < 3; a++) {
            rateRms = Math.max(rateRms, rms(highPass(Arrays.copyOf(rate[a], n), window)));
        }
        return new Flight(speedSum / n, ratioSum / n, Math.max(f[0], u[0]), Math.max(f[1], u[1]), rateRms, Math.sqrt(shakeSum / n), shakeMax,
                gateBuffet);
    }

    static double[] wobble(double[][] axis, int n, int window) {
        double[][] hp = {highPass(Arrays.copyOf(axis[0], n), window), highPass(Arrays.copyOf(axis[1], n), window),
                highPass(Arrays.copyOf(axis[2], n), window)};
        double sum = 0.0;
        double max = 0.0;
        int count = 0;
        for (int i = window / 2; i < n - window / 2; i++) {
            double m = Math.sqrt(hp[0][i] * hp[0][i] + hp[1][i] * hp[1][i] + hp[2][i] * hp[2][i]);
            sum += m * m;
            max = Math.max(max, m);
            count++;
        }
        return new double[]{Math.toDegrees(Math.sqrt(sum / Math.max(1, count))), Math.toDegrees(max)};
    }

    static double[] highPass(double[] x, int window) {
        int n = x.length;
        double[] prefix = new double[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + x[i];
        }
        double[] out = new double[n];
        int half = window / 2;
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - half);
            int hi = Math.min(n, i + half + 1);
            out[i] = x[i] - (prefix[hi] - prefix[lo]) / (hi - lo);
        }
        return out;
    }

    static double rms(double[] x) {
        double s = 0.0;
        for (double v : x) {
            s += v * v;
        }
        return Math.sqrt(s / x.length);
    }

    @Test
    void flightModelDoesNotOscillateAtSpeed() {
        for (int k = 0; k < PRESETS.length; k++) {
            String name = PRESETS[k].frame().name();
            for (double[] run : RUNS) {
                if (run[2] != 0.0) {
                    continue;
                }
                Flight flight = fly(PRESETS[k], run);
                String label = name + " tilt " + run[0] + " throttle " + run[1] + " " + flight;
                assertTrue(flight.wobbleRms() < MAX_WOBBLE_RMS_DEG, label);
                assertTrue(flight.wobbleMax() < MAX_WOBBLE_PEAK_DEG, label);
                assertTrue(flight.rateRms() < MAX_RATE_RMS_DPS, label);
            }
            Flight full = fly(PRESETS[k], RUNS[0]);
            assertTrue(full.speed() > MIN_SPEED[k], name + " " + full);
        }
    }

    @Test
    void cameraVibrationStaysAtHoverLevelAtSpeed() {
        for (DroneBuild build : PRESETS) {
            String name = build.frame().name();
            Flight hover = fly(build, 0.0, 0.0, 0.0);
            assertTrue(hover.shakeRms() > 0.005 && hover.shakeRms() < 0.05, name + " " + hover);
            for (double[] run : RUNS) {
                Flight flight = fly(build, run);
                String label = name + " tilt " + run[0] + " throttle " + run[1] + " swing " + run[2] + " " + flight + " hover " + hover.shakeRms();
                assertTrue(flight.shakeRms() < MAX_SHAKE_RATIO * hover.shakeRms(), label);
                assertTrue(flight.shakeMax() < MAX_SHAKE_PEAK_DEG, label);
            }
        }
    }

    @Test
    void lightlyWornPartsKeepTheImageCalmAtSpeed() {
        PartWear[] worn = {
                PartWear.PRISTINE.withMotor(0, 0.97F).withMotor(1, 0.97F).withMotor(2, 0.97F).withMotor(3, 0.97F),
                PartWear.PRISTINE.withProps(new float[]{0.95F, 0.95F, 0.95F, 0.95F}),
                PartWear.PRISTINE.withProps(new float[]{0.9F, 0.9F, 0.9F, 0.9F}).withMotor(0, 0.9F).withMotor(1, 0.9F).withMotor(2, 0.9F)
                        .withMotor(3, 0.9F).withArm(0, 0.9F).withArm(1, 0.9F).withArm(2, 0.9F).withArm(3, 0.9F)};
        for (DroneBuild build : PRESETS) {
            String name = build.frame().name();
            for (PartWear wear : worn) {
                assertTrue(wear.damagedProps() == 0, name);
                Flight hover = fly(build, wear, 0.0, 0.0, 0.0);
                assertTrue(hover.shakeRms() > 0.005 && hover.shakeRms() < 0.05, name + " " + hover);
                for (double[] run : new double[][]{RUNS[0], RUNS[2]}) {
                    Flight flight = fly(build, wear, run[0], run[1], run[2]);
                    String label = name + " tilt " + run[0] + " throttle " + run[1] + " " + flight + " hover " + hover.shakeRms();
                    assertTrue(flight.shakeRms() < MAX_SHAKE_RATIO * hover.shakeRms(), label);
                    assertTrue(flight.shakeMax() < MAX_SHAKE_PEAK_DEG, label);
                }
            }
        }
    }

    @Test
    void aBentPropStillShowsAtFullThrottle() {
        DroneBuild build = PRESETS[2];
        PartWear bent = PartWear.PRISTINE.withProp(0, 0.5F);
        Flight hover = fly(build, bent, 0.0, 0.0, 0.0);
        Flight full = fly(build, bent, RUNS[0][0], RUNS[0][1], RUNS[0][2]);
        assertTrue(full.shakeRms() > 2.5 * hover.shakeRms(), full + " hover " + hover.shakeRms());
    }

    @Test
    void wallsAndGatesDoNotShakeTheImageAtSpeed() {
        for (DroneBuild build : PRESETS) {
            String name = build.frame().name();
            Flight hover = fly(build, 0.0, 0.0, 0.0);
            assertTrue(hover.gateBuffet() > 0.95 * CameraShake.RECIRCULATION_DEG, name + " " + hover);
            Flight full = fly(build, RUNS[0]);
            assertTrue(full.gateBuffet() < MAX_GATE_BUFFET_SHARE * hover.gateBuffet(), name + " " + full);
        }
    }
}
