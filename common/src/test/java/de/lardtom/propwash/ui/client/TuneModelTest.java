package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.PidLoopRate;
import de.lardtom.propwash.ui.TuneEdits;
import de.lardtom.propwash.ui.TuneKeys;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuneModelTest {
    @Test
    void editsMarkDirtyAndBumpTheRevision() {
        TuneModel model = TuneModel.detached(DroneBuild.RACER, Map.of());
        assertFalse(model.live());
        assertFalse(model.dirty());
        int before = model.revision();
        model.set(TuneKeys.ANGLE_LIMIT, 40);
        assertTrue(model.dirty());
        assertEquals(before + 1, model.revision());
        assertEquals(40.0, model.value(TuneKeys.ANGLE_LIMIT));
        assertTrue(model.modified(TuneKeys.ANGLE_LIMIT));
        model.set(TuneKeys.ANGLE_LIMIT, 40);
        assertEquals(before + 1, model.revision());
    }

    @Test
    void changingTheLoopRateRefreshesDerivedDefaults() {
        TuneModel model = TuneModel.detached(DroneBuild.FREESTYLE, Map.of());
        double before = model.value("pid.roll.p");
        model.set(TuneKeys.PID_LOOP, PidLoopRate.HZ_500.ordinal());
        assertNotEquals(before, model.value("pid.roll.p"));
        assertFalse(model.manualPid());
    }

    @Test
    void pidEditsAndResetToDerived() {
        TuneModel model = TuneModel.detached(DroneBuild.CINEWHOOP, Map.of());
        double derived = model.value("pid.pitch.p");
        model.step("pid.pitch.p", 5);
        assertTrue(model.manualPid());
        assertEquals(derived + 5.0, model.value("pid.pitch.p"), 1e-6);
        model.resetPidToDerived();
        assertFalse(model.manualPid());
        assertEquals(derived, model.value("pid.pitch.p"), 1e-6);
    }

    @Test
    void filterEditsStayModifiedAndTheConfigFollowsTheWorkingCopy() {
        TuneModel model = TuneModel.detached(DroneBuild.FREESTYLE, Map.of());
        model.set(TuneKeys.GYRO_LPF, 100);
        assertTrue(model.modified(TuneKeys.GYRO_LPF));
        assertEquals(100.0, model.config().filters.gyroLpfHz, 1e-6);
        model.set(TuneKeys.THROTTLE_MID, 0.35);
        assertEquals(0.35, model.config().pid.throttleMid, 1e-6);
        assertEquals(model.value("pid.roll.p"), model.config().pid.roll.p, 1e-3);
    }

    @Test
    void discardRestoresTheOriginal() {
        TuneModel model = TuneModel.detached(DroneBuild.WHOOP, Map.of(TuneKeys.MOTOR_IDLE, 7.0F));
        model.set(TuneKeys.MOTOR_IDLE, 9.0);
        model.discard();
        assertFalse(model.dirty());
        assertEquals(7.0, model.value(TuneKeys.MOTOR_IDLE), 1e-6);
    }

    @Test
    void templatesNeverCarryManualPids() {
        Map<String, Float> manual = TuneEdits.set(Map.of(TuneKeys.THROTTLE_EXPO, 0.3F), Map.of(), "pid.roll.p", 50);
        Map<String, Float> safe = TuneModel.templateSafe(manual);
        assertFalse(safe.containsKey("pid.roll.p"));
        assertFalse(safe.containsKey(TuneKeys.PID_SOURCE));
        assertEquals(0.3F, safe.get(TuneKeys.THROTTLE_EXPO));
    }
}
