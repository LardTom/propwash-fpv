package de.lardtom.propwash.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LosAimTest {
    @Test
    void yawFollowsMinecraftConvention() {
        assertEquals(0.0F, LosAim.yawTo(0.0, 5.0), 1.0E-4F);
        assertEquals(90.0F, LosAim.yawTo(-5.0, 0.0), 1.0E-4F);
        assertEquals(-90.0F, LosAim.yawTo(5.0, 0.0), 1.0E-4F);
        assertEquals(180.0F, Math.abs(LosAim.yawTo(0.0, -5.0)), 1.0E-4F);
    }

    @Test
    void targetAboveGivesNegativePitch() {
        assertTrue(LosAim.pitchTo(0.0, 5.0, 5.0) < 0.0F);
        assertTrue(LosAim.pitchTo(0.0, -5.0, 5.0) > 0.0F);
        assertEquals(-45.0F, LosAim.pitchTo(0.0, 5.0, 5.0), 1.0E-4F);
    }

    @Test
    void pitchIsClamped() {
        assertEquals(-LosAim.MAX_PITCH, LosAim.pitchTo(0.0, 10.0, 0.0), 1.0E-4F);
        assertEquals(LosAim.MAX_PITCH, LosAim.pitchTo(0.0, -10.0, 0.0), 1.0E-4F);
        assertEquals(LosAim.MAX_PITCH, LosAim.clampPitch(120.0F), 1.0E-4F);
    }

    @Test
    void wrapAroundTakesTheShortWay() {
        float next = LosAim.approach(179.0F, -179.0F, 0.01, LosAim.TAU_SECONDS);
        assertTrue(next > 179.0F, "sollte über 180° weiterdrehen, war " + next);
        float back = LosAim.approach(-179.0F, 179.0F, 0.01, LosAim.TAU_SECONDS);
        assertTrue(back < -179.0F, "sollte über -180° weiterdrehen, war " + back);
    }

    @Test
    void smoothingConverges() {
        float yaw = 0.0F;
        for (int i = 0; i < 120; i++) {
            yaw = LosAim.approach(yaw, 60.0F, 1.0 / 60.0, LosAim.TAU_SECONDS);
        }
        assertEquals(60.0F, yaw, 0.01F);
        float half = LosAim.approach(0.0F, 60.0F, LosAim.TAU_SECONDS, LosAim.TAU_SECONDS);
        assertEquals(60.0 * (1.0 - Math.exp(-1.0)), half, 1.0E-3);
        assertEquals(10.0F, LosAim.approach(10.0F, 60.0F, 0.0, LosAim.TAU_SECONDS));
    }

    @Test
    void holdsViewWhenTooClose() {
        LosAim.Angles held = LosAim.aim(12.0F, 7.0F, 0.2, 0.1, 0.3, 0.05);
        assertEquals(12.0F, held.yaw());
        assertEquals(7.0F, held.pitch());
        LosAim.Angles moved = LosAim.aim(12.0F, 7.0F, 0.0, 0.0, 5.0, 0.05);
        assertTrue(Math.abs(moved.yaw()) < 12.0F);
    }
}
