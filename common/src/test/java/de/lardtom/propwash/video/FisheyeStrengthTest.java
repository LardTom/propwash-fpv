package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FisheyeStrengthTest {
    private static final double[] STRENGTHS = {0.05, 0.2, 0.4, 0.6, 0.8, 0.9, 1.0};

    private static FisheyeLens wide(double horizontal, double strength) {
        return FisheyeLens.of(horizontal, strength, ImageArea.full(1920, 1080));
    }

    private static FisheyeLens boxed(double horizontal, double strength) {
        return FisheyeLens.of(horizontal, strength, ImageArea.of(ImageFormat.FOUR_THREE, 1920, 1080));
    }

    @Test
    void sliderEndsAndDefaultMapToKnownProjections() {
        assertEquals(1.0, FisheyeLens.bend(0.0), 0.0);
        assertEquals(0.0, FisheyeLens.bend(FisheyeLens.DEFAULT_STRENGTH), 0.0);
        assertEquals(0.0, FisheyeLens.bend(FisheyeLens.DEFAULT_PERCENT / 100.0), 0.0);
        assertEquals(-0.25, FisheyeLens.bend(1.0), 1.0E-15);
        assertEquals(0.75, FisheyeLens.bend(0.2), 1.0E-15);
        assertEquals(FisheyeLens.bend(1.0), FisheyeLens.bend(7.0), 0.0);
        assertEquals(FisheyeLens.bend(0.0), FisheyeLens.bend(-3.0), 0.0);
        assertEquals(FisheyeLens.bend(FisheyeLens.DEFAULT_STRENGTH), FisheyeLens.bend(Double.NaN), 0.0);
        for (double theta = 0.05; theta < 1.4; theta += 0.05) {
            assertEquals(Math.tan(theta), FisheyeLens.map(theta, 1.0), 1.0E-12);
            assertEquals(theta, FisheyeLens.map(theta, 0.0), 0.0);
            assertEquals(2.0 * Math.tan(theta * 0.5), FisheyeLens.map(theta, 0.25), 1.0E-12);
            assertEquals(2.0 * Math.tanh(theta * 0.5), FisheyeLens.map(theta, -0.25), 1.0E-12);
        }
    }

    @Test
    void defaultStrengthIsTheEquidistantLensBitForBit() {
        for (double h : new double[]{60.0, 104.0, 125.0, 130.0, 136.0, 160.0}) {
            FisheyeLens lens = wide(h, FisheyeLens.DEFAULT_STRENGTH);
            double halfW = 960.0;
            assertEquals(halfW / Math.toRadians(h * 0.5), lens.focal(), 0.0);
            assertEquals(Math.hypot(960.0, 540.0) / lens.focal() < Math.toRadians(FisheyeLens.MAX_FIELD_DEG)
                    ? Math.hypot(960.0, 540.0) / lens.focal() : Math.toRadians(FisheyeLens.MAX_FIELD_DEG), lens.circleRad(), 0.0);
            assertEquals(lens.focal() * lens.circleRad(), lens.circleRadius(), 0.0);
            assertEquals(Math.min(h / (16.0 / 9.0), 160.0), FisheyeLens.verticalDeg(h, 16.0 / 9.0, FisheyeLens.DEFAULT_STRENGTH), 0.0);
            assertEquals(FisheyeLens.of(h, ImageArea.full(1920, 1080)), lens);
        }
    }

    @Test
    void inverseAndDerivativeMatchTheMapping() {
        for (double strength : STRENGTHS) {
            double bend = FisheyeLens.bend(strength);
            for (double theta = 0.0; theta < Math.toRadians(80.0); theta += 0.02) {
                double r = FisheyeLens.map(theta, bend);
                assertEquals(theta, FisheyeLens.unmap(r, bend), 1.0E-12, "Stärke " + strength);
                double h = 1.0E-6;
                double numeric = (FisheyeLens.map(theta + h, bend) - FisheyeLens.map(Math.max(0.0, theta - h), bend)) / (theta + h - Math.max(0.0, theta - h));
                assertEquals(numeric, FisheyeLens.derivative(theta, bend), 1.0E-6, "Stärke " + strength + " θ " + theta);
                assertEquals(1.0, FisheyeLens.derivative(0.0, bend), 1.0E-12);
            }
        }
        assertEquals(Double.POSITIVE_INFINITY, FisheyeLens.unmap(2.0, -0.25), 0.0);
    }

    @Test
    void imageWidthStaysTheModuleFieldAtEveryStrength() {
        for (double strength : STRENGTHS) {
            for (double h : new double[]{104.0, 125.0, 130.0, 136.0}) {
                FisheyeLens lens = wide(h, strength);
                double[] out = new double[2];
                assertTrue(lens.tangentAt(960.0, 0.0, out));
                assertEquals(Math.tan(Math.toRadians(h * 0.5)), out[0], 1.0E-9, "H " + h + " Stärke " + strength);
                assertTrue(lens.project(Math.tan(Math.toRadians(h * 0.5)), 0.0, 1.0, out));
                assertEquals(960.0, out[0], 1.0E-6);
            }
        }
    }

    @Test
    void strongerLensMagnifiesTheCentreNarrowsTheHeightAndWidensTheDiagonal() {
        for (double h : new double[]{104.0, 125.0, 130.0}) {
            double magnification = 1.0;
            double vertical = 180.0;
            double diagonal = 2.0 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(h * 0.5)) * Math.sqrt(1.0 + 81.0 / 256.0)));
            for (double strength : STRENGTHS) {
                FisheyeLens lens = wide(h, strength);
                assertTrue(lens.centreMagnification() > magnification, "H " + h + " Stärke " + strength);
                magnification = lens.centreMagnification();
                double v = FisheyeLens.verticalDeg(h, 16.0 / 9.0, strength);
                double d = FisheyeLens.diagonalDeg(h, 16.0 / 9.0, strength);
                assertTrue(v < vertical && d > diagonal, "H " + h + " Stärke " + strength + ": " + v + " / " + d);
                vertical = v;
                diagonal = d;
            }
            assertEquals(FovMath.verticalFromHorizontal(h, 16.0 / 9.0), FisheyeLens.verticalDeg(h, 16.0 / 9.0, 1.0E-9), 1.0E-4);
        }
        assertEquals(2.09, wide(130.0, 1.0).centreMagnification(), 0.01);
        assertEquals(1.93, wide(125.0, 1.0).centreMagnification(), 0.01);
    }

    @Test
    void weakLensIsAlmostFlat() {
        FisheyeLens lens = wide(125.0, 0.05);
        assertEquals(1.0, lens.centreMagnification(), 0.1);
        assertEquals(1.0, lens.centreUpscale(), 0.1);
        assertTrue(lens.fullImage());
    }

    @Test
    void strongestLensStillFillsTheBuiltInWideImages() {
        for (double h : new double[]{104.0, 125.0, 130.0}) {
            assertTrue(wide(h, 1.0).fullImage(), "H " + h);
        }
        assertTrue(wide(133.0, 1.0).fullImage());
        assertFalse(wide(136.0, 1.0).fullImage());
        assertTrue(wide(136.0, FisheyeLens.DEFAULT_STRENGTH).fullImage());
        assertEquals(77.4, Math.toDegrees(wide(130.0, 1.0).cornerRad()), 0.1);
        assertTrue(wide(130.0, 1.0).centreUpscale() < 4.0, "Hochrechnung " + wide(130.0, 1.0).centreUpscale());
        assertTrue(wide(130.0, 1.0).centreUpscale() > wide(130.0, FisheyeLens.DEFAULT_STRENGTH).centreUpscale());
        assertFalse(boxed(125.0, 1.0).fullImage());
        assertTrue(boxed(125.0, FisheyeLens.DEFAULT_STRENGTH).fullImage());
    }

    @Test
    void everyPixelInsideTheCircleHasRenderedSourceAtEveryStrength() {
        double[] out = new double[2];
        for (double strength : STRENGTHS) {
            for (ImageArea area : new ImageArea[]{ImageArea.full(1920, 1080), ImageArea.of(ImageFormat.FOUR_THREE, 1920, 1080),
                    ImageArea.full(2560, 1080), ImageArea.full(1080, 1920)}) {
                for (double h = 60.0; h <= 160.0; h += 10.0) {
                    FisheyeLens lens = FisheyeLens.of(h, strength, area);
                    for (int i = 0; i <= 30; i++) {
                        for (int j = 0; j <= 30; j++) {
                            double dx = -area.width() * 0.5 + area.width() * i / 30.0;
                            double dy = -area.height() * 0.5 + area.height() * j / 30.0;
                            if (lens.covers(dx, dy)) {
                                assertTrue(lens.sourceAt(dx, dy, out), area + " H " + h + " Stärke " + strength + " Pixel " + dx + "," + dy);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void projectionAndTangentAreInverseAtEveryStrength() {
        double[] tangent = new double[2];
        double[] pixel = new double[2];
        for (double strength : STRENGTHS) {
            FisheyeLens lens = wide(130.0, strength);
            for (double dx = -900.0; dx <= 900.0; dx += 150.0) {
                for (double dy = -500.0; dy <= 500.0; dy += 125.0) {
                    if (!lens.covers(dx, dy)) {
                        continue;
                    }
                    assertTrue(lens.tangentAt(dx, dy, tangent));
                    assertTrue(lens.project(tangent[0], tangent[1], 1.0, pixel));
                    assertEquals(dx, pixel[0], 1.0E-6);
                    assertEquals(dy, pixel[1], 1.0E-6);
                }
            }
        }
    }

    @Test
    void filterFootprintFollowsTheMapping() {
        for (double strength : STRENGTHS) {
            FisheyeLens lens = wide(125.0, strength);
            double theta = Math.toRadians(50.0);
            double h = 1.0E-6;
            double source = lens.sourceScale();
            double numeric = source * (Math.tan(theta + h) - Math.tan(theta - h))
                    / (lens.focal() * (FisheyeLens.map(theta + h, lens.bend()) - FisheyeLens.map(theta - h, lens.bend())));
            assertEquals(numeric, lens.radialFootprint(theta), 1.0E-4 * numeric);
            assertEquals(source * Math.tan(theta) / (lens.focal() * FisheyeLens.map(theta, lens.bend())), lens.tangentialFootprint(theta), 1.0E-12);
        }
    }
}
