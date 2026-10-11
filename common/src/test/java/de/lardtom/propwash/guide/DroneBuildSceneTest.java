package de.lardtom.propwash.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.DronePart;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scene.SceneStep;
import de.lardtom.propwash.guide.scene.TimedCue;
import de.lardtom.propwash.guide.scenes.DroneBuildScene;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DroneBuildSceneTest {
    private static final Pattern FAKE_UMLAUT = Pattern.compile("(?i)\\b\\w*(fuer|ueber|koennen|muess|waehl|oeffn|schliess|groess|laed|naechst|zurueck)\\w*\\b");

    private static Path guideLang() {
        for (Path candidate : List.of(Path.of("common/src/lang/guide"), Path.of("src/lang/guide"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        fail("Sprachquellen der Flugschule nicht gefunden");
        return Path.of(".");
    }

    private static JsonObject lang(String language) throws IOException {
        try (Reader reader = Files.newBufferedReader(guideLang().resolve(language + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void sceneIsRegisteredAndMatchesItems() {
        Scene scene = GuideScenes.byId(DroneBuildScene.ID);
        assertNotNull(scene);
        assertEquals(8, scene.stepCount());
        assertTrue(GuideScenes.forItem(DroneBuildScene.WORKBENCH, false).contains(scene));
        assertTrue(GuideScenes.forItem("propwash:m2207_1750", true).contains(scene));
        assertTrue(GuideScenes.forItem(DroneBuildScene.DRONE_ITEM, false).contains(scene));
        assertFalse(GuideScenes.forItem("propwash:goggles_analog", false).contains(scene));
    }

    @Test
    void everyTextExistsInBothLanguages() throws IOException {
        JsonObject de = lang("de_de");
        JsonObject en = lang("en_us");
        assertEquals(de.keySet(), en.keySet(), "de_de und en_us der Flugschule haben unterschiedliche Schlüssel");
        for (Scene scene : GuideScenes.all()) {
            for (String key : scene.translationKeys()) {
                assertTrue(de.has(key), "de_de fehlt " + key);
                assertTrue(en.has(key), "en_us fehlt " + key);
            }
        }
        for (String key : de.keySet()) {
            String text = de.get(key).getAsString();
            assertFalse(FAKE_UMLAUT.matcher(text).find(), "Umschreibung statt Umlaut in " + key + ": " + text);
            assertFalse(text.isBlank(), key);
        }
    }

    @Test
    void storyEndsWithADroneThatFlewAndLandedAgain() {
        Scene scene = DroneBuildScene.create();
        int fly = scene.stepCount() - 1;
        SceneState airborne = SceneEvaluator.evaluate(scene, scene.stepStart(fly) + 70.0F);
        ActorState flying = airborne.actor(DroneBuildScene.DRONE);
        assertNotNull(flying);
        assertTrue(flying.armed());
        assertTrue(flying.position().y() > 1.7F);
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        ActorState drone = end.actor(DroneBuildScene.DRONE);
        assertNotNull(drone);
        assertTrue(drone.fullyAssembled());
        assertFalse(drone.armed());
        assertTrue(drone.position().y() < 1.2F);
        ActorState pilot = end.actor(DroneBuildScene.PILOT);
        assertNotNull(pilot);
        assertEquals(DroneBuildScene.REMOTE, pilot.handItem());
        assertEquals(DroneBuildScene.GOGGLES, pilot.headItem());
        assertEquals(27, end.blocks().size());
        assertFalse(end.callouts().isEmpty());
        assertFalse(end.keyHints().isEmpty());
    }

    @Test
    void partsArriveInTheirOwnSteps() {
        Scene scene = DroneBuildScene.create();
        for (DronePart part : DronePart.values()) {
            int steps = 0;
            for (SceneStep step : scene.steps()) {
                for (TimedCue timed : step.cues()) {
                    if (timed.cue() instanceof Cue.AttachPart attach && attach.part() == part) {
                        steps++;
                    }
                }
            }
            assertEquals(1, steps, part + " wird nicht genau einmal angebaut");
        }
        SceneState afterMotors = SceneEvaluator.evaluate(scene, scene.stepEnd(2));
        ActorState drone = afterMotors.actor(DroneBuildScene.DRONE);
        assertNotNull(drone);
        assertTrue(drone.part(DronePart.PROPS).attached());
        assertFalse(drone.part(DronePart.BATTERY).visible());
        assertTrue(SceneEvaluator.evaluate(scene, scene.stepEnd(3)).actor(DroneBuildScene.DRONE).fullyAssembled());
    }

    @Test
    void everyStepShowsAtLeastOneCallout() {
        Scene scene = DroneBuildScene.create();
        Set<String> seen = new HashSet<>();
        for (int step = 0; step < scene.stepCount(); step++) {
            boolean any = false;
            for (float t = scene.stepStart(step); t < scene.stepEnd(step); t += 2.0F) {
                SceneState state = SceneEvaluator.evaluate(scene, t);
                for (SceneState.CalloutView callout : state.callouts()) {
                    seen.add(callout.textKey());
                    any = true;
                }
            }
            assertTrue(any, "Schritt ohne Hinweis: " + scene.steps().get(step).name());
        }
        for (String key : scene.translationKeys()) {
            if (key.contains(".callout.")) {
                assertTrue(seen.contains(key), "Hinweis nie sichtbar: " + key.toLowerCase(Locale.ROOT));
            }
        }
    }

    @Test
    void calloutsOnlyNameWhatIsAlreadyThere() {
        Scene scene = DroneBuildScene.create();
        int motors = 2;
        for (float t = scene.stepStart(motors); t < scene.stepEnd(motors); t += 1.0F) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            ActorState drone = state.actor(DroneBuildScene.DRONE);
            assertNotNull(drone);
            for (SceneState.CalloutView callout : state.callouts()) {
                if (callout.textKey().endsWith(".callout.props")) {
                    assertTrue(drone.part(DronePart.PROPS).attached(), "Propeller-Hinweis vor dem Anbau bei " + t);
                }
                if (callout.textKey().endsWith(".callout.motors")) {
                    assertTrue(drone.part(DronePart.MOTORS).attached(), "Motor-Hinweis vor dem Anbau bei " + t);
                }
            }
        }
        for (float t = 0.0F; t <= scene.duration(); t += 2.0F) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            boolean charger = state.block(DroneBuildScene.CHARGER_X, 1, DroneBuildScene.CHARGER_Z) != null;
            boolean explained = t >= scene.stepStart(3) + DroneBuildScene.CHARGER_DROP;
            assertEquals(explained, charger, "Ladegerät ohne Erklärung bei " + t);
        }
    }
}
