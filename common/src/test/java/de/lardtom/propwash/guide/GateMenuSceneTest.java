package de.lardtom.propwash.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneFraming;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scene.SceneStep;
import de.lardtom.propwash.guide.scene.TimedCue;
import de.lardtom.propwash.guide.scenes.GateMenuScene;
import de.lardtom.propwash.guide.scenes.SceneKit;
import de.lardtom.propwash.testutil.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateMenuSceneTest {
    private static final float[][] VIEWPORTS = {{616.0F, 256.0F}, {456.0F, 166.0F}, {748.0F, 378.0F}};
    private static final float MARGIN = 6.0F;
    private static final Set<String> KNOWN_KEYS = Set.of("key.use", "key.sneak", SceneKit.KEY_GATE_EDIT_MOUSE);

    private static JsonObject lang(String language) throws IOException {
        try (Reader reader = Files.newBufferedReader(TestPaths.lang("gatehint").resolve(language + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void sceneHasFiveStepsAndIsRegisteredByTheClient() {
        Scene scene = GateMenuScene.create();
        assertEquals(GateMenuScene.ID, scene.id());
        assertEquals(List.of("open", "adjust", "redstone", "position", "freeform"), scene.steps().stream().map(SceneStep::name).toList());
        assertTrue(scene.duration() <= 60.0F * 20.0F, "zu lang: " + scene.duration());
        assertFalse(GuideScenes.all().stream().anyMatch(s -> s.id().equals(GateMenuScene.ID)), "Szene meldet erst der Client an");
        for (String item : List.of(SceneKit.GATE_SMALL, SceneKit.GATE_MEDIUM, SceneKit.GATE_LARGE, SceneKit.FREEFORM_GATE, SceneKit.COURSE_TOOL)) {
            assertTrue(scene.items().matches(item, false), "Item öffnet das Kapitel nicht: " + item);
        }
        assertFalse(scene.items().matches(SceneKit.RACE_FLAG, false));
    }

    @Test
    void everyTextExistsAndFits() throws IOException {
        Scene scene = GateMenuScene.create();
        for (String language : List.of("de_de", "en_us")) {
            JsonObject texts = lang(language);
            assertTrue(texts.has(scene.titleKey()) && texts.get(scene.titleKey()).getAsString().length() <= 38, language + " Titel");
            assertTrue(texts.has(scene.descriptionKey()) && texts.get(scene.descriptionKey()).getAsString().length() <= 64, language + " Beschreibung");
            for (String key : scene.translationKeys()) {
                assertTrue(texts.has(key), language + " ohne " + key);
                int length = texts.get(key).getAsString().length();
                int limit = key.contains(".step.") && key.endsWith(".text") ? 150 : key.contains(".step.") ? 34 : key.contains(".hint.") ? 28 : 90;
                assertTrue(length <= limit, language + " " + key + " hat " + length + " Zeichen (max. " + limit + ")");
            }
            assertEquals(lang("en_us").keySet(), texts.keySet(), "beide Sprachen mit denselben Schlüsseln");
        }
    }

    @Test
    void everyStepExplainsSomethingAndEveryLabelIsSeen() {
        Scene scene = GateMenuScene.create();
        Set<String> seen = new HashSet<>();
        for (int step = 0; step < scene.stepCount(); step++) {
            boolean any = false;
            for (float t = scene.stepStart(step); t < scene.stepEnd(step); t += 1.0F) {
                SceneState state = SceneEvaluator.evaluate(scene, t);
                for (SceneState.CalloutView callout : state.callouts()) {
                    seen.add(callout.textKey());
                    any = true;
                }
                for (SceneState.KeyHintView hint : state.keyHints()) {
                    seen.add(hint.textKey());
                }
            }
            assertTrue(any, "Schritt ohne Hinweis: " + scene.steps().get(step).name());
        }
        for (String key : scene.translationKeys()) {
            if (key.contains(".callout.") || key.contains(".hint.")) {
                assertTrue(seen.contains(key), "nie sichtbar: " + key.toLowerCase(Locale.ROOT));
            }
        }
    }

    @Test
    void onlyKnownKeysAndPropwashItems() {
        Scene scene = GateMenuScene.create();
        Set<String> items = new LinkedHashSet<>(List.of(SceneKit.GATE_SMALL, SceneKit.GATE_MEDIUM, SceneKit.GATE_LARGE, SceneKit.COURSE_TOOL,
                SceneKit.FREEFORM_GATE, SceneKit.REMOTE));
        Set<String> used = new LinkedHashSet<>(scene.items().ids());
        used.add(scene.icon());
        for (SceneStep step : scene.steps()) {
            for (TimedCue timed : step.cues()) {
                switch (timed.cue()) {
                    case Cue.Spawn spawn when spawn.spec() instanceof ActorSpec.Pilot pilot -> {
                        used.add(pilot.headItem());
                        used.add(pilot.handItem());
                    }
                    case Cue.Equip equip -> used.add(equip.item());
                    case Cue.Keys keys -> {
                        for (KeyToken token : keys.keys()) {
                            if (token.kind() == KeyToken.Kind.MAPPING) {
                                assertTrue(KNOWN_KEYS.contains(token.value()), "unbekannte Taste " + token.value());
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        for (String id : used) {
            if (id.startsWith(SceneKit.NAMESPACE)) {
                assertTrue(items.contains(id), "unbekanntes Propwash-Item " + id);
            }
        }
    }

    @Test
    void theLampLightsWhenTheDronePassesAndGoesOutAfterThePulse() {
        Scene scene = GateMenuScene.create();
        float start = scene.stepStart(2);
        assertEquals("false", lamp(SceneEvaluator.evaluate(scene, start + GateMenuScene.PASS_AT - 2.0F)));
        assertEquals("true", lamp(SceneEvaluator.evaluate(scene, start + GateMenuScene.PASS_AT + 1.0F)));
        assertEquals("false", lamp(SceneEvaluator.evaluate(scene, start + GateMenuScene.PASS_AT + GateMenuScene.PULSE + 1.0F)));
        SceneState pass = SceneEvaluator.evaluate(scene, start + GateMenuScene.PASS_AT);
        ActorState drone = pass.visibleActors().stream().filter(actor -> actor.id().equals(GateMenuScene.DRONE)).findFirst().orElseThrow();
        assertEquals(GateMenuScene.GATE_POS.z(), drone.position().z(), 0.6F, "Drohne mitten im Gate, wenn die Lampe angeht");
        float bottom = GateMenuScene.LIFTED_POS.y() + 0.5F;
        assertTrue(drone.position().y() > bottom + 0.3F && drone.position().y() < bottom + GateMenuScene.WIDE_HEIGHT - 0.3F,
                "Drohne in der Öffnung: " + drone.position().y());
        ActorState gate = pass.visibleActors().stream().filter(actor -> actor.id().equals(GateMenuScene.WIDE)).findFirst().orElseThrow();
        assertEquals(GateMenuScene.LIFTED_POS.y(), gate.position().y(), 1.0E-4F, "Gate steht auf der Lampe");
    }

    @Test
    void thePositionStepMovesTheGateInSmallStepsOnTheGrid() throws IOException {
        Scene scene = GateMenuScene.create();
        int step = scene.steps().stream().map(SceneStep::name).toList().indexOf("position");
        assertTrue(step >= 0, "Schritt Position fehlt");
        Set<Float> rests = new LinkedHashSet<>();
        float previous = Float.NaN;
        for (float t = scene.stepStart(step); t < scene.stepEnd(step); t += 0.5F) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            ActorState gate = state.visibleActors().stream().filter(actor -> actor.id().equals(GateMenuScene.WIDE)).findFirst().orElse(null);
            assertTrue(gate != null, "Gate fehlt im Schritt Position bei " + t);
            ScenePos offset = gate.position().add(-GateMenuScene.LIFTED_POS.x(), -GateMenuScene.LIFTED_POS.y(), -GateMenuScene.LIFTED_POS.z());
            float far = Math.max(Math.abs(offset.x()), Math.max(Math.abs(offset.y()), Math.abs(offset.z())));
            assertTrue(far <= 1.0F + 1.0E-4F, "Gate springt mehr als einen Block weit: " + offset + " bei " + t);
            if (offset.x() == previous) {
                float grid = offset.x() * 16.0F;
                assertEquals(Math.round(grid), grid, 1.0E-3F, "Ruhelage nicht auf 1/16: " + offset.x());
                rests.add(offset.x());
            }
            previous = offset.x();
        }
        assertTrue(rests.size() >= 3, "Gate rastet nicht in mehreren kleinen Schritten: " + rests);
        Set<String> callouts = new LinkedHashSet<>();
        for (float t = scene.stepStart(step); t < scene.stepEnd(step); t += 1.0F) {
            for (SceneState.CalloutView callout : SceneEvaluator.evaluate(scene, t).callouts()) {
                callouts.add(callout.textKey());
            }
        }
        for (String language : List.of("en_us", "de_de")) {
            JsonObject texts = lang(language);
            StringBuilder all = new StringBuilder(texts.get("propwash.guide.scene.gate_menu.step.position.text").getAsString());
            for (String key : callouts) {
                all.append(' ').append(texts.get(key).getAsString());
            }
            for (String part : List.of("1/16", "1/4", "Position")) {
                assertTrue(all.toString().contains(part), language + ": Schritt Position nennt " + part + " nicht: " + all);
            }
        }
    }

    private static String lamp(SceneState state) {
        for (SceneState.BlockView block : state.blocks()) {
            if (block.at().x() == GateMenuScene.LAMP_X && block.at().y() == GateMenuScene.LAMP_Y && block.at().z() == GateMenuScene.LAMP_Z) {
                return block.block().properties().getOrDefault("lit", "?") + (block.block().id().equals("minecraft:redstone_lamp") ? "" : " " + block.block().id());
            }
        }
        return "fehlt";
    }

    @Test
    void labelsFollowTheFlightSchoolRules() {
        Scene scene = GateMenuScene.create();
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < scene.stepCount(); i++) {
            for (int c = 0; c < scene.steps().get(i).cues().size(); c++) {
                assertTrue(ids.add(scene.cueId(i, c)), "doppelte Cue-Nummer");
            }
        }
        for (float t = 0.0F; t <= scene.duration(); t += 0.5F) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            Set<String> layout = new HashSet<>();
            List<SceneState.CalloutView> shown = new ArrayList<>();
            for (SceneState.CalloutView callout : state.callouts()) {
                assertTrue(layout.add(callout.layoutId()), "Hinweis doppelt bei " + t);
                if (callout.alpha() > 0.02F) {
                    shown.add(callout);
                }
            }
            for (int a = 0; a < shown.size(); a++) {
                for (int b = a + 1; b < shown.size(); b++) {
                    Cue.Callout first = cue(scene, shown.get(a));
                    Cue.Callout second = cue(scene, shown.get(b));
                    assertFalse(Scene.supersedes(second, first) || Scene.supersedes(first, second),
                            "bei " + t + ": " + first.textKey() + " und " + second.textKey() + " zeigen auf dieselbe Stelle");
                }
            }
        }
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        SceneState before = SceneEvaluator.evaluate(scene, scene.duration() - 1.0F);
        assertFalse(end.callouts().isEmpty(), "Schlussbild ohne Hinweis");
        for (SceneState.CalloutView callout : end.callouts()) {
            assertEquals(1.0F, callout.alpha(), 1.0E-6F, callout.textKey());
            SceneState.CalloutView earlier = before.callouts().stream().filter(c -> c.cue() == callout.cue()).findFirst().orElse(null);
            if (earlier != null && callout.age() > SceneEvaluator.FADE_TICKS + 1.0F) {
                assertEquals(1.0F, earlier.alpha(), 1.0E-6F, callout.textKey() + " blinkt vor dem Ende");
            }
        }
    }

    private static Cue.Callout cue(Scene scene, SceneState.CalloutView view) {
        int id = view.cue();
        for (int i = 0; i < scene.stepCount(); i++) {
            List<TimedCue> cues = scene.steps().get(i).cues();
            for (int c = 0; c < cues.size(); c++) {
                if (scene.cueId(i, c) == id) {
                    return (Cue.Callout) cues.get(c).cue();
                }
            }
        }
        throw new AssertionError("Cue " + id + " fehlt");
    }

    @Test
    void anchorsAndDronesStayInView() {
        Scene scene = GateMenuScene.create();
        SceneFraming framing = SceneFraming.of(scene);
        List<String> problems = new ArrayList<>();
        for (float t = 0.0F; t <= scene.duration(); t += 1.0F) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            for (float[] viewport : VIEWPORTS) {
                float w = viewport[0];
                float h = viewport[1];
                SceneProjection projection = framing.projection(state.camera(), state.time(), SceneProjection.basePixelsPerBlock(w, h, scene.viewRadius()),
                        0.0F, 0.0F, w, h, 1.0F);
                String where = scene.steps().get(state.step()).name() + " t=" + t + " " + (int) w + "x" + (int) h;
                for (SceneState.CalloutView callout : state.callouts()) {
                    if (callout.alpha() >= 0.5F) {
                        check(projection, callout.anchor(), w, h, MARGIN, where + " Hinweis " + callout.textKey(), problems);
                    }
                }
                for (SceneState.KeyHintView hint : state.keyHints()) {
                    if (hint.anchor() != null && hint.alpha() >= 0.5F) {
                        check(projection, hint.anchor(), w, h, MARGIN, where + " Taste " + hint.textKey(), problems);
                    }
                }
                for (ActorState actor : state.visibleActors()) {
                    if (actor.spec() instanceof ActorSpec.Drone && actor.presence() >= 0.99F) {
                        check(projection, actor.position().add(0.0F, 0.1F, 0.0F), w, h, 0.0F, where + " Drohne", problems);
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " Probleme, zuerst: " + problems.stream().limit(8).toList());
    }

    private static void check(SceneProjection projection, ScenePos pos, float w, float h, float margin, String what, List<String> problems) {
        float[] p = projection.project(pos);
        if (p[0] < margin || p[0] > w - margin || p[1] < margin || p[1] > h - margin) {
            problems.add(what + String.format(Locale.ROOT, " bei %.0f/%.0f", p[0], p[1]));
        }
    }
}
