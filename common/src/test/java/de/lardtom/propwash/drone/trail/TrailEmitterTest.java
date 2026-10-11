package de.lardtom.propwash.drone.trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailEmitterTest {
    private static final double FRAME = 1.0 / 144.0;

    private static void fly(TrailEmitter emitter, double from, double to, double speed, boolean emits) {
        for (double t = from; t < to; t += FRAME) {
            emitter.update(t, speed * t, 64.0, 0.0, emits);
        }
    }

    @Test
    void samplesAtAFixedRateIndependentOfTheFrameRate() {
        TrailEmitter fast = new TrailEmitter(TrailModel.SAMPLES_PER_TRAIL);
        fly(fast, 0.0, 0.5, 10.0, true);
        TrailEmitter slow = new TrailEmitter(TrailModel.SAMPLES_PER_TRAIL);
        for (double t = 0.0; t < 0.5; t += 1.0 / 30.0) {
            slow.update(t, 10.0 * t, 64.0, 0.0, true);
        }
        int expected = (int) Math.round(0.5 / TrailModel.SAMPLE_INTERVAL);
        assertTrue(Math.abs(fast.buffer().size() - expected) <= 2, "144 fps: " + fast.buffer().size());
        assertTrue(slow.buffer().size() <= expected + 1, "30 fps: " + slow.buffer().size());
        assertTrue(slow.buffer().size() >= 14, "30 fps: " + slow.buffer().size());
    }

    @Test
    void trailLengthFollowsTheSpeed() {
        TrailEmitter slow = new TrailEmitter(TrailModel.SAMPLES_PER_TRAIL);
        fly(slow, 0.0, 4.0, 3.0, true);
        TrailEmitter fast = new TrailEmitter(TrailModel.SAMPLES_PER_TRAIL);
        fly(fast, 0.0, 4.0, 30.0, true);
        TrailBuffer s = slow.buffer();
        TrailBuffer f = fast.buffer();
        double slowSpan = s.time(s.newest()) - s.time(0);
        double fastSpan = f.time(f.newest()) - f.time(0);
        assertEquals(TrailModel.MIN_LIFE, slowSpan, 0.06);
        assertEquals(TrailModel.MAX_LIFE, fastSpan, 0.06);
        assertTrue(f.size() <= TrailModel.SAMPLES_PER_TRAIL);
        assertEquals(30.0, fast.speed(), 0.5);
    }

    @Test
    void noNewSamplesWhileNotEmittingAndTheTrailFadesOut() {
        TrailEmitter emitter = new TrailEmitter(TrailModel.SAMPLES_PER_TRAIL);
        fly(emitter, 0.0, 1.0, 10.0, true);
        assertTrue(emitter.emitting());
        int before = emitter.buffer().size();
        emitter.update(1.0, 10.0, 64.0, 0.0, false);
        assertFalse(emitter.emitting());
        assertTrue(emitter.buffer().size() <= before);
        fly(emitter, 1.0, 1.5, 10.0, false);
        assertTrue(emitter.buffer().size() < before);
        fly(emitter, 1.5, 3.5, 10.0, false);
        assertTrue(emitter.isEmpty());
        assertEquals(0.0, emitter.speed());
    }

    @Test
    void hoveringInPlaceAddsNoSamples() {
        TrailEmitter emitter = new TrailEmitter(TrailModel.SAMPLES_PER_TRAIL);
        for (double t = 0.0; t < 3.0; t += FRAME) {
            emitter.update(t, 5.0 + 0.001 * Math.sin(t), 64.0, 0.0, true);
        }
        assertTrue(emitter.buffer().size() <= 1, "Proben: " + emitter.buffer().size());
        assertTrue(TrailModel.landed(true, emitter.speed()));
    }

    @Test
    void aTeleportStartsANewTrail() {
        TrailEmitter emitter = new TrailEmitter(TrailModel.SAMPLES_PER_TRAIL);
        fly(emitter, 0.0, 1.0, 10.0, true);
        emitter.update(1.05, 500.0, 64.0, 0.0, true);
        assertEquals(1, emitter.buffer().size());
        assertEquals(500.0, emitter.buffer().x(0));
    }

    @Test
    void aSmallStepBackInTimeKeepsTheTrailALargeOneResetsAndTheSameFrameTwiceIsIgnored() {
        TrailEmitter emitter = new TrailEmitter(TrailModel.SAMPLES_PER_TRAIL);
        fly(emitter, 10.0, 11.0, 10.0, true);
        int size = emitter.buffer().size();
        double newest = emitter.buffer().time(emitter.buffer().newest());
        emitter.update(10.99, 200.0, 64.0, 0.0, true);
        assertEquals(size, emitter.buffer().size());
        emitter.update(11.0 - TrailModel.MAX_REWIND, 200.0, 64.0, 0.0, true);
        assertEquals(size, emitter.buffer().size());
        assertEquals(newest, emitter.buffer().time(emitter.buffer().newest()));
        emitter.update(11.05, 110.5, 64.0, 0.0, true);
        assertEquals(11.05, emitter.buffer().time(emitter.buffer().newest()), 1.0E-9);
        assertTrue(emitter.buffer().size() >= size - 3);
        emitter.update(2.0, 0.0, 64.0, 0.0, true);
        assertEquals(1, emitter.buffer().size());
        emitter.update(2.0, 50.0, 64.0, 0.0, true);
        assertEquals(1, emitter.buffer().size());
        emitter.update(Double.NaN, 0.0, 64.0, 0.0, true);
        emitter.update(3.0, Double.POSITIVE_INFINITY, 64.0, 0.0, true);
        assertEquals(0.0, emitter.buffer().x(0));
    }
}
