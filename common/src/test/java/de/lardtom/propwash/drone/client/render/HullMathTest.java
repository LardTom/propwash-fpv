package de.lardtom.propwash.drone.client.render;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullMathTest {
    private static float lowest(float[] points, double dx, double dy, double dz) {
        float low = Float.POSITIVE_INFINITY;
        for (int i = 0; i + 2 < points.length; i += 3) {
            low = Math.min(low, (float) (dx * points[i] + dy * points[i + 1] + dz * points[i + 2]));
        }
        return low;
    }

    @Test
    void uniqueDropsRepeatedVerticesOnly() {
        float[] cube = new float[8 * 3 * 3];
        int n = 0;
        for (int repeat = 0; repeat < 3; repeat++) {
            for (int c = 0; c < 8; c++) {
                cube[n++] = (c & 1) == 0 ? -40.0F : 40.0F;
                cube[n++] = (c & 2) == 0 ? -5.0F : 12.5F;
                cube[n++] = (c & 4) == 0 ? -60.0F : 60.0F;
            }
        }
        float[] unique = HullMath.unique(cube);
        assertEquals(24, unique.length);
        assertEquals(-5.0F, lowest(unique, 0.0, 1.0, 0.0), 0.0F);
        assertEquals(24, HullMath.reduce(cube, 2048, 1024).length);
        assertEquals(0, HullMath.unique(new float[0]).length);
    }

    @Test
    void supportPointsAreRealVerticesAndLoseLessThanAMillimetre() {
        Random random = new Random(3L);
        int count = 6000;
        float[] cloud = new float[count * 3];
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double y = random.nextDouble() * 2.0 - 1.0;
            double r = Math.sqrt(1.0 - y * y);
            double radius = i < 4000 ? 180.0 : 180.0 * random.nextDouble();
            cloud[3 * i] = (float) (Math.cos(a) * r * radius);
            cloud[3 * i + 1] = (float) (y * radius * 0.25);
            cloud[3 * i + 2] = (float) (Math.sin(a) * r * radius);
        }
        float[] reduced = HullMath.reduce(cloud, 4096, 4096);
        assertTrue(reduced.length / 3 <= 4102 && reduced.length / 3 > 200 && reduced.length / 3 < 6000, "" + reduced.length / 3);
        double worst = 0.0;
        for (int k = 0; k < 4000; k++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double y = random.nextDouble() * 2.0 - 1.0;
            double r = Math.sqrt(1.0 - y * y);
            float full = lowest(cloud, Math.cos(a) * r, y, Math.sin(a) * r);
            float kept = lowest(reduced, Math.cos(a) * r, y, Math.sin(a) * r);
            assertTrue(kept >= full - 1.0E-4F);
            worst = Math.max(worst, kept - full);
        }
        assertTrue(worst < 0.5, "größter Verlust " + worst + " mm");
    }

    @Test
    void outlineKeepsTheOuterProfileOfAProp() {
        float[] profile = {
                5.0F, 4.0F, 0.0F,
                64.8F, 2.0F, 0.0F,
                64.8F, 3.5F, 0.0F,
                30.0F, 2.5F, 0.0F,
                40.0F, 0.5F, 0.0F,
                6.0F, -1.0F, 0.0F,
                20.0F, 5.5F, 0.0F};
        float[] outline = HullMath.outline(profile, 9);
        assertTrue(outline.length / 2 <= 7 && outline.length / 2 >= 4);
        float top = Float.NEGATIVE_INFINITY;
        float bottom = Float.POSITIVE_INFINITY;
        float outer = 0.0F;
        boolean inner = false;
        for (int i = 0; i + 1 < outline.length; i += 2) {
            top = Math.max(top, outline[i + 1]);
            bottom = Math.min(bottom, outline[i + 1]);
            outer = Math.max(outer, outline[i]);
            inner |= outline[i] == 30.0F;
        }
        assertEquals(5.5F, top, 0.0F);
        assertEquals(-1.0F, bottom, 0.0F);
        assertEquals(64.8F, outer, 0.0F);
        assertTrue(!inner);
        assertEquals(0, HullMath.outline(new float[0], 9).length);
    }

    @Test
    void ringsEncloseTheSweptDiscWithoutGoingFarOutside() {
        float radius = 165.0F;
        float[] rings = HullMath.rings(new float[]{radius, 3.0F, 0.0F, 7.0F}, 32);
        assertEquals((32 + 1) * 3, rings.length);
        for (int k = 0; k < 3600; k++) {
            double a = Math.PI * 2.0 * k / 3600.0;
            double tilt = Math.toRadians(40.0);
            double dx = Math.cos(a) * Math.sin(tilt);
            double dz = Math.sin(a) * Math.sin(tilt);
            double dy = Math.cos(tilt);
            double disc = dy * 3.0 - radius * Math.sin(tilt);
            float hull = lowest(rings, dx, dy, dz);
            assertTrue(hull <= disc + 1.0E-3, "Scheibe ragt heraus bei " + k);
            assertTrue(hull >= disc - 0.6, "Hülle " + (disc - hull) + " mm zu weit bei " + k);
        }
    }
}
