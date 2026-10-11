package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceSelectionTest {
    private static final DeviceChooser.Candidate RADIO = new DeviceChooser.Candidate("radio", "RadioMaster TX16S Joystick", false);
    private static final DeviceChooser.Candidate PAD = new DeviceChooser.Candidate("pad", "Xbox Controller", true);
    private static final DeviceChooser.Candidate WHEEL = new DeviceChooser.Candidate("wheel", "Racing Wheel", false);
    private static final List<DeviceChooser.Candidate> ALL = List.of(WHEEL, PAD, RADIO);

    @Test
    void autoPrefersRadioThenGamepadThenJoystick() {
        assertEquals(2, DeviceChooser.pick(ALL, ""));
        assertEquals(1, DeviceChooser.pick(List.of(WHEEL, PAD), ""));
        assertEquals(0, DeviceChooser.pick(List.of(WHEEL), null));
        assertEquals(-1, DeviceChooser.pick(List.of(), ""));
    }

    @Test
    void explicitSelectionWinsOverScore() {
        assertEquals(0, DeviceChooser.pick(ALL, "wheel"));
        assertEquals(2, DeviceChooser.pick(ALL, "missing"));
    }

    @Test
    void keyboardSelectionNeverPicksADevice() {
        assertEquals(-1, DeviceChooser.pick(ALL, InputConfig.KEYBOARD_GUID));
        assertEquals(-1, DeviceChooser.pick(ALL, InputConfig.KEYBOARD_GUID, "radio", i -> false));
    }

    @Test
    void devicesThatFailedToOpenAreSkipped() {
        Set<Integer> failed = Set.of(2);
        assertEquals(1, DeviceChooser.pick(ALL, "", null, failed::contains));
        assertEquals(1, DeviceChooser.pick(ALL, "radio", null, failed::contains));
        assertEquals(0, DeviceChooser.pick(ALL, "", null, i -> i != 0));
        assertEquals(-1, DeviceChooser.pick(ALL, "", null, i -> true));
    }

    @Test
    void sessionDeviceIsKeptAndNeverReplacedMidFlight() {
        assertEquals(1, DeviceChooser.pick(ALL, "", "pad", i -> false));
        assertEquals(-1, DeviceChooser.pick(List.of(WHEEL, PAD), "", "radio", i -> false));
        assertEquals(-1, DeviceChooser.pick(ALL, "", "radio", i -> i == 2));
        assertEquals(0, DeviceChooser.pick(ALL, "wheel", "radio", i -> false));
        assertEquals(-1, DeviceChooser.pick(List.of(WHEEL, PAD), "radio", "radio", i -> false));
    }
}
