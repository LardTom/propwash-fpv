package de.lardtom.propwash.input;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.KeyDefaults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxSwitchTest {
    @Test
    void blackboxIsAnOptionalLatchingFlightFunction() {
        assertEquals("propwash.input.switch.blackbox", SwitchFunction.BLACKBOX.translationKey());
        assertTrue(SwitchFunction.BLACKBOX.supportsToggle());
        assertTrue(SwitchFunction.BLACKBOX.latchesOnGamepad());
        assertFalse(SwitchFunction.BLACKBOX.command());
        assertTrue(SwitchFunction.flightFunctions().contains(SwitchFunction.BLACKBOX));
        assertFalse(ProfileDefaults.radio("Funke", 8).binding(SwitchFunction.BLACKBOX).bound());
        assertFalse(ProfileDefaults.gamepad("Pad").binding(SwitchFunction.BLACKBOX).bound());
    }

    @Test
    void blackboxKeySitsNextToTheModeKeysOnTheNumpad() {
        assertEquals(KeyDefaults.BLACKBOX, FunctionKeys.entry(SwitchFunction.BLACKBOX));
        assertEquals(InputConstants.KEY_NUMPAD7, KeyDefaults.BLACKBOX.key());
        assertEquals("key.propwash.blackbox", KeyDefaults.BLACKBOX.mappingName());
        assertTrue(KeyDefaults.OTHER_MODULES.contains(KeyDefaults.BLACKBOX));
    }

    @Test
    void switchBitsStillFitInAnInt() {
        assertTrue(SwitchFunction.values().length <= Integer.SIZE);
        assertEquals(1 << SwitchFunction.BLACKBOX.ordinal(), SwitchEvaluator.bit(SwitchFunction.BLACKBOX));
    }
}
