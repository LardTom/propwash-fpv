package de.lardtom.propwash.sim.review;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FilterSettings;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Fluid;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.PidAutoTune;
import de.lardtom.propwash.sim.PidLoopRate;
import de.lardtom.propwash.sim.PidProfile;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimEventType;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StepResult;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericalRobustnessReviewTest {
    static final class World {
        final DroneSim sim;
        final PilotInput input = new PilotInput();
        final SimEnvironment env = new SimEnvironment();
        final StepResult out = new StepResult();
        final MoveReport report = new MoveReport();
        final double foot;
        double ceilingY = Double.POSITIVE_INFINITY;
        double x;
        double y;
        double z;

        World(DroneBuild build, SimTuning tuning) {
            this.sim = new DroneSim(build, new FlightControllerConfig(), tuning, 42L);
            this.foot = this.sim.params().hitboxFootOffset;
        }

        void place(double py) {
            this.sim.reset(0.0, py, 0.0, 0.0);
            this.x = 0.0;
            this.y = py;
            this.z = 0.0;
        }

        void arm() {
            this.input.throttle = 0.0;
            this.input.armSwitch = false;
            this.frame(0L);
            this.input.armSwitch = true;
            this.frame(0L);
        }

        void frame(long nanos) {
            this.env.groundDistance = Math.max(0.0, this.y);
            this.sim.step(nanos, this.input, this.env, this.out);
            double dy = this.out.dy;
            double ay = dy;
            if (dy < 0.0) {
                ay = Math.max(dy, Math.min(0.0, -(this.y + this.foot)));
            } else if (dy > 0.0 && this.y + dy > this.ceilingY) {
                ay = Math.max(0.0, this.ceilingY - this.y);
            }
            this.x += this.out.dx;
            this.y += ay;
            this.z += this.out.dz;
            this.report.clear();
            this.report.requestedX = this.out.dx;
            this.report.requestedY = dy;
            this.report.requestedZ = this.out.dz;
            this.report.actualX = this.out.dx;
            this.report.actualY = ay;
            this.report.actualZ = this.out.dz;
            this.report.hitY = Math.abs(ay - dy) > 1e-7;
            this.sim.afterMove(this.report);
        }
    }

    private static SimTuning quiet() {
        SimTuning t = new SimTuning();
        t.gyroNoiseDps = 0.0;
        t.gyroVibrationDps = 0.0;
        return t;
    }

    @Test
    void transientNanInForcePathDoesNotPoisonTheSimForever() {
        World w = new World(DroneBuild.FREESTYLE, new SimTuning());
        w.place(20.0);
        w.arm();
        w.input.mode = StabMode.ANGLE;
        for (int f = 0; f < 60; f++) {
            w.input.throttle = w.sim.telemetry().hoverThrottleStick;
            w.frame(16_666_667L);
        }
        w.sim.tuning().turbulenceScale = Double.NaN;
        w.frame(16_666_667L);
        assertTrue(!w.sim.events().contains(SimEventType.SIM_FAULT), "editing the returned tuning copy reached the sim");
        SimTuning bad = new SimTuning();
        bad.turbulenceScale = Double.NaN;
        w.sim.setTuning(bad);
        w.frame(16_666_667L);
        assertTrue(!w.sim.events().contains(SimEventType.SIM_FAULT), "setTuning did not sanitize NaN");
        w.sim.setTuning(new SimTuning());
        int faultyFrames = 0;
        for (int f = 0; f < 120; f++) {
            w.frame(16_666_667L);
            if (w.sim.events().contains(SimEventType.SIM_FAULT)) {
                faultyFrames++;
            }
        }
        assertEquals(0, faultyFrames, "SIM_FAULT keeps firing after the cause was removed; telemetry gLoad="
                + w.sim.telemetry().gLoad + " propWash=" + w.sim.telemetry().propWash);
    }

    @Test
    void nanPositionIsDetected() {
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 1L);
        sim.reset(0.0, 50.0, 0.0, 0.0);
        SimSnapshot s = new SimSnapshot();
        sim.copyStateTo(s);
        s.px = Double.NaN;
        sim.copyStateFrom(s);
        StepResult out = new StepResult();
        for (int f = 0; f < 60; f++) {
            sim.step(1.0 / 60.0, new PilotInput(), new SimEnvironment(), out);
        }
        Vector3d p = new Vector3d();
        sim.getPosition(p);
        assertTrue(Double.isFinite(out.dx) && Double.isFinite(p.x), "dx=" + out.dx + " px=" + p.x + " faults="
                + sim.telemetry().faults);
    }

    private static int[] pushIntoCeiling(double fps, double crashScale) {
        return pushIntoCeiling(DroneBuild.FREESTYLE, fps, crashScale);
    }

    private static int[] pushIntoCeiling(DroneBuild build, double fps, double crashScale) {
        SimTuning t = quiet();
        t.crashSpeedScale = crashScale;
        World w = new World(build, t);
        w.place(10.0);
        w.ceilingY = 10.02;
        w.arm();
        w.input.mode = StabMode.ANGLE;
        w.input.throttle = 1.0;
        long nanos = Math.round(1e9 / fps);
        int impacts = 0;
        int crashes = 0;
        int propStrikes = 0;
        for (int f = 0; f < (int) (3.0 * fps); f++) {
            w.frame(nanos);
            impacts += w.sim.events().count(SimEventType.BUMP) + w.sim.events().count(SimEventType.HARD_IMPACT);
            crashes += w.sim.events().count(SimEventType.CRASH);
            propStrikes += w.sim.events().count(SimEventType.PROP_STRIKE);
        }
        return new int[]{impacts, crashes, w.sim.telemetry().armed ? 1 : 0, propStrikes};
    }

    @Test
    void sustainedContactIsNotReclassifiedAsImpactEveryFrame() {
        int[] fast = pushIntoCeiling(144.0, 1.0);
        int[] slow = pushIntoCeiling(20.0, 1.0);
        int[] fastStrict = pushIntoCeiling(144.0, 0.7);
        int[] slowStrict = pushIntoCeiling(20.0, 0.7);
        String msg = "impacts in 3 s: 20 fps=" + slow[0] + " 144 fps=" + fast[0] + "; crashSpeedScale 0.7: 20 fps crashes="
                + slowStrict[1] + " armed=" + slowStrict[2] + ", 144 fps crashes=" + fastStrict[1] + " armed=" + fastStrict[2];
        assertTrue(slow[0] <= 2 && slowStrict[2] == 1, msg);
        assertEquals(0, fast[3] + slow[3], msg);
    }

    @Test
    void raceDroneWithTheBatteryUnderTheFrameTouchesACeilingWithItsProps() {
        int[] race = pushIntoCeiling(DroneBuild.RACER, 144.0, 1.0);
        int[] freestyle = pushIntoCeiling(DroneBuild.FREESTYLE, 144.0, 1.0);
        assertTrue(race[3] > 0, "Prop-Treffer an der Decke: " + race[3]);
        assertEquals(0, freestyle[3], "Freestyle: der Akku oben hält die Props von der Decke fern");
    }

    @Test
    void brownoutIsNotReportedRepeatedlyWhileDisarmed() {
        DroneSim sim = new DroneSim(DroneBuild.WHOOP, new FlightControllerConfig(), new SimTuning(), 1L);
        sim.reset(0.0, 0.02, 0.0, 0.0);
        sim.setBatteryCharge(0.0);
        SimEnvironment env = new SimEnvironment();
        env.groundDistance = 0.0;
        StepResult out = new StepResult();
        PilotInput in = new PilotInput();
        int brownouts = 0;
        for (int f = 0; f < 20 * 60 * 10; f++) {
            sim.step(0.05, in, env, out);
            brownouts += sim.events().count(SimEventType.BROWNOUT);
        }
        assertTrue(brownouts <= 1, "BROWNOUT events while disarmed in 10 min: " + brownouts);
    }

    @Test
    void editingTheReturnedConfigCannotInjectNaN() {
        World w = new World(DroneBuild.FREESTYLE, quiet());
        w.place(20.0);
        w.arm();
        w.input.mode = StabMode.HORIZON;
        w.sim.config().pid.horizonStickTransitionPercent = 0.0;
        int faults = 0;
        for (int f = 0; f < 30; f++) {
            w.input.throttle = w.sim.telemetry().hoverThrottleStick;
            w.frame(16_666_667L);
            faults += w.sim.events().count(SimEventType.SIM_FAULT);
        }
        assertEquals(0, faults, "setpoint=" + w.sim.telemetry().setpointDps[0]);
    }

    @Test
    void restitutionNeverAddsEnergy() {
        SimTuning t = quiet();
        t.restitutionScale = 4.0;
        World w = new World(DroneBuild.CINEWHOOP, t);
        w.place(1.0 - w.foot);
        double maxBottomLate = 0.0;
        for (int f = 0; f < 60 * 30; f++) {
            w.frame(16_666_667L);
            if (f > 60 * 20) {
                maxBottomLate = Math.max(maxBottomLate, w.y + w.foot);
            }
        }
        assertTrue(maxBottomLate < 1.0, "still bouncing to " + maxBottomLate + " m after 20 s (dropped from 1 m)");
    }

    @Test
    void waterDragNeverReversesVelocity() {
        SimTuning t = quiet();
        t.dragScale = 2.0;
        DroneSim sim = new DroneSim(DroneBuild.WHOOP, new FlightControllerConfig(), t, 1L);
        sim.reset(0.0, 10.0, 0.0, 0.0);
        SimSnapshot s = new SimSnapshot();
        sim.copyStateTo(s);
        s.vy = -20.0;
        sim.copyStateFrom(s);
        SimEnvironment env = new SimEnvironment();
        env.fluid = Fluid.WATER;
        StepResult out = new StepResult();
        Vector3d v = new Vector3d();
        double maxUp = 0.0;
        for (int k = 0; k < 20; k++) {
            sim.step(500_000L, new PilotInput(), env, out);
            sim.getVelocity(v);
            maxUp = Math.max(maxUp, v.y);
        }
        assertTrue(maxUp <= 0.0, "entering water downward at 20 m/s produced upward velocity " + maxUp + " m/s");
    }

    @Test
    void derivedPidsFollowLoopRateChanges() {
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 1L);
        FlightControllerConfig c = sim.config().copy();
        c.filters.pidLoop = PidLoopRate.HZ_500;
        c.filters.gyroLpfHz = 100.0;
        sim.setConfig(c);
        FilterSettings f = new FilterSettings();
        f.pidLoop = PidLoopRate.HZ_500;
        f.gyroLpfHz = 100.0;
        PidProfile expected = PidAutoTune.derive(sim.params(), f, new PidProfile());
        assertEquals(expected.roll.p, sim.config().pid.roll.p, "roll P after switching to 500 Hz / gyro 100 Hz");
    }
}
