package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseConfigTest {
    private static final List<DeviceChooser.Candidate> DEVICES = List.of(new DeviceChooser.Candidate("pad", "Xbox Controller", true),
            new DeviceChooser.Candidate("radio", "Radiomaster Pocket Joystick", false));

    @Test
    void defaultsLetABeginnerFlyAngleAndAProFlyAcro() {
        MouseSettings s = new MouseSettings();
        assertEquals(MouseMode.AUTO, s.mode);
        assertEquals(MouseAxis.ROLL, s.xAxis);
        assertFalse(new InputConfig().invertPitch);
        assertTrue(s.wheelThrottle());
        assertTrue(s.mode.holds(true));
        assertFalse(s.mode.holds(false));
        assertTrue(s.returnSpeed > 0.0F && s.returnSpeed < 1.0F);
        assertTrue(s.deadzone > 0.0F && s.deadzone < 0.1F);
        assertTrue(s.smoothingMs >= 16.0F && s.smoothingMs <= 25.0F);
    }

    @Test
    void sanitizeRepairsBrokenValues() {
        MouseSettings s = new MouseSettings();
        s.mode = null;
        s.xAxis = null;
        s.sensitivity = Float.NaN;
        s.dpi = 0;
        s.smoothingMs = 500.0F;
        s.deadzone = -1.0F;
        s.expo = 7.0F;
        s.returnSpeed = Float.POSITIVE_INFINITY;
        s.wheelStep = 2.0F;
        s.sanitize();
        assertEquals(MouseMode.AUTO, s.mode);
        assertEquals(MouseAxis.ROLL, s.xAxis);
        assertEquals(MouseSettings.DEFAULT_SENSITIVITY, s.sensitivity);
        assertEquals(MouseSettings.DEFAULT_DPI, s.dpi);
        assertEquals(MouseSettings.MAX_SMOOTHING_MS, s.smoothingMs);
        assertEquals(0.0F, s.deadzone);
        assertEquals(1.0F, s.expo);
        assertEquals(MouseSettings.DEFAULT_RETURN_SPEED, s.returnSpeed);
        assertEquals(MouseSettings.MAX_WHEEL_STEP, s.wheelStep);
        s.dpi = 99_999;
        s.sanitize();
        assertEquals(MouseSettings.MAX_DPI, s.dpi);
    }

    @Test
    void mouseIsAnInputSourceWithoutDevice() {
        InputConfig config = new InputConfig();
        assertFalse(config.mouseSelected());
        config.selectedGuid = InputConfig.MOUSE_GUID;
        assertTrue(config.mouseSelected());
        assertTrue(config.keyboardSelected());
        assertTrue(InputConfig.withoutDevice(InputConfig.MOUSE_GUID));
        assertTrue(InputConfig.withoutDevice(InputConfig.KEYBOARD_GUID));
        assertFalse(InputConfig.withoutDevice("pad"));
        assertFalse(InputConfig.withoutDevice(null));
        config.selectedGuid = InputConfig.KEYBOARD_GUID;
        assertFalse(config.mouseSelected());
        assertTrue(config.keyboardSelected());
    }

    @Test
    void choosingTheMouseNeverOpensAController() {
        assertEquals(-1, DeviceChooser.pick(DEVICES, InputConfig.MOUSE_GUID));
        assertEquals(-1, DeviceChooser.pick(DEVICES, InputConfig.MOUSE_GUID, "radio", i -> false));
        assertEquals(1, DeviceChooser.pick(DEVICES, "radio"));
    }

    @Test
    void mouseSettingsAreCopiedMergedAndSanitized() {
        InputConfig config = new InputConfig();
        config.selectedGuid = InputConfig.MOUSE_GUID;
        config.mouse.sensitivity = 2.5F;
        config.mouse.mode = MouseMode.HOLD;
        InputConfig copy = config.copy();
        assertNotSame(config.mouse, copy.mouse);
        assertEquals(2.5F, copy.mouse.sensitivity);
        assertEquals(MouseMode.HOLD, copy.mouse.mode);
        assertTrue(copy.mouseSelected());
        InputConfig target = new InputConfig();
        target.mergeReceiverFrom(config);
        assertEquals(2.5F, target.mouse.sensitivity);
        assertTrue(target.mouseSelected());
        InputConfig broken = new InputConfig();
        broken.mouse = null;
        broken.sanitize();
        assertEquals(MouseSettings.DEFAULT_DPI, broken.mouse.dpi);
    }

    @Test
    void theMouseIsNeverOfferedAsANewDeviceToSetUp() {
        InputConfig config = new InputConfig();
        config.neverAskFor(InputConfig.MOUSE_GUID);
        assertTrue(config.neverAsk.isEmpty());
        config.neverAsk.add(InputConfig.MOUSE_GUID);
        config.sanitize();
        assertTrue(config.neverAsk.isEmpty());
    }
}
