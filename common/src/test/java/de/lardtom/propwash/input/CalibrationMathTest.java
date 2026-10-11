package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationMathTest {
    private static final float EPS = 1.0E-4F;

    @Test
    void centeredMappingScalesBothHalvesSeparately() {
        ChannelMapping m = new ChannelMapping(0, false);
        m.min = -0.8F;
        m.center = 0.1F;
        m.max = 0.9F;
        assertEquals(0.0F, m.centered(0.1F), EPS);
        assertEquals(1.0F, m.centered(0.9F), EPS);
        assertEquals(-1.0F, m.centered(-0.8F), EPS);
        assertEquals(0.5F, m.centered(0.5F), EPS);
        assertEquals(-0.5F, m.centered(-0.35F), EPS);
        assertEquals(1.0F, m.centered(1.0F), EPS);
        assertEquals(-1.0F, m.centered(-1.0F), EPS);
    }

    @Test
    void invertFlipsCenteredAndLinear() {
        ChannelMapping m = new ChannelMapping(0, true);
        assertEquals(-0.5F, m.centered(0.5F), EPS);
        assertEquals(0.25F, m.linear(0.5F), EPS);
        assertEquals(1.0F, m.linear(-1.0F), EPS);
    }

    @Test
    void linearMappingUsesFullTravel() {
        ChannelMapping m = new ChannelMapping(2, false);
        m.min = -0.9F;
        m.center = 0.0F;
        m.max = 0.9F;
        assertEquals(0.0F, m.linear(-0.9F), EPS);
        assertEquals(0.5F, m.linear(0.0F), EPS);
        assertEquals(1.0F, m.linear(0.9F), EPS);
        assertEquals(1.0F, m.linear(1.0F), EPS);
        assertEquals(0.0F, m.linear(-1.0F), EPS);
    }

    @Test
    void degenerateCalibrationIsRepaired() {
        ChannelMapping m = new ChannelMapping(0, false);
        m.min = 0.5F;
        m.max = 0.5F;
        m.center = Float.NaN;
        m.sanitize();
        assertEquals(-1.0F, m.min, EPS);
        assertEquals(0.0F, m.center, EPS);
        assertEquals(1.0F, m.max, EPS);

        ChannelMapping offCenter = new ChannelMapping(0, false);
        offCenter.min = -0.5F;
        offCenter.max = 0.5F;
        offCenter.center = 0.7F;
        offCenter.sanitize();
        assertEquals(0.0F, offCenter.center, EPS);
    }

    @Test
    void deadbandRescalesOutsideBand() {
        assertEquals(0.0F, StickMapper.applyDeadband(0.04F, 0.05F), EPS);
        assertEquals(0.0F, StickMapper.applyDeadband(-0.05F, 0.05F), EPS);
        assertEquals(1.0F, StickMapper.applyDeadband(1.0F, 0.05F), EPS);
        assertEquals(-1.0F, StickMapper.applyDeadband(-1.0F, 0.05F), EPS);
        assertEquals(0.5F, StickMapper.applyDeadband(0.525F, 0.05F), EPS);
        assertEquals(0.0F, StickMapper.applyDeadband(Float.NaN, 0.05F), EPS);
    }

    @Test
    void recorderProducesCalibrationFromExtremes() {
        CalibrationRecorder rec = new CalibrationRecorder(4);
        rec.captureCenter(new float[]{0.02F, -0.01F, -0.95F, 0.0F});
        rec.observe(new float[]{0.97F, 0.9F, 0.96F, 0.0F});
        rec.observe(new float[]{-0.93F, -0.88F, -0.97F, 0.1F});
        ChannelMapping roll = new ChannelMapping(0, false);
        assertTrue(rec.apply(roll, true));
        assertEquals(-0.93F, roll.min, 1.0E-3F);
        assertEquals(0.02F, roll.center, 1.0E-3F);
        assertEquals(0.97F, roll.max, 1.0E-3F);
        assertEquals(1.0F, roll.centered(0.97F), EPS);
        assertEquals(0.0F, roll.centered(0.02F), EPS);

        ChannelMapping throttle = new ChannelMapping(2, false);
        assertTrue(rec.apply(throttle, false));
        assertEquals(-0.97F, throttle.min, 1.0E-3F);
        assertEquals(0.96F, throttle.max, 1.0E-3F);
        assertEquals((throttle.min + throttle.max) / 2.0F, throttle.center, 1.0E-3F);

        ChannelMapping yaw = new ChannelMapping(3, false);
        assertFalse(rec.apply(yaw, true));
        assertEquals(-1.0F, yaw.min, EPS);
    }

    @Test
    void recorderIgnoresCenterOutsideTravel() {
        CalibrationRecorder rec = new CalibrationRecorder(1);
        rec.captureCenter(new float[]{0.95F});
        rec.observe(new float[]{-1.0F});
        rec.observe(new float[]{1.0F});
        ChannelMapping m = new ChannelMapping(0, false);
        assertTrue(rec.apply(m, true));
        assertEquals(0.0F, m.center, 1.0E-3F);
    }

    @Test
    void throttleModes() {
        DeviceProfile p = new DeviceProfile();
        p.deadband = 0.0F;
        p.throttle = new ChannelMapping(0, false);
        float[] low = {-1.0F, 0, 0, 0};
        float[] mid = {0.0F, 0, 0, 0};
        float[] high = {1.0F, 0, 0, 0};
        p.throttleMode = ThrottleMode.FULL_RANGE;
        assertEquals(0.0F, StickMapper.staticThrottle(p, low), EPS);
        assertEquals(0.5F, StickMapper.staticThrottle(p, mid), EPS);
        assertEquals(1.0F, StickMapper.staticThrottle(p, high), EPS);
        p.throttleMode = ThrottleMode.UPPER_HALF;
        assertEquals(0.0F, StickMapper.staticThrottle(p, low), EPS);
        assertEquals(0.0F, StickMapper.staticThrottle(p, mid), EPS);
        assertEquals(1.0F, StickMapper.staticThrottle(p, high), EPS);

        p.throttleMode = ThrottleMode.INCREMENTAL;
        p.throttleRate = 1.0F;
        StickMapper mapper = new StickMapper();
        for (int i = 0; i < 25; i++) {
            mapper.map(p, high, 0.02F);
        }
        assertEquals(0.5F, mapper.map(p, mid, 0.02F).throttle(), 0.02F);
        for (int i = 0; i < 100; i++) {
            mapper.map(p, low, 0.02F);
        }
        assertEquals(0.0F, mapper.map(p, mid, 0.02F).throttle(), EPS);
    }

    @Test
    void unassignedChannelsStayIdle() {
        DeviceProfile p = new DeviceProfile();
        p.roll = ChannelMapping.unassigned();
        p.throttle = new ChannelMapping(9, false);
        StickPositions s = new StickMapper().map(p, new float[]{1.0F, 1.0F, 1.0F, 1.0F}, 0.016F);
        assertEquals(0.0F, s.roll(), EPS);
        assertEquals(0.0F, s.throttle(), EPS);
        assertEquals(1.0F, s.pitch(), EPS);
    }

    @Test
    void microsConversion() {
        assertEquals(1500, ChannelMath.centeredMicros(0.0F));
        assertEquals(2000, ChannelMath.centeredMicros(1.0F));
        assertEquals(1000, ChannelMath.centeredMicros(-1.5F));
        assertEquals(1000, ChannelMath.throttleMicros(0.0F));
        assertEquals(1750, ChannelMath.throttleMicros(0.75F));
        assertEquals(0.4F, ChannelMath.fromMicros(1700), EPS);
    }
}
