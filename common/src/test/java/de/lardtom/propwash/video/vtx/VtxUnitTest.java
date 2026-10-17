package de.lardtom.propwash.video.vtx;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VtxUnitTest {
    private static final VtxTable RACE = VtxTable.of(false, 400, List.of(25, 200, 400, 800, 1600), 3.5, 8.0);
    private static final VtxTable HD = VtxTable.of(true, 700, List.of(25, 100, 400, 700, 1200), 9.0, 23.0);
    private static final double FRAME = 0.05;

    private static VtxUnit unit(VtxTable table, Map<String, Float> tune) {
        VtxUnit unit = new VtxUnit(table, 22.0);
        unit.configure(VtxPowerSettings.selection(tune, table), VtxPowerSettings.lowPower(tune));
        return unit;
    }

    private static void run(VtxUnit unit, double seconds, boolean armed, double airSpeed, double margin) {
        for (double t = 0.0; t < seconds; t += FRAME) {
            unit.update(FRAME, armed, airSpeed, 22.0, margin, true);
        }
    }

    @Test
    void manualRatedLevelBehavesLikeBeforeTheFeature() {
        VtxUnit unit = unit(RACE, VtxPowerSettings.withLevel(Map.of(), 400));
        run(unit, 5.0, true, 10.0, 20.0);
        assertEquals(400, unit.effectiveMw());
        assertEquals(0.0, unit.gainDb());
        assertEquals(0.0, unit.extraW());
        assertFalse(unit.throttled());
        assertFalse(unit.auto());
    }

    @Test
    void emptyTuneRunsAnalogAutoFromTheRadioLink() {
        VtxUnit unit = unit(RACE, Map.of());
        assertTrue(unit.auto());
        run(unit, 5.0, true, 10.0, 20.0);
        assertEquals(400, unit.effectiveMw());
        run(unit, 10.0, true, 10.0, 40.0);
        assertEquals(25, unit.effectiveMw());
        run(unit, 1.0, true, 10.0, 0.0);
        assertEquals(1600, unit.effectiveMw());
    }

    @Test
    void manualLevelAddsCurrentAndRange() {
        VtxUnit unit = unit(RACE, VtxPowerSettings.withLevel(Map.of(), 1600));
        run(unit, 1.0, true, 10.0, 20.0);
        assertEquals(1600, unit.effectiveMw());
        assertEquals(4.3 * 1.2, unit.extraW(), 1.0E-9);
        assertEquals(2.0, unit.reachFactor(), 1.0E-9);
    }

    @Test
    void lowPowerWhenDisarmedLikeBetaflight() {
        VtxUnit on = unit(RACE, VtxPowerSettings.withLowPower(VtxPowerSettings.withLevel(Map.of(), 800), VtxPowerSettings.LowPower.DISARMED));
        run(on, 1.0, false, 0.0, 20.0);
        assertEquals(25, on.effectiveMw());
        assertTrue(on.lowPowerActive());
        run(on, 1.0, true, 5.0, 20.0);
        assertEquals(800, on.effectiveMw());
        run(on, 1.0, false, 0.0, 20.0);
        assertEquals(25, on.effectiveMw());
        VtxUnit until = unit(RACE, VtxPowerSettings.withLowPower(VtxPowerSettings.withLevel(Map.of(), 800), VtxPowerSettings.LowPower.UNTIL_ARMED));
        run(until, 1.0, false, 0.0, 20.0);
        assertEquals(25, until.effectiveMw());
        run(until, 1.0, true, 5.0, 20.0);
        run(until, 1.0, false, 0.0, 20.0);
        assertEquals(800, until.effectiveMw(), "nach dem ersten Scharfschalten volle Leistung, um die Drohne zu finden");
    }

    @Test
    void autoRegulatesByLinkMarginOnDigital() {
        VtxUnit unit = unit(HD, VtxPowerSettings.withAuto(Map.of()));
        assertTrue(unit.auto());
        run(unit, 20.0, true, 10.0, 35.0);
        assertEquals(25, unit.effectiveMw());
        run(unit, 1.0, true, 10.0, 2.0);
        assertEquals(1200, unit.effectiveMw());
    }

    @Test
    void overheatingOnTheGroundStepsThePowerDownAndFlyingRestoresIt() {
        VtxUnit unit = unit(RACE, VtxPowerSettings.withLevel(Map.of(), 1600));
        double seconds = 0.0;
        while (!unit.throttled() && seconds < 600.0) {
            unit.update(FRAME, false, 0.0, 22.0, 20.0, true);
            seconds += FRAME;
        }
        assertTrue(unit.throttled(), "kein Hitzeschutz nach " + seconds + " s");
        assertTrue(seconds > 45.0 && seconds < 150.0, "Hitzeschutz nach " + seconds + " s");
        assertEquals(800, unit.effectiveMw());
        assertTrue(unit.hot());
        run(unit, VtxUnit.PROTECT_STEP_S * 0.5, false, 0.0, 20.0);
        assertEquals(800, unit.effectiveMw(), "Hitzeschutz regelt höchstens eine Stufe je " + VtxUnit.PROTECT_STEP_S + " s herunter");
        run(unit, 120.0, false, 0.0, 20.0);
        assertTrue(unit.effectiveMw() < 1600 && unit.tempC() < VtxHeat.LIMIT_C + 3.0, "Temperatur " + unit.tempC());
        run(unit, 120.0, true, 15.0, 20.0);
        assertFalse(unit.throttled());
        assertEquals(1600, unit.effectiveMw());
        assertFalse(unit.hot());
    }

    @Test
    void withoutHardwareLimitsThereIsNoProtection() {
        VtxUnit unit = unit(RACE, VtxPowerSettings.withLevel(Map.of(), 1600));
        for (int i = 0; i < 6000; i++) {
            unit.update(FRAME, false, 0.0, 22.0, 20.0, false);
        }
        assertEquals(1600, unit.effectiveMw());
        assertFalse(unit.throttled());
        assertFalse(unit.hot());
        assertTrue(unit.tempC() > VtxHeat.LIMIT_C);
    }

    @Test
    void switchingToAutoStartsFromTheCurrentLevel() {
        VtxUnit unit = unit(HD, VtxPowerSettings.withLevel(Map.of(), 1200));
        run(unit, 1.0, true, 10.0, 0.0);
        unit.configure(VtxPowerSettings.selection(VtxPowerSettings.withAuto(Map.of()), HD), VtxPowerSettings.LowPower.OFF);
        unit.update(FRAME, true, 10.0, 22.0, 0.0, true);
        assertEquals(1200, unit.effectiveMw());
    }
}
