package de.lardtom.propwash.video;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.sim.CameraOcclusion;
import de.lardtom.propwash.sim.FpvRig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageAreaTest {
    private static final List<int[]> WINDOWS = List.of(new int[]{1920, 1080}, new int[]{2560, 1080}, new int[]{1366, 768}, new int[]{1512, 982},
            new int[]{3024, 1964}, new int[]{1921, 1081}, new int[]{1280, 1024}, new int[]{1000, 800}, new int[]{800, 1200}, new int[]{854, 480},
            new int[]{640, 480}, new int[]{3, 2}, new int[]{1, 1});

    private static double tanHalf(double degrees) {
        return Math.tan(Math.toRadians(degrees) / 2.0);
    }

    @Test
    void autoFollowsTheVideoSystem() {
        assertSame(ImageFormat.FOUR_THREE, ImageFormat.AUTO.resolve(VideoSystem.ANALOG));
        assertSame(ImageFormat.WIDE, ImageFormat.AUTO.resolve(VideoSystem.DIGITAL));
        assertSame(ImageFormat.WIDE, ImageFormat.AUTO.resolve(VideoSystem.CREATIVE));
        assertSame(ImageFormat.WIDE, ImageFormat.AUTO.resolve(null));
        for (VideoSystem system : VideoSystem.values()) {
            assertSame(ImageFormat.WIDE, ImageFormat.WIDE.resolve(system));
            assertSame(ImageFormat.FOUR_THREE, ImageFormat.FOUR_THREE.resolve(system));
        }
        assertSame(ImageFormat.WIDE, ImageFormat.WIDE.resolve(null));
    }

    @Test
    void wideFillsTheWholeWindow() {
        for (int[] window : WINDOWS) {
            ImageArea wide = ImageArea.of(ImageFormat.WIDE, window[0], window[1]);
            assertTrue(wide.full());
            assertEquals(ImageArea.full(window[0], window[1]), wide);
            assertEquals(ImageArea.full(window[0], window[1]), ImageArea.of(ImageFormat.AUTO, window[0], window[1]));
            assertEquals((double) window[0] / window[1], wide.spanAspect(), 0.0);
        }
    }

    @Test
    void fourThreeIsPillarboxedOnWideWindows() {
        ImageArea area = ImageArea.of(ImageFormat.FOUR_THREE, 1920, 1080);
        assertEquals(new ImageArea(240, 0, 1440, 1080, 1920, 1080), area);
        assertTrue(area.pillarboxed());
        assertFalse(area.letterboxed());
        assertEquals(new ImageArea(560, 0, 1440, 1080, 2560, 1080), ImageArea.of(ImageFormat.FOUR_THREE, 2560, 1080));
        assertEquals(new ImageArea(171, 0, 1024, 768, 1366, 768), ImageArea.of(ImageFormat.FOUR_THREE, 1366, 768));
        assertEquals(ImageArea.full(640, 480), ImageArea.of(ImageFormat.FOUR_THREE, 640, 480));
    }

    @Test
    void fourThreeIsLetterboxedOnNarrowWindows() {
        ImageArea area = ImageArea.of(ImageFormat.FOUR_THREE, 1000, 800);
        assertEquals(new ImageArea(0, 25, 1000, 750, 1000, 800), area);
        assertTrue(area.letterboxed());
        assertFalse(area.pillarboxed());
        assertEquals(new ImageArea(0, 300, 800, 600, 800, 1200), ImageArea.of(ImageFormat.FOUR_THREE, 800, 1200));
    }

    @Test
    void barsAreEqualOnBothSidesAndTheImageStaysFourThree() {
        for (int[] window : WINDOWS) {
            ImageArea area = ImageArea.of(ImageFormat.FOUR_THREE, window[0], window[1]);
            assertEquals(area.x(), area.screenWidth() - area.right(), "Ränder links/rechts bei " + window[0] + "x" + window[1]);
            assertEquals(area.y(), area.screenHeight() - area.bottom(), "Ränder oben/unten bei " + window[0] + "x" + window[1]);
            assertFalse(area.pillarboxed() && area.letterboxed());
            if (window[0] >= 640) {
                assertEquals(4.0 / 3.0, area.aspect(), 2.0 / Math.min(area.width(), area.height()), window[0] + "x" + window[1]);
            }
            assertTrue(area.contains(area.x(), area.y()));
            assertFalse(area.contains(area.right(), area.y()));
        }
    }

    @Test
    void degenerateWindowsStayValid() {
        ImageArea zero = ImageArea.of(ImageFormat.FOUR_THREE, 0, 0);
        assertEquals(1, zero.width());
        assertEquals(1, zero.height());
        assertThrows(IllegalArgumentException.class, () -> new ImageArea(10, 0, 100, 10, 100, 10));
    }

    @Test
    void theHorizontalFieldOfViewSpansTheFourThreeImage() {
        for (int[] window : WINDOWS) {
            if (window[0] < 100) {
                continue;
            }
            for (double horizontal : new double[]{FovMath.MIN_HORIZONTAL_DEGREES, 104.0, 125.0, 130.0, FovMath.MAX_HORIZONTAL_DEGREES}) {
                ImageArea area = ImageArea.of(ImageFormat.FOUR_THREE, window[0], window[1]);
                double tanY = tanHalf(FovMath.verticalFromHorizontal(horizontal, area));
                double screenTanX = tanY * window[0] / window[1];
                assertEquals(tanHalf(horizontal), area.imageTangentX(screenTanX), 2.0E-6 * tanHalf(horizontal),
                        "Breite des 4:3-Bilds bei " + window[0] + "x" + window[1] + ", " + horizontal + "°");
                double imageTanY = area.imageTangentY(tanY);
                assertEquals(tanHalf(horizontal) / area.aspect(), imageTanY, 2.0E-6 * tanHalf(horizontal));
            }
        }
    }

    @Test
    void theImageMatchesARealFourThreeCamera() {
        double horizontal = 125.0;
        double camera = 2.0 * Math.toDegrees(Math.atan(tanHalf(horizontal) * 3.0 / 4.0));
        ImageArea pillar = ImageArea.of(ImageFormat.FOUR_THREE, 1440 * 4, 1080 * 4);
        assertEquals(camera, FovMath.verticalFromHorizontal(horizontal, ImageArea.of(ImageFormat.FOUR_THREE, 1920, 1080)), 1.0E-4);
        assertEquals(camera, FovMath.verticalFromHorizontal(horizontal, pillar), 1.0E-4);
        ImageArea letter = ImageArea.of(ImageFormat.FOUR_THREE, 1000, 800);
        float window = FovMath.verticalFromHorizontal(horizontal, letter);
        assertEquals(camera, FovMath.verticalWithin(window, letter.heightShare()), 1.0E-3);
        assertEquals(110.5, camera, 0.05);
        assertTrue(camera > FovMath.verticalFromHorizontal(horizontal, 16.0 / 9.0));
    }

    @Test
    void wideKeepsTodaysFieldOfViewExactly() {
        for (int[] window : WINDOWS) {
            ImageArea area = ImageArea.of(ImageFormat.WIDE, window[0], window[1]);
            for (double horizontal = FovMath.MIN_HORIZONTAL_DEGREES; horizontal <= FovMath.MAX_HORIZONTAL_DEGREES; horizontal += 7.5) {
                assertEquals(FovMath.verticalFromHorizontal(horizontal, (double) window[0] / window[1]), FovMath.verticalFromHorizontal(horizontal, area));
            }
            assertEquals(0.7F, (float) area.imageTangentX(0.7F));
            assertEquals(0.4F, (float) area.imageTangentY(0.4F));
        }
        assertEquals(87.5F, FovMath.verticalWithin(87.5F, 1.0));
        assertEquals(87.5F, FovMath.verticalWithin(87.5F, Double.NaN));
    }

    @Test
    void actionCamRecordingsKeepTheWideFieldOfView() {
        for (int[] window : WINDOWS) {
            if (window[0] < 100) {
                continue;
            }
            double horizontal = 125.0;
            ImageArea wide = ImageArea.of(ImageFormat.WIDE, window[0], window[1]);
            double wideY = tanHalf(FovMath.verticalFromHorizontal(horizontal, wide));
            double wideX = wideY * window[0] / window[1];
            ImageArea boxed = ImageArea.of(ImageFormat.FOUR_THREE, window[0], window[1]);
            double boxedY = tanHalf(FovMath.verticalFromHorizontal(horizontal, boxed));
            double boxedX = boxedY * window[0] / window[1];
            assertEquals(wideY, wide.wideTangentY(wideX, wideY), 0.0);
            assertEquals(wideY, boxed.wideTangentY(boxedX, boxedY), 2.0E-6 * wideY, window[0] + "x" + window[1]);
            assertEquals(wideX, boxed.imageTangentX(boxedX), 2.0E-6 * wideX);
            assertTrue(boxedY >= wideY - 1.0E-6 && boxedX >= wideX - 1.0E-6, "4:3-Bild deckt den 16:9-Ausschnitt ab");
        }
    }

    @Test
    void horizontalAndVerticalConvertBothWays() {
        for (double horizontal : new double[]{60.0, 90.0, 125.0, 160.0}) {
            for (double aspect : new double[]{4.0 / 3.0, 16.0 / 9.0, 21.0 / 9.0}) {
                assertEquals(horizontal, FovMath.horizontalFromVertical(FovMath.verticalFromHorizontal(horizontal, aspect), aspect), 1.0E-3);
            }
        }
    }

    @Test
    void theFourThreeAnalysisSeesTheSamePropsPlusTheTallerImage() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            FpvRig rig = FpvRig.of(build);
            double wide = CameraOcclusion.percent(rig, rig.tiltDeg(), rig.horizontalFovDeg(), FpvRig.ASPECT, CameraOcclusion.ROWS);
            double boxed = CameraOcclusion.percent(rig, rig.tiltDeg(), rig.horizontalFovDeg(), ImageFormat.FOUR_THREE_ASPECT, CameraOcclusion.ROWS);
            double tanH = tanHalf(rig.horizontalFovDeg());
            double wideArea = wide * tanH / FpvRig.ASPECT;
            double boxedArea = boxed * tanH / ImageFormat.FOUR_THREE_ASPECT;
            assertTrue(boxedArea >= wideArea * 0.995 - 0.01, build.frame().id() + ": 4:3 " + boxed + " %, 16:9 " + wide + " %");
            assertTrue(boxed >= 0.0 && boxed <= 100.0);
        }
    }
}
