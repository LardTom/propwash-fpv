package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceHotplugTest {
    private static final DeviceChooser.Candidate STICK = new DeviceChooser.Candidate("stick", "Generic USB Joystick", false);
    private static final DeviceChooser.Candidate PAD = new DeviceChooser.Candidate("pad", "Xbox Controller", true);
    private static final DeviceChooser.Candidate RADIO = new DeviceChooser.Candidate("radio", "RadioMaster Pocket Joystick", false);
    private static final DeviceChooser.Candidate RADIO_2 = new DeviceChooser.Candidate("radio2", "Jumper T-Pro Joystick", false);
    private static final IntPredicate NONE = i -> false;
    private static final ToIntFunction<String> NO_SETUP = guid -> -1;

    private static IntPredicate arrived(int... indices) {
        Set<Integer> set = new java.util.HashSet<>();
        for (int index : indices) {
            set.add(index);
        }
        return set::contains;
    }

    private static ToIntFunction<String> ranks(Map<String, Integer> ranks) {
        return guid -> ranks.getOrDefault(guid, -1);
    }

    @Test
    void radioPluggedInDuringKeyboardFlightIsTakenAtOnce() {
        assertEquals(0, DeviceChooser.pick(List.of(RADIO), "", null, NONE, NO_SETUP, arrived(0)));
        assertEquals(0, DeviceChooser.pick(List.of(RADIO), "", "", NONE, NO_SETUP, arrived(0)));
    }

    @Test
    void radioPluggedInDuringFlightReplacesTheUnconfiguredSessionDevice() {
        List<DeviceChooser.Candidate> devices = List.of(STICK, RADIO);
        ToIntFunction<String> usage = ranks(Map.of("radio", 0));
        assertEquals(0, DeviceChooser.pick(devices, "", "stick", NONE, usage));
        assertEquals(1, DeviceChooser.pick(devices, "", "stick", NONE, usage, arrived(1)));
        assertEquals(1, DeviceChooser.pick(devices, "", "stick", NONE, NO_SETUP, arrived(1)));
        assertEquals(1, DeviceChooser.pick(List.of(PAD, RADIO), "", "pad", NONE, NO_SETUP, arrived(1)));
    }

    @Test
    void configuredSessionDeviceKeepsTheFlightWhenALesserDeviceIsPluggedIn() {
        List<DeviceChooser.Candidate> devices = List.of(RADIO, PAD);
        ToIntFunction<String> usage = ranks(Map.of("pad", 0, "radio", 1));
        assertEquals(0, DeviceChooser.pick(devices, "", "radio", NONE, usage, arrived(1)));
        assertEquals(0, DeviceChooser.pick(devices, "", "radio", NONE, NO_SETUP, arrived(1)));
        assertEquals(0, DeviceChooser.pick(List.of(RADIO, STICK), "", "radio", NONE, NO_SETUP, arrived(1)));
    }

    @Test
    void selectedDeviceStillWinsAndKeyboardSelectionStillPicksNothing() {
        List<DeviceChooser.Candidate> devices = List.of(RADIO, PAD);
        assertEquals(1, DeviceChooser.pick(devices, "pad", "radio", NONE, NO_SETUP, arrived(1)));
        assertEquals(0, DeviceChooser.pick(devices, "radio", "pad", NONE, NO_SETUP, arrived(1)));
        assertEquals(-1, DeviceChooser.pick(devices, InputConfig.KEYBOARD_GUID, "radio", NONE, NO_SETUP, arrived(0, 1)));
        assertEquals(-1, DeviceChooser.pick(devices, InputConfig.MOUSE_GUID, null, NONE, NO_SETUP, arrived(0, 1)));
    }

    @Test
    void unpluggedSessionDeviceLeavesFailsafeAndNeverFallsBackToADeviceThatWasAlreadyThere() {
        assertEquals(-1, DeviceChooser.pick(List.of(STICK, PAD), "", "radio", NONE, NO_SETUP, NONE));
        assertEquals(-1, DeviceChooser.pick(List.of(STICK, PAD), "", "radio", NONE, ranks(Map.of("pad", 3)), NONE));
        assertEquals(-1, DeviceChooser.pick(List.of(), "", "radio", NONE, NO_SETUP, NONE));
    }

    @Test
    void sameRadioPluggedBackInResumesTheFlight() {
        assertEquals(1, DeviceChooser.pick(List.of(PAD, RADIO), "", "radio", NONE, NO_SETUP, arrived(1)));
        assertEquals(1, DeviceChooser.pick(List.of(PAD, RADIO), "", "radio", NONE, NO_SETUP, NONE));
    }

    @Test
    void anotherRadioPluggedInAfterTheSessionDeviceWasLostTakesOver() {
        List<DeviceChooser.Candidate> devices = List.of(PAD, RADIO_2);
        ToIntFunction<String> usage = ranks(Map.of("pad", 0));
        assertEquals(1, DeviceChooser.pick(devices, "", "radio", NONE, NO_SETUP, arrived(1)));
        assertEquals(1, DeviceChooser.pick(devices, "", "radio", NONE, usage, arrived(1)));
        assertEquals(1, DeviceChooser.pick(devices, "radio", "radio", NONE, usage, arrived(1)));
    }

    @Test
    void pluggedDeviceThatFailsToOpenIsSkippedUntilItsRetry() {
        List<DeviceChooser.Candidate> devices = List.of(STICK, RADIO);
        assertEquals(0, DeviceChooser.pick(devices, "", "stick", i -> i == 1, NO_SETUP, arrived(1)));
        assertEquals(-1, DeviceChooser.pick(List.of(PAD, RADIO_2), "", "radio", i -> i == 1, NO_SETUP, arrived(1)));
    }

    @Test
    void bestOfSeveralPluggedDevicesFollowsTheUsualOrder() {
        List<DeviceChooser.Candidate> devices = List.of(STICK, PAD, RADIO);
        assertEquals(2, DeviceChooser.pick(devices, "", "lost", NONE, NO_SETUP, arrived(0, 1, 2)));
        assertEquals(1, DeviceChooser.pick(devices, "", "lost", NONE, ranks(Map.of("pad", 0)), arrived(0, 1, 2)));
        assertEquals(1, DeviceChooser.pick(devices, "", "lost", NONE, NO_SETUP, arrived(0, 1)));
    }

    @Test
    void withoutArrivalsTheSessionRuleIsUnchanged() {
        List<DeviceChooser.Candidate> all = List.of(STICK, PAD, RADIO);
        assertEquals(1, DeviceChooser.pick(all, "", "pad", NONE, NO_SETUP, NONE));
        assertEquals(-1, DeviceChooser.pick(List.of(STICK, PAD), "", "radio", NONE, NO_SETUP, NONE));
        assertEquals(-1, DeviceChooser.pick(all, "", "radio", i -> i == 2, NO_SETUP, NONE));
        assertEquals(0, DeviceChooser.pick(all, "stick", "radio", NONE, NO_SETUP, NONE));
        assertEquals(2, DeviceChooser.pick(all, "", null, NONE, NO_SETUP, NONE));
    }
}
