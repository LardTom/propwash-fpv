package de.lardtom.propwash.video.record;

import de.lardtom.propwash.build.ActionCamSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingQualityTest {
    @Test
    void levelsAreOrderedBySizeAndQuality() {
        for (int height : RecordingFormat.HEIGHTS) {
            for (int fps : RecordingFormat.RATES) {
                RecordingFormat format = RecordingFormat.of(height, fps);
                assertTrue(RecordingQuality.HIGH.qp() < RecordingQuality.STANDARD.qp());
                assertTrue(RecordingQuality.STANDARD.qp() < RecordingQuality.SMALL.qp());
                assertTrue(RecordingQuality.HIGH.maxBitrate(format) > RecordingQuality.STANDARD.maxBitrate(format));
                assertTrue(RecordingQuality.STANDARD.maxBitrate(format) > RecordingQuality.SMALL.maxBitrate(format));
                assertTrue(RecordingQuality.HIGH.typicalMegabytesPerMinute(format) > RecordingQuality.STANDARD.typicalMegabytesPerMinute(format));
                assertTrue(RecordingQuality.STANDARD.typicalMegabytesPerMinute(format) > RecordingQuality.SMALL.typicalMegabytesPerMinute(format));
                for (RecordingQuality quality : RecordingQuality.values()) {
                    assertTrue(quality.typicalBitrate(format) <= quality.maxBitrate(format));
                    assertTrue(quality.typicalMegabytesPerMinute(format) <= quality.maxMegabytesPerMinute(format));
                    assertTrue(quality.typicalMegabytesPerMinute(format) >= 5);
                }
            }
        }
    }

    @Test
    void standardKeepsTheOldBitrateAsItsCeiling() {
        RecordingFormat full = RecordingFormat.of(1080, 60);
        assertEquals(full.bitrate(), RecordingQuality.STANDARD.maxBitrate(full));
        assertEquals(RecordingQuality.STANDARD, RecordingQuality.DEFAULT);
        assertTrue(RecordingQuality.HIGH.maxBitrate(full) <= 50_000_000L);
        assertEquals(110, RecordingQuality.STANDARD.typicalMegabytesPerMinute(full));
        assertEquals("propwash.video.record.quality.small", RecordingQuality.SMALL.translationKey());
    }

    @Test
    void grainIsSubtleOnlyInTheDarkAndCanBeSwitchedOff() {
        float light = CameraLook.grain(ActionCamSpec.LIGHT, true);
        float heavy = CameraLook.grain(ActionCamSpec.HEAVY, true);
        assertTrue(light > heavy && heavy > 0.0F);
        assertTrue(light * 255.0F < 4.0F, "light grain " + light * 255.0F + " levels");
        assertEquals(0.0F, CameraLook.grain(ActionCamSpec.LIGHT, false));
        assertEquals(1.0, CameraLook.grainWeight(0.0), 1.0E-9);
        assertTrue(CameraLook.grainWeight(0.1) > 0.3 && CameraLook.grainWeight(0.1) < 0.7);
        assertEquals(0.0, CameraLook.grainWeight(CameraLook.GRAIN_GONE_ABOVE), 1.0E-9);
        assertEquals(0.0, CameraLook.grainWeight(0.6), 1.0E-9);
        assertTrue(CameraLook.sharpness(ActionCamSpec.HEAVY) > 0.0F && CameraLook.sharpness(ActionCamSpec.LIGHT) < 0.0F);
    }
}
