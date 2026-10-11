package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraAnglesTest {
    private static Quaterniond vanilla(double yaw, double pitch) {
        return new Quaterniond().rotationYXZ(Math.PI - Math.toRadians(yaw), -Math.toRadians(pitch), 0.0);
    }

    @Test
    void identityLooksNorth() {
        CameraAngles angles = CameraAngles.of(new Quaterniond());
        assertEquals(180.0, Math.abs(angles.yaw()), 1.0E-9);
        assertEquals(0.0, angles.pitch(), 1.0E-9);
        assertEquals(0.0, angles.roll(), 1.0E-9);
    }

    @Test
    void matchesTheVanillaCameraWithoutRoll() {
        for (double yaw = -170.0; yaw <= 180.0; yaw += 37.0) {
            for (double pitch = -85.0; pitch <= 85.0; pitch += 17.0) {
                CameraAngles angles = CameraAngles.of(vanilla(yaw, pitch));
                assertEquals(0.0, CameraAngles.wrap(angles.yaw() - yaw), 1.0E-7, "yaw " + yaw + " pitch " + pitch);
                assertEquals(pitch, angles.pitch(), 1.0E-7);
                assertEquals(0.0, angles.roll(), 1.0E-7);
            }
        }
    }

    @Test
    void rollIsALocalTurnAroundTheViewAxis() {
        Quaterniond rolled = vanilla(30.0, 10.0).rotateZ(Math.toRadians(25.0));
        CameraAngles angles = CameraAngles.of(rolled);
        assertEquals(30.0, angles.yaw(), 1.0E-7);
        assertEquals(10.0, angles.pitch(), 1.0E-7);
        assertEquals(25.0, angles.roll(), 1.0E-7);
    }

    @Test
    void roundTripsRandomOrientations() {
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            Quaterniond q = new Quaterniond(random.nextGaussian(), random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            CameraAngles angles = CameraAngles.of(q);
            assertTrue(angles.pitch() >= -90.0 && angles.pitch() <= 90.0);
            assertEquals(0.0, CameraAngles.degreesBetween(angles.rotation(), q), 1.0E-4);
        }
    }

    @Test
    void straightDownStaysExact() {
        Quaterniond down = vanilla(40.0, 90.0).rotateZ(Math.toRadians(15.0));
        CameraAngles angles = CameraAngles.of(down);
        assertEquals(90.0, angles.pitch(), 1.0E-6);
        assertEquals(0.0, CameraAngles.degreesBetween(angles.rotation(), down), 1.0E-4);
    }

    @Test
    void wrapsLikeMinecraft() {
        assertEquals(-180.0, CameraAngles.wrap(180.0));
        assertEquals(179.0, CameraAngles.wrap(-181.0));
        assertEquals(10.0, CameraAngles.wrap(730.0));
        assertEquals(-10.0, CameraAngles.wrap(-370.0));
    }
}
