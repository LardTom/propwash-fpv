package de.lardtom.propwash.osd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdOutlineTest {
    private static float quantized(float alpha) {
        return OsdColors.alphaByte(alpha) / 255.0F;
    }

    private static float glyphBrightness(float alpha, OsdConfig.Outline outline) {
        float stack = OsdOutline.stackAlpha(alpha, outline);
        float fill = quantized(OsdOutline.fillAlpha(alpha, stack));
        return fill;
    }

    @Test
    void outlineFollowsTheOpacityUpToItsFullStrength() {
        for (OsdConfig.Outline outline : OsdConfig.Outline.values()) {
            assertEquals(outline.alphaFactor(), OsdOutline.stackAlpha(OsdOutline.FULL_OUTLINE_OPACITY, outline), 1.0E-6F, outline.name());
            assertTrue(OsdOutline.stackAlpha(OsdConfig.DEFAULT_OPACITY / 100.0F, outline) <= outline.alphaFactor(), outline.name());
            assertEquals(outline.alphaFactor(), OsdOutline.stackAlpha(1.0F, outline), 1.0E-6F, outline.name());
            assertEquals(OsdOutline.stackAlpha(0.8F, outline), OsdOutline.copyAlpha(OsdOutline.stackAlpha(0.8F, outline)), 1.0E-6F, outline.name());
        }
    }

    @Test
    void textStaysBrightOnTopOfItsOutline() {
        for (OsdConfig.Outline outline : OsdConfig.Outline.values()) {
            if (outline == OsdConfig.Outline.NONE) {
                continue;
            }
            assertTrue(glyphBrightness(0.75F, outline) >= 0.9F, outline.name());
            assertTrue(glyphBrightness(0.5F, outline) >= 0.7F, outline.name());
            assertEquals(1.0F, glyphBrightness(1.0F, outline), 1.0E-6F, outline.name());
        }
        assertEquals(0.5F, glyphBrightness(0.5F, OsdConfig.Outline.NONE), 0.01F);
    }

    @Test
    void barOutlineLeavesTheBarInteriorUncovered() {
        float[] ring = OsdOutline.ring(10.0F, 20.0F, 30.0F, 24.0F, 1.0F);
        assertEquals(16, ring.length);
        for (int i = 0; i < ring.length; i += 4) {
            boolean overlapsX = ring[i] < 30.0F && ring[i + 2] > 10.0F;
            boolean overlapsY = ring[i + 1] < 24.0F && ring[i + 3] > 20.0F;
            assertTrue(!(overlapsX && overlapsY), "strip " + i / 4);
        }
        float area = 0.0F;
        for (int i = 0; i < ring.length; i += 4) {
            area += (ring[i + 2] - ring[i]) * (ring[i + 3] - ring[i + 1]);
        }
        assertEquals(22.0F * 6.0F - 20.0F * 4.0F, area, 1.0E-4F);
        float[] shadow = OsdOutline.shadow(10.0F, 20.0F, 30.0F, 24.0F, 1.0F);
        for (int i = 0; i < shadow.length; i += 4) {
            boolean overlapsX = shadow[i] < 30.0F && shadow[i + 2] > 10.0F;
            boolean overlapsY = shadow[i + 1] < 24.0F && shadow[i + 3] > 20.0F;
            assertTrue(!(overlapsX && overlapsY), "shadow " + i / 4);
        }
        float barCoverage = OsdOutline.coverage(0.0F, 0.5F);
        assertEquals(0.5F, barCoverage, 1.0E-6F);
    }

    @Test
    void outlineFadesOutWithTheOpacity() {
        for (OsdConfig.Outline outline : OsdConfig.Outline.values()) {
            float previous = 0.0F;
            for (float alpha = 0.05F; alpha <= 1.0F; alpha += 0.05F) {
                float stack = OsdOutline.stackAlpha(alpha, outline);
                assertTrue(stack + 1.0E-6F >= previous, outline + " @ " + alpha);
                assertTrue(stack <= outline.alphaFactor() + 1.0E-6F, outline + " @ " + alpha);
                previous = stack;
            }
        }
        assertEquals(0.0F, OsdOutline.stackAlpha(0.0F, OsdConfig.Outline.THICK));
        assertEquals(0.0F, OsdOutline.stackAlpha(Float.NaN, OsdConfig.Outline.THICK));
        assertEquals(0.0F, OsdOutline.fillAlpha(0.0F, 0.5F));
        assertEquals(1.0F, OsdOutline.fillAlpha(1.0F, 1.0F));
        assertEquals(0.0F, OsdOutline.layerAlpha(0.3F, 0));
    }

    @Test
    void layersStackToTheTarget() {
        for (int layers = 1; layers <= 8; layers++) {
            assertEquals(0.4F, OsdOutline.stacked(OsdOutline.layerAlpha(0.4F, layers), layers), 1.0E-5F);
        }
        assertEquals(1.0F, OsdOutline.layerAlpha(1.0F, 8));
    }
}
