package de.lardtom.propwash.osd;

import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdProjectionTest {
    private static final float EPS = 1.0E-3F;
    private static final float CX = 240.0F;
    private static final float CY = 135.0F;
    private static final float H = 270.0F;

    private static OsdProjection projection(Quaternionf rotation, float fov) {
        return new OsdProjection().setup(rotation, fov, CX, CY, H);
    }

    @Test
    void forwardProjectsToTheCenter() {
        OsdProjection p = projection(new Quaternionf(), 90.0F);
        Vector2f out = new Vector2f();
        assertTrue(p.project(0.0, 0.0, -10.0, out));
        assertEquals(CX, out.x, EPS);
        assertEquals(CY, out.y, EPS);
        assertEquals(H * 0.5F, p.focal(), EPS);
    }

    @Test
    void verticalFovMapsToTheScreenEdges() {
        OsdProjection p = projection(new Quaternionf(), 90.0F);
        Vector2f out = new Vector2f();
        assertTrue(p.project(0.0, 1.0, -1.0, out));
        assertEquals(0.0F, out.y, EPS);
        assertTrue(p.project(1.0, 0.0, -1.0, out));
        assertEquals(CX + H * 0.5F, out.x, EPS);
    }

    @Test
    void pointsBehindTheCameraAreRejected() {
        OsdProjection p = projection(new Quaternionf(), 90.0F);
        Vector2f out = new Vector2f(-1.0F, -1.0F);
        assertFalse(p.project(0.0, 0.0, 5.0, out));
        assertFalse(p.project(1.0, 0.0, 0.0, out));
    }

    @Test
    void minecraftYawMatchesTheCamera() {
        Quaternionf rotation = OsdProjection.cameraRotation(0.0, 0.0, 0.0, new Quaternionf());
        OsdProjection p = projection(rotation, 70.0F);
        Vector2f out = new Vector2f();
        assertTrue(p.project(0.0, 0.0, 10.0, out));
        assertEquals(CX, out.x, EPS);
        assertTrue(p.project(-1.0, 0.0, 10.0, out));
        assertTrue(out.x > CX);
        Quaternionf east = OsdProjection.cameraRotation(-90.0, 0.0, 0.0, new Quaternionf());
        assertTrue(projection(east, 70.0F).project(10.0, 0.0, 0.0, out));
        assertEquals(CX, out.x, EPS);
    }

    @Test
    void levelCameraPutsTheHorizonThroughTheCenter() {
        OsdProjection p = projection(new Quaternionf(), 90.0F);
        OsdProjection.Horizon h = new OsdProjection.Horizon();
        assertTrue(p.horizon(h));
        assertEquals(CX, h.x, EPS);
        assertEquals(CY, h.y, EPS);
        assertEquals(0.0F, h.angle, EPS);
        assertEquals(-1.0F, h.skyY, EPS);
        assertFalse(h.upsideDown);
    }

    @Test
    void uptiltMovesTheHorizonDownByTheProjectedAngle() {
        float tilt = 30.0F;
        Quaternionf rotation = new Quaternionf().rotationX((float) Math.toRadians(tilt));
        OsdProjection p = projection(rotation, 90.0F);
        OsdProjection.Horizon h = new OsdProjection.Horizon();
        assertTrue(p.horizon(h));
        assertEquals(CX, h.x, EPS);
        assertEquals(CY + p.focal() * (float) Math.tan(Math.toRadians(tilt)), h.y, 0.01F);
        assertEquals(0.0F, h.angle, EPS);
    }

    @Test
    void rollRightTiltsTheHorizonCounterClockwise() {
        float roll = 20.0F;
        Quaternionf rotation = OsdProjection.cameraRotation(0.0, 0.0, roll, new Quaternionf());
        OsdProjection p = projection(rotation, 90.0F);
        OsdProjection.Horizon h = new OsdProjection.Horizon();
        assertTrue(p.horizon(h));
        assertEquals(-Math.toRadians(roll), h.angle, 1.0E-4);
        assertEquals(CX, h.x, EPS);
        assertEquals(CY, h.y, EPS);
    }

    @Test
    void horizonLineContainsProjectedHorizonPoints() {
        Quaternionf rotation = OsdProjection.cameraRotation(37.0, -25.0, 18.0, new Quaternionf());
        OsdProjection p = projection(rotation, 100.0F);
        OsdProjection.Horizon h = new OsdProjection.Horizon();
        assertTrue(p.horizon(h));
        Vector2f out = new Vector2f();
        float dirX = (float) Math.cos(h.angle);
        float dirY = (float) Math.sin(h.angle);
        int checked = 0;
        for (int azimuth = 0; azimuth < 360; azimuth += 10) {
            double rad = Math.toRadians(azimuth);
            if (p.project(Math.sin(rad), 0.0, Math.cos(rad), out)) {
                float cross = (out.x - h.x) * dirY - (out.y - h.y) * dirX;
                assertEquals(0.0F, cross, 0.05F, "Azimut " + azimuth);
                checked++;
            }
        }
        assertTrue(checked > 5);
    }

    @Test
    void horizonAzimuthAndLadderAgree() {
        Quaternionf rotation = OsdProjection.cameraRotation(120.0, -20.0, 0.0, new Quaternionf());
        OsdProjection p = projection(rotation, 90.0F);
        OsdProjection.Horizon h = new OsdProjection.Horizon();
        assertTrue(p.horizon(h));
        Vector3f azimuth = new Vector3f();
        assertTrue(p.horizonAzimuth(h, azimuth));
        assertEquals(0.0F, azimuth.y, EPS);
        assertEquals(1.0F, azimuth.length(), EPS);
        Vector2f out = new Vector2f();
        assertTrue(p.projectElevation(azimuth, 0.0, out));
        assertEquals(h.x, out.x, 0.01F);
        assertEquals(h.y, out.y, 0.01F);
        assertTrue(p.projectElevation(azimuth, 10.0, out));
        assertTrue(out.y < h.y);
        assertTrue(p.projectElevation(azimuth, -10.0, out));
        assertTrue(out.y > h.y);
    }

    @Test
    void lookingStraightDownHasNoHorizon() {
        Quaternionf rotation = OsdProjection.cameraRotation(0.0, 90.0, 0.0, new Quaternionf());
        OsdProjection p = projection(rotation, 90.0F);
        assertFalse(p.horizon(new OsdProjection.Horizon()));
    }

    @Test
    void screenAngleGuidesTowardsHiddenTargets() {
        OsdProjection p = projection(new Quaternionf(), 90.0F);
        assertEquals(0.0F, p.screenAngle(1.0, 0.0, 1.0), EPS);
        assertEquals((float) Math.PI, Math.abs(p.screenAngle(-1.0, 0.0, 1.0)), EPS);
        assertEquals((float) (-Math.PI * 0.5), p.screenAngle(0.0, 1.0, 1.0), EPS);
    }

    @Test
    void invalidRotationFallsBackToIdentity() {
        OsdProjection p = new OsdProjection().setup(new Quaternionf(Float.NaN, 0.0F, 0.0F, 1.0F), 90.0F, CX, CY, H);
        assertFalse(p.valid());
        Vector2f out = new Vector2f();
        assertTrue(p.project(0.0, 0.0, -1.0, out));
        assertEquals(CX, out.x, EPS);
    }
}
