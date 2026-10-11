package de.lardtom.propwash.video.record;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StabilizerTest {
    private static final float TAN_X = (float) Math.tan(Math.toRadians(60.0));
    private static final float TAN_Y = TAN_X * 9.0F / 16.0F;

    private static Quaternionf camera(float yawDegrees, float pitchDegrees, float rollDegrees) {
        return new Quaternionf().rotationYXZ((float) Math.toRadians(yawDegrees), (float) Math.toRadians(pitchDegrees),
                (float) Math.toRadians(-rollDegrees));
    }

    private static Quaternionf output(Quaternionf actual, Stabilizer stabilizer) {
        Quaternionf relative = new Quaternionf().setFromNormalized(stabilizer.warp());
        return new Quaternionf(actual).mul(relative);
    }

    private static void update(Stabilizer stabilizer, Stabilizer.Mode mode, Quaternionf actual, double seconds) {
        float zoom = mode == Stabilizer.Mode.HORIZON ? Stabilizer.HORIZON_ZOOM : mode == Stabilizer.Mode.SMOOTH ? Stabilizer.SMOOTH_ZOOM : 1.0F;
        stabilizer.update(mode, actual, seconds, TAN_X, TAN_Y, TAN_X / zoom, TAN_Y / zoom);
    }

    private static boolean cornersInside(Matrix3f warp, float zoom) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                Vector3f ray = new Vector3f(sx * TAN_X / zoom, sy * TAN_Y / zoom, -1.0F);
                warp.transform(ray);
                if (ray.z >= 0.0F) {
                    return false;
                }
                if (Math.abs(ray.x / -ray.z) > TAN_X * 1.001F || Math.abs(ray.y / -ray.z) > TAN_Y * 1.001F) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void offLeavesTheImageUntouched() {
        Stabilizer stabilizer = new Stabilizer();
        update(stabilizer, Stabilizer.Mode.OFF, camera(30, 10, 20), 0.016);
        assertEquals(1.0F, stabilizer.zoom());
        assertTrue(stabilizer.warp().equals(new Matrix3f(), 1.0E-6F));
    }

    @Test
    void steadyCameraNeedsNoCorrection() {
        Stabilizer stabilizer = new Stabilizer();
        Quaternionf actual = camera(45, -5, 0);
        for (int i = 0; i < 60; i++) {
            update(stabilizer, Stabilizer.Mode.SMOOTH, actual, 1.0 / 60.0);
        }
        assertTrue(stabilizer.warp().equals(new Matrix3f(), 1.0E-4F));
        assertEquals(Stabilizer.SMOOTH_ZOOM, stabilizer.zoom());
    }

    @Test
    void highFrequencyShakeIsSmoothedOut() {
        Stabilizer stabilizer = new Stabilizer();
        float rawSpan = 0.0F;
        float stableSpan = 0.0F;
        float minRaw = Float.MAX_VALUE;
        float maxRaw = -Float.MAX_VALUE;
        float minOut = Float.MAX_VALUE;
        float maxOut = -Float.MAX_VALUE;
        for (int i = 0; i < 240; i++) {
            double t = i / 60.0;
            float shake = (float) (1.5 * Math.sin(2.0 * Math.PI * 18.0 * t));
            Quaternionf actual = camera(10, shake, shake * 0.7F);
            update(stabilizer, Stabilizer.Mode.SMOOTH, actual, 1.0 / 60.0);
            if (i < 60) {
                continue;
            }
            Vector3f forward = output(actual, stabilizer).transform(new Vector3f(0, 0, -1));
            Vector3f rawForward = actual.transform(new Vector3f(0, 0, -1));
            float pitchOut = (float) Math.toDegrees(Math.asin(forward.y));
            float pitchRaw = (float) Math.toDegrees(Math.asin(rawForward.y));
            minRaw = Math.min(minRaw, pitchRaw);
            maxRaw = Math.max(maxRaw, pitchRaw);
            minOut = Math.min(minOut, pitchOut);
            maxOut = Math.max(maxOut, pitchOut);
            assertTrue(cornersInside(stabilizer.warp(), stabilizer.zoom()));
        }
        rawSpan = maxRaw - minRaw;
        stableSpan = maxOut - minOut;
        assertTrue(stableSpan < rawSpan * 0.35F, "shake " + rawSpan + " -> " + stableSpan);
    }

    @Test
    void horizonIsLevelledWithinTheCrop() {
        Stabilizer stabilizer = new Stabilizer();
        Quaternionf actual = camera(80, -10, 12);
        for (int i = 0; i < 120; i++) {
            update(stabilizer, Stabilizer.Mode.HORIZON, actual, 1.0 / 60.0);
        }
        assertEquals(12.0F, Stabilizer.rollDegrees(actual), 0.5F);
        assertEquals(0.0F, Stabilizer.rollDegrees(output(actual, stabilizer)), 0.5F);
        assertTrue(cornersInside(stabilizer.warp(), stabilizer.zoom()));
    }

    @Test
    void strongRollIsReducedButNeverShowsBorders() {
        Stabilizer stabilizer = new Stabilizer();
        Quaternionf actual = camera(0, 0, 55);
        for (int i = 0; i < 120; i++) {
            update(stabilizer, Stabilizer.Mode.HORIZON, actual, 1.0 / 60.0);
        }
        float roll = Math.abs(Stabilizer.rollDegrees(output(actual, stabilizer)));
        assertTrue(roll < 40.0F && roll > 10.0F, "roll " + roll);
        assertTrue(cornersInside(stabilizer.warp(), stabilizer.zoom()));
    }

    @Test
    void randomFlightNeverShowsBorders() {
        Stabilizer stabilizer = new Stabilizer();
        Random random = new Random(7L);
        float yaw = 0.0F;
        float pitch = 0.0F;
        float roll = 0.0F;
        for (int i = 0; i < 600; i++) {
            yaw += (random.nextFloat() - 0.5F) * 20.0F;
            pitch = Math.max(-80.0F, Math.min(80.0F, pitch + (random.nextFloat() - 0.5F) * 16.0F));
            roll = Math.max(-170.0F, Math.min(170.0F, roll + (random.nextFloat() - 0.5F) * 30.0F));
            Stabilizer.Mode mode = i < 300 ? Stabilizer.Mode.SMOOTH : Stabilizer.Mode.HORIZON;
            update(stabilizer, mode, camera(yaw, pitch, roll), 1.0 / 60.0);
            assertTrue(cornersInside(stabilizer.warp(), stabilizer.zoom()), "frame " + i);
        }
    }

    @Test
    void strengthFiftyKeepsTheEarlierTuning() {
        assertEquals(1.15F, Stabilizer.zoom(Stabilizer.Mode.SMOOTH, 0.5F), 1.0E-6F);
        assertEquals(1.6F, Stabilizer.zoom(Stabilizer.Mode.HORIZON, 0.5F), 1.0E-6F);
        assertEquals(0.14, Stabilizer.smoothSeconds(0.5F), 1.0E-9);
        assertEquals(1.0F, Stabilizer.zoom(Stabilizer.Mode.OFF, 1.0F));
    }

    @Test
    void strengthTradesCropForSteadiness() {
        float previousSmooth = 0.0F;
        float previousHorizon = 0.0F;
        double previousSeconds = 0.0;
        for (int step = 0; step <= 10; step++) {
            float strength = step / 10.0F;
            float smooth = Stabilizer.zoom(Stabilizer.Mode.SMOOTH, strength);
            float horizon = Stabilizer.zoom(Stabilizer.Mode.HORIZON, strength);
            double seconds = Stabilizer.smoothSeconds(strength);
            assertTrue(smooth > previousSmooth && horizon > previousHorizon && seconds > previousSeconds, "strength " + strength);
            previousSmooth = smooth;
            previousHorizon = horizon;
            previousSeconds = seconds;
        }
        assertEquals(1.0F, Stabilizer.zoom(Stabilizer.Mode.SMOOTH, 0.0F));
        assertEquals(1.3F, Stabilizer.zoom(Stabilizer.Mode.SMOOTH, 1.0F), 1.0E-6F);
        assertEquals(Stabilizer.zoom(Stabilizer.Mode.SMOOTH, 0.5F), Stabilizer.zoom(Stabilizer.Mode.SMOOTH, Float.NaN));
        assertEquals(1.3F, Stabilizer.zoom(Stabilizer.Mode.SMOOTH, 7.0F), 1.0E-6F);
    }

    @Test
    void horizonHoldFollowsTheCrop() {
        float aspect = 16.0F / 9.0F;
        assertEquals(22.3F, Stabilizer.horizonHoldDegrees(Stabilizer.zoom(Stabilizer.Mode.HORIZON, 0.5F), aspect), 0.2F);
        assertEquals(8.4F, Stabilizer.horizonHoldDegrees(Stabilizer.zoom(Stabilizer.Mode.HORIZON, 0.0F), aspect), 0.2F);
        assertEquals(0.0F, Stabilizer.horizonHoldDegrees(1.0F, aspect), 0.01F);
        for (float roll : new float[]{5.0F, 8.0F}) {
            Stabilizer stabilizer = new Stabilizer();
            float zoom = Stabilizer.zoom(Stabilizer.Mode.HORIZON, 0.0F);
            for (int i = 0; i < 240; i++) {
                stabilizer.update(Stabilizer.Mode.HORIZON, 0.0F, camera(0.0F, 0.0F, roll), 1.0 / 60.0, TAN_X, TAN_Y, TAN_X / zoom, TAN_Y / zoom);
            }
            assertEquals(0.0F, Stabilizer.rollDegrees(output(camera(0.0F, 0.0F, roll), stabilizer)), 0.5F, "roll " + roll);
            assertTrue(cornersInside(stabilizer.warp(), stabilizer.zoom()));
        }
    }

    @Test
    void everyStrengthKeepsTheBordersHidden() {
        for (int step = 0; step <= 4; step++) {
            float strength = step / 4.0F;
            for (Stabilizer.Mode mode : Stabilizer.Mode.values()) {
                Stabilizer stabilizer = new Stabilizer();
                Random random = new Random(11L + step);
                float yaw = 0.0F;
                float pitch = 0.0F;
                float roll = 0.0F;
                float zoom = Stabilizer.zoom(mode, strength);
                for (int i = 0; i < 300; i++) {
                    yaw += (random.nextFloat() - 0.5F) * 20.0F;
                    pitch = Math.max(-80.0F, Math.min(80.0F, pitch + (random.nextFloat() - 0.5F) * 16.0F));
                    roll = Math.max(-170.0F, Math.min(170.0F, roll + (random.nextFloat() - 0.5F) * 30.0F));
                    stabilizer.update(mode, strength, camera(yaw, pitch, roll), 1.0 / 60.0, TAN_X, TAN_Y, TAN_X / zoom, TAN_Y / zoom);
                    assertTrue(cornersInside(stabilizer.warp(), stabilizer.zoom()), mode + " " + strength + " frame " + i);
                }
                assertEquals(zoom, mode == Stabilizer.Mode.OFF ? 1.0F : stabilizer.zoom(), 1.0E-6F);
            }
        }
    }
}
