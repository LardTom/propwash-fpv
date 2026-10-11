package de.lardtom.propwash.creative;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.sim.AirframeSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeDraftTest {
    private static final DroneBuild INSTALLED = CreativeFlightTest.creative(DroneBuild.FREESTYLE);

    @Test
    void valuesBackAtTheBaseAreDropped() {
        CreativeDraft draft = new CreativeDraft(INSTALLED);
        assertFalse(draft.dirty());
        double kv = draft.value(CreativeParam.MOTOR_KV);
        assertEquals(DroneBuild.FREESTYLE.motor().kv, kv);
        draft.set(CreativeParam.MOTOR_KV, 2100.0);
        assertTrue(draft.dirty());
        assertTrue(draft.overridden(CreativeParam.MOTOR_KV));
        assertEquals(2100.0, draft.value(CreativeParam.MOTOR_KV));
        draft.step(CreativeParam.MOTOR_KV, -35);
        assertFalse(draft.overridden(CreativeParam.MOTOR_KV), draft.spec().toString());
        assertFalse(draft.dirty());
        assertEquals(INSTALLED, draft.build());
    }

    @Test
    void clampedInputReportsANote() {
        CreativeDraft draft = new CreativeDraft(INSTALLED);
        Optional<CreativeLimits.Note> note = draft.set(CreativeParam.BATTERY_CELLS, 40.0);
        assertTrue(note.isPresent());
        assertEquals(CreativeParam.BATTERY_CELLS.max(), note.get().applied());
        assertEquals(12.0, draft.value(CreativeParam.BATTERY_CELLS));
        assertTrue(draft.set(CreativeParam.BATTERY_CELLS, 4.0).isEmpty());
        assertTrue(draft.set(CreativeParam.MOTOR_KV, Double.NaN).isEmpty());
        assertEquals(4, AirframeSpec.of(draft.build()).cells);
    }

    @Test
    void autoValuesFollowTheOtherValues() {
        CreativeDraft draft = new CreativeDraft(INSTALLED);
        double before = draft.base(CreativeParam.INERTIA_ROLL);
        draft.set(CreativeParam.BATTERY_MASS, 600.0);
        double after = draft.base(CreativeParam.INERTIA_ROLL);
        assertTrue(after > before, before + " -> " + after);
        draft.set(CreativeParam.INERTIA_ROLL, after);
        assertFalse(draft.overridden(CreativeParam.INERTIA_ROLL));
        draft.set(CreativeParam.INERTIA_ROLL, after * 3.0);
        assertTrue(draft.overridden(CreativeParam.INERTIA_ROLL));
        draft.reset(CreativeParam.INERTIA);
        assertFalse(draft.overridden(CreativeParam.INERTIA_ROLL));
    }

    @Test
    void baseParts() {
        CreativeDraft draft = new CreativeDraft(INSTALLED);
        assertTrue(draft.basePart(PartKind.FRAME).isEmpty());
        draft.basePart(PartKind.FRAME, FrameType.LONG_RANGE_7.key());
        assertEquals(Optional.of(FrameType.LONG_RANGE_7.key()), draft.basePart(PartKind.FRAME));
        assertEquals(FrameType.LONG_RANGE_7.wheelbaseMm, draft.value(CreativeParam.WHEELBASE));
        draft.basePart(PartKind.FRAME, "no_such_frame");
        assertEquals(Optional.of(FrameType.LONG_RANGE_7.key()), draft.basePart(PartKind.FRAME));
        draft.basePart(PartKind.FRAME, FrameType.FREESTYLE_5.key());
        assertTrue(draft.spec().base().isEmpty());
        draft.basePart(PartKind.STACK, StackType.STACK_30.key());
        draft.basePart(PartKind.STACK, null);
        assertTrue(draft.spec().isEmpty());
    }

    @Test
    void loadingATemplateDropsInstalledParts() {
        CreativeDraft draft = new CreativeDraft(INSTALLED);
        List<CreativeLimits.Note> notes = draft.load(CreativeFlightTest.template(DroneBuild.FREESTYLE).with(CreativeParam.MOTOR_KV, 1.0E7));
        assertEquals(1, notes.size());
        assertEquals(java.util.Map.of(PartKind.STACK, StackType.STACK_30.key()), draft.spec().base());
        assertEquals(CreativeParam.MOTOR_KV.max(), draft.value(CreativeParam.MOTOR_KV));
        draft.revert();
        assertFalse(draft.dirty());
        draft.load(CreativeFlightTest.template(DroneBuild.RACER));
        draft.resetAll();
        assertTrue(draft.spec().isEmpty());
    }

    @Test
    void warningsForSillyValues() {
        CreativeDraft draft = new CreativeDraft(INSTALLED);
        assertTrue(draft.warnings().isEmpty(), draft.warnings().toString());
        draft.set(CreativeParam.PROP_DIAMETER, 250.0);
        assertTrue(draft.warnings().contains(CreativeCheck.Warning.PROPS_OVERLAP));
        draft.set(CreativeParam.INERTIA_PITCH, draft.base(CreativeParam.INERTIA_PITCH) * 0.1);
        assertTrue(draft.warnings().contains(CreativeCheck.Warning.INERTIA_LOW));
        draft.set(CreativeParam.INERTIA_PITCH, draft.base(CreativeParam.INERTIA_PITCH) * 10.0);
        assertTrue(draft.warnings().contains(CreativeCheck.Warning.INERTIA_HIGH));
        draft.resetAll();
        draft.set(CreativeParam.FRAME_MASS, 1.0);
        draft.set(CreativeParam.BATTERY_MASS, 5.0);
        draft.set(CreativeParam.PROP_THRUST_COEFFICIENT, 0.4);
        assertTrue(draft.warnings().contains(CreativeCheck.Warning.EXTREME_THRUST), draft.warnings().toString());
    }
}
