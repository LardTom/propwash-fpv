package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FisheyeLensTest {
    private static final double EPS = 1.0E-9;

    private static FisheyeLens wide(double horizontal) {
        return FisheyeLens.of(horizontal, ImageArea.full(1920, 1080));
    }

    private static FisheyeLens boxed(double horizontal) {
        return FisheyeLens.of(horizontal, ImageArea.of(ImageFormat.FOUR_THREE, 1920, 1080));
    }

    @Test
    void theImageWidthSpansTheHorizontalFieldOfTheModule() {
        for (double h : new double[]{60.0, 104.0, 125.0, 130.0, 145.0, 160.0}) {
            FisheyeLens lens = wide(h);
            double[] out = new double[2];
            assertTrue(lens.tangentAt(960.0, 0.0, out));
            assertEquals(Math.tan(Math.toRadians(h * 0.5)), out[0], 1.0E-9, "H " + h);
            assertEquals(0.0, out[1], EPS);
        }
    }

    @Test
    void angleGrowsLinearlyWithTheDistanceFromTheCentre() {
        FisheyeLens lens = wide(130.0);
        double[] out = new double[2];
        for (double r = 0.0; r <= 960.0; r += 60.0) {
            lens.tangentAt(r * 0.6, -r * 0.8, out);
            double angle = Math.atan(Math.hypot(out[0], out[1]));
            assertEquals(Math.toRadians(65.0) * r / 960.0, angle, 1.0E-9);
        }
    }

    @Test
    void theCentreLooksAsMuchBiggerAsTheDesignNoteMeasured() {
        assertEquals(1.41, wide(104.0).centreMagnification(), 0.01);
        assertEquals(1.76, wide(125.0).centreMagnification(), 0.01);
        assertEquals(1.89, wide(130.0).centreMagnification(), 0.01);
        assertEquals(1.89, boxed(130.0).centreMagnification(), 0.01);
    }

    @Test
    void verticalAndDiagonalMatchRealFpvCameras() {
        assertEquals(73.1, FisheyeLens.verticalDeg(130.0, 16.0 / 9.0), 0.05);
        assertEquals(96.0, FisheyeLens.verticalDeg(128.0, 4.0 / 3.0), 0.05);
        assertEquals(160.0, FisheyeLens.diagonalDeg(128.0, 4.0 / 3.0), 0.05);
        double sixteenNine = 130.0 / FisheyeLens.diagonalDeg(130.0, 16.0 / 9.0);
        double fourThree = 104.0 / FisheyeLens.diagonalDeg(104.0, 4.0 / 3.0);
        assertTrue(sixteenNine > 0.84 && sixteenNine < 0.88, "H/D 16:9 " + sixteenNine);
        assertTrue(fourThree > 0.78 && fourThree < 0.82, "H/D 4:3 " + fourThree);
        assertEquals(160.0, FisheyeLens.diagonalDeg(160.0, 16.0 / 9.0), EPS);
    }

    @Test
    void everyImagePixelInsideTheCircleHasRenderedSource() {
        ImageArea[] areas = {ImageArea.full(1920, 1080), ImageArea.of(ImageFormat.FOUR_THREE, 1920, 1080), ImageArea.full(2560, 1080),
                ImageArea.of(ImageFormat.FOUR_THREE, 1000, 1000), ImageArea.full(1080, 1920), ImageArea.full(3024, 1964)};
        double[] out = new double[2];
        for (ImageArea area : areas) {
            for (double h = 60.0; h <= 160.0; h += 5.0) {
                FisheyeLens lens = FisheyeLens.of(h, area);
                assertTrue(lens.renderVerticalDeg() < 179.0, area + " " + h);
                double halfW = area.width() * 0.5;
                double halfH = area.height() * 0.5;
                for (int i = 0; i <= 40; i++) {
                    for (int j = 0; j <= 40; j++) {
                        double dx = -halfW + area.width() * i / 40.0;
                        double dy = -halfH + area.height() * j / 40.0;
                        if (!lens.covers(dx, dy)) {
                            continue;
                        }
                        assertTrue(lens.sourceAt(dx, dy, out), area + " H " + h + " Pixel " + dx + "," + dy + " → " + out[0] + "," + out[1]);
                    }
                }
            }
        }
    }

    @Test
    void theRenderedViewIsNoWiderThanTheImageNeeds() {
        FisheyeLens lens = wide(130.0);
        double[] out = new double[2];
        assertTrue(lens.fullImage());
        assertTrue(lens.sourceAt(960.0, 540.0, out));
        assertEquals(960.0, Math.abs(out[0]), 1.0E-6);
        assertEquals(540.0, Math.abs(out[1]), 1.0E-6);
        FisheyeLens four = boxed(125.0);
        assertTrue(four.sourceAt(720.0, 540.0, out));
        assertEquals(540.0, Math.abs(out[1]), 1.0E-6);
        assertTrue(Math.abs(out[0]) < 960.0);
    }

    @Test
    void renderedFieldForTheBuiltInCameras() {
        assertEquals(121.3, wide(130.0).renderVerticalDeg(), 0.1);
        assertEquals(144.9, wide(130.0).renderHorizontalDeg(), 0.1);
        assertEquals(74.6, Math.toDegrees(wide(130.0).cornerRad()), 0.05);
        assertTrue(wide(104.0).renderVerticalDeg() < wide(125.0).renderVerticalDeg());
        assertEquals(2.79, wide(130.0).centreUpscale(), 0.01);
        assertEquals(1.64, wide(104.0).centreUpscale(), 0.01);
        assertTrue(boxed(125.0).centreUpscale() > wide(125.0).centreUpscale());
    }

    @Test
    void veryWideLensesGetARoundImageCircle() {
        FisheyeLens lens = wide(160.0);
        assertFalse(lens.fullImage());
        assertEquals(FisheyeLens.MAX_FIELD_DEG, Math.toDegrees(lens.circleRad()), 1.0E-9);
        assertFalse(lens.covers(960.0, 540.0));
        assertTrue(lens.covers(960.0, 0.0));
        assertTrue(wide(139.0).fullImage());
        assertFalse(boxed(130.0).fullImage());
        assertTrue(boxed(125.0).fullImage());
    }

    @Test
    void projectingADirectionIsTheInverseOfTheTangent() {
        FisheyeLens lens = boxed(125.0);
        double[] tangent = new double[2];
        double[] image = new double[2];
        for (int i = 0; i < 200; i++) {
            double dx = Math.sin(i * 1.7) * 700.0;
            double dy = Math.cos(i * 2.3) * 520.0;
            assertTrue(lens.tangentAt(dx, dy, tangent));
            assertTrue(lens.project(tangent[0], tangent[1], 1.0, image));
            assertEquals(dx, image[0], 1.0E-6);
            assertEquals(dy, image[1], 1.0E-6);
        }
        assertTrue(lens.project(0.0, 0.0, 5.0, image));
        assertEquals(0.0, image[0], EPS);
        assertFalse(lens.project(0.0, 0.0, -5.0, image));
        assertFalse(lens.project(1.0, 0.0, -0.5, image));
    }

    @Test
    void theFilterFootprintGrowsTowardsTheEdge() {
        FisheyeLens lens = wide(130.0);
        assertEquals(1.0 / lens.centreUpscale(), lens.radialFootprint(0.0), 1.0E-9);
        assertEquals(1.0 / lens.centreUpscale(), lens.tangentialFootprint(0.0), 1.0E-9);
        double previous = 0.0;
        for (double theta = 0.0; theta < lens.circleRad(); theta += 0.05) {
            double radial = lens.radialFootprint(theta);
            assertTrue(radial > previous);
            assertTrue(lens.tangentialFootprint(theta) <= radial + 1.0E-12);
            previous = radial;
        }
        assertTrue(lens.radialFootprint(Math.toRadians(65.0)) > 1.5);
    }

    @Test
    void centreEquivalentFieldHasTheSameScaleInTheMiddle() {
        FisheyeLens lens = wide(130.0);
        double vertical = lens.centreEquivalentVerticalDeg();
        double focal = 540.0 / Math.tan(Math.toRadians(vertical) * 0.5);
        assertEquals(lens.focal(), focal, 1.0E-6);
        assertTrue(vertical < FovMath.verticalFromHorizontal(130.0, 16.0 / 9.0));
    }

    @Test
    void invalidInputFallsBackToSaneValues() {
        FisheyeLens lens = FisheyeLens.of(Double.NaN, 0.0, 0.0, 0.0, 0.0);
        assertTrue(Double.isFinite(lens.focal()) && lens.focal() > 0.0);
        assertTrue(Double.isFinite(lens.tanY()) && lens.tanY() > 0.0);
    }
}
