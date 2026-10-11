package de.lardtom.propwash.input;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileMigrationTest {
    private static final float[] NO_AXES = new float[0];

    @Test
    void gamepadRescueLatchesAndAngleStartsOn() {
        DeviceProfile pad = ProfileDefaults.gamepad("Pad");
        SwitchAssignment rescue = pad.binding(SwitchFunction.RESCUE);
        assertEquals(SwitchSource.BUTTON, rescue.source);
        assertTrue(rescue.toggle);
        assertFalse(rescue.startOn);
        assertTrue(pad.binding(SwitchFunction.ANGLE).startOn);
        assertFalse(pad.binding(SwitchFunction.ARM).startOn);
    }

    @Test
    void rescueStaysActiveAfterReleasingTheButton() {
        DeviceProfile pad = ProfileDefaults.gamepad("Pad");
        SwitchEvaluator evaluator = new SwitchEvaluator();
        boolean[] released = new boolean[ProfileDefaults.PAD_BUTTONS];
        boolean[] pressed = released.clone();
        pressed[ProfileDefaults.PAD_EAST] = true;
        evaluator.evaluate(pad, NO_AXES, released);
        assertTrue(SwitchEvaluator.isSet(evaluator.evaluate(pad, NO_AXES, pressed), SwitchFunction.RESCUE));
        assertTrue(SwitchEvaluator.isSet(evaluator.evaluate(pad, NO_AXES, released), SwitchFunction.RESCUE));
        evaluator.evaluate(pad, NO_AXES, pressed);
        assertFalse(SwitchEvaluator.isSet(evaluator.evaluate(pad, NO_AXES, released), SwitchFunction.RESCUE));
    }

    @Test
    void angleToggleStartsOnAndReturnsToItsStartStateAfterReset() {
        DeviceProfile pad = ProfileDefaults.gamepad("Pad");
        SwitchEvaluator evaluator = new SwitchEvaluator();
        boolean[] released = new boolean[ProfileDefaults.PAD_BUTTONS];
        boolean[] pressed = released.clone();
        pressed[ProfileDefaults.PAD_LEFT_SHOULDER] = true;
        int bits = evaluator.evaluate(pad, NO_AXES, released);
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.ANGLE));
        assertFalse(SwitchEvaluator.isSet(bits, SwitchFunction.ARM));
        evaluator.evaluate(pad, NO_AXES, pressed);
        assertFalse(SwitchEvaluator.isSet(evaluator.evaluate(pad, NO_AXES, released), SwitchFunction.ANGLE));
        evaluator.reset();
        assertTrue(SwitchEvaluator.isSet(evaluator.evaluate(pad, NO_AXES, pressed), SwitchFunction.ANGLE));
    }

    @Test
    void startOnOnlySurvivesOnToggleButtons() {
        SwitchAssignment hold = SwitchAssignment.button(3, false).startingOn();
        assertFalse(hold.startOn);
        SwitchAssignment toggle = SwitchAssignment.toggleButton(3).startingOn();
        toggle.toggle = false;
        toggle.sanitize();
        assertFalse(toggle.startOn);
        SwitchAssignment kept = SwitchAssignment.toggleButton(3).startingOn();
        kept.sanitize();
        assertTrue(kept.startOn);
        assertFalse(kept.equals(SwitchAssignment.toggleButton(3)));
        assertTrue(kept.copy().equals(kept));
    }

    @Test
    void oldGamepadProfilesAreMigratedOnce() {
        Gson gson = new Gson();
        DeviceProfile old = ProfileDefaults.gamepad("Pad");
        old.setBinding(SwitchFunction.RESCUE, SwitchAssignment.button(ProfileDefaults.PAD_EAST, false));
        old.setBinding(SwitchFunction.ANGLE, SwitchAssignment.toggleButton(ProfileDefaults.PAD_LEFT_SHOULDER));
        DeviceProfile custom = ProfileDefaults.gamepad("Custom");
        custom.setBinding(SwitchFunction.RESCUE, SwitchAssignment.button(ProfileDefaults.PAD_NORTH, false));
        DeviceProfile radio = ProfileDefaults.radio("Radio", 8);
        radio.setBinding(SwitchFunction.RESCUE, SwitchAssignment.button(ProfileDefaults.PAD_EAST, false));
        InputConfig legacy = new InputConfig();
        legacy.profiles.put("old", old);
        legacy.profiles.put("custom", custom);
        legacy.profiles.put("radio", radio);
        String json = gson.toJson(legacy).replace("\"profileVersion\":0,", "");
        assertFalse(json.contains("profileVersion"));

        InputConfig loaded = gson.fromJson(json, InputConfig.class);
        loaded.sanitize();
        assertEquals(InputConfig.PROFILE_VERSION, loaded.profileVersion);
        assertTrue(loaded.profile("old").binding(SwitchFunction.RESCUE).toggle);
        assertTrue(loaded.profile("old").binding(SwitchFunction.ANGLE).startOn);
        assertFalse(loaded.profile("custom").binding(SwitchFunction.RESCUE).toggle);
        assertFalse(loaded.profile("radio").binding(SwitchFunction.RESCUE).toggle);

        loaded.profile("old").setBinding(SwitchFunction.RESCUE, SwitchAssignment.button(ProfileDefaults.PAD_EAST, false));
        InputConfig again = gson.fromJson(gson.toJson(loaded), InputConfig.class);
        again.sanitize();
        assertFalse(again.profile("old").binding(SwitchFunction.RESCUE).toggle);
        assertEquals(loaded.profileVersion, again.copy().profileVersion);
    }

    @Test
    void profileLimitEvictsOldestButKeepsSelectedAndNewest() {
        InputConfig config = new InputConfig();
        List<String> guids = new ArrayList<>();
        for (int i = 0; i < InputConfig.MAX_PROFILES + 5; i++) {
            String guid = String.format("%032d", i);
            guids.add(guid);
            config.profiles.put(guid, ProfileDefaults.radio("R" + i, 4));
        }
        config.selectedGuid = guids.getFirst();
        DeviceProfile newest = ProfileDefaults.gamepad("New");
        config.profiles.put("new", newest);
        config.sanitize();
        assertEquals(InputConfig.MAX_PROFILES, config.profiles.size());
        assertNotNull(config.profile(guids.getFirst()));
        assertSame(newest, config.profile("new"));
        assertNull(config.profile(guids.get(1)));
        assertNull(config.profile(guids.get(6)));
        assertNotNull(config.profile(guids.get(7)));
        assertTrue(config.isNewest("new"));
    }

    @Test
    void touchMovesProfileToTheEnd() {
        InputConfig config = new InputConfig();
        config.profiles.put("a", ProfileDefaults.radio("A", 4));
        config.profiles.put("b", ProfileDefaults.radio("B", 4));
        assertFalse(config.isNewest("a"));
        config.touch("a");
        assertTrue(config.isNewest("a"));
        config.touch("missing");
        assertEquals(2, config.profiles.size());
    }

    @Test
    void keyboardSelectionIsKeptBySanitize() {
        InputConfig config = new InputConfig();
        config.selectedGuid = InputConfig.KEYBOARD_GUID;
        config.sanitize();
        assertTrue(config.keyboardSelected());
        assertTrue(config.copy().keyboardSelected());
    }
}
