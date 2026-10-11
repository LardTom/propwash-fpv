package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlightMathTest {
    @Test
    void darknessFollowsTheLightLevel() {
        assertEquals(1.0, HeadlightMath.darkness(0, 0.0), 1.0E-9);
        assertEquals(1.0, HeadlightMath.darkness(2, 0.0), 1.0E-9);
        assertEquals(0.0, HeadlightMath.darkness(15, 0.0), 1.0E-9);
        assertEquals(0.0, HeadlightMath.darkness(11, 0.0), 1.0E-9);
        double dusk = HeadlightMath.darkness(6, 0.0);
        assertTrue(dusk > 0.2 && dusk < 0.8, "Dämmerung " + dusk);
        assertTrue(HeadlightMath.darkness(0, 1.0) < HeadlightMath.darkness(0, 0.0));
        assertTrue(HeadlightMath.darkness(0, 1.0) > 0.5);
    }

    @Test
    void gainNeedsLightAndDarkness() {
        assertEquals(HeadlightMath.GAIN, HeadlightMath.gain(1.0, 1.0), 1.0E-9);
        assertEquals(0.0, HeadlightMath.gain(0.0, 1.0), 1.0E-9);
        assertEquals(0.0, HeadlightMath.gain(1.0, 0.0), 1.0E-9);
        assertEquals(0.0, HeadlightMath.gain(1.0, 1.0E-4), 1.0E-9);
    }

    @Test
    void lightFallsOffWithDistanceAndAngle() {
        assertEquals(1.0, HeadlightMath.attenuation(0.0), 1.0E-9);
        assertEquals(0.5, HeadlightMath.attenuation(HeadlightMath.FALLOFF_BLOCKS), 1.0E-9);
        assertTrue(HeadlightMath.attenuation(10.0) < HeadlightMath.attenuation(5.0));
        assertEquals(0.0, HeadlightMath.attenuation(HeadlightMath.RANGE_BLOCKS), 1.0E-9);
        assertEquals(0.0, HeadlightMath.attenuation(Double.NaN), 1.0E-9);
        assertEquals(1.0, HeadlightMath.cone(1.0), 1.0E-9);
        assertEquals(HeadlightMath.SPILL, HeadlightMath.cone(Math.cos(Math.toRadians(80.0))), 1.0E-9);
        double edge = HeadlightMath.cone(Math.cos(Math.toRadians(30.0)));
        assertTrue(edge > HeadlightMath.SPILL && edge < 1.0);
        double rim = HeadlightMath.cone(Math.cos(Math.toRadians(HeadlightMath.CONE_INNER_DEG + (HeadlightMath.CONE_OUTER_DEG - HeadlightMath.CONE_INNER_DEG) * 0.75)));
        assertTrue(rim < 0.15, "Rand des Kegels fällt deutlich ab: " + rim);
    }

    @Test
    void headlightIsWarmWhiteAndFollowsTheLedColourSlightly() {
        float[] white = HeadlightMath.tint(1.0F, 1.0F, 1.0F);
        assertEquals(1.0F, white[0], 1.0E-6F);
        assertTrue(white[1] < 1.0F && white[2] < white[1], "warmweiß");
        float[] cyan = HeadlightMath.tint(0.0F, 1.0F, 1.0F);
        assertEquals(1.0F, Math.max(cyan[0], Math.max(cyan[1], cyan[2])), 1.0E-6F);
        assertTrue(cyan[0] < white[0] && cyan[0] > 0.5F, "Cyan färbt nur leicht");
    }

    @Test
    void darkCaveWallBecomesVisible() {
        double caveWall = 0.03;
        double boost = HeadlightMath.boost(caveWall, HeadlightMath.gain(1.0, 1.0), 1.0, HeadlightMath.attenuation(3.0));
        assertTrue(caveWall * boost > 0.2, "Wand in 3 Blöcken: " + caveWall * boost);
        double brightSky = HeadlightMath.boost(0.9, HeadlightMath.gain(1.0, 1.0), 1.0, 1.0);
        assertEquals(1.0, brightSky, 1.0E-9);
    }

    @Test
    void blackPixelsGetANeutralLiftAndDarkHuesAreNotAmplified() {
        double near = HeadlightMath.lift(HeadlightMath.GAIN, 1.0, HeadlightMath.attenuation(2.0));
        assertTrue(near > 0.03, "Schwarz vor der Linse bleibt schwarz: " + near);
        double far = HeadlightMath.lift(HeadlightMath.GAIN, 1.0, HeadlightMath.attenuation(8.0));
        assertTrue(far > 0.0 && far < near);
        assertEquals(0.0, HeadlightMath.lift(0.0, 1.0, 1.0), 1.0E-9);
        assertEquals(0.0, HeadlightMath.hueTrust(0.01), 1.0E-9);
        assertEquals(1.0, HeadlightMath.hueTrust(0.3), 1.0E-9);
        assertTrue(HeadlightMath.attenuation(7.0) > 0.5, "Wände 5 bis 10 Blöcke voraus werden sichtbar hell");
    }

    @Test
    void smoothingApproachesTheTarget() {
        assertEquals(0.7, HeadlightMath.smooth(Double.NaN, 0.7, 0.016), 1.0E-9);
        double step = HeadlightMath.smooth(0.0, 1.0, 0.05);
        assertTrue(step > 0.0 && step < 0.5);
        assertEquals(1.0, HeadlightMath.smooth(0.0, 1.0, 100.0), 1.0E-6);
        assertEquals(0.3, HeadlightMath.smooth(0.3, 1.0, 0.0), 1.0E-9);
    }

    @Test
    void overlayMatchesTheConeAndGain() {
        double radius = HeadlightMath.overlayRadius(90.0, 100.0);
        assertEquals(Math.tan(Math.toRadians(HeadlightMath.CONE_OUTER_DEG)) * 100.0, radius, 1.0E-6);
        assertEquals(1.0, HeadlightMath.overlayLayerGain(HeadlightMath.GAIN), 1.0E-9);
        assertEquals(0.0, HeadlightMath.overlayLayerGain(0.0), 1.0E-9);
        double half = HeadlightMath.overlayLayerGain(HeadlightMath.GAIN * 0.5);
        assertTrue(half > 0.0 && half < 1.0);
    }

    @Test
    void hotspotIsBrightAndWarmInTheCentreOnly() {
        assertEquals(1.0 + HeadlightMath.HOTSPOT_BOOST, HeadlightMath.hotspot(1.0), 1.0E-9);
        assertEquals(1.0, HeadlightMath.hotspot(Math.cos(Math.toRadians(HeadlightMath.CONE_INNER_DEG + 1.0))), 1.0E-9);
        double[] centre = HeadlightMath.warmTint(1.0);
        double[] edge = HeadlightMath.warmTint(Math.cos(Math.toRadians(30.0)));
        assertEquals(1.0, centre[0], 1.0E-9);
        assertTrue(centre[2] < edge[2]);
        assertTrue(centre[1] > centre[2]);
        assertEquals(Math.cos(Math.toRadians(HeadlightMath.HOTSPOT_DEG)), 0.992546, 1.0E-6);
    }
}
