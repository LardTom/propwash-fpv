package de.lardtom.propwash.osd;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdCoverageTest {
    private static List<float[]> quads(OsdCoverage.Shapes shapes) {
        List<float[]> all = new ArrayList<>();
        for (int i = 0; i < shapes.fillCount(); i++) {
            all.add(new float[]{shapes.fill()[i * 4], shapes.fill()[i * 4 + 1], shapes.fill()[i * 4 + 2], shapes.fill()[i * 4 + 3]});
        }
        for (int i = 0; i < shapes.outlineCount(); i++) {
            all.add(new float[]{shapes.outline()[i * 4], shapes.outline()[i * 4 + 1], shapes.outline()[i * 4 + 2], shapes.outline()[i * 4 + 3]});
        }
        return all;
    }

    private static float area(float[] quads, int count) {
        float area = 0.0F;
        for (int i = 0; i < count; i++) {
            area += (quads[i * 4 + 2] - quads[i * 4]) * (quads[i * 4 + 3] - quads[i * 4 + 1]);
        }
        return area;
    }

    private static void assertNoOverlap(OsdCoverage.Shapes shapes) {
        List<float[]> all = quads(shapes);
        for (int a = 0; a < all.size(); a++) {
            for (int b = a + 1; b < all.size(); b++) {
                float[] p = all.get(a);
                float[] q = all.get(b);
                float w = Math.min(p[2], q[2]) - Math.max(p[0], q[0]);
                float h = Math.min(p[3], q[3]) - Math.max(p[1], q[1]);
                assertTrue(w <= 1.0E-4F || h <= 1.0E-4F, "Überlappung " + a + "/" + b);
            }
        }
    }

    @Test
    void singleTexelOutlinesMatchTheirCopies() {
        boolean[] dot = {true};
        OsdCoverage.Shapes thin = OsdCoverage.mask(dot, 1, 1, OsdConfig.Outline.THIN, 0.25F);
        assertEquals(1.0F, area(thin.fill(), thin.fillCount()), 1.0E-5F);
        assertEquals(1.0F, area(thin.outline(), thin.outlineCount()), 1.0E-5F);
        assertNoOverlap(thin);
        OsdCoverage.Shapes thick = OsdCoverage.mask(dot, 1, 1, OsdConfig.Outline.THICK, 1.0F);
        assertEquals(8.0F, area(thick.outline(), thick.outlineCount()), 1.0E-5F);
        assertNoOverlap(thick);
        OsdCoverage.Shapes shadow = OsdCoverage.mask(dot, 1, 1, OsdConfig.Outline.SHADOW, 1.0F);
        assertEquals(1.0F, area(shadow.outline(), shadow.outlineCount()), 1.0E-5F);
        assertEquals(1.0F, shadow.outline()[0], 1.0E-5F);
        assertEquals(1.0F, shadow.outline()[1], 1.0E-5F);
        OsdCoverage.Shapes none = OsdCoverage.mask(dot, 1, 1, OsdConfig.Outline.NONE, 0.25F);
        assertEquals(0, none.outlineCount());
    }

    @Test
    void diagonalTexelsNeverCoverAPixelTwice() {
        boolean[] diagonal = {true, false, false, true};
        OsdCoverage.Shapes thin = OsdCoverage.mask(diagonal, 2, 2, OsdConfig.Outline.THIN, 0.25F);
        assertNoOverlap(thin);
        assertEquals(2.0F, area(thin.fill(), thin.fillCount()), 1.0E-5F);
        assertEquals(2.0F - 2.0F * 0.25F * 0.25F, area(thin.outline(), thin.outlineCount()), 1.0E-5F);
        OsdCoverage.Shapes thick = OsdCoverage.mask(diagonal, 2, 2, OsdConfig.Outline.THICK, 1.0F);
        assertNoOverlap(thick);
        assertEquals(12.0F, area(thick.outline(), thick.outlineCount()), 1.0E-5F);
    }

    @Test
    void glyphRowsMergeIntoFewQuads() {
        boolean[] bar = new boolean[5 * 3];
        for (int i = 0; i < bar.length; i++) {
            bar[i] = true;
        }
        OsdCoverage.Shapes shapes = OsdCoverage.mask(bar, 5, 3, OsdConfig.Outline.THIN, 0.3F);
        assertEquals(1, shapes.fillCount());
        assertEquals(4, shapes.outlineCount());
        assertEquals(-0.3F, shapes.minX(), 1.0E-5F);
        assertEquals(5.3F, shapes.maxX(), 1.0E-5F);
    }

    @Test
    void overlappingRectsBecomeOneLayer() {
        int[] cross = {0, 4, 10, 6, 4, 0, 6, 10};
        OsdCoverage.Shapes shapes = OsdCoverage.rects(cross, 2, OsdConfig.Outline.THIN, 0.5F);
        assertNoOverlap(shapes);
        assertEquals(10.0F * 2.0F + 2.0F * 8.0F, area(shapes.fill(), shapes.fillCount()), 1.0E-4F);
        assertEquals(40.0F * 0.5F - 4.0F * 0.25F, area(shapes.outline(), shapes.outlineCount()), 1.0E-4F);
        assertTrue(OsdCoverage.rects(new int[]{3, 3, 3, 9}, 1, OsdConfig.Outline.THIN, 0.5F).isEmpty());
    }
}
