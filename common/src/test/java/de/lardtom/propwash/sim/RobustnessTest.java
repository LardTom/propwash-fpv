package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobustnessTest {
    private static void assertFinite(DroneSim sim) {
        RigidBody b = sim.body;
        assertTrue(Double.isFinite(b.px + b.py + b.pz + b.vx + b.vy + b.vz + b.qw + b.qx + b.qy + b.qz + b.lx + b.ly + b.lz));
        for (int i = 0; i < 4; i++) {
            assertTrue(Double.isFinite(sim.power.omega[i]));
        }
        assertTrue(Double.isFinite(sim.power.busVoltage + sim.power.soc + sim.power.mAhUsed));
    }

    @Test
    void oddFrameTimesAreHandled() {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        double[] dts = {0.0, 1e-9, 0.016, 0.05, 0.2, Double.NaN, -1.0, Double.POSITIVE_INFINITY};
        for (double dt : dts) {
            w.input.throttle = w.sim.telemetry().hoverThrottleStick;
            w.sim.step(dt, w.input, w.env, w.result);
            w.move();
            assertFinite(w.sim);
            assertTrue(w.result.substeps <= DroneSim.MAX_SUBSTEPS);
            assertEquals(dt == 0.2, w.sim.events().contains(SimEventType.FRAME_HITCH), "dt " + dt);
            if (dt == 0.2) {
                assertEquals(DroneSim.MAX_SUBSTEPS, w.result.substeps);
            }
            if (!(dt > 0.0) || Double.isInfinite(dt)) {
                assertEquals(0, w.result.substeps, "dt " + dt);
            }
        }
        assertTrue(w.sim.telemetry().armed);
    }

    @Test
    void nanInputsAreTreatedAsNeutral() {
        DroneSim a = new DroneSim(DroneBuild.RACER, new FlightControllerConfig(), new SimTuning(), 9L);
        DroneSim b = new DroneSim(DroneBuild.RACER, new FlightControllerConfig(), new SimTuning(), 9L);
        a.reset(0, 50, 0, 0.3);
        b.reset(0, 50, 0, 0.3);
        PilotInput nan = new PilotInput();
        nan.roll = Double.NaN;
        nan.pitch = Double.NaN;
        nan.yaw = Double.NaN;
        nan.throttle = Double.NaN;
        nan.mode = null;
        SimEnvironment badEnv = new SimEnvironment();
        badEnv.groundDistance = Double.NaN;
        badEnv.windX = Double.NaN;
        badEnv.windY = Double.POSITIVE_INFINITY;
        badEnv.windZ = Double.NaN;
        badEnv.gustIntensity = Double.NaN;
        badEnv.airDensity = Double.NaN;
        badEnv.ambientTempC = Double.NaN;
        badEnv.fluid = null;
        PilotInput zero = new PilotInput();
        SimEnvironment clean = new SimEnvironment();
        StepResult out = new StepResult();
        for (int f = 0; f < 100; f++) {
            a.step(0.01, nan, badEnv, out);
            b.step(0.01, zero, clean, out);
        }
        SimSnapshot sa = new SimSnapshot();
        SimSnapshot sb = new SimSnapshot();
        a.copyStateTo(sa);
        b.copyStateTo(sb);
        assertSnapshotsEqual(sa, sb);
    }

    @Test
    void nanStateRollsBackWithinTheFrame() {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        SimSnapshot before = new SimSnapshot();
        w.sim.copyStateTo(before);
        w.sim.injectFaultNextSubstep();
        w.frame(1.0 / 60.0);
        assertTrue(w.sim.events().contains(SimEventType.SIM_FAULT));
        assertFalse(w.sim.telemetry().armed);
        assertEquals(DisarmReason.SIM_FAULT, w.sim.telemetry().lastDisarmReason);
        assertEquals(1, w.sim.telemetry().faults);
        assertFinite(w.sim);
        assertEquals(before.py, w.sim.body.py, 1e-9);
        assertEquals(0.0, w.sim.body.vx);
        w.frame(1.0 / 60.0);
        assertFalse(w.sim.events().contains(SimEventType.SIM_FAULT));
        assertFinite(w.sim);
    }

    @Test
    void transientNanInForcePathDoesNotPoisonLaterFrames() {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, new SimTuning(), 20.0, StabMode.ANGLE, 1.0);
        w.sim.injectThrustFaultNextSubstep();
        w.frame(1.0 / 60.0);
        assertTrue(w.sim.events().contains(SimEventType.SIM_FAULT));
        assertFalse(w.sim.telemetry().armed);
        assertFinite(w.sim);
        w.input.throttle = 0.0;
        w.input.armSwitch = false;
        w.frame(1.0 / 60.0);
        assertFalse(w.sim.events().contains(SimEventType.SIM_FAULT));
        w.input.armSwitch = true;
        w.frame(1.0 / 60.0);
        assertTrue(w.sim.telemetry().armed, "re-arm after the fault, blockers " + w.sim.telemetry().armBlockers);
        AltitudeHold hold = new AltitudeHold(20.0);
        int faultyFrames = 0;
        for (int f = 0; f < 120; f++) {
            hold.apply(w, 1.0 / 60.0);
            w.frame(1.0 / 60.0);
            if (w.sim.events().contains(SimEventType.SIM_FAULT)) {
                faultyFrames++;
            }
        }
        assertEquals(0, faultyFrames);
        assertTrue(w.sim.telemetry().armed);
        Telemetry t = w.sim.telemetry();
        assertTrue(Double.isFinite(t.gLoad + t.propWash + t.motorThrustN[0] + w.sim.wash.weight[0] + t.gyroDps[0]));
        assertTrue(t.gLoad > 0.5 && t.gLoad < 1.5, "g " + t.gLoad);
        assertFinite(w.sim);
    }

    @Test
    void corruptSnapshotIsRepairedAndNanPositionIsCaught() {
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 1L);
        sim.reset(1.0, 50.0, 2.0, 0.0);
        SimSnapshot s = new SimSnapshot();
        sim.copyStateTo(s);
        s.px = Double.NaN;
        s.vy = Double.POSITIVE_INFINITY;
        s.qw = Double.NaN;
        s.rotorOmega[2] = Double.NaN;
        s.soc = Double.NaN;
        s.lastBusVoltage = Double.NaN;
        sim.copyStateFrom(s);
        assertFinite(sim);
        assertEquals(1, sim.telemetry().faults);
        assertEquals(1.0, sim.body.px, 1e-12);
        StepResult out = new StepResult();
        for (int f = 0; f < 30; f++) {
            sim.step(1.0 / 60.0, new PilotInput(), new SimEnvironment(), out);
            assertTrue(Double.isFinite(out.dx + out.dy + out.dz));
        }
        sim.body.pz = Double.NaN;
        sim.step(1.0 / 60.0, new PilotInput(), new SimEnvironment(), out);
        assertTrue(sim.events().contains(SimEventType.SIM_FAULT));
        assertTrue(Double.isFinite(out.dx + out.dy + out.dz));
        assertFinite(sim);
        MoveReport overflow = new MoveReport();
        overflow.requestedX = -1e308;
        overflow.actualX = 1e308;
        overflow.hitX = true;
        double px = sim.body.px;
        sim.afterMove(overflow);
        assertEquals(px, sim.body.px);
        assertFinite(sim);
    }

    @Test
    void randomScriptStaysFiniteAndNormalized() {
        Random r = new Random(1234);
        SimTuning t = new SimTuning();
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), t, 77L));
        w.wallX = 8.0;
        w.placeOnGround(0.0, 0.0, 0.0);
        w.env.windX = 2.0;
        w.env.gustIntensity = 0.5;
        double omegaMax = w.sim.params().omegaMax;
        double time = 0.0;
        double nextChange = 0.0;
        double maxMah = 0.0;
        StabMode[] modes = StabMode.values();
        while (time < 60.0) {
            if (time >= nextChange) {
                w.input.roll = r.nextDouble() * 2.0 - 1.0;
                w.input.pitch = r.nextDouble() * 2.0 - 1.0;
                w.input.yaw = r.nextDouble() * 2.0 - 1.0;
                w.input.throttle = r.nextDouble();
                w.input.mode = modes[r.nextInt(modes.length)];
                if (r.nextDouble() < 0.2) {
                    w.input.armSwitch = !w.input.armSwitch;
                    if (w.input.armSwitch) {
                        w.input.throttle = 0.0;
                    }
                }
                nextChange = time + 0.2 + r.nextDouble() * 0.8;
            }
            double dt = r.nextDouble() * 0.05;
            w.frame(dt);
            time += dt;
            assertFinite(w.sim);
            RigidBody b = w.sim.body;
            double norm = Math.sqrt(b.qw * b.qw + b.qx * b.qx + b.qy * b.qy + b.qz * b.qz);
            assertEquals(1.0, norm, 1e-9);
            for (int i = 0; i < 4; i++) {
                assertTrue(w.sim.power.omega[i] <= omegaMax && w.sim.power.omega[i] >= 0.0);
            }
            double mah = w.sim.power.mAhUsed;
            assertTrue(mah >= maxMah - 1.0, "mAh dropped from " + maxMah + " to " + mah);
            maxMah = Math.max(maxMah, mah);
        }
        assertTrue(maxMah > 10.0);
    }

    private static void assertSnapshotsEqual(SimSnapshot a, SimSnapshot b) {
        double[] va = {a.px, a.py, a.pz, a.vx, a.vy, a.vz, a.qw, a.qx, a.qy, a.qz, a.lx, a.ly, a.lz, a.soc, a.mAhUsed,
                a.polarizationVoltage, a.lastBatteryCurrent, a.batteryHeatCurrent, a.lastBusVoltage};
        double[] vb = {b.px, b.py, b.pz, b.vx, b.vy, b.vz, b.qw, b.qx, b.qy, b.qz, b.lx, b.ly, b.lz, b.soc, b.mAhUsed,
                b.polarizationVoltage, b.lastBatteryCurrent, b.batteryHeatCurrent, b.lastBusVoltage};
        for (int i = 0; i < va.length; i++) {
            assertEquals(Double.doubleToRawLongBits(va[i]), Double.doubleToRawLongBits(vb[i]), "field " + i);
        }
        for (int i = 0; i < 4; i++) {
            assertEquals(Double.doubleToRawLongBits(a.rotorOmega[i]), Double.doubleToRawLongBits(b.rotorOmega[i]));
            assertEquals(Double.doubleToRawLongBits(a.motorTempC[i]), Double.doubleToRawLongBits(b.motorTempC[i]));
            assertEquals(Double.doubleToRawLongBits(a.escTempC[i]), Double.doubleToRawLongBits(b.escTempC[i]));
        }
        assertEquals(a.armed, b.armed);
        assertEquals(a.sleeping, b.sleeping);
        assertEquals(a.groundedFrames, b.groundedFrames);
        assertEquals(a.rngState, b.rngState);
        assertEquals(a.accumulatorNanos, b.accumulatorNanos);
    }

    @Test
    void identicalInputsGiveBitIdenticalState() {
        SimTuning t = new SimTuning();
        FlatWorld[] worlds = {
                new FlatWorld(new DroneSim(DroneBuild.CINEWHOOP, new FlightControllerConfig(), t, 5L)),
                new FlatWorld(new DroneSim(DroneBuild.CINEWHOOP, new FlightControllerConfig(), t, 5L))};
        Random[] scripts = {new Random(99), new Random(99)};
        for (FlatWorld w : worlds) {
            w.wallX = 6.0;
            w.placeOnGround(0.0, 0.0, 0.0);
            w.env.windZ = 1.5;
            w.env.gustIntensity = 0.4;
            w.arm();
        }
        double time = 0.0;
        while (time < 120.0) {
            double dt = 0.0;
            for (int k = 0; k < 2; k++) {
                Random r = scripts[k];
                FlatWorld w = worlds[k];
                if (r.nextDouble() < 0.02) {
                    w.input.roll = r.nextDouble() - 0.5;
                    w.input.pitch = r.nextDouble() - 0.5;
                    w.input.yaw = r.nextDouble() - 0.5;
                    w.input.throttle = 0.2 + 0.4 * r.nextDouble();
                    w.input.mode = r.nextBoolean() ? StabMode.ANGLE : StabMode.HORIZON;
                }
                dt = 0.004 + r.nextDouble() * 0.03;
                w.frame(dt);
            }
            time += dt;
        }
        SimSnapshot a = new SimSnapshot();
        SimSnapshot b = new SimSnapshot();
        worlds[0].sim.copyStateTo(a);
        worlds[1].sim.copyStateTo(b);
        assertSnapshotsEqual(a, b);
        assertTrue(worlds[0].sim.telemetry().propWash >= 0.0);
    }

    private static DroneSim splitRun(long[] frames) {
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 3L);
        sim.reset(0.0, 200.0, 0.0, 0.0);
        PilotInput in = new PilotInput();
        in.roll = 0.3;
        in.pitch = -0.2;
        in.yaw = 0.1;
        in.throttle = 0.04;
        SimEnvironment env = new SimEnvironment();
        env.windX = 1.0;
        env.gustIntensity = 0.3;
        StepResult out = new StepResult();
        MoveReport report = new MoveReport();
        in.armSwitch = false;
        sim.step(0L, in, env, out);
        in.armSwitch = true;
        sim.step(0L, in, env, out);
        assertTrue(sim.telemetry().armed);
        for (long frame : frames) {
            sim.step(frame, in, env, out);
            report.clear();
            report.requestedX = out.dx;
            report.requestedY = out.dy;
            report.requestedZ = out.dz;
            report.actualX = out.dx;
            report.actualY = out.dy;
            report.actualZ = out.dz;
            sim.afterMove(report);
        }
        return sim;
    }

    private static long[] split(long total, long frame) {
        int n = (int) (total / frame);
        long rest = total - n * frame;
        long[] frames = new long[rest > 0 ? n + 1 : n];
        for (int i = 0; i < n; i++) {
            frames[i] = frame;
        }
        if (rest > 0) {
            frames[n] = rest;
        }
        return frames;
    }

    @Test
    void frameSplitDoesNotChangeTheResult() {
        long total = 3_000_000_000L + 123_457L;
        long[] fps60 = split(total, 16_666_667L);
        long[] fps144 = split(total, 6_944_444L);
        Random r = new Random(5);
        long[] random = new long[2000];
        long sum = 0;
        int count = 0;
        while (sum < total) {
            long f = Math.min(total - sum, (long) (r.nextDouble() * 50_000_000L));
            random[count++] = f;
            sum += f;
        }
        long[] randomFrames = java.util.Arrays.copyOf(random, count);
        SimSnapshot a = new SimSnapshot();
        SimSnapshot b = new SimSnapshot();
        SimSnapshot c = new SimSnapshot();
        splitRun(fps60).copyStateTo(a);
        splitRun(fps144).copyStateTo(b);
        splitRun(randomFrames).copyStateTo(c);
        assertSnapshotsEqual(a, b);
        assertSnapshotsEqual(a, c);
    }

    private static double[] choreography(double fps) {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 50.0, StabMode.ANGLE, 1.0);
        w.sim.teleport(0.0, 50.0, 0.0, false);
        w.x = 0.0;
        w.y = 50.0;
        w.z = 0.0;
        w.input.mode = StabMode.ACRO;
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        double[][] script = {{0.1, 0.0, 0.0}, {-0.1, 0.0, 0.0}, {0.0, 0.1, 0.0}, {0.0, -0.1, 0.0}, {0.0, 0.0, 0.1},
                {0.0, 0.0, -0.1}, {0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}};
        int perSegment = (int) Math.round(fps / 2.0);
        double dt = 1.0 / fps;
        for (double[] step : script) {
            w.input.roll = step[0];
            w.input.pitch = step[1];
            w.input.yaw = step[2];
            for (int f = 0; f < perSegment; f++) {
                w.frame(dt);
            }
        }
        Telemetry t = w.sim.telemetry();
        return new double[]{w.sim.body.px, w.sim.body.py, w.sim.body.pz, t.rollDeg, t.pitchDeg, t.headingDeg};
    }

    @Test
    void choreographyIsFrameRateIndependent() {
        double[] reference = choreography(144.0);
        for (double fps : new double[]{30.0, 60.0}) {
            double[] other = choreography(fps);
            double dx = other[0] - reference[0];
            double dy = other[1] - reference[1];
            double dz = other[2] - reference[2];
            assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) <= 0.05, "fps " + fps + " offset " + Math.sqrt(dx * dx + dy * dy + dz * dz));
            for (int a = 3; a < 6; a++) {
                assertEquals(reference[a], other[a], 1.0, "fps " + fps + " angle " + a);
            }
        }
    }

    @Tag("slow")
    @Test
    void substepCostIsSmall() {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, new SimTuning(), 30.0, StabMode.ANGLE, 1.0);
        AltitudeHold hold = new AltitudeHold(30.0);
        for (int f = 0; f < 2000; f++) {
            hold.apply(w, 1.0 / 144.0);
            w.frame(1.0 / 144.0);
        }
        long start = System.nanoTime();
        for (int f = 0; f < 200; f++) {
            w.input.throttle = w.sim.telemetry().hoverThrottleStick;
            w.frame(50_000_000L);
        }
        double ms = (System.nanoTime() - start) * 1e-6;
        System.out.println("20000 substeps took " + ms + " ms");
        assertTrue(ms < 150.0, "took " + ms);
    }

    @Test
    void steppingDoesNotAllocate() {
        SimTuning t = new SimTuning();
        t.thermalEffects = true;
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), t, 8L));
        w.wallX = 3.0;
        w.placeOnGround(0.0, 0.0, 0.0);
        w.env.windX = 3.0;
        w.env.gustIntensity = 0.6;
        w.arm();
        Random r = new Random(4);
        double[] sticks = new double[40];
        for (int i = 0; i < sticks.length; i++) {
            sticks[i] = r.nextDouble();
        }
        for (int f = 0; f < 2000; f++) {
            w.input.roll = sticks[f % 40] - 0.5;
            w.input.throttle = sticks[(f + 7) % 40];
            w.frame(1.0 / 120.0);
        }
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(thread);
        for (int f = 0; f < 10_000; f++) {
            w.input.roll = sticks[f % 40] - 0.5;
            w.input.pitch = sticks[(f + 3) % 40] - 0.5;
            w.input.throttle = sticks[(f + 7) % 40];
            w.input.mode = (f / 500) % 2 == 0 ? StabMode.ACRO : StabMode.ANGLE;
            w.frame(1.0 / 120.0);
        }
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(allocated / 10_000.0 <= 64.0, "bytes per frame " + allocated / 10_000.0);
    }
}
