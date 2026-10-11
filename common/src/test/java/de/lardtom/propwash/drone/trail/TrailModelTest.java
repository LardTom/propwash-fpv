package de.lardtom.propwash.drone.trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailModelTest {
    @Test
    void lifeGrowsWithSpeedBetweenOneAndTwoSeconds() {
        assertEquals(1.0F, TrailModel.life(0.0));
        assertEquals(1.0F, TrailModel.life(TrailModel.SLOW_SPEED));
        assertEquals(1.5F, TrailModel.life((TrailModel.SLOW_SPEED + TrailModel.FAST_SPEED) / 2.0), 1.0E-6);
        assertEquals(2.0F, TrailModel.life(TrailModel.FAST_SPEED));
        assertEquals(2.0F, TrailModel.life(80.0));
        assertEquals(1.0F, TrailModel.life(Double.NaN));
    }

    @Test
    void capsStayWithinTheBudget() {
        int perTrail = (int) Math.ceil(TrailModel.MAX_LIFE / TrailModel.SAMPLE_INTERVAL) + 1;
        assertTrue(TrailModel.SAMPLES_PER_TRAIL >= perTrail);
        assertTrue(TrailModel.SAMPLES_PER_TRAIL * TrailModel.MAX_TRAILS <= 8000);
    }

    @Test
    void onlyArmedDronesInTheAirEmit() {
        assertTrue(TrailModel.emits(true, false));
        assertFalse(TrailModel.emits(false, false));
        assertFalse(TrailModel.emits(true, true));
        assertTrue(TrailModel.landed(true, 0.2));
        assertFalse(TrailModel.landed(true, 6.0));
        assertFalse(TrailModel.landed(false, 0.0));
    }

    @Test
    void fadeAndTaper() {
        assertEquals(1.0F, TrailModel.freshness(0.0, 1.5F));
        assertEquals(0.5F, TrailModel.freshness(0.75, 1.5F), 1.0E-6);
        assertEquals(0.0F, TrailModel.freshness(3.0, 1.5F));
        assertEquals(0.0F, TrailModel.freshness(0.1, 0.0F));
        assertEquals(1.0F, TrailModel.alpha(1.0F));
        assertEquals(0.0F, TrailModel.alpha(0.0F));
        assertEquals(0.75F, TrailModel.alpha(0.5F), 1.0E-6);
        assertTrue(TrailModel.alpha(0.2F) > 0.3F);
        assertEquals(TrailModel.TAIL_WIDTH, TrailModel.widthScale(0.0F), 1.0E-6);
        assertEquals(1.0F, TrailModel.widthScale(1.0F), 1.0E-6);
    }

    @Test
    void farTrailsKeepAMinimumWidthOnScreen() {
        float near = TrailModel.halfWidth(TrailModel.CORE_HALF_WIDTH, TrailModel.CORE_MIN_ANGLE, 2.0, 1.0F);
        float far = TrailModel.halfWidth(TrailModel.CORE_HALF_WIDTH, TrailModel.CORE_MIN_ANGLE, 200.0, 1.0F);
        assertEquals(TrailModel.CORE_HALF_WIDTH, near, 1.0E-6);
        assertEquals(200.0 * TrailModel.CORE_MIN_ANGLE, far, 1.0E-6);
        assertEquals(TrailModel.CORE_HALF_WIDTH * TrailModel.TAIL_WIDTH,
                TrailModel.halfWidth(TrailModel.CORE_HALF_WIDTH, TrailModel.CORE_MIN_ANGLE, 2.0, 0.0F), 1.0E-6);
        assertEquals(far, TrailModel.halfWidth(TrailModel.CORE_HALF_WIDTH, TrailModel.CORE_MIN_ANGLE, 200.0, 0.0F), 1.0E-6);
        assertTrue(TrailModel.GLOW_HALF_WIDTH > TrailModel.CORE_HALF_WIDTH);
        assertTrue(TrailModel.GLOW_MIN_ANGLE > TrailModel.CORE_MIN_ANGLE);
    }

    @Test
    void fadesNearTheCameraAndAtTheEndOfTheRange() {
        assertEquals(0.0F, TrailModel.nearFade(0.2));
        assertEquals(1.0F, TrailModel.nearFade(5.0));
        assertEquals(1.0F, TrailModel.farFade(10.0, 128.0));
        assertEquals(0.0F, TrailModel.farFade(130.0, 128.0));
        assertEquals(0.5F, TrailModel.farFade(116.0, 128.0), 1.0E-6);
        assertEquals(0.0F, TrailModel.farFade(1.0, 0.0));
        assertEquals(TrailModel.MAX_CORE_PULL, TrailModel.corePull(1000.0));
        assertEquals(0.04, TrailModel.corePull(10.0), 1.0E-9);
    }

    @Test
    void jumpsAreTeleportsNotFastFlight() {
        assertFalse(TrailModel.isJump(1.5, 1.0 / 40.0));
        assertFalse(TrailModel.isJump(60.0, 1.0));
        assertTrue(TrailModel.isJump(20.0, 1.0 / 40.0));
        assertTrue(TrailModel.isJump(200.0, 1.0));
    }
}
