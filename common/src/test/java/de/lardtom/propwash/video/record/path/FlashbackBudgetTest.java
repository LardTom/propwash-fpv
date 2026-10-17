package de.lardtom.propwash.video.record.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlashbackBudgetTest {
    @Test
    void circleVerticesFollowImguiTessellation() {
        assertEquals(32, FlashbackBudget.circleVertices(10));
        assertEquals(32, FlashbackBudget.circleVertices(15));
        assertEquals(48, FlashbackBudget.circleVertices(17));
        assertEquals(48, FlashbackBudget.circleVertices(20));
        assertEquals(48, FlashbackBudget.circleVertices(35));
        assertEquals(96, FlashbackBudget.circleVertices(36));
        assertEquals(96, FlashbackBudget.circleVertices(40));
        assertEquals(96, FlashbackBudget.circleVertices(80));
        assertEquals(96, FlashbackBudget.circleVertices(320));
        for (int r = 1; r < 400; r++) {
            assertTrue(FlashbackBudget.circleVertices(r) >= FlashbackBudget.circleVertices(r - 1) || r == 1, "Radius " + r);
            assertTrue(FlashbackBudget.circleVertices(r) <= 96, "Radius " + r);
        }
    }

    @Test
    void keyframeCostDependsOnUiScale() {
        assertEquals(32, FlashbackBudget.keyframeVertices(1.0F));
        assertEquals(32, FlashbackBudget.keyframeVertices(1.5F));
        assertEquals(48, FlashbackBudget.keyframeVertices(1.75F));
        assertEquals(48, FlashbackBudget.keyframeVertices(2.0F));
        assertEquals(48, FlashbackBudget.keyframeVertices(3.5F));
        assertEquals(96, FlashbackBudget.keyframeVertices(3.6F));
        assertEquals(96, FlashbackBudget.keyframeVertices(4.0F));
    }

    @Test
    void maxKeyframesPerScale() {
        int usable = FlashbackBudget.VERTEX_LIMIT - 1 - FlashbackBudget.RESERVE;
        assertEquals((usable - 2 * FlashbackBudget.HOLD_KEYFRAME_VERTICES) / 32, FlashbackBudget.maxCameraKeyframes(1.0F, 2));
        assertEquals((usable - 2 * FlashbackBudget.HOLD_KEYFRAME_VERTICES) / 48, FlashbackBudget.maxCameraKeyframes(2.0F, 2));
        assertEquals((usable - 2 * FlashbackBudget.HOLD_KEYFRAME_VERTICES) / 96, FlashbackBudget.maxCameraKeyframes(4.0F, 2));
        assertTrue(FlashbackBudget.maxCameraKeyframes(2.0F, 2) > 1200);
        assertTrue(FlashbackBudget.maxCameraKeyframes(4.0F, 2) > 600);
        for (float scale : new float[]{0.5F, 1.0F, 1.25F, 2.0F, 3.0F, 4.0F, 8.0F}) {
            int max = FlashbackBudget.maxCameraKeyframes(scale, 2);
            assertTrue(FlashbackBudget.fill(max, 2, scale) <= 1.0, "Skalierung " + scale);
            assertTrue(FlashbackBudget.fill(max + 1, 2, scale) > 1.0, "Skalierung " + scale);
            assertTrue(FlashbackBudget.fits(max, 2, scale));
            assertFalse(FlashbackBudget.fits(max + 1, 2, scale));
            assertTrue(FlashbackBudget.vertices(max, 2, scale) + FlashbackBudget.RESERVE < FlashbackBudget.VERTEX_LIMIT);
        }
        assertEquals(0.0, FlashbackBudget.fill(0, 0, 2.0F), 0.0);
    }

    @Test
    void measuredCrashIsOverBudget() {
        assertTrue(FlashbackBudget.vertices(2854, 2, 1.0F) > FlashbackBudget.VERTEX_LIMIT);
        assertTrue(FlashbackBudget.fill(2854, 2, 1.0F) > 1.0);
        assertTrue(FlashbackBudget.fill(920, 2, 2.0F) < 1.0);
    }

    @Test
    void assumedScaleNeverBelowTwo() {
        assertEquals(2.0F, FlashbackBudget.assumedScale(1.0F));
        assertEquals(2.0F, FlashbackBudget.assumedScale(Float.NaN));
        assertEquals(3.0F, FlashbackBudget.assumedScale(3.0F));
        assertEquals(1.5F, FlashbackBudget.quantize(1.53F));
        assertEquals(0.125F, FlashbackBudget.quantize(0.01F));
        assertEquals(1.0F, FlashbackBudget.quantize(Float.POSITIVE_INFINITY));
    }

    @Test
    void secondsThatFitScaleWithKeyframeRate() {
        double everyTick = FlashbackBudget.secondsThatFit(1201, 60.0, 2.0F, 2);
        assertEquals(FlashbackBudget.maxCameraKeyframes(2.0F, 2) / 20.0, everyTick, 1.0E-9);
        assertEquals(everyTick * 2.0, FlashbackBudget.secondsThatFit(601, 60.0, 2.0F, 2), 1.0E-9);
        assertTrue(Double.isNaN(FlashbackBudget.secondsThatFit(1, 60.0, 2.0F, 2)));
    }
}
