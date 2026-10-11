package de.lardtom.propwash.input;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceSetupDefaultsTest {
    private static final DeviceChooser.Candidate PAD = new DeviceChooser.Candidate("pad", "Xbox Wireless Controller", true);
    private static final DeviceChooser.Candidate BOXER = new DeviceChooser.Candidate("boxer", "OpenTX Radiomaster Boxer Joystick", false, true);
    private static final DeviceChooser.Candidate POCKET = new DeviceChooser.Candidate("pocket", "EdgeTX Radiomaster Pocket Joystick", false, true);
    private static final List<DeviceChooser.Candidate> ALL = List.of(PAD, BOXER, POCKET);

    private static InputConfig configWith(String... guids) {
        InputConfig config = new InputConfig();
        for (String guid : guids) {
            DeviceProfile profile = ProfileDefaults.radio(guid, 8);
            profile.setupDone = true;
            config.profiles.put(guid, profile);
        }
        return config;
    }

    @Test
    void autoPicksTheMostRecentlyUsedCompletedDevice() {
        InputConfig config = configWith("pocket", "pad");
        assertEquals(0, DeviceChooser.pick(ALL, "", null, i -> false, config::usageRank));
        config.touch("pocket");
        assertEquals(2, DeviceChooser.pick(ALL, "", null, i -> false, config::usageRank));
        assertEquals(1, DeviceChooser.pick(ALL, "boxer", null, i -> false, config::usageRank));
        assertEquals(0, DeviceChooser.pick(ALL, "", "pad", i -> false, config::usageRank));
        assertEquals(0, DeviceChooser.pick(ALL, "", null, i -> i == 2, config::usageRank));
        assertEquals(-1, DeviceChooser.pick(ALL, InputConfig.KEYBOARD_GUID, null, i -> false, config::usageRank));
    }

    @Test
    void completedGamepadBeatsUnfinishedRadioButRadioStillBeatsUnfinishedGamepad() {
        InputConfig config = configWith("pad");
        assertEquals(0, DeviceChooser.pick(List.of(PAD, BOXER), "", null, i -> false, config::usageRank));
        InputConfig fresh = new InputConfig();
        fresh.profiles.put("pad", ProfileDefaults.gamepad("Pad"));
        assertEquals(1, DeviceChooser.pick(List.of(PAD, BOXER), "", null, i -> false, fresh::usageRank));
        assertEquals(1, DeviceChooser.pick(List.of(PAD, BOXER), ""));
    }

    @Test
    void usageRankFollowsProfileOrderAndIgnoresUnfinishedProfiles() {
        InputConfig config = configWith("a", "b");
        config.profiles.put("c", ProfileDefaults.radio("C", 8));
        assertEquals(0, config.usageRank("a"));
        assertEquals(1, config.usageRank("b"));
        assertEquals(-1, config.usageRank("c"));
        assertEquals(-1, config.usageRank("missing"));
        config.touch("a");
        assertEquals(2, config.usageRank("a"));
        assertTrue(config.usageRank("a") > config.usageRank("b"));
    }

    @Test
    void setupStateSurvivesCopyMergeAndJson() {
        InputConfig config = configWith("boxer");
        config.profiles.put("pad", ProfileDefaults.gamepad("Pad"));
        config.neverAskFor("wheel");
        InputConfig copy = config.copy();
        assertTrue(copy.profile("boxer").setupDone);
        assertFalse(copy.profile("pad").setupDone);
        assertEquals(List.of("wheel"), copy.neverAsk);

        InputConfig tabEdit = config.copy();
        tabEdit.profile("boxer").setupDone = false;
        tabEdit.profile("boxer").deadband = 0.1F;
        InputConfig merged = config.copy();
        merged.mergeReceiverFrom(tabEdit);
        merged.mergeSwitchesFrom(tabEdit);
        assertTrue(merged.profile("boxer").setupDone);
        assertEquals(0.1F, merged.profile("boxer").deadband, 1.0E-6F);

        DeviceProfile reset = merged.profile("boxer");
        reset.copyReceiverFrom(ProfileDefaults.reset(reset, false, 8));
        assertTrue(reset.setupDone);

        Gson gson = new Gson();
        InputConfig back = gson.fromJson(gson.toJson(config), InputConfig.class);
        back.sanitize();
        assertEquals(SetupStatus.COMPLETED, back.setupStatus("boxer"));
        assertEquals(SetupStatus.INCOMPLETE, back.setupStatus("pad"));
        assertEquals(SetupStatus.NEVER, back.setupStatus("wheel"));
    }

    @Test
    void oldCalibratedProfilesCountAsSetUpOnce() {
        Gson gson = new Gson();
        InputConfig legacy = new InputConfig();
        DeviceProfile calibrated = ProfileDefaults.radio("Boxer", 8);
        calibrated.roll.min = -0.93F;
        calibrated.roll.max = 0.97F;
        legacy.profiles.put("calibrated", calibrated);
        legacy.profiles.put("plain", ProfileDefaults.radio("Pocket", 8));
        legacy.profiles.put("pad", ProfileDefaults.gamepad("Pad"));
        String json = gson.toJson(legacy).replace("\"profileVersion\":0", "\"profileVersion\":1").replace(",\"setupDone\":false", "");
        assertFalse(json.contains("setupDone"));

        InputConfig loaded = gson.fromJson(json, InputConfig.class);
        loaded.sanitize();
        assertEquals(InputConfig.PROFILE_VERSION, loaded.profileVersion);
        assertEquals(SetupStatus.COMPLETED, loaded.setupStatus("calibrated"));
        assertEquals(SetupStatus.INCOMPLETE, loaded.setupStatus("plain"));
        assertEquals(SetupStatus.INCOMPLETE, loaded.setupStatus("pad"));
        assertEquals(List.of(), loaded.neverAsk);

        loaded.profile("calibrated").setupDone = false;
        InputConfig again = gson.fromJson(gson.toJson(loaded), InputConfig.class);
        again.sanitize();
        assertEquals(SetupStatus.INCOMPLETE, again.setupStatus("calibrated"));
    }

    @Test
    void radioDefaultsAreAetrWithSwitchesOnChannelsFiveToEight() {
        DeviceProfile radio = ProfileDefaults.create("EdgeTX Radiomaster Boxer Joystick", false, 8);
        assertFalse(radio.gamepadLayout);
        assertFalse(radio.setupDone);
        assertEquals(0, radio.roll.axis);
        assertEquals(1, radio.pitch.axis);
        assertEquals(2, radio.throttle.axis);
        assertEquals(3, radio.yaw.axis);
        assertEquals(4, radio.binding(SwitchFunction.ARM).index);
        assertEquals(5, radio.binding(SwitchFunction.ANGLE).index);
        assertEquals(5, radio.binding(SwitchFunction.HORIZON).index);
        assertEquals(6, radio.binding(SwitchFunction.BEEPER).index);
        assertEquals(7, radio.binding(SwitchFunction.RESCUE).index);
        assertEquals(1700, radio.binding(SwitchFunction.ARM).lowMicros());
        assertEquals(2100, radio.binding(SwitchFunction.ARM).highMicros());
        for (SwitchFunction function : SwitchFunction.values()) {
            assertNull(radio.stickConflict(radio.binding(function)), function.name());
        }

        SwitchEvaluator evaluator = new SwitchEvaluator();
        boolean[] buttons = new boolean[0];
        float[] axes = {0.0F, 0.0F, -1.0F, 0.0F, -1.0F, -1.0F, -1.0F, -1.0F};
        int bits = evaluator.evaluate(radio, axes, buttons);
        for (SwitchFunction function : SwitchFunction.values()) {
            assertFalse(SwitchEvaluator.isSet(bits, function), function.name());
        }
        axes[4] = 1.0F;
        axes[5] = 0.0F;
        bits = evaluator.evaluate(radio, axes, buttons);
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.ARM));
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.HORIZON));
        assertFalse(SwitchEvaluator.isSet(bits, SwitchFunction.ANGLE));
        axes[5] = 1.0F;
        axes[6] = 1.0F;
        axes[7] = 1.0F;
        bits = evaluator.evaluate(radio, axes, buttons);
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.ANGLE));
        assertFalse(SwitchEvaluator.isSet(bits, SwitchFunction.HORIZON));
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.BEEPER));
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.RESCUE));

        DeviceProfile six = ProfileDefaults.radio("Tiny", 6);
        assertTrue(six.binding(SwitchFunction.HORIZON).bound());
        assertFalse(six.binding(SwitchFunction.BEEPER).bound());
        assertFalse(six.binding(SwitchFunction.RESCUE).bound());
    }

    @Test
    void commonRadiosAreRecognizedByName() {
        for (String name : List.of("Radiomaster Pocket", "Jumper T-Lite", "RadioLink AT9S", "FrSky Taranis X9D", "BETAFPV LiteRadio 3",
                "TBS Tango 2", "Spektrum RC Simulator", "Radiomaster MT12")) {
            assertTrue(ProfileDefaults.looksLikeRadio(name), name);
        }
        for (String name : List.of("Xbox Wireless Controller", "DualSense Wireless Controller", "Thrustmaster T.16000M", "Logitech G29")) {
            assertFalse(ProfileDefaults.looksLikeRadio(name), name);
        }
        assertTrue(ProfileDefaults.create("Xbox Wireless Controller", true, 6).gamepadLayout);
        assertFalse(ProfileDefaults.create("Radiomaster Pocket", true, 8).gamepadLayout);
    }
}
