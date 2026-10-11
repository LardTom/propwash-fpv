package de.lardtom.propwash.input;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchCommandsTest {
    private static final float[] NO_AXES = new float[0];

    @Test
    void commandsAreTheLastEightSwitchFunctions() {
        List<SwitchFunction> commands = SwitchFunction.commands();
        assertEquals(SwitchFunction.COMMAND_COUNT, commands.size());
        assertEquals(SwitchFunction.values().length, SwitchFunction.flightFunctions().size() + commands.size());
        for (int i = 0; i < SwitchFunction.COMMAND_COUNT; i++) {
            SwitchFunction function = commands.get(i);
            assertEquals(SwitchFunction.values().length - SwitchFunction.COMMAND_COUNT + i, function.ordinal());
            assertTrue(function.command());
            assertEquals(i, function.commandIndex());
            assertEquals(function, SwitchFunction.command(i));
            assertEquals("propwash.input.switch.command_" + (i + 1), function.translationKey());
            assertTrue(function.supportsToggle());
            assertFalse(function.latchesOnGamepad());
        }
        for (SwitchFunction function : SwitchFunction.flightFunctions()) {
            assertFalse(function.command(), function.name());
            assertEquals(-1, function.commandIndex());
        }
        assertTrue(SwitchFunction.values().length <= Integer.SIZE);
    }

    @Test
    void normalizeWorksLikeTheChatInput() {
        assertEquals("/time set night", SwitchCommands.normalize("/time set night"));
        assertEquals("/time set night", SwitchCommands.normalize("time set night"));
        assertEquals("/time set night", SwitchCommands.normalize("   /time   set\tnight  "));
        assertEquals("//wand", SwitchCommands.normalize("//wand"));
        assertEquals("", SwitchCommands.normalize(""));
        assertEquals("", SwitchCommands.normalize("   "));
        assertEquals("", SwitchCommands.normalize("/"));
        assertEquals("", SwitchCommands.normalize(" / "));
        assertEquals("", SwitchCommands.normalize(null));
        assertEquals("time set night", SwitchCommands.body("/time set night"));
        assertEquals("/wand", SwitchCommands.body("//wand"));
        assertEquals("", SwitchCommands.body(""));
    }

    @Test
    void commandsLongerThanTheChatAreRejected() {
        String fits = "/" + "a".repeat(SwitchCommands.MAX_LENGTH - 1);
        assertEquals(SwitchCommands.MAX_LENGTH, fits.length());
        assertEquals(SwitchCommands.Problem.NONE, SwitchCommands.problem(fits));
        assertEquals(SwitchCommands.Problem.NONE, SwitchCommands.check("a".repeat(SwitchCommands.MAX_LENGTH - 1)));
        assertEquals(SwitchCommands.Problem.TOO_LONG, SwitchCommands.check("a".repeat(SwitchCommands.MAX_LENGTH)));
        assertEquals(SwitchCommands.Problem.TOO_LONG, SwitchCommands.problem(fits + "b"));
        assertEquals(256, SwitchCommands.MAX_LENGTH);
        assertEquals("", SwitchCommands.cleanStored("x".repeat(400)));
    }

    @Test
    void charactersTheServerKicksForAreRejected() {
        assertEquals(SwitchCommands.Problem.ILLEGAL_CHARACTER, SwitchCommands.problem("/say §cred"));
        assertEquals(SwitchCommands.Problem.ILLEGAL_CHARACTER, SwitchCommands.problem("/say a\u007fb"));
        assertEquals(SwitchCommands.Problem.ILLEGAL_CHARACTER, SwitchCommands.problem("/say a\u0001b"));
        assertEquals("/say a b", SwitchCommands.normalize("/say a\nb"));
        assertEquals(SwitchCommands.Problem.NONE, SwitchCommands.problem("/say Grüße äöü ß"));
        assertFalse(SwitchCommands.allowedCharacter('§'));
        assertTrue(SwitchCommands.allowedCharacter('ß'));
        assertEquals("", SwitchCommands.cleanStored("/say §cred"));
    }

    @Test
    void sanitizeKeepsOnlyCommandSlotsWithText() {
        Map<String, SwitchCommand> raw = new LinkedHashMap<>();
        raw.put("command_1", new SwitchCommand(" time set night ", "/time set day"));
        raw.put("command_2", new SwitchCommand("", "  "));
        raw.put("arm", new SwitchCommand("/kill", ""));
        raw.put("command_9", new SwitchCommand("/kill", ""));
        raw.put("command_3", new SwitchCommand("x".repeat(300), "/weather clear"));
        raw.put("command_4", null);
        Map<String, SwitchCommand> clean = SwitchCommands.sanitize(raw);
        assertEquals(List.of("command_1", "command_3"), List.copyOf(clean.keySet()));
        assertEquals(new SwitchCommand("/time set night", "/time set day"), clean.get("command_1"));
        assertEquals(new SwitchCommand("", "/weather clear"), clean.get("command_3"));
        assertTrue(SwitchCommands.sanitize(null).isEmpty());
    }

    @Test
    void commandsLiveInTheDeviceProfileWithTheBindings() {
        DeviceProfile profile = ProfileDefaults.radio("Boxer", 8);
        profile.setBinding(SwitchFunction.COMMAND_2, SwitchAssignment.button(3, false));
        profile.setCommand(SwitchFunction.COMMAND_2, new SwitchCommand("/time set night", "/time set day"));
        profile.setCommand(SwitchFunction.ARM, new SwitchCommand("/kill", ""));
        assertTrue(profile.command(SwitchFunction.ARM).empty());
        assertEquals("/time set night", profile.command(SwitchFunction.COMMAND_2).on);
        assertTrue(profile.command(SwitchFunction.COMMAND_1).empty());

        DeviceProfile copy = profile.copy();
        assertEquals(profile.command(SwitchFunction.COMMAND_2), copy.command(SwitchFunction.COMMAND_2));
        assertNotSame(profile.commands.get("command_2"), copy.commands.get("command_2"));
        assertEquals(profile.binding(SwitchFunction.COMMAND_2), copy.binding(SwitchFunction.COMMAND_2));

        profile.setCommand(SwitchFunction.COMMAND_2, new SwitchCommand());
        assertFalse(profile.commands.containsKey("command_2"));
        assertEquals("/time set night", copy.command(SwitchFunction.COMMAND_2).on);
    }

    @Test
    void savedConfigKeepsCommandsAndDropsBrokenOnes() {
        InputConfig config = new InputConfig();
        DeviceProfile profile = ProfileDefaults.gamepad("Pad");
        profile.setBinding(SwitchFunction.COMMAND_1, SwitchAssignment.toggleButton(ProfileDefaults.PAD_EAST + 4));
        profile.setCommand(SwitchFunction.COMMAND_1, new SwitchCommand("/time set night", "/time set day"));
        profile.commands.put("command_5", new SwitchCommand("/say §x", ""));
        config.profiles.put("guid", profile);
        config.keyboard.commands.put("command_8", new SwitchCommand("weather clear", ""));
        Gson gson = new Gson();
        InputConfig loaded = gson.fromJson(gson.toJson(config), InputConfig.class);
        loaded.sanitize();
        DeviceProfile again = loaded.profile("guid");
        assertEquals(new SwitchCommand("/time set night", "/time set day"), again.command(SwitchFunction.COMMAND_1));
        assertTrue(again.binding(SwitchFunction.COMMAND_1).toggle);
        assertFalse(again.commands.containsKey("command_5"));
        assertEquals("/weather clear", loaded.keyboard.commands.get("command_8").on);

        InputConfig old = gson.fromJson("{\"profiles\":{\"g\":{\"name\":\"Old\",\"switches\":{}}},\"keyboard\":{\"response\":7.0}}", InputConfig.class);
        old.sanitize();
        assertTrue(old.profile("g").commands.isEmpty());
        assertTrue(old.keyboard.commands.isEmpty());
    }

    @Test
    void configuratorMergeCarriesCommandsAndKeyboardCommands() {
        InputConfig base = new InputConfig();
        base.profiles.put("guid", ProfileDefaults.radio("Boxer", 8));
        InputConfig working = base.copy();
        working.profile("guid").setCommand(SwitchFunction.COMMAND_3, new SwitchCommand("/gamemode spectator", ""));
        working.profile("guid").setBinding(SwitchFunction.COMMAND_3, SwitchAssignment.axisMicros(6, 1700, 2100));
        working.keyboard.commands.put("command_1", new SwitchCommand("/time set noon", ""));
        assertTrue(base.profile("guid").command(SwitchFunction.COMMAND_3).empty());
        InputConfig merged = base.copy();
        merged.mergeReceiverFrom(working);
        merged.mergeSwitchesFrom(working);
        assertEquals("/gamemode spectator", merged.profile("guid").command(SwitchFunction.COMMAND_3).on);
        assertTrue(merged.profile("guid").binding(SwitchFunction.COMMAND_3).bound());
        assertEquals("/time set noon", merged.keyboard.commands.get("command_1").on);
        assertTrue(base.keyboard.commands.isEmpty());
    }

    @Test
    void wizardResetKeepsCommandsButNotOnSticks() {
        DeviceProfile profile = ProfileDefaults.radio("Boxer", 8);
        profile.setBinding(SwitchFunction.COMMAND_1, SwitchAssignment.axisMicros(7, 1700, 2100));
        profile.setBinding(SwitchFunction.COMMAND_2, SwitchAssignment.axisMicros(2, 1700, 2100));
        profile.setCommand(SwitchFunction.COMMAND_1, new SwitchCommand("/time set night", ""));
        DeviceProfile fresh = ProfileDefaults.radio("Boxer", 8);
        fresh.keepCommandsFrom(profile);
        assertEquals(profile.binding(SwitchFunction.COMMAND_1), fresh.binding(SwitchFunction.COMMAND_1));
        assertFalse(fresh.binding(SwitchFunction.COMMAND_2).bound(), "Gas-Achse ist ein Knüppel");
        assertEquals("/time set night", fresh.command(SwitchFunction.COMMAND_1).on);
        assertEquals(profile.binding(SwitchFunction.ARM), fresh.binding(SwitchFunction.ARM));
    }

    @Test
    void commandSwitchesAreEvaluatedLikeEveryOtherFunction() {
        DeviceProfile pad = ProfileDefaults.gamepad("Pad");
        pad.setBinding(SwitchFunction.COMMAND_4, SwitchAssignment.button(9, false));
        pad.setBinding(SwitchFunction.COMMAND_5, SwitchAssignment.toggleButton(10));
        pad.sanitize();
        SwitchEvaluator evaluator = new SwitchEvaluator();
        boolean[] buttons = new boolean[ProfileDefaults.PAD_BUTTONS];
        evaluator.evaluate(pad, NO_AXES, buttons);
        buttons[9] = true;
        buttons[10] = true;
        int bits = evaluator.evaluate(pad, NO_AXES, buttons);
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.COMMAND_4));
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.COMMAND_5));
        buttons[9] = false;
        buttons[10] = false;
        bits = evaluator.evaluate(pad, NO_AXES, buttons);
        assertFalse(SwitchEvaluator.isSet(bits, SwitchFunction.COMMAND_4));
        assertTrue(SwitchEvaluator.isSet(bits, SwitchFunction.COMMAND_5), "Umschalten hält den Zustand");
        int readable = SwitchEvaluator.readable(pad, NO_AXES, buttons);
        assertTrue(SwitchEvaluator.isSet(readable, SwitchFunction.COMMAND_4));
        assertFalse(SwitchEvaluator.isSet(readable, SwitchFunction.COMMAND_1));
    }

    @Test
    void movingAStickOntoACommandAxisMovesTheCommandToo() {
        DeviceProfile profile = ProfileDefaults.radio("Boxer", 8);
        profile.setBinding(SwitchFunction.COMMAND_1, SwitchAssignment.axisMicros(7, 1700, 2100));
        List<SwitchFunction> moved = profile.assignAxis(StickChannel.YAW, 7);
        assertTrue(moved.contains(SwitchFunction.COMMAND_1), moved.toString());
        assertEquals(null, profile.stickConflict(profile.binding(SwitchFunction.COMMAND_1)));
    }
}
