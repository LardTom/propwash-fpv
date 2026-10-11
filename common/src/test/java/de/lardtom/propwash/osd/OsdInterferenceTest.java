package de.lardtom.propwash.osd;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.sim.BatteryState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdInterferenceTest {
    @Test
    void onlyWeakAnalogVideoJittersTheOsdAndOnlySlightly() {
        assertEquals(0.0F, OsdInterference.jitterAmplitude(VideoSystem.ANALOG, 0.4));
        assertEquals(0.0F, OsdInterference.jitterAmplitude(VideoSystem.DIGITAL, 0.0));
        assertEquals(0.0F, OsdInterference.jitterAmplitude(VideoSystem.CREATIVE, 0.0));
        assertEquals(0.0F, OsdInterference.jitterAmplitude(null, 0.0));
        assertEquals(OsdInterference.MAX_JITTER, OsdInterference.jitterAmplitude(VideoSystem.ANALOG, 0.0), 1.0E-6);
        assertTrue(OsdInterference.MAX_JITTER <= 1.0F);
        assertTrue(OsdInterference.jitterAmplitude(VideoSystem.ANALOG, 0.1) > OsdInterference.jitterAmplitude(VideoSystem.ANALOG, 0.2));
    }

    @Test
    void sharedShiftFollowsTheVideoButStaysReadable() {
        assertEquals(0.0F, OsdInterference.sharedShift(VideoSystem.DIGITAL, 0.1, 640));
        assertEquals(0.0F, OsdInterference.sharedShift(null, 0.1, 640));
        assertEquals(0.0F, OsdInterference.sharedShift(VideoSystem.ANALOG, Double.NaN, 640));
        float small = OsdInterference.sharedShift(VideoSystem.ANALOG, 0.01, 640);
        assertTrue(small > 0.0F && small < 1.0F, "small shift " + small);
        assertEquals(OsdInterference.MAX_SHIFT, OsdInterference.sharedShift(VideoSystem.ANALOG, 0.1, 640), 1.0E-6F);
        assertEquals(-OsdInterference.MAX_SHIFT, OsdInterference.sharedShift(VideoSystem.ANALOG, -0.1, 640), 1.0E-6F);
    }

    @Test
    void noiseIsDeterministicAndRoughlyUniform() {
        assertEquals(OsdInterference.noise(3, 42L), OsdInterference.noise(3, 42L));
        int below = 0;
        int samples = 20_000;
        for (int i = 0; i < samples; i++) {
            double value = OsdInterference.noise(i % 41, i / 41);
            assertTrue(value >= 0.0 && value < 1.0);
            if (value < 0.3) {
                below++;
            }
        }
        assertEquals(0.3, below / (double) samples, 0.02);
    }

    @Test
    void batteryStateFallbackFromCharge() {
        assertEquals(BatteryState.OK, OsdSampler.batteryStateFor(0.5));
        assertEquals(BatteryState.LOW, OsdSampler.batteryStateFor(0.15));
        assertEquals(BatteryState.CRITICAL, OsdSampler.batteryStateFor(0.05));
        assertEquals(BatteryState.EMPTY, OsdSampler.batteryStateFor(0.0));
        assertEquals(BatteryState.OK, OsdSampler.batteryStateFor(Double.NaN));
    }
}
