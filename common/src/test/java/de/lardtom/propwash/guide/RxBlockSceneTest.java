package de.lardtom.propwash.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scenes.RxBlockScene;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RxBlockSceneTest {
    private static JsonObject lang(String language) throws IOException {
        for (Path candidate : List.of(Path.of("common/src/lang/rx"), Path.of("src/lang/rx"))) {
            Path file = candidate.resolve(language + ".json");
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        fail("Sprachquellen des RX-Blocks nicht gefunden");
        return new JsonObject();
    }

    @Test
    void sceneHasFiveStepsWithinTheLimits() {
        Scene scene = RxBlockScene.create();
        assertEquals(RxBlockScene.ID, scene.id());
        assertEquals(5, scene.stepCount());
        assertTrue(scene.duration() <= 60.0F * 20.0F, "zu lang: " + scene.duration());
        assertTrue(scene.items().matches(RxBlockScene.RX_BLOCK, false));
        assertFalse(GuideScenes.all().stream().anyMatch(s -> s.id().equals(RxBlockScene.ID)), "Szene wird erst vom Client angemeldet");
    }

    @Test
    void everyTextExistsAndFits() throws IOException {
        Scene scene = RxBlockScene.create();
        for (String language : List.of("de_de", "en_us")) {
            JsonObject texts = lang(language);
            assertTrue(texts.get(scene.titleKey()).getAsString().length() <= 38, language + " Titel zu lang");
            assertTrue(texts.get(scene.descriptionKey()).getAsString().length() <= 64, language + " Beschreibung zu lang");
            for (String key : scene.translationKeys()) {
                assertTrue(texts.has(key), language + " ohne " + key);
                int length = texts.get(key).getAsString().length();
                int limit = key.contains(".step.") && key.endsWith(".text") ? 150 : key.contains(".step.") ? 34 : key.contains(".hint.") ? 28 : 90;
                assertTrue(length <= limit, language + " " + key + " hat " + length + " Zeichen (max. " + limit + ")");
            }
        }
    }

    @Test
    void everyStepShowsACallout() {
        Scene scene = RxBlockScene.create();
        for (int step = 0; step < scene.stepCount(); step++) {
            boolean any = false;
            for (float t = scene.stepStart(step); t < scene.stepEnd(step); t += 1.0F) {
                SceneState state = SceneEvaluator.evaluate(scene, t);
                any |= !state.callouts().isEmpty();
            }
            assertTrue(any, "Schritt ohne Hinweis: " + scene.steps().get(step).name());
        }
    }
}
