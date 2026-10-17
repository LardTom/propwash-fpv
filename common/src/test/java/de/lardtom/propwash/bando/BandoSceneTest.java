package de.lardtom.propwash.bando;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneFraming;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BandoSceneTest {
    private static final float[][] VIEWPORTS = {{616.0F, 256.0F}, {456.0F, 166.0F}, {748.0F, 378.0F}};
    private static final float MARGIN = 6.0F;
    private static final float SAMPLE = 1.0F;

    private static Path lang() {
        for (Path candidate : List.of(Path.of("common/src/lang/bando"), Path.of("src/lang/bando"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        fail("Sprachquellen des Bando-Moduls nicht gefunden");
        return Path.of(".");
    }

    private static JsonObject texts(String language) throws IOException {
        try (Reader reader = Files.newBufferedReader(lang().resolve(language + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void sceneHasTheUsualShape() {
        Scene scene = BandoScene.create();
        assertEquals(BandoScene.ID, scene.id());
        assertTrue(scene.stepCount() >= 3 && scene.stepCount() <= 8, scene.stepCount() + " Schritte");
        assertTrue(scene.duration() <= 60.0F * 20.0F, "zu lang: " + scene.duration());
    }

    @Test
    void textsExistAndFit() throws IOException {
        Scene scene = BandoScene.create();
        for (String language : List.of("de_de", "en_us")) {
            JsonObject texts = texts(language);
            assertNotNull(texts.get(scene.titleKey()), language + " Titel fehlt");
            assertTrue(texts.get(scene.titleKey()).getAsString().length() <= 38, language + " Titel zu lang");
            assertTrue(texts.get(scene.descriptionKey()).getAsString().length() <= 64, language + " Beschreibung zu lang");
            Set<String> keys = new HashSet<>(scene.translationKeys());
            keys.add(scene.titleKey());
            keys.add(scene.descriptionKey());
            Set<String> sceneTexts = new HashSet<>();
            for (String key : texts.keySet()) {
                if (key.startsWith("propwash.guide.")) {
                    sceneTexts.add(key);
                }
            }
            assertEquals(keys, sceneTexts, language + ": Schlüssel passen nicht zur Szene");
            for (String key : scene.translationKeys()) {
                String text = texts.get(key).getAsString();
                int limit = key.contains(".step.") && key.endsWith(".text") ? 150 : key.contains(".step.") ? 34 : key.contains(".hint.") ? 28 : 90;
                assertTrue(text.length() <= limit, language + " " + key + " hat " + text.length() + " Zeichen (max. " + limit + ")");
            }
        }
    }

    @Test
    void everyStepExplainsSomething() {
        Scene scene = BandoScene.create();
        Set<String> seen = new HashSet<>();
        for (int step = 0; step < scene.stepCount(); step++) {
            boolean any = false;
            for (float t = scene.stepStart(step); t < scene.stepEnd(step); t += SAMPLE) {
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
                assertTrue(seen.contains(key), "nie sichtbar: " + key.toLowerCase(Locale.ROOT));
            }
        }
    }

    @Test
    void anchorsAndDroneStayInView() {
        Scene scene = BandoScene.create();
        List<String> problems = new ArrayList<>();
        SceneFraming framing = SceneFraming.of(scene);
        for (float t = 0.0F; t <= scene.duration(); t += SAMPLE) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            for (float[] viewport : VIEWPORTS) {
                float w = viewport[0];
                float h = viewport[1];
                SceneProjection projection = framing.projection(state.camera(), state.time(),
                        SceneProjection.basePixelsPerBlock(w, h, scene.viewRadius()), 0.0F, 0.0F, w, h, 1.0F);
                String where = scene.steps().get(state.step()).name() + " t=" + t + " " + (int) w + "x" + (int) h;
                for (SceneState.CalloutView callout : state.callouts()) {
                    if (callout.alpha() >= 0.5F) {
                        check(projection, callout.anchor(), w, h, MARGIN, where + " Hinweis " + callout.textKey(), problems);
                    }
                }
                for (ActorState actor : state.visibleActors()) {
                    if (actor.spec() instanceof ActorSpec.Drone && actor.presence() >= 0.99F) {
                        check(projection, actor.position().add(0.0F, 0.1F, 0.0F), w, h, 0.0F, where + " Drohne", problems);
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " Probleme, erstes: " + (problems.isEmpty() ? "" : problems.getFirst()));
    }

    private static void check(SceneProjection projection, ScenePos pos, float w, float h, float margin, String what, List<String> problems) {
        float[] p = projection.project(pos);
        if (p[0] < margin || p[0] > w - margin || p[1] < margin || p[1] > h - margin) {
            problems.add(what + String.format(Locale.ROOT, " bei %.0f/%.0f", p[0], p[1]));
        }
    }
}
