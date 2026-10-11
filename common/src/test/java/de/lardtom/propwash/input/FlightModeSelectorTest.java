package de.lardtom.propwash.input;

import de.lardtom.propwash.sim.StabMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightModeSelectorTest {
    private static final int MODE_CHANNEL = ProfileDefaults.AUX_MODE;

    @Test
    void allModeSwitchesOffFliesAcroEvenAfterAKeyChoice() {
        Pilot pilot = Pilot.radio(8);
        pilot.micros(MODE_CHANNEL, 1000);
        assertEquals(StabMode.ACRO, pilot.startSession());
        pilot.tap(pilot.cycle);
        pilot.tap(pilot.cycle);
        assertEquals(StabMode.HORIZON, pilot.tick());
        pilot.micros(MODE_CHANNEL, 2000);
        assertEquals(StabMode.ANGLE, pilot.tick());
        pilot.micros(MODE_CHANNEL, 1000);
        assertEquals(StabMode.ACRO, pilot.tick());
        assertEquals(FlightModeSelector.Source.SWITCH, pilot.modes.source());
        for (int i = 0; i < 40; i++) {
            assertEquals(StabMode.ACRO, pilot.tick());
        }
        assertEquals(StabMode.ACRO, pilot.startSession());
    }

    @Test
    void acroKeyWinsOverAModeChannelParkedInTheMiddle() {
        Pilot pilot = Pilot.radio(8);
        pilot.micros(MODE_CHANNEL, 1500);
        assertEquals(StabMode.HORIZON, pilot.startSession());
        pilot.tap(pilot.acro);
        assertEquals(StabMode.ACRO, pilot.tick());
        assertTrue(pilot.overrides());
        for (int i = 0; i < 100; i++) {
            assertEquals(StabMode.ACRO, pilot.tick());
        }
        assertEquals(StabMode.ACRO, pilot.startSession());
        pilot.micros(MODE_CHANNEL, 1000);
        assertEquals(StabMode.ACRO, pilot.tick());
        assertEquals(FlightModeSelector.Source.SWITCH, pilot.modes.source());
        assertFalse(pilot.overrides());
        pilot.micros(MODE_CHANNEL, 2000);
        assertEquals(StabMode.ANGLE, pilot.tick());
        pilot.micros(MODE_CHANNEL, 1500);
        assertEquals(StabMode.HORIZON, pilot.tick());
    }

    @Test
    void cycleKeyStepsOnFromTheModeActuallyFlown() {
        Pilot pilot = Pilot.radio(8);
        pilot.micros(MODE_CHANNEL, 2000);
        assertEquals(StabMode.ANGLE, pilot.startSession());
        pilot.tap(pilot.cycle);
        assertEquals(StabMode.HORIZON, pilot.tick());
        pilot.tap(pilot.cycle);
        assertEquals(StabMode.ACRO, pilot.tick());
        pilot.tap(pilot.cycle);
        assertEquals(StabMode.ANGLE, pilot.tick());
        assertFalse(pilot.overrides());
        pilot.tap(pilot.angle);
        assertEquals(StabMode.ANGLE, pilot.tick());
    }

    @Test
    void holdingTheCycleKeyStepsExactlyOnce() {
        Pilot pilot = Pilot.keyboard(false);
        assertEquals(StabMode.ACRO, pilot.startSession());
        pilot.hold(pilot.cycle, 30);
        assertEquals(StabMode.ANGLE, pilot.modes.mode());
        pilot.hold(pilot.cycle, 4);
        assertEquals(StabMode.HORIZON, pilot.modes.mode());
        pilot.tap(pilot.cycle);
        pilot.tick();
        pilot.tap(pilot.cycle);
        assertEquals(StabMode.ANGLE, pilot.tick());
    }

    @Test
    void keyboardFlightStartsAsConfiguredAndCycles() {
        Pilot pilot = Pilot.keyboard(true);
        assertEquals(StabMode.ANGLE, pilot.startSession());
        pilot.tap(pilot.cycle);
        assertEquals(StabMode.HORIZON, pilot.tick());
        pilot.tap(pilot.cycle);
        assertEquals(StabMode.ACRO, pilot.tick());
        assertEquals(StabMode.ANGLE, pilot.startSession());
        Pilot acro = Pilot.keyboard(false);
        assertEquals(StabMode.ACRO, acro.startSession());
        acro.tap(acro.horizon);
        assertEquals(StabMode.HORIZON, acro.tick());
        assertFalse(acro.overrides());
    }

    @Test
    void radioWithoutModeSwitchKeepsTheKeyChoice() {
        Pilot pilot = Pilot.radio(8);
        pilot.profile.setBinding(SwitchFunction.ANGLE, SwitchAssignment.none());
        pilot.profile.setBinding(SwitchFunction.HORIZON, SwitchAssignment.none());
        pilot.micros(MODE_CHANNEL, 2000);
        assertEquals(StabMode.ACRO, pilot.startSession());
        pilot.tap(pilot.angle);
        assertEquals(StabMode.ANGLE, pilot.tick());
        assertFalse(pilot.overrides());
        pilot.micros(MODE_CHANNEL, 1000);
        assertEquals(StabMode.ANGLE, pilot.tick());
        assertEquals(StabMode.ANGLE, pilot.startSession());
        assertNull(pilot.modes.lastSwitch());
    }

    @Test
    void removingTheModeSwitchFallsBackToAcro() {
        Pilot pilot = Pilot.radio(8);
        pilot.micros(MODE_CHANNEL, 2000);
        assertEquals(StabMode.ANGLE, pilot.startSession());
        pilot.profile.setBinding(SwitchFunction.ANGLE, SwitchAssignment.none());
        pilot.profile.setBinding(SwitchFunction.HORIZON, SwitchAssignment.none());
        assertEquals(StabMode.ACRO, pilot.tick());
        pilot.profile.setBinding(SwitchFunction.ANGLE, SwitchAssignment.axisMicros(MODE_CHANNEL, 1700, 2100));
        assertEquals(StabMode.ANGLE, pilot.tick());
    }

    @Test
    void reconnectingTheRadioKeepsTheKeyChoiceUnlessTheSwitchMoved() {
        Pilot pilot = Pilot.radio(8);
        pilot.micros(MODE_CHANNEL, 1000);
        assertEquals(StabMode.ACRO, pilot.startSession());
        pilot.tap(pilot.horizon);
        assertEquals(StabMode.HORIZON, pilot.tick());
        pilot.device = false;
        for (int i = 0; i < 10; i++) {
            assertEquals(StabMode.HORIZON, pilot.tick());
        }
        pilot.device = true;
        assertEquals(StabMode.HORIZON, pilot.tick());
        pilot.device = false;
        pilot.micros(MODE_CHANNEL, 2000);
        pilot.tick();
        pilot.device = true;
        assertEquals(StabMode.ANGLE, pilot.tick());
    }

    @Test
    void radioPluggedInAfterAKeyboardStartTakesOver() {
        Pilot pilot = Pilot.radio(8);
        pilot.micros(MODE_CHANNEL, 1000);
        assertEquals(StabMode.ACRO, pilot.startSession());
        pilot.device = false;
        pilot.modes.startKeyboard(true);
        assertEquals(StabMode.ANGLE, pilot.tick());
        pilot.device = true;
        assertEquals(StabMode.ACRO, pilot.tick());
        assertEquals(FlightModeSelector.Source.SWITCH, pilot.modes.source());
    }

    @Test
    void gamepadToggleAndKeysShareOneMode() {
        Pilot pilot = Pilot.gamepad();
        assertEquals(StabMode.ANGLE, pilot.startSession());
        pilot.buttons[ProfileDefaults.PAD_LEFT_SHOULDER] = true;
        assertEquals(StabMode.ACRO, pilot.tick());
        pilot.buttons[ProfileDefaults.PAD_LEFT_SHOULDER] = false;
        assertEquals(StabMode.ACRO, pilot.tick());
        pilot.tap(pilot.cycle);
        assertEquals(StabMode.ANGLE, pilot.tick());
        pilot.tap(pilot.cycle);
        assertEquals(StabMode.HORIZON, pilot.tick());
        assertTrue(pilot.overrides());
        pilot.buttons[ProfileDefaults.PAD_LEFT_SHOULDER] = true;
        assertEquals(StabMode.ANGLE, pilot.tick());
        assertFalse(pilot.overrides());
    }

    @Test
    void switchModeNeedsAReadableModeSwitch() {
        int angle = SwitchEvaluator.bit(SwitchFunction.ANGLE);
        int horizon = SwitchEvaluator.bit(SwitchFunction.HORIZON);
        assertNull(FlightModeSelector.fromSwitches(0, 0));
        assertNull(FlightModeSelector.fromSwitches(angle, SwitchEvaluator.bit(SwitchFunction.ARM)));
        assertEquals(StabMode.ACRO, FlightModeSelector.fromSwitches(0, horizon));
        assertEquals(StabMode.ANGLE, FlightModeSelector.fromSwitches(angle | horizon, angle | horizon));
        assertEquals(StabMode.HORIZON, FlightModeSelector.fromSwitches(horizon, angle | horizon));
        DeviceProfile radio = ProfileDefaults.radio("R", 8);
        int readable = SwitchEvaluator.readable(radio, new float[8], new boolean[0]);
        assertTrue(SwitchEvaluator.isSet(readable, SwitchFunction.ANGLE));
        assertTrue(SwitchEvaluator.isSet(readable, SwitchFunction.HORIZON));
        assertTrue(SwitchEvaluator.isSet(readable, SwitchFunction.ARM));
        assertFalse(SwitchEvaluator.isSet(readable, SwitchFunction.LEDS_OFF));
        assertEquals(0, SwitchEvaluator.readable(radio, new float[3], new boolean[0]));
        assertEquals(StabMode.ACRO, FlightModeSelector.next(StabMode.HORIZON));
    }

    @Test
    void modeFromTheSwitchIsNotAKeyOverride() {
        FlightModeSelector modes = new FlightModeSelector();
        int angle = SwitchEvaluator.bit(SwitchFunction.ANGLE);
        assertTrue(modes.observe(true, angle, angle));
        assertFalse(modes.keyOverridesSwitch(true, angle, angle));
        modes.select(StabMode.ACRO);
        assertTrue(modes.keyOverridesSwitch(true, angle, angle));
        assertFalse(modes.keyOverridesSwitch(false, angle, angle));
        assertFalse(modes.observe(true, angle, angle));
        assertEquals(StabMode.ACRO, modes.mode());
        modes.reset();
        assertEquals(StabMode.ACRO, modes.mode());
        assertEquals(FlightModeSelector.Source.DEFAULT, modes.source());
        assertNull(modes.lastSwitch());
    }

    private static final class Pilot {
        private static final int REPEAT_DELAY_TICKS = 5;

        private final DeviceProfile profile;
        private final float[] axes;
        private final boolean[] buttons;
        private final boolean keyboardOnly;
        private final boolean startInAngle;
        private final SwitchEvaluator evaluator = new SwitchEvaluator();
        private final FlightModeSelector modes = new FlightModeSelector();
        private final KeyRepeatFilter cycle = new KeyRepeatFilter();
        private final KeyRepeatFilter acro = new KeyRepeatFilter();
        private final KeyRepeatFilter angle = new KeyRepeatFilter();
        private final KeyRepeatFilter horizon = new KeyRepeatFilter();
        private boolean device;
        private int bits;
        private int readable;

        private Pilot(DeviceProfile profile, int axisCount, int buttonCount, boolean keyboardOnly, boolean startInAngle) {
            this.profile = profile;
            this.axes = new float[axisCount];
            this.buttons = new boolean[buttonCount];
            this.keyboardOnly = keyboardOnly;
            this.startInAngle = startInAngle;
            this.device = !keyboardOnly;
            for (int i = 4; i < axisCount; i++) {
                this.micros(i, 1000);
            }
        }

        static Pilot radio(int axisCount) {
            return new Pilot(ProfileDefaults.radio("Radiomaster Pocket Joystick", axisCount), axisCount, 0, false, true);
        }

        static Pilot gamepad() {
            return new Pilot(ProfileDefaults.gamepad("Pad"), ProfileDefaults.PAD_AXES, ProfileDefaults.PAD_BUTTONS, false, true);
        }

        static Pilot keyboard(boolean startInAngle) {
            return new Pilot(new DeviceProfile(), 0, 0, true, startInAngle);
        }

        void micros(int channel, int us) {
            this.axes[channel] = ChannelMath.fromRangeMicros(us);
        }

        StabMode startSession() {
            this.read();
            if (this.keyboardOnly) {
                this.modes.startKeyboard(this.startInAngle);
            }
            this.modes.observe(this.device, this.bits, this.readable);
            return this.modes.mode();
        }

        StabMode tick() {
            this.read();
            this.modes.observe(this.device, this.bits, this.readable);
            return this.modes.mode();
        }

        boolean overrides() {
            return this.modes.keyOverridesSwitch(this.device, this.bits, this.readable);
        }

        void tap(KeyRepeatFilter key) {
            this.press(key, 1, false);
        }

        void hold(KeyRepeatFilter key, int ticks) {
            for (int t = 0; t < ticks; t++) {
                int clicks = t == 0 ? 1 : t >= REPEAT_DELAY_TICKS ? 2 : 0;
                this.press(key, clicks, true);
                this.tick();
            }
            this.press(key, 0, false);
            this.tick();
        }

        private void press(KeyRepeatFilter key, int clicks, boolean down) {
            if (!key.pressed(clicks, down)) {
                return;
            }
            if (key == this.cycle) {
                this.modes.cycle();
            } else if (key == this.acro) {
                this.modes.select(StabMode.ACRO);
            } else if (key == this.angle) {
                this.modes.select(StabMode.ANGLE);
            } else {
                this.modes.select(StabMode.HORIZON);
            }
        }

        private void read() {
            if (!this.device) {
                this.bits = 0;
                this.readable = 0;
                return;
            }
            this.bits = this.evaluator.evaluate(this.profile, this.axes, this.buttons);
            this.readable = SwitchEvaluator.readable(this.profile, this.axes, this.buttons);
        }
    }
}
