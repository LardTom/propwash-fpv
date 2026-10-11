package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrottleDetectionTest {
    private static final long MS = 1_000_000L;
    private static final long FRAME_MS = 16L;
    private static final long HOLD_MS = ThrottleDetector.HOLD_NANOS / MS;
    private static final long SETTLE_MS = HOLD_MS + 500L;
    private static final int THROTTLE = 2;
    private static final int AUX = 4;
    private static final boolean[] CLAIMED = {true, true, false, true, false, false};

    private static final class Radio {
        private final float[] axes;
        private final ThrottleDetector detector;
        private final Random random = new Random(42L);
        private float noise;
        private long t;
        private StickDetector.Detection result;
        private long detectedAt = -1L;

        private Radio(float throttleStart) {
            this.axes = new float[]{0.0F, 0.0F, throttleStart, 0.0F, -1.0F, -1.0F};
            this.detector = new ThrottleDetector(this.axes, CLAIMED);
        }

        private Radio(float throttleStart, int expectedAxis, boolean expectedInvert) {
            this.axes = new float[]{0.0F, 0.0F, throttleStart, 0.0F, -1.0F, -1.0F};
            this.detector = new ThrottleDetector(this.axes, CLAIMED, expectedAxis, expectedInvert);
        }

        private Radio noisy(float amplitude) {
            this.noise = amplitude;
            return this;
        }

        private Radio hold(float value, long ms) {
            for (long e = 0; e < ms; e += FRAME_MS) {
                this.frame(THROTTLE, value);
            }
            return this;
        }

        private Radio move(float to, long ms) {
            return this.move(THROTTLE, to, ms);
        }

        private Radio move(int axis, float to, long ms) {
            float from = this.axes[axis];
            for (long e = FRAME_MS; e <= ms; e += FRAME_MS) {
                this.frame(axis, from + (to - from) * (e / (float) ms));
            }
            this.frame(axis, to);
            return this;
        }

        private Radio stroke(float to) {
            return this.move(to, 150L);
        }

        private void frame(int axis, float value) {
            float jitter = this.noise == 0.0F ? 0.0F : (this.random.nextFloat() * 2.0F - 1.0F) * this.noise;
            this.axes[axis] = value;
            float[] sample = this.axes.clone();
            sample[axis] = Math.max(-1.0F, Math.min(1.0F, value + jitter));
            StickDetector.Detection d = this.detector.update(sample, this.t * MS);
            if (d != null && this.result == null) {
                this.result = d;
                this.detectedAt = this.t;
            }
            this.t += FRAME_MS;
        }

        private StickDetector.Detection detected() {
            assertNotNull(this.result, "Gas nicht erkannt, Stufe " + this.detector.stage());
            assertEquals(THROTTLE, this.result.axis());
            return this.result;
        }

        private void nothing() {
            assertNull(this.result, "Gas ohne vollständige Bewegung erkannt");
        }

        private void asks() {
            assertNull(this.result, "Gas trotz Widerspruch zur Startlage gespeichert");
            assertEquals(DetectionStage.CONFIRM, this.detector.stage());
            assertEquals(1.0F, this.detector.progress(this.t * MS), 1.0E-6F);
        }
    }

    @Test
    void stickRestingDownAtStartIsNeverDetected() {
        Radio radio = new Radio(-1.0F).hold(-1.0F, 5000L);
        radio.nothing();
        assertEquals(DetectionStage.WAITING, radio.detector.stage());
        assertEquals(-1, radio.detector.leadingAxis());
    }

    @Test
    void stickDownBriefTopThenDownGivesNormalDirection() {
        Radio radio = new Radio(-1.0F).stroke(1.0F).hold(1.0F, 200L);
        long downStroke = radio.t;
        radio.stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(radio.detected().invert());
        assertTrue(radio.detectedAt >= downStroke + HOLD_MS, "Gas vor Ablauf der Haltezeit erkannt");
        assertTrue(radio.detectedAt <= downStroke + 150L + HOLD_MS + 2 * FRAME_MS, "Haltezeit zu lang");
    }

    @Test
    void pauseAtTheTopIsNeverTakenAsDown() {
        Radio radio = new Radio(-1.0F).stroke(1.0F).hold(1.0F, 3000L);
        radio.nothing();
        assertEquals(DetectionStage.ONE_END, radio.detector.stage());
        assertTrue(radio.detector.stuck());
        radio.stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(radio.detected().invert());
    }

    @Test
    void stickAlreadyUpNeedsOneMoreStrokeAndAQuestion() {
        Radio radio = new Radio(1.0F).stroke(-1.0F).hold(-1.0F, 2000L);
        radio.nothing();
        assertTrue(radio.detector.stuck());
        radio.stroke(1.0F).stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        radio.asks();
        assertTrue(radio.detector.startedAtEnd(THROTTLE));
        StickDetector.Detection answer = radio.detector.confirm(true);
        assertNotNull(answer);
        assertEquals(THROTTLE, answer.axis());
        assertFalse(answer.invert());
    }

    @ParameterizedTest
    @ValueSource(floats = {-0.4F, -0.2F, 0.0F})
    void nudgeBeforeTheUpStrokeIsNeverTakenAsDown(float nudge) {
        Radio radio = new Radio(-1.0F).move(nudge, 300L).move(-1.0F, 300L).stroke(1.0F).hold(1.0F, HOLD_MS + 1500L);
        radio.nothing();
        assertEquals(DetectionStage.ONE_END, radio.detector.stage());
        radio.stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(radio.detected().invert());
        Radio inverted = new Radio(1.0F).move(-nudge, 300L).move(1.0F, 300L).stroke(-1.0F).hold(-1.0F, HOLD_MS + 1500L);
        inverted.nothing();
        inverted.stroke(1.0F).hold(1.0F, SETTLE_MS);
        assertTrue(inverted.detected().invert());
    }

    @Test
    void laterLeadInMustBeAFullStroke() {
        Radio radio = new Radio(-1.0F).stroke(1.0F).stroke(0.0F).stroke(1.0F).stroke(-1.0F).hold(-1.0F, HOLD_MS + 1000L);
        radio.nothing();
        assertEquals(DetectionStage.ONE_END, radio.detector.stage());
        radio.stroke(1.0F).stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(radio.detected().invert());
    }

    @ParameterizedTest
    @ValueSource(longs = {550L, 600L, 1000L, 1200L})
    void pauseAtTheTopAfterAnEarlierDownStrokeIsNotEnough(long pause) {
        Radio middle = new Radio(0.0F).move(-1.0F, 300L).stroke(1.0F).hold(1.0F, pause);
        middle.nothing();
        assertEquals(DetectionStage.HOLDING, middle.detector.stage());
        middle.stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(middle.detected().invert());
        Radio fromTop = new Radio(1.0F).stroke(-1.0F).hold(-1.0F, 2000L);
        assertTrue(fromTop.detector.stuck());
        fromTop.stroke(1.0F).hold(1.0F, pause);
        fromTop.nothing();
        fromTop.stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        fromTop.asks();
        assertFalse(fromTop.detector.confirm(true).invert());
    }

    @ParameterizedTest
    @ValueSource(floats = {-1.0F, -0.5F, 0.0F, 0.1F})
    void directionComesFromTheMovementWhereverTheStickStarts(float start) {
        Radio normal = new Radio(start).stroke(1.0F).stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(normal.detected().invert());
        Radio inverted = new Radio(-start).stroke(-1.0F).stroke(1.0F).hold(1.0F, SETTLE_MS);
        assertTrue(inverted.detected().invert());
    }

    @Test
    void shortLeadInWaitsForAFullStroke() {
        Radio radio = new Radio(0.6F).stroke(1.0F).stroke(-1.0F).hold(-1.0F, HOLD_MS + 1000L);
        radio.nothing();
        radio.stroke(1.0F).stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(radio.detected().invert());
    }

    @Test
    void invertedRadioWithStickDown() {
        Radio radio = new Radio(1.0F).stroke(-1.0F).hold(-1.0F, HOLD_MS + 1000L);
        radio.nothing();
        radio.stroke(1.0F).hold(1.0F, SETTLE_MS);
        assertTrue(radio.detected().invert());
    }

    @Test
    void gamepadThrottleRestingMidStick() {
        Radio pad = new Radio(0.0F).stroke(-1.0F).stroke(1.0F).hold(1.0F, SETTLE_MS);
        assertTrue(pad.detected().invert());
    }

    @Test
    void pushUpAndReleaseIsNotEnoughTravel() {
        Radio pad = new Radio(0.0F).stroke(-1.0F).stroke(0.0F).hold(0.0F, 2000L);
        pad.nothing();
        assertEquals(DetectionStage.TOO_SMALL, pad.detector.stage());
        assertEquals(0.5F, pad.detector.travel(THROTTLE), 1.0E-4F);
        pad.stroke(1.0F).hold(1.0F, SETTLE_MS);
        assertTrue(pad.detected().invert());
    }

    @Test
    void releasingBeforeTheHoldEndsDoesNotDetect() {
        Radio pad = new Radio(0.0F).stroke(-1.0F).stroke(1.0F).hold(1.0F, HOLD_MS - 200L).stroke(0.0F).hold(0.0F, HOLD_MS + 1000L);
        pad.nothing();
        assertEquals(DetectionStage.ONE_END, pad.detector.stage());
    }

    @Test
    void noisyIdleAndNoisyMovement() {
        Radio idle = new Radio(-1.0F).noisy(0.04F).hold(-1.0F, 5000L);
        idle.nothing();
        assertEquals(DetectionStage.WAITING, idle.detector.stage());
        Radio moving = new Radio(-1.0F).noisy(0.06F).hold(-1.0F, 500L).stroke(1.0F).hold(1.0F, 400L).stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(moving.detected().invert());
    }

    @Test
    void tooLittleTravelWarnsAndNeverDetects() {
        Radio radio = new Radio(-1.0F);
        for (int i = 0; i < 4; i++) {
            radio.stroke(-0.3F).stroke(-1.0F);
        }
        radio.hold(-1.0F, 1000L);
        radio.nothing();
        assertEquals(DetectionStage.TOO_SMALL, radio.detector.stage());
        assertEquals(THROTTLE, radio.detector.leadingAxis());
        assertEquals(0.35F, radio.detector.travel(THROTTLE), 1.0E-4F);
    }

    @Test
    void twoAxesMovingTogetherAreAmbiguous() {
        Radio radio = new Radio(-1.0F).stroke(1.0F).move(AUX, 1.0F, 150L).stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        radio.nothing();
        assertEquals(DetectionStage.AMBIGUOUS, radio.detector.stage());
    }

    @Test
    void claimedAxesAreIgnored() {
        Radio radio = new Radio(-1.0F).move(3, 1.0F, 150L).move(3, -1.0F, 150L).move(0, 1.0F, 150L).hold(-1.0F, SETTLE_MS);
        radio.nothing();
        assertEquals(DetectionStage.WAITING, radio.detector.stage());
    }

    @Test
    void progressAndEndsWhileHolding() {
        Radio radio = new Radio(-1.0F).stroke(1.0F).stroke(-1.0F);
        assertEquals(DetectionStage.HOLDING, radio.detector.stage());
        assertEquals(-1.0F, radio.detector.downEnd(), 1.0E-4F);
        assertEquals(1.0F, radio.detector.upEnd(), 1.0E-4F);
        float last = -1.0F;
        while (radio.result == null && radio.t < 10_000L) {
            float progress = radio.detector.progress(radio.t * MS);
            assertTrue(progress >= last && progress <= 1.0F, "Fortschritt springt zurück: " + last + " → " + progress);
            last = progress;
            radio.hold(-1.0F, FRAME_MS);
        }
        assertFalse(radio.detected().invert());
        assertTrue(last > 0.9F);
        assertTrue(Float.isNaN(new ThrottleDetector(new float[]{0.0F}, null).downEnd()));
    }

    @Test
    void holdTolerantToWobbleButNotToABounce() {
        Radio wobble = new Radio(-1.0F).stroke(1.0F).stroke(-1.0F).hold(-1.0F, 300L).move(-0.8F, 60L).move(-1.0F, 60L).hold(-1.0F, HOLD_MS);
        assertFalse(wobble.detected().invert());
        Radio bounce = new Radio(-1.0F).stroke(1.0F).stroke(-1.0F).hold(-1.0F, 300L).move(-0.4F, 100L).move(-1.0F, 100L).hold(-1.0F, SETTLE_MS);
        bounce.nothing();
        assertEquals(DetectionStage.ONE_END, bounce.detector.stage());
        bounce.stroke(1.0F).stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(bounce.detected().invert());
    }

    @Test
    void axisAppearingLaterIsTracked() {
        float[] baseline = {Float.NaN, Float.NaN, Float.NaN};
        ThrottleDetector detector = new ThrottleDetector(baseline, null);
        long t = 0L;
        StickDetector.Detection result = null;
        float[] path = {-1.0F, 1.0F, -1.0F};
        float current = Float.NaN;
        for (float target : path) {
            for (int i = 0; i <= 10; i++) {
                float v = Float.isNaN(current) ? target : current + (target - current) * i / 10.0F;
                result = result != null ? result : detector.update(new float[]{0.0F, v, 0.0F}, t * MS);
                t += FRAME_MS;
            }
            current = target;
        }
        for (long held = 0L; held < SETTLE_MS && result == null; held += FRAME_MS) {
            result = detector.update(new float[]{0.0F, -1.0F, 0.0F}, t * MS);
            t += FRAME_MS;
        }
        assertNotNull(result);
        assertEquals(1, result.axis());
        assertFalse(result.invert());
    }

    @ParameterizedTest
    @ValueSource(floats = {0.6F, 0.7F, 0.8F, 0.9F, 1.0F})
    void downStrokeThenUpAndRestingAtTheTopAsksInsteadOfInverting(float nudge) {
        for (long rest : new long[]{HOLD_MS + 100L, HOLD_MS + 1000L, 5000L}) {
            Radio radio = new Radio(-1.0F).move(nudge, 250L).hold(nudge, 100L).move(-1.0F, 250L).hold(-1.0F, 300L).stroke(1.0F).hold(1.0F, rest);
            radio.asks();
            assertEquals(1.0F, radio.detector.downEnd(), 1.0E-4F);
            assertFalse(radio.detector.confirm(false).invert());
            assertTrue(radio.detector.confirm(true).invert());
            radio.stroke(-1.0F).hold(-1.0F, SETTLE_MS);
            assertFalse(radio.detected().invert());
            Radio inverted = new Radio(1.0F).move(-nudge, 250L).hold(-nudge, 100L).move(1.0F, 250L).hold(1.0F, 300L).stroke(-1.0F).hold(-1.0F, rest);
            inverted.asks();
            assertTrue(inverted.detector.confirm(false).invert());
            inverted.stroke(1.0F).hold(1.0F, SETTLE_MS);
            assertTrue(inverted.detected().invert());
        }
    }

    @Test
    void repeatingUpDownUpAndRestingAtTheTopAsks() {
        new Radio(-1.0F).stroke(1.0F).stroke(-1.0F).stroke(1.0F).hold(1.0F, 2000L).asks();
        new Radio(-1.0F).stroke(1.0F).hold(1.0F, 500L).stroke(-1.0F).hold(-1.0F, 800L).stroke(1.0F).hold(1.0F, 2000L).asks();
    }

    @Test
    void glitchAtTheStartThenRestingAtTheTopAsks() {
        Radio radio = new Radio(-1.0F).hold(-1.0F, 200L);
        radio.frame(THROTTLE, 1.0F);
        radio.hold(-1.0F, 500L).stroke(1.0F).hold(1.0F, 2500L);
        radio.asks();
    }

    @Test
    void confirmIsOnlyAnsweredWhileAsking() {
        Radio radio = new Radio(-1.0F).stroke(1.0F).stroke(-1.0F).hold(-1.0F, 300L);
        assertEquals(DetectionStage.HOLDING, radio.detector.stage());
        assertNull(radio.detector.confirm(true));
        assertNull(new ThrottleDetector(new float[]{0.0F}, null).confirm(false));
    }

    @Test
    void leavingTheHeldEndDropsTheQuestion() {
        Radio radio = new Radio(-1.0F).stroke(1.0F).stroke(-1.0F).stroke(1.0F).hold(1.0F, 2000L);
        radio.asks();
        radio.move(0.2F, 120L);
        assertNotEquals(DetectionStage.CONFIRM, radio.detector.stage());
        assertNull(radio.detector.confirm(true));
        radio.stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(radio.detected().invert());
    }

    @Test
    void stickFromTheMiddleChecksTheProfileDirection() {
        for (float start : new float[]{-0.2F, 0.0F, 0.15F}) {
            Radio expectNormal = new Radio(start, THROTTLE, false).stroke(-1.0F).stroke(1.0F).hold(1.0F, SETTLE_MS);
            expectNormal.asks();
            assertFalse(expectNormal.detector.startedAtEnd(THROTTLE));
            assertFalse(expectNormal.detector.confirm(false).invert());
            Radio follows = new Radio(start, THROTTLE, false).stroke(1.0F).stroke(-1.0F).hold(-1.0F, SETTLE_MS);
            assertFalse(follows.detected().invert());
            Radio gamepad = new Radio(start, THROTTLE, true).stroke(-1.0F).stroke(1.0F).hold(1.0F, SETTLE_MS);
            assertTrue(gamepad.detected().invert());
            Radio otherAxis = new Radio(start, 0, false).stroke(-1.0F).stroke(1.0F).hold(1.0F, SETTLE_MS);
            assertTrue(otherAxis.detected().invert());
        }
    }

    @Test
    void startEndAgainstTheProfileDirectionAsks() {
        Radio topStart = new Radio(1.0F, THROTTLE, false).stroke(-1.0F).stroke(1.0F).hold(1.0F, SETTLE_MS);
        topStart.asks();
        assertTrue(topStart.detector.startedAtEnd(THROTTLE));
        assertFalse(topStart.detector.confirm(false).invert());
        assertTrue(topStart.detector.confirm(true).invert());
        topStart.stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertNull(topStart.result);
        assertEquals(DetectionStage.CONFIRM, topStart.detector.stage());
        assertFalse(topStart.detector.confirm(true).invert());
        Radio invertedProfile = new Radio(-1.0F, THROTTLE, true).stroke(1.0F).stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        invertedProfile.asks();
        assertFalse(invertedProfile.detector.confirm(true).invert());
    }

    @Test
    void startEndAndProfileDirectionAgreeingNeedNoQuestion() {
        Radio normal = new Radio(-1.0F, THROTTLE, false).stroke(1.0F).stroke(-1.0F).hold(-1.0F, SETTLE_MS);
        assertFalse(normal.detected().invert());
        Radio inverted = new Radio(1.0F, THROTTLE, true).stroke(-1.0F).stroke(1.0F).hold(1.0F, SETTLE_MS);
        assertTrue(inverted.detected().invert());
    }

    @Test
    void withTheProfileDirectionRestingAtTheTopIsNeverStoredWhereverTheStickStarts() {
        Random random = new Random(11L);
        for (int run = 0; run < 400; run++) {
            boolean invertedRadio = random.nextBoolean();
            float down = invertedRadio ? 1.0F : -1.0F;
            float start = random.nextInt(3) == 0 ? -down : random.nextFloat() * 2.0F - 1.0F;
            Radio radio = new Radio(start, THROTTLE, invertedRadio);
            int moves = random.nextInt(4);
            for (int i = 0; i < moves; i++) {
                float target = random.nextBoolean() ? 1.0F : random.nextFloat() * 2.0F - 1.0F;
                radio.move(target, 80L + random.nextInt(300)).hold(target, random.nextInt(600));
            }
            radio.stroke(down).hold(down, random.nextInt(400));
            StickDetector.Detection early = radio.result;
            radio.stroke(-down).hold(-down, 1000L + random.nextInt(3000));
            if (early != null) {
                assertEquals(invertedRadio, early.invert(), "Lauf " + run + ": Richtung gegen das Profil gespeichert");
                continue;
            }
            assertNull(radio.result, "Lauf " + run + ": Start " + start + ", oben gehalten und gespeichert");
            radio.stroke(down).hold(down, SETTLE_MS);
            if (radio.result != null) {
                assertEquals(invertedRadio, radio.result.invert(), "Lauf " + run + ": Richtung gegen das Profil gespeichert");
            } else {
                assertEquals(DetectionStage.CONFIRM, radio.detector.stage(), "Lauf " + run + ": unten gehalten, weder erkannt noch Rückfrage");
                assertEquals(invertedRadio, radio.detector.confirm(true).invert(), "Lauf " + run + ": Antwort „unten“ falsch umgesetzt");
            }
        }
    }

    @Test
    void neverStoresTheOppositeOfTheRestingEndForAnySequence() {
        Random random = new Random(7L);
        for (int run = 0; run < 400; run++) {
            boolean invertedRadio = random.nextBoolean();
            float down = invertedRadio ? 1.0F : -1.0F;
            float start = down * (0.8F + random.nextFloat() * 0.2F);
            Radio radio = new Radio(start);
            int moves = random.nextInt(5);
            for (int i = 0; i < moves; i++) {
                float target = random.nextFloat() * 2.0F - 1.0F;
                if (random.nextInt(3) == 0) {
                    target = random.nextBoolean() ? 1.0F : -1.0F;
                }
                radio.move(target, 80L + random.nextInt(300)).hold(target, random.nextInt(800));
            }
            boolean endsDown = random.nextBoolean();
            float end = endsDown ? down : -down;
            radio.stroke(-end).hold(-end, random.nextInt(400)).stroke(end).hold(end, 1000L + random.nextInt(3000));
            if (radio.result != null) {
                assertEquals(invertedRadio, radio.result.invert(), "Lauf " + run + ": Richtung gegen die Ruhelage gespeichert");
            }
            if (endsDown && radio.result == null) {
                assertNotEquals(DetectionStage.CONFIRM, radio.detector.stage(), "Lauf " + run + ": Rückfrage trotz Halten am Startende");
            }
            if (!endsDown) {
                assertNull(radio.result, "Lauf " + run + ": oben gehalten und gespeichert");
            }
        }
    }
}
