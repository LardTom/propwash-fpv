package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermalNeutralityTest {
    static final double AMBIENT_C = 25.0;
    static final double SIM_CHECK_MARGIN_C = 20.0;

    record Climb(double maxTemp, int derateEvents, int wearEvents, double seconds) {
    }

    static Climb fullThrottleClimb(DroneBuild build) {
        FlatWorld w = FlatWorld.of(build, new SimTuning());
        w.env.ambientTempC = AMBIENT_C;
        w.place(0.0, 2.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        w.input.throttle = 1.0;
        double max = AMBIENT_C;
        int derate = 0;
        int wear = 0;
        double dt = 1.0 / 30.0;
        int frames = 0;
        while (frames < 30 * 60 * 30 && w.sim.telemetry().armed && w.sim.telemetry().chargeFraction > 0.0) {
            w.frame(dt);
            frames++;
            derate += w.sim.events().count(SimEventType.MOTOR_DERATE);
            wear += w.sim.events().count(SimEventType.MOTOR_THERMAL_WEAR);
            for (double t : w.sim.telemetry().motorTempC) {
                max = Math.max(max, t);
            }
        }
        return new Climb(max, derate, wear, frames * dt);
    }

    @Test
    void builtinDronesNeverReachTheDerateThresholdWhileHovering() {
        for (DroneBuild build : BuiltinRegressionTest.buildableCombinations()) {
            AirframeSpec p = AirframeSpec.of(build);
            double hover = OperatingPoints.hoverMotorTemp(p, AMBIENT_C);
            assertTrue(hover < p.motorDerateStartC, p.buildKey + " schwebt bei " + hover + " °C");
        }
    }

    @Test
    void builtinDronesNeverDerateInAFullThrottleClimbUntilEmpty() {
        Set<DroneBuild> candidates = new LinkedHashSet<>(DroneBuild.PRESETS);
        for (DroneBuild build : BuiltinRegressionTest.buildableCombinations()) {
            AirframeSpec p = AirframeSpec.of(build);
            if (OperatingPoints.fullThrottleMotorTemp(p, AMBIENT_C) > p.motorDerateStartC - SIM_CHECK_MARGIN_C) {
                candidates.add(build);
            }
        }
        List<String> hot = new ArrayList<>();
        for (DroneBuild build : candidates) {
            AirframeSpec p = AirframeSpec.of(build);
            Climb climb = fullThrottleClimb(build);
            assertTrue(climb.seconds > 5.0, p.buildKey + " flog nur " + climb.seconds + " s");
            if (climb.maxTemp >= p.motorDerateStartC || climb.derateEvents > 0 || climb.wearEvents > 0) {
                hot.add(p.buildKey + " " + climb);
            }
        }
        assertEquals(List.of(), hot);
        assertTrue(candidates.size() > DroneBuild.PRESETS.size(), "die heißesten Kombinationen werden mitgeflogen");
    }

    @Test
    void quasiStaticEstimateMatchesTheSimulation() {
        for (DroneBuild build : List.of(DroneBuild.CINEWHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER)) {
            AirframeSpec p = AirframeSpec.of(build);
            SimTuning tuning = new SimTuning();
            DroneSim sim = new DroneSim(build, new FlightControllerConfig(), tuning, 7L);
            PilotInput input = new PilotInput();
            SimEnvironment env = new SimEnvironment();
            env.ambientTempC = AMBIENT_C;
            StepResult result = new StepResult();
            sim.reset(0.0, 50.0, 0.0, 0.0);
            sim.setPoseLocked(true);
            sim.step(0L, input, env, result);
            input.armSwitch = true;
            sim.step(0L, input, env, result);
            sim.setDutyOverride(1.0, 1.0, 1.0, 1.0);
            double max = AMBIENT_C;
            for (int k = 0; k < 3000 && sim.telemetry().armed && sim.telemetry().chargeFraction > 0.0; k++) {
                sim.step(100_000_000L, input, env, result);
                max = Math.max(max, sim.telemetry().motorTempC[0]);
            }
            double estimate = OperatingPoints.fullThrottleMotorTemp(p, AMBIENT_C);
            assertEquals(max, estimate, 5.0, p.buildKey);
        }
    }
}
