package de.lardtom.propwash.drone.trail;

import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.drone.led.LedColor;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailColorTest {
    private static float[] hsv(int rgb) {
        return TrailColor.hsv(rgb);
    }

    @Test
    void litColouredLedsWin() {
        DronePaint paint = DronePaint.of(Map.of(PaintSlot.FRAME, 0x1E8C2A));
        int color = TrailColor.pick(LedColor.MAGENTA, paint, 1L);
        assertEquals(TrailColor.vivid(LedColor.MAGENTA.rgb()), color);
        assertEquals(TrailColor.vivid(0x1E8C2A), TrailColor.pick(null, paint, 1L));
    }

    @Test
    void whiteLedsAndGreyPaintFallThrough() {
        DronePaint paint = DronePaint.of(Map.of(PaintSlot.FRAME, 0x202020, PaintSlot.TPU, 0xF0F0F0, PaintSlot.PROP_FRONT_LEFT, 0xFF7A00));
        assertEquals(TrailColor.vivid(0xFF7A00), TrailColor.pick(LedColor.WHITE, paint, 7L));
        assertEquals(TrailColor.vivid(0xFF7A00), TrailColor.pick(LedColor.WARM_WHITE, paint, 7L));
    }

    @Test
    void unpaintedDronesGetAStablePaletteColour() {
        int a = TrailColor.pick(null, DronePaint.DEFAULT, 42L);
        assertEquals(a, TrailColor.pick(null, DronePaint.DEFAULT, 42L));
        assertEquals(a, TrailColor.palette(42L));
        Set<Integer> seen = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            seen.add(TrailColor.palette(seed * 7919L));
        }
        assertEquals(TrailColor.PALETTE.length, seen.size());
        for (int color : TrailColor.PALETTE) {
            assertTrue(TrailColor.colourful(color));
            assertTrue(hsv(color)[2] > 0.9F);
        }
        assertEquals(TrailColor.palette(1L), TrailColor.pick(null, null, 1L));
    }

    @Test
    void trailColoursAreBrightForTheNightAndTheHaloIsDarkForTheDay() {
        int dark = 0x400A0A;
        assertTrue(TrailColor.colourful(dark));
        int vivid = TrailColor.vivid(dark);
        float[] v = hsv(vivid);
        assertEquals(1.0F, v[2], 0.01F);
        assertTrue(v[1] >= TrailColor.VIVID_SATURATION - 0.01F);
        assertEquals(hsv(dark)[0], v[0], 0.02F);
        float[] halo = hsv(TrailColor.halo(vivid));
        assertEquals(TrailColor.HALO_VALUE, halo[2], 0.01F);
        assertEquals(v[0], halo[0], 0.02F);
        assertFalse(TrailColor.colourful(0x101010));
        assertFalse(TrailColor.colourful(0xC8C8C8));
    }

    @Test
    void hsvRoundTrip() {
        for (int rgb : new int[]{0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF, 0x3A6BFF, 0x9A4DFF}) {
            float[] c = hsv(rgb);
            assertEquals(rgb, TrailColor.fromHsv(c[0], c[1], c[2]));
        }
    }
}
