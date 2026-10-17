package de.lardtom.propwash.osd;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StepResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoltageDisplayTest {
    static final long FRAME = 16_000_000L;

    static OsdData data(double pack, int cells) {
        OsdData d = new OsdData();
        d.hasSim = true;
        d.packVoltage = pack;
        d.cellVoltage = pack / cells;
        d.cells = cells;
        d.warnCellVoltage = 3.5;
        d.criticalCellVoltage = 3.3;
        return d;
    }

    @Test
    void firstValuePassesThenStepsAreSmoothedLikeBetaflight() {
        VoltageDisplay v = new VoltageDisplay();
        OsdData d = data(24.0, 6);
        v.apply(d, 0.016);
        assertEquals(24.0, d.packVoltage, 1e-12);
        double t = 0.0;
        double shown = 24.0;
        while (t < VoltageDisplay.TAU_SECONDS - 1e-9) {
            d = data(18.0, 6);
            v.apply(d, 0.016);
            shown = d.packVoltage;
            t += 0.016;
        }
        assertEquals(24.0 - 6.0 * (1.0 - Math.exp(-t / VoltageDisplay.TAU_SECONDS)), shown, 1e-9);
        assertEquals(shown / 6.0, d.cellVoltage, 1e-9);
        d = data(18.0, 6);
        v.apply(d, 0.0);
        assertEquals(shown, d.packVoltage, 1e-12);
    }

    @Test
    void alertFollowsThresholdsWithHysteresis() {
        assertEquals(VoltageDisplay.NONE, VoltageDisplay.next(VoltageDisplay.NONE, 3.6, 3.5, 3.3));
        assertEquals(VoltageDisplay.WARNING, VoltageDisplay.next(VoltageDisplay.NONE, 3.45, 3.5, 3.3));
        assertEquals(VoltageDisplay.CRITICAL, VoltageDisplay.next(VoltageDisplay.NONE, 3.2, 3.5, 3.3));
        assertEquals(VoltageDisplay.CRITICAL, VoltageDisplay.next(VoltageDisplay.CRITICAL, 3.31, 3.5, 3.3));
        assertEquals(VoltageDisplay.WARNING, VoltageDisplay.next(VoltageDisplay.CRITICAL, 3.34, 3.5, 3.3));
        assertEquals(VoltageDisplay.WARNING, VoltageDisplay.next(VoltageDisplay.WARNING, 3.52, 3.5, 3.3));
        assertEquals(VoltageDisplay.NONE, VoltageDisplay.next(VoltageDisplay.NONE, 3.52, 3.5, 3.3));
        assertEquals(VoltageDisplay.NONE, VoltageDisplay.next(VoltageDisplay.WARNING, 3.54, 3.5, 3.3));
        assertEquals(VoltageDisplay.NONE, VoltageDisplay.next(VoltageDisplay.WARNING, 3.2, Double.NaN, Double.NaN));
    }

    @Test
    void withoutSimDataNothingIsFilteredOrFlagged() {
        VoltageDisplay v = new VoltageDisplay();
        OsdData d = data(20.0, 6);
        v.apply(d, 0.016);
        d.hasSim = false;
        d.packVoltage = 25.0;
        d.voltageAlert = VoltageDisplay.CRITICAL;
        v.apply(d, 0.016);
        assertEquals(25.0, d.packVoltage, 1e-12);
        assertEquals(VoltageDisplay.NONE, d.voltageAlert);
        OsdData fresh = data(25.0, 6);
        v.apply(fresh, 0.016);
        assertEquals(25.0, fresh.packVoltage, 1e-12);
    }

    record Punch(int worst, int afterwards, double lowest, double before) {
    }

    static Punch punch(double charge, boolean infinite) {
        SimTuning tuning = new SimTuning();
        tuning.infiniteBattery = infinite;
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), tuning, 7L);
        PilotInput input = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        StepResult result = new StepResult();
        sim.reset(0.0, 200.0, 0.0, 0.0);
        sim.setBatteryCharge(charge);
        sim.step(0L, input, env, result);
        input.armSwitch = true;
        sim.step(0L, input, env, result);
        VoltageDisplay v = new VoltageDisplay();
        OsdData d = new OsdData();
        input.throttle = 0.25;
        for (int k = 0; k < 200; k++) {
            sim.step(FRAME, input, env, result);
            d.applySim(sim);
            v.apply(d, FRAME / 1.0e9);
        }
        double before = d.cellVoltage;
        int worst = d.voltageAlert;
        double lowest = before;
        input.throttle = 1.0;
        for (int k = 0; k < 63; k++) {
            sim.step(FRAME, input, env, result);
            d.applySim(sim);
            v.apply(d, FRAME / 1.0e9);
            worst = Math.max(worst, d.voltageAlert);
            lowest = Math.min(lowest, d.cellVoltage);
        }
        input.throttle = 0.25;
        for (int k = 0; k < 190; k++) {
            sim.step(FRAME, input, env, result);
            d.applySim(sim);
            v.apply(d, FRAME / 1.0e9);
        }
        return new Punch(worst, d.voltageAlert, lowest, before);
    }

    @Test
    void endOfPackPunchFlashesTheVoltageAndRecovers() {
        Punch full = punch(1.0, false);
        assertEquals(VoltageDisplay.NONE, full.worst(), "voller Akku: " + full);
        Punch low = punch(0.1, false);
        assertEquals(VoltageDisplay.CRITICAL, low.worst(), "10 %: " + low);
        assertEquals(VoltageDisplay.NONE, low.afterwards(), "10 %, nach 3 s Schweben: " + low);
        Punch end = punch(0.06, false);
        assertEquals(VoltageDisplay.CRITICAL, end.worst(), "Ende des Packs: " + end);
        assertTrue(end.before() - end.lowest() > 0.5, "Einbruch im OSD je Zelle " + (end.before() - end.lowest()));
        assertTrue(end.before() - end.lowest() > 1.3 * (full.before() - full.lowest()), "voll " + full + ", Ende " + end);
        Punch endless = punch(0.07, true);
        assertEquals(VoltageDisplay.NONE, endless.worst(), "unendlicher Akku: " + endless);
    }
}
