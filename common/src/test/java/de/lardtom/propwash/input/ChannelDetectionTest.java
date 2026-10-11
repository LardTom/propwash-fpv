package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelDetectionTest {
    private static final long MS = 1_000_000L;

    private static StickDetector.Detection feed(ChannelDetector detector, float[] axes, long startMs, long durationMs) {
        StickDetector.Detection result = null;
        for (long t = startMs; t <= startMs + durationMs && result == null; t += 16) {
            result = detector.update(axes, t * MS);
        }
        return result;
    }

    @Test
    void detectsCenteredAxisAndDirection() {
        float[] rest = {0.0F, 0.0F, -1.0F, 0.0F, 0.0F};
        ChannelDetector detector = ChannelDetector.centered(rest, new boolean[5]);
        assertNull(detector.update(rest, 0L));
        StickDetector.Detection d = feed(detector, new float[]{0.02F, 0.05F, -1.0F, -0.95F, 0.0F}, 16, 400);
        assertNotNull(d);
        assertEquals(3, d.axis());
        assertTrue(d.invert());
    }

    @Test
    void ignoresSmallOrAmbiguousMovement() {
        float[] rest = {0.0F, 0.0F, 0.0F};
        ChannelDetector detector = ChannelDetector.centered(rest, null);
        assertNull(feed(detector, new float[]{0.3F, 0.0F, 0.0F}, 0, 500));
        assertNull(feed(detector, new float[]{0.9F, 0.8F, 0.0F}, 600, 500));
    }

    @Test
    void briefSpikeDoesNotCount() {
        float[] rest = {0.0F, 0.0F};
        ChannelDetector detector = ChannelDetector.centered(rest, null);
        assertNull(detector.update(new float[]{1.0F, 0.0F}, 0L));
        assertNull(detector.update(new float[]{1.0F, 0.0F}, 50 * MS));
        assertNull(detector.update(rest, 60 * MS));
        assertNull(detector.update(new float[]{1.0F, 0.0F}, 100 * MS));
        assertNull(detector.update(new float[]{1.0F, 0.0F}, 180 * MS));
        assertNotNull(detector.update(new float[]{1.0F, 0.0F}, 230 * MS));
    }

    @Test
    void excludedAxesAreSkipped() {
        float[] rest = {0.0F, 0.0F};
        boolean[] excluded = {true, false};
        ChannelDetector detector = ChannelDetector.centered(rest, excluded);
        assertNull(feed(detector, new float[]{1.0F, 0.1F}, 0, 400));
        StickDetector.Detection d = feed(detector, new float[]{1.0F, 0.9F}, 500, 400);
        assertNotNull(d);
        assertEquals(1, d.axis());
        assertFalse(d.invert());
    }

    @Test
    void stickHeldAtStartIsNotTakenAsRest() {
        float[] held = {1.0F, 0.0F, -1.0F, 0.0F};
        ChannelDetector detector = ChannelDetector.centered(held, new boolean[4]);
        assertFalse(detector.restKnown(0));
        assertNull(feed(detector, new float[]{0.0F, 0.0F, -1.0F, 0.0F}, 0, 400));
        assertTrue(detector.restKnown(0));
        StickDetector.Detection d = feed(detector, new float[]{1.0F, 0.0F, -1.0F, 0.0F}, 420, 400);
        assertNotNull(d);
        assertEquals(0, d.axis());
        assertFalse(d.invert());
    }

    @Test
    void releasingAHeldStickNeverCountsAsMovement() {
        ChannelDetector detector = ChannelDetector.centered(new float[]{-1.0F, 0.0F}, null);
        for (long t = 0; t <= 2000; t += 16) {
            float roll = t < 200 ? -1.0F + t / 200.0F : 0.0F;
            assertNull(detector.update(new float[]{roll, 0.0F}, t * MS), "Loslassen bei " + t + " ms erkannt");
        }
    }

    @Test
    void centeredFeedbackStages() {
        float[] rest = {0.0F, 0.0F, 0.0F};
        ChannelDetector detector = ChannelDetector.centered(rest, null);
        detector.update(rest, 0L);
        assertEquals(DetectionStage.WAITING, detector.stage());
        detector.update(new float[]{0.3F, 0.0F, 0.0F}, 16 * MS);
        assertEquals(DetectionStage.TOO_SMALL, detector.stage());
        assertEquals(0, detector.leadingAxis());
        detector.update(new float[]{0.9F, 0.8F, 0.0F}, 32 * MS);
        assertEquals(DetectionStage.AMBIGUOUS, detector.stage());
        detector.update(new float[]{0.9F, 0.0F, 0.0F}, 48 * MS);
        assertEquals(DetectionStage.HOLDING, detector.stage());
        assertEquals(0.0F, detector.progress(48 * MS), 1.0E-6F);
        assertEquals(0.5F, detector.progress(108 * MS), 1.0E-6F);
    }

    @Test
    void factoryPicksDetectorByChannel() {
        float[] rest = {0.0F, 0.0F, -1.0F, 0.0F};
        assertTrue(StickDetector.forChannel(StickChannel.ROLL, rest, null) instanceof ChannelDetector);
        assertTrue(StickDetector.forChannel(StickChannel.YAW, rest, null) instanceof ChannelDetector);
        assertTrue(StickDetector.forChannel(StickChannel.THROTTLE, rest, null) instanceof ThrottleDetector);
    }

    @Test
    void detectionKeepsCalibrationOnlyForSameAxis() {
        ChannelMapping old = new ChannelMapping(1, false);
        old.min = -0.9F;
        old.max = 0.9F;
        ChannelMapping same = new StickDetector.Detection(1, true).toMapping(old);
        assertEquals(-0.9F, same.min);
        assertTrue(same.invert);
        ChannelMapping other = new StickDetector.Detection(4, false).toMapping(old);
        assertEquals(4, other.axis);
        assertEquals(-1.0F, other.min);
    }

    @Test
    void deviceChooserPrefersSelectionThenRadios() {
        List<DeviceChooser.Candidate> devices = List.of(
                new DeviceChooser.Candidate("pad", "Xbox Wireless Controller", true),
                new DeviceChooser.Candidate("radio", "Radiomaster Boxer Joystick", false),
                new DeviceChooser.Candidate("stick", "Generic USB Joystick", false));
        assertEquals(1, DeviceChooser.pick(devices, ""));
        assertEquals(0, DeviceChooser.pick(devices, "pad"));
        assertEquals(1, DeviceChooser.pick(devices, "missing"));
        assertEquals(1, DeviceChooser.pick(List.of(devices.get(2), devices.get(0)), ""));
        assertEquals(-1, DeviceChooser.pick(List.of(), ""));
    }

    @Test
    void profileDefaults() {
        DeviceProfile radio = ProfileDefaults.create("EdgeTX Radiomaster Boxer Joystick", true, 8);
        assertFalse(radio.gamepadLayout);
        assertEquals(0, radio.roll.axis);
        assertEquals(1, radio.pitch.axis);
        assertEquals(2, radio.throttle.axis);
        assertEquals(3, radio.yaw.axis);
        assertEquals(ThrottleMode.FULL_RANGE, radio.throttleMode);
        assertEquals(SwitchSource.AXIS, radio.binding(SwitchFunction.ARM).source);
        assertEquals(4, radio.binding(SwitchFunction.ARM).index);

        DeviceProfile small = ProfileDefaults.radio("Tiny", 4);
        assertFalse(small.binding(SwitchFunction.ARM).bound());

        DeviceProfile pad = ProfileDefaults.create("Xbox Wireless Controller", true, 6);
        assertTrue(pad.gamepadLayout);
        assertEquals(ProfileDefaults.PAD_LEFT_Y, pad.throttle.axis);
        assertTrue(pad.throttle.invert);
        assertTrue(pad.pitch.invert);
        assertEquals(ThrottleMode.UPPER_HALF, pad.throttleMode);
        assertTrue(pad.binding(SwitchFunction.ARM).toggle);

        float[] axes = {0.0F, -1.0F, 0.0F, -1.0F, -1.0F, -1.0F};
        StickPositions s = new StickMapper().map(pad, axes, 0.016F);
        assertEquals(1.0F, s.throttle(), 1.0E-4F);
        assertEquals(1.0F, s.pitch(), 1.0E-4F);
    }
}
