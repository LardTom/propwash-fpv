package de.lardtom.propwash.drone;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundSettleTest {
    private static float tiltDeg(Quaternionf q) {
        Vector3f up = q.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        return (float) Math.toDegrees(Math.acos(Math.max(-1.0F, Math.min(1.0F, up.y))));
    }

    private static float headingDeg(Quaternionf q) {
        Vector3f nose = q.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        return (float) Math.toDegrees(Math.atan2(nose.x, nose.z));
    }

    @Test
    void flatDroneIsLeftAlone() {
        Quaternionf out = new Quaternionf();
        for (int yaw = 0; yaw < 360; yaw += 30) {
            Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(yaw));
            assertFalse(GroundSettle.step(q, out));
            assertEquals(q, out);
            Quaternionf nearly = new Quaternionf(q).rotateX((float) Math.toRadians(0.4));
            assertFalse(GroundSettle.step(nearly, out));
            Quaternionf back = new Quaternionf(q).rotateZ((float) Math.PI);
            assertFalse(GroundSettle.step(back, out));
        }
        assertFalse(GroundSettle.step(new Quaternionf(0.0F, 0.0F, 0.0F, 0.0F), out));
        assertFalse(GroundSettle.step(new Quaternionf(Float.NaN, 0.0F, 0.0F, 1.0F), out));
    }

    @Test
    void tiltedDroneTipsOntoItsFeetWithinAFewTicks() {
        for (int yaw = 0; yaw < 360; yaw += 45) {
            for (int pitch = -80; pitch <= 80; pitch += 20) {
                for (int roll = -85; roll <= 85; roll += 17) {
                    Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch))
                            .rotateZ((float) Math.toRadians(roll));
                    float start = tiltDeg(q);
                    if (start < 1.0F || start > 89.0F) {
                        continue;
                    }
                    String at = yaw + "/" + pitch + "/" + roll;
                    Quaternionf out = new Quaternionf();
                    float previous = start;
                    int steps = 0;
                    while (GroundSettle.step(q, out)) {
                        float tilt = tiltDeg(out);
                        assertTrue(tilt < previous + 1.0E-3F, at + ": kippt weiter (" + previous + " → " + tilt + ")");
                        assertTrue(previous - tilt <= 24.01F, at + ": Sprung " + (previous - tilt));
                        assertEquals(1.0F, out.lengthSquared(), 1.0E-4F, at);
                        previous = tilt;
                        q.set(out);
                        steps++;
                        assertTrue(steps <= 5, at + ": zu viele Schritte");
                    }
                    assertTrue(tiltDeg(q) < 0.6F, at + ": liegt mit " + tiltDeg(q) + "°");
                }
            }
        }
    }

    @Test
    void droneOnItsBackSettlesOnItsBack() {
        Quaternionf q = new Quaternionf().rotateY(0.7F).rotateZ((float) Math.toRadians(150.0));
        Quaternionf out = new Quaternionf();
        int steps = 0;
        while (GroundSettle.step(q, out)) {
            q.set(out);
            steps++;
            assertTrue(steps <= 3);
        }
        assertEquals(2, steps);
        assertTrue(tiltDeg(q) > 179.4F, "liegt mit " + tiltDeg(q) + "°");
    }

    @Test
    void headingSurvivesTheTip() {
        for (int yaw = -150; yaw <= 150; yaw += 50) {
            Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(yaw)).rotateZ((float) Math.toRadians(40.0));
            Quaternionf out = new Quaternionf();
            while (GroundSettle.step(q, out)) {
                q.set(out);
            }
            assertEquals(yaw, headingDeg(q), 0.5F, "Kurs nach Rollen bei " + yaw);
            q = new Quaternionf().rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(-35.0));
            while (GroundSettle.step(q, out)) {
                q.set(out);
            }
            assertEquals(yaw, headingDeg(q), 0.5F, "Kurs nach Nicken bei " + yaw);
        }
    }
}
