package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryWarningTest {
    static final long FRAME = 100_000_000L;
    static final Set<SimEventType> WARNINGS = EnumSet.of(SimEventType.BATTERY_LOW, SimEventType.BATTERY_CRITICAL, SimEventType.PACK_DEPLETED);

    static final class Bench {
        final DroneSim sim;
        final PilotInput input = new PilotInput();
        final SimEnvironment env = new SimEnvironment();
        final StepResult result = new StepResult();
        final List<SimEventType> events = new ArrayList<>();
        final EnumSet<BatteryState> states = EnumSet.noneOf(BatteryState.class);
        double minCell = Double.MAX_VALUE;

        Bench(SimTuning tuning, double charge) {
            this.sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), tuning, 7L);
            this.sim.reset(0.0, 50.0, 0.0, 0.0);
            this.sim.setPoseLocked(true);
            this.sim.setBatteryCharge(charge);
            this.sim.step(0L, this.input, this.env, this.result);
            this.input.armSwitch = true;
            this.sim.step(0L, this.input, this.env, this.result);
            assertTrue(this.sim.telemetry().armed);
        }

        Bench run(double duty, double seconds) {
            this.sim.setDutyOverride(duty, duty, duty, duty);
            for (int k = 0; k < Math.round(seconds * 10.0); k++) {
                this.sim.step(FRAME, this.input, this.env, this.result);
                Telemetry t = this.sim.telemetry();
                this.minCell = Math.min(this.minCell, t.batteryCellVoltage);
                this.states.add(t.batteryState);
                EventQueue queue = this.sim.events();
                for (int i = 0; i < queue.size(); i++) {
                    if (WARNINGS.contains(queue.type(i))) {
                        this.events.add(queue.type(i));
                    }
                }
            }
            return this;
        }

        BatteryState state() {
            return this.sim.telemetry().batteryState;
        }
    }

    static SimTuning tuning(boolean infinite) {
        SimTuning tuning = new SimTuning();
        tuning.infiniteBattery = infinite;
        return tuning;
    }

    @Test
    void punchOnAFullPackWarnsOnlyWhileTheVoltageSags() {
        Bench bench = new Bench(tuning(false), 1.0).run(1.0, 6.0);
        assertTrue(bench.minCell < 3.5, "Testaufbau sackt nicht unter die Warnschwelle: " + bench.minCell);
        assertEquals(List.of(SimEventType.BATTERY_LOW), bench.events);
        assertEquals(BatteryState.LOW, bench.state());
        bench.run(0.1, 6.0);
        assertEquals(BatteryState.OK, bench.state(), "Warnung bleibt nach dem Schubstoß stehen");
        assertTrue(bench.sim.telemetry().batteryCharge > 0.85);
    }

    @Test
    void halfPackPunchIsCriticalOnlyDuringThePunch() {
        Bench bench = new Bench(tuning(false), 0.6).run(1.0, 6.0);
        assertTrue(bench.states.contains(BatteryState.CRITICAL));
        bench.run(0.1, 6.0);
        assertEquals(BatteryState.OK, bench.state());
        bench.run(1.0, 6.0);
        assertEquals(List.of(SimEventType.BATTERY_LOW, SimEventType.BATTERY_CRITICAL, SimEventType.BATTERY_LOW,
                SimEventType.BATTERY_CRITICAL), bench.events, "jede neue Unterschreitung meldet sich wieder");
    }

    @Test
    void drainedPackLatchesLow() {
        Bench bench = new Bench(tuning(false), 0.04).run(0.5, 5.0);
        assertTrue(bench.events.contains(SimEventType.BATTERY_LOW), "leerer Akku warnt nicht: " + bench.events);
        bench.run(0.05, 6.0);
        assertTrue(bench.state().ordinal() >= BatteryState.LOW.ordinal(), "Warnung eines leeren Akkus verschwindet: " + bench.state());
        assertEquals(1, bench.events.stream().filter(e -> e == SimEventType.BATTERY_LOW).count());
    }

    @Test
    void infiniteBatteryNeverWarnsButStillSags() {
        Bench bench = new Bench(tuning(true), 1.0).run(1.0, 8.0).run(0.1, 2.0).run(1.0, 8.0);
        assertTrue(bench.minCell < 3.5, "Spannungseinbruch fehlt mit unendlichem Akku: " + bench.minCell);
        assertTrue(bench.events.isEmpty(), "Warnungen mit unendlichem Akku: " + bench.events);
        assertEquals(EnumSet.of(BatteryState.OK), bench.states);
        assertEquals(1.0, bench.sim.telemetry().batteryCharge, 1e-12);
    }

    @Test
    void infiniteBatteryOnAnAlmostEmptyPackStaysQuiet() {
        Bench bench = new Bench(tuning(true), 0.04).run(1.0, 6.0).run(0.1, 2.0);
        assertTrue(bench.events.isEmpty(), "Warnungen mit unendlichem Akku: " + bench.events);
        assertEquals(EnumSet.of(BatteryState.OK), bench.states);
    }

    @Test
    void switchingInfiniteBatteryOnClearsALatchedWarning() {
        Bench bench = new Bench(tuning(false), 0.04).run(0.5, 5.0);
        assertTrue(bench.state().ordinal() >= BatteryState.LOW.ordinal());
        bench.sim.setTuning(tuning(true));
        bench.events.clear();
        bench.states.clear();
        bench.run(1.0, 3.0);
        assertEquals(EnumSet.of(BatteryState.OK), bench.states);
        assertTrue(bench.events.isEmpty());
        bench.sim.setTuning(tuning(false));
        bench.run(0.5, 5.0);
        assertFalse(bench.events.isEmpty(), "nach dem Ausschalten warnt der fast leere Akku wieder");
    }
}
