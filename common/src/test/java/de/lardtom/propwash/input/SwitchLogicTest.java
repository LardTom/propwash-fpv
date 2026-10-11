package de.lardtom.propwash.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchLogicTest {
    private static final long MS = 1_000_000L;
    private static final boolean[] NO_BUTTONS = new boolean[0];

    @Test
    void axisRangeIsInclusiveAndInvertible() {
        SwitchAssignment b = SwitchAssignment.axisRange(4, 0.5F, 1.0F);
        assertTrue(b.rawActive(new float[]{0, 0, 0, 0, 1.0F}, NO_BUTTONS));
        assertTrue(b.rawActive(new float[]{0, 0, 0, 0, 0.5F}, NO_BUTTONS));
        assertFalse(b.rawActive(new float[]{0, 0, 0, 0, 0.49F}, NO_BUTTONS));
        assertFalse(b.rawActive(new float[]{0, 0, 0, 0}, NO_BUTTONS));
        b.invert = true;
        assertTrue(b.rawActive(new float[]{0, 0, 0, 0, -1.0F}, NO_BUTTONS));
        assertTrue(b.rawActive(new float[]{0, 0, 0, 0, -0.5F}, NO_BUTTONS));
        assertFalse(b.rawActive(new float[]{0, 0, 0, 0, 0.0F}, NO_BUTTONS));
        assertFalse(b.rawActive(new float[]{0, 0, 0, 0, 1.0F}, NO_BUTTONS));
        assertFalse(b.rawActive(new float[]{0, 0, 0, 0}, NO_BUTTONS));
    }

    @Test
    void buttonBindingAndUnbound() {
        SwitchAssignment b = SwitchAssignment.button(1, false);
        assertTrue(b.rawActive(new float[0], new boolean[]{false, true}));
        assertFalse(b.rawActive(new float[0], new boolean[]{true, false}));
        assertFalse(b.rawActive(new float[0], new boolean[]{true}));
        assertFalse(SwitchAssignment.none().rawActive(new float[]{1.0F}, new boolean[]{true}));
    }

    @Test
    void rangeAroundSnapsToEdges() {
        SwitchAssignment high = SwitchAssignment.axisAround(3, 0.98F);
        assertEquals(2100, high.highMicros());
        assertEquals(1900, high.lowMicros());
        assertTrue(high.rawActive(new float[]{0, 0, 0, 1.0F}, NO_BUTTONS));
        SwitchAssignment low = SwitchAssignment.axisAround(3, -1.0F);
        assertEquals(900, low.lowMicros());
        SwitchAssignment mid = SwitchAssignment.axisAround(3, 0.0F);
        assertEquals(-0.2F, mid.low, 1.0E-4F);
        assertEquals(0.2F, mid.high, 1.0E-4F);
        assertTrue(mid.rawActive(new float[]{0, 0, 0, 0.05F}, NO_BUTTONS));
        assertFalse(mid.rawActive(new float[]{0, 0, 0, 1.0F}, NO_BUTTONS));
    }

    @Test
    void toggleLatchesOnRisingEdgeOnly() {
        DeviceProfile p = new DeviceProfile();
        p.setBinding(SwitchFunction.ARM, SwitchAssignment.toggleButton(0));
        p.setBinding(SwitchFunction.BEEPER, SwitchAssignment.button(1, false));
        SwitchEvaluator e = new SwitchEvaluator();
        float[] axes = new float[0];
        assertEquals(0, e.evaluate(p, axes, new boolean[]{true, false}));
        assertEquals(0, e.evaluate(p, axes, new boolean[]{false, false}));
        int bits = e.evaluate(p, axes, new boolean[]{true, true});
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.ARM));
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.BEEPER));
        bits = e.evaluate(p, axes, new boolean[]{false, false});
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.ARM));
        assertFalse(SwitchEvaluator.isSet(bits, SwitchFunction.BEEPER));
        e.evaluate(p, axes, new boolean[]{true, false});
        bits = e.evaluate(p, axes, new boolean[]{false, false});
        assertFalse(SwitchEvaluator.isSet(bits, SwitchFunction.ARM));
        e.evaluate(p, axes, new boolean[]{true, false});
        e.reset();
        assertEquals(0, e.evaluate(p, axes, new boolean[]{true, false}));
    }

    @Test
    void switchDetectorFindsButtonFlip() {
        SwitchDetector d = new SwitchDetector(new float[]{0.0F}, new boolean[]{false, true, false}, null);
        assertNull(d.update(new float[]{0.0F}, new boolean[]{false, true, false}, 0L));
        assertNull(d.update(new float[]{0.0F}, new boolean[]{false, false, false}, 10 * MS));
        SwitchAssignment b = d.update(new float[]{0.0F}, new boolean[]{false, false, false}, 300 * MS);
        assertNotNull(b);
        assertEquals(SwitchSource.BUTTON, b.source);
        assertEquals(1, b.index);
        assertTrue(b.invert);
        assertTrue(b.rawActive(new float[0], new boolean[]{false, false, false}));
    }

    @Test
    void switchDetectorPrefersNewlyPressedButton() {
        SwitchDetector d = new SwitchDetector(new float[0], new boolean[]{true, false, false}, null);
        boolean[] middle = {false, true, false};
        assertNull(d.update(new float[0], middle, 0L));
        SwitchAssignment b = d.update(new float[0], middle, 250 * MS);
        assertNotNull(b);
        assertEquals(1, b.index);
        assertFalse(b.invert);
    }

    @Test
    void switchDetectorFindsAxisPositionAndSkipsSticks() {
        float[] rest = {0.0F, 0.0F, -1.0F, 0.0F, -1.0F, 0.0F};
        boolean[] sticks = {true, true, true, true, false, false};
        SwitchDetector d = new SwitchDetector(rest, new boolean[0], sticks);
        assertNull(d.update(new float[]{1.0F, 1.0F, 1.0F, 1.0F, -1.0F, 0.0F}, new boolean[0], 0L));
        assertNull(d.update(new float[]{1.0F, 1.0F, 1.0F, 1.0F, -1.0F, 0.0F}, new boolean[0], 500 * MS));
        float[] flipped = {0.0F, 0.0F, -1.0F, 0.0F, 0.01F, 0.0F};
        assertNull(d.update(flipped, new boolean[0], 600 * MS));
        SwitchAssignment b = d.update(flipped, new boolean[0], 850 * MS);
        assertNotNull(b);
        assertEquals(SwitchSource.AXIS, b.source);
        assertEquals(4, b.index);
        assertTrue(b.rawActive(flipped, new boolean[0]));
        assertFalse(b.rawActive(rest, new boolean[0]));
    }

    @Test
    void switchDetectorNeedsStablePosition() {
        SwitchDetector d = new SwitchDetector(new float[]{-1.0F}, new boolean[0], null);
        assertNull(d.update(new float[]{0.0F}, new boolean[0], 0L));
        assertNull(d.update(new float[]{0.5F}, new boolean[0], 150 * MS));
        assertNull(d.update(new float[]{1.0F}, new boolean[0], 300 * MS));
        assertNotNull(d.update(new float[]{1.0F}, new boolean[0], 520 * MS));
    }

    @Test
    void configRoundTripAndSanitize() {
        InputConfig config = new InputConfig();
        config.selectedGuid = "abc";
        DeviceProfile radio = ProfileDefaults.radio("Radiomaster Boxer", 8);
        radio.setBinding(SwitchFunction.FLIP_OVER, SwitchAssignment.button(3, true));
        config.profiles.put("abc", radio);
        config.profiles.put("pad", ProfileDefaults.gamepad("Pad"));
        config.sanitize();
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
        String json = gson.toJson(config);
        InputConfig back = gson.fromJson(json, InputConfig.class);
        back.sanitize();
        assertEquals(json, gson.toJson(back));
        assertEquals(SwitchSource.BUTTON, back.profile("abc").binding(SwitchFunction.FLIP_OVER).source);
        assertTrue(back.profile("pad").binding(SwitchFunction.ARM).toggle);

        InputConfig broken = gson.fromJson("{\"selectedGuid\":null,\"profiles\":{\"x\":{\"roll\":null,\"deadband\":7,"
                + "\"throttleMode\":\"WARP\",\"switches\":{\"arm\":{\"source\":\"LASER\",\"index\":3},\"bogus\":{\"source\":\"AXIS\",\"index\":1}}}}}", InputConfig.class);
        broken.sanitize();
        assertEquals("", broken.selectedGuid);
        DeviceProfile x = broken.profile("x");
        assertNotNull(x);
        assertFalse(x.roll.assigned());
        assertEquals(DeviceProfile.MAX_DEADBAND, x.deadband, 1.0E-4F);
        assertEquals(ThrottleMode.FULL_RANGE, x.throttleMode);
        assertFalse(x.binding(SwitchFunction.ARM).bound());
        assertEquals(0, x.switches.size());
    }

    @Test
    void mergeKeepsPortionsSeparate() {
        InputConfig base = new InputConfig();
        base.profiles.put("g", ProfileDefaults.radio("R", 8));
        InputConfig receiverEdit = base.copy();
        receiverEdit.profile("g").deadband = 0.2F;
        receiverEdit.profile("g").setBinding(SwitchFunction.ARM, SwitchAssignment.none());
        InputConfig modesEdit = base.copy();
        modesEdit.profile("g").setBinding(SwitchFunction.BEEPER, SwitchAssignment.button(0, false));
        modesEdit.profile("g").deadband = 0.0F;
        InputConfig target = base.copy();
        target.mergeReceiverFrom(receiverEdit);
        target.mergeSwitchesFrom(modesEdit);
        assertEquals(0.2F, target.profile("g").deadband, 1.0E-4F);
        assertTrue(target.profile("g").binding(SwitchFunction.ARM).bound());
        assertTrue(target.profile("g").binding(SwitchFunction.BEEPER).bound());
    }

    @Test
    void radioFrameNoneIsIdle() {
        RadioFrame frame = new RadioFrame(RadioSource.NONE, "X", "Y", new StickPositions(1, 1, 1, 1), 5, 7, new float[]{1.0F}, new boolean[]{true});
        assertFalse(frame.connected());
        assertEquals(StickPositions.IDLE, frame.sticks());
        assertEquals(0, frame.activeSwitches());
        assertEquals(0, frame.readableSwitches());
        assertEquals(0, frame.axes().length);
        assertEquals("", frame.deviceName());
    }
}
