package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.interact.CargoMass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirframeSwapTest {
    private static final DroneBuild BUILD = DroneBuild.FREESTYLE.withAccessory(AccessoryType.GRIPPER);

    @Test
    void heavierAirframeInFlightKeepsTheStateAndRaisesTheHoverThrottle() {
        DroneSim sim = new DroneSim(BUILD, new FlightControllerConfig(), new SimTuning(), 11L);
        sim.reset(0.0, 70.0, 0.0, 0.0);
        SimSnapshot before = new SimSnapshot();
        sim.copyStateTo(before);
        double hover = sim.telemetry().hoverThrottleStick;
        AirframeSpec laden = AirframeSpec.of(CargoMass.withPayload(BUILD, 300.0));
        assertTrue(sim.setAirframe(laden));
        assertSame(laden, sim.params());
        assertFalse(sim.setAirframe(laden), "gleiche Werte: nichts zu tun");
        SimSnapshot after = new SimSnapshot();
        sim.copyStateTo(after);
        assertEquals(before.px, after.px, 0.0);
        assertEquals(before.py, after.py, 0.0);
        assertEquals(before.vy, after.vy, 0.0);
        assertEquals(before.soc, after.soc, 0.0);
        assertTrue(sim.telemetry().hoverThrottleStick > hover + 0.02, hover + " -> " + sim.telemetry().hoverThrottleStick);
        assertTrue(sim.setAirframe(AirframeSpec.of(BUILD)));
        assertEquals(hover, sim.telemetry().hoverThrottleStick, 1.0E-12);
    }

    @Test
    void cargoMakesTheDroneSinkAtTheOldHoverThrottle() {
        double light = sink(BUILD);
        double heavy = sink(CargoMass.withPayload(BUILD, 400.0));
        assertTrue(heavy < light - 0.3, "mit 400 g Fracht tiefer: " + light + " vs " + heavy);
    }

    private static double sink(DroneBuild flown) {
        DroneSim sim = new DroneSim(BUILD, new FlightControllerConfig(), new SimTuning(), 5L);
        sim.reset(0.0, 70.0, 0.0, 0.0);
        PilotInput input = new PilotInput();
        input.mode = StabMode.ANGLE;
        input.linkOk = true;
        input.throttle = 0.0;
        SimEnvironment env = new SimEnvironment();
        StepResult out = new StepResult();
        sim.step(0L, input, env, out);
        input.armSwitch = true;
        sim.step(0L, input, env, out);
        assertTrue(sim.telemetry().armed, "scharf");
        for (int i = 0; i < 60; i++) {
            sim.step(1.0 / 60.0, input, env, out);
        }
        input.throttle = sim.telemetry().hoverThrottleStick;
        sim.setAirframe(AirframeSpec.of(flown));
        for (int i = 0; i < 120; i++) {
            sim.step(1.0 / 60.0, input, env, out);
        }
        SimSnapshot s = new SimSnapshot();
        sim.copyStateTo(s);
        return s.py;
    }
}
