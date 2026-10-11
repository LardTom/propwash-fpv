package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfiHintTest {
    @Test
    void onMacTheDefaultTurnsAppleFrameworkOff() {
        MfiHint.Plan plan = MfiHint.plan(true, null, new InputConfig().appleGameController);
        assertEquals(MfiHint.Source.OPTION_OFF, plan.source());
        assertTrue(plan.setsOff());
        assertFalse(plan.resets());
        assertFalse(plan.appleFramework());
        assertFalse(plan.optionLocked());
        assertEquals("SDL_JOYSTICK_MFI", MfiHint.NAME);
        assertEquals("0", MfiHint.OFF);
    }

    @Test
    void theOptionGivesTheDeviceBackToApple() {
        MfiHint.Plan plan = MfiHint.plan(true, null, true);
        assertEquals(MfiHint.Source.OPTION_ON, plan.source());
        assertTrue(plan.resets());
        assertFalse(plan.setsOff());
        assertTrue(plan.appleFramework());
    }

    @Test
    void anEnvironmentVariableWinsAndLocksTheOption() {
        for (boolean option : new boolean[]{false, true}) {
            MfiHint.Plan off = MfiHint.plan(true, "0", option);
            assertEquals(MfiHint.Source.ENVIRONMENT, off.source());
            assertFalse(off.setsOff());
            assertFalse(off.resets());
            assertTrue(off.optionLocked());
            assertFalse(off.appleFramework());
            assertEquals("0", off.environment());
            assertTrue(MfiHint.plan(true, "1", option).appleFramework());
            assertTrue(MfiHint.plan(true, "", option).appleFramework());
            assertFalse(MfiHint.plan(true, "false", option).appleFramework());
        }
    }

    @Test
    void otherSystemsAreNeverTouched() {
        for (String environment : new String[]{null, "0", "1"}) {
            for (boolean option : new boolean[]{false, true}) {
                MfiHint.Plan plan = MfiHint.plan(false, environment, option);
                assertEquals(MfiHint.Source.NOT_MAC, plan.source());
                assertFalse(plan.setsOff());
                assertFalse(plan.resets());
                assertFalse(plan.optionLocked());
                assertNull(plan.environment());
            }
        }
    }

    @Test
    void onlyAChangedOptionRestartsTheBackend() {
        MfiHint.Plan off = MfiHint.plan(true, null, false);
        MfiHint.Plan on = MfiHint.plan(true, null, true);
        assertTrue(MfiHint.restartNeeded(off, on));
        assertTrue(MfiHint.restartNeeded(on, off));
        assertFalse(MfiHint.restartNeeded(off, off));
        assertFalse(MfiHint.restartNeeded(on, on));
        assertFalse(MfiHint.restartNeeded(MfiHint.plan(true, "0", false), on));
        assertFalse(MfiHint.restartNeeded(off, MfiHint.plan(true, "1", true)));
        assertFalse(MfiHint.restartNeeded(MfiHint.plan(false, null, false), MfiHint.plan(false, null, true)));
    }

    @Test
    void sdlBooleanRulesMatch() {
        assertTrue(MfiHint.enabled(null));
        assertTrue(MfiHint.enabled(""));
        assertTrue(MfiHint.enabled("1"));
        assertTrue(MfiHint.enabled("yes"));
        assertFalse(MfiHint.enabled("0"));
        assertFalse(MfiHint.enabled("00"));
        assertFalse(MfiHint.enabled("FALSE"));
        assertFalse(MfiHint.enabled("false"));
    }

    @Test
    void theOptionTravelsWithCopyAndReceiverMerge() {
        InputConfig source = new InputConfig();
        source.appleGameController = true;
        assertTrue(source.copy().appleGameController);
        InputConfig target = new InputConfig();
        target.mergeReceiverFrom(source);
        assertTrue(target.appleGameController);
    }
}
