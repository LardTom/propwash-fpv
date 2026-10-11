package de.lardtom.propwash.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotLookTest {
    private static final double DT = 1.0 / 60.0;
    private static final double RADIUS = 6.0;
    private static final double HEIGHT = 1.5;
    private static final double ANGULAR_SPEED = 1.2;
    private static final float START_YAW = 37.5F;
    private static final float START_PITCH = 12.25F;

    private static double[] circling(int frame) {
        double angle = frame * DT * ANGULAR_SPEED;
        return new double[]{Math.cos(angle) * RADIUS, HEIGHT, Math.sin(angle) * RADIUS};
    }

    private static float yawError(LosAim.Angles angles, double[] d) {
        return Math.abs(LosAim.wrapDegrees(angles.yaw() - LosAim.yawTo(d[0], d[2])));
    }

    private static float pitchError(LosAim.Angles angles, double[] d) {
        return Math.abs(angles.pitch() - LosAim.pitchTo(d[0], d[1], d[2]));
    }

    @Test
    void onlyLineOfSightFollowsTheDrone() {
        assertTrue(PilotLook.viewFollowsDrone(PilotMode.LINE_OF_SIGHT));
        assertFalse(PilotLook.viewFollowsDrone(PilotMode.FPV));
        assertFalse(PilotLook.viewFollowsDrone(PilotMode.NONE));
    }

    @Test
    void fpvKeepsYawAndPitchWhileTheDroneCirclesThePilot() {
        for (PilotMode mode : new PilotMode[]{PilotMode.FPV, PilotMode.NONE}) {
            LosAim.Angles angles = new LosAim.Angles(START_YAW, START_PITCH);
            for (int frame = 0; frame < 600; frame++) {
                double[] d = circling(frame);
                angles = PilotLook.view(mode, angles.yaw(), angles.pitch(), d[0], d[1], d[2], DT);
                assertEquals(START_YAW, angles.yaw(), 0.0F, mode + " dreht den Blick in Frame " + frame);
                assertEquals(START_PITCH, angles.pitch(), 0.0F, mode + " neigt den Blick in Frame " + frame);
            }
        }
    }

    @Test
    void lineOfSightTracksTheCirclingDrone() {
        LosAim.Angles angles = new LosAim.Angles(START_YAW, START_PITCH);
        float swept = 0.0F;
        for (int frame = 0; frame < 600; frame++) {
            double[] d = circling(frame);
            float previous = angles.yaw();
            angles = PilotLook.view(PilotMode.LINE_OF_SIGHT, angles.yaw(), angles.pitch(), d[0], d[1], d[2], DT);
            swept += Math.abs(LosAim.wrapDegrees(angles.yaw() - previous));
            if (frame >= 30) {
                assertTrue(yawError(angles, d) < 3.0F, "Gieren hängt nach: " + yawError(angles, d) + "° in Frame " + frame);
                assertTrue(pitchError(angles, d) < 3.0F, "Nicken hängt nach: " + pitchError(angles, d) + "° in Frame " + frame);
            }
        }
        assertTrue(swept > 600.0F, "Blick dreht sich nur um " + swept + "°");
    }

    @Test
    void switchingModesTakesEffectOnTheNextFrame() {
        LosAim.Angles angles = new LosAim.Angles(START_YAW, START_PITCH);
        LosAim.Angles frozen = null;
        for (int frame = 0; frame < 540; frame++) {
            PilotMode mode = frame < 180 || frame >= 360 ? PilotMode.LINE_OF_SIGHT : PilotMode.FPV;
            double[] d = circling(frame);
            LosAim.Angles before = angles;
            angles = PilotLook.view(mode, angles.yaw(), angles.pitch(), d[0], d[1], d[2], DT);
            if (frame == 179) {
                frozen = angles;
            }
            if (mode == PilotMode.FPV) {
                assertEquals(frozen.yaw(), angles.yaw(), 0.0F, "FPV dreht den Blick in Frame " + frame);
                assertEquals(frozen.pitch(), angles.pitch(), 0.0F, "FPV neigt den Blick in Frame " + frame);
            }
            if (frame == 360) {
                float error = yawError(before, d);
                float step = Math.abs(LosAim.wrapDegrees(angles.yaw() - before.yaw()));
                assertTrue(error > 30.0F, "Drohne ist während FPV nicht weitergeflogen");
                assertTrue(step > 0.0F && step < error, "Rückkehr in den Sichtflug springt statt weich zu drehen");
            }
            if (frame >= 370) {
                assertTrue(yawError(angles, d) < 3.0F, "Sichtflug fängt die Drohne nicht wieder ein: " + yawError(angles, d) + "°");
            }
        }
    }

    @Test
    void fpvHeadSettlesSlightlyDownAndSmoothly() {
        float lookingUpAtDrone = -40.0F;
        float weight = 0.0F;
        float pitch = PilotLook.headPitch(lookingUpAtDrone, weight, false);
        assertEquals(lookingUpAtDrone, pitch, 0.0F);
        float maxStep = (float) (1.0 - Math.exp(-DT / PilotLook.HEAD_SETTLE_SECONDS));
        for (int frame = 0; frame < 120; frame++) {
            float next = PilotLook.headWeight(weight, true, DT);
            assertTrue(next >= weight, "Kopf-Übergang läuft rückwärts");
            assertTrue(next - weight <= maxStep + 1.0E-6F, "Kopf springt in Frame " + frame);
            float nextPitch = PilotLook.headPitch(lookingUpAtDrone, next, false);
            assertTrue(nextPitch >= pitch, "Kopf nickt nicht gleichmäßig nach unten");
            weight = next;
            pitch = nextPitch;
        }
        assertEquals(1.0F, weight, 0.0F);
        assertEquals(PilotLook.FPV_HEAD_PITCH, PilotLook.headPitch(lookingUpAtDrone, weight, false), 1.0E-4F);
        assertTrue(PilotLook.FPV_HEAD_PITCH > 0.0F && PilotLook.FPV_HEAD_PITCH <= 30.0F, "Kopf nicht leicht nach unten");
        assertEquals(-PilotLook.FPV_HEAD_PITCH, PilotLook.headPitch(40.0F, 1.0F, true), 1.0E-4F);
    }

    @Test
    void headReturnsSmoothlyWhenLeavingFpv() {
        float weight = 1.0F;
        float maxStep = (float) (1.0 - Math.exp(-DT / PilotLook.HEAD_SETTLE_SECONDS));
        for (int frame = 0; frame < 120; frame++) {
            float next = PilotLook.headWeight(weight, false, DT);
            assertTrue(next <= weight && weight - next <= maxStep + 1.0E-6F, "Kopf springt beim Verlassen des FPV");
            weight = next;
        }
        assertEquals(0.0F, weight, 0.0F);
    }

    @Test
    void headIgnoresPausesAndBrokenFrameTimes() {
        assertEquals(0.3F, PilotLook.headWeight(0.3F, true, 0.0), 0.0F);
        assertEquals(0.3F, PilotLook.headWeight(0.3F, true, Double.NaN), 0.0F);
        float afterPause = PilotLook.headWeight(0.0F, true, 30.0);
        assertEquals(1.0 - Math.exp(-PilotLook.MAX_HEAD_STEP_SECONDS / PilotLook.HEAD_SETTLE_SECONDS), afterPause, 1.0E-5);
        assertEquals(1.0F, PilotLook.headWeight(5.0F, true, 0.0), 0.0F);
        assertEquals(0.0F, PilotLook.headWeight(Float.NaN, false, 0.0), 0.0F);
    }
}
