package de.lardtom.propwash.guide;

import de.lardtom.propwash.bando.BandoScene;
import de.lardtom.propwash.creative.CreativeScene;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scene.SceneStep;
import de.lardtom.propwash.guide.scene.TimedCue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalloutRulesTest {
    private static final ActorSpec ITEM = new ActorSpec.Item("minecraft:stone", 1.0F, false);

    static List<Scene> allScenes() {
        List<Scene> scenes = new ArrayList<>(GuideScenes.all());
        scenes.add(BandoScene.create());
        scenes.add(CreativeScene.create());
        return scenes;
    }

    private static Scene framesLike() {
        return SceneBuilder.scene("test:frames")
                .icon("minecraft:stone")
                .step("lineup", 40.0F, s -> {
                    s.at(0.0F).spawn("a", ITEM, new ScenePos(-1.0F, 1.0F, 0.0F), 0.0F, Cue.Appear.INSTANT, 0.0F);
                    s.at(0.0F).spawn("b", ITEM, new ScenePos(1.0F, 1.0F, 0.0F), 0.0F, Cue.Appear.INSTANT, 0.0F);
                })
                .step("hframe", 120.0F, s -> {
                    s.at(26.0F).callout("hframe", Anchor.on("b", 0.0F, 0.25F, 0.0F), Cue.Side.ABOVE, 0.0F);
                    s.at(66.0F).camera(ScenePos.ZERO, 0.0F, 60.0F, 1.0F, 30.0F);
                    s.at(90.0F).callout("tag_a", Anchor.on("a", 0.0F, 0.2F, -0.3F), Cue.Side.ABOVE, 0.0F);
                    s.at(92.0F).callout("tag_b", Anchor.on("b", 0.0F, 0.2F, -0.3F), Cue.Side.ABOVE, 0.0F);
                    s.at(94.0F).callout("detail", Anchor.on("a", 0.0F, 0.25F, 0.0F), Cue.Side.RIGHT, 0.0F);
                })
                .build();
    }

    private static List<String> names(SceneState state) {
        List<String> names = new ArrayList<>();
        for (SceneState.CalloutView callout : state.callouts()) {
            if (callout.alpha() > 0.02F) {
                names.add(callout.textKey().substring(callout.textKey().lastIndexOf('.') + 1));
            }
        }
        return names;
    }

    @Test
    void newerLabelAtTheSameSpotReplacesTheOlderOne() {
        Scene scene = framesLike();
        float step = scene.stepStart(1);
        assertEquals(List.of("hframe"), names(SceneEvaluator.evaluate(scene, step + 60.0F)));
        assertEquals(List.of("hframe", "tag_a"), names(SceneEvaluator.evaluate(scene, step + 91.0F)));
        SceneState fading = SceneEvaluator.evaluate(scene, step + 90.5F);
        assertTrue(fading.callouts().getFirst().alpha() < 1.0F, "der alte Hinweis blendet vor dem neuen aus");
        assertEquals(List.of("tag_a", "tag_b"), names(SceneEvaluator.evaluate(scene, step + 92.5F)));
        assertEquals(List.of("tag_a", "tag_b", "detail"), names(SceneEvaluator.evaluate(scene, scene.duration())));
    }

    @Test
    void doneShowsTheLastStepsOpenLabelsSteadily() {
        Scene scene = framesLike();
        for (float t = scene.duration() - SceneEvaluator.FADE_TICKS * 2.0F; t <= scene.duration(); t += 0.5F) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            for (SceneState.CalloutView callout : state.callouts()) {
                if (callout.age() >= SceneEvaluator.FADE_TICKS) {
                    assertEquals(1.0F, callout.alpha(), 1.0E-6F, callout.textKey() + " blendet am Ende bei " + t + " aus");
                }
            }
        }
        Scene earlier = SceneBuilder.scene("test:early")
                .icon("minecraft:stone")
                .step("one", 60.0F, s -> {
                    s.at(0.0F).spawn("a", ITEM, ScenePos.ZERO, 0.0F, Cue.Appear.INSTANT, 0.0F);
                    s.at(5.0F).callout("short", Anchor.on("a", 0.0F, 0.5F, 0.0F), Cue.Side.RIGHT, 20.0F);
                    s.at(10.0F).callout("open", Anchor.on("a", 0.0F, 0.5F, 0.0F), Cue.Side.LEFT, 0.0F);
                })
                .step("two", 60.0F, s -> s.at(5.0F).callout("last", Anchor.on("a", 0.0F, 0.5F, 0.0F), Cue.Side.ABOVE, 50.0F))
                .build();
        assertEquals(List.of(), names(SceneEvaluator.evaluate(earlier, earlier.duration())));
        assertTrue(SceneEvaluator.evaluate(earlier, 58.0F).callouts().stream().allMatch(c -> c.alpha() < 1.0F),
                "Hinweise eines Schritts blenden zum Schrittende aus");
    }

    @Test
    void differentSidesOrAnchorsStayTogether() {
        Cue.Callout above = new Cue.Callout("a", Anchor.on("x", 0.0F, 0.2F, 0.0F), Cue.Side.ABOVE);
        assertFalse(Scene.supersedes(new Cue.Callout("b", Anchor.on("x", 0.0F, 0.2F, 0.0F), Cue.Side.RIGHT), above));
        assertFalse(Scene.supersedes(new Cue.Callout("b", Anchor.on("y", 0.0F, 0.2F, 0.0F), Cue.Side.ABOVE), above));
        assertFalse(Scene.supersedes(new Cue.Callout("b", Anchor.on("x", 0.0F, 0.9F, 0.0F), Cue.Side.ABOVE), above));
        assertFalse(Scene.supersedes(new Cue.Callout("b", Anchor.at(0.0F, 0.2F, 0.0F), Cue.Side.ABOVE), above));
        assertTrue(Scene.supersedes(new Cue.Callout("b", Anchor.on("x", 0.1F, 0.3F, -0.2F), Cue.Side.ABOVE), above));
    }

    @Test
    void calloutIdsAreUniquePerCue() {
        for (Scene scene : allScenes()) {
            Set<Integer> ids = new HashSet<>();
            for (int i = 0; i < scene.stepCount(); i++) {
                for (int c = 0; c < scene.steps().get(i).cues().size(); c++) {
                    assertTrue(ids.add(scene.cueId(i, c)), scene.id() + ": doppelte Cue-Nummer");
                }
            }
            for (float t = 0.0F; t <= scene.duration(); t += 7.0F) {
                Set<String> seen = new HashSet<>();
                for (SceneState.CalloutView callout : SceneEvaluator.evaluate(scene, t).callouts()) {
                    assertTrue(callout.cue() >= 0);
                    assertTrue(seen.add(callout.layoutId()), scene.id() + ": Hinweis doppelt bei " + t);
                }
            }
        }
    }

    @Test
    void noLessonShowsTwoLabelsForTheSameSpot() {
        for (Scene scene : allScenes()) {
            List<Cue.Callout> cues = new ArrayList<>();
            for (SceneStep step : scene.steps()) {
                for (TimedCue timed : step.cues()) {
                    if (timed.cue() instanceof Cue.Callout callout) {
                        cues.add(callout);
                    }
                }
            }
            for (float t = 0.0F; t <= scene.duration(); t += 0.5F) {
                SceneState state = SceneEvaluator.evaluate(scene, t);
                List<SceneState.CalloutView> shown = state.callouts().stream().filter(c -> c.alpha() > 0.02F).toList();
                for (int a = 0; a < shown.size(); a++) {
                    for (int b = a + 1; b < shown.size(); b++) {
                        Cue.Callout first = cue(scene, shown.get(a));
                        Cue.Callout second = cue(scene, shown.get(b));
                        assertFalse(Scene.supersedes(second, first) || Scene.supersedes(first, second),
                                scene.id() + " bei " + t + ": " + first.textKey() + " und " + second.textKey() + " zeigen auf dieselbe Stelle");
                    }
                }
            }
        }
    }

    @Test
    void everyLessonEndsSteadyWithoutAFadeAtTheEnd() {
        for (Scene scene : allScenes()) {
            SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
            SceneState before = SceneEvaluator.evaluate(scene, scene.duration() - 1.0F);
            assertFalse(end.callouts().isEmpty(), scene.id() + ": Schlussbild ohne Hinweis");
            for (SceneState.CalloutView callout : end.callouts()) {
                assertEquals(1.0F, callout.alpha(), 1.0E-6F, scene.id() + ": " + callout.textKey());
                SceneState.CalloutView earlier = before.callouts().stream().filter(c -> c.cue() == callout.cue()).findFirst().orElse(null);
                if (earlier != null && callout.age() > SceneEvaluator.FADE_TICKS + 1.0F) {
                    assertEquals(1.0F, earlier.alpha(), 1.0E-6F, scene.id() + ": " + callout.textKey() + " blinkt vor dem Ende");
                }
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
        throw new AssertionError("Cue " + id + " fehlt in " + scene.id());
    }
}
