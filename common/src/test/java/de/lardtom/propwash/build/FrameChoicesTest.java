package de.lardtom.propwash.build;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameChoicesTest {
    private static DroneBuild with(DroneBuild base, StackType stack, MotorType motor, PropType prop) {
        return new DroneBuild(base.frame(), stack, motor, prop, base.video(), base.battery());
    }

    private static Set<MotorType> motors(DroneBuild preset) {
        Set<MotorType> out = new HashSet<>();
        for (MotorType motor : MotorType.values()) {
            if (with(preset, preset.stack(), motor, preset.prop()).buildable()) {
                out.add(motor);
            }
        }
        return out;
    }

    private static Set<StackType> stacks(DroneBuild preset) {
        Set<StackType> out = new HashSet<>();
        for (StackType stack : StackType.values()) {
            if (!stack.creative() && with(preset, stack, preset.motor(), preset.prop()).buildable()) {
                out.add(stack);
            }
        }
        return out;
    }

    @Test
    void everyPresetIsBuildable() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            assertTrue(preset.buildable(), preset.frame().key());
        }
    }

    @Test
    void framesOfferRealChoices() {
        assertEquals(Set.of(MotorType.M1404_4600, MotorType.M2004_3000), motors(DroneBuild.CINEWHOOP));
        assertEquals(Set.of(MotorType.M2207_1750, MotorType.M2306_1860, MotorType.M2807_1300), motors(DroneBuild.LONG_RANGE));
        assertEquals(Set.of(StackType.AIO_20, StackType.STACK_30), stacks(DroneBuild.RACER));
        assertEquals(Set.of(StackType.AIO_20, StackType.STACK_30), stacks(DroneBuild.LONG_RANGE));
        assertTrue(with(DroneBuild.WHOOP, DroneBuild.WHOOP.stack(), DroneBuild.WHOOP.motor(), PropType.P31_3B).buildable());
    }

    @Test
    void freestyleKeepsTheThirtyByThirtyStack() {
        assertEquals(Set.of(StackType.STACK_30), stacks(DroneBuild.FREESTYLE));
        assertFalse(with(DroneBuild.FREESTYLE, StackType.AIO_20, DroneBuild.FREESTYLE.motor(), DroneBuild.FREESTYLE.prop()).buildable());
    }

    @Test
    void lighterStackWarnsAboutEscCurrent() {
        DroneBuild racerAio = with(DroneBuild.RACER, StackType.AIO_20, DroneBuild.RACER.motor(), DroneBuild.RACER.prop());
        assertTrue(racerAio.warnings().contains(DroneBuild.Issue.ESC_UNDERSIZED));
        assertTrue(racerAio.totalMassGrams() < DroneBuild.RACER.totalMassGrams());
    }
}
