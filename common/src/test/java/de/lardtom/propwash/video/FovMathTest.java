package de.lardtom.propwash.video;

import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import de.lardtom.propwash.build.VideoType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FovMathTest {
    @Test
    void squareScreenKeepsTheAngle() {
        assertEquals(90.0F, FovMath.verticalFromHorizontal(90.0, 1.0), 1.0E-4F);
    }

    @Test
    void wideScreenNarrowsTheVerticalAngle() {
        float vertical = FovMath.verticalFromHorizontal(120.0, 16.0 / 9.0);
        assertEquals(88.51F, vertical, 0.05F);
        assertTrue(FovMath.verticalFromHorizontal(160.0, 16.0 / 9.0) < 160.0F);
    }

    @Test
    void invalidAspectFallsBackToWidescreen() {
        assertEquals(FovMath.verticalFromHorizontal(120.0, 16.0 / 9.0), FovMath.verticalFromHorizontal(120.0, 0.0), 1.0E-5F);
        assertEquals(FovMath.verticalFromHorizontal(120.0, 16.0 / 9.0), FovMath.verticalFromHorizontal(120.0, Double.NaN), 1.0E-5F);
    }

    @Test
    void theCameraOfTheVideoModuleDecidesTheFieldOfView() {
        assertEquals(125.0, FovMath.cameraHorizontalDegrees(VideoType.ANALOG_NANO), 0.0);
        assertEquals(125.0, FovMath.cameraHorizontalDegrees(VideoType.ANALOG_RACE), 0.0);
        assertEquals(104.0, FovMath.cameraHorizontalDegrees(VideoType.DIGITAL_LITE), 0.0);
        assertEquals(130.0, FovMath.cameraHorizontalDegrees(VideoType.DIGITAL_HD), 0.0);
        for (VideoType video : VideoType.values()) {
            if (video.builtin()) {
                assertTrue(FovMath.fromModule(video), video.key() + " hat ein eigenes Kamera-Sichtfeld");
            }
        }
    }

    @Test
    void modulesWithoutAFieldOfViewKeepTheOldDefaultOfTheirSystem() {
        VideoDefinition analog = VideoDefinition.builder(Identifier.fromNamespaceAndPath("fovtest", "plain_analog")).mass(5.0).link(VideoLink.ANALOG)
                .latency(12).range(200).imageQuality(0.5).build();
        VideoDefinition digital = VideoDefinition.builder(Identifier.fromNamespaceAndPath("fovtest", "plain_digital")).mass(5.0).link(VideoLink.DIGITAL)
                .latency(25).range(200).imageQuality(0.8).build();
        VideoDefinition fisheye = VideoDefinition.builder(Identifier.fromNamespaceAndPath("fovtest", "fisheye")).mass(5.0).link(VideoLink.DIGITAL)
                .latency(25).range(200).imageQuality(0.8).cameraFov(175.0).build();
        assertEquals(FovMath.DEFAULT_ANALOG_DEGREES, FovMath.cameraHorizontalDegrees(analog), 0.0);
        assertEquals(FovMath.DEFAULT_DIGITAL_DEGREES, FovMath.cameraHorizontalDegrees(digital), 0.0);
        assertEquals(FovMath.MAX_HORIZONTAL_DEGREES, FovMath.cameraHorizontalDegrees(fisheye), 0.0);
        assertEquals(FovMath.DEFAULT_ANALOG_DEGREES, FovMath.cameraHorizontalDegrees((VideoType) null), 0.0);
    }
}
