package de.lardtom.propwash.osd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdSamplerTest {
    private static final long SECOND = 1_000_000_000L;
    private static final long FRAME = SECOND / 50L;

    private static final class FakeSource implements OsdSampler.Source {
        boolean active = true;
        boolean armed;
        double speed = 10.0;
        float stickRoll;
        long split;
        boolean racing;
        int fills;
        double y = Double.NaN;
        double homeY = Double.NaN;

        @Override
        public boolean active() {
            return this.active;
        }

        @Override
        public void fill(OsdData data) {
            this.fills++;
            data.armed = this.armed;
            data.speed = this.speed;
            data.stickRoll = this.stickRoll;
            data.racing = this.racing;
            data.splitDeltaNanos = this.split;
            data.cellVoltage = 3.9;
            if (Double.isFinite(this.y)) {
                data.hasPosition = true;
                data.y = this.y;
            }
            if (Double.isFinite(this.homeY)) {
                data.hasHome = true;
                data.homeY = this.homeY;
            }
        }
    }

    private static long run(OsdSampler sampler, long now, long duration) {
        long end = now + duration;
        long t = now;
        while (t < end) {
            t += FRAME;
            sampler.sample(t);
        }
        return t;
    }

    @Test
    void inactiveSourceLeavesTheOsdOff() {
        FakeSource source = new FakeSource();
        source.active = false;
        OsdSampler sampler = new OsdSampler(source);
        assertFalse(sampler.sample(SECOND).active);
        assertEquals(0, source.fills);
    }

    @Test
    void flightTimeCountsOnlyWhileArmedAndOnTimeAlways() {
        FakeSource source = new FakeSource();
        OsdSampler sampler = new OsdSampler(source);
        long t = run(sampler, 0L, 2 * SECOND);
        OsdData data = sampler.data();
        assertTrue(data.active);
        assertEquals(0.0, data.flightSeconds, 1.0E-9);
        assertEquals(2.0, data.onSeconds, 0.05);
        source.armed = true;
        t = run(sampler, t, 3 * SECOND);
        assertEquals(3.0, data.flightSeconds, 0.05);
        assertTrue(data.armedSeconds > 2.9);
        source.armed = false;
        run(sampler, t, SECOND);
        assertEquals(3.0, data.flightSeconds, 0.05);
        assertEquals(0.0, data.armedSeconds, 1.0E-9);
    }

    @Test
    void statsAppearAfterDisarmAndDisappearOnRearmOrSticks() {
        FakeSource source = new FakeSource();
        OsdSampler sampler = new OsdSampler(source);
        OsdData data = sampler.data();
        long t = run(sampler, 0L, SECOND);
        source.armed = true;
        source.speed = 25.0;
        t = run(sampler, t, 4 * SECOND);
        assertFalse(data.showStats);
        source.armed = false;
        t = run(sampler, t, SECOND);
        assertTrue(data.showStats);
        assertEquals(25.0, data.stats.maxSpeed, 1.0E-9);
        assertEquals(4.0, data.stats.flightSeconds, 0.05);
        source.stickRoll = 0.9F;
        t = run(sampler, t, FRAME);
        assertTrue(data.showStats);
        t = run(sampler, t, SECOND);
        assertFalse(data.showStats);
        source.stickRoll = 0.0F;
        source.armed = true;
        t = run(sampler, t, 3 * SECOND);
        source.armed = false;
        t = run(sampler, t, FRAME);
        assertTrue(data.showStats);
        source.armed = true;
        run(sampler, t, FRAME);
        assertFalse(data.showStats);
        assertTrue(data.stats.flightSeconds < 0.1);
    }

    @Test
    void shortHopsDoNotShowStats() {
        FakeSource source = new FakeSource();
        OsdSampler sampler = new OsdSampler(source);
        source.armed = true;
        long t = run(sampler, 0L, SECOND);
        source.armed = false;
        run(sampler, t, FRAME);
        assertFalse(sampler.data().showStats);
    }

    @Test
    void statsTimeOut() {
        FakeSource source = new FakeSource();
        OsdSampler sampler = new OsdSampler(source);
        source.armed = true;
        long t = run(sampler, 0L, 5 * SECOND);
        source.armed = false;
        t = run(sampler, t, FRAME);
        assertTrue(sampler.data().showStats);
        run(sampler, t, (long) (OsdSampler.STATS_MAX_SECONDS + 1.0) * SECOND);
        assertFalse(sampler.data().showStats);
    }

    @Test
    void splitAgeRestartsWhenTheDeltaChanges() {
        FakeSource source = new FakeSource();
        OsdSampler sampler = new OsdSampler(source);
        source.racing = true;
        source.split = -300_000_000L;
        long t = run(sampler, 0L, 2 * SECOND);
        assertEquals(2.0, sampler.data().splitAgeSeconds, 0.05);
        source.split = 150_000_000L;
        run(sampler, t, SECOND / 2L);
        assertEquals(0.5, sampler.data().splitAgeSeconds, 0.05);
        source.split = 0L;
        run(sampler, t, FRAME);
        assertEquals(OsdData.NO_SPLIT, sampler.data().splitAgeSeconds);
    }

    @Test
    void endingTheSessionResetsEverything() {
        FakeSource source = new FakeSource();
        OsdSampler sampler = new OsdSampler(source);
        source.armed = true;
        long t = run(sampler, 0L, 5 * SECOND);
        source.active = false;
        t = run(sampler, t, FRAME);
        assertFalse(sampler.data().active);
        assertEquals(0.0, sampler.data().flightSeconds, 1.0E-9);
        source.active = true;
        source.armed = false;
        run(sampler, t, FRAME);
        assertTrue(sampler.data().active);
        assertFalse(sampler.data().showStats);
        assertEquals(0.0, sampler.data().flightSeconds, 1.0E-9);
    }

    @Test
    void overrideReplacesTheLiveSource() {
        FakeSource live = new FakeSource();
        live.active = false;
        FakeSource override = new FakeSource();
        OsdSampler sampler = new OsdSampler(live);
        sampler.setOverride(override);
        assertTrue(sampler.hasOverride());
        assertTrue(sampler.sample(SECOND).active);
        assertEquals(1, override.fills);
        sampler.setOverride(null);
        assertFalse(sampler.sample(2 * SECOND).active);
        assertEquals(0, live.fills);
    }

    @Test
    void altitudeUsesHomeHeightAsTheOnlyReference() {
        FakeSource source = new FakeSource();
        source.homeY = 80.0;
        source.y = 84.9;
        OsdSampler sampler = new OsdSampler(source);
        assertEquals(4.9, sampler.sample(SECOND).altitude, 1.0E-9);
        source.y = 85.1;
        assertEquals(5.1, sampler.sample(SECOND + FRAME).altitude, 1.0E-9);
        source.y = 65.0;
        assertEquals(-15.0, sampler.sample(SECOND + 2 * FRAME).altitude, 1.0E-9);
    }

    @Test
    void altitudeWithoutHomeIsRelativeToTheArmingHeight() {
        FakeSource source = new FakeSource();
        source.y = 70.0;
        OsdSampler sampler = new OsdSampler(source);
        assertEquals(0.0, sampler.sample(SECOND).altitude, 1.0E-9);
        source.y = 72.0;
        assertEquals(2.0, sampler.sample(SECOND + FRAME).altitude, 1.0E-9);
        source.armed = true;
        assertEquals(0.0, sampler.sample(SECOND + 2 * FRAME).altitude, 1.0E-9);
        source.y = 90.0;
        assertEquals(18.0, sampler.sample(SECOND + 3 * FRAME).altitude, 1.0E-9);
        source.y = Double.NaN;
        assertTrue(Double.isNaN(sampler.sample(SECOND + 4 * FRAME).altitude));
    }
}
