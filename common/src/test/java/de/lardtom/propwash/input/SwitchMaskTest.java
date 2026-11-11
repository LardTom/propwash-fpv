package de.lardtom.propwash.input;

import com.google.gson.Gson;
import de.lardtom.propwash.sim.StabMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchMaskTest {
    private static final SwitchFunction[] FUNCTIONS = SwitchFunction.values();
    private static final long HIGH = ~((1L << 31) - 1L);
    private static final long[] HIGH_BITS = {1L << 31, 1L << 32, 1L << 33, 1L << 40, 1L << 47, 1L << 62, Long.MIN_VALUE, HIGH};

    @Test
    void maskTypesAreLong() throws ReflectiveOperationException {
        assertEquals(long.class, component("activeSwitches").getType());
        assertEquals(long.class, component("readableSwitches").getType());
        assertEquals(long.class, RadioState.class.getMethod("activeSwitches").getReturnType());
        assertEquals(long.class, RadioState.class.getMethod("readableSwitches").getReturnType());
        assertEquals(long.class, SwitchEvaluator.class.getMethod("bit", SwitchFunction.class).getReturnType());
        assertEquals(long.class, SwitchEvaluator.class.getMethod("evaluate", DeviceProfile.class, float[].class, boolean[].class).getReturnType());
        assertEquals(long.class, SwitchEvaluator.class.getMethod("readable", DeviceProfile.class, float[].class, boolean[].class).getReturnType());
        assertEquals(boolean.class, SwitchEvaluator.class.getMethod("isSet", long.class, SwitchFunction.class).getReturnType());
        Set<String> maskMethods = Set.of("fromSwitches", "observe", "preview", "keyOverridesSwitch");
        for (Class<?> selector : List.of(FlightModeSelector.class, Mode3dSelector.class, TurtleSelector.class)) {
            selector.getMethod("fromSwitches", long.class, long.class);
            selector.getMethod("observe", boolean.class, long.class, long.class);
            for (Method method : selector.getDeclaredMethods()) {
                if (maskMethods.contains(method.getName())) {
                    assertFalse(Arrays.asList(method.getParameterTypes()).contains(int.class), selector.getSimpleName() + "#" + method.getName());
                }
            }
        }
        for (Class<?> selector : List.of(FlightModeSelector.class, Mode3dSelector.class)) {
            selector.getMethod("preview", boolean.class, long.class, long.class);
            selector.getMethod("keyOverridesSwitch", boolean.class, long.class, long.class);
        }
        RadioFrame.class.getConstructor(RadioSource.class, String.class, String.class, StickPositions.class, long.class, long.class,
                float[].class, boolean[].class);
        RadioFrame.class.getConstructor(RadioSource.class, String.class, String.class, StickPositions.class, long.class, long.class,
                float[].class, boolean[].class, float.class);
    }

    @Test
    void protectedSignaturesStay() throws ReflectiveOperationException {
        Field instance = RadioState.class.getField("INSTANCE");
        assertEquals(RadioState.class, instance.getType());
        assertTrue(Modifier.isStatic(instance.getModifiers()) && Modifier.isFinal(instance.getModifiers()));
        assertEquals(boolean.class, RadioState.class.getMethod("isActive", SwitchFunction.class).getReturnType());
        assertEquals(boolean.class, SwitchFunction.class.getMethod("command").getReturnType());
        assertEquals(boolean.class, SwitchFunction.class.getMethod("supportsToggle").getReturnType());
        assertEquals(int.class, SwitchFunction.class.getField("COMMAND_COUNT").getType());
        assertEquals(8, SwitchFunction.COMMAND_COUNT);
        assertSame(SwitchFunction.COMMAND_8, FUNCTIONS[FUNCTIONS.length - 1]);
        assertEquals(SwitchFunction.COMMAND_1.ordinal() + SwitchFunction.COMMAND_COUNT, FUNCTIONS.length);
        Constructor<?>[] constructors = SwitchFunction.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertArrayEquals(new Class<?>[]{String.class, int.class}, constructors[0].getParameterTypes());
    }

    @Test
    void everyFunctionHasItsOwnBit() {
        long all = 0L;
        for (SwitchFunction function : FUNCTIONS) {
            long bit = SwitchEvaluator.bit(function);
            assertEquals(1L << function.ordinal(), bit, function.name());
            assertEquals(1, Long.bitCount(bit), function.name());
            assertEquals(0L, all & bit, function.name());
            all |= bit;
        }
        assertEquals((1L << FUNCTIONS.length) - 1L, all);
        assertTrue(FUNCTIONS.length < Long.SIZE);
    }

    @Test
    void bitsFromPosition31NeverAliasBuiltInFunctions() {
        for (int position = 31; position < Long.SIZE; position++) {
            long high = 1L << position;
            for (SwitchFunction function : FUNCTIONS) {
                assertFalse(SwitchEvaluator.isSet(high, function), position + " " + function);
                assertTrue(SwitchEvaluator.isSet(high | SwitchEvaluator.bit(function), function), position + " " + function);
                assertFalse(SwitchEvaluator.isSet(~SwitchEvaluator.bit(function), function), function.name());
                assertTrue(SwitchEvaluator.isSet(-1L, function), function.name());
            }
        }
        assertEquals(0L, HIGH & ((1L << FUNCTIONS.length) - 1L));
        assertEquals(33, Long.bitCount(HIGH));
        assertEquals((long) Integer.MIN_VALUE, HIGH);
    }

    @Test
    void evaluateAndReadableFillTheLongMask() {
        DeviceProfile profile = new DeviceProfile();
        for (SwitchFunction function : FUNCTIONS) {
            profile.setBinding(function, SwitchAssignment.button(function.ordinal(), false));
        }
        boolean[] pressed = new boolean[FUNCTIONS.length];
        Arrays.fill(pressed, true);
        long all = (1L << FUNCTIONS.length) - 1L;
        assertEquals(all, new SwitchEvaluator().evaluate(profile, new float[0], pressed));
        assertEquals(all, SwitchEvaluator.readable(profile, new float[0], new boolean[FUNCTIONS.length]));
        for (SwitchFunction function : FUNCTIONS) {
            boolean[] one = new boolean[FUNCTIONS.length];
            one[function.ordinal()] = true;
            assertEquals(SwitchEvaluator.bit(function), new SwitchEvaluator().evaluate(profile, new float[0], one), function.name());
        }
        assertEquals(0L, SwitchEvaluator.readable(profile, new float[0], new boolean[0]));
    }

    @Test
    void radioFrameAndStateCarryHighBitsUnchanged() {
        for (long high : HIGH_BITS) {
            long active = SwitchEvaluator.bit(SwitchFunction.ANGLE) | SwitchEvaluator.bit(SwitchFunction.COMMAND_8) | high;
            long readable = active | SwitchEvaluator.bit(SwitchFunction.HORIZON);
            RadioFrame frame = new RadioFrame(RadioSource.JOYSTICK, "Funke", "guid", StickPositions.IDLE, active, readable, new float[0], new boolean[0]);
            assertEquals(active, frame.activeSwitches());
            assertEquals(readable, frame.readableSwitches());
            RadioFrame none = new RadioFrame(RadioSource.NONE, "Funke", "guid", StickPositions.IDLE, active, readable, new float[0], new boolean[0], 0.5F);
            assertEquals(0L, none.activeSwitches());
            assertEquals(0L, none.readableSwitches());
            RadioState radio = RadioState.INSTANCE;
            try {
                radio.publish(frame);
                assertEquals(active, radio.activeSwitches());
                assertEquals(readable, radio.readableSwitches());
                assertTrue(radio.isActive(SwitchFunction.ANGLE));
                assertTrue(radio.isActive(SwitchFunction.COMMAND_8));
                assertFalse(radio.isActive(SwitchFunction.HORIZON));
                assertFalse(radio.isActive(SwitchFunction.ARM));
                assertFalse(radio.isActive(null));
            } finally {
                radio.publish(RadioFrame.NONE);
            }
        }
        assertEquals(0L, RadioFrame.NONE.activeSwitches());
    }

    @Test
    void flightModeSelectorIgnoresBitsFrom31() {
        long angle = SwitchEvaluator.bit(SwitchFunction.ANGLE);
        long horizon = SwitchEvaluator.bit(SwitchFunction.HORIZON);
        for (long high : HIGH_BITS) {
            assertNull(FlightModeSelector.fromSwitches(high, high));
            assertEquals(StabMode.ANGLE, FlightModeSelector.fromSwitches(angle | high, angle | horizon | high));
            assertEquals(StabMode.HORIZON, FlightModeSelector.fromSwitches(horizon | high, angle | horizon | high));
            assertEquals(StabMode.ACRO, FlightModeSelector.fromSwitches(high, angle | high));
        }
        assertSameTrace(high -> {
            FlightModeSelector selector = new FlightModeSelector();
            List<Object> trace = new ArrayList<>();
            long[][] steps = {{0, angle | horizon}, {angle, angle | horizon}, {horizon, angle | horizon}, {horizon, angle | horizon}, {0, 0}, {0, angle}};
            for (long[] step : steps) {
                long active = step[0] | high;
                long readable = step[1] | high;
                trace.add(selector.preview(true, active, readable));
                trace.add(selector.observe(true, active, readable));
                trace.add(selector.keyOverridesSwitch(true, active, readable));
                trace.add(selector.mode());
                trace.add(selector.source());
                if (step[0] == horizon) {
                    selector.cycle();
                    trace.add(selector.keyOverridesSwitch(true, active, readable));
                }
            }
            return trace;
        });
    }

    @Test
    void mode3dSelectorIgnoresBitsFrom31() {
        long mode3d = SwitchEvaluator.bit(SwitchFunction.MODE_3D);
        for (long high : HIGH_BITS) {
            assertNull(Mode3dSelector.fromSwitches(high, high));
            assertEquals(Boolean.TRUE, Mode3dSelector.fromSwitches(mode3d | high, mode3d | high));
            assertEquals(Boolean.FALSE, Mode3dSelector.fromSwitches(high, mode3d | high));
        }
        assertSameTrace(high -> {
            Mode3dSelector selector = new Mode3dSelector();
            List<Object> trace = new ArrayList<>();
            long[][] steps = {{0, mode3d}, {mode3d, mode3d}, {mode3d, mode3d}, {0, mode3d}, {0, 0}};
            for (long[] step : steps) {
                long active = step[0] | high;
                long readable = step[1] | high;
                trace.add(selector.preview(true, active, readable));
                trace.add(selector.observe(true, active, readable));
                trace.add(selector.keyOverridesSwitch(true, active, readable));
                trace.add(selector.selected());
                trace.add(selector.source());
                if (step[0] == mode3d) {
                    selector.toggle();
                    trace.add(selector.keyOverridesSwitch(true, active, readable));
                }
            }
            return trace;
        });
    }

    @Test
    void turtleSelectorIgnoresBitsFrom31() {
        long turtle = SwitchEvaluator.bit(SwitchFunction.TURTLE);
        for (long high : HIGH_BITS) {
            assertNull(TurtleSelector.fromSwitches(high, high));
            assertEquals(Boolean.TRUE, TurtleSelector.fromSwitches(turtle | high, turtle | high));
            assertEquals(Boolean.FALSE, TurtleSelector.fromSwitches(high, turtle | high));
        }
        assertSameTrace(high -> {
            TurtleSelector selector = new TurtleSelector();
            List<Object> trace = new ArrayList<>();
            long[][] steps = {{0, turtle}, {turtle, turtle}, {0, turtle}, {0, 0}, {turtle, turtle}};
            for (long[] step : steps) {
                trace.add(selector.observe(true, step[0] | high, step[1] | high));
                trace.add(selector.selected());
                trace.add(selector.fromKey());
            }
            return trace;
        });
    }

    @Test
    void savedProfileKeysStayReadable() {
        for (SwitchFunction function : FUNCTIONS) {
            assertEquals(function.name().toLowerCase(Locale.ROOT), DeviceProfile.key(function));
        }
        String stored = "{\"name\":\"Funke\",\"switches\":{"
                + "\"arm\":{\"source\":\"BUTTON\",\"index\":0,\"toggle\":true},"
                + "\"angle\":{\"source\":\"BUTTON\",\"index\":1},"
                + "\"race_restart\":{\"source\":\"BUTTON\",\"index\":2},"
                + "\"turtle\":{\"source\":\"BUTTON\",\"index\":3},"
                + "\"command_8\":{\"source\":\"BUTTON\",\"index\":4,\"toggle\":true}},"
                + "\"commands\":{\"command_8\":{\"on\":\"/time set day\"}}}";
        Gson gson = new Gson();
        DeviceProfile profile = gson.fromJson(stored, DeviceProfile.class);
        profile.sanitize();
        assertEquals(List.of("arm", "angle", "reset", "turtle", "command_8"), new ArrayList<>(profile.switches.keySet()));
        assertEquals(0, profile.binding(SwitchFunction.ARM).index);
        assertEquals(2, profile.binding(SwitchFunction.RESET).index);
        assertEquals(4, profile.binding(SwitchFunction.COMMAND_8).index);
        assertTrue(profile.binding(SwitchFunction.ARM).toggle);
        assertTrue(profile.command(SwitchFunction.COMMAND_8).hasOn());
        boolean[] pressed = {true, true, true, true, true};
        long bits = pressedAfterRelease(profile, pressed);
        long expected = SwitchEvaluator.bit(SwitchFunction.ARM) | SwitchEvaluator.bit(SwitchFunction.ANGLE) | SwitchEvaluator.bit(SwitchFunction.RESET)
                | SwitchEvaluator.bit(SwitchFunction.TURTLE) | SwitchEvaluator.bit(SwitchFunction.COMMAND_8);
        assertEquals(expected, bits);
        DeviceProfile again = gson.fromJson(gson.toJson(profile), DeviceProfile.class);
        again.sanitize();
        assertEquals(new ArrayList<>(profile.switches.keySet()), new ArrayList<>(again.switches.keySet()));
        assertEquals(expected, pressedAfterRelease(again, pressed));
    }

    private static long pressedAfterRelease(DeviceProfile profile, boolean[] pressed) {
        SwitchEvaluator evaluator = new SwitchEvaluator();
        assertEquals(0L, evaluator.evaluate(profile, new float[0], new boolean[pressed.length]));
        return evaluator.evaluate(profile, new float[0], pressed);
    }

    private static RecordComponent component(String name) {
        for (RecordComponent component : RadioFrame.class.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component;
            }
        }
        throw new AssertionError(name);
    }

    private static void assertSameTrace(Function<Long, List<Object>> run) {
        List<Object> plain = run.apply(0L);
        for (long high : HIGH_BITS) {
            assertEquals(plain, run.apply(high), Long.toHexString(high));
        }
    }
}
