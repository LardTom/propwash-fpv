package de.lardtom.propwash.input;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchInversionTest {
    private static final boolean[] NO_BUTTONS = new boolean[0];
    private static final int AXES = 8;

    private static float us(int micros) {
        return ChannelMath.fromRangeMicros(micros);
    }

    private static float[] axis(int index, int micros) {
        float[] axes = new float[AXES];
        axes[index] = us(micros);
        return axes;
    }

    private static boolean on(int bits, SwitchFunction function) {
        return SwitchEvaluator.isSet(bits, function);
    }

    @Test
    void twoPositionSwitchSwapsOnAndOffAtOnce() {
        DeviceProfile p = new DeviceProfile();
        SwitchAssignment arm = SwitchAssignment.axisMicros(4, 1700, 2100);
        p.setBinding(SwitchFunction.ARM, arm);
        SwitchEvaluator e = new SwitchEvaluator();
        assertFalse(on(e.evaluate(p, axis(4, 1000), NO_BUTTONS), SwitchFunction.ARM));
        assertTrue(on(e.evaluate(p, axis(4, 2000), NO_BUTTONS), SwitchFunction.ARM));
        arm.invert = true;
        assertFalse(on(e.evaluate(p, axis(4, 2000), NO_BUTTONS), SwitchFunction.ARM));
        assertTrue(on(e.evaluate(p, axis(4, 1000), NO_BUTTONS), SwitchFunction.ARM));
        assertEquals(900, arm.activeLowMicros());
        assertEquals(1300, arm.activeHighMicros());
        assertEquals(1700, arm.lowMicros());
        assertEquals(2100, arm.highMicros());
        arm.invert = false;
        assertFalse(on(e.evaluate(p, axis(4, 1000), NO_BUTTONS), SwitchFunction.ARM));
    }

    @Test
    void threePositionSwitchReversesItsOrder() {
        DeviceProfile p = new DeviceProfile();
        SwitchAssignment low = SwitchAssignment.axisMicros(5, 900, 1250);
        SwitchAssignment mid = SwitchAssignment.axisMicros(5, 1300, 1700);
        SwitchAssignment high = SwitchAssignment.axisMicros(5, 1750, 2100);
        p.setBinding(SwitchFunction.ANGLE, low);
        p.setBinding(SwitchFunction.HORIZON, mid);
        p.setBinding(SwitchFunction.BEEPER, high);
        SwitchEvaluator e = new SwitchEvaluator();
        SwitchFunction[] normal = {SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.BEEPER};
        int[] positions = {1000, 1500, 2000};
        for (int i = 0; i < positions.length; i++) {
            int bits = e.evaluate(p, axis(5, positions[i]), NO_BUTTONS);
            for (int j = 0; j < normal.length; j++) {
                assertEquals(i == j, on(bits, normal[j]), positions[i] + " µs " + normal[j]);
            }
        }
        low.invert = true;
        mid.invert = true;
        high.invert = true;
        for (int i = 0; i < positions.length; i++) {
            int bits = e.evaluate(p, axis(5, positions[i]), NO_BUTTONS);
            for (int j = 0; j < normal.length; j++) {
                assertEquals(positions.length - 1 - i == j, on(bits, normal[j]), "invertiert " + positions[i] + " µs " + normal[j]);
            }
        }
    }

    @Test
    void rangeIsMirroredAroundTheCenterAndEditedInChannelSpace() {
        SwitchAssignment b = SwitchAssignment.axisMicros(6, 1000, 1350);
        b.invert = true;
        assertTrue(b.mirrored());
        assertFalse(b.flipsOutput());
        assertEquals(1650, b.activeLowMicros());
        assertEquals(2000, b.activeHighMicros());
        assertTrue(b.rawActive(axis(6, 1800), NO_BUTTONS));
        assertFalse(b.rawActive(axis(6, 1200), NO_BUTTONS));
        b.setActiveLowMicros(1600);
        assertEquals(1600, b.activeLowMicros());
        assertEquals(2000, b.activeHighMicros());
        b.setActiveHighMicros(2100);
        assertEquals(2100, b.activeHighMicros());
        assertEquals(900, b.lowMicros());
        b.setActiveLowMicros(2150);
        assertEquals(2100, b.activeLowMicros());
        assertTrue(b.activeLow() <= b.activeHigh());
        b.setActiveLow(-0.8F);
        b.setActiveHigh(-1.0F);
        assertTrue(b.activeLow() <= b.activeHigh());
        assertEquals(-0.8F, b.activeLow(), 1.0E-6F);
        assertEquals(-0.8F, b.activeHigh(), 1.0E-6F);
        b.invert = false;
        assertEquals(0.8F, b.activeLow(), 1.0E-6F);
        assertEquals(1700, SwitchAssignment.mirrorMicros(1300));
    }

    @Test
    void buttonSwapsOnAndOffAndMissingButtonStaysOff() {
        SwitchAssignment b = SwitchAssignment.button(1, false);
        assertTrue(b.rawActive(new float[0], new boolean[]{false, true}));
        b.invert = true;
        assertFalse(b.mirrored());
        assertTrue(b.flipsOutput());
        assertFalse(b.rawActive(new float[0], new boolean[]{false, true}));
        assertTrue(b.rawActive(new float[0], new boolean[]{true, false}));
        assertFalse(b.rawActive(new float[0], new boolean[]{false}));
        SwitchAssignment axisGone = SwitchAssignment.axisMicros(7, 900, 1300);
        axisGone.invert = true;
        assertFalse(axisGone.rawActive(new float[]{0.0F}, NO_BUTTONS));
    }

    @Test
    void toggleSwapsTheLatchAndStillTogglesOnPress() {
        DeviceProfile p = new DeviceProfile();
        SwitchAssignment angle = SwitchAssignment.toggleButton(0);
        p.setBinding(SwitchFunction.ANGLE, angle);
        SwitchEvaluator e = new SwitchEvaluator();
        boolean[] released = {false};
        boolean[] pressed = {true};
        assertFalse(on(e.evaluate(p, new float[0], released), SwitchFunction.ANGLE));
        assertTrue(on(e.evaluate(p, new float[0], pressed), SwitchFunction.ANGLE));
        assertTrue(on(e.evaluate(p, new float[0], released), SwitchFunction.ANGLE));
        angle.invert = true;
        assertFalse(on(e.evaluate(p, new float[0], released), SwitchFunction.ANGLE));
        assertTrue(on(e.evaluate(p, new float[0], pressed), SwitchFunction.ANGLE));
        assertTrue(on(e.evaluate(p, new float[0], released), SwitchFunction.ANGLE));
        assertFalse(on(e.evaluate(p, new float[0], pressed), SwitchFunction.ANGLE));

        SwitchAssignment latchedAxis = SwitchAssignment.axisMicros(4, 1700, 2100);
        latchedAxis.toggle = true;
        latchedAxis.invert = true;
        assertFalse(latchedAxis.mirrored());
        assertTrue(latchedAxis.flipsOutput());
        assertEquals(1700, latchedAxis.activeLowMicros());
        assertEquals(2100, latchedAxis.activeHighMicros());
        p.setBinding(SwitchFunction.RESCUE, latchedAxis);
        e.reset();
        assertTrue(on(e.evaluate(p, axis(4, 1000), new boolean[1]), SwitchFunction.RESCUE));
        assertFalse(on(e.evaluate(p, axis(4, 2000), new boolean[1]), SwitchFunction.RESCUE));
        assertFalse(on(e.evaluate(p, axis(4, 1000), new boolean[1]), SwitchFunction.RESCUE));
    }

    @Test
    void inversionIsStoredWithTheAssignmentAndOldConfigsLoadNotInverted() {
        Gson gson = new Gson();
        SwitchAssignment legacy = gson.fromJson("{\"source\":\"AXIS\",\"index\":4,\"low\":0.4,\"high\":1.2}", SwitchAssignment.class);
        assertFalse(legacy.invert);
        SwitchAssignment b = SwitchAssignment.axisMicros(4, 1700, 2100);
        SwitchAssignment inverted = b.copy();
        inverted.invert = true;
        assertNotEquals(b, inverted);
        assertEquals(inverted, inverted.copy());

        InputConfig config = new InputConfig();
        config.profileVersion = InputConfig.PROFILE_VERSION;
        DeviceProfile p = ProfileDefaults.radio("Boxer", AXES);
        p.binding(SwitchFunction.ARM).invert = true;
        p.setBinding(SwitchFunction.FLIP_OVER, SwitchAssignment.button(3, true));
        config.profiles.put("boxer", p);
        config.sanitize();
        InputConfig back = gson.fromJson(gson.toJson(config), InputConfig.class);
        back.sanitize();
        assertTrue(back.profile("boxer").binding(SwitchFunction.ARM).invert);
        assertTrue(back.profile("boxer").binding(SwitchFunction.FLIP_OVER).invert);
        assertFalse(back.profile("boxer").binding(SwitchFunction.ANGLE).invert);
        assertEquals(p.binding(SwitchFunction.ARM), back.profile("boxer").binding(SwitchFunction.ARM));
    }

    @Test
    void legacyInversionIsMigratedOnce() {
        DeviceProfile p = ProfileDefaults.radio("Boxer", AXES);
        SwitchAssignment arm = SwitchAssignment.axisMicros(4, 1700, 2100);
        arm.invert = true;
        SwitchAssignment angle = SwitchAssignment.axisMicros(5, 900, 1300);
        angle.invert = true;
        SwitchAssignment horizon = SwitchAssignment.axisMicros(5, 1300, 1700);
        horizon.invert = true;
        SwitchAssignment rescue = SwitchAssignment.toggleButton(2);
        rescue.invert = true;
        p.setBinding(SwitchFunction.ARM, arm);
        p.setBinding(SwitchFunction.ANGLE, angle);
        p.setBinding(SwitchFunction.HORIZON, horizon);
        p.setBinding(SwitchFunction.RESCUE, rescue);
        p.setBinding(SwitchFunction.BEEPER, SwitchAssignment.button(3, true));
        InputConfig old = new InputConfig();
        old.profiles.put("boxer", p);
        Gson gson = new Gson();
        String json = gson.toJson(old).replace("\"profileVersion\":0", "\"profileVersion\":1");

        InputConfig loaded = gson.fromJson(json, InputConfig.class);
        loaded.sanitize();
        assertEquals(InputConfig.PROFILE_VERSION, loaded.profileVersion);
        DeviceProfile q = loaded.profile("boxer");
        SwitchAssignment a = q.binding(SwitchFunction.ARM);
        assertTrue(a.invert);
        assertEquals(900, a.activeLowMicros());
        assertEquals(1675, a.activeHighMicros());
        assertTrue(a.rawActive(axis(4, 1000), NO_BUTTONS));
        assertTrue(a.rawActive(axis(4, 1500), NO_BUTTONS));
        assertFalse(a.rawActive(axis(4, 2000), NO_BUTTONS));
        SwitchAssignment an = q.binding(SwitchFunction.ANGLE);
        assertTrue(an.invert);
        assertEquals(1325, an.activeLowMicros());
        assertEquals(2100, an.activeHighMicros());
        assertFalse(q.binding(SwitchFunction.HORIZON).invert);
        assertEquals(1300, q.binding(SwitchFunction.HORIZON).lowMicros());
        assertTrue(q.binding(SwitchFunction.RESCUE).toggle);
        assertFalse(q.binding(SwitchFunction.RESCUE).invert);
        assertTrue(q.binding(SwitchFunction.BEEPER).invert);

        InputConfig again = gson.fromJson(gson.toJson(loaded), InputConfig.class);
        again.sanitize();
        assertEquals(a, again.profile("boxer").binding(SwitchFunction.ARM));
        assertEquals(an, again.profile("boxer").binding(SwitchFunction.ANGLE));
    }

    @Test
    void armSourceWatchCountsOnlyRealChanges() {
        ArmSourceWatch watch = new ArmSourceWatch();
        SwitchAssignment arm = SwitchAssignment.axisMicros(4, 1700, 2100);
        assertFalse(watch.observe(RadioSource.NONE, "", arm));
        int start = watch.revision();
        assertTrue(watch.observe(RadioSource.JOYSTICK, "boxer", arm));
        assertFalse(watch.observe(RadioSource.JOYSTICK, "boxer", arm.copy()));
        SwitchAssignment inverted = arm.copy();
        inverted.invert = true;
        assertTrue(watch.observe(RadioSource.JOYSTICK, "boxer", inverted));
        inverted.invert = false;
        assertTrue(watch.observe(RadioSource.JOYSTICK, "boxer", inverted));
        assertFalse(watch.observe(RadioSource.JOYSTICK, "boxer", arm));
        assertTrue(watch.observe(RadioSource.GAMEPAD, "pad", arm));
        assertTrue(watch.observe(RadioSource.KEYBOARD, "", SwitchAssignment.none()));
        assertFalse(watch.observe(RadioSource.KEYBOARD, "", null));
        assertTrue(watch.observe(RadioSource.NONE, "", arm));
        assertEquals(start + 6, watch.revision());
    }
}
