package de.lardtom.propwash.input;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualAssignmentTest {
    private static final int AXES = 8;
    private static final int BUTTONS = 24;

    private static float us(int micros) {
        return ChannelMath.fromRangeMicros(micros);
    }

    @Test
    void auxChannelsAreTheAxesWithoutStick() {
        DeviceProfile boxer = ProfileDefaults.radio("OpenTX Radiomaster Boxer Joystick", AXES);
        assertArrayEquals(new int[]{4, 5, 6, 7}, boxer.auxAxes(AXES));
        assertEquals(1, boxer.auxNumber(4, AXES));
        assertEquals(4, boxer.auxNumber(7, AXES));
        assertEquals(0, boxer.auxNumber(2, AXES));
        assertEquals(StickChannel.THROTTLE, boxer.stickOnAxis(2));

        List<SwitchSources.Choice> choices = SwitchSources.choices(boxer, AXES, BUTTONS, SwitchAssignment.none());
        assertEquals(1 + 4 + BUTTONS, choices.size());
        assertEquals(SwitchSources.Choice.NONE, choices.getFirst());
        assertEquals(new SwitchSources.Choice(SwitchSource.AXIS, 4), choices.get(1));
        assertEquals(new SwitchSources.Choice(SwitchSource.AXIS, 7), choices.get(4));
        assertEquals(new SwitchSources.Choice(SwitchSource.BUTTON, 0), choices.get(5));
        assertEquals(new SwitchSources.Choice(SwitchSource.BUTTON, 23), choices.getLast());
    }

    @Test
    void currentBindingOnStickAxisStaysSelectable() {
        DeviceProfile boxer = ProfileDefaults.radio("Boxer", AXES);
        SwitchAssignment onStick = SwitchAssignment.axisMicros(2, 1700, 2100);
        List<SwitchSources.Choice> choices = SwitchSources.choices(boxer, AXES, BUTTONS, onStick);
        assertTrue(choices.contains(new SwitchSources.Choice(SwitchSource.AXIS, 2)));
        assertEquals(1 + 5 + BUTTONS, choices.size());
        SwitchAssignment missingButton = SwitchAssignment.button(40, false);
        assertTrue(SwitchSources.choices(boxer, AXES, BUTTONS, missingButton).contains(new SwitchSources.Choice(SwitchSource.BUTTON, 40)));
    }

    @Test
    void rangesSnapToTwentyFiveMicrosAndClampTo900To2100() {
        SwitchAssignment b = SwitchAssignment.axisMicros(5, 1712, 1988);
        assertEquals(1700, b.lowMicros());
        assertEquals(2000, b.highMicros());
        b.setLowMicros(850);
        assertEquals(900, b.lowMicros());
        b.setHighMicros(2300);
        assertEquals(2100, b.highMicros());
        b.setLowMicros(2050);
        b.setHighMicros(1000);
        assertEquals(2050, b.lowMicros());
        assertEquals(2050, b.highMicros());
        b.setLowMicros(2100);
        assertEquals(2050, b.lowMicros());
        assertEquals(ChannelMath.RANGE_MIN_US, ChannelMath.rangeMicros(-5.0F));
        assertEquals(ChannelMath.RANGE_MAX_US, ChannelMath.rangeMicros(5.0F));
        assertEquals(1500, ChannelMath.rangeMicros(Float.NaN));
    }

    @Test
    void manualAuxRangeEvaluation() {
        DeviceProfile p = ProfileDefaults.radio("Boxer", AXES);
        SwitchAssignment horizon = SwitchSources.apply(SwitchAssignment.none(), new SwitchSources.Choice(SwitchSource.AXIS, 6),
                SwitchFunction.HORIZON, false);
        assertEquals(SwitchSources.DEFAULT_LOW_US, horizon.lowMicros());
        assertEquals(SwitchSources.DEFAULT_HIGH_US, horizon.highMicros());
        horizon.setLowMicros(1300);
        horizon.setHighMicros(1700);
        p.setBinding(SwitchFunction.HORIZON, horizon);
        float[] axes = new float[AXES];
        boolean[] buttons = new boolean[BUTTONS];
        axes[6] = us(1300);
        assertTrue(horizon.rawActive(axes, buttons));
        axes[6] = us(1700);
        assertTrue(horizon.rawActive(axes, buttons));
        axes[6] = us(1725);
        assertFalse(horizon.rawActive(axes, buttons));
        axes[6] = us(1000);
        assertFalse(horizon.rawActive(axes, buttons));
        horizon.invert = true;
        assertFalse(horizon.rawActive(axes, buttons));
        axes[6] = us(1500);
        assertTrue(horizon.rawActive(axes, buttons));
        horizon.setHighMicros(2100);
        assertEquals(900, horizon.activeLowMicros());
        assertEquals(1700, horizon.activeHighMicros());
        axes[6] = us(1000);
        assertTrue(horizon.rawActive(axes, buttons));
        axes[6] = us(2000);
        assertFalse(horizon.rawActive(axes, buttons));

        SwitchAssignment full = SwitchAssignment.axisMicros(4, 1700, 2100);
        axes[4] = 1.0F;
        assertTrue(full.rawActive(axes, buttons));
        SwitchAssignment bottom = SwitchAssignment.axisMicros(4, 900, 1300);
        axes[4] = -1.0F;
        assertTrue(bottom.rawActive(axes, buttons));
    }

    @Test
    void manualButtonAssignmentAndToggle() {
        DeviceProfile p = ProfileDefaults.radio("Boxer", AXES);
        SwitchAssignment rescue = SwitchSources.apply(p.binding(SwitchFunction.RESCUE), new SwitchSources.Choice(SwitchSource.BUTTON, 23),
                SwitchFunction.RESCUE, false);
        assertEquals(SwitchSource.BUTTON, rescue.source);
        assertEquals(23, rescue.index);
        assertFalse(rescue.toggle);
        rescue.toggle = true;
        p.setBinding(SwitchFunction.RESCUE, rescue);
        SwitchEvaluator e = new SwitchEvaluator();
        float[] axes = new float[AXES];
        boolean[] buttons = new boolean[BUTTONS];
        assertFalse(SwitchEvaluator.isSet(e.evaluate(p, axes, buttons), SwitchFunction.RESCUE));
        buttons[23] = true;
        assertTrue(SwitchEvaluator.isSet(e.evaluate(p, axes, buttons), SwitchFunction.RESCUE));
        buttons[23] = false;
        assertTrue(SwitchEvaluator.isSet(e.evaluate(p, axes, buttons), SwitchFunction.RESCUE));
        buttons[23] = true;
        assertFalse(SwitchEvaluator.isSet(e.evaluate(p, axes, buttons), SwitchFunction.RESCUE));

        SwitchAssignment flip = SwitchSources.apply(rescue, new SwitchSources.Choice(SwitchSource.BUTTON, 3), SwitchFunction.FLIP_OVER, false);
        assertFalse(flip.toggle);
        SwitchAssignment padArm = SwitchSources.apply(SwitchAssignment.none(), new SwitchSources.Choice(SwitchSource.BUTTON, 9), SwitchFunction.ARM, true);
        assertTrue(padArm.toggle);
        SwitchAssignment radioArm = SwitchSources.apply(SwitchAssignment.none(), new SwitchSources.Choice(SwitchSource.BUTTON, 9), SwitchFunction.ARM, false);
        assertFalse(radioArm.toggle);
        assertFalse(SwitchSources.apply(rescue, SwitchSources.Choice.NONE, SwitchFunction.RESCUE, false).bound());
    }

    @Test
    void changingSourceKeepsRangeWithinTheSameKind() {
        SwitchAssignment b = SwitchAssignment.axisMicros(4, 1300, 1700);
        b.invert = true;
        SwitchAssignment moved = SwitchSources.apply(b, new SwitchSources.Choice(SwitchSource.AXIS, 7), SwitchFunction.ANGLE, false);
        assertEquals(7, moved.index);
        assertEquals(1300, moved.lowMicros());
        assertEquals(1700, moved.highMicros());
        assertTrue(moved.invert);
        SwitchAssignment toButton = SwitchSources.apply(b, new SwitchSources.Choice(SwitchSource.BUTTON, 1), SwitchFunction.ANGLE, false);
        assertFalse(toButton.invert);
        assertEquals(4, b.index);
    }

    @Test
    void toggleOnAxisSourceSurvivesSanitize() {
        SwitchAssignment b = SwitchAssignment.axisMicros(6, 1700, 2100);
        b.toggle = true;
        b.sanitize();
        assertTrue(b.toggle);
        SwitchAssignment wide = SwitchAssignment.axisRange(6, -3.0F, 3.0F);
        wide.sanitize();
        assertEquals(900, wide.lowMicros());
        assertEquals(2100, wide.highMicros());
    }

    @Test
    void manualStickAssignmentSwapsChannels() {
        DeviceProfile p = ProfileDefaults.radio("Boxer", AXES);
        p.roll.min = -0.9F;
        p.roll.max = 0.95F;
        p.pitch.center = 0.03F;
        p.assignAxis(StickChannel.ROLL, 1);
        assertEquals(1, p.roll.axis);
        assertEquals(0, p.pitch.axis);
        assertEquals(0.03F, p.roll.center, 1.0E-6F);
        assertEquals(-0.9F, p.pitch.min, 1.0E-6F);
        p.assignAxis(StickChannel.THROTTLE, 6);
        assertEquals(6, p.throttle.axis);
        assertFalse(p.throttle.calibrated());
        assertArrayEquals(new int[]{2, 4, 5, 7}, p.auxAxes(AXES));
        p.assignAxis(StickChannel.YAW, -1);
        assertFalse(p.yaw.assigned());
        p.assignAxis(StickChannel.YAW, 6);
        assertEquals(6, p.yaw.axis);
        assertFalse(p.throttle.assigned());
        p.roll.invert = true;
        p.assignAxis(StickChannel.ROLL, 0);
        assertTrue(p.roll.invert);
        assertEquals(1, p.pitch.axis);
    }

    @Test
    void freeAssignmentSurvivesConfigRoundTrip() {
        InputConfig config = new InputConfig();
        config.profileVersion = InputConfig.PROFILE_VERSION;
        DeviceProfile p = ProfileDefaults.radio("OpenTX Radiomaster Boxer Joystick", AXES);
        SwitchAssignment arm = SwitchAssignment.axisMicros(5, 900, 1225);
        arm.invert = true;
        arm.toggle = true;
        p.setBinding(SwitchFunction.ARM, arm);
        p.setBinding(SwitchFunction.RESET, SwitchAssignment.button(23, false));
        p.assignAxis(StickChannel.YAW, 7);
        config.profiles.put("boxer", p);
        config.sanitize();
        Gson gson = new Gson();
        InputConfig back = gson.fromJson(gson.toJson(config), InputConfig.class);
        back.sanitize();
        DeviceProfile loaded = back.profile("boxer");
        assertNotNull(loaded);
        assertEquals(arm, loaded.binding(SwitchFunction.ARM));
        assertTrue(loaded.binding(SwitchFunction.ARM).invert);
        assertEquals(1225, loaded.binding(SwitchFunction.ARM).highMicros());
        assertEquals(23, loaded.binding(SwitchFunction.RESET).index);
        assertEquals(7, loaded.yaw.axis);
        assertArrayEquals(new int[]{3, 4, 5, 6}, loaded.auxAxes(AXES));
    }

    @Test
    void stickOnAuxAxisMovesItsSwitchesToTheFreedAxis() {
        DeviceProfile boxer = ProfileDefaults.radio("OpenTX Radiomaster Boxer Joystick", AXES);
        SwitchAssignment rescueBefore = boxer.binding(SwitchFunction.RESCUE).copy();
        assertEquals(7, rescueBefore.index);
        SwitchEvaluator evaluator = new SwitchEvaluator();
        float[] axes = new float[AXES];
        boolean[] buttons = new boolean[BUTTONS];

        assertEquals(List.of(SwitchFunction.RESCUE), boxer.assignAxis(StickChannel.YAW, 7));
        SwitchAssignment rescue = boxer.binding(SwitchFunction.RESCUE);
        assertEquals(SwitchSource.AXIS, rescue.source);
        assertEquals(3, rescue.index);
        assertEquals(rescueBefore.lowMicros(), rescue.lowMicros());
        assertEquals(rescueBefore.highMicros(), rescue.highMicros());
        for (SwitchFunction function : SwitchFunction.values()) {
            assertNull(boxer.stickConflict(boxer.binding(function)), function.name());
        }

        axes[7] = 0.9F;
        assertFalse(SwitchEvaluator.isSet(evaluator.evaluate(boxer, axes, buttons), SwitchFunction.RESCUE));
        axes[3] = 1.0F;
        assertTrue(SwitchEvaluator.isSet(evaluator.evaluate(boxer, axes, buttons), SwitchFunction.RESCUE));
    }

    @Test
    void switchIsClearedWhenNoAxisBecomesFree() {
        DeviceProfile p = ProfileDefaults.radio("Boxer", AXES);
        assertTrue(p.assignAxis(StickChannel.YAW, -1).isEmpty());
        assertEquals(List.of(SwitchFunction.RESCUE), p.assignAxis(StickChannel.YAW, 7));
        assertFalse(p.binding(SwitchFunction.RESCUE).bound());
        assertEquals(4, p.binding(SwitchFunction.ARM).index);
    }

    @Test
    void swappingSticksLeavesSwitchesAlone() {
        DeviceProfile p = ProfileDefaults.radio("Boxer", AXES);
        DeviceProfile before = p.copy();
        assertTrue(p.assignAxis(StickChannel.ROLL, 1).isEmpty());
        assertTrue(p.assignAxis(StickChannel.THROTTLE, 3).isEmpty());
        for (SwitchFunction function : SwitchFunction.values()) {
            assertEquals(before.binding(function), p.binding(function), function.name());
        }
    }

    @Test
    void resetsKeepSwitchesOffCustomSticks() {
        DeviceProfile p = ProfileDefaults.radio("Boxer", AXES);
        p.assignAxis(StickChannel.YAW, 7);
        DeviceProfile defaults = ProfileDefaults.reset(p, false, AXES);

        p.copySwitchesFrom(defaults);
        assertEquals(StickChannel.YAW, p.stickConflict(p.binding(SwitchFunction.RESCUE)));
        assertEquals(List.of(SwitchFunction.RESCUE), p.moveSwitchesOffSticks(defaults.stickAxes()));
        assertEquals(3, p.binding(SwitchFunction.RESCUE).index);

        int[] sticksBefore = p.stickAxes();
        p.copyReceiverFrom(defaults);
        assertEquals(List.of(SwitchFunction.RESCUE), p.moveSwitchesOffSticks(sticksBefore));
        assertEquals(3, p.yaw.axis);
        assertEquals(7, p.binding(SwitchFunction.RESCUE).index);
        for (SwitchFunction function : SwitchFunction.values()) {
            assertNull(p.stickConflict(p.binding(function)), function.name());
        }
    }

    @Test
    void sanitizeDropsToggleForMomentaryFunctions() {
        DeviceProfile p = new DeviceProfile();
        for (SwitchFunction function : SwitchFunction.values()) {
            SwitchAssignment b = SwitchAssignment.button(function.ordinal(), false);
            b.toggle = true;
            p.switches.put(DeviceProfile.key(function), b);
        }
        p.sanitize();
        for (SwitchFunction function : SwitchFunction.values()) {
            assertEquals(function.supportsToggle(), p.binding(function).toggle, function.name());
        }
    }
}
