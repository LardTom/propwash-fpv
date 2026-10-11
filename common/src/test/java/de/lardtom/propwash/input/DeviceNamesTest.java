package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceNamesTest {
    private static final int VID = DeviceNames.OPENTX_VENDOR;
    private static final int PID = DeviceNames.OPENTX_PRODUCT;

    @Test
    void stripsFirmwarePrefixAndJoystickSuffix() {
        assertEquals("Radiomaster Boxer", DeviceNames.displayName("OpenTX Radiomaster Boxer Joystick", VID, PID));
        assertEquals("RadioMaster TX16S", DeviceNames.displayName("EdgeTX RadioMaster TX16S Joystick", VID, PID));
        assertEquals("Radiomaster Boxer", DeviceNames.displayName("  opentx Radiomaster Boxer JOYSTICK  "));
        assertEquals("Radiomaster Boxer", DeviceNames.displayName("Radiomaster Boxer"));
    }

    @Test
    void neverClaimsOpenTxAlone() {
        assertEquals(DeviceNames.FIRMWARE_BADGE, DeviceNames.displayName("OpenTX Joystick", VID, PID));
        assertEquals(DeviceNames.FIRMWARE_BADGE, DeviceNames.displayName("EdgeTX", -1, -1));
        assertEquals(DeviceNames.FIRMWARE_BADGE, DeviceNames.displayName("OpenTX", VID, PID));
    }

    @Test
    void leavesOtherDevicesAlone() {
        assertEquals("Xbox Wireless Controller", DeviceNames.displayName("Xbox Wireless Controller", 0x045E, 0x0B13));
        assertEquals("Generic USB Joystick", DeviceNames.displayName("Generic USB Joystick", 0x0079, 0x0006));
        assertEquals("", DeviceNames.displayName(null));
        assertEquals("", DeviceNames.displayName("   "));
    }

    @Test
    void detectsFirmwareByUsbIdOrName() {
        assertTrue(DeviceNames.edgeTxOrOpenTx("USB HID", VID, PID));
        assertTrue(DeviceNames.edgeTxOrOpenTx("OpenTX Radiomaster Boxer Joystick", -1, -1));
        assertTrue(DeviceNames.edgeTxOrOpenTx("EdgeTX Jumper T-Pro", 0, 0));
        assertFalse(DeviceNames.edgeTxOrOpenTx("Radiomaster Boxer", 0x0483, 0x5710));
        assertFalse(DeviceNames.edgeTxOrOpenTx("Xbox Wireless Controller", 0x045E, 0x0B13));
        assertFalse(DeviceNames.edgeTxOrOpenTx(null, VID, 0x1234));
    }

    @Test
    void radioScoresAboveGamepadEvenWithoutRadioWordInName() {
        DeviceChooser.Candidate usbOnly = new DeviceChooser.Candidate("a", "USB HID", true, DeviceNames.edgeTxOrOpenTx("USB HID", VID, PID));
        DeviceChooser.Candidate pad = new DeviceChooser.Candidate("b", "Xbox Wireless Controller", true);
        assertEquals(3, DeviceChooser.score(usbOnly));
        assertEquals(2, DeviceChooser.score(pad));
    }
}
