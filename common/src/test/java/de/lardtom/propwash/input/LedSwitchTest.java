package de.lardtom.propwash.input;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedSwitchTest {
    private static final SwitchFunction LEDS = SwitchFunction.LEDS_OFF;
    private static final int AXES = 10;
    private static final int BUTTONS = 12;
    private static final int AUX = 8;

    private static float[] axes(int index, int micros) {
        float[] axes = new float[AXES];
        axes[index] = ChannelMath.fromRangeMicros(micros);
        return axes;
    }

    private static boolean off(SwitchEvaluator evaluator, DeviceProfile profile, float[] axes, boolean[] buttons) {
        return SwitchEvaluator.isSet(evaluator.evaluate(profile, axes, buttons), LEDS);
    }

    private static boolean[] pressed(int index, boolean down) {
        boolean[] buttons = new boolean[BUTTONS];
        buttons[index] = down;
        return buttons;
    }

    @Test
    void ledsOffIsALatchingSwitchFunctionWithItsOwnNames() {
        assertTrue(LEDS.supportsToggle());
        assertTrue(LEDS.latchesOnGamepad());
        assertEquals("propwash.input.switch.leds_off", LEDS.translationKey());
        assertEquals("leds_off", DeviceProfile.key(LEDS));
        assertTrue(LEDS.ordinal() > SwitchFunction.RECORD.ordinal());
        assertTrue(SwitchFunction.values().length <= Integer.SIZE);
    }

    @Test
    void withoutAnAssignmentTheLedsStayOn() {
        DeviceProfile profile = ProfileDefaults.radio("Boxer", AXES);
        assertFalse(profile.binding(LEDS).bound());
        SwitchEvaluator evaluator = new SwitchEvaluator();
        for (int micros : new int[]{900, 1000, 1500, 2000, 2100}) {
            for (int axis = 0; axis < AXES; axis++) {
                assertFalse(off(evaluator, profile, axes(axis, micros), pressed(3, true)), axis + " @ " + micros);
            }
        }
        DeviceProfile pad = ProfileDefaults.gamepad("Xbox Controller");
        assertFalse(pad.binding(LEDS).bound());
    }

    @Test
    void auxSwitchFromTheModesTabTurnsTheLedsOffInItsRange() {
        DeviceProfile profile = ProfileDefaults.radio("Boxer", AXES);
        SwitchAssignment binding = SwitchSources.apply(profile.binding(LEDS), new SwitchSources.Choice(SwitchSource.AXIS, AUX), LEDS, false);
        profile.setBinding(LEDS, binding);
        assertEquals(SwitchSources.DEFAULT_LOW_US, binding.activeLowMicros());
        assertEquals(SwitchSources.DEFAULT_HIGH_US, binding.activeHighMicros());
        assertFalse(binding.toggle);
        SwitchEvaluator evaluator = new SwitchEvaluator();
        boolean[] none = new boolean[BUTTONS];
        assertFalse(off(evaluator, profile, axes(AUX, 1000), none));
        assertFalse(off(evaluator, profile, axes(AUX, 1500), none));
        assertTrue(off(evaluator, profile, axes(AUX, 2000), none));
        assertFalse(off(evaluator, profile, axes(AUX, 1000), none));
    }

    @Test
    void invertSwapsTheSwitchPositionsAtOnce() {
        DeviceProfile profile = new DeviceProfile();
        SwitchAssignment binding = SwitchAssignment.axisMicros(AUX, 1700, 2100);
        profile.setBinding(LEDS, binding);
        SwitchEvaluator evaluator = new SwitchEvaluator();
        boolean[] none = new boolean[0];
        assertTrue(off(evaluator, profile, axes(AUX, 2000), none));
        binding.invert = true;
        assertFalse(off(evaluator, profile, axes(AUX, 2000), none));
        assertTrue(off(evaluator, profile, axes(AUX, 1000), none));
        assertEquals(900, binding.activeLowMicros());
        assertEquals(1300, binding.activeHighMicros());
        binding.invert = false;
        assertFalse(off(evaluator, profile, axes(AUX, 1000), none));
    }

    @Test
    void threePositionSwitchCanTurnTheLedsOffInTheMiddleOnly() {
        DeviceProfile profile = new DeviceProfile();
        SwitchAssignment binding = SwitchAssignment.axisMicros(AUX, 1300, 1700);
        profile.setBinding(LEDS, binding);
        SwitchEvaluator evaluator = new SwitchEvaluator();
        boolean[] none = new boolean[0];
        assertFalse(off(evaluator, profile, axes(AUX, 1000), none));
        assertTrue(off(evaluator, profile, axes(AUX, 1500), none));
        assertFalse(off(evaluator, profile, axes(AUX, 2000), none));
        binding.setActiveLowMicros(1000);
        binding.setActiveHighMicros(1350);
        assertTrue(off(evaluator, profile, axes(AUX, 1000), none));
        assertFalse(off(evaluator, profile, axes(AUX, 1500), none));
    }

    @Test
    void unreadableSourceNeverSwitchesTheLedsOff() {
        DeviceProfile profile = new DeviceProfile();
        SwitchAssignment binding = SwitchAssignment.axisMicros(AXES + 3, 1700, 2100);
        profile.setBinding(LEDS, binding);
        SwitchEvaluator evaluator = new SwitchEvaluator();
        assertFalse(off(evaluator, profile, axes(AUX, 2000), new boolean[0]));
        binding.invert = true;
        assertFalse(off(evaluator, profile, axes(AUX, 1000), new boolean[0]));
        profile.setBinding(LEDS, SwitchAssignment.button(BUTTONS + 2, true));
        assertFalse(off(evaluator, profile, axes(AUX, 1000), new boolean[BUTTONS]));
    }

    @Test
    void buttonHoldsOrTogglesTheLeds() {
        DeviceProfile profile = new DeviceProfile();
        profile.setBinding(LEDS, SwitchAssignment.button(3, false));
        SwitchEvaluator evaluator = new SwitchEvaluator();
        float[] axes = new float[AXES];
        assertFalse(off(evaluator, profile, axes, pressed(3, false)));
        assertTrue(off(evaluator, profile, axes, pressed(3, true)));
        assertFalse(off(evaluator, profile, axes, pressed(3, false)));

        profile.setBinding(LEDS, SwitchAssignment.toggleButton(3));
        evaluator.reset();
        assertFalse(off(evaluator, profile, axes, pressed(3, false)));
        assertTrue(off(evaluator, profile, axes, pressed(3, true)));
        assertTrue(off(evaluator, profile, axes, pressed(3, false)));
        assertFalse(off(evaluator, profile, axes, pressed(3, true)));
        assertFalse(off(evaluator, profile, axes, pressed(3, false)));

        profile.binding(LEDS).invert = true;
        evaluator.reset();
        assertTrue(off(evaluator, profile, axes, pressed(3, false)));
        assertFalse(off(evaluator, profile, axes, pressed(3, true)));
        assertFalse(off(evaluator, profile, axes, pressed(3, false)));
    }

    @Test
    void gamepadButtonsLatchAndStartWithTheLedsOn() {
        DeviceProfile pad = ProfileDefaults.gamepad("Xbox Controller");
        SwitchAssignment binding = SwitchSources.apply(pad.binding(LEDS), new SwitchSources.Choice(SwitchSource.BUTTON, 6), LEDS, true);
        assertTrue(binding.toggle);
        assertFalse(binding.startOn);
        pad.setBinding(LEDS, binding);
        SwitchEvaluator evaluator = new SwitchEvaluator();
        float[] axes = new float[ProfileDefaults.PAD_AXES];
        boolean[] released = new boolean[ProfileDefaults.PAD_BUTTONS];
        boolean[] down = new boolean[ProfileDefaults.PAD_BUTTONS];
        down[6] = true;
        assertFalse(off(evaluator, pad, axes, released));
        assertTrue(off(evaluator, pad, axes, down));
        assertTrue(off(evaluator, pad, axes, released));
    }

    @Test
    void assignmentSurvivesSavingAndSanitizing() {
        DeviceProfile profile = ProfileDefaults.radio("Boxer", AXES);
        SwitchAssignment binding = SwitchAssignment.axisMicros(AUX, 1000, 1350);
        binding.invert = true;
        profile.setBinding(LEDS, binding);
        Gson gson = new Gson();
        DeviceProfile loaded = gson.fromJson(gson.toJson(profile), DeviceProfile.class);
        loaded.sanitize();
        assertEquals(binding, loaded.binding(LEDS));

        SwitchAssignment toggle = SwitchAssignment.toggleButton(4);
        profile.setBinding(LEDS, toggle);
        profile.sanitize();
        assertTrue(profile.binding(LEDS).toggle);
    }

    @Test
    void ledsOffDoesNotDisturbTheOtherFunctions() {
        DeviceProfile profile = ProfileDefaults.radio("Boxer", AXES);
        profile.setBinding(LEDS, SwitchAssignment.axisMicros(AUX, 1700, 2100));
        SwitchEvaluator evaluator = new SwitchEvaluator();
        float[] axes = axes(AUX, 2000);
        for (int i = 4; i < AUX; i++) {
            axes[i] = -1.0F;
        }
        int bits = evaluator.evaluate(profile, axes, new boolean[0]);
        assertEquals(SwitchEvaluator.bit(LEDS), bits);
    }
}
