package de.lardtom.propwash.osd;

import de.lardtom.propwash.video.FisheyeLens;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdProjectionFisheyeTest {
    private static final float CX = 240.0F;
    private static final float CY = 135.0F;
    private static final float W = 480.0F;
    private static final float H_DEG = 130.0F;

    private static OsdProjection fisheye(Quaternionf rotation) {
        return new OsdProjection().setupFisheye(rotation, H_DEG, CX, CY, W);
    }

    private static float focal() {
        return (float) (W * 0.5 / Math.toRadians(H_DEG * 0.5));
    }

    @Test
    void angleFromTheAxisGrowsLinearlyAcrossTheImage() {
        OsdProjection p = fisheye(new Quaternionf());
        assertTrue(p.fisheye());
        Vector2f out = new Vector2f();
        assertTrue(p.project(0.0, 0.0, -10.0, out));
        assertEquals(CX, out.x, 1.0E-3F);
        assertEquals(CY, out.y, 1.0E-3F);
        double half = Math.toRadians(H_DEG * 0.5);
        assertTrue(p.project(Math.sin(half), 0.0, -Math.cos(half), out));
        assertEquals(CX + W * 0.5F, out.x, 0.01F);
        assertTrue(p.project(0.0, Math.sin(0.4), -Math.cos(0.4), out));
        assertEquals(CY - focal() * 0.4F, out.y, 0.01F);
    }

    @Test
    void uptiltMovesTheHorizonByTheAngleNotTheTangent() {
        float tilt = 30.0F;
        OsdProjection p = fisheye(new Quaternionf().rotationX((float) Math.toRadians(tilt)));
        OsdProjection.Horizon h = new OsdProjection.Horizon();
        assertTrue(p.horizon(h));
        assertEquals(CX, h.x, 1.0E-3F);
        assertEquals(CY + focal() * (float) Math.toRadians(tilt), h.y, 0.01F);
        Vector2f out = new Vector2f();
        assertTrue(p.project(0.0, 0.0, -1.0, out));
    }

    @Test
    void horizonAzimuthAndLadderAgree() {
        Quaternionf rotation = OsdProjection.cameraRotation(120.0, -20.0, 15.0, new Quaternionf());
        OsdProjection p = fisheye(rotation);
        OsdProjection.Horizon h = new OsdProjection.Horizon();
        assertTrue(p.horizon(h));
        Vector3f azimuth = new Vector3f();
        assertTrue(p.horizonAzimuth(h, azimuth));
        assertEquals(0.0F, azimuth.y, 1.0E-4F);
        Vector2f out = new Vector2f();
        assertTrue(p.projectElevation(azimuth, 0.0, out));
        assertEquals(h.x, out.x, 0.01F);
        assertEquals(h.y, out.y, 0.01F);
    }

    @Test
    void theHorizonLineTouchesTheCurvedHorizonAtItsNearestPoint() {
        Quaternionf rotation = OsdProjection.cameraRotation(37.0, -25.0, 18.0, new Quaternionf());
        OsdProjection p = fisheye(rotation);
        OsdProjection.Horizon h = new OsdProjection.Horizon();
        assertTrue(p.horizon(h));
        float dirX = (float) Math.cos(h.angle);
        float dirY = (float) Math.sin(h.angle);
        Vector2f out = new Vector2f();
        float nearest = Float.MAX_VALUE;
        for (int azimuth = 0; azimuth < 3600; azimuth++) {
            double rad = Math.toRadians(azimuth * 0.1);
            if (p.project(Math.sin(rad), 0.0, Math.cos(rad), out)) {
                float cross = Math.abs((out.x - h.x) * dirY - (out.y - h.y) * dirX);
                float along = Math.abs((out.x - h.x) * dirX + (out.y - h.y) * dirY);
                if (along < 5.0F) {
                    assertEquals(0.0F, cross, 0.1F, "Azimut " + azimuth * 0.1);
                }
                nearest = Math.min(nearest, (float) Math.hypot(out.x - CX, out.y - CY));
            }
        }
        assertEquals(Math.hypot(h.x - CX, h.y - CY), nearest, 0.2);
    }

    @Test
    void lensStrengthBendsTheProjectionLikeTheVideoLens() {
        for (double strength : new double[]{0.2, 0.8, 1.0}) {
            FisheyeLens lens = FisheyeLens.of(H_DEG, strength, W, 270.0, W, 270.0);
            OsdProjection p = new OsdProjection().setupFisheye(new Quaternionf(), H_DEG, (float) lens.bend(), CX, CY, W);
            assertEquals(lens.focal(), p.focal(), 1.0E-3);
            Vector2f out = new Vector2f();
            double[] expected = new double[2];
            for (double angle = 0.1; angle < 1.1; angle += 0.2) {
                assertTrue(p.project(Math.sin(angle), 0.0, -Math.cos(angle), out));
                assertTrue(lens.project(Math.tan(angle), 0.0, 1.0, expected));
                assertEquals(CX + expected[0], out.x, 0.01F, "Stärke " + strength + " Winkel " + angle);
            }
            double half = Math.toRadians(H_DEG * 0.5);
            assertTrue(p.project(Math.sin(half), 0.0, -Math.cos(half), out));
            assertEquals(CX + W * 0.5F, out.x, 0.02F);
            Quaternionf rotation = OsdProjection.cameraRotation(120.0, -20.0, 15.0, new Quaternionf());
            OsdProjection tilted = new OsdProjection().setupFisheye(rotation, H_DEG, (float) lens.bend(), CX, CY, W);
            OsdProjection.Horizon h = new OsdProjection.Horizon();
            assertTrue(tilted.horizon(h));
            Vector3f azimuth = new Vector3f();
            assertTrue(tilted.horizonAzimuth(h, azimuth));
            assertTrue(tilted.projectElevation(azimuth, 0.0, out));
            assertEquals(h.x, out.x, 0.02F, "Stärke " + strength);
            assertEquals(h.y, out.y, 0.02F, "Stärke " + strength);
        }
    }

    @Test
    void flatSetupAfterFisheyeIsFlatAgain() {
        OsdProjection p = fisheye(new Quaternionf());
        p.setup(new Quaternionf(), 90.0F, CX, CY, 270.0F);
        assertFalse(p.fisheye());
        assertEquals(135.0F, p.focal(), 1.0E-3F);
    }
}
