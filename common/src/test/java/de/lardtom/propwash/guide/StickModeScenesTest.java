package de.lardtom.propwash.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scenes.ConfiguratorScene;
import de.lardtom.propwash.guide.scenes.Mode3dScene;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class StickModeScenesTest {
    private static final String PREFIX = "propwash.guide.scene.configurator.callout.";
    private static final Map<String, StickChannel> STICK_CALLOUTS = Map.of("stick_roll", StickChannel.ROLL, "stick_pitch", StickChannel.PITCH,
            "stick_yaw", StickChannel.YAW, "stick_throttle", StickChannel.THROTTLE, "input", StickChannel.ROLL);

    private static JsonObject lang(String language) throws IOException {
        for (Path candidate : List.of(Path.of("common/src/lang/guide"), Path.of("src/lang/guide"))) {
            Path file = candidate.resolve(language + ".json");
            if (Files.isRegularFile(file)) {
                return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        fail("Sprachquellen der Flugschule nicht gefunden");
        return new JsonObject();
    }

    private static String shown(JsonObject lang, String base, StickMode mode) {
        String variant = mode.variantKey(base);
        return lang.has(variant) ? lang.get(variant).getAsString() : lang.get(base).getAsString();
    }

    private static Scene configurator() {
        Scene scene = GuideScenes.byId(ConfiguratorScene.ID);
        assertNotNull(scene);
        return scene;
    }

    private static SceneState.CalloutView callout(Scene scene, String key, StickMode mode) {
        for (float t = 0.0F; t <= scene.duration(); t += 1.0F) {
            for (SceneState.CalloutView view : SceneEvaluator.evaluate(scene, t, mode).callouts()) {
                if (view.textKey().equals(key) && view.alpha() >= 0.99F) {
                    return view;
                }
            }
        }
        fail(key + " nie voll sichtbar");
        return null;
    }

    @ParameterizedTest
    @EnumSource(StickMode.class)
    void stickCalloutsSitOnTheStickOfTheMode(StickMode mode) {
        Scene scene = configurator();
        for (Map.Entry<String, StickChannel> e : STICK_CALLOUTS.entrySet()) {
            SceneState.CalloutView base = callout(scene, PREFIX + e.getKey(), StickMode.MODE_2);
            SceneState.CalloutView shown = callout(scene, PREFIX + e.getKey(), mode);
            boolean same = mode.sameSide(e.getValue(), StickMode.MODE_2);
            assertEquals(same, base.anchor().x() == shown.anchor().x(), mode + " " + e.getKey());
            assertEquals(base.anchor().y(), shown.anchor().y());
            assertEquals(same, base.side() == shown.side(), mode + " " + e.getKey() + " Seite");
        }
        SceneState.CalloutView left = callout(scene, PREFIX + "left", mode);
        SceneState.CalloutView right = callout(scene, PREFIX + "right", mode);
        assertEquals(Cue.Side.LEFT, left.side());
        assertEquals(Cue.Side.RIGHT, right.side());
        assertTrue(left.anchor().x() < right.anchor().x());
    }

    @ParameterizedTest
    @EnumSource(StickMode.class)
    void throttleCalloutsOfThe3dLessonFollowTheThrottleStick(StickMode mode) {
        Scene scene = GuideScenes.byId(Mode3dScene.ID);
        assertNotNull(scene);
        SceneState.CalloutView base = callout(scene, "propwash.guide.scene.mode_3d.callout.center", StickMode.MODE_2);
        SceneState.CalloutView shown = callout(scene, "propwash.guide.scene.mode_3d.callout.center", mode);
        assertEquals(mode.left(StickChannel.THROTTLE), shown.side() == Cue.Side.LEFT);
        assertEquals(mode.sameSide(StickChannel.THROTTLE, StickMode.MODE_2), base.anchor().x() == shown.anchor().x());
    }

    @ParameterizedTest
    @EnumSource(StickMode.class)
    void stickTextsNameTheRightStick(StickMode mode) throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        for (Map.Entry<String, StickChannel> e : STICK_CALLOUTS.entrySet()) {
            if (e.getKey().equals("input")) {
                continue;
            }
            boolean left = mode.left(e.getValue());
            assertTrue(shown(en, PREFIX + e.getKey(), mode).startsWith(left ? "Left stick" : "Right stick"), mode + " " + e.getKey());
            assertTrue(shown(de, PREFIX + e.getKey(), mode).startsWith(left ? "Linker Knüppel" : "Rechter Knüppel"), mode + " " + e.getKey());
        }
        String number = String.valueOf(mode.number());
        String step = shown(en, "propwash.guide.scene.configurator.step.sticks.text", mode);
        assertTrue(step.contains("(mode " + number + ")"), step);
        assertTrue(shown(de, "propwash.guide.scene.configurator.step.sticks.text", mode).contains("(Mode " + number + ")"));
        String leftText = shown(en, PREFIX + "left", mode).toLowerCase(Locale.ROOT);
        String rightText = shown(en, PREFIX + "right", mode).toLowerCase(Locale.ROOT);
        for (StickChannel channel : StickChannel.values()) {
            String word = channel.name().toLowerCase(Locale.ROOT);
            assertEquals(mode.left(channel), leftText.contains(word), mode + " links " + word);
            assertEquals(!mode.left(channel), rightText.contains(word), mode + " rechts " + word);
        }
    }

    @Test
    void everyModeVariantExistsInBothLanguagesAndFits() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        int variants = 0;
        for (Scene scene : GuideScenes.all()) {
            for (String key : scene.translationKeys()) {
                for (StickMode mode : StickMode.values()) {
                    String variant = mode.variantKey(key);
                    if (variant.equals(key)) {
                        continue;
                    }
                    assertEquals(en.has(variant), de.has(variant), variant);
                    if (!en.has(variant)) {
                        continue;
                    }
                    variants++;
                    int limit = key.contains(".step.") && key.endsWith(".text") ? 150 : key.contains(".hint.") ? 28 : 90;
                    for (JsonObject lang : List.of(en, de)) {
                        String text = lang.get(variant).getAsString();
                        assertTrue(text.length() <= limit, variant + " hat " + text.length() + " Zeichen");
                        assertFalse(text.isBlank(), variant);
                    }
                    assertNotEquals(en.get(key).getAsString(), en.get(variant).getAsString(), variant + " gleicht dem Grundtext");
                }
            }
        }
        assertEquals(17, variants);
    }
}
