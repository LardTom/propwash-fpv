package de.lardtom.propwash.sim;

import com.google.gson.Gson;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormulaTest {
    private static RateCurve betaflight(double rc, double sr, double expo) {
        RateCurve r = new RateCurve();
        r.rcRate = rc;
        r.superRate = sr;
        r.rcExpo = expo;
        return r;
    }

    private static RateCurve actual(double center, double max, double expo) {
        RateCurve r = new RateCurve();
        r.centerRateDps = center;
        r.maxRateDps = max;
        r.actualExpo = expo;
        return r;
    }

    @Test
    void ratesMatchReferenceTableAndInvert() {
        double[] x = {0.25, 0.5, 1.0};
        double[][] expected = {
                {60.61, 153.85, 666.67},
                {30.78, 86.54, 666.67},
                {55.00, 185.00, 670.00},
                {36.32, 114.69, 670.00}};
        RateCurve[] profiles = {betaflight(1.0, 0.7, 0.0), betaflight(1.0, 0.7, 0.5), actual(70, 670, 0.0), actual(70, 670, 0.5)};
        RatesType[] types = {RatesType.BETAFLIGHT, RatesType.BETAFLIGHT, RatesType.ACTUAL, RatesType.ACTUAL};
        for (int p = 0; p < profiles.length; p++) {
            for (int i = 0; i < x.length; i++) {
                assertEquals(expected[p][i], RateCurves.rateDps(types[p], profiles[p], x[i]), 0.01);
                assertEquals(-expected[p][i], RateCurves.rateDps(types[p], profiles[p], -x[i]), 0.01);
            }
            assertEquals(expected[p][2], RateCurves.maxRateDps(types[p], profiles[p]), 0.01);
        }
        assertEquals(195.40, RateCurves.rateDps(RatesType.BETAFLIGHT, betaflight(2.5, 0.0, 0.0), 0.1), 0.01);
        Random random = new Random(7);
        for (int k = 0; k < 1000; k++) {
            double stick = -1.0 + 2.0 * random.nextDouble();
            int p = k % profiles.length;
            double rate = RateCurves.rateDps(types[p], profiles[p], stick);
            assertEquals(stick, RateCurves.stickFor(types[p], profiles[p], rate), 1e-6);
        }
    }

    @Test
    void throttleCurveReferenceLimitsAndInverse() {
        PidProfile p = new PidProfile();
        p.throttleMid = 0.4;
        p.throttleExpo = 0.5;
        assertEquals(0.5875, ThrottleCurve.apply(p, 0.7), 1e-9);
        assertEquals(0.275, ThrottleCurve.apply(p, 0.2), 1e-9);
        assertEquals(0.4, ThrottleCurve.apply(p, 0.4), 1e-9);
        for (int k = 0; k <= 100; k++) {
            double t = k / 100.0;
            assertEquals(t, ThrottleCurve.inverse(p, ThrottleCurve.apply(p, t)), 1e-9);
        }
        PidProfile scale = new PidProfile();
        scale.throttleLimitType = ThrottleLimitType.SCALE;
        scale.throttleLimitPercent = 80;
        assertEquals(0.8, ThrottleCurve.apply(scale, 1.0), 1e-9);
        assertEquals(0.4, ThrottleCurve.apply(scale, 0.5), 1e-9);
        PidProfile clip = new PidProfile();
        clip.throttleLimitType = ThrottleLimitType.CLIP;
        clip.throttleLimitPercent = 80;
        assertEquals(0.8, ThrottleCurve.apply(clip, 0.9), 1e-9);
        assertEquals(0.5, ThrottleCurve.apply(clip, 0.5), 1e-9);
        for (int k = 0; k <= 79; k++) {
            double t = k / 100.0;
            assertEquals(t, ThrottleCurve.inverse(scale, ThrottleCurve.apply(scale, t)), 1e-9);
            assertEquals(t, ThrottleCurve.inverse(clip, ThrottleCurve.apply(clip, t)), 1e-9);
        }
    }

    private static double[] duties(Mixer m) {
        double[] d = new double[4];
        for (int i = 0; i < 4; i++) {
            d[i] = 0.055 + 0.945 * m.output[i];
        }
        return d;
    }

    @Test
    void mixerReferenceCases() {
        AirframeSpec params = AirframeSpec.of(DroneBuild.FREESTYLE);
        Mixer m = new Mixer();
        m.mix(params, 0.2, 0.0, 0.0, 0.0, true, false);
        assertArrayClose(new double[]{0.055, 0.055, 0.433, 0.433}, duties(m), 1e-4);
        m.mix(params, 0.2, 0.0, 0.0, 0.0, false, false);
        assertArrayClose(new double[]{0.055, 0.055, 0.244, 0.244}, duties(m), 1e-4);
        assertTrue(m.saturated);
        m.mix(params, 0.5, 0.4, 0.2, 0.0, true, false);
        assertArrayClose(new double[]{0.2222, 0.0, 1.0, 0.3333}, m.output, 1e-4);
        assertArrayClose(new double[]{0.2650, 0.055, 1.0, 0.3700}, duties(m), 1e-4);
        assertEquals(0.3889, m.throttle, 1e-4);
        assertTrue(m.saturated);
        m.mix(params, 0.0, 0.0, 0.1, 0.5, true, false);
        double[] propsIn = m.output.clone();
        m.mix(params, 0.0, 0.0, 0.1, 0.5, true, true);
        for (int i = 0; i < 4; i++) {
            assertEquals(0.5 - (propsIn[i] - 0.5), m.output[i], 1e-12);
        }
        m.mix(params, 0.1, 0.2, 0.0, 0.5, true, false);
        double[] rollPitchRate = m.output.clone();
        m.mix(params, 0.1, 0.2, 0.0, 0.5, true, true);
        assertArrayClose(rollPitchRate, m.output, 1e-12);
    }

    private static void assertArrayClose(double[] expected, double[] actual, double tol) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], tol, "index " + i);
        }
    }

    @Test
    void pt1StepAndPt2Cutoff() {
        double f = 10.0;
        double dt = 1e-5;
        Pt1 pt1 = new Pt1();
        pt1.configure(f, dt);
        double tau = 1.0 / (2.0 * Math.PI * f);
        int steps = (int) Math.round(tau / dt);
        double y = 0.0;
        for (int k = 0; k < steps; k++) {
            y = pt1.apply(1.0);
        }
        assertEquals(0.632, y, 0.005);
        double k = SimMath.pt1Gain(f * Pt2.CASCADE_CORRECTION, dt);
        double w = 2.0 * Math.PI * f * dt;
        double re = 1.0 - (1.0 - k) * Math.cos(w);
        double im = (1.0 - k) * Math.sin(w);
        double mag = k / Math.sqrt(re * re + im * im);
        double db = 20.0 * Math.log10(mag * mag);
        assertEquals(-3.0103, db, 0.3);
        Pt2 pt2 = new Pt2();
        pt2.configure(f, dt);
        double peak = 0.0;
        for (int i = 0; i < 400_000; i++) {
            double out = pt2.apply(Math.sin(2.0 * Math.PI * f * i * dt));
            if (i > 200_000) {
                peak = Math.max(peak, Math.abs(out));
            }
        }
        assertEquals(-3.0103, 20.0 * Math.log10(peak), 0.3);
    }

    private static DroneSim armedStand(DroneBuild build) {
        SimTuning t = Tests.quietTuning();
        t.infiniteBattery = true;
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), t, 1L);
        sim.reset(0, 100, 0, 0);
        PilotInput in = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        StepResult out = new StepResult();
        sim.step(0L, in, env, out);
        in.armSwitch = true;
        sim.step(0L, in, env, out);
        assertTrue(sim.telemetry().armed);
        return sim;
    }

    private static DroneSim conventionRun(double[] extra) {
        DroneSim sim = armedStand(DroneBuild.FREESTYLE);
        AirframeSpec p = sim.params();
        OperatingPoints.Point hover = new OperatingPoints.Point();
        OperatingPoints.hover(p, 1.0, false, 0.0, true, hover);
        for (int i = 0; i < 4; i++) {
            sim.power.omega[i] = p.hoverOmega;
        }
        sim.setDutyOverride(hover.duty + extra[0], hover.duty + extra[1], hover.duty + extra[2], hover.duty + extra[3]);
        PilotInput in = new PilotInput();
        in.armSwitch = true;
        SimEnvironment env = new SimEnvironment();
        StepResult out = new StepResult();
        for (int k = 0; k < 4; k++) {
            sim.step(5_000_000L, in, env, out);
        }
        return sim;
    }

    @Test
    void axisConventions() {
        DroneSim left = conventionRun(new double[]{0, 0, 0.05, 0.05});
        assertTrue(left.body.wbz > 0.0);
        assertTrue(left.telemetry().rollDeg > 0.0);
        assertTrue(left.body.r10 > 0.0);
        DroneSim rear = conventionRun(new double[]{0.05, 0, 0.05, 0});
        assertTrue(rear.body.wbx > 0.0);
        assertTrue(rear.telemetry().pitchDeg > 0.0);
        DroneSim ccw = conventionRun(new double[]{0, 0.05, 0.05, 0});
        assertTrue(ccw.body.wby < 0.0);
        assertTrue(Tests.yawRateDps(ccw) > 0.0);
        RigidBody b = new RigidBody();
        b.setUpright(Math.PI / 2.0);
        assertEquals(90.0, b.headingRad() * SimMath.DEG, 1e-9);
        assertEquals(-90.0, -b.headingRad() * SimMath.DEG, 1e-9);
        assertEquals(1.0, b.r02, 1e-9);
    }

    private static void setYawPitch(RigidBody b, double psi, double theta) {
        double cy = Math.cos(psi / 2.0);
        double sy = Math.sin(psi / 2.0);
        double cx = Math.cos(theta / 2.0);
        double sx = Math.sin(theta / 2.0);
        b.qw = cy * cx;
        b.qx = cy * sx;
        b.qy = sy * cx;
        b.qz = -sy * sx;
        b.updateDerived();
    }

    @Test
    void twistHeadingMatchesHeadingAndIsContinuous() {
        Random random = new Random(3);
        RigidBody b = new RigidBody();
        for (int k = 0; k < 1000; k++) {
            double psi = -Math.PI + 2.0 * Math.PI * random.nextDouble();
            double theta = Math.toRadians(-89.0 + 178.0 * random.nextDouble());
            setYawPitch(b, psi, theta);
            assertEquals(psi, AttitudeController.twistHeading(b, 0.0), 1e-12);
            assertEquals(psi, b.headingRad(), 1e-9);
        }
        double psi = 0.7;
        double prev = Double.NaN;
        for (int k = 0; k <= 2000; k++) {
            double theta = Math.toRadians(80.0 + 20.0 * k / 2000.0);
            setYawPitch(b, psi, theta);
            double twist = AttitudeController.twistHeading(b, 0.0);
            if (!Double.isNaN(prev)) {
                assertTrue(Math.abs(twist - prev) < 1e-6);
            }
            prev = twist;
            setYawPitch(b, psi, -theta);
            assertEquals(psi, AttitudeController.twistHeading(b, 0.0), 1e-9);
        }
    }

    @Test
    void openCircuitVoltageCurve() {
        for (BatteryType.Chemistry c : BatteryType.Chemistry.values()) {
            assertEquals(c.fullCellVoltage, CellCurve.ocv(c, 1.0));
            assertEquals(c.emptyCellVoltage, CellCurve.ocv(c, 0.0));
            assertEquals(c.cutoffCellVoltage, CellCurve.ocv(c, -0.05), 1e-12);
            assertEquals(0.8 * c.cutoffCellVoltage, CellCurve.ocv(c, -0.10), 1e-12);
            assertEquals(0.8 * c.cutoffCellVoltage, CellCurve.ocv(c, -0.30), 1e-12);
            double prev = CellCurve.ocv(c, -0.10);
            for (int k = -100; k <= 1000; k++) {
                double v = CellCurve.ocv(c, k / 1000.0);
                assertTrue(v >= prev);
                prev = v;
            }
        }
    }

    private static final Class<?>[] CONFIG_CLASSES = {FlightControllerConfig.class, RateProfile.class, RateCurve.class,
            PidProfile.class, AxisPid.class, FilterSettings.class, SimTuning.class};

    @Test
    void configurationObjectsArePlainCopyableSanitizableAndJsonFriendly() throws Exception {
        for (Class<?> type : CONFIG_CLASSES) {
            assertTrue(Modifier.isPublic(type.getModifiers()));
            type.getConstructor().newInstance();
            for (Field f : type.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                assertTrue(Modifier.isPublic(f.getModifiers()), type.getSimpleName() + "." + f.getName());
                assertFalse(Modifier.isFinal(f.getModifiers()), type.getSimpleName() + "." + f.getName());
            }
        }
        FlightControllerConfig c = new FlightControllerConfig();
        FlightControllerConfig copy = c.copy();
        assertNotSame(c.rates, copy.rates);
        assertNotSame(c.rates.roll, copy.rates.roll);
        assertNotSame(c.pid, copy.pid);
        assertNotSame(c.pid.yaw, copy.pid.yaw);
        assertNotSame(c.filters, copy.filters);
        copy.pid.roll.p = 99;
        copy.rates.yaw.maxRateDps = 900;
        copy.filters.gyroLpfHz = 300;
        assertEquals(42, c.pid.roll.p);
        assertEquals(670, c.rates.yaw.maxRateDps);
        assertEquals(250, c.filters.gyroLpfHz);

        FlightControllerConfig broken = new FlightControllerConfig();
        broken.rates = null;
        broken.pid.roll = null;
        broken.pid.itermRelax = null;
        broken.pid.angleLimitDeg = 500;
        broken.pid.motorIdlePercent = Double.NaN;
        broken.pid.derivedForBuild = null;
        broken.filters.gyroLpfHz = 5;
        broken.filters.dtermLpf2Hz = -3;
        broken.filters.pidLoop = null;
        broken.armMaxTiltDeg = -10;
        broken.sanitize();
        assertEquals(RatesType.ACTUAL, broken.rates.type);
        assertEquals(42, broken.pid.roll.p);
        assertEquals(ItermRelaxMode.RP, broken.pid.itermRelax);
        assertEquals(85, broken.pid.angleLimitDeg);
        assertEquals(5.5, broken.pid.motorIdlePercent);
        assertEquals("", broken.pid.derivedForBuild);
        assertEquals(20, broken.filters.gyroLpfHz);
        assertEquals(0, broken.filters.dtermLpf2Hz);
        assertEquals(PidLoopRate.HZ_2000, broken.filters.pidLoop);
        assertEquals(0, broken.armMaxTiltDeg);
        RateCurve rates = new RateCurve();
        rates.centerRateDps = 500;
        rates.maxRateDps = 100;
        rates.sanitize();
        assertEquals(500, rates.maxRateDps);
        SimTuning tuning = new SimTuning();
        tuning.crashSpeedScale = 0.0;
        tuning.gyroNoiseDps = 99;
        tuning.sanitize();
        assertEquals(0.25, tuning.crashSpeedScale);
        assertEquals(20, tuning.gyroNoiseDps);

        Gson gson = new Gson();
        FlightControllerConfig derived = FlightControllerConfig.defaultsFor(AirframeSpec.of(DroneBuild.WHOOP));
        derived.rates.type = RatesType.BETAFLIGHT;
        derived.pid.throttleLimitType = ThrottleLimitType.CLIP;
        String json = gson.toJson(derived);
        FlightControllerConfig back = gson.fromJson(json, FlightControllerConfig.class);
        assertEquals(json, gson.toJson(back));
        assertEquals(RatesType.BETAFLIGHT, back.rates.type);
        assertEquals(derived.pid.roll.d, back.pid.roll.d);
        SimTuning tuningBack = gson.fromJson(gson.toJson(new SimTuning()), SimTuning.class);
        assertEquals(gson.toJson(new SimTuning()), gson.toJson(tuningBack));
    }

    @Test
    void gaussianNoiseHasUnitVariance() {
        SimRandom r = new SimRandom(42L);
        int n = 1_000_000;
        double sum = 0.0;
        double sq = 0.0;
        for (int i = 0; i < n; i++) {
            double g = r.gauss();
            sum += g;
            sq += g * g;
        }
        double mean = sum / n;
        double var = sq / n - mean * mean;
        assertEquals(0.0, mean, 0.01);
        assertEquals(1.0, var, 0.02);
    }

    @Test
    void eventQueueKeepsLatestAndCountsOverflowWithoutAllocation() {
        EventQueue q = new EventQueue();
        for (int i = 0; i < 100; i++) {
            q.add(SimEventType.BUMP, i, i, -1);
        }
        assertEquals(32, q.size());
        assertEquals(68, q.overflowCount());
        assertEquals(68.0, q.value(0));
        assertEquals(99.0, q.value(31));
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int round = 0; round < 3; round++) {
            q.clear();
            for (int i = 0; i < 100; i++) {
                q.add(SimEventType.CRASH, i, i, 2);
            }
        }
        long before = bean.getThreadAllocatedBytes(thread);
        for (int round = 0; round < 100; round++) {
            q.clear();
            for (int i = 0; i < 100; i++) {
                q.add(SimEventType.CRASH, i, i, 2);
            }
        }
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(allocated < 1024, "allocated " + allocated);
        assertEquals(SimEventType.CRASH, q.type(5));
        assertEquals(2, q.motor(5));
    }
}
