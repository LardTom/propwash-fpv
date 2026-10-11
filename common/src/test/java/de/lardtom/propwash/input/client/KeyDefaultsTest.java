package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.input.SwitchFunction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyDefaultsTest {
    private static final Set<Integer> VANILLA_DEFAULTS = Set.of(
            InputConstants.KEY_W, InputConstants.KEY_A, InputConstants.KEY_S, InputConstants.KEY_D, InputConstants.KEY_SPACE,
            InputConstants.KEY_LSHIFT, InputConstants.KEY_LCONTROL, InputConstants.KEY_E, InputConstants.KEY_F, InputConstants.KEY_Q,
            InputConstants.KEY_T, InputConstants.KEY_TAB, InputConstants.KEY_SLASH, InputConstants.KEY_O, InputConstants.KEY_P,
            InputConstants.KEY_L, InputConstants.KEY_G, InputConstants.KEY_C, InputConstants.KEY_X, InputConstants.KEY_F1,
            InputConstants.KEY_F2, InputConstants.KEY_F4, InputConstants.KEY_F5, InputConstants.KEY_F11,
            InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4, InputConstants.KEY_5,
            InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8, InputConstants.KEY_9,
            InputConstants.KEY_B, InputConstants.KEY_H, InputConstants.KEY_I, InputConstants.KEY_N, InputConstants.KEY_V,
            InputConstants.KEY_F3, InputConstants.KEY_F6);

    @Test
    void noTwoPropwashMappingsShareADefaultKey() {
        assertEquals(List.of(), KeyDefaults.duplicates(KeyDefaults.all()));
    }

    @Test
    void gateRotateSharesTheFlightModeKey() {
        assertEquals(InputConstants.KEY_R, KeyDefaults.GATE_ROTATE.key());
        assertEquals(KeyDefaults.FLIGHT_MODE.key(), KeyDefaults.GATE_ROTATE.key());
        assertEquals("key.propwash.gate_rotate", KeyDefaults.GATE_ROTATE.mappingName());
        assertTrue(KeyDefaults.sharesKey(KeyDefaults.FLIGHT_MODE.mappingName(), KeyDefaults.GATE_ROTATE.mappingName()));
        assertTrue(KeyDefaults.sharesKey(KeyDefaults.GATE_ROTATE.mappingName(), KeyDefaults.FLIGHT_MODE.mappingName()));
        assertFalse(KeyDefaults.sharesKey(KeyDefaults.GATE_ROTATE.mappingName(), KeyDefaults.GATE_ROTATE.mappingName()));
        assertFalse(KeyDefaults.sharesKey(KeyDefaults.GATE_ROTATE.mappingName(), KeyDefaults.ARM.mappingName()));
        assertFalse(KeyDefaults.sharesKey(KeyDefaults.FLIGHT_MODE.mappingName(), "key.swapOffhand"));
        assertFalse(KeyDefaults.sharesKey(KeyDefaults.GATE_ROTATE.mappingName(), "key.drop"));
    }

    @Test
    void sharedPairsAreNoDuplicatesButAThirdMappingIs() {
        KeyDefaults.Entry flight = new KeyDefaults.Entry("drone_flight_mode", InputConstants.KEY_R, true, false);
        KeyDefaults.Entry gate = new KeyDefaults.Entry("gate_rotate", InputConstants.KEY_R, false, false);
        KeyDefaults.Entry other = new KeyDefaults.Entry("rescue", InputConstants.KEY_R, false, false);
        assertEquals(List.of(), KeyDefaults.duplicates(List.of(flight, gate)));
        assertEquals(List.of(InputConstants.KEY_R + "=[drone_flight_mode, gate_rotate, rescue]"), KeyDefaults.duplicates(List.of(flight, gate, other)));
        assertEquals(List.of(InputConstants.KEY_R + "=[gate_rotate, rescue]"), KeyDefaults.duplicates(List.of(gate, other)));
    }

    @Test
    void duplicateFinderReportsCollisions() {
        List<KeyDefaults.Entry> clash = List.of(new KeyDefaults.Entry("a", InputConstants.KEY_M, true, false),
                new KeyDefaults.Entry("b", InputConstants.KEY_M, false, false),
                new KeyDefaults.Entry("c", KeyDefaults.UNBOUND, true, false),
                new KeyDefaults.Entry("d", KeyDefaults.UNBOUND, true, false));
        assertEquals(List.of(InputConstants.KEY_M + "=[a, b]"), KeyDefaults.duplicates(clash));
    }

    @Test
    void namesAreUniqueAndOldKeyboardSwitchesAreGone() {
        Set<String> names = new HashSet<>();
        for (KeyDefaults.Entry entry : KeyDefaults.all()) {
            assertTrue(names.add(entry.name()), entry.name());
            assertTrue(entry.mappingName().startsWith("key.propwash."));
        }
        for (String removed : KeyDefaults.REMOVED) {
            assertFalse(names.contains(removed), removed);
        }
    }

    @Test
    void noDefaultSharesAVanillaKey() {
        for (KeyDefaults.Entry entry : KeyDefaults.all()) {
            if (entry.key() == KeyDefaults.UNBOUND) {
                continue;
            }
            assertFalse(VANILLA_DEFAULTS.contains(entry.key()), entry.name() + " liegt auf einer Vanilla-Standardtaste");
        }
        assertNotEquals(KeyDefaults.ARM.key(), KeyDefaults.FLIGHT_MODE.key());
    }

    @Test
    void mouseCenterSitsOnTheMiddleButtonNextToPickBlock() {
        assertTrue(KeyDefaults.MOUSE_CENTER.mouse());
        assertEquals(InputConstants.MOUSE_BUTTON_MIDDLE, KeyDefaults.MOUSE_CENTER.key());
        assertTrue(KeyDefaults.OWN.contains(KeyDefaults.MOUSE_CENTER));
        assertTrue(KeyDefaults.MOUSE_CENTER.flightStick());
        assertTrue(KeyDefaults.sharesKey(KeyDefaults.MOUSE_CENTER.mappingName(), KeyDefaults.VANILLA_PICK_ITEM));
        assertTrue(KeyDefaults.sharesKey(KeyDefaults.VANILLA_PICK_ITEM, KeyDefaults.MOUSE_CENTER.mappingName()));
        assertFalse(KeyDefaults.sharesKey(KeyDefaults.MOUSE_CENTER.mappingName(), "key.attack"));
        assertTrue(KeyDefaults.sharesKey(KeyDefaults.MOUSE_CENTER.mappingName(), KeyDefaults.VANILLA_SPECTATOR_HOTBAR));
        assertFalse(KeyDefaults.sharesKey(KeyDefaults.VANILLA_PICK_ITEM, KeyDefaults.VANILLA_SPECTATOR_HOTBAR));
        for (KeyDefaults.Entry entry : KeyDefaults.all()) {
            if (entry != KeyDefaults.MOUSE_CENTER) {
                assertFalse(entry.mouse(), entry.name());
            }
        }
    }

    @Test
    void mouseButtonsAndKeyboardKeysWithTheSameNumberDoNotClash() {
        KeyDefaults.Entry mouse = new KeyDefaults.Entry("a", 2, true, false, true);
        KeyDefaults.Entry key = new KeyDefaults.Entry("b", 2, true, false, false);
        KeyDefaults.Entry otherMouse = new KeyDefaults.Entry("c", 2, true, false, true);
        assertEquals(List.of(), KeyDefaults.duplicates(List.of(mouse, key)));
        assertEquals(List.of("mouse.2=[a, c]"), KeyDefaults.duplicates(List.of(mouse, key, otherMouse)));
    }

    @Test
    void keyboardSticksFollowVanillaMovementUntilBound() {
        for (KeyDefaults.Entry entry : List.of(KeyDefaults.THROTTLE_UP, KeyDefaults.THROTTLE_DOWN, KeyDefaults.YAW_LEFT, KeyDefaults.YAW_RIGHT)) {
            assertEquals(KeyDefaults.UNBOUND, entry.key(), entry.name());
            assertTrue(entry.flightStick(), entry.name());
        }
    }

    @Test
    void ledSwitchHasItsOwnDroneKeyNextToTheActionCam() {
        assertEquals(KeyDefaults.LEDS, FunctionKeys.entry(SwitchFunction.LEDS_OFF));
        assertEquals("key.propwash.drone_leds", KeyDefaults.LEDS.mappingName());
        assertEquals(InputConstants.KEY_F8, KeyDefaults.LEDS.key());
        assertTrue(KeyDefaults.OWN.contains(KeyDefaults.LEDS));
        assertFalse(KeyDefaults.LEDS.flightStick());
        assertNotEquals(KeyDefaults.ACTION_CAM_RECORD.key(), KeyDefaults.LEDS.key());
    }

    @Test
    void everySwitchFunctionHasAKey() {
        for (SwitchFunction function : SwitchFunction.values()) {
            KeyDefaults.Entry entry = FunctionKeys.entry(function);
            assertTrue(KeyDefaults.all().contains(entry), function.name());
            if (function.command()) {
                assertEquals(KeyDefaults.UNBOUND, entry.key(), function.name());
            } else {
                assertNotEquals(KeyDefaults.UNBOUND, entry.key(), function.name());
            }
        }
        assertNotEquals(FunctionKeys.entry(SwitchFunction.ANGLE), FunctionKeys.entry(SwitchFunction.HORIZON));
        assertNotEquals(KeyDefaults.FLIGHT_MODE, FunctionKeys.entry(SwitchFunction.HORIZON));
    }

    @Test
    void customCommandsHaveTheirOwnUnboundKeys() {
        assertEquals(SwitchFunction.COMMAND_COUNT, KeyDefaults.COMMANDS.size());
        Set<String> names = new HashSet<>();
        for (int i = 0; i < SwitchFunction.COMMAND_COUNT; i++) {
            KeyDefaults.Entry entry = KeyDefaults.COMMANDS.get(i);
            assertEquals("key.propwash.command_" + (i + 1), entry.mappingName());
            assertEquals(entry, FunctionKeys.entry(SwitchFunction.command(i)));
            assertEquals(KeyDefaults.UNBOUND, entry.key());
            assertFalse(entry.own());
            assertFalse(entry.flightStick());
            assertTrue(KeyDefaults.isCommand(entry.mappingName()));
            assertTrue(names.add(entry.mappingName()));
        }
        assertFalse(KeyDefaults.isCommand(KeyDefaults.ARM.mappingName()));
        assertFalse(KeyDefaults.OWN.contains(KeyDefaults.COMMANDS.getFirst()));
    }
}
