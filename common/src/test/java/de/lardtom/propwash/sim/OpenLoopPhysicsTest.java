package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenLoopPhysicsTest {
    static final DroneBuild[] PRESETS = DerivationTest.PRESETS;

    private static DroneSim freeSim(DroneBuild build, SimTuning tuning, double y) {
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), tuning, 42L);
        sim.reset(0.0, y, 0.0, 0.0);
        return sim;
    }

    private static void runFree(DroneSim sim, PilotInput in, SimEnvironment env, long frames, long frameNanos) {
        StepResult out = new StepResult();
        MoveReport report = new MoveReport();
        for (long f = 0; f < frames; f++) {
            sim.step(frameNanos, in, env, out);
            report.clear();
            report.requestedX = out.dx;
            report.requestedY = out.dy;
            report.requestedZ = out.dz;
            report.actualX = out.dx;
            report.actualY = out.dy;
            report.actualZ = out.dz;
            sim.afterMove(report);
        }
    }

    @Test
    void freeFallWithoutDrag() {
        SimTuning t = Tests.quietTuning();
        t.dragScale = 0.0;
        DroneSim sim = freeSim(DroneBuild.FREESTYLE, t, 100.0);
        runFree(sim, new PilotInput(), new SimEnvironment(), 20, 50_000_000L);
        assertEquals(-9.81, sim.body.vy, 0.005);
        assertEquals(-4.905, sim.body.py - 100.0, 0.01);
        assertEquals(1.0, sim.telemetry().time, 1e-9);
    }

    @Test
    void flatTerminalVelocityDisarmed() {
        double[] expected = {10.0, 15.2, 16.4, 18.8, 18.0};
        for (int k = 0; k < PRESETS.length; k++) {
            DroneSim sim = freeSim(PRESETS[k], Tests.quietTuning(), 5000.0);
            runFree(sim, new PilotInput(), new SimEnvironment(), 400, 50_000_000L);
            assertEquals(expected[k], -sim.body.vy, 0.03 * expected[k], PRESETS[k].frame().name());
            assertTrue(sim.telemetry().tiltDeg < 0.01);
        }
    }

    @Test
    void testStandFullThrottleMatchesStationaryPoint() {
        for (DroneBuild b : PRESETS) {
            TestStand stand = TestStand.of(b);
            stand.spinUp();
            stand.duty(1.0);
            stand.substeps(24000);
            AirframeSpec p = stand.sim.params();
            OperatingPoints.Point expected = new OperatingPoints.Point();
            OperatingPoints.uniformDuty(p, 1.0, stand.sim.power.soc, false, stand.sim.power.polarization, 0.0, 0.0, expected);
            String n = b.frame().name();
            double expectedRpm = expected.omega * 60.0 / (2.0 * Math.PI);
            for (int i = 0; i < 4; i++) {
                assertEquals(expectedRpm, stand.rpm(i), 0.02 * expectedRpm, n + " rpm");
                assertEquals(expected.thrustPerMotor, stand.sim.thrust[i], 0.02 * expected.thrustPerMotor, n + " thrust");
            }
            assertEquals(expected.batteryCurrent, stand.sim.telemetry().totalCurrentA, 0.02 * expected.batteryCurrent, n + " current");
            assertEquals(expected.busVoltage, stand.sim.telemetry().packVoltage, 0.02 * expected.busVoltage, n + " voltage");
        }
    }

    @Test
    void motorTimeConstantMatchesDerivation() {
        for (DroneBuild b : PRESETS) {
            SimTuning stiff = Tests.quietTuning();
            stiff.batterySag = false;
            stiff.infiniteBattery = true;
            TestStand stand = new TestStand(b, stiff);
            stand.sim.power.stiffBus = true;
            AirframeSpec p = stand.sim.params();
            OperatingPoints.Point hover = new OperatingPoints.Point();
            OperatingPoints.hover(p, 1.0, false, 0.0, true, hover);
            stand.duty(hover.duty);
            stand.substeps(4000);
            double start = stand.omega(0);
            stand.duty(hover.duty + 0.1);
            TestStand probe = new TestStand(b, stiff);
            probe.sim.power.stiffBus = true;
            probe.duty(hover.duty);
            probe.substeps(4000);
            probe.duty(hover.duty + 0.1);
            probe.substeps(4000);
            double end = probe.omega(0);
            double target = start + 0.632 * (end - start);
            int n = 0;
            while (stand.omega(0) < target && n < 4000) {
                stand.substeps(1);
                n++;
            }
            double t63 = n * DroneSim.H;
            assertEquals(p.motorTau, t63, 0.15 * p.motorTau, b.frame().name());
        }
    }

    @Test
    void activeBrakingIsFastAndRegenerates() {
        for (DroneBuild b : PRESETS) {
            AirframeSpec p = AirframeSpec.of(b);
            TestStand braking = TestStand.of(b);
            braking.spinUp();
            braking.duty(1.0);
            braking.substeps(1000);
            double full = braking.omega(0);
            braking.duty(0.055);
            double minCurrent = Double.POSITIVE_INFINITY;
            int half = -1;
            int tenth = -1;
            for (int k = 1; k <= 4000; k++) {
                braking.substeps(1);
                minCurrent = Math.min(minCurrent, braking.sim.power.batteryCurrent);
                if (half < 0 && braking.omega(0) < 0.5 * full) {
                    half = k;
                }
                if (tenth < 0 && braking.omega(0) < 0.1 * full) {
                    tenth = k;
                }
            }
            String n = b.frame().name();
            assertTrue(half > 0 && half * DroneSim.H < 3.0 * p.motorTau, n + " half at " + half);
            assertTrue(minCurrent < 0.0, n + " min current " + minCurrent);
            TestStand coasting = TestStand.of(b);
            coasting.spinUp();
            coasting.duty(1.0);
            coasting.substeps(1000);
            coasting.input.armSwitch = false;
            int coastTenth = -1;
            for (int k = 1; k <= 200_000 && coastTenth < 0; k++) {
                coasting.substeps(1);
                if (coasting.omega(0) < 0.1 * full) {
                    coastTenth = k;
                }
            }
            assertTrue(tenth > 0, n);
            assertTrue(coastTenth < 0 || coastTenth >= 5 * tenth, n + " coast " + coastTenth + " brake " + tenth);
        }
    }

    @Test
    void escCurrentLimitHoldsAndEventsAreRateLimited() {
        TestStand stand = TestStand.of(DroneBuild.FREESTYLE);
        double limit = stand.sim.params().escCurrentLimit;
        int[] events = new int[4];
        double maxCurrent = 0.0;
        for (int k = 0; k < 3980; k++) {
            stand.duty((k / 250) % 2 == 0 ? 1.0 : 0.055);
            stand.substeps(1);
            for (int i = 0; i < 4; i++) {
                maxCurrent = Math.max(maxCurrent, Math.abs(stand.sim.power.current[i]));
            }
            EventQueue q = stand.sim.events();
            for (int e = 0; e < q.size(); e++) {
                if (q.type(e) == SimEventType.ESC_CURRENT_LIMIT) {
                    events[q.motor(e)]++;
                }
            }
        }
        assertTrue(maxCurrent <= limit + 1e-9, "max " + maxCurrent);
        assertTrue(maxCurrent > 0.99 * limit, "max " + maxCurrent);
        for (int i = 0; i < 4; i++) {
            assertTrue(events[i] >= 1 && events[i] <= 4, "motor " + i + " events " + events[i]);
        }
        DroneBuild undersized = new DroneBuild(FrameType.CINEWHOOP_3, StackType.AIO_20, MotorType.M2207_1750, PropType.P51_3B,
                VideoType.DIGITAL_HD, BatteryType.B6S_1300);
        assertTrue(undersized.warnings().contains(DroneBuild.Issue.ESC_UNDERSIZED));
        AirframeSpec p = AirframeSpec.of(undersized);
        PowerSystem power = new PowerSystem();
        power.configure(p, DroneSim.H);
        power.setStateOfCharge(1.0);
        double[] duty = {1.0, 1.0, 1.0, 1.0};
        double[] lambda = new double[4];
        double worst = 0.0;
        int limitedSteps = 0;
        for (int k = 0; k < 4000; k++) {
            power.solveBus(duty, true, true);
            power.stepRotors(DroneSim.H, lambda, 1.0, true);
            power.integrateBattery(DroneSim.H, true);
            for (int i = 0; i < 4; i++) {
                worst = Math.max(worst, Math.abs(power.current[i]));
            }
            if (power.anyLimited) {
                limitedSteps++;
            }
        }
        assertTrue(worst <= p.escCurrentLimit + 1e-9, "worst " + worst);
        assertTrue(limitedSteps > 50, "limited " + limitedSteps);
    }

    @Test
    void batteryCoulombCountingSagAndRecovery() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        PowerSystem power = new PowerSystem();
        double h = DroneSim.H;
        power.configure(p, h);
        power.setStateOfCharge(1.0);
        for (int k = 0; k < 360 * DroneSim.PHYSICS_HZ; k++) {
            power.forceBatteryCurrent(10.0, h, true, false);
        }
        assertEquals(1000.0, power.mAhUsed, 1.0);
        AirframeSpec lr = AirframeSpec.of(DroneBuild.LONG_RANGE);
        PowerSystem pack = new PowerSystem();
        pack.configure(lr, h);
        pack.setStateOfCharge(1.0);
        double current = lr.continuousCurrent;
        pack.forceBatteryCurrent(current, h, true, true);
        double ocv = lr.cells * CellCurve.ocv(lr.chemistry, pack.soc);
        double drop = ocv - pack.busVoltage;
        double ohmic = (1.0 - ModelConstants.SAG_FAST_FRACTION) * lr.packResistance;
        assertEquals(ohmic * current, drop, 0.02 * ohmic * current);
        for (int k = 1; k < 25 * DroneSim.PHYSICS_HZ; k++) {
            pack.forceBatteryCurrent(current, h, true, true);
        }
        pack.forceBatteryCurrent(current, h, true, true);
        ocv = lr.cells * CellCurve.ocv(lr.chemistry, pack.soc);
        drop = ocv - pack.busVoltage;
        double expected = (lr.packResistance + lr.polarizationResistance) * current;
        assertEquals(expected, drop, 0.02 * expected);
        assertEquals(ModelConstants.SAG_FAST_FRACTION * lr.packResistance * current, pack.fastPolarization, 1e-6);
        double initialFast = pack.fastPolarization;
        double initialSlow = pack.polarization;
        int fast = 0;
        int slow = 0;
        for (int n = 1; n <= 40 * DroneSim.PHYSICS_HZ && (fast == 0 || slow == 0); n++) {
            pack.forceBatteryCurrent(0.0, h, true, true);
            if (fast == 0 && pack.fastPolarization <= initialFast * Math.exp(-1.0)) {
                fast = n;
            }
            if (slow == 0 && pack.polarization <= initialSlow * Math.exp(-1.0)) {
                slow = n;
            }
        }
        assertEquals(ModelConstants.SAG_FAST_TAU, fast * h, 0.05);
        assertEquals(ModelConstants.POL_TAU, slow * h, 0.5);
    }

    private static double bisectBus(AirframeSpec p, PowerSystem ps, double[] duty) {
        double e = ps.emf;
        double r = ps.internalResistance;
        double aux = p.auxPower / Math.max(ps.lastBusVoltage, 1.0);
        double lo = 0.2 * e;
        double hi = e + 0.1 * p.cells;
        for (int k = 0; k < 200; k++) {
            double v = 0.5 * (lo + hi);
            double sum = aux;
            for (int i = 0; i < 4; i++) {
                double c = (duty[i] * v - p.kt * ps.omega[i]) / p.phaseResistance;
                c = SimMath.clamp(c, -p.escCurrentLimit, OperatingPoints.currentLimit(p, v, ps.omega[i]));
                sum += (p.phaseResistance * c + p.kt * ps.omega[i]) * c / v;
            }
            if (e - r * sum - v > 0.0) {
                lo = v;
            } else {
                hi = v;
            }
        }
        return 0.5 * (lo + hi);
    }

    @Test
    void closedFormBusMatchesBisectionAndDoesNotOscillate() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        PowerSystem ps = new PowerSystem();
        ps.configure(p, DroneSim.H);
        ps.setStateOfCharge(0.7);
        Random random = new Random(11);
        double[] duty = new double[4];
        double[] lambda = new double[4];
        for (int trial = 0; trial < 200; trial++) {
            for (int i = 0; i < 4; i++) {
                duty[i] = random.nextDouble();
                ps.omega[i] = random.nextDouble() * p.omegaMax * 0.8;
            }
            ps.heatCurrent = random.nextDouble() * 200.0;
            ps.solveBus(duty, true, true);
            assertEquals(bisectBus(p, ps, duty), ps.busVoltage, 1e-3);
        }
        ps.stopRotors();
        ps.setStateOfCharge(1.0);
        double[] full = {1.0, 1.0, 1.0, 1.0};
        double prev = Double.NaN;
        double prevDerivative = 0.0;
        int signChanges = 0;
        for (int k = 0; k < 600; k++) {
            ps.solveBus(full, true, true);
            ps.stepRotors(DroneSim.H, lambda, 1.0, true);
            ps.integrateBattery(DroneSim.H, true);
            double v = ps.busVoltage;
            if (!Double.isNaN(prev)) {
                double d = v - prev;
                if (Math.abs(d) > 1e-3) {
                    if (prevDerivative != 0.0 && Math.signum(d) != Math.signum(prevDerivative)) {
                        signChanges++;
                    }
                    prevDerivative = d;
                }
            }
            prev = v;
        }
        assertTrue(signChanges <= 1, "sign changes " + signChanges);
    }

    @Test
    void groundEffectReferenceValues() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        double spacing = 2.0 * p.motorOffset;
        assertEquals(1.114, GroundEffect.factor(p.propRadius, p.propDiameter / 2.0, spacing), 0.002);
        assertEquals(1.044, GroundEffect.factor(p.propRadius, p.propDiameter, spacing), 0.002);
        assertEquals(1.000, GroundEffect.factor(p.propRadius, Double.POSITIVE_INFINITY, spacing), 0.002);
        assertEquals(1.000, GroundEffect.factor(p.propRadius, 50.0, spacing), 0.002);
        assertEquals(ModelConstants.GE_CAP, GroundEffect.factor(p.propRadius, 0.0, spacing), 1e-12);
    }

    @Test
    void inflowFactorsAndHForceDirection() {
        assertEquals(0.0, RotorAero.axialThrustFactor(1.0));
        assertEquals(0.0, RotorAero.axialThrustFactor(2.0));
        assertEquals(1.3, RotorAero.axialThrustFactor(-0.6), 1e-12);
        assertEquals(1.3, RotorAero.axialThrustFactor(-5.0), 1e-12);
        assertEquals(1.0, RotorAero.axialThrustFactor(0.0));
        assertTrue(RotorAero.hForceMagnitude(10.0, 5.0, 1000.0, 0.065) > 0.0);
        SimTuning t = Tests.quietTuning();
        t.dragScale = 0.0;
        t.infiniteBattery = true;
        TestStand stand = new TestStand(DroneBuild.FREESTYLE, t);
        OperatingPoints.Point hover = new OperatingPoints.Point();
        OperatingPoints.hover(stand.sim.params(), 1.0, false, 0.0, true, hover);
        stand.duty(hover.duty);
        stand.env.windX = 6.0;
        stand.env.windZ = -3.0;
        stand.substeps(400);
        double sx = stand.sim.specificForceWorld(0);
        double sz = stand.sim.specificForceWorld(2);
        assertTrue(sx > 0.0);
        assertTrue(sz < 0.0);
        assertEquals(-2.0, sx / sz, 1e-6);
        assertTrue(stand.sim.specificForceWorld(1) > 9.0);
    }

    @Test
    void vortexRingWeights() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        double t = p.hoverThrust();
        double vh = Math.sqrt(t / (2.0 * ModelConstants.RHO0 * p.discArea * p.ductGain));
        assertTrue(PropWash.vortexRingWeight(t, t, -1.0 * vh, 0.0, ModelConstants.RHO0, p.discArea, p.ductGain) >= 0.95);
        assertTrue(PropWash.vortexRingWeight(t, t, -0.1 * vh, 0.0, ModelConstants.RHO0, p.discArea, p.ductGain) <= 0.02);
        assertTrue(PropWash.vortexRingWeight(t, t, -1.0 * vh, 1.5 * vh, ModelConstants.RHO0, p.discArea, p.ductGain) <= 0.05);
        assertEquals(p.hoverInducedVelocity, vh, 1e-9);
    }

    @Test
    void bodyDragNeverAddsEnergy() {
        Random random = new Random(5);
        BodyAero aero = new BodyAero();
        for (DroneBuild b : PRESETS) {
            AirframeSpec p = AirframeSpec.of(b);
            for (int k = 0; k < 2000; k++) {
                double ux = random.nextGaussian() * 20.0;
                double uy = random.nextGaussian() * 20.0;
                double uz = random.nextGaussian() * 20.0;
                aero.drag(p, 0.3 + random.nextDouble() * 1.2, random.nextDouble() * 4.0, ux, uy, uz, DroneSim.H);
                assertTrue(aero.fx * ux + aero.fy * uy + aero.fz * uz <= 0.0);
            }
            for (int k = 0; k < 2000; k++) {
                double ux = random.nextGaussian() * 60.0;
                double uy = random.nextGaussian() * 60.0;
                double uz = random.nextGaussian() * 60.0;
                aero.drag(p, ModelConstants.RHO0, DroneSim.WATER_DRAG * random.nextDouble() * 4.0, ux, uy, uz, DroneSim.H);
                double h = DroneSim.H / p.massKg;
                assertTrue((ux + h * aero.fx) * ux >= 0.0 && (uy + h * aero.fy) * uy >= 0.0 && (uz + h * aero.fz) * uz >= 0.0,
                        b + " water drag reversed the relative velocity");
            }
        }
    }

    @Test
    void torqueFreeRotationConservesMomentumAndEnergy() {
        RigidBody b = new RigidBody();
        b.configure(0.5, 1.0e-3, 2.5e-3, 1.6e-3);
        b.setUpright(0.3);
        b.setBodyRates(10.0, 20.0, 5.0);
        double l0 = Math.sqrt(b.lx * b.lx + b.ly * b.ly + b.lz * b.lz);
        double e0 = b.kineticEnergy();
        for (int k = 0; k < 10 * DroneSim.PHYSICS_HZ; k++) {
            b.integrate(DroneSim.H, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double l1 = Math.sqrt(b.lx * b.lx + b.ly * b.ly + b.lz * b.lz);
        assertEquals(0.0, Math.abs(l1 - l0) / l0, 1e-12);
        assertEquals(e0, b.kineticEnergy(), 0.005 * e0);
        double norm = Math.sqrt(b.qw * b.qw + b.qx * b.qx + b.qy * b.qy + b.qz * b.qz);
        assertEquals(1.0, norm, 1e-12);
    }

    @Test
    void gyroscopicTorqueFromRotorMomentum() {
        SimTuning t = Tests.quietTuning();
        t.dragScale = 0.0;
        DroneSim sim = freeSim(DroneBuild.FREESTYLE, t, 100.0);
        SimEnvironment thin = new SimEnvironment();
        thin.airDensity = 0.3;
        AirframeSpec p = sim.params();
        sim.power.omega[0] = 800.0;
        sim.power.omega[1] = 2400.0;
        sim.power.omega[2] = 2400.0;
        sim.power.omega[3] = 800.0;
        sim.body.setBodyRates(6.0, 0.0, 0.0);
        double h = 0.0;
        for (int i = 0; i < 4; i++) {
            h += p.spin(i) * p.rotorInertiaEff * sim.power.omega[i];
        }
        double expected = -(6.0 * h) / p.inertiaZz;
        double before = sim.body.wbz;
        sim.step(DroneSim.SUBSTEP_NANOS, new PilotInput(), thin, new StepResult());
        double accel = (sim.body.wbz - before) / DroneSim.H;
        assertEquals(expected, accel, 0.05 * Math.abs(expected));
    }

    @Test
    void yawKickExceedsSteadyYawAcceleration() {
        for (DroneBuild b : PRESETS) {
            SimTuning t = Tests.quietTuning();
            t.infiniteBattery = true;
            DroneSim sim = freeSim(b, t, 100.0);
            FlatWorld w = new FlatWorld(sim);
            w.groundY = Double.NEGATIVE_INFINITY;
            w.place(0, 100, 0, 0);
            w.arm();
            AirframeSpec p = sim.params();
            OperatingPoints.Point hover = new OperatingPoints.Point();
            OperatingPoints.hover(p, 1.0, false, 0.0, true, hover);
            for (int i = 0; i < 4; i++) {
                sim.power.omega[i] = p.hoverOmega;
            }
            double d = hover.duty;
            sim.setDutyOverride(d, d, d, d);
            for (int k = 0; k < 400; k++) {
                w.frame(DroneSim.SUBSTEP_NANOS);
            }
            sim.body.setBodyRates(0.0, 0.0, 0.0);
            double delta = 0.1;
            sim.setDutyOverride(d, d + delta, d + delta, d);
            double peak = 0.0;
            double prev = Tests.yawRateDps(sim);
            double last = 0.0;
            for (int k = 0; k < 400; k++) {
                w.frame(DroneSim.SUBSTEP_NANOS);
                double r = Tests.yawRateDps(sim);
                double accel = (r - prev) / DroneSim.H;
                prev = r;
                peak = Math.max(peak, accel);
                last = accel;
            }
            assertTrue(peak >= 3.0 * last, b.frame().name() + " peak " + peak + " steady " + last);
            assertTrue(last > 0.0);
        }
    }

    @Test
    void punchFromHover() {
        double[] vy = {11.0, 21.2, 39.2, 44.3, 27.9};
        double[] g = {2.98, 4.69, 10.44, 12.77, 6.47};
        for (int k = 0; k < PRESETS.length; k++) {
            FlatWorld w = Tests.hovering(PRESETS[k], Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
            w.sim.setBatteryCharge(1.0);
            double v0 = w.sim.body.vy;
            w.input.throttle = 1.0;
            w.input.roll = 0.0;
            w.input.pitch = 0.0;
            w.run(1.0, 144.0);
            String n = PRESETS[k].frame().name();
            assertEquals(vy[k], w.sim.body.vy - v0, 0.10 * vy[k], n + " vy");
            assertEquals(g[k], w.sim.telemetry().gLoadMax, 0.10 * g[k], n + " g");
        }
    }
}
