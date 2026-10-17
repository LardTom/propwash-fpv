package de.lardtom.propwash.input.client;

import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KeyRenameTest {
    private static final String OLD_OPTION = "key_key.propwash.race_restart";
    private static final String NEW_OPTION = "key_key.propwash.reset";

    @Test
    void resetKeepsTheBackspaceDefault() {
        assertEquals("key.propwash.reset", KeyDefaults.RESET.mappingName());
        assertEquals(KeyDefaults.RESET, FunctionKeys.entry(SwitchFunction.RESET));
        assertEquals("key.propwash.reset", KeyDefaults.RENAMED.get("key.propwash.race_restart"));
    }

    @Test
    void oldResetBindingMovesToTheNewKey() {
        CompoundTag options = new CompoundTag();
        options.putString(OLD_OPTION, "key.keyboard.f6");
        options.putString("key_key.propwash.rescue", "key.keyboard.j");
        KeyDefaults.migrateOptions(options);
        assertEquals(Optional.of("key.keyboard.f6"), options.getString(NEW_OPTION));
        assertFalse(options.contains(OLD_OPTION));
        assertEquals(Optional.of("key.keyboard.j"), options.getString("key_key.propwash.rescue"));
    }

    @Test
    void newBindingIsNeverOverwritten() {
        CompoundTag options = new CompoundTag();
        options.putString(OLD_OPTION, "key.keyboard.f6");
        options.putString(NEW_OPTION, "key.keyboard.r");
        KeyDefaults.migrateOptions(options);
        assertEquals(Optional.of("key.keyboard.r"), options.getString(NEW_OPTION));
        assertFalse(options.contains(OLD_OPTION));
    }

    @Test
    void optionsWithoutTheOldKeyStayUntouched() {
        CompoundTag options = new CompoundTag();
        options.putString("key_key.propwash.rescue", "key.keyboard.j");
        KeyDefaults.migrateOptions(options);
        assertEquals(1, options.size());
        assertFalse(options.contains(NEW_OPTION));
    }

    @Test
    void oldSwitchBindingMovesToReset() {
        DeviceProfile profile = new DeviceProfile();
        profile.switches.put("race_restart", SwitchAssignment.button(23, false));
        profile.sanitize();
        assertEquals(23, profile.binding(SwitchFunction.RESET).index);
        assertFalse(profile.switches.containsKey("race_restart"));
        assertEquals("reset", DeviceProfile.key(SwitchFunction.RESET));
    }

    @Test
    void existingResetSwitchWinsOverTheOldOne() {
        DeviceProfile profile = new DeviceProfile();
        profile.switches.put("race_restart", SwitchAssignment.button(23, false));
        profile.switches.put("reset", SwitchAssignment.button(7, false));
        profile.sanitize();
        assertEquals(7, profile.binding(SwitchFunction.RESET).index);
        assertFalse(profile.switches.containsKey("race_restart"));
    }
}
