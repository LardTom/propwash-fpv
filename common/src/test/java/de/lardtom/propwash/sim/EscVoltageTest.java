package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.StackType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscVoltageTest {
    private static DroneBuild withBattery(StackType stack, BatteryType battery) {
        FrameType frame = AddonParts.part(AddonParts.fiveInch("wide_fit")
                .batteryFit(new de.lardtom.propwash.api.part.BatteryFitRule(4, 8, 400.0, 6, 0)).build());
        DroneBuild fs = DroneBuild.FREESTYLE;
        return new DroneBuild(frame, stack, fs.motor(), fs.prop(), fs.video(), battery);
    }

    private static int count(FlatWorld w, SimEventType type) {
        return w.sim.events().count(type);
    }

    @Test
    void tooManyCellsBurnTheEscOnArming() {
        DroneBuild build = withBattery(StackType.STACK_30, AddonParts.lipo(8, 1300, 280.0, 0.0055, 120));
        assertTrue(build.buildable(), build.problems().toString());
        AirframeSpec p = AirframeSpec.of(build);
        assertTrue(p.escOvervoltage);
        assertEquals(6, p.escMaxCells);
        assertTrue(BuildAnalysis.of(build).warnings().contains(BuildWarning.ESC_OVERVOLTAGE));
        FlatWorld w = FlatWorld.of(build);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.input.armSwitch = false;
        w.frame(0L);
        assertFalse(w.sim.telemetry().escBurned);
        w.input.armSwitch = true;
        w.frame(0L);
        assertFalse(w.sim.telemetry().armed);
        assertTrue(w.sim.telemetry().escBurned);
        assertEquals(1, count(w, SimEventType.ESC_BURNED));
        assertEquals(8.0, w.sim.events().value(0));
        assertTrue(ArmBlocker.HARDWARE_FAULT.isSet(w.sim.telemetry().armBlockers));
        w.input.armSwitch = false;
        w.frame(0L);
        w.input.armSwitch = true;
        w.frame(0L);
        assertFalse(w.sim.telemetry().armed);
        assertEquals(0, count(w, SimEventType.ESC_BURNED), "brennt nur einmal durch");
        assertEquals(1, count(w, SimEventType.ARM_BLOCKED));
    }

    @Test
    void cellsInsideTheRangeAndTheSwitchKeepTheEscAlive() {
        DroneBuild fine = withBattery(AddonParts.stack3to8(), AddonParts.lipo(8, 1300, 280.0, 0.0055, 120));
        assertFalse(AirframeSpec.of(fine).escOvervoltage);
        FlatWorld w = FlatWorld.of(fine);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.arm();
        assertTrue(w.sim.telemetry().armed);
        assertFalse(w.sim.telemetry().escBurned);

        DroneBuild hot = withBattery(StackType.STACK_30, AddonParts.lipo(8, 1300, 280.0, 0.0055, 120));
        SimTuning off = Tests.quietTuning();
        off.escOvervoltage = false;
        FlatWorld free = FlatWorld.of(hot, off);
        free.placeOnGround(0.0, 0.0, 0.0);
        free.arm();
        assertTrue(free.sim.telemetry().armed);
        assertFalse(free.sim.telemetry().escBurned);
    }

    @Test
    void newBuildClearsTheBurntEsc() {
        DroneBuild hot = withBattery(StackType.STACK_30, AddonParts.lipo(8, 1300, 280.0, 0.0055, 120));
        FlatWorld w = FlatWorld.of(hot);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.arm();
        assertTrue(w.sim.telemetry().escBurned);
        assertTrue(w.sim.setBuild(withBattery(StackType.STACK_30, BatteryType.B6S_1300)));
        w.input.armSwitch = false;
        w.frame(0L);
        w.input.armSwitch = true;
        w.frame(0L);
        assertTrue(w.sim.telemetry().armed);
        assertFalse(w.sim.telemetry().escBurned);
    }

    @Test
    void builtinCombinationsNeverOvervoltTheirEsc() {
        for (DroneBuild build : BuiltinRegressionTest.buildableCombinations()) {
            assertFalse(AirframeSpec.of(build).escOvervoltage, build.toString());
        }
    }
}
