package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseSticksTest {
    private static final float EPS = 1.0E-4F;

    private static MouseSettings plain(MouseMode mode) {
        MouseSettings s = new MouseSettings();
        s.mode = mode;
        s.deadzone = 0.0F;
        s.expo = 0.0F;
        s.smoothingMs = 0.0F;
        s.returnSpeed = 0.0F;
        s.dpi = 800;
        s.sensitivity = 1.0F;
        return s;
    }

    private static float counts(float cm, int dpi) {
        return cm / MouseSticks.CM_PER_INCH * dpi;
    }

    private static MouseSticks.Output run(MouseSticks sticks, MouseSettings s, float cmPerSecondX, float cmPerSecondForward, float seconds, float fps,
                                          boolean selfLevel) {
        return run(sticks, s, cmPerSecondX, cmPerSecondForward, seconds, fps, selfLevel, false);
    }

    private static MouseSticks.Output run(MouseSticks sticks, MouseSettings s, float cmPerSecondX, float cmPerSecondForward, float seconds, float fps,
                                          boolean selfLevel, boolean invertPitch) {
        float dt = 1.0F / fps;
        int frames = Math.round(seconds * fps);
        MouseSticks.Output out = sticks.last();
        for (int i = 0; i < frames; i++) {
            out = sticks.update(counts(cmPerSecondX * dt, s.dpi), -counts(cmPerSecondForward * dt, s.dpi), dt, s, selfLevel, invertPitch);
        }
        return out;
    }

    @Test
    void rateModeMouseSpeedIsDeflection() {
        MouseSettings s = plain(MouseMode.RATE);
        MouseSticks sticks = new MouseSticks();
        MouseSticks.Output out = run(sticks, s, 15.0F, 0.0F, 0.2F, 100.0F, true);
        assertEquals(0.5F, out.roll(), EPS);
        assertEquals(0.0F, out.pitch(), EPS);
        assertEquals(0.0F, out.yaw(), EPS);
        assertFalse(out.holdX());
        out = run(sticks, s, -60.0F, 0.0F, 0.1F, 100.0F, true);
        assertEquals(-1.0F, out.roll(), EPS);
    }

    @Test
    void rateModeSpringsBackWhenTheMouseStops() {
        MouseSettings s = plain(MouseMode.RATE);
        MouseSticks sticks = new MouseSticks();
        run(sticks, s, 0.0F, 15.0F, 0.2F, 100.0F, false);
        assertEquals(0.5F, sticks.last().pitch(), EPS);
        MouseSticks.Output out = run(sticks, s, 0.0F, 0.0F, 0.01F, 100.0F, false);
        assertEquals(0.0F, out.pitch(), EPS);

        s.smoothingMs = 20.0F;
        run(sticks, s, 0.0F, 15.0F, 0.2F, 100.0F, false);
        assertEquals(0.5F, sticks.last().pitch(), EPS);
        out = run(sticks, s, 0.0F, 0.0F, 0.01F, 100.0F, false);
        assertEquals(0.25F, out.pitch(), EPS);
        out = run(sticks, s, 0.0F, 0.0F, 0.01F, 100.0F, false);
        assertEquals(0.0F, out.pitch(), EPS);
    }

    @Test
    void holdModeKeepsTheStickWhereItWasPushed() {
        MouseSettings s = plain(MouseMode.HOLD);
        MouseSticks sticks = new MouseSticks();
        run(sticks, s, 0.0F, 6.0F, 0.5F, 60.0F, true);
        assertEquals(0.5F, sticks.last().pitch(), 1.0E-3F);
        assertTrue(sticks.last().holdY());
        MouseSticks.Output out = run(sticks, s, 0.0F, 0.0F, 2.0F, 60.0F, true);
        assertEquals(0.5F, out.pitch(), 1.0E-3F);
        run(sticks, s, 0.0F, -6.0F, 0.5F, 60.0F, true);
        assertEquals(0.0F, sticks.last().pitch(), 1.0E-3F);
        run(sticks, s, 0.0F, 30.0F, 1.0F, 60.0F, true);
        assertEquals(1.0F, sticks.last().pitch(), EPS);
    }

    @Test
    void holdModeReturnsWithTheReturnSpeed() {
        MouseSettings s = plain(MouseMode.HOLD);
        s.returnSpeed = 0.5F;
        MouseSticks sticks = new MouseSticks();
        sticks.update(counts(-3.0F, s.dpi), 0.0F, 0.01F, s, true, false);
        assertEquals(-0.5F, sticks.last().roll(), 1.0E-3F);
        MouseSticks.Output out = run(sticks, s, 0.0F, 0.0F, 0.5F, 100.0F, true);
        assertEquals(-0.25F, out.roll(), 1.0E-3F);
        out = run(sticks, s, 0.0F, 0.0F, 1.0F, 100.0F, true);
        assertEquals(0.0F, out.roll(), EPS);
    }

    @Test
    void centerKeyLevelsTheHeldStick() {
        MouseSettings s = plain(MouseMode.HOLD);
        MouseSticks sticks = new MouseSticks();
        run(sticks, s, 6.0F, 6.0F, 0.5F, 60.0F, true);
        assertTrue(sticks.last().roll() > 0.4F);
        sticks.center();
        MouseSticks.Output out = sticks.update(0.0F, 0.0F, 1.0F / 60.0F, s, true, false);
        assertEquals(0.0F, out.roll(), EPS);
        assertEquals(0.0F, out.pitch(), EPS);
    }

    @Test
    void sensitivityScalesTheNeededMouseSpeed() {
        MouseSettings s = plain(MouseMode.RATE);
        s.sensitivity = 2.0F;
        MouseSticks sticks = new MouseSticks();
        assertEquals(0.5F, run(sticks, s, 7.5F, 0.0F, 0.2F, 100.0F, false).roll(), EPS);
        MouseSettings hold = plain(MouseMode.HOLD);
        hold.sensitivity = 0.5F;
        MouseSticks held = new MouseSticks();
        assertEquals(0.5F, run(held, hold, 12.0F, 0.0F, 0.5F, 100.0F, true).roll(), 1.0E-3F);
    }

    @Test
    void sameDeskTravelGivesTheSameSticksForEveryDpi() {
        for (MouseMode mode : MouseMode.values()) {
            MouseSettings low = plain(mode);
            low.dpi = 400;
            low.deadzone = 0.03F;
            low.expo = 0.3F;
            low.smoothingMs = 12.0F;
            low.returnSpeed = 0.4F;
            MouseSettings high = low.copy();
            high.dpi = 1600;
            MouseSticks a = new MouseSticks();
            MouseSticks b = new MouseSticks();
            float dt = 1.0F / 144.0F;
            for (int i = 0; i < 300; i++) {
                float cm = (float) Math.sin(i * 0.05) * 0.3F;
                float forward = i < 150 ? 0.08F : 0.0F;
                MouseSticks.Output oa = a.update(counts(cm, 400), -counts(forward, 400), dt, low, i % 100 < 50, false);
                MouseSticks.Output ob = b.update(counts(cm, 1600), -counts(forward, 1600), dt, high, i % 100 < 50, false);
                assertEquals(oa.roll(), ob.roll(), 1.0E-4F, mode + " Bild " + i);
                assertEquals(oa.pitch(), ob.pitch(), 1.0E-4F, mode + " Bild " + i);
            }
        }
    }

    @Test
    void forwardIsPitchForwardUnlessInverted() {
        MouseSettings s = plain(MouseMode.RATE);
        MouseSticks sticks = new MouseSticks();
        MouseSticks.Output normal = run(sticks, s, 3.0F, 10.0F, 0.1F, 100.0F, false);
        assertTrue(normal.pitch() > 0.3F);
        MouseSticks inverted = new MouseSticks();
        MouseSticks.Output flipped = run(inverted, s, 3.0F, 10.0F, 0.1F, 100.0F, false, true);
        assertEquals(-normal.pitch(), flipped.pitch());
        assertEquals(normal.roll(), flipped.roll());
        MouseSettings hold = plain(MouseMode.HOLD);
        MouseSticks.Output held = run(new MouseSticks(), hold, 0.0F, 4.0F, 0.5F, 100.0F, true);
        MouseSticks.Output heldFlipped = run(new MouseSticks(), hold, 0.0F, 4.0F, 0.5F, 100.0F, true, true);
        assertTrue(held.pitch() > 0.1F);
        assertEquals(-held.pitch(), heldFlipped.pitch());
        assertTrue(heldFlipped.holdY());
    }

    @Test
    void mouseXCanYawAndYawAlwaysFollowsSpeed() {
        MouseSettings s = plain(MouseMode.HOLD);
        s.xAxis = MouseAxis.YAW;
        MouseSticks sticks = new MouseSticks();
        MouseSticks.Output out = run(sticks, s, 15.0F, 0.0F, 0.2F, 100.0F, true);
        assertEquals(0.5F, out.yaw(), EPS);
        assertEquals(0.0F, out.roll(), EPS);
        assertFalse(out.holdX());
        assertTrue(out.holdY());
        out = run(sticks, s, 0.0F, 0.0F, 0.02F, 100.0F, true);
        assertEquals(0.0F, out.yaw(), EPS);
    }

    @Test
    void automaticHoldsInSelfLevelModesAndFollowsSpeedInAcro() {
        MouseSettings s = plain(MouseMode.AUTO);
        assertTrue(MouseMode.AUTO.holds(true));
        assertFalse(MouseMode.AUTO.holds(false));
        assertTrue(MouseMode.HOLD.holds(false));
        assertFalse(MouseMode.RATE.holds(true));
        MouseSticks sticks = new MouseSticks();
        run(sticks, s, 0.0F, 6.0F, 0.5F, 100.0F, true);
        assertEquals(0.5F, run(sticks, s, 0.0F, 0.0F, 0.5F, 100.0F, true).pitch(), 1.0E-3F);
        MouseSticks.Output acro = run(sticks, s, 0.0F, 0.0F, 0.01F, 100.0F, false);
        assertEquals(0.0F, acro.pitch(), EPS);
        assertFalse(acro.holdY());
        MouseSticks.Output angle = run(sticks, s, 0.0F, 0.0F, 0.01F, 100.0F, true);
        assertEquals(0.0F, angle.pitch(), EPS);
    }

    @Test
    void deadzoneAndExpoShapeTheOutput() {
        assertEquals(0.0F, MouseSticks.shape(0.02F, 0.03F, 0.0F), EPS);
        assertEquals(0.0F, MouseSticks.shape(-0.03F, 0.03F, 0.5F), EPS);
        assertEquals(1.0F, MouseSticks.shape(1.0F, 0.03F, 0.5F), EPS);
        assertEquals(-1.0F, MouseSticks.shape(-3.0F, 0.1F, 1.0F), EPS);
        assertEquals(0.125F, MouseSticks.shape(0.5F, 0.0F, 1.0F), EPS);
        assertEquals(0.5F, MouseSticks.shape(0.5F, 0.0F, 0.0F), EPS);
        assertEquals(0.0F, MouseSticks.shape(Float.NaN, 0.0F, 0.0F), EPS);
        float previous = -1.0F;
        for (int i = -100; i <= 100; i++) {
            float v = MouseSticks.shape(i / 100.0F, 0.05F, 0.4F);
            assertTrue(v >= previous - EPS, "monoton bei " + i);
            previous = v;
        }
    }

    @Test
    void sameMotionGivesTheSameDeflectionAtEveryFrameRate() {
        MouseSettings s = plain(MouseMode.RATE);
        s.smoothingMs = 15.0F;
        for (float fps : new float[]{30.0F, 60.0F, 144.0F, 240.0F, 500.0F}) {
            MouseSticks sticks = new MouseSticks();
            assertEquals(0.4F, run(sticks, s, 12.0F, 0.0F, 0.5F, fps, false).roll(), 0.01F, fps + " fps");
        }
    }

    @Test
    void slowMousePollingStaysSteadyAtEveryFrameRate() {
        MouseSettings s = plain(MouseMode.RATE);
        s.smoothingMs = MouseSettings.DEFAULT_SMOOTHING_MS;
        for (int fps : new int[]{60, 144, 240, 360}) {
            for (int hz : new int[]{125, 500, 1000}) {
                float[] range = polled(s, fps, hz, 12.0F);
                assertTrue(range[0] > 0.4F * 0.8F && range[1] < 0.4F * 1.2F, fps + " fps, " + hz + " Hz: " + range[0] + " … " + range[1]);
                if (hz >= 500) {
                    assertTrue(range[0] > 0.4F * 0.95F && range[1] < 0.4F * 1.05F, fps + " fps, " + hz + " Hz: " + range[0] + " … " + range[1]);
                }
            }
        }
    }

    private static float[] polled(MouseSettings s, int fps, int hz, float cmPerSecond) {
        MouseSticks sticks = new MouseSticks();
        long frame = 1_000_000_000L / fps;
        long poll = 1_000_000_000L / hz;
        float countsPerPoll = counts(cmPerSecond * poll / 1.0E9F, s.dpi);
        long nextPoll = 3_100_000L;
        long now = 0L;
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < fps * 2; i++) {
            now += frame;
            while (nextPoll <= now) {
                long jitter = (nextPoll / poll % 7 - 3) * 100_000L;
                sticks.motion(nextPoll + jitter, countsPerPoll, 0.0F);
                nextPoll += poll;
            }
            MouseSticks.Output out = sticks.update(now, frame / 1.0E9F, s, false, false);
            if (i > fps / 2) {
                min = Math.min(min, out.roll());
                max = Math.max(max, out.roll());
            }
        }
        return new float[]{min, max};
    }

    @Test
    void invalidInputKeepsTheLastValue() {
        MouseSettings s = plain(MouseMode.RATE);
        MouseSticks sticks = new MouseSticks();
        MouseSticks.Output before = run(sticks, s, 15.0F, 0.0F, 0.1F, 100.0F, false);
        assertEquals(before, sticks.update(5.0F, 5.0F, 0.0F, s, false, false));
        assertEquals(before, sticks.update(5.0F, 5.0F, Float.NaN, s, false, false));
        MouseSticks.Output nan = sticks.update(Float.NaN, Float.POSITIVE_INFINITY, 0.01F, s, false, false);
        assertTrue(Float.isFinite(nan.roll()) && Float.isFinite(nan.pitch()));
        sticks.reset();
        assertEquals(MouseSticks.Output.CENTER, sticks.last());
    }
}
