package de.lardtom.propwash.api.flight;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalModelTest {
    private static Quaternionf frame(double headingDeg, double pitchDeg, double rollDeg) {
        return new Quaternionf().rotateY((float) Math.toRadians(-headingDeg)).rotateX((float) Math.toRadians(pitchDeg))
                .rotateZ((float) Math.toRadians(-rollDeg));
    }

    private static void step(GimbalModel g, Quaternionf q, double seconds) {
        for (int i = 0; i < Math.round(seconds * 100.0); i++) {
            g.update(0.01, q.x, q.y, q.z, q.w);
        }
    }

    private static Vector3f forward(GimbalModel g) {
        return new Quaternionf((float) g.qx(), (float) g.qy(), (float) g.qz(), (float) g.qw()).transform(new Vector3f(0.0F, 0.0F, -1.0F));
    }

    @Test
    void cameraStaysLevelWhileTheFrameRolls() {
        GimbalModel g = new GimbalModel();
        step(g, frame(90.0, -10.0, 30.0), 1.0);
        assertEquals(0.0, g.rollDeg(), 1.0E-9);
        Vector3f up = new Quaternionf((float) g.qx(), (float) g.qy(), (float) g.qz(), (float) g.qw()).transform(new Vector3f(1.0F, 0.0F, 0.0F));
        assertEquals(0.0, up.y, 1.0E-4, "Horizont schief");
        assertEquals(90.0, g.headingDeg(), 0.5);
    }

    @Test
    void pitchCommandIsSlewLimitedAndClamped() {
        GimbalModel g = new GimbalModel().smoothing(0.6, 120.0, 90.0);
        Quaternionf level = frame(0.0, 0.0, 0.0);
        step(g, level, 0.1);
        g.pitch(-60.0);
        step(g, level, 0.3);
        assertEquals(-27.0, g.pitchDeg(), 1.5, "Nickrate nicht begrenzt");
        step(g, level, 1.0);
        assertEquals(-60.0, g.pitchDeg(), 1.0E-6);
        Vector3f f = forward(g);
        assertEquals(Math.sin(Math.toRadians(-60.0)), f.y, 1.0E-4);
        g.pitch(80.0);
        assertEquals(30.0, g.commandedPitch(), 1.0E-9);
    }

    @Test
    void followModeLagsAndLockModeHoldsHeading() {
        GimbalModel follow = new GimbalModel();
        step(follow, frame(0.0, 0.0, 0.0), 0.5);
        Quaternionf turned = frame(60.0, 0.0, 0.0);
        step(follow, turned, 0.2);
        assertTrue(follow.headingDeg() > 5.0 && follow.headingDeg() < 50.0, "kein weiches Nachführen: " + follow.headingDeg());
        step(follow, turned, 4.0);
        assertEquals(60.0, follow.headingDeg(), 0.5);

        GimbalModel lock = new GimbalModel();
        step(lock, frame(0.0, 0.0, 0.0), 0.5);
        lock.mode(GimbalModel.Mode.LOCK);
        step(lock, frame(40.0, 0.0, 0.0), 2.0);
        assertEquals(0.0, lock.headingDeg(), 1.0E-6);
        step(lock, frame(160.0, 0.0, 0.0), 2.0);
        assertTrue(lock.atLimit(), "mechanische Gier-Grenze greift nicht");
        assertEquals(60.0, lock.headingDeg(), 0.5);
    }

    @Test
    void fpvModeFollowsTheFrameRollAndInvalidSettingsThrow() {
        GimbalModel g = new GimbalModel().mode(GimbalModel.Mode.FPV);
        step(g, frame(10.0, 0.0, 25.0), 0.5);
        assertEquals(25.0, g.rollDeg(), 0.5);
        assertEquals(10.0, g.headingDeg(), 0.5);
        assertFalse(Double.isNaN(g.qw()));
        assertThrows(IllegalArgumentException.class, () -> new GimbalModel().pitchRange(10.0, -10.0));
        assertThrows(IllegalArgumentException.class, () -> new GimbalModel().smoothing(0.5, 0.0, 10.0));
    }
}
