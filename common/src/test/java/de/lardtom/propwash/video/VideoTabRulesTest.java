package de.lardtom.propwash.video;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoTabRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoTabRulesTest {
    private static VideoConfig tuned() {
        VideoConfig config = new VideoConfig();
        config.creativeFovFromModule = false;
        config.fovCreative = 150;
        config.creativeLatencyFromModule = false;
        config.creativeLatencyMs = 0;
        config.creativeRangeBlocks = 2000;
        config.creativeNoise = 0.8F;
        config.creativeObstruction = false;
        return config;
    }

    @Test
    void tabShowsOnlyTheWornGoggles() {
        assertFalse(VideoTabRules.showsSettings(null));
        assertFalse(VideoTabRules.showsCreative(null));
        assertTrue(VideoTabRules.showsSettings(VideoSystem.ANALOG));
        assertFalse(VideoTabRules.showsCreative(VideoSystem.ANALOG));
        assertTrue(VideoTabRules.showsSettings(VideoSystem.DIGITAL));
        assertFalse(VideoTabRules.showsCreative(VideoSystem.DIGITAL));
        assertTrue(VideoTabRules.showsCreative(VideoSystem.CREATIVE));
    }

    @Test
    void creativeValuesNeverReachNormalGoggles() {
        VideoConfig config = tuned();
        for (VideoSystem goggles : new VideoSystem[]{null, VideoSystem.ANALOG, VideoSystem.DIGITAL}) {
            String name = String.valueOf(goggles);
            for (VideoType camera : VideoType.values()) {
                assertEquals(FovMath.cameraHorizontalDegrees(camera), config.horizontalFov(goggles, camera), 1.0E-9, name);
                assertEquals(camera.latencyMs, config.latencyMs(goggles, camera.latencyMs), name);
                assertEquals(camera.rangeBlocks, config.rangeBlocks(goggles, camera.rangeBlocks), 1.0E-9, name);
            }
            assertEquals(0.0F, config.extraNoise(goggles), name);
            assertTrue(config.obstructs(goggles), name);
        }
    }

    @Test
    void creativeGogglesUseTheirOwnValues() {
        VideoConfig config = tuned();
        VideoType camera = VideoType.values()[0];
        assertEquals(150.0, config.horizontalFov(VideoSystem.CREATIVE, camera), 1.0E-9);
        assertEquals(0, config.latencyMs(VideoSystem.CREATIVE, 28));
        assertEquals(2000.0, config.rangeBlocks(VideoSystem.CREATIVE, 300.0), 1.0E-9);
        assertEquals(0.8F, config.extraNoise(VideoSystem.CREATIVE));
        assertFalse(config.obstructs(VideoSystem.CREATIVE));
        config.creativeRangeBlocks = 0;
        assertTrue(Double.isInfinite(config.rangeBlocks(VideoSystem.CREATIVE, 300.0)));
    }

    @Test
    void savingWithNormalGogglesKeepsTheStoredCreativeValues() {
        VideoConfig stored = new VideoConfig();
        VideoConfig draft = tuned();
        draft.effectStrength = 0.25F;
        for (VideoSystem goggles : new VideoSystem[]{VideoSystem.ANALOG, VideoSystem.DIGITAL}) {
            VideoConfig saved = VideoTabRules.saved(stored, draft, goggles);
            assertEquals(0.25F, saved.effectStrength);
            assertTrue(saved.creativeFovFromModule);
            assertTrue(saved.creativeLatencyFromModule);
            assertEquals(stored.creativeRangeBlocks, saved.creativeRangeBlocks);
            assertEquals(stored.creativeNoise, saved.creativeNoise);
            assertEquals(stored.creativeObstruction, saved.creativeObstruction);
            assertEquals(stored.fovCreative, saved.fovCreative);
        }
        VideoConfig creative = VideoTabRules.saved(stored, draft, VideoSystem.CREATIVE);
        assertEquals(2000, creative.creativeRangeBlocks);
        assertFalse(creative.creativeFovFromModule);
    }

    @Test
    void withoutGogglesNothingIsSavedOrReset() {
        VideoConfig stored = tuned();
        stored.effectStrength = 0.4F;
        VideoConfig draft = new VideoConfig();
        VideoConfig saved = VideoTabRules.saved(stored, draft, null);
        assertEquals(0.4F, saved.effectStrength);
        assertEquals(2000, saved.creativeRangeBlocks);
        VideoConfig reset = VideoTabRules.defaults(stored, null);
        assertEquals(0.4F, reset.effectStrength);
        assertEquals(2000, reset.creativeRangeBlocks);
    }

    @Test
    void defaultsLeaveHiddenCreativeValuesAlone() {
        VideoConfig current = tuned();
        current.effectStrength = 0.1F;
        VideoConfig analog = VideoTabRules.defaults(current, VideoSystem.ANALOG);
        assertEquals(new VideoConfig().effectStrength, analog.effectStrength);
        assertEquals(2000, analog.creativeRangeBlocks);
        assertFalse(analog.creativeFovFromModule);
        VideoConfig creative = VideoTabRules.defaults(current, VideoSystem.CREATIVE);
        assertEquals(new VideoConfig().creativeRangeBlocks, creative.creativeRangeBlocks);
        assertTrue(creative.creativeFovFromModule);
    }
}
