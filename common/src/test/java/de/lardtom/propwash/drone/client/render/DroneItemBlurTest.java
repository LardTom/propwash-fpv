package de.lardtom.propwash.drone.client.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneItemBlurTest {
    private static final float THRESHOLD = 0.12F;

    @Test
    void onlyTheStringBlurAsksForSpinningProps() {
        assertEquals("blur", DroneItemRenderer.BLUR);
        assertTrue(DroneItemRenderer.blurRequested(List.of("blur")));
        assertTrue(DroneItemRenderer.blurRequested(List.of("pack", "blur")));
        assertFalse(DroneItemRenderer.blurRequested(null));
        assertFalse(DroneItemRenderer.blurRequested(List.of()));
        assertFalse(DroneItemRenderer.blurRequested(List.of("ccw")));
        assertFalse(DroneItemRenderer.blurRequested(List.of("ccw_blur")));
        assertFalse(DroneItemRenderer.blurRequested(List.of("Blur")));
    }

    @Test
    void decorationSpinsOnlyPropsWithBlurModel() {
        for (PropDamage damage : List.of(PropDamage.NONE, PropDamage.WORN, PropDamage.DAMAGED)) {
            assertTrue(DroneVisuals.propBlur(false, 0.0F, THRESHOLD, true, true, damage), damage.name());
            assertFalse(DroneVisuals.propBlur(false, 0.0F, THRESHOLD, true, false, damage), damage.name() + " ohne _blur-Modell");
        }
        assertFalse(DroneVisuals.propBlur(false, 0.0F, THRESHOLD, true, true, PropDamage.BROKEN));
        assertFalse(DroneVisuals.propBlur(false, 0.0F, THRESHOLD, false, true, PropDamage.NONE));
    }

    @Test
    void flightBlurIsUnchanged() {
        assertTrue(DroneVisuals.propBlur(true, 0.5F, THRESHOLD, false, true, PropDamage.NONE));
        assertTrue(DroneVisuals.propBlur(true, 0.5F, THRESHOLD, false, false, PropDamage.DAMAGED));
        assertFalse(DroneVisuals.propBlur(true, THRESHOLD, THRESHOLD, false, true, PropDamage.NONE));
        assertFalse(DroneVisuals.propBlur(false, 1.0F, THRESHOLD, false, true, PropDamage.NONE));
        assertFalse(DroneVisuals.propBlur(true, 1.0F, THRESHOLD, false, true, PropDamage.BROKEN));
    }
}
