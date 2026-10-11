package de.lardtom.propwash.drone.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneIconColorTest {
    private static int luma(int rgb) {
        return Math.round(0.2126F * ((rgb >> 16) & 0xFF) + 0.7152F * ((rgb >> 8) & 0xFF) + 0.0722F * (rgb & 0xFF));
    }

    @Test
    void darkPaintIsLiftedOnTinyIcons() {
        for (int dark : new int[]{0x000000, 0x1B1D22, 0x2A2C30, FallbackShapes.CARBON & 0xFFFFFF, 0x0000FF}) {
            int lifted = DroneItemRenderer.readableIconColor(dark);
            assertTrue(luma(lifted) >= DroneItemRenderer.ICON_MIN_LUMA - 1, Integer.toHexString(lifted));
        }
        assertEquals(0xFFC23D, DroneItemRenderer.readableIconColor(0xFFC23D));
        assertEquals(0x3D8FD6, DroneItemRenderer.readableIconColor(0xFF3D8FD6));
        assertTrue(luma(FallbackShapes.ICON_CARBON & 0xFFFFFF) >= DroneItemRenderer.ICON_MIN_LUMA, "Rahmen-Symbol ohne Lack lesbar");
        int blue = DroneItemRenderer.readableIconColor(0x0000FF);
        assertTrue((blue & 0xFF) > ((blue >> 16) & 0xFF), "Farbton bleibt erhalten");
    }
}
