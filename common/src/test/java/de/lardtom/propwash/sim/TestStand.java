package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestStand {
    final DroneSim sim;
    final PilotInput input = new PilotInput();
    final SimEnvironment env = new SimEnvironment();
    final StepResult result = new StepResult();

    TestStand(DroneBuild build, SimTuning tuning) {
        this.sim = new DroneSim(build, new FlightControllerConfig(), tuning, 42L);
        this.sim.reset(0.0, 50.0, 0.0, 0.0);
        this.sim.setPoseLocked(true);
        this.input.armSwitch = false;
        this.sim.step(0L, this.input, this.env, this.result);
        this.input.armSwitch = true;
        this.sim.step(0L, this.input, this.env, this.result);
        assertTrue(this.sim.telemetry().armed);
    }

    static TestStand of(DroneBuild build) {
        SimTuning t = Tests.quietTuning();
        t.infiniteBattery = true;
        return new TestStand(build, t);
    }

    void duty(double d) {
        this.sim.setDutyOverride(d, d, d, d);
    }

    void spinUp() {
        OperatingPoints.Point hover = new OperatingPoints.Point();
        OperatingPoints.hover(this.sim.params(), this.sim.power.soc, false, 0.0, true, hover);
        this.duty(hover.duty);
        this.substeps(1000);
    }

    void substeps(int n) {
        for (int k = 0; k < n; k++) {
            this.sim.step(DroneSim.SUBSTEP_NANOS, this.input, this.env, this.result);
        }
    }

    double omega(int i) {
        return this.sim.power.omega[i];
    }

    double rpm(int i) {
        return this.sim.power.omega[i] * 60.0 / (2.0 * Math.PI);
    }
}
