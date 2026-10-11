package de.lardtom.propwash.video;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.sim.WearEffects;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WearShakeTest {
    private static PartWear all(float condition) {
        PartWear wear = PartWear.PRISTINE.withProps(new float[]{condition, condition, condition, condition});
        for (int p = 0; p < PartWear.POSITIONS; p++) {
            wear = wear.withMotor(p, condition).withArm(p, condition);
        }
        return wear;
    }

    @Test
    void partsTheGameCountsAsUndamagedDoNotShakeTheCamera() {
        for (double brittleness : new double[]{0.0, 0.3, 1.0}) {
            assertEquals(0.0, WearShake.level(PartWear.PRISTINE, brittleness), 0.0);
            assertEquals(0.0, WearShake.level(all(0.97F), brittleness), 0.0);
            assertEquals(0.0, WearShake.level(all(PartWear.DAMAGED_BELOW), brittleness), 0.0);
            assertTrue(WearEffects.vibration(all(0.97F), brittleness) > 0.05);
        }
        assertEquals(0.0, WearShake.level(null, 0.0), 0.0);
    }

    @Test
    void startsWithoutAJumpBelowTheDamageThreshold() {
        for (double brittleness : new double[]{0.0, 0.5, 1.0}) {
            double just = WearShake.level(all(0.89F), brittleness);
            assertTrue(just > 0.0 && just < 0.12, brittleness + " " + just);
            double previous = 0.0;
            for (float condition = 0.9F; condition >= 0.02F; condition -= 0.01F) {
                double level = WearShake.level(PartWear.PRISTINE.withProp(1, condition).withMotor(2, condition), brittleness);
                assertTrue(level >= previous - 1.0E-9, brittleness + " " + condition);
                assertTrue(level - previous < 0.03, brittleness + " " + condition + " " + level + " " + previous);
                previous = level;
            }
        }
    }

    @Test
    void damagedPartsStayClearlyVisibleAtFullThrottle() {
        double bent = WearShake.level(PartWear.PRISTINE.withProp(0, 0.5F), 0.0);
        assertTrue(bent > 0.2 && bent < WearEffects.vibration(PartWear.PRISTINE.withProp(0, 0.5F), 0.0), "" + bent);
        double full = CameraShake.buzzDegrees(2.8, WearShake.atSpeed(bent, 2.8));
        assertTrue(full > 6.0 * CameraShake.BUZZ_HOVER_DEG, "" + full);
        double hover = CameraShake.buzzDegrees(1.0, WearShake.atSpeed(bent, 1.0));
        assertTrue(hover < 1.25 * CameraShake.BUZZ_HOVER_DEG, "" + hover);
        double broken = WearShake.level(PartWear.PRISTINE.withProp(0, 0.0F), 0.0);
        assertTrue(broken > 0.9 && broken <= WearEffects.BROKEN_PROP_VIBRATION, "" + broken);
    }

    @Test
    void neverAboveThePhysicalVibrationAndBounded() {
        Random random = new Random(7L);
        for (int i = 0; i < 2000; i++) {
            PartWear wear = PartWear.PRISTINE;
            for (int p = 0; p < PartWear.POSITIONS; p++) {
                wear = wear.withProp(p, random.nextFloat()).withMotor(p, random.nextFloat()).withArm(p, random.nextFloat());
            }
            double brittleness = random.nextDouble();
            double level = WearShake.level(wear, brittleness);
            assertTrue(level >= 0.0 && level <= WearEffects.vibration(wear, brittleness) + 1.0E-9);
            double ratio = 4.0 * random.nextDouble();
            double atSpeed = WearShake.atSpeed(level, ratio);
            assertTrue(atSpeed >= 0.0 && atSpeed <= WearEffects.MAX_VIBRATION);
        }
        assertEquals(0.0, WearShake.atSpeed(Double.NaN, 2.0), 0.0);
        assertEquals(0.0, WearShake.atSpeed(1.0, Double.NaN), 0.0);
        assertEquals(WearEffects.MAX_VIBRATION, WearShake.atSpeed(4.0, 99.0), 0.0);
    }
}
