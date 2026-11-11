package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarknessPulseTest {
    @Test
    void blendsInLikeTheVanillaEffectAndLingersAfterLeaving() {
        DarknessPulse pulse = new DarknessPulse();
        assertFalse(pulse.active());
        for (int i = 0; i < DarknessPulse.BLEND_TICKS - 1; i++) {
            pulse.tick(true);
        }
        assertTrue(pulse.blend() < 1.0F);
        pulse.tick(true);
        assertEquals(1.0F, pulse.blend(), 1.0E-5F);
        for (int i = 0; i < DarknessPulse.HOLD_TICKS - 1; i++) {
            pulse.tick(false);
        }
        assertEquals(1.0F, pulse.blend(), 1.0E-5F);
        for (int i = 0; i < DarknessPulse.BLEND_TICKS + 1; i++) {
            pulse.tick(false);
        }
        assertEquals(0.0F, pulse.blend(), 1.0E-5F);
        assertFalse(pulse.active());
    }

    @Test
    void lightPulsesWithTheVanillaPeriod() {
        assertEquals(DarknessPulse.LIGHT_DEPTH, DarknessPulse.lightScale(1.0F, 0.0F), 1.0E-6F);
        assertEquals(0.0F, DarknessPulse.lightScale(1.0F, 20.0F), 1.0E-6F);
        assertEquals(0.0F, DarknessPulse.lightScale(1.0F, 40.0F), 1.0E-6F);
        assertEquals(DarknessPulse.LIGHT_DEPTH, DarknessPulse.lightScale(1.0F, 80.0F), 1.0E-5F);
        assertEquals(DarknessPulse.LIGHT_DEPTH * 0.5F, DarknessPulse.lightScale(0.5F, 0.0F), 1.0E-6F);
        assertEquals(0.0F, DarknessPulse.lightScale(0.0F, 0.0F), 1.0E-6F);
        assertEquals(0.25F, DarknessPulse.brightness(1.0F, 0.75F), 1.0E-6F);
        assertEquals(0.0F, DarknessPulse.brightness(0.5F, 1.0F), 1.0E-6F);
        assertTrue(DarknessPulse.near(12.0, 0.0, 16.0));
        assertFalse(DarknessPulse.near(12.0, 1.0, 16.0));
    }

    @Test
    void wardenDarknessDimsAndDisturbsTheFeed() {
        for (FeedLook look : FeedLook.values()) {
            EffectParams clear = feed(look, 0.0F);
            EffectParams dark = feed(look, 1.0F);
            assertTrue(dark.brightness <= 1.0F - DarknessPulse.FEED_BRIGHTNESS * 0.6F + 1.0E-6F, look + " Helligkeit " + dark.brightness);
            assertTrue(dark.brightness < clear.brightness, look.name());
            assertTrue(dark.noise >= DarknessPulse.FEED_NOISE * 0.6F - 1.0E-6F, look.name());
            assertTrue(dark.vignette >= DarknessPulse.FEED_VIGNETTE - 1.0E-6F, look.name());
            assertFalse(dark.visuallyClean(), look.name());
        }
    }

    @Test
    void withoutDarknessTheFeedStaysExactlyAsBefore() {
        for (FeedLook look : FeedLook.values()) {
            EffectParams a = feed(look, 0.0F);
            FeedModel model = new FeedModel(42L);
            FeedInput input = new FeedInput().set(look, true, 0.8F, 30_000_000L);
            EffectParams b = null;
            for (int frame = 0; frame < 120; frame++) {
                b = model.update(frame * 16_666_667L, input);
            }
            assertEquals(b.brightness, a.brightness, look.name());
            assertEquals(b.noise, a.noise, look.name());
            assertEquals(b.vignette, a.vignette, look.name());
            assertEquals(b.desaturation, a.desaturation, look.name());
        }
    }

    private static EffectParams feed(FeedLook look, float darkness) {
        FeedModel model = new FeedModel(42L);
        FeedInput input = new FeedInput().set(look, true, 0.8F, 30_000_000L);
        input.darkness = darkness;
        EffectParams p = null;
        for (int frame = 0; frame < 120; frame++) {
            p = model.update(frame * 16_666_667L, input);
        }
        return p;
    }
}
