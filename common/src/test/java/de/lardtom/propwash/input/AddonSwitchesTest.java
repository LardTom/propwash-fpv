package de.lardtom.propwash.input;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchRegistry;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.dev.example.ExampleSwitches;
import de.lardtom.propwash.input.addon.AccessoryActionHints;
import de.lardtom.propwash.input.addon.AddonBindings;
import de.lardtom.propwash.input.addon.AddonIds;
import de.lardtom.propwash.input.addon.AddonInputMath;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.addon.AddonSwitchEvaluator;
import de.lardtom.propwash.input.addon.AxisAssignment;
import de.lardtom.propwash.input.addon.client.AddonTestRadio;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.net.AddonAxisC2S;
import de.lardtom.propwash.input.net.AddonFeaturesS2C;
import de.lardtom.propwash.input.net.AddonSwitchC2S;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonSwitchesTest {
    private static final String NS = "addonswitchestest";
    private static final Identifier REGISTERED = id("registered");
    private static final Identifier MOMENTARY = id("momentary");
    private static final Identifier TILT = id("tilt");

    @BeforeAll
    static void register() {
        if (AddonInputs.switchSpec(REGISTERED) == null) {
            Api17Registries.SWITCHES.register(SwitchSpec.builder(REGISTERED).toggle(true).latchesOnGamepad(true).serverSync(true).build());
            Api17Registries.SWITCHES.register(SwitchSpec.builder(MOMENTARY).toggle(false).build());
            Api17Registries.SWITCHES.registerAxis(AxisSpec.builder(TILT).center(0.0).keyRateDps(60).build());
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }

    private static SwitchSpec toggleSpec(String path, boolean latches) {
        return SwitchSpec.builder(id(path)).toggle(true).latchesOnGamepad(latches).build();
    }

    private static SwitchSpec momentarySpec(String path) {
        return SwitchSpec.builder(id(path)).toggle(false).build();
    }

    @Test
    void toggleSwitchLatchesAndPushButtonHolds() {
        SwitchSpec toggle = toggleSpec("toggle", false);
        SwitchSpec push = momentarySpec("push");
        DeviceProfile profile = new DeviceProfile();
        profile.setAddonBinding(toggle.id(), SwitchAssignment.toggleButton(0));
        SwitchAssignment pushBinding = SwitchAssignment.toggleButton(1);
        profile.setAddonBinding(push.id(), pushBinding);
        List<SwitchSpec> specs = List.of(toggle, push);
        AddonSwitchEvaluator evaluator = new AddonSwitchEvaluator();
        assertEquals(0L, evaluator.evaluate(profile, specs, new float[0], new boolean[]{false, false}));
        assertEquals(0b11L, evaluator.evaluate(profile, specs, new float[0], new boolean[]{true, true}));
        assertEquals(0b01L, evaluator.evaluate(profile, specs, new float[0], new boolean[]{false, false}));
        assertEquals(0b01L, evaluator.evaluate(profile, specs, new float[0], new boolean[]{false, false}));
        assertEquals(0b10L, evaluator.evaluate(profile, specs, new float[0], new boolean[]{true, true}));
        assertEquals(0b00L, evaluator.evaluate(profile, specs, new float[0], new boolean[]{false, false}));

        assertEquals(0L, evaluator.keys(specs, new boolean[]{false, false}, new int[]{0, 0}));
        assertEquals(0b11L, evaluator.keys(specs, new boolean[]{true, true}, new int[]{1, 1}));
        assertEquals(0b01L, evaluator.keys(specs, new boolean[]{false, false}, new int[]{0, 0}));
        assertEquals(0b10L, evaluator.keys(specs, new boolean[]{false, false}, new int[]{1, 1}));
        assertEquals(0b00L, evaluator.keys(specs, new boolean[]{false, false}, new int[]{0, 0}));
        assertEquals(0b01L, evaluator.keys(specs, new boolean[]{false, false}, new int[]{3, 0}));
    }

    @Test
    void registeredPushButtonNeverLatchesAfterNormalizing() {
        DeviceProfile profile = new DeviceProfile();
        SwitchAssignment latched = SwitchAssignment.toggleButton(2).startingOn();
        profile.setAddonBinding(MOMENTARY, latched);
        profile.sanitize();
        SwitchAssignment kept = profile.addonBinding(MOMENTARY);
        assertTrue(kept.bound());
        assertFalse(kept.toggle);
        assertFalse(kept.startOn);
    }

    @Test
    void gamepadButtonsLatchOnlyWhenTheSwitchAsksForIt() {
        SwitchSources.Choice button = new SwitchSources.Choice(SwitchSource.BUTTON, 3);
        SwitchSources.Choice axis = new SwitchSources.Choice(SwitchSource.AXIS, 5);
        SwitchSpec latching = toggleSpec("latching", true);
        SwitchSpec plain = toggleSpec("plain", false);
        SwitchSpec push = momentarySpec("push");
        assertTrue(AddonBindings.apply(SwitchAssignment.none(), button, latching, true).toggle);
        assertFalse(AddonBindings.apply(SwitchAssignment.none(), button, latching, false).toggle);
        assertFalse(AddonBindings.apply(SwitchAssignment.none(), button, plain, true).toggle);
        assertFalse(AddonBindings.apply(SwitchAssignment.none(), button, push, true).toggle);
        SwitchAssignment onAxis = AddonBindings.apply(SwitchAssignment.none(), axis, latching, true);
        assertFalse(onAxis.toggle);
        assertEquals(SwitchSource.AXIS, onAxis.source);
        assertEquals(SwitchSources.DEFAULT_LOW_US, onAxis.lowMicros());
        assertEquals(SwitchSources.DEFAULT_HIGH_US, onAxis.highMicros());
        assertEquals(SwitchSource.NONE, AddonBindings.apply(onAxis, SwitchSources.Choice.NONE, latching, true).source);

        SwitchAssignment detected = SwitchAssignment.button(4, true);
        AddonBindings.applyButtonDefaults(latching, detected, true);
        assertTrue(detected.toggle);
        assertFalse(detected.invert);
        SwitchAssignment pushed = SwitchAssignment.toggleButton(4);
        AddonBindings.applyButtonDefaults(push, pushed, true);
        assertFalse(pushed.toggle);
    }

    @Test
    void simulatedGamepadUsesTheGamepadLayoutAndLatchesAddonButtons() {
        DeviceProfile profile = ProfileDefaults.gamepad("Testpad");
        SwitchSpec latching = AddonInputs.switchSpec(REGISTERED);
        SimulatedRadio pad = new SimulatedRadio("pad", "Testpad", profile, 2, 3).asGamepad();
        assertTrue(pad.device().gamepad());
        RawInput raw = pad.raw();
        assertTrue(raw.gamepadCapable());
        assertTrue(raw.usesGamepadLayout(profile));
        assertEquals(ProfileDefaults.PAD_AXES, raw.axes(profile).length);
        assertEquals(ProfileDefaults.PAD_BUTTONS, raw.buttons(profile).length);
        assertEquals(ProfileDefaults.PAD_BUTTONS, raw.buttonCount());
        pad.setButton(ProfileDefaults.PAD_NORTH, true);
        pad.setAxis(ProfileDefaults.PAD_RIGHT_TRIGGER, 0.5F);
        RawInput pressed = pad.raw();
        assertTrue(pressed.buttons(profile)[ProfileDefaults.PAD_NORTH]);
        assertEquals(0.5F, pressed.axes(profile)[ProfileDefaults.PAD_RIGHT_TRIGGER]);
        assertArrayEquals(pad.buttons(), pressed.buttons(profile));
        assertArrayEquals(pad.axes(), pressed.axes(profile));
        SwitchSources.Choice north = new SwitchSources.Choice(SwitchSource.BUTTON, ProfileDefaults.PAD_NORTH);
        SwitchAssignment latched = AddonBindings.apply(SwitchAssignment.none(), north, latching, raw.usesGamepadLayout(profile));
        assertTrue(latched.toggle);
        profile.setAddonBinding(REGISTERED, latched);
        List<SwitchSpec> specs = List.of(latching);
        AddonSwitchEvaluator evaluator = new AddonSwitchEvaluator();
        pad.setButton(ProfileDefaults.PAD_NORTH, false);
        assertEquals(0b0L, evaluator.evaluate(profile, specs, pad.raw().axes(profile), pad.raw().buttons(profile)));
        assertEquals(0b1L, evaluator.evaluate(profile, specs, pressed.axes(profile), pressed.buttons(profile)));
        assertEquals(0b1L, evaluator.evaluate(profile, specs, pad.raw().axes(profile), pad.raw().buttons(profile)));
        pad.setButton(ProfileDefaults.PAD_NORTH, true);
        assertEquals(0b0L, evaluator.evaluate(profile, specs, pad.raw().axes(profile), pad.raw().buttons(profile)));

        SimulatedRadio radio = new SimulatedRadio("radio", "Testfunke", profile, ProfileDefaults.PAD_AXES, ProfileDefaults.PAD_BUTTONS);
        assertFalse(radio.device().gamepad());
        assertFalse(radio.raw().gamepadCapable());
        assertFalse(radio.raw().usesGamepadLayout(profile));
        assertFalse(AddonBindings.apply(SwitchAssignment.none(), north, latching, radio.raw().usesGamepadLayout(profile)).toggle);
        SimulatedRadio wide = new SimulatedRadio("wide", "Testpad", profile, 10, 20).asGamepad();
        assertEquals(10, wide.raw().axisCount());
        assertEquals(ProfileDefaults.PAD_AXES, wide.raw().axes(profile).length);
        assertEquals(ProfileDefaults.PAD_BUTTONS, wide.raw().buttons(profile).length);
    }

    @Test
    void apiTestRadioBindsLikeTheAddonsPageAndLatchesOnlyOnTheGamepad() {
        InputConfig empty = new InputConfig();
        empty.sanitize();
        SimulatedRadio pad = AddonTestRadio.device(true);
        assertEquals(AddonTestRadio.GAMEPAD_GUID, pad.guid());
        assertTrue(pad.device().gamepad());
        assertEquals(ProfileDefaults.PAD_AXES, pad.axes().length);
        assertEquals(ProfileDefaults.PAD_BUTTONS, pad.buttons().length);
        assertEquals(0.0F, pad.axes()[ProfileDefaults.PAD_LEFT_Y]);
        assertEquals(-1.0F, pad.axes()[ProfileDefaults.PAD_LEFT_TRIGGER]);
        assertEquals(-1.0F, pad.axes()[ProfileDefaults.PAD_RIGHT_TRIGGER]);
        InputConfig config = AddonTestRadio.withDevice(empty, pad);
        assertNull(empty.profile(AddonTestRadio.GAMEPAD_GUID));
        DeviceProfile profile = config.profile(AddonTestRadio.GAMEPAD_GUID);
        assertTrue(profile != null && profile.gamepadLayout && profile.setupDone);
        assertTrue(pad.raw().usesGamepadLayout(profile));

        SwitchAssignment latched = AddonTestRadio.buttonBinding(pad, config, REGISTERED, ProfileDefaults.PAD_NORTH);
        SwitchAssignment held = AddonTestRadio.buttonBinding(pad, config, MOMENTARY, ProfileDefaults.PAD_WEST);
        assertTrue(latched != null && latched.toggle && latched.source == SwitchSource.BUTTON && latched.index == ProfileDefaults.PAD_NORTH);
        assertTrue(held != null && !held.toggle && held.source == SwitchSource.BUTTON);
        assertNull(AddonTestRadio.buttonBinding(pad, config, id("unknown"), 0));
        assertNull(AddonTestRadio.buttonBinding(pad, config, REGISTERED, ProfileDefaults.PAD_BUTTONS));
        assertNull(AddonTestRadio.buttonBinding(pad, config, REGISTERED, -1));
        assertNull(AddonTestRadio.channelBinding(pad, TILT, 0, 1700, 2000));
        config = AddonTestRadio.withBinding(config, AddonTestRadio.GAMEPAD_GUID, REGISTERED, latched);
        config = AddonTestRadio.withBinding(config, AddonTestRadio.GAMEPAD_GUID, MOMENTARY, held);
        DeviceProfile bound = config.profile(AddonTestRadio.GAMEPAD_GUID);
        assertTrue(bound.addonBinding(REGISTERED).toggle);
        assertFalse(bound.addonBinding(MOMENTARY).toggle);

        List<SwitchSpec> specs = List.of(AddonInputs.switchSpec(REGISTERED), AddonInputs.switchSpec(MOMENTARY));
        AddonSwitchEvaluator evaluator = new AddonSwitchEvaluator();
        long[] seen = new long[5];
        for (int i = 0; i < seen.length; i++) {
            boolean down = i % 2 == 1;
            pad.setButton(ProfileDefaults.PAD_NORTH, down);
            if (down) {
                pad.setButton(ProfileDefaults.PAD_NORTH, false);
                pad.setButton(ProfileDefaults.PAD_NORTH, true);
            }
            seen[i] = evaluator.evaluate(bound, specs, pad.raw().axes(bound), pad.raw().buttons(bound));
        }
        assertArrayEquals(new long[]{0b00L, 0b01L, 0b01L, 0b00L, 0b00L}, seen, "Umschalter rastet je Druck einmal, Prellen innerhalb eines Bildes zählt nicht");
        pad.setButton(ProfileDefaults.PAD_WEST, true);
        assertEquals(0b10L, evaluator.evaluate(bound, specs, pad.raw().axes(bound), pad.raw().buttons(bound)));
        pad.setButton(ProfileDefaults.PAD_WEST, false);
        assertEquals(0b00L, evaluator.evaluate(bound, specs, pad.raw().axes(bound), pad.raw().buttons(bound)));

        SimulatedRadio radio = AddonTestRadio.device(false);
        assertEquals(AddonTestRadio.RADIO_GUID, radio.guid());
        assertFalse(radio.device().gamepad());
        assertEquals(AddonTestRadio.RADIO_CHANNELS, radio.axes().length);
        assertEquals(AddonTestRadio.RADIO_BUTTONS, radio.buttons().length);
        assertEquals(0.0F, radio.axes()[0]);
        assertEquals(-1.0F, radio.axes()[2]);
        assertEquals(-1.0F, radio.axes()[AddonTestRadio.RADIO_CHANNELS - 1]);
        InputConfig both = AddonTestRadio.withDevice(config, radio);
        DeviceProfile radioProfile = both.profile(AddonTestRadio.RADIO_GUID);
        assertTrue(radioProfile != null && !radioProfile.gamepadLayout && radioProfile.setupDone);
        assertFalse(radio.raw().usesGamepadLayout(radioProfile));
        SwitchAssignment onRadio = AddonTestRadio.buttonBinding(radio, both, REGISTERED, 0);
        assertTrue(onRadio != null && !onRadio.toggle, "an der Funke rastet ein Knopf nicht ein");
        SwitchAssignment range = AddonTestRadio.channelBinding(radio, REGISTERED, 6, 1700, 2000);
        assertTrue(range != null && range.source == SwitchSource.AXIS && range.index == 6);
        assertEquals(1700, range.lowMicros());
        assertEquals(2000, range.highMicros());
        assertNull(AddonTestRadio.channelBinding(radio, REGISTERED, AddonTestRadio.RADIO_CHANNELS, 1700, 2000));
        assertNull(AddonTestRadio.channelBinding(radio, REGISTERED, 6, 1800, 1700));
        assertNull(AddonTestRadio.channelBinding(radio, REGISTERED, 6, ChannelMath.RANGE_MIN_US - 1, 1300));
        assertNull(AddonTestRadio.channelBinding(radio, id("unknown"), 6, 1000, 1300));
        both = AddonTestRadio.withBinding(both, AddonTestRadio.RADIO_GUID, REGISTERED, range);
        DeviceProfile ranged = both.profile(AddonTestRadio.RADIO_GUID);
        AddonSwitchEvaluator onChannel = new AddonSwitchEvaluator();
        List<SwitchSpec> one = List.of(AddonInputs.switchSpec(REGISTERED));
        radio.setMicros(6, 2000);
        assertEquals(0b1L, onChannel.evaluate(ranged, one, radio.axes(), radio.buttons()));
        radio.setMicros(6, 1000);
        assertEquals(0b0L, onChannel.evaluate(ranged, one, radio.axes(), radio.buttons()));

        InputConfig cleared = AddonTestRadio.withoutDevices(both);
        assertNull(cleared.profile(AddonTestRadio.GAMEPAD_GUID));
        assertNull(cleared.profile(AddonTestRadio.RADIO_GUID));
        assertTrue(both.profile(AddonTestRadio.RADIO_GUID) != null);
    }

    @Test
    void apiTestRadioBindsAccessoryActionsLikeTheModesTab() {
        InputConfig empty = new InputConfig();
        empty.sanitize();
        SimulatedRadio radio = AddonTestRadio.device(false);
        InputConfig config = AddonTestRadio.withDevice(empty, radio);
        DeviceProfile before = config.profile(AddonTestRadio.RADIO_GUID).copy();

        SwitchAssignment button = AddonTestRadio.actionButtonBinding(radio, config, 1, 3);
        assertEquals(SwitchSources.apply(SwitchAssignment.none(), new SwitchSources.Choice(SwitchSource.BUTTON, 3), SwitchFunction.ACCESSORY_1, false),
                button, "Knopf wie die Auswahl „Quelle“ im Tab „Modi“");
        assertTrue(button != null && button.source == SwitchSource.BUTTON && button.index == 3 && !button.toggle, "Taster rastet nie ein");
        SwitchAssignment range = AddonTestRadio.actionChannelBinding(radio, 2, 9, 1700, 2000);
        assertTrue(range != null && range.source == SwitchSource.AXIS && range.index == 9);
        assertEquals(1700, range.lowMicros());
        assertEquals(2000, range.highMicros());

        assertNull(AddonTestRadio.actionButtonBinding(radio, config, 0, 3));
        assertNull(AddonTestRadio.actionButtonBinding(radio, config, SwitchFunction.ACCESSORY_COUNT + 1, 3));
        assertNull(AddonTestRadio.actionButtonBinding(radio, config, 1, -1));
        assertNull(AddonTestRadio.actionButtonBinding(radio, config, 1, AddonTestRadio.RADIO_BUTTONS));
        assertNull(AddonTestRadio.actionButtonBinding(radio, empty, 1, 3), "ohne Profil der Testfunke keine Belegung");
        assertNull(AddonTestRadio.actionChannelBinding(radio, 0, 9, 1700, 2000));
        assertNull(AddonTestRadio.actionChannelBinding(radio, 1, 2, 1700, 2000), "Gas ist kein Schalterkanal");
        assertNull(AddonTestRadio.actionChannelBinding(radio, 1, AddonTestRadio.RADIO_CHANNELS, 1700, 2000));
        assertNull(AddonTestRadio.actionChannelBinding(radio, 1, 9, 1800, 1700));
        assertNull(AddonTestRadio.actionChannelBinding(radio, 1, 9, 1700, ChannelMath.RANGE_MAX_US + 1));

        config = AddonTestRadio.withActionBinding(config, AddonTestRadio.RADIO_GUID, 1, button);
        config = AddonTestRadio.withActionBinding(config, AddonTestRadio.RADIO_GUID, 2, range);
        DeviceProfile outside = AddonTestRadio.withActionBinding(config, AddonTestRadio.RADIO_GUID, SwitchFunction.ACCESSORY_COUNT + 1, range)
                .profile(AddonTestRadio.RADIO_GUID);
        DeviceProfile bound = config.profile(AddonTestRadio.RADIO_GUID);
        for (int number = 1; number <= SwitchFunction.ACCESSORY_COUNT; number++) {
            assertEquals(bound.binding(SwitchFunction.accessory(number)), outside.binding(SwitchFunction.accessory(number)), "Nummer 5 ändert nichts");
        }
        assertEquals(button, bound.binding(SwitchFunction.ACCESSORY_1));
        assertEquals(range, bound.binding(SwitchFunction.ACCESSORY_2));
        assertFalse(bound.binding(SwitchFunction.ACCESSORY_3).bound());
        for (SwitchFunction function : SwitchFunction.values()) {
            if (!function.accessoryAction()) {
                assertEquals(before.binding(function), bound.binding(function), "unverändert: " + function);
            }
        }

        SwitchEvaluator evaluator = new SwitchEvaluator();
        long rest = evaluator.evaluate(bound, radio.axes(), radio.buttons());
        assertFalse(SwitchEvaluator.isSet(rest, SwitchFunction.ACCESSORY_1) || SwitchEvaluator.isSet(rest, SwitchFunction.ACCESSORY_2));
        radio.setButton(3, true);
        long pressed = evaluator.evaluate(bound, radio.axes(), radio.buttons());
        assertTrue(SwitchEvaluator.isSet(pressed, SwitchFunction.ACCESSORY_1));
        assertFalse(SwitchEvaluator.isSet(pressed, SwitchFunction.ACCESSORY_2));
        radio.setButton(3, false);
        assertFalse(SwitchEvaluator.isSet(evaluator.evaluate(bound, radio.axes(), radio.buttons()), SwitchFunction.ACCESSORY_1), "Taster nur gehalten");
        radio.setMicros(9, 2000);
        for (int frame = 0; frame < 40; frame++) {
            long held = evaluator.evaluate(bound, radio.axes(), radio.buttons());
            assertTrue(SwitchEvaluator.isSet(held, SwitchFunction.ACCESSORY_2), "Schalter bleibt an, Bild " + frame);
            assertFalse(SwitchEvaluator.isSet(held, SwitchFunction.ACCESSORY_1));
        }
        radio.setMicros(9, 1000);
        assertFalse(SwitchEvaluator.isSet(evaluator.evaluate(bound, radio.axes(), radio.buttons()), SwitchFunction.ACCESSORY_2));

        SimulatedRadio pad = AddonTestRadio.device(true);
        InputConfig padConfig = AddonTestRadio.withDevice(empty, pad);
        SwitchAssignment padButton = AddonTestRadio.actionButtonBinding(pad, padConfig, 1, ProfileDefaults.PAD_SOUTH);
        assertTrue(padButton != null && padButton.source == SwitchSource.BUTTON && !padButton.toggle, "am Gamepad rastet eine Zubehör-Aktion nicht ein");
        assertNull(AddonTestRadio.actionChannelBinding(pad, 1, ProfileDefaults.PAD_LEFT_Y, 1700, 2000));
    }

    @Test
    void threePositionSwitchMakesAModeSelector() {
        List<SwitchSpec> specs = List.of(momentarySpec("low"), momentarySpec("mid"), momentarySpec("high"));
        DeviceProfile profile = new DeviceProfile();
        profile.setAddonBinding(specs.get(0).id(), SwitchAssignment.axisMicros(6, 1000, 1300));
        profile.setAddonBinding(specs.get(1).id(), SwitchAssignment.axisMicros(6, 1350, 1650));
        profile.setAddonBinding(specs.get(2).id(), SwitchAssignment.axisMicros(6, 1700, 2000));
        AddonSwitchEvaluator evaluator = new AddonSwitchEvaluator();
        long[] expected = {0b001L, 0b010L, 0b100L};
        int[] micros = {1000, 1500, 2000};
        for (int i = 0; i < micros.length; i++) {
            float[] axes = new float[7];
            axes[6] = ChannelMath.fromRangeMicros(micros[i]);
            assertEquals(expected[i], evaluator.evaluate(profile, specs, axes, new boolean[0]), micros[i] + " µs");
            assertEquals(0b111L, AddonSwitchEvaluator.readable(profile, specs, axes, new boolean[0]));
        }
    }

    @Test
    void sixtyFourSwitchesAndSixteenAxesAtMost() throws ReflectiveOperationException {
        Constructor<Api17Registries.Switches> constructor = Api17Registries.Switches.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Api17Registries.Switches registry = constructor.newInstance();
        for (int i = 0; i < SwitchRegistry.MAX_SWITCHES; i++) {
            registry.register(SwitchSpec.builder(id("s" + i)).build());
        }
        assertThrows(IllegalStateException.class, () -> registry.register(SwitchSpec.builder(id("s64")).build()));
        for (int i = 0; i < SwitchRegistry.MAX_AXES; i++) {
            registry.registerAxis(AxisSpec.builder(id("a" + i)).build());
        }
        assertThrows(IllegalStateException.class, () -> registry.registerAxis(AxisSpec.builder(id("a16")).build()));

        List<SwitchSpec> specs = new ArrayList<>();
        DeviceProfile profile = new DeviceProfile();
        for (int i = 0; i <= SwitchRegistry.MAX_SWITCHES; i++) {
            SwitchSpec spec = momentarySpec("bit" + i);
            specs.add(spec);
            profile.setAddonBinding(spec.id(), SwitchAssignment.button(i, false));
        }
        boolean[] pressed = new boolean[SwitchRegistry.MAX_SWITCHES + 1];
        pressed[63] = true;
        pressed[64] = true;
        long bits = new AddonSwitchEvaluator().evaluate(profile, specs, new float[0], pressed);
        assertEquals(Long.MIN_VALUE, bits);
        assertTrue(AddonSwitchEvaluator.isSet(bits, 63));
        assertEquals(0L, AddonSwitchEvaluator.bit(64));
        assertEquals(0L, AddonSwitchEvaluator.bit(-1));

        profile.sanitize();
        long kept = profile.switches.keySet().stream().filter(AddonBindings::addonKey).count();
        assertEquals(AddonBindings.MAX_STORED, kept);
        assertEquals(64, AddonBindings.MAX_STORED);

        List<AddonSwitchC2S.Entry> seventeen = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            seventeen.add(new AddonSwitchC2S.Entry(id("s" + i), true));
        }
        assertThrows(IllegalArgumentException.class, () -> new AddonSwitchC2S(1, 0, seventeen));
        List<AddonAxisC2S.Entry> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add(new AddonAxisC2S.Entry(id("a" + i), (short) i));
        }
        assertThrows(IllegalArgumentException.class, () -> new AddonAxisC2S(1, 0, nine));
    }

    @Test
    void namespacedAssignmentSurvivesNormalizingWithoutTheAddon() {
        String stored = "{\"name\":\"Funke\",\"switches\":{"
                + "\"arm\":{\"source\":\"BUTTON\",\"index\":0,\"toggle\":true},"
                + "\"noclipmod:ghost\":{\"source\":\"AXIS\",\"index\":5,\"low\":0.5,\"high\":1.0,\"toggle\":false},"
                + "\"propwash:ghost\":{\"source\":\"BUTTON\",\"index\":1},"
                + "\"Bad Key:x\":{\"source\":\"BUTTON\",\"index\":1},"
                + "\"gimbalmod:mode/high\":{\"source\":\"BUTTON\",\"index\":2,\"toggle\":true},"
                + "\"gimbalmod:unbound\":{\"source\":\"NONE\",\"index\":-1},"
                + "\"unknown_builtin\":{\"source\":\"BUTTON\",\"index\":3}},"
                + "\"addonAxes\":{\"gimbalmod:gimbal_tilt\":{\"source\":\"AXIS\",\"index\":6,\"invert\":true,\"deadband\":0.1},"
                + "\"propwash:tilt\":{\"source\":\"AXIS\",\"index\":6},\"gimbalmod:zoom\":{\"source\":\"AXIS\",\"index\":-1}}}";
        Gson gson = new Gson();
        DeviceProfile profile = gson.fromJson(stored, DeviceProfile.class);
        profile.sanitize();
        assertEquals(List.of("arm", "noclipmod:ghost", "gimbalmod:mode/high"), new ArrayList<>(profile.switches.keySet()));
        SwitchAssignment ghost = profile.addonBinding(Identifier.fromNamespaceAndPath("noclipmod", "ghost"));
        assertEquals(SwitchSource.AXIS, ghost.source);
        assertEquals(5, ghost.index);
        assertTrue(profile.addonBinding(Identifier.fromNamespaceAndPath("gimbalmod", "mode/high")).toggle);
        assertEquals(List.of("gimbalmod:gimbal_tilt", "gimbalmod:zoom"), new ArrayList<>(profile.addonAxes.keySet()));
        AxisAssignment tilt = profile.addonAxis(Identifier.fromNamespaceAndPath("gimbalmod", "gimbal_tilt"));
        assertTrue(tilt.onAxis());
        assertTrue(tilt.invert);
        assertEquals(0.1F, tilt.deadband);
        assertSame(AxisAssignment.Source.KEYS, profile.addonAxis(Identifier.fromNamespaceAndPath("gimbalmod", "zoom")).source);

        DeviceProfile again = gson.fromJson(gson.toJson(profile), DeviceProfile.class);
        again.sanitize();
        assertEquals(new ArrayList<>(profile.switches.keySet()), new ArrayList<>(again.switches.keySet()));
        assertEquals(profile.addonAxes, again.addonAxes);
        DeviceProfile copy = profile.copy();
        assertEquals(profile.switches, copy.switches);
        assertEquals(profile.addonAxes, copy.addonAxes);

        DeviceProfile old = gson.fromJson("{\"name\":\"Alt\",\"switches\":{\"arm\":{\"source\":\"BUTTON\",\"index\":0}}}", DeviceProfile.class);
        old.sanitize();
        assertTrue(old.addonAxes.isEmpty());
        DeviceProfile nulled = gson.fromJson("{\"name\":\"Null\",\"addonAxes\":null}", DeviceProfile.class);
        nulled.sanitize();
        assertTrue(nulled.addonAxes.isEmpty());

        DeviceProfile reset = new DeviceProfile();
        reset.copySwitchesFrom(new DeviceProfile());
        reset.keepAddonsFrom(profile);
        assertTrue(reset.addonBinding(Identifier.fromNamespaceAndPath("noclipmod", "ghost")).bound());
        assertTrue(reset.addonAxis(Identifier.fromNamespaceAndPath("gimbalmod", "gimbal_tilt")).onAxis());
        assertFalse(reset.switches.containsKey("arm"));
    }

    @Test
    void addonBindingsDoNotTouchBuiltInBits() {
        DeviceProfile plain = new DeviceProfile();
        plain.setBinding(SwitchFunction.ARM, SwitchAssignment.button(0, false));
        DeviceProfile mixed = plain.copy();
        mixed.setAddonBinding(Identifier.fromNamespaceAndPath("noclipmod", "ghost"), SwitchAssignment.button(0, false));
        boolean[] pressed = {true};
        assertEquals(new SwitchEvaluator().evaluate(plain, new float[0], pressed), new SwitchEvaluator().evaluate(mixed, new float[0], pressed));
        assertEquals(SwitchEvaluator.readable(plain, new float[0], pressed), SwitchEvaluator.readable(mixed, new float[0], pressed));
    }

    @Test
    void sticksMovingOntoAnAddonAxisMoveTheAssignmentAway() {
        DeviceProfile profile = ProfileDefaults.radio("Funke", 8);
        Identifier ghost = Identifier.fromNamespaceAndPath("noclipmod", "ghost");
        Identifier tilt = Identifier.fromNamespaceAndPath("gimbalmod", "gimbal_tilt");
        profile.setAddonBinding(ghost, SwitchAssignment.axisMicros(5, 1700, 2000));
        AxisAssignment onSix = AxisAssignment.axis(6);
        onSix.invert = true;
        profile.setAddonAxis(tilt, onSix);
        int[] before = profile.stickAxes();
        profile.assignAxis(StickChannel.ROLL, 5);
        assertEquals(before[0], profile.addonBinding(ghost).index);
        profile.assignAxis(StickChannel.PITCH, 6);
        AxisAssignment moved = profile.addonAxis(tilt);
        assertEquals(before[1], moved.index);
        assertTrue(moved.invert);
    }

    @Test
    void axesUseDeadbandInversionAndKeyRate() {
        assertEquals(0.0, AddonInputMath.deadband(0.04, 0.05));
        assertEquals(0.0, AddonInputMath.deadband(-0.05, 0.05));
        assertEquals(1.0, AddonInputMath.deadband(1.0, 0.05), 1.0E-9);
        assertEquals(-1.0, AddonInputMath.deadband(-1.5, 0.05), 1.0E-9);
        assertEquals(0.5, AddonInputMath.deadband(0.55, 0.1), 1.0E-9);
        assertEquals(0.0, AddonInputMath.deadband(Double.NaN, 0.1));
        AxisAssignment assignment = AxisAssignment.axis(3);
        assignment.deadband = 0.1F;
        assignment.invert = true;
        assertEquals(-0.5, AddonInputMath.axisValue(assignment, 0.55F), 1.0E-6);

        double value = 0.0;
        for (int i = 0; i < 4; i++) {
            value = AddonInputMath.keyStep(value, true, false, 60.0, 0.25);
        }
        assertEquals(60.0 / AddonInputMath.FULL_SCALE_DEGREES, value, 1.0E-9);
        assertEquals(value, AddonInputMath.keyStep(value, true, true, 60.0, 0.25));
        assertEquals(value, AddonInputMath.keyStep(value, false, false, 60.0, 0.25));
        assertEquals(value + 0.25 * 60.0 / 90.0, AddonInputMath.keyStep(value, true, false, 60.0, 5.0), 1.0E-9);
        assertEquals(1.0, AddonInputMath.keyStep(0.99, true, false, 3600.0, 0.25));
        assertEquals(-1.0, AddonInputMath.keyStep(-0.99, false, true, 3600.0, 0.25));

        for (double v : new double[]{-1.0, -0.5, 0.0, 0.3333, 0.7, 1.0}) {
            assertEquals(v, AddonInputMath.dequantize(AddonInputMath.quantize(v)), AddonInputMath.QUANTUM);
        }
        assertEquals(Short.MAX_VALUE, AddonInputMath.quantize(2.0));
        assertEquals(-Short.MAX_VALUE, AddonInputMath.quantize(-2.0));

        AxisAssignment broken = new AxisAssignment();
        broken.source = AxisAssignment.Source.AXIS;
        broken.index = -1;
        broken.deadband = Float.NaN;
        broken.sanitize();
        assertSame(AxisAssignment.Source.KEYS, broken.source);
        assertEquals(AxisAssignment.DEFAULT_DEADBAND, broken.deadband);
        AxisAssignment wide = AxisAssignment.axis(2);
        wide.deadband = 0.9F;
        wide.sanitize();
        assertEquals(AxisAssignment.MAX_DEADBAND, wide.deadband);
    }

    @Test
    void idsFollowTheNamespaceRules() {
        assertTrue(AddonIds.valid("noclipmod:ghost"));
        assertTrue(AddonIds.valid("cam_drones:mode/high.v2"));
        assertFalse(AddonIds.valid("propwash:ghost"));
        assertFalse(AddonIds.valid("ghost"));
        assertFalse(AddonIds.valid("Noclip:ghost"));
        assertFalse(AddonIds.valid("a:" + "x".repeat(63)));
        assertNull(AddonIds.parse("propwash:ghost"));
        assertEquals(Identifier.fromNamespaceAndPath("noclipmod", "ghost"), AddonIds.parse("noclipmod:ghost"));
    }

    @Test
    void accessoryActionsHaveNamesAndTheHintComesOncePerType() {
        AccessoryBehavior named = new AccessoryBehavior() {
            @Override
            public Optional<String> actionNameKey(int number) {
                return switch (number) {
                    case 1 -> Optional.of("example.fire");
                    case 3 -> Optional.of("  ");
                    case 4 -> throw new IllegalStateException("kaputt");
                    default -> Optional.empty();
                };
            }
        };
        Identifier launcher = id("launcher");
        AccessoryActionHints.Named actions = AccessoryActionHints.named(launcher, named).orElseThrow();
        assertEquals(Map.of(1, "example.fire"), actions.actions());
        assertEquals(Optional.of("example.fire"), AccessoryActionHints.nameKey(named, 1));
        assertEquals(Optional.empty(), AccessoryActionHints.nameKey(named, 0));
        assertEquals(Optional.empty(), AccessoryActionHints.nameKey(named, 5));
        assertEquals(Optional.empty(), AccessoryActionHints.nameKey(null, 1));
        assertTrue(AccessoryActionHints.named(id("plain"), new AccessoryBehavior() {
        }).isEmpty());

        AccessoryActionHints.Seen seen = new AccessoryActionHints.Seen();
        assertTrue(AccessoryActionHints.fresh(seen, "player", launcher));
        assertTrue(AccessoryActionHints.shown(seen, "player", launcher));
        assertFalse(AccessoryActionHints.fresh(seen, "player", launcher));
        assertFalse(AccessoryActionHints.shown(seen, "player", launcher));
        assertTrue(AccessoryActionHints.fresh(seen, "player", id("other")));
        assertTrue(AccessoryActionHints.fresh(seen, "someone_else", launcher));
        Gson gson = new Gson();
        AccessoryActionHints.Seen reread = gson.fromJson(gson.toJson(seen), AccessoryActionHints.Seen.class);
        AccessoryActionHints.sanitize(reread);
        assertFalse(AccessoryActionHints.fresh(reread, "player", launcher));
        for (int i = 0; i < AccessoryActionHints.MAX_TYPES + 5; i++) {
            AccessoryActionHints.shown(seen, "player", id("type" + i));
        }
        assertEquals(AccessoryActionHints.MAX_TYPES, seen.players.get("player").size());
        assertTrue(AccessoryActionHints.fresh(seen, "player", launcher));
    }

    @Test
    void framesCarryAddonStateNextToTheBuiltInMask() {
        RadioFrame frame = new RadioFrame(RadioSource.JOYSTICK, "Funke", "guid", StickPositions.IDLE, 0b101L, 0b111L, new float[3], new boolean[2]);
        assertEquals(0L, frame.addonSwitches());
        assertEquals(0, frame.addonAxes().length);
        RadioFrame staged = frame.withAddons(Long.MIN_VALUE | 1L, new float[]{0.5F, -1.0F});
        assertEquals(0b101L, staged.activeSwitches());
        assertEquals(0b111L, staged.readableSwitches());
        assertEquals(Long.MIN_VALUE | 1L, staged.addonSwitches());
        assertArrayEquals(new float[]{0.5F, -1.0F}, staged.addonAxes());
        assertEquals(0L, RadioFrame.NONE.addonSwitches());
    }

    @Test
    void payloadsRoundTrip() {
        AddonSwitchC2S switches = new AddonSwitchC2S(42, 7, List.of(new AddonSwitchC2S.Entry(REGISTERED, true),
                new AddonSwitchC2S.Entry(MOMENTARY, false)));
        ByteBuf buf = Unpooled.buffer();
        AddonSwitchC2S.CODEC.encode(buf, switches);
        assertEquals(switches, AddonSwitchC2S.CODEC.decode(buf));
        AddonAxisC2S axes = new AddonAxisC2S(42, 7, List.of(new AddonAxisC2S.Entry(TILT, AddonInputMath.quantize(-0.25))));
        buf = Unpooled.buffer();
        AddonAxisC2S.CODEC.encode(buf, axes);
        assertEquals(axes, AddonAxisC2S.CODEC.decode(buf));
        AddonFeaturesS2C features = new AddonFeaturesS2C(List.of(REGISTERED), List.of(TILT), List.of(id("ghost_rule")));
        buf = Unpooled.buffer();
        AddonFeaturesS2C.CODEC.encode(buf, features);
        assertEquals(features, AddonFeaturesS2C.CODEC.decode(buf));
    }

    private static Path devtestAssets() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            Path dir = candidate.resolve("src/devtest/resources/assets");
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }
        throw new IllegalStateException("devtest-Assets nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
    }

    private static JsonObject itemModelFor(JsonObject node, String context) {
        if (!"minecraft:select".equals(node.get("type").getAsString())) {
            return node;
        }
        for (JsonElement entry : node.getAsJsonArray("cases")) {
            JsonObject branch = entry.getAsJsonObject();
            JsonElement when = branch.get("when");
            boolean match = when.isJsonArray() ? when.getAsJsonArray().asList().stream().anyMatch(e -> e.getAsString().equals(context))
                    : when.getAsString().equals(context);
            if (match) {
                return itemModelFor(branch.getAsJsonObject("model"), context);
            }
        }
        return itemModelFor(node.getAsJsonObject("fallback"), context);
    }

    @Test
    void exampleSignalLightHasAnItemModelInEveryDisplayContext() throws IOException {
        Identifier light = ExampleSwitches.SIGNAL_LIGHT;
        Path file = devtestAssets().resolve(light.getNamespace()).resolve("items").resolve(light.getPath() + ".json");
        assertTrue(Files.isRegularFile(file), "Item-Modell fehlt: " + file);
        JsonObject model = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("model");
        Map<String, String> flags = Map.of("ground", "ground", "fixed", "box", "thirdperson_righthand", "hand", "thirdperson_lefthand", "hand",
                "firstperson_righthand", "hand", "firstperson_lefthand", "hand", "gui", "");
        flags.forEach((context, flag) -> {
            JsonObject leaf = itemModelFor(model, context);
            assertEquals("minecraft:special", leaf.get("type").getAsString(), context);
            JsonObject part = leaf.getAsJsonObject("model");
            assertEquals("propwash:part", part.get("type").getAsString(), context);
            if (flag.isEmpty()) {
                assertFalse(part.has("ground") || part.has("hand") || part.has("box"), context);
            } else {
                assertTrue(part.has(flag) && part.get(flag).getAsBoolean(), context + " ohne " + flag);
            }
        });
    }
}
