package de.lardtom.propwash.input;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RemoteDemoTest {
    private static final long MS = 1_000_000L;
    private static final float FRAME_S = 0.016F;
    private static final int ROLL = 0;
    private static final int PITCH = 1;
    private static final int THROTTLE = 2;
    private static final int YAW = 3;
    private static final int AUX = 4;

    private static float[] radio(RemoteDemo.Frame f, boolean invertedThrottle) {
        float throttle = invertedThrottle ? -f.leftY() : f.leftY();
        return new float[]{f.rightX(), f.rightY(), throttle, f.leftX(), f.switch0(), f.switch1(), f.switch2(), f.switch3()};
    }

    private static boolean[] buttons(RemoteDemo.Frame f) {
        return new boolean[]{f.button0() > 0.5F, f.button1() > 0.5F, f.button2() > 0.5F};
    }

    private static long nanos(float seconds) {
        return Math.round(seconds * 1000.0) * MS;
    }

    @ParameterizedTest
    @EnumSource(RemoteDemo.Motion.class)
    void everyMotionLoopsSmoothlyInsideTheStickRange(RemoteDemo.Motion motion) {
        RemoteDemo.Frame first = RemoteDemo.frame(motion, 0.0F);
        RemoteDemo.Frame previous = first;
        for (float t = FRAME_S; t < motion.period(); t += FRAME_S) {
            RemoteDemo.Frame f = RemoteDemo.frame(motion, t);
            for (float v : new float[]{f.leftX(), f.leftY(), f.rightX(), f.rightY(), f.switch0(), f.switch1(), f.switch2(), f.switch3()}) {
                assertTrue(v >= -1.0F && v <= 1.0F, motion + " bei " + t + " s außerhalb −1…1: " + v);
            }
            assertTrue(jump(previous, f) <= 0.6F, motion + " springt bei " + t + " s um " + jump(previous, f));
            previous = f;
        }
        assertTrue(jump(previous, first) <= 0.6F, motion + " springt beim Neustart der Schleife um " + jump(previous, first));
        assertEquals(first, RemoteDemo.frame(motion, motion.period()));
        assertEquals(first, RemoteDemo.frame(motion, Float.NaN));
    }

    private static float jump(RemoteDemo.Frame a, RemoteDemo.Frame b) {
        return Math.max(Math.max(Math.abs(a.leftX() - b.leftX()), Math.abs(a.leftY() - b.leftY())),
                Math.max(Math.abs(a.rightX() - b.rightX()), Math.abs(a.rightY() - b.rightY())));
    }

    @Test
    void throttleDemoIsExactlyWhatTheDetectorWants() {
        for (boolean inverted : new boolean[]{false, true}) {
            ThrottleDetector detector = new ThrottleDetector(radio(RemoteDemo.frame(RemoteDemo.Motion.THROTTLE, 0.0F), inverted),
                    new boolean[]{true, true, false, true, false, false, false, false}, THROTTLE, inverted);
            StickDetector.Detection result = null;
            float detectedAt = Float.NaN;
            for (float t = 0.0F; t < RemoteDemo.Motion.THROTTLE.period() && result == null; t += FRAME_S) {
                result = detector.update(radio(RemoteDemo.frame(RemoteDemo.Motion.THROTTLE, t), inverted), nanos(t));
                detectedAt = t;
            }
            assertNotNull(result, "Gas-Vorführung nicht erkannt (" + detector.stage() + ")");
            assertEquals(THROTTLE, result.axis());
            assertEquals(inverted, result.invert());
            RemoteDemo.Cue cue = RemoteDemo.frame(RemoteDemo.Motion.THROTTLE, detectedAt + 0.05F).cue();
            assertEquals(RemoteDemo.Cue.DETECTED, cue, "„erkannt“ passt nicht zur Erkennung bei " + detectedAt + " s");
        }
    }

    @Test
    void throttleDemoShowsUpThenDownThenHold() {
        assertEquals(RemoteDemo.Cue.THROTTLE_UP, RemoteDemo.frame(RemoteDemo.Motion.THROTTLE, 0.8F).cue());
        assertTrue(RemoteDemo.frame(RemoteDemo.Motion.THROTTLE, 1.3F).throttle() > 0.99F);
        assertEquals(RemoteDemo.Cue.THROTTLE_DOWN, RemoteDemo.frame(RemoteDemo.Motion.THROTTLE, 1.8F).cue());
        RemoteDemo.Frame hold = RemoteDemo.frame(RemoteDemo.Motion.THROTTLE, 2.62F);
        assertEquals(RemoteDemo.Cue.THROTTLE_HOLD, hold.cue());
        assertTrue(hold.holding() && hold.hold() > 0.4F && hold.hold() < 0.6F);
        assertEquals(0.0F, hold.throttle(), 1.0E-6F);
    }

    @ParameterizedTest
    @EnumSource(value = StickChannel.class, names = {"ROLL", "PITCH", "YAW"})
    void stickDemosAreDetectedOnTheirOwnAxisWithoutInversion(StickChannel channel) {
        RemoteDemo.Motion motion = RemoteDemo.forChannel(channel);
        boolean[] claimed = new boolean[8];
        int expected = switch (channel) {
            case ROLL -> ROLL;
            case PITCH -> PITCH;
            case YAW -> YAW;
            case THROTTLE -> THROTTLE;
        };
        ChannelDetector detector = ChannelDetector.centered(radio(RemoteDemo.frame(motion, 0.0F), false), claimed);
        StickDetector.Detection result = null;
        float detectedAt = Float.NaN;
        for (float t = 0.0F; t < motion.period() && result == null; t += FRAME_S) {
            result = detector.update(radio(RemoteDemo.frame(motion, t), false), nanos(t));
            detectedAt = t;
        }
        assertNotNull(result, channel + "-Vorführung nicht erkannt");
        assertEquals(expected, result.axis());
        assertFalse(result.invert());
        assertTrue(detectedAt < 1.35F, channel + " erst nach " + detectedAt + " s erkannt, Vorführung hält nur bis 1,35 s");
        assertEquals(channel, RemoteDemo.frame(motion, 0.5F).channel());
    }

    @Test
    void switchDemosAreDetectedOnTheirChannel() {
        for (SwitchFunction function : SwitchFunction.values()) {
            RemoteDemo.Target target = RemoteDemo.forSwitch(function, false);
            RemoteDemo.Frame start = RemoteDemo.frame(target, 0.0F);
            boolean[] sticks = {true, true, true, true, false, false, false, false};
            SwitchDetector detector = new SwitchDetector(radio(start, false), buttons(start), sticks);
            SwitchAssignment binding = null;
            for (float t = 0.0F; t < target.motion().period() && binding == null; t += FRAME_S) {
                RemoteDemo.Frame f = RemoteDemo.frame(target, t);
                binding = detector.update(radio(f, false), buttons(f), nanos(t));
            }
            assertNotNull(binding, function + ": Vorführung nicht als Schalter erkannt");
            if (target.motion() == RemoteDemo.Motion.BUTTON) {
                assertEquals(SwitchSource.BUTTON, binding.source, function.name());
                assertEquals(target.index(), binding.index, function.name());
                assertFalse(function.supportsToggle(), function + " ist ein Taster und braucht einen Knopf");
            } else {
                assertEquals(SwitchSource.AXIS, binding.source, function.name());
                assertEquals(AUX + target.index(), binding.index, function.name());
            }
        }
        assertEquals(new RemoteDemo.Target(RemoteDemo.Motion.SWITCH, RemoteDemo.ARM_SWITCH), RemoteDemo.forSwitch(SwitchFunction.ARM, false));
        assertEquals(RemoteDemo.MODE_SWITCH, RemoteDemo.forSwitch(SwitchFunction.ANGLE, false).index());
        assertEquals(RemoteDemo.Motion.SWITCH_MID, RemoteDemo.forSwitch(SwitchFunction.HORIZON, false).motion());
    }

    @Test
    void radioDefaultsMatchTheDemonstratedModeSwitch() {
        DeviceProfile profile = ProfileDefaults.radio("EdgeTX", 8);
        SwitchEvaluator evaluator = new SwitchEvaluator();
        float[] axes = {0.0F, 0.0F, -1.0F, 0.0F, -1.0F, -1.0F, -1.0F, -1.0F};
        RemoteDemo.Cue[] cues = {RemoteDemo.Cue.MODE_ACRO, RemoteDemo.Cue.MODE_HORIZON, RemoteDemo.Cue.MODE_ANGLE};
        float[] times = {3.3F, 4.5F, 6.0F};
        for (int i = 0; i < cues.length; i++) {
            RemoteDemo.Frame f = RemoteDemo.frame(RemoteDemo.Motion.ARM_AND_MODE, times[i]);
            assertEquals(cues[i], f.cue());
            axes[AUX + RemoteDemo.MODE_SWITCH] = f.switch1();
            int bits = evaluator.evaluate(profile, axes, new boolean[0]);
            assertEquals(i == 1, SwitchEvaluator.isSet(bits, SwitchFunction.HORIZON), "Horizon bei " + cues[i]);
            assertEquals(i == 2, SwitchEvaluator.isSet(bits, SwitchFunction.ANGLE), "Angle bei " + cues[i]);
        }
        RemoteDemo.Frame armed = RemoteDemo.frame(RemoteDemo.Motion.ARM_AND_MODE, 1.5F);
        assertEquals(RemoteDemo.Cue.ARM, armed.cue());
        axes[AUX + RemoteDemo.MODE_SWITCH] = -1.0F;
        axes[AUX + RemoteDemo.ARM_SWITCH] = armed.switch0();
        assertTrue(SwitchEvaluator.isSet(evaluator.evaluate(profile, axes, new boolean[0]), SwitchFunction.ARM));
        axes[AUX + RemoteDemo.ARM_SWITCH] = RemoteDemo.frame(RemoteDemo.Motion.ARM_AND_MODE, 0.2F).switch0();
        assertFalse(SwitchEvaluator.isSet(evaluator.evaluate(profile, axes, new boolean[0]), SwitchFunction.ARM));
    }

    @Test
    void gamepadDemosPressButtons() {
        for (SwitchFunction function : SwitchFunction.values()) {
            RemoteDemo.Target target = RemoteDemo.forSwitch(function, true);
            assertTrue(target.motion() == RemoteDemo.Motion.BUTTON || target.motion() == RemoteDemo.Motion.PAD_TOGGLE, function.name());
            boolean pressed = false;
            for (float t = 0.0F; t < target.motion().period(); t += FRAME_S) {
                pressed |= RemoteDemo.frame(target, t).buttonPress(target.index()) > 0.99F;
            }
            assertTrue(pressed, function + ": Knopf wird nie gedrückt");
        }
    }

    @Test
    void centerDemosEndInTheMiddle() {
        RemoteDemo.Frame center = RemoteDemo.frame(RemoteDemo.Motion.CENTER, 2.5F);
        assertEquals(0.5F, center.throttle(), 1.0E-4F);
        assertEquals(0.0F, center.rightX(), 1.0E-4F);
        assertEquals(0.0F, center.rightY(), 1.0E-4F);
        assertEquals(0.0F, center.leftX(), 1.0E-4F);
        RemoteDemo.Frame threeD = RemoteDemo.frame(RemoteDemo.Motion.CENTER_3D, 3.0F);
        assertEquals(0.5F, threeD.throttle(), 1.0E-4F);
        assertEquals(RemoteDemo.Cue.CENTER_3D, threeD.cue());
        float highest = 0.0F;
        for (float t = 0.9F; t < 2.4F; t += FRAME_S) {
            highest = Math.max(highest, RemoteDemo.frame(RemoteDemo.Motion.CENTER_3D, t).leftY());
        }
        assertTrue(highest > 0.1F, "3D-Mitte ohne sichtbares Einpendeln");
    }

    @Test
    void extremesReachEveryEndStop() {
        float[] min = {1, 1, 1, 1};
        float[] max = {-1, -1, -1, -1};
        for (float t = 0.0F; t < RemoteDemo.Motion.EXTREMES.period(); t += FRAME_S) {
            RemoteDemo.Frame f = RemoteDemo.frame(RemoteDemo.Motion.EXTREMES, t);
            float[] v = {f.leftX(), f.leftY(), f.rightX(), f.rightY()};
            for (int i = 0; i < 4; i++) {
                min[i] = Math.min(min[i], v[i]);
                max[i] = Math.max(max[i], v[i]);
            }
        }
        for (int i = 0; i < 4; i++) {
            assertTrue(min[i] < -0.98F && max[i] > 0.98F, "Achse " + i + " erreicht nicht beide Anschläge");
        }
    }

    @Test
    void everyCueHasATextInBothLanguages() throws IOException {
        Path dir = langDir();
        for (String language : List.of("de_de", "en_us")) {
            JsonObject json = new Gson().fromJson(Files.readString(dir.resolve(language + ".json"), StandardCharsets.UTF_8), JsonObject.class);
            Set<RemoteDemo.Cue> cues = EnumSet.allOf(RemoteDemo.Cue.class);
            cues.remove(RemoteDemo.Cue.NONE);
            for (RemoteDemo.Cue cue : cues) {
                assertTrue(json.has(cue.translationKey()), language + ": " + cue.translationKey() + " fehlt");
                assertFalse(json.get(cue.translationKey()).getAsString().isBlank(), cue.translationKey());
            }
        }
    }

    private static Path langDir() {
        for (Path candidate : List.of(Path.of("common/src/lang/input"), Path.of("src/lang/input"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        fail("Sprachdateien des Moduls input nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }
}
