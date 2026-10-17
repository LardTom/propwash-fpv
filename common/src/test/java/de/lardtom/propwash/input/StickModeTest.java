package de.lardtom.propwash.input;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StickModeTest {
    private record Layout(StickChannel leftVertical, StickChannel leftHorizontal, StickChannel rightVertical, StickChannel rightHorizontal) {
    }

    private static final Map<StickMode, Layout> TABLE = new EnumMap<>(StickMode.class);

    static {
        TABLE.put(StickMode.MODE_1, new Layout(StickChannel.PITCH, StickChannel.YAW, StickChannel.THROTTLE, StickChannel.ROLL));
        TABLE.put(StickMode.MODE_2, new Layout(StickChannel.THROTTLE, StickChannel.YAW, StickChannel.PITCH, StickChannel.ROLL));
        TABLE.put(StickMode.MODE_3, new Layout(StickChannel.PITCH, StickChannel.ROLL, StickChannel.THROTTLE, StickChannel.YAW));
        TABLE.put(StickMode.MODE_4, new Layout(StickChannel.THROTTLE, StickChannel.ROLL, StickChannel.PITCH, StickChannel.YAW));
    }

    @ParameterizedTest
    @EnumSource(StickMode.class)
    void layoutMatchesTheFourModes(StickMode mode) {
        Layout expected = TABLE.get(mode);
        assertEquals(expected.leftVertical(), mode.channel(true, true));
        assertEquals(expected.leftHorizontal(), mode.channel(true, false));
        assertEquals(expected.rightVertical(), mode.channel(false, true));
        assertEquals(expected.rightHorizontal(), mode.channel(false, false));
        for (StickChannel channel : StickChannel.values()) {
            assertEquals(channel, mode.channel(mode.left(channel), StickMode.vertical(channel)), mode + " " + channel);
        }
        assertEquals(mode, StickMode.byNumber(mode.number()));
        assertEquals("Mode " + mode.number(), "Mode " + mode.name().substring(5));
    }

    @ParameterizedTest
    @EnumSource(StickMode.class)
    void placingPutsEveryChannelOnItsStick(StickMode mode) {
        StickMode.Sticks sticks = mode.place(0.1F, 0.2F, 0.3F, 0.4F);
        Map<StickChannel, Float> value = Map.of(StickChannel.ROLL, 0.1F, StickChannel.PITCH, 0.2F, StickChannel.YAW, 0.3F, StickChannel.THROTTLE, 0.4F);
        assertEquals(value.get(mode.channel(true, false)), sticks.leftX());
        assertEquals(value.get(mode.channel(true, true)), sticks.leftY());
        assertEquals(value.get(mode.channel(false, false)), sticks.rightX());
        assertEquals(value.get(mode.channel(false, true)), sticks.rightY());
        StickMode.Sticks mode2 = StickMode.MODE_2.place(0.1F, 0.2F, 0.3F, 0.4F);
        assertEquals(sticks, mode.fromMode2(mode2.leftX(), mode2.leftY(), mode2.rightX(), mode2.rightY()));
    }

    @Test
    void modeTwoIsTheDefaultAndKeepsTodaysLayout() {
        assertSame(StickMode.MODE_2, StickMode.DEFAULT);
        assertSame(StickMode.MODE_2, new InputConfig().stickMode());
        assertEquals(new StickMode.Sticks(1.0F, 2.0F, 3.0F, 4.0F), StickMode.MODE_2.fromMode2(1.0F, 2.0F, 3.0F, 4.0F));
        assertSame(StickMode.MODE_2, StickMode.byNumber(0));
        assertSame(StickMode.MODE_2, StickMode.byNumber(5));
        assertEquals("a.b", StickMode.MODE_2.variantKey("a.b"));
        assertEquals("a.b.mode1", StickMode.MODE_1.variantKey("a.b"));
        assertEquals("p.left", StickMode.MODE_1.sideKey("p", StickChannel.PITCH));
        assertEquals("p.right", StickMode.MODE_2.sideKey("p", StickChannel.PITCH));
    }

    @Test
    void throttleAndPitchNeverShareAStickNorDoYawAndRoll() {
        for (StickMode mode : StickMode.values()) {
            assertNotEquals(mode.left(StickChannel.THROTTLE), mode.left(StickChannel.PITCH));
            assertNotEquals(mode.left(StickChannel.YAW), mode.left(StickChannel.ROLL));
        }
    }

    @ParameterizedTest
    @EnumSource(StickMode.class)
    void gamepadDefaultsFollowTheMode(StickMode mode) {
        DeviceProfile pad = ProfileDefaults.gamepad("Pad", mode);
        Layout layout = TABLE.get(mode);
        assertEquals(ProfileDefaults.PAD_LEFT_Y, pad.channel(layout.leftVertical()).axis);
        assertEquals(ProfileDefaults.PAD_LEFT_X, pad.channel(layout.leftHorizontal()).axis);
        assertEquals(ProfileDefaults.PAD_RIGHT_Y, pad.channel(layout.rightVertical()).axis);
        assertEquals(ProfileDefaults.PAD_RIGHT_X, pad.channel(layout.rightHorizontal()).axis);
        assertTrue(pad.pitch.invert);
        assertTrue(pad.throttle.invert);
        assertFalse(pad.roll.invert);
        assertFalse(pad.yaw.invert);
        assertTrue(ProfileDefaults.padSticksMatch(pad, mode));
        DeviceProfile reference = ProfileDefaults.gamepad("Pad");
        assertEquals(reference.throttleMode, pad.throttleMode);
        assertEquals(reference.switches, pad.switches);
        assertTrue(ProfileDefaults.create("Xbox Wireless Controller", true, 6, mode).gamepadLayout);
        assertTrue(ProfileDefaults.padSticksMatch(ProfileDefaults.create("Xbox Wireless Controller", true, 6, mode), mode));
    }

    @Test
    void modeTwoGamepadStaysExactlyAsBefore() {
        DeviceProfile pad = ProfileDefaults.gamepad("Pad", StickMode.MODE_2);
        assertEquals(new ChannelMapping(ProfileDefaults.PAD_RIGHT_X, false), pad.roll);
        assertEquals(new ChannelMapping(ProfileDefaults.PAD_RIGHT_Y, true), pad.pitch);
        assertEquals(new ChannelMapping(ProfileDefaults.PAD_LEFT_X, false), pad.yaw);
        assertEquals(new ChannelMapping(ProfileDefaults.PAD_LEFT_Y, true), pad.throttle);
    }

    @ParameterizedTest
    @EnumSource(StickMode.class)
    void radiosKeepAetrInEveryMode(StickMode mode) {
        DeviceProfile radio = ProfileDefaults.create("EdgeTX Radiomaster Boxer Joystick", true, 8, mode);
        DeviceProfile reference = ProfileDefaults.radio("EdgeTX Radiomaster Boxer Joystick", 8);
        assertFalse(radio.gamepadLayout);
        for (StickChannel channel : StickChannel.values()) {
            assertEquals(reference.channel(channel), radio.channel(channel), mode + " " + channel);
        }
        assertEquals(ProfileDefaults.PadRemap.NOT_GAMEPAD, ProfileDefaults.remapPadSticks(radio, StickMode.MODE_2, mode));
    }

    @Test
    void switchingTheModeMovesUntouchedGamepadsAndKeepsTheirCalibration() {
        DeviceProfile pad = ProfileDefaults.gamepad("Pad");
        pad.throttle.min = -0.9F;
        pad.throttle.max = 0.95F;
        pad.pitch.center = 0.02F;
        assertEquals(ProfileDefaults.PadRemap.REMAPPED, ProfileDefaults.remapPadSticks(pad, StickMode.MODE_2, StickMode.MODE_1));
        assertTrue(ProfileDefaults.padSticksMatch(pad, StickMode.MODE_1));
        assertEquals(ProfileDefaults.PAD_RIGHT_Y, pad.throttle.axis);
        assertEquals(ProfileDefaults.PAD_LEFT_Y, pad.pitch.axis);
        assertEquals(0.02F, pad.throttle.center);
        assertEquals(-0.9F, pad.pitch.min);
        assertEquals(0.95F, pad.pitch.max);
        assertEquals(ProfileDefaults.PadRemap.ALREADY, ProfileDefaults.remapPadSticks(pad, StickMode.MODE_2, StickMode.MODE_1));
        assertEquals(ProfileDefaults.PadRemap.REMAPPED, ProfileDefaults.remapPadSticks(pad, StickMode.MODE_1, StickMode.MODE_2));
        assertEquals(ProfileDefaults.gamepad("Pad").throttle.axis, pad.throttle.axis);
        assertEquals(-0.9F, pad.throttle.min);
    }

    @Test
    void customisedGamepadsStayUntilAskedFor() {
        DeviceProfile pad = ProfileDefaults.gamepad("Pad");
        pad.yaw = new ChannelMapping(ProfileDefaults.PAD_LEFT_TRIGGER, false);
        DeviceProfile before = pad.copy();
        assertEquals(ProfileDefaults.PadRemap.CUSTOM, ProfileDefaults.remapPadSticks(pad, StickMode.MODE_2, StickMode.MODE_3));
        for (StickChannel channel : StickChannel.values()) {
            assertEquals(before.channel(channel), pad.channel(channel));
        }
        ProfileDefaults.applyPadSticks(pad, StickMode.MODE_3);
        assertTrue(ProfileDefaults.padSticksMatch(pad, StickMode.MODE_3));
    }

    @Test
    void configChangeRemapsOnlyDefaultGamepadsAndIsPersonal() {
        InputConfig config = new InputConfig();
        config.profiles.put("pad", ProfileDefaults.gamepad("Pad"));
        DeviceProfile custom = ProfileDefaults.gamepad("Custom");
        custom.roll = new ChannelMapping(ProfileDefaults.PAD_LEFT_TRIGGER, false);
        config.profiles.put("custom", custom);
        config.profiles.put("radio", ProfileDefaults.radio("Boxer", 8));
        InputConfig.StickModeChange change = config.changeStickMode(StickMode.MODE_4);
        assertEquals(StickMode.MODE_2, change.from());
        assertEquals(StickMode.MODE_4, change.to());
        assertEquals(List.of("pad"), change.remapped());
        assertTrue(change.customised("custom"));
        assertTrue(ProfileDefaults.padSticksMatch(config.profile("pad"), StickMode.MODE_4));
        assertEquals(ProfileDefaults.PAD_LEFT_TRIGGER, config.profile("custom").roll.axis);
        assertEquals(0, config.profile("radio").roll.axis);
        assertSame(StickMode.MODE_4, config.stickMode());
        assertSame(StickMode.MODE_4, config.copy().stickMode());
        InputConfig merged = new InputConfig();
        merged.mergeReceiverFrom(config);
        assertSame(StickMode.MODE_4, merged.stickMode());
        assertTrue(ProfileDefaults.padSticksMatch(config.profileOrCreate("new", "Xbox Wireless Controller", true, 6), StickMode.MODE_4));
    }

    @Test
    void storedModeSurvivesJsonAndBrokenValuesFallBackToModeTwo() {
        Gson gson = new Gson();
        InputConfig config = new InputConfig();
        config.stickMode = StickMode.MODE_3;
        InputConfig read = gson.fromJson(gson.toJson(config), InputConfig.class);
        read.sanitize();
        assertSame(StickMode.MODE_3, read.stickMode);
        InputConfig broken = gson.fromJson("{\"stickMode\":\"MODE_9\"}", InputConfig.class);
        broken.sanitize();
        assertSame(StickMode.MODE_2, broken.stickMode);
        InputConfig old = gson.fromJson("{\"invertPitch\":true}", InputConfig.class);
        old.sanitize();
        assertSame(StickMode.MODE_2, old.stickMode);
    }
}
